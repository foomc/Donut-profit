package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Full-screen wheel: one wedge per slot, a pointer at the top, the winner's name when it stops, and an X to close. */
public class WheelScreen extends Screen {
	public static final String[] PALETTE_NAMES = {"Festive", "Rainbow", "Ocean", "Gold", "Neon"};
	private static final int[][] PALETTES = {
			{0xFF2E9E4F, 0xFF8EDDA0, 0xFFF4F1EA, 0xFF1E7A3C, 0xFFD63A3A, 0xFFA8E8B5},
			{},
			{0xFF1D6FA5, 0xFF38B6D8, 0xFFE3F4FA, 0xFF14507A, 0xFF2A9D8F, 0xFF7FD6E8},
			{0xFFE0A526, 0xFFF6D77A, 0xFFFFF3D0, 0xFF9C6A12, 0xFFC98A1B, 0xFFF2C14E},
			{0xFF7A3FE0, 0xFFFF4FA3, 0xFF35E0E8, 0xFF4B24A8, 0xFFB83BE0, 0xFF8FFFE0}
	};
	private static final int[] RIMS = {0xFF3DDC57, 0xFFFFFFFF, 0xFF38B6D8, 0xFFF6D77A, 0xFFFF4FA3};

	private static final float RIM_IN = 0.93f;
	private static final float RIM_OUT = 0.975f;
	private static final float HUB = 0.27f;

	// polar lookup table, rebuilt only when the wheel radius changes
	private static float[] tblU, tblR;
	private static int tblG = -1;

	private static final class Confetti {
		float x, y, vx, vy;
		int col;
	}

	private final Screen parent;
	private ButtonWidget mainBtn;
	private int cx, cy, R;
	private final List<Confetti> confetti = new ArrayList<>();
	private final Random rng = new Random();
	private long lastFrame = 0;
	private int lastUnderPointer = -1;
	private long lastTickSound = 0;

	public WheelScreen(Screen parent) {
		super(Text.literal("Spin the Wheel"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		R = Math.max(48, Math.min(150, (this.height - 122) / 2));
		cx = this.width / 2;
		cy = 56 + R;

		addDrawable((ctx, mx, my, d) -> ctx.fill(0, 0, width, height, 0xEA0A0F1A));
		addDrawable(new WheelDrawable());

		int by = this.height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("Shuffle"), b -> AuctionManager.shuffle())
				.dimensions(cx - 130, by, 74, 20).build());
		mainBtn = addDrawableChild(ButtonWidget.builder(Text.literal("BEGIN"), b -> onMain())
				.dimensions(cx - 50, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Menu"), b -> {
			if (this.client != null) this.client.setScreen(new AuctionScreen());
		}).dimensions(cx + 56, by, 74, 20).build());

