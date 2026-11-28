package com.donutprofit;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Timed bidding auction. People "bid" by paying you while the auction is RUNNING.
 * Mode 0: payments add up (highest total wins). Mode 1: highest single payment wins.
 * Ties go to whoever reached that amount first.
 */
public final class BidAuctionManager {
	public enum State { IDLE, RUNNING, ENDED }

	public static final class Bid {
		public final String name;
		public double value;
		public long reachedAt;
		public int count;

		Bid(String name) {
			this.name = name;
		}
	}

	public static State state = State.IDLE;
	public static String winner = null;
	public static double winnerValue = 0;
	public static long startNanos, endNanos, finishedAt;
	public static boolean justFinished = false;
	public static String lastEvent = "";
	public static long lastEventNanos = 0;

	/** The prize is copied when the auction starts, so changing the menu mid-auction doesn't change it. */
	public static ItemStack prizeStack = ItemStack.EMPTY;
	public static String prizeName = "";

	private static final Map<String, Bid> BIDS = new HashMap<>();
	private static int lastBeepSecond = -1;

	public static final int SNIPE_SECONDS = 10;

	private BidAuctionManager() {}

	// ------------------------------------------------------------------ flow

	public static void start() {
		ProfitConfig c = ProfitConfig.get();
		BIDS.clear();
		winner = null;
		winnerValue = 0;
		prizeStack = AuctionManager.prizeStack().copy();
		prizeName = AuctionManager.prizeName();
		startNanos = System.nanoTime();
		endNanos = startNanos + (long) c.bidDuration * 1_000_000_000L;
		lastBeepSecond = -1;
		state = State.RUNNING;
		event("Bidding is open! Starting bid " + money(c.bidStart));
	}

	public static void endNow() {
		if (state == State.RUNNING) finish();
	}

	public static void reset() {
		BIDS.clear();
		winner = null;
		winnerValue = 0;
		state = State.IDLE;
	}

	/** Called for every payment you receive. */
	public static void onPayment(String from, double amount) {
		if (state != State.RUNNING) return;
		ProfitConfig c = ProfitConfig.get();
		if (amount < c.bidStart) {
			event(from + " paid " + money(amount) + " (below the starting bid)");
			return;
		}
		Bid b = BIDS.computeIfAbsent(from, Bid::new);
		String before = leaderName();
		double newValue = c.bidMode == 1 ? Math.max(b.value, amount) : b.value + amount;
		if (c.bidMode == 1 && newValue <= b.value) {
			event(from + " paid " + money(amount) + " (not higher than their best)");
			return;
		}
		b.value = newValue;
		b.reachedAt = System.nanoTime();
		b.count++;

		String after = leaderName();
		String msg;
		if (from.equals(after) && !from.equals(before)) msg = from + " takes the lead with " + money(b.value) + "!";
		else msg = from + " bid " + money(b.value);

		long now = System.nanoTime();
		if (c.bidAntiSnipe && endNanos - now < SNIPE_SECONDS * 1_000_000_000L) {
			endNanos = now + SNIPE_SECONDS * 1_000_000_000L;
			msg += "  (+" + SNIPE_SECONDS + "s)";
		}
		event(msg);
		AuctionManager.playTick(from.equals(after) ? 1.6f : 1.0f);
	}

	/** Advances the countdown. Safe to call every tick and every frame. */
	public static void update() {
		if (state != State.RUNNING) return;
		long now = System.nanoTime();
		long remNanos = endNanos - now;
		int sec = (int) Math.ceil(remNanos / 1_000_000_000.0);
		if (sec <= 5 && sec > 0 && sec != lastBeepSecond) {
			lastBeepSecond = sec;
			AuctionManager.playTick(1.9f);
		}
		if (remNanos <= 0) finish();
	}

	private static void finish() {
		Bid l = leader();
		state = State.ENDED;
		winner = l == null ? null : l.name;
		winnerValue = l == null ? 0 : l.value;
		finishedAt = System.nanoTime();
		justFinished = true;
		event(winner == null ? "Auction ended with no bids" : "SOLD to " + winner + " for " + money(winnerValue) + "!");
		if (winner != null) AuctionManager.playWin();
	}

	// ------------------------------------------------------------------ queries

	public static Bid leader() {
		Bid best = null;
		for (Bid b : BIDS.values()) {
			if (best == null || b.value > best.value || (b.value == best.value && b.reachedAt < best.reachedAt)) best = b;
		}
		return best;
	}

	private static String leaderName() {
		Bid l = leader();
		return l == null ? null : l.name;
	}

	public static List<Bid> top(int n) {
		List<Bid> l = new ArrayList<>(BIDS.values());
		l.sort(Comparator.<Bid>comparingDouble(b -> -b.value).thenComparingLong(b -> b.reachedAt));
		return l.size() > n ? l.subList(0, n) : l;
	}

	public static int bidderCount() {
		return BIDS.size();
	}

	public static double remainingSeconds() {
		if (state != State.RUNNING) return 0;
		return Math.max(0, (endNanos - System.nanoTime()) / 1_000_000_000.0);
	}

	// ------------------------------------------------------------------ helpers

	public static void event(String s) {
		lastEvent = s;
		lastEventNanos = System.nanoTime();
	}

	/** 84_000_000 -> "$84M" */
	public static String money(double v) {
		return ProfitTracker.format(v).substring(1);
	}

	/** 125 -> "2:05" */
	public static String clock(double seconds) {
		int s = (int) Math.ceil(seconds);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}
}
