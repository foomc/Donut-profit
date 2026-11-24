package com.donutprofit;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/** Small drawing helpers shared by the auction screens. */
final class Gfx {
	private Gfx() {}

	static int hsb(float h, float s, float b) {
		return 0xFF000000 | Color.HSBtoRGB(((h % 1f) + 1f) % 1f, s, b);
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = Math.round(((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		int ar = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int ag = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int ab = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (aa << 24) | (ar << 16) | (ag << 8) | ab;
	}

	static float lum(int c) {
		return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
	}

	static int withAlpha(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
		return (al << 24) | (argb & 0xFFFFFF);
	}

	private static int inset(int k, int h, int r) {
		if (r <= 0) return 0;
		double dy;
		if (k < r) dy = r - k - 0.5;
		else if (k >= h - r) dy = k + 0.5 - (h - r);
		else return 0;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	/** Filled rectangle with circular corners. */
	static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int k = 0; k < h; k++) {
			int in = inset(k, h, r);
			ctx.fill(x + in, y + k, x + w - in, y + k + 1, color);
		}
	}

	/** Filled circle. */
	static void disc(DrawContext ctx, int cx, int cy, int r, int color) {
		rrect(ctx, cx - r, cy - r, r * 2 + 1, r * 2 + 1, r, color);
	}

	static String fit(TextRenderer tr, String s, int maxW) {
		if (maxW <= 0) return "";
		if (tr.getWidth(s) <= maxW) return s;
		String t = s;
		while (t.length() > 1 && tr.getWidth(t + "..") > maxW) t = t.substring(0, t.length() - 1);
		return t + "..";
	}

	/** Text at a fractional position and scale (top-left anchored). */
	static void text(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, int color, boolean shadow) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(scale, scale);
		ctx.drawText(tr, s, 0, 0, color, shadow);
		ctx.getMatrices().popMatrix();
	}

	static int textWidth(TextRenderer tr, String s, float scale) {
		return Math.round(tr.getWidth(s) * scale);
	}

	/** Rainbow text, one colour per character, at a fractional position and scale. */
	static void rainbow(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, float time, float sat) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(scale, scale);
		int cx = 0;
		int i = 0;
		for (char c : s.toCharArray()) {
			String ch = String.valueOf(c);
			ctx.drawText(tr, ch, cx, 0, hsb(time * 2f - i * 0.07f, sat, 1f), true);
			cx += tr.getWidth(ch);
			i++;
		}
		ctx.getMatrices().popMatrix();
	}
}
