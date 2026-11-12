package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks profit since the game was launched.
 * Primary source: the sidebar scoreboard balance. Fallback: chat messages
 * ("X paid you $5K", "You sold ... for $2M") when no balance is on the sidebar.
 * Everything is in memory only, so it resets when the game closes.
 */
public final class ProfitTracker {
	private static final Pattern AMOUNT = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbBtT])?(?![a-zA-Z])");
	private static final Pattern LOOSE_AMOUNT = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbBtT])?(?![a-zA-Z0-9])");

	// Scoreboard mode
	private static boolean hasBaseline = false;
	private static double baseline = 0;
	private static double current = 0;
	private static long lastBoardRead = -100000;

	// Chat mode
	private static double chatProfit = 0;
	private static double lastRes = 1;
	private static double baselineRes = 1;

	private static long ticks = 0;

	private ProfitTracker() {}

	public static void reset() {
		hasBaseline = false;
		baseline = 0;
		current = 0;
		chatProfit = 0;
	}

	private static boolean boardActive() {
		return hasBaseline && ticks - lastBoardRead < 200;
	}

	public static double profit() {
		return chatProfit;
	}

	public static void tick(MinecraftClient client) {
		ticks++;
		if (client.world == null || client.player == null) return;
		if (ticks % 10 != 0) return;

		Double balance = readBalance(sidebarLines(client));
		if (balance == null) return;

		double res = lastRes;
		current = balance;
		lastBoardRead = ticks;
		if (!hasBaseline) {
			baseline = balance;
			baselineRes = res;
			hasBaseline = true;
			chatProfit = 0;
		} else {
			// The sidebar is rounded (e.g. $1.4B). If chat tracking is off by more than the
			// rounding error, chat missed something, so trust the sidebar.
			double board = current - baseline;
			double tolerance = res + baselineRes;
			if (Math.abs(board - chatProfit) > tolerance) chatProfit = board;
		}
	}

	/** Called for every game chat message. Only used when no scoreboard balance is available. */
	public static void onChat(String raw) {
		String msg = normalize(raw);
		String low = msg.toLowerCase();

		Matcher m = AMOUNT.matcher(msg);
		if (!m.find()) return;
		Double v = toNumber(m.group(1), m.group(2));
		if (v == null) return;

		if (low.contains("paid you") || low.contains("sent you") || low.contains("you received")
				|| (low.contains("sold") && low.contains("for"))) {
			chatProfit += v;
		} else if (low.contains("you paid") || low.contains("you sent")) {
			chatProfit -= v;
		}
	}

	// ---------- sidebar reading ----------

	public static List<String> sidebarLines(MinecraftClient client) {
		List<String> lines = new ArrayList<>();
		if (client.world == null) return lines;
		Scoreboard sb = client.world.getScoreboard();
		ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
		if (obj == null) return lines;

		for (ScoreboardEntry entry : sb.getScoreboardEntries(obj)) {
			Team team = sb.getScoreHolderTeam(entry.owner());
			String line = Team.decorateName(team, Text.literal(entry.owner())).getString();
			lines.add(normalize(line));
		}
		return lines;
	}

	static Double readBalance(List<String> lines) {
		// 1) a line that mentions money / balance / bal / cash
		for (String line : lines) {
			String low = line.toLowerCase();
			for (String key : new String[]{"money", "balance", "bal", "cash"}) {
				int idx = low.indexOf(key);
				if (idx >= 0) {
					Double v = parseAny(line.substring(idx + key.length()));
					if (v != null) return v;
				}
			}
		}
		// 2) any line with a $ amount
		for (String line : lines) {
			int idx = line.indexOf('$');
			if (idx >= 0) {
				Double v = parseAny(line.substring(idx));
				if (v != null) return v;
			}
		}
		return null;
	}

	private static Double parseAny(String s) {
		Matcher m = AMOUNT.matcher(s);
		if (m.find()) return toNumber(m.group(1), m.group(2));
		m = LOOSE_AMOUNT.matcher(s);
		if (m.find()) return toNumber(m.group(1), m.group(2));
		return null;
	}

	private static Double toNumber(String num, String suffix) {
		try {
			double v = Double.parseDouble(num.replace(",", ""));
			int decimals = num.contains(".") ? num.length() - num.indexOf('.') - 1 : 0;
			double unit = 1;
			if (suffix != null && !suffix.isEmpty()) {
				switch (Character.toUpperCase(suffix.charAt(0))) {
					case 'K' -> unit = 1_000d;
					case 'M' -> unit = 1_000_000d;
					case 'B' -> unit = 1_000_000_000d;
					case 'T' -> unit = 1_000_000_000_000d;
					default -> {}
				}
			}
			lastRes = unit / Math.pow(10, decimals);
			if (suffix != null && !suffix.isEmpty()) {
				switch (Character.toUpperCase(suffix.charAt(0))) {
					case 'K' -> v *= 1_000d;
					case 'M' -> v *= 1_000_000d;
					case 'B' -> v *= 1_000_000_000d;
					case 'T' -> v *= 1_000_000_000_000d;
					default -> {}
				}
			}
			return v;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

	/** Strips colour codes and converts small-caps letters (used by some servers) to normal ones. */
	static String normalize(String s) {
		s = s.replaceAll("§.", "");
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			int i = SMALL_CAPS.indexOf(c);
			if (i >= 0 && c != 'x') sb.append((char) ('a' + i));
			else sb.append(c);
		}
		return sb.toString().trim();
	}

	// ---------- formatting ----------

	/** Formats 84_000_000 as "+$84M", 1_250_000 as "+$1.25M". */
	public static String format(double value) {
		double abs = Math.abs(value);
		String[] suffixes = {"", "K", "M", "B", "T"};
		int i = 0;
		while (abs >= 1000 && i < suffixes.length - 1) {
			abs /= 1000;
			i++;
		}
		String num = i == 0 ? String.valueOf(Math.round(abs)) : trim(String.format("%.2f", abs));
		String sign = value < 0 ? "-" : "+";
		return sign + "$" + num + suffixes[i];
	}

	private static String trim(String s) {
		if (s.contains(".")) {
			s = s.replaceAll("0+$", "");
			if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	public static String status() {
		return "baseline=" + baseline + " current=" + current + " chatProfit=" + chatProfit;
	}
}
