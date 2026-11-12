package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads your balance off the sidebar scoreboard and tracks profit since the game was launched. */
public final class ProfitTracker {
	private static final Pattern NUMBER = Pattern.compile("\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBbTt])?");

	private static boolean hasBaseline = false;
	private static double baseline = 0;
	private static double current = 0;
	private static int ticks = 0;

	private ProfitTracker() {}

	public static void reset() {
		hasBaseline = false;
		baseline = 0;
		current = 0;
	}

	/** Profit since the game was launched (or since /profit reset). */
	public static double profit() {
		return hasBaseline ? current - baseline : 0;
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) return;
		if (++ticks % 10 != 0) return;

		Double balance = readBalance(client);
		if (balance == null) return;

		current = balance;
		if (!hasBaseline) {
			baseline = balance;
			hasBaseline = true;
		}
	}

	private static Double readBalance(MinecraftClient client) {
		Scoreboard sb = client.world.getScoreboard();
		ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
		if (obj == null) return null;

		List<String> lines = new ArrayList<>();
		for (ScoreboardEntry entry : sb.getScoreboardEntries(obj)) {
			Team team = sb.getScoreHolderTeam(entry.owner());
			String line = Team.decorateName(team, Text.literal(entry.owner())).getString();
			lines.add(strip(line));
		}

		// Prefer a line mentioning "money", fall back to any line with a $ amount.
		for (String line : lines) {
			if (line.toLowerCase().contains("money")) {
				Double v = parse(line.substring(line.toLowerCase().indexOf("money") + 5));
				if (v != null) return v;
			}
		}
		for (String line : lines) {
			if (line.contains("$")) {
				Double v = parse(line.substring(line.indexOf('$')));
				if (v != null) return v;
			}
		}
		return null;
	}

	private static String strip(String s) {
		return s.replaceAll("§.", "").trim();
	}

	/** Parses things like "$1,234,567", "$84M", "$1.25B". */
	static Double parse(String s) {
		Matcher m = NUMBER.matcher(s);
		if (!m.find()) return null;
		try {
			double v = Double.parseDouble(m.group(1).replace(",", ""));
			String suffix = m.group(2);
			if (suffix != null) {
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

	/** Formats 84_000_000 as "$84M", 1_250_000 as "$1.25M". */
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
}
