package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Wheel auction state. Players pay you while the auction is OPEN; every "entry price" paid is one slot
 * (up to a per-person maximum). SPIN picks one slot at random and the wheel animates to it.
 */
public final class AuctionManager {
	public enum State { IDLE, OPEN, SPINNING, DONE }

	public static final Item[] PRESET_ITEMS = {
			Items.ELYTRA, Items.DRAGON_HEAD, Items.NETHERITE_INGOT, Items.NETHERITE_BLOCK,
			Items.GILDED_BLACKSTONE, Items.ENCHANTED_GOLDEN_APPLE, Items.DEEPSLATE_EMERALD_ORE};
	public static final String[] PRESET_NAMES = {
			"Elytra", "Dragon Head", "Netherite Ingot", "Netherite Block",
			"Gilded Blackstone", "Enchanted Golden Apple", "Deepslate Emerald Ore"};

	public static State state = State.IDLE;
	/** One entry per slot, in wheel order (clockwise from the top at rotation 0). */
	public static final List<String> wedges = new ArrayList<>();
	public static final Map<String, Integer> slotsBy = new LinkedHashMap<>();
	public static final Map<String, Double> paidBy = new LinkedHashMap<>();

	/** Used when the prize is set to "Held" (preset index -1). Not saved. */
	public static ItemStack heldPrize = ItemStack.EMPTY;

	public static double rotation = 0;       // wheel turns (1.0 = one full turn, clockwise)
	public static int winnerIdx = -1;
	public static String winner = null;
	public static long finishedAtNanos = 0;
	public static boolean justFinished = false;
	public static String lastEvent = "";
	public static long lastEventNanos = 0;

	private static long spinStart;
	private static double spinFrom, spinTotal, spinDur;
	private static long lastUpdate = 0;
	private static final Random RNG = new Random();
	private static final ItemStack[] PRESET_STACKS = new ItemStack[PRESET_ITEMS.length];

	private AuctionManager() {}

	// ------------------------------------------------------------------ prize

	public static ItemStack prizeStack() {
		int idx = ProfitConfig.get().auctionPrize;
		if (idx < 0) return heldPrize.isEmpty() ? presetStack(0) : heldPrize;
		idx = Math.min(idx, PRESET_ITEMS.length - 1);
		return presetStack(idx);
	}

	private static ItemStack presetStack(int i) {
		if (PRESET_STACKS[i] == null) PRESET_STACKS[i] = new ItemStack(PRESET_ITEMS[i]);
		return PRESET_STACKS[i];
	}

	/** Name of the item itself (e.g. "Elytra"). */
	public static String baseName() {
		int idx = ProfitConfig.get().auctionPrize;
		if (idx < 0) return heldPrize.isEmpty() ? PRESET_NAMES[0] : heldPrize.getName().getString();
		return PRESET_NAMES[Math.min(idx, PRESET_NAMES.length - 1)];
	}

	/** Custom name if you typed one, otherwise the item's name. */
	public static String prizeName() {
		String custom = ProfitConfig.get().auctionPrizeName;
		return custom == null || custom.isBlank() ? baseName() : custom.trim();
	}

	// ------------------------------------------------------------------ flow

	public static void begin() {
		wedges.clear();
		slotsBy.clear();
		paidBy.clear();
		winner = null;
		winnerIdx = -1;
		state = State.OPEN;
		event("Entries are open!");
	}

	public static void reset() {
		wedges.clear();
		slotsBy.clear();
		paidBy.clear();
		winner = null;
		winnerIdx = -1;
		state = State.IDLE;
	}

	public static void shuffle() {
		if (state == State.SPINNING) return;
		Collections.shuffle(wedges, RNG);
	}

	public static void demo() {
		if (state == State.SPINNING) return;
		if (state != State.OPEN) begin();
		String[] names = {"Steve", "Alex", "Notch", "jeb_", "Dream", "Herobrine", "Technoblade", "Sapnap", "GeorgeNotFound", "Skeppy"};
		for (String n : names) addSlots(n, 1 + RNG.nextInt(3), 0);
		event("Demo players added");
	}

	/** Called for every payment you receive. */
	public static void onPayment(String from, double amount) {
		if (state != State.OPEN) return;
		ProfitConfig c = ProfitConfig.get();
		double cost = Math.max(1, c.auctionEntry);
		int slots = (int) Math.floor(amount / cost + 1e-9);
		if (slots <= 0) {
			event(from + " paid " + ProfitTracker.format(amount).substring(1) + " (below the entry price)");
			return;
		}
		int have = slotsBy.getOrDefault(from, 0);
		int add = Math.min(slots, c.auctionMaxSlots - have);
		if (add <= 0) {
			event(from + " is already at the max slots");
			return;
		}
		addSlots(from, add, amount);
		event(from + " joined with " + add + (add == 1 ? " slot" : " slots"));
	}

	private static void addSlots(String name, int add, double paid) {
		for (int i = 0; i < add; i++) {
			wedges.add(RNG.nextInt(wedges.size() + 1), name);
		}
		slotsBy.merge(name, add, Integer::sum);
		paidBy.merge(name, paid, Double::sum);
	}

	/** Closes entries and starts the spin. Returns false if there is nothing to spin. */
	public static boolean spin() {
		if (state != State.OPEN || wedges.isEmpty()) return false;
		int n = wedges.size();
		winnerIdx = RNG.nextInt(n);
		double jitter = (RNG.nextDouble() - 0.5) * 0.7;               // stop somewhere inside the wedge
		double endFrac = mod(-(winnerIdx + 0.5 + jitter) / n, 1.0);    // wheel angle that puts the wedge under the pointer
		double cur = mod(rotation, 1.0);
		double delta = mod(endFrac - cur, 1.0);
		int turns = 5 + RNG.nextInt(4);
		spinFrom = rotation;
		spinTotal = turns + delta;
		spinDur = Math.max(3, ProfitConfig.get().auctionSpin);
		spinStart = System.nanoTime();
		winner = null;
		state = State.SPINNING;
		event("Spinning...");
		return true;
	}

	/** Advances the wheel. Safe to call as often as you like. */
	public static void update() {
		long now = System.nanoTime();
		double dt = lastUpdate == 0 ? 0.016 : Math.min(0.1, (now - lastUpdate) / 1_000_000_000.0);
		lastUpdate = now;
		if (state == State.SPINNING) {
			double p = Math.min(1.0, (now - spinStart) / 1_000_000_000.0 / spinDur);
			rotation = spinFrom + spinTotal * (1.0 - Math.pow(1.0 - p, 4.0));
			if (p >= 1.0) finish(now);
		} else if (state == State.OPEN || state == State.IDLE) {
			rotation += dt * 0.02;   // slow idle drift
		}
	}

	private static void finish(long now) {
		state = State.DONE;
		winner = winnerIdx >= 0 && winnerIdx < wedges.size() ? wedges.get(winnerIdx) : null;
		finishedAtNanos = now;
		justFinished = true;
		event("Winner: " + winner);
		playWin();
	}

	// ------------------------------------------------------------------ helpers

	public static int totalSlots() {
		return wedges.size();
	}

	public static void event(String s) {
		lastEvent = s;
		lastEventNanos = System.nanoTime();
	}

	public static double mod(double x, double m) {
		return x - Math.floor(x / m) * m;
	}

	private static float volume() {
		return ProfitConfig.get().auctionVolume / 100f;
	}

	public static void playTick(float pitch) {
		float v = volume();
		if (v <= 0f) return;
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch, v));
	}

	public static void playWin() {
		float v = volume();
		if (v <= 0f) return;
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, v));
	}
}
