package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.awt.Color;

/** Rainbow "Profit of the Day" panel. Driven by {@link ProfitConfig}. */
public final class ProfitHud {
	private static final int PAD = 7;
	private static final int BAR = 3;
	private static final float AMOUNT_SCALE = 2.0f;
	private static final String TITLE = "PROFIT OF THE DAY";

	private static double displayed = 0;

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

	public static void render(DrawContext ctx, RenderTickCounter tick) {
		ProfitConfig cfg = ProfitConfig.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!cfg.enabled || mc.options.hudHidden || mc.player == null) return;

		TextRenderer tr = mc.textRenderer;
		double target = ProfitTracker.profit();
		float dt = tick.getDynamicDeltaTicks();
		displayed += (target - displayed) * (1.0 - Math.pow(0.82, dt));
		if (Math.abs(target - displayed) < 0.5) displayed = target;

		String amount = ProfitTracker.format(displayed);
		if (!cfg.showPlusSign && amount.startsWith("+")) amount = amount.substring(1);

		long period = Math.max(500L, (long) (4000L / cfg.rainbowSpeed));
		float time = (System.currentTimeMillis() % period) / (float) period;

		// Layout in unscaled panel units
		int titleW = cfg.showTitle ? tr.getWidth(TITLE) : 0;
		int amountW = Math.round(tr.getWidth(amount) * AMOUNT_SCALE);
		int barW = cfg.showAccentBar ? BAR : 0;
		int w = barW + PAD * 2 + Math.max(titleW, amountW);
		int titleH = cfg.showTitle ? tr.fontHeight + 4 : 0;
		int h = PAD * 2 + titleH + Math.round(tr.fontHeight * AMOUNT_SCALE);

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

		ctx.getMatrices().popMatrix();
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
