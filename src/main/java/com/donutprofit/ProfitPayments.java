package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Payments you have received, shown under the profit panel until you tick them off. In memory only. */
public final class ProfitPayments {
	/** Seconds: tick draws in, short pause, then the row slides away. */
	static final float TICK_TIME = 0.20f;
	static final float HOLD = 0.30f;
	static final float SLIDE_TIME = 0.45f;
	private static final int MAX_STORED = 50;

	public static final class Payment {
		public final String from;
		public final double amount;
		boolean checked;
		float t;                 // seconds since it was ticked
		float appear;            // 0..1 slide/fade in
		float yPos = -1f;        // animated row position
		int slot = -1;           // target slot, -1 = not shown
		boolean hitValid;
		int hx0, hy0, hx1, hy1;  // checkbox rectangle in screen coordinates

		Payment(String from, double amount, float appear) {
			this.from = from;
			this.amount = amount;
			this.appear = appear;
		}

		float tick() {
			return checked ? Math.min(1f, t / TICK_TIME) : 0f;
		}

		float slide() {
			return checked ? Math.max(0f, Math.min(1f, (t - TICK_TIME - HOLD) / SLIDE_TIME)) : 0f;
		}
	}

	private static final List<Payment> LIST = new ArrayList<>();
	private static final List<Payment> SAMPLE = List.of(
			new Payment("Steve", 5_000_000, 1f),
			new Payment("Alex", 250_000, 1f));

	private static long last = 0;
	private static boolean wasDown = false;

	private ProfitPayments() {}

	public static void add(String from, double amount) {
		LIST.add(0, new Payment(from, amount, 0f));
		while (LIST.size() > MAX_STORED) LIST.remove(LIST.size() - 1);
	}

	public static void clear() {
		LIST.clear();
	}

	public static boolean hasAny() {
		return !LIST.isEmpty();
	}

	/** Real payments, or two sample rows when previewing in the settings menu with nothing received yet. */
	public static List<Payment> list(boolean preview) {
		if (!LIST.isEmpty()) return LIST;
		return preview ? SAMPLE : LIST;
	}

	public static boolean isSample(List<Payment> l) {
		return l == SAMPLE;
	}

	/** Advances animations; call once per drawn frame. */
	public static void update() {
		long now = System.nanoTime();
		float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1_000_000_000f);
		last = now;
		for (Payment p : LIST) {
			p.appear = Math.min(1f, p.appear + dt / 0.25f);
			if (p.checked) p.t += dt;
		}
		LIST.removeIf(p -> p.checked && p.slide() >= 1f);
	}

	/** Ticks the payment whose checkbox is under the mouse. */
	public static boolean click(int mx, int my) {
		for (Payment p : LIST) {
			if (p.checked || !p.hitValid) continue;
			if (mx >= p.hx0 - 3 && mx <= p.hx1 + 3 && my >= p.hy0 - 3 && my <= p.hy1 + 3) {
				p.checked = true;
				p.t = 0f;
				return true;
			}
		}
		return false;
	}

	/** Detects a left-click while any screen is open (chat, inventory, the payments screen...). */
	public static void pollClick(int mx, int my) {
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (down && !wasDown) click(mx, my);
		wasDown = down;
	}
}