		// close (X) in the top right
		final int xx = this.width - 28, xy = 8;
		addDrawableChild(ButtonWidget.builder(Text.empty(), b -> close()).dimensions(xx, xy, 20, 20).build()).setAlpha(0f);
		addDrawable(new CloseVisual(xx, xy, 20, 20));
	}

	private void onMain() {
		switch (AuctionManager.state) {
			case OPEN -> AuctionManager.spin();
			case IDLE, DONE -> AuctionManager.begin();
			default -> {}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(parent);
	}

	// ------------------------------------------------------------------ colours

	private static int colorFor(int pal, int k, int count) {
		if (pal == 1) return Gfx.hsb((float) k / count, 0.75f, 0.95f);
		int[] a = PALETTES[pal];
		int c = a[k % a.length];
		if (count > 1 && k == count - 1 && c == a[0]) c = a[1];
		return c;
	}

	private static void ensureTable(int g) {
		if (tblG == g) return;
		int s = 2 * g + 1;
		tblU = new float[s * s];
		tblR = new float[s * s];
		for (int iy = 0; iy < s; iy++) {
			for (int ix = 0; ix < s; ix++) {
				double dx = ix - g, dy = iy - g;
				double d = Math.sqrt(dx * dx + dy * dy);
				int i = iy * s + ix;
				if (d > g) {
					tblU[i] = -1f;
					tblR[i] = 2f;
					continue;
				}
				double u = Math.atan2(dy, dx) / (2 * Math.PI) + 0.25;   // 0 at the top, clockwise
				u -= Math.floor(u);
				tblU[i] = (float) u;
				tblR[i] = (float) (d / g);
			}
		}
		tblG = g;
	}

	// ------------------------------------------------------------------ drawing

	private final class CloseVisual implements Drawable {
		private final int x, y, w, h;

		CloseVisual(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
			Gfx.rrect(ctx, x, y, w, h, 3, hov ? 0xFFE04848 : 0xFF8E2B2B);
			Gfx.rrect(ctx, x + 1, y + 1, w - 2, h - 2, 2, hov ? 0xFF3A1414 : 0xFF2A1414);
			int col = hov ? 0xFFFFFFFF : 0xFFFF8E8E;
			for (int i = 0; i < 8; i++) {
				ctx.fill(x + 6 + i, y + 6 + i, x + 8 + i, y + 8 + i, col);
				ctx.fill(x + 13 - i, y + 6 + i, x + 15 - i, y + 8 + i, col);
			}
		}
	}

	private final class WheelDrawable implements Drawable {
		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			MinecraftClient mc = MinecraftClient.getInstance();
			TextRenderer tr = mc.textRenderer;
			ProfitConfig cfg = ProfitConfig.get();
			AuctionManager.update();

			long now = System.nanoTime();
			float fdt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
			lastFrame = now;
			float t = (System.currentTimeMillis() % 4000L) / 4000f;

			// main button label / state
			switch (AuctionManager.state) {
				case OPEN -> {
					mainBtn.setMessage(Text.literal("SPIN!").formatted(Formatting.GOLD, Formatting.BOLD));
					mainBtn.active = AuctionManager.totalSlots() > 0;
				}
				case SPINNING -> {
					mainBtn.setMessage(Text.literal("Spinning..."));
					mainBtn.active = false;
				}
				default -> {
					mainBtn.setMessage(Text.literal("BEGIN").formatted(Formatting.GREEN, Formatting.BOLD));
					mainBtn.active = true;
				}
			}

			// ---- title
			String title = "SPIN THE WHEEL";
			float ts = 2.2f;
			int tw = Gfx.textWidth(tr, title, ts);
			Gfx.rainbow(ctx, tr, title, cx - tw / 2f, 10, ts, t, 0.7f);
			Gfx.text(ctx, tr, "★", cx - tw / 2f - 24, 10, ts, 0xFF55FF55, true);
			Gfx.text(ctx, tr, "★", cx + tw / 2f + 8, 10, ts, 0xFFFFD34D, true);

			String me = mc.getSession().getUsername();
			String entry = ProfitTracker.format(cfg.auctionEntry).substring(1);
			String sub = "Entry " + entry + "  •  max " + cfg.auctionMaxSlots + " slots each  •  /pay " + me + " " + entry.substring(1);
			ctx.drawText(tr, sub, cx - tr.getWidth(sub) / 2, 34, 0xFFFFE066, true);
			String prizeLine = "Prize: " + AuctionManager.prizeName();
			ctx.drawText(tr, prizeLine, cx - tr.getWidth(prizeLine) / 2, 45, 0xFFB8C4D8, true);

			// ---- wheel
			int n = AuctionManager.wedges.size();
			boolean empty = n == 0;
			int count = empty ? 12 : n;
			int pal = cfg.auctionPalette;
			int[] cols = new int[count];
			for (int k = 0; k < count; k++) {
				cols[k] = empty ? ((k & 1) == 0 ? 0xFF222B3A : 0xFF1A2230) : colorFor(pal, k, count);
			}
			boolean done = AuctionManager.state == AuctionManager.State.DONE && AuctionManager.winnerIdx >= 0 && !empty;
			float doneAge = done ? (now - AuctionManager.finishedAtNanos) / 1_000_000_000f : 0f;
			if (done) {
				float dim = Math.min(1f, doneAge / 0.6f) * 0.6f;
				float pulse = 0.25f + 0.2f * (float) Math.sin(doneAge * 6f);
				for (int k = 0; k < count; k++) {
					if (k == AuctionManager.winnerIdx) cols[k] = Gfx.mix(cols[k], 0xFFFFFFFF, pulse);
					else cols[k] = Gfx.mix(cols[k], 0xFF0A0F1A, dim);
				}
			}
			float rotFrac = (float) AuctionManager.mod(AuctionManager.rotation, 1.0);
			drawDisk(ctx, cols, count, rotFrac, RIMS[pal]);

			// rim lights
			int bulbs = 28;
			int br = Math.round(R * 0.955f);
			for (int i = 0; i < bulbs; i++) {
				double a = 2 * Math.PI * i / bulbs;
				int bx = cx + (int) Math.round(Math.cos(a) * br);
				int by = cy + (int) Math.round(Math.sin(a) * br);
				boolean on = ((i + (int) (System.currentTimeMillis() / 250)) & 1) == 0;
				ctx.fill(bx - 1, by - 1, bx + 1, by + 1, on ? 0xFFFFF2A8 : 0xFF6A6240);
			}

			// hub with the prize item
			int hubR = Math.round(R * HUB);
			Gfx.disc(ctx, cx, cy, hubR + 2, RIMS[pal]);
			Gfx.disc(ctx, cx, cy, hubR, 0xFF12161E);
			ItemStack prize = AuctionManager.prizeStack();
			if (!prize.isEmpty()) {
				float sc = Math.max(1f, hubR * 1.35f / 16f);
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(cx - 8 * sc, cy - 8 * sc);
				ctx.getMatrices().scale(sc, sc);
				ctx.drawItem(prize, 0, 0);
				ctx.getMatrices().popMatrix();
			}

			// names along the wedges
			if (!empty && R * 0.42 >= n) {
				float arc = (float) (2 * Math.PI * R * 0.6 / n);
				float sc = Math.max(0.5f, Math.min(0.85f, arc / 10f));
				int startX = Math.round(R * 0.32f);
				int maxW = Math.round(R * 0.58f / sc);
				for (int k = 0; k < n; k++) {
					double theta = 2 * Math.PI * ((k + 0.5) / n + rotFrac) - Math.PI / 2;
					String nm = Gfx.fit(tr, AuctionManager.wedges.get(k), maxW);
					boolean light = Gfx.lum(cols[k]) > 0.62f;
					ctx.getMatrices().pushMatrix();
					ctx.getMatrices().translate(cx, cy);
					ctx.getMatrices().rotate((float) theta);
					ctx.getMatrices().translate(startX, -3.5f * sc);
					ctx.getMatrices().scale(sc, sc);
					ctx.drawText(tr, nm, 0, 0, light ? 0xFF10141C : 0xFFFFFFFF, !light);
					ctx.getMatrices().popMatrix();
				}
			}
			if (empty) {
				String w = "Waiting for entries...";
				ctx.drawText(tr, w, cx - tr.getWidth(w) / 2, cy + hubR + 10, 0xFF8D99AD, true);
			}

			// pointer (white outline, red body)
			for (int i = 0; i < 22; i++) {
				int hw = Math.round(10 * (1f - i / 22f));
				ctx.fill(cx - hw - 1, cy - R - 14 + i - 1, cx + hw + 2, cy - R - 14 + i + 1, 0xFFFFFFFF);
			}
			for (int i = 0; i < 20; i++) {
				int hw = Math.round(8 * (1f - i / 20f));
				ctx.fill(cx - hw, cy - R - 13 + i, cx + hw + 1, cy - R - 12 + i, 0xFFE63B3B);
			}

			// tick sound as wedges pass the pointer
			if (!empty && AuctionManager.state == AuctionManager.State.SPINNING) {
				int under = (int) Math.floor(AuctionManager.mod(-AuctionManager.rotation, 1.0) * n);
				if (under != lastUnderPointer && now - lastTickSound > 45_000_000L) {
					AuctionManager.playTick(1.4f);
					lastTickSound = now;
				}
				lastUnderPointer = under;
			}

			// confetti
			if (AuctionManager.justFinished) {
				AuctionManager.justFinished = false;
				for (int i = 0; i < 140; i++) {
					Confetti c = new Confetti();
					c.x = rng.nextInt(Math.max(1, width));
					c.y = -rng.nextInt(80);
					c.vx = (rng.nextFloat() - 0.5f) * 40f;
					c.vy = 60f + rng.nextFloat() * 120f;
					c.col = Gfx.hsb(rng.nextFloat(), 0.8f, 1f);
					confetti.add(c);
				}
			}
			for (Confetti c : confetti) {
				c.x += c.vx * fdt;
				c.y += c.vy * fdt;
				ctx.fill((int) c.x, (int) c.y, (int) c.x + 3, (int) c.y + 2, c.col);
			}
			confetti.removeIf(c -> c.y > height + 10);

			// winner banner
			if (done && AuctionManager.winner != null) drawWinner(ctx, tr, doneAge, t);

			// footer info
			long age = now - AuctionManager.lastEventNanos;
			if (!AuctionManager.lastEvent.isEmpty() && age < 8_000_000_000L) {
				float a = age < 6_000_000_000L ? 1f : 1f - (age - 6_000_000_000L) / 2_000_000_000f;
				String ev = Gfx.fit(tr, AuctionManager.lastEvent, width - 40);
				ctx.drawText(tr, ev, cx - tr.getWidth(ev) / 2, height - 44, Gfx.withAlpha(0xFFE8F0FF, a), true);
			}
			String stat = AuctionManager.slotsBy.size() + " players  •  " + n + " slots";
			ctx.drawText(tr, stat, 10, height - 22, 0xFF8D99AD, true);
		}

		private void drawDisk(DrawContext ctx, int[] cols, int count, float rotFrac, int rim) {
			int cell = count > 90 ? 2 : 1;
			int g = Math.max(8, R / cell);
			ensureTable(g);
			int s = 2 * g + 1;
			int x0 = cx - g * cell;
			int y0 = cy - g * cell;
			int rimDark = Gfx.mix(rim, 0xFF000000, 0.6f);
			for (int iy = 0; iy < s; iy++) {
				int y = y0 + iy * cell;
				int runStart = 0, runCol = 0;
				for (int ix = 0; ix <= s; ix++) {
					int col = 0;
					if (ix < s) {
						int i = iy * s + ix;
						float u = tblU[i];
						if (u >= 0f) {
							float r = tblR[i];
							if (r > RIM_OUT) col = rimDark;
							else if (r > RIM_IN) col = rim;
							else if (r >= HUB * 0.9f) {
								float f = u - rotFrac;
								f -= (float) Math.floor(f);
								int k = (int) (f * count);
								if (k >= count) k = count - 1;
								col = cols[k];
							}
						}
					}
					if (col != runCol) {
						if (runCol != 0) ctx.fill(x0 + runStart * cell, y, x0 + ix * cell, y + cell, runCol);
						runStart = ix;
						runCol = col;
					}
				}
			}
		}

		private void drawWinner(DrawContext ctx, TextRenderer tr, float age, float t) {
			String name = AuctionManager.winner;
			float p = Math.min(1f, age / 0.45f);
			float back = 1f + 2.2f * (float) Math.pow(p - 1f, 3) + 1.2f * (float) Math.pow(p - 1f, 2);   // ease-out-back
			float sc = Math.max(0.01f, 0.5f + 0.5f * back);
			float nameScale = 2.6f;
			int nameW = Gfx.textWidth(tr, name, nameScale);
			String prizeText = "wins " + AuctionManager.prizeName();
			int boxW = Math.max(Math.max(nameW, tr.getWidth(prizeText)) + 60, 190);
			int boxH = 92;

			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(cx, cy);
			ctx.getMatrices().scale(sc, sc);

			int x = -boxW / 2, y = -boxH / 2;
			Gfx.rrect(ctx, x - 2, y - 2, boxW + 4, boxH + 4, 7, Gfx.hsb(t, 0.7f, 1f));
			Gfx.rrect(ctx, x, y, boxW, boxH, 6, 0xF0101622);

			String head = "★ WINNER ★";
			ctx.drawText(tr, head, -tr.getWidth(head) / 2, y + 9, 0xFFFFD34D, true);
			Gfx.rainbow(ctx, tr, name, -nameW / 2f, y + 26, nameScale, t, 0.65f);
			ctx.drawText(tr, prizeText, -tr.getWidth(prizeText) / 2, y + 62, 0xFFE8F0FF, true);
			ItemStack st = AuctionManager.prizeStack();
			if (!st.isEmpty()) ctx.drawItem(st, -8, y + 72);
			ctx.getMatrices().popMatrix();
		}
	}
}
