package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.awt.Color;

/** Rainbow "Profit of the Day" panel. */
public final class ProfitHud {
	public static boolean visible = true;

	private static final int X = 8;
	private static final int Y = 8;
	private static final int PAD = 7;
	private static final int BAR = 3;
	private static final float AMOUNT_SCALE = 2.0f;
	private static final String TITLE = "PROFIT OF THE DAY";

	private static double displayed = 0;

	private ProfitHud() {}

	private static int rainbow(float hue, float sat, float bri) {
		return 0xFF000000 | Color.HSBtoRGB(((hue % 1f) + 1f) % 1f, sat, bri);
	}

	public static void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!visible || mc.options.hudHidden || mc.player == null) return;

		TextRenderer tr = mc.textRenderer;
		double target = ProfitTracker.profit();
		float dt = tick.getDynamicDeltaTicks();
		displayed += (target - displayed) * (1.0 - Math.pow(0.82, dt));
		if (Math.abs(target - displayed) < 0.5) displayed = target;

		String amount = ProfitTracker.format(displayed);
		float time = (System.currentTimeMillis() % 4000L) / 4000f;

		int titleW = tr.getWidth(TITLE);
		int amountW = Math.round(tr.getWidth(amount) * AMOUNT_SCALE);
		int w = BAR + PAD * 2 + Math.max(titleW, amountW);
		int h = PAD * 2 + tr.fontHeight + 4 + Math.round(tr.fontHeight * AMOUNT_SCALE);

		// Background + animated rainbow border
		roundedFill(ctx, X, Y, w, h, 3, 0xD0101018);
		border(ctx, X, Y, w, h, 3, time);

		// Left rainbow accent bar (scrolling vertically)
		for (int row = 4; row < h - 4; row++) {
			ctx.fill(X + 3, Y + row, X + 3 + BAR, Y + row + 1, rainbow(time - row / (float) h, 0.85f, 1f));
		}

		int tx = X + BAR + PAD + 3;

		// Title, rainbow per character
		int cx = tx;
		int i = 0;
		for (char c : TITLE.toCharArray()) {
			String s = String.valueOf(c);
			ctx.drawText(tr, s, cx, Y + PAD, rainbow(time * 2f - i * 0.045f, 0.7f, 1f), true);
			cx += tr.getWidth(s);
			i++;
		}

		// Amount, big rainbow per character
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(tx, Y + PAD + tr.fontHeight + 4);
		ctx.getMatrices().scale(AMOUNT_SCALE, AMOUNT_SCALE);
		int ax = 0;
		i = 0;
		for (char c : amount.toCharArray()) {
			String s = String.valueOf(c);
			ctx.drawText(tr, s, ax, 0, rainbow(time * 2f - i * 0.07f, 0.8f, 1f), true);
			ax += tr.getWidth(s);
			i++;
		}
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

	/** 1px rainbow border whose hue flows around the perimeter. */
	private static void border(DrawContext ctx, int x, int y, int w, int h, int r, float time) {
		float perim = 2f * (w + h);
		for (int px = r; px < w - r; px++) {
			ctx.fill(x + px, y, x + px + 1, y + 1, rainbow(time - px / perim, 0.9f, 1f));
			ctx.fill(x + px, y + h - 1, x + px + 1, y + h, rainbow(time - (w + h + (w - px)) / perim, 0.9f, 1f));
		}
		for (int py = r; py < h - r; py++) {
			ctx.fill(x + w - 1, y + py, x + w, y + py + 1, rainbow(time - (w + py) / perim, 0.9f, 1f));
			ctx.fill(x, y + py, x + 1, y + py + 1, rainbow(time - (2 * w + h + (h - py)) / perim, 0.9f, 1f));
		}
		// corner pixels
		int c = rainbow(time, 0.9f, 1f);
		for (int k = 1; k < r; k++) {
			ctx.fill(x + r - k, y + k, x + r - k + 1, y + k + 1, c);
			ctx.fill(x + w - r + k - 1, y + k, x + w - r + k, y + k + 1, c);
			ctx.fill(x + r - k, y + h - 1 - k, x + r - k + 1, y + h - k, c);
			ctx.fill(x + w - r + k - 1, y + h - 1 - k, x + w - r + k, y + h - k, c);
		}
	}
}
