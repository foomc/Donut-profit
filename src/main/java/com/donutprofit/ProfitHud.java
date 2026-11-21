package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.awt.Color;
import java.util.List;

/** Rainbow "Profit of the Day" panel with the payments list attached underneath. Driven by {@link ProfitConfig}. */
public final class ProfitHud {
	private static final int PAD = 7;
	private static final int BAR = 3;
	private static final float AMOUNT_SCALE = 2.0f;
	private static final String TITLE = "PROFIT OF THE DAY";

	// payments panel layout (unscaled units)
	private static final int GAP = 3;
	private static final int ROW_H = 15;
	private static final int ROWS_TOP = 19;
	private static final int BOX = 9;
	private static final int[][] TICK = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}};
	private static final int GREEN = 0xFF3DDC57;

	private static double displayed = 0;
	private static float rowsAnim = 0f;
	private static long lastScreenFrame = 0;
	private static int hoverX = -10000, hoverY = -10000;

	private ProfitHud() {}

	private static int rainbow(float hue, float sat, float bri) {
		return 0xFF000000 | Color.HSBtoRGB(((hue % 1f) + 1f) % 1f, sat, bri);
	}

	/** Colour for a point at phase `phase`: rainbow, or a flat colour with a gentle shimmer. */
	private static int color(ProfitConfig cfg, float phase, float sat, float bri) {
		if (cfg.colorMode == 0) return rainbow(phase, sat, bri);
		int base = ProfitConfig.COLOR_VALUES[cfg.colorMode];
		float shimmer = 0.88f + 0.12f * (float) Math.sin(phase * Math.PI * 2);
		int r = Math.min(255, Math.round(((base >> 16) & 0xFF) * shimmer));
		int g = Math.min(255, Math.round(((base >> 8) & 0xFF) * shimmer));
		int b = Math.min(255, Math.round((base & 0xFF) * shimmer));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int fade(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
		return (al << 24) | (argb & 0xFFFFFF);
	}

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int ag = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int ab = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | (ar << 16) | (ag << 8) | ab;
	}

	/** HUD layer callback (no screen open). Screens draw the HUD through {@link #drawOnScreen}. */
	public static void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden || mc.player == null) return;
		if (mc.currentScreen != null) return;
		hoverX = -10000;
		hoverY = -10000;
		draw(ctx, tick.getDynamicDeltaTicks(), false);
	}

	/** Draws the HUD over any open screen (chat, inventory...) and lets you click payment checkboxes. */
	public static void drawOnScreen(DrawContext ctx, int mouseX, int mouseY) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden || mc.player == null) return;
		if (mc.currentScreen instanceof ProfitConfigScreen) return;
		long now = System.nanoTime();
		float dtTicks = lastScreenFrame == 0 ? 1f : Math.min(5f, (now - lastScreenFrame) / 50_000_000f);
		lastScreenFrame = now;
		hoverX = mouseX;
		hoverY = mouseY;
		draw(ctx, dtTicks, false);
		ProfitPayments.pollClick(mouseX, mouseY);
	}

	/** Live preview used by the settings menu. */
	public static void draw(DrawContext ctx, float dt) {
		hoverX = -10000;
		hoverY = -10000;
		draw(ctx, dt, true);
	}

	private static void draw(DrawContext ctx, float dt, boolean preview) {
		ProfitConfig cfg = ProfitConfig.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!cfg.enabled || mc.player == null) return;

		TextRenderer tr = mc.textRenderer;
		double target = ProfitTracker.profit();
		displayed += (target - displayed) * (1.0 - Math.pow(0.82, dt));
		if (Math.abs(target - displayed) < 0.5) displayed = target;

		String amount = ProfitTracker.format(displayed);
		if (!cfg.showPlusSign && amount.startsWith("+")) amount = amount.substring(1);

		long period = Math.max(500L, (long) (4000L / cfg.rainbowSpeed));
		float time = (System.currentTimeMillis() % period) / (float) period;

		// ---- main panel layout (unscaled units)
		int titleW = cfg.showTitle ? tr.getWidth(TITLE) : 0;
		int amountW = Math.round(tr.getWidth(amount) * AMOUNT_SCALE);
		int barW = cfg.showAccentBar ? BAR : 0;
		int mainW = barW + PAD * 2 + Math.max(titleW, amountW);
		int titleH = cfg.showTitle ? tr.fontHeight + 4 : 0;
		int h = PAD * 2 + titleH + Math.round(tr.fontHeight * AMOUNT_SCALE);

		// ---- payments: assign slots
		ProfitPayments.update();
		List<ProfitPayments.Payment> list = cfg.showPayments ? ProfitPayments.list(preview) : List.of();
		boolean sample = ProfitPayments.isSample(list);
		int maxRows = Math.max(1, cfg.paymentRows);
		int slot = 0, hidden = 0, sliding = 0, amtMaxW = 0;
		for (ProfitPayments.Payment p : list) {
			p.hitValid = false;
			if (p.slide() > 0f) {
				p.slot = p.slot < 0 ? 0 : p.slot;
				sliding++;
			} else if (slot < maxRows) {
				p.slot = slot++;
			} else {
				p.slot = -1;
				hidden++;
			}
			if (p.slot >= 0) amtMaxW = Math.max(amtMaxW, tr.getWidth("+" + ProfitTracker.format(p.amount).replace("+", "")));
		}
		int shown = slot;
		rowsAnim += (shown - rowsAnim) * (1f - (float) Math.pow(0.65, dt));
		if (Math.abs(shown - rowsAnim) < 0.02f) rowsAnim = shown;
		boolean showPay = cfg.showPayments && (rowsAnim > 0.02f || shown > 0 || sliding > 0);

		int w = mainW;
		if (showPay) w = Math.max(mainW, 8 + BOX + 6 + 40 + 10 + amtMaxW + 8);
		int payH = ROWS_TOP + Math.round(rowsAnim * ROW_H) + (hidden > 0 ? 11 : 0) + 5;

		float s = cfg.scale;
		int sw = ctx.getScaledWindowWidth();
		float px;
		switch (cfg.anchor) {
			case 1 -> px = cfg.offsetX + 6;                     // top left
			case 2 -> px = sw - w * s - 6 + cfg.offsetX;        // top right
			default -> px = (sw - w * s) / 2f + cfg.offsetX;    // top center
		}
		float py = cfg.offsetY;

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(px, py);
		ctx.getMatrices().scale(s, s);

		int alpha = Math.round(255 * cfg.backgroundOpacity / 100f);
		roundedFill(ctx, 0, 0, w, h, 3, (alpha << 24) | 0x101018);
		if (cfg.showBorder) border(ctx, cfg, 0, 0, w, h, 3, time);

		if (cfg.showAccentBar) {
			for (int row = 4; row < h - 4; row++) {
				ctx.fill(3, row, 3 + BAR, row + 1, color(cfg, time - row / (float) h, 0.85f, 1f));
			}
		}

		int tx = barW + PAD + (cfg.showAccentBar ? 3 : 0);
		int ty = PAD;

		if (cfg.showTitle) {
			int cx = tx;
			int i = 0;
			for (char c : TITLE.toCharArray()) {
				String ch = String.valueOf(c);
				ctx.drawText(tr, ch, cx, ty, color(cfg, time * 2f - i * 0.045f, 0.7f, 1f), true);
				cx += tr.getWidth(ch);
				i++;
			}
			ty += tr.fontHeight + 4;
		}

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(tx, ty);
		ctx.getMatrices().scale(AMOUNT_SCALE, AMOUNT_SCALE);
		int ax = 0;
		int i = 0;
		for (char c : amount.toCharArray()) {
			String ch = String.valueOf(c);
			ctx.drawText(tr, ch, ax, 0, color(cfg, time * 2f - i * 0.07f, 0.8f, 1f), true);
			ax += tr.getWidth(ch);
			i++;
		}
		ctx.getMatrices().popMatrix();

		// ---- payments panel
		if (showPay) {
			int top = h + GAP;
			roundedFill(ctx, 0, top, w, payH, 3, (alpha << 24) | 0x101018);
			if (cfg.showBorder) border(ctx, cfg, 0, top, w, payH, 3, time + 0.12f);

			int hx = 8;
			int n = 0;
			for (char c : "PAYMENTS".toCharArray()) {
				String ch = String.valueOf(c);
				ctx.drawText(tr, ch, hx, top + 5, color(cfg, time * 2f - n * 0.06f + 0.3f, 0.7f, 1f), true);
				hx += tr.getWidth(ch);
				n++;
			}
			int total = shown + hidden;
			if (total > 0) {
				String cnt = String.valueOf(total);
				ctx.drawText(tr, cnt, w - 8 - tr.getWidth(cnt), top + 5, 0xFF8D8D96, true);
			}
			ctx.fill(8, top + 16, w - 8, top + 17, 0x30FFFFFF);

			for (ProfitPayments.Payment p : list) {
				drawRow(ctx, cfg, tr, p, w, top, time, dt, s, px, py, sample);
			}
			if (hidden > 0) {
				ctx.drawText(tr, "+" + hidden + " more", 8, top + ROWS_TOP + Math.round(rowsAnim * ROW_H) + 1, 0xFF8D8D96, true);
			}
		}

		ctx.getMatrices().popMatrix();
	}

	private static void drawRow(DrawContext ctx, ProfitConfig cfg, TextRenderer tr, ProfitPayments.Payment p,
								int w, int top, float time, float dt, float s, float px, float py, boolean sample) {
		if (p.slot < 0) return;
		float targetY = p.slot * ROW_H;
		if (p.yPos < 0f) p.yPos = targetY;
		else p.yPos += (targetY - p.yPos) * (1f - (float) Math.pow(0.6, dt));

		float slide = p.slide();
		float tick = p.tick();
		float ap = 1f - (1f - p.appear) * (1f - p.appear);
		float e = slide * slide;
		float a = ap * (1f - slide);
		if (a <= 0.01f) return;

		int xOff = Math.round((1f - ap) * -12f + e * (w + 10));
		int ry = top + ROWS_TOP + Math.round(p.yPos);
		int bx = 8 + xOff;
		int by = ry + (ROW_H - BOX) / 2;

		// checkbox hit area in screen coordinates
		boolean hovered = false;
		if (!sample && !p.checked && slide == 0f && p.appear > 0.5f) {
			p.hx0 = Math.round(px + bx * s);
			p.hy0 = Math.round(py + by * s);
			p.hx1 = Math.round(px + (bx + BOX) * s);
			p.hy1 = Math.round(py + (by + BOX) * s);
			p.hitValid = true;
			hovered = hoverX >= p.hx0 - 3 && hoverX <= p.hx1 + 3 && hoverY >= p.hy0 - 3 && hoverY <= p.hy1 + 3;
		}

		// checkbox: outline, dark inside, green fill + drawn-in tick when ticked
		int outline = mix(color(cfg, time - ry * 0.01f, 0.7f, 1f), GREEN, tick);
		if (hovered) outline = mix(outline, 0xFFFFFFFF, 0.35f);
		ctx.fill(bx, by, bx + BOX, by + BOX, fade(outline, a));
		int inner = hovered ? 0xFF2C2C3A : 0xFF14141C;
		ctx.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, fade(inner, a));
		if (tick > 0f) {
			ctx.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, fade(GREEN, a * tick));
			int pts = (int) Math.ceil(tick * TICK.length);
			for (int k = 0; k < pts; k++) {
				ctx.fill(bx + TICK[k][0], by + TICK[k][1] - 1, bx + TICK[k][0] + 1, by + TICK[k][1] + 1, fade(0xFFFFFFFF, a));
			}
		}

		// name (left) and amount (right)
		String amt = ProfitTracker.format(p.amount);
		int amtW = tr.getWidth(amt);
		int nameX = bx + BOX + 6;
		int availName = w - 8 - amtW - 10 - (8 + BOX + 6);
		String name = fit(tr, p.from, availName);
		int ty = ry + (ROW_H - 8) / 2;
		int nameCol = mix(0xFFE8E8F0, 0xFF7A7A86, tick);
		ctx.drawText(tr, name, nameX, ty, fade(nameCol, a), true);
		if (tick > 0f) {
			int lw = Math.round(tr.getWidth(name) * tick);
			ctx.fill(nameX, ty + 4, nameX + lw, ty + 5, fade(0xFFB0B0BA, a));
		}
		int amtCol = mix(0xFF55FF55, 0xFF7A9A7E, tick);
		ctx.drawText(tr, amt, w - 8 - amtW + xOff, ty, fade(amtCol, a), true);
	}

	private static String fit(TextRenderer tr, String s, int maxW) {
		if (tr.getWidth(s) <= maxW) return s;
		String t = s;
		while (t.length() > 1 && tr.getWidth(t + "..") > maxW) t = t.substring(0, t.length() - 1);
		return t + "..";
	}

	/** Filled rectangle with the corners clipped. */
	private static void roundedFill(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
		for (int k = 0; k < h; k++) {
			int inset = 0;
			if (k < r) inset = r - k;
			else if (k >= h - r) inset = r - (h - 1 - k);
			ctx.fill(x + inset, y + k, x + w - inset, y + k + 1, color);
		}
	}

	/** 1px border whose colour flows around the perimeter. */
	private static void border(DrawContext ctx, ProfitConfig cfg, int x, int y, int w, int h, int r, float time) {
		float perim = 2f * (w + h);
		for (int px = r; px < w - r; px++) {
			ctx.fill(x + px, y, x + px + 1, y + 1, color(cfg, time - px / perim, 0.9f, 1f));
			ctx.fill(x + px, y + h - 1, x + px + 1, y + h, color(cfg, time - (w + h + (w - px)) / perim, 0.9f, 1f));
		}
		for (int py = r; py < h - r; py++) {
			ctx.fill(x + w - 1, y + py, x + w, y + py + 1, color(cfg, time - (w + py) / perim, 0.9f, 1f));
			ctx.fill(x, y + py, x + 1, y + py + 1, color(cfg, time - (2 * w + h + (h - py)) / perim, 0.9f, 1f));
		}
		int c = color(cfg, time, 0.9f, 1f);
		for (int k = 1; k < r; k++) {
			ctx.fill(x + r - k, y + k, x + r - k + 1, y + k + 1, c);
			ctx.fill(x + w - r + k - 1, y + k, x + w - r + k, y + k + 1, c);
			ctx.fill(x + r - k, y + h - 1 - k, x + r - k + 1, y + h - k, c);
			ctx.fill(x + w - r + k - 1, y + h - 1 - k, x + w - r + k, y + h - k, c);
		}
	}
}
