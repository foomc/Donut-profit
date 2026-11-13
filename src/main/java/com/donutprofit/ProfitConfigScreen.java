package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Settings menu: a custom-drawn tabbed panel with animated switches and sliders.
 * The real HUD is drawn on top of the panel, so every change previews live.
 */
public class ProfitConfigScreen extends Screen {
	private static final int PW = 330;
	private static final int PH = 240;
	private static final int PAD = 16;
	private static final int ROW_H = 24;
	private static final int PITCH = 27;
	private static final String[] TABS = {"Layout", "Style", "Elements", "Tracking"};

	private static int lastTab = 0;

	// shared animation state (updated once per frame by the panel drawable)
	private static float FADE = 0f;
	private static float DT = 0.016f;
	private static long lastFrame = 0;
	private static long openNanos = 0;

	private final Screen parent;
	private int tab = lastTab;
	private int px, py;

	public ProfitConfigScreen(Screen parent) {
		super(Text.literal("Donut Profit Settings"));
		this.parent = parent;
		openNanos = System.nanoTime();
		lastFrame = 0;
		FADE = 0f;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		ProfitConfig c = ProfitConfig.get();
		px = (this.width - PW) / 2;
		py = Math.max(4, (this.height - PH) / 2);

		addDrawable(new PanelDrawable());

		// tabs
		int gap = 4;
		int tw = (PW - PAD * 2 - gap * (TABS.length - 1)) / TABS.length;
		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			addDrawableChild(new TabButton(px + PAD + i * (tw + gap), py + 40, tw, 20, TABS[i], idx, () -> tab == idx, () -> {
				tab = idx;
				lastTab = idx;
				this.clearAndInit();
			}));
		}

		int x = px + PAD;
		int w = PW - PAD * 2;
		int y = py + 68;

		switch (tab) {
			case 0 -> {
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "HUD Enabled", () -> c.enabled, v -> c.enabled = v)); y += PITCH;
				addDrawableChild(new CycleRow(x, y, w, ROW_H, "Position", ProfitConfig.ANCHORS, () -> c.anchor, v -> c.anchor = v, null)); y += PITCH;
				addDrawableChild(new SliderRow(x, y, w, ROW_H, "X Offset", -300, 300, c.offsetX, v -> c.offsetX = (int) Math.round(v), "px")); y += PITCH;
				addDrawableChild(new SliderRow(x, y, w, ROW_H, "Y Offset", 0, 300, c.offsetY, v -> c.offsetY = (int) Math.round(v), "px")); y += PITCH;
				addDrawableChild(new SliderRow(x, y, w, ROW_H, "Size", 50, 250, c.scale * 100.0, v -> c.scale = v.floatValue() / 100f, "%"));
			}
			case 1 -> {
				addDrawableChild(new CycleRow(x, y, w, ROW_H, "Colour", ProfitConfig.COLOR_MODES, () -> c.colorMode, v -> c.colorMode = v,
						() -> c.colorMode == 0 ? hsb(time(), 0.8f, 1f) : (0xFF000000 | ProfitConfig.COLOR_VALUES[c.colorMode]))); y += PITCH;
				addDrawableChild(new SliderRow(x, y, w, ROW_H, "Background", 0, 100, c.backgroundOpacity, v -> c.backgroundOpacity = (int) Math.round(v), "%")); y += PITCH;
				addDrawableChild(new SliderRow(x, y, w, ROW_H, "Animation Speed", 10, 400, c.rainbowSpeed * 100.0, v -> c.rainbowSpeed = v.floatValue() / 100f, "%"));
			}
			case 2 -> {
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "Title", () -> c.showTitle, v -> c.showTitle = v)); y += PITCH;
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "Border", () -> c.showBorder, v -> c.showBorder = v)); y += PITCH;
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "Accent Bar", () -> c.showAccentBar, v -> c.showAccentBar = v)); y += PITCH;
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "Plus Sign", () -> c.showPlusSign, v -> c.showPlusSign = v));
			}
			default -> {
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "Count Sales", () -> c.countSales, v -> c.countSales = v)); y += PITCH;
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "Count Payments", () -> c.countPayments, v -> c.countPayments = v)); y += PITCH;
				addDrawableChild(new ToggleRow(x, y, w, ROW_H, "Sync With Sidebar", () -> c.syncWithSidebar, v -> c.syncWithSidebar = v));
			}
		}

		// footer
		int fy = py + PH - 32;
		int fg = 6;
		int fw = (w - fg * 2) / 3;
		addDrawableChild(new ActionButton(x, fy, fw, 20, "Reset Profit", 2, ProfitTracker::reset));
		addDrawableChild(new ActionButton(x + fw + fg, fy, fw, 20, "Defaults", 0, () -> {
			ProfitConfig.resetToDefaults();
			this.clearAndInit();
		}));
		addDrawableChild(new ActionButton(x + (fw + fg) * 2, fy, fw, 20, "Done", 1, this::close));

		// live HUD preview, drawn last so it sits on top of the panel
		addDrawable((ctx, mx, my, d) -> ProfitHud.draw(ctx, 0.5f));
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, this.width, this.height, ((int) (0x55 * FADE) << 24));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		ProfitConfig.save();
	}

	@Override
	public void close() {
		ProfitConfig.save();
		if (this.client != null) this.client.setScreen(parent);
	}

	// ------------------------------------------------------------------ panel

	private final class PanelDrawable implements Drawable {
		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			long now = System.nanoTime();
			DT = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
			lastFrame = now;
			float t = Math.min(1f, (now - openNanos) / 1_000_000_000f / 0.22f);
			FADE = 1f - (1f - t) * (1f - t) * (1f - t);

			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(0f, (1f - FADE) * 10f);

			// soft shadow
			for (int i = 7; i >= 1; i--) {
				rrect(ctx, px - i, py - i, PW + i * 2, PH + i * 2, 8 + i, 0x0C000000);
			}
			// border (hue breathes) + body
			rrect(ctx, px, py, PW, PH, 8, hsb(time(), 0.7f, 1f));
			rrect(ctx, px + 1, py + 1, PW - 2, PH - 2, 7, 0xFF14141E);
			// flowing highlight along the top edge
			for (int i = 8; i < PW - 8; i++) {
				ctx.fill(px + i, py, px + i + 1, py + 1, fa(hsb(time() - i / (float) PW, 0.8f, 1f)));
			}

			TextRenderer tr = tr();
			// title (rainbow, 1.5x)
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(px + PAD, py + 11);
			ctx.getMatrices().scale(1.5f, 1.5f);
			int cx = 0;
			int i = 0;
			for (char ch : "DONUT PROFIT".toCharArray()) {
				String s = String.valueOf(ch);
				ctx.drawText(tr, s, cx, 0, fa(accent(time() * 2f - i * 0.06f)), true);
				cx += tr.getWidth(s);
				i++;
			}
			ctx.getMatrices().popMatrix();
			ctx.drawText(tr, "Settings  ·  saved automatically", px + PAD, py + 29, fa(0xFF8A8A9C), false);

			// divider under tabs
			ctx.fill(px + PAD, py + 63, px + PW - PAD, py + 64, fa(0x22FFFFFF));

			if (tab == 3) {
				ctx.drawText(tr, "Sales and payments are read from chat messages about you.", px + PAD, py + 68 + PITCH * 3 + 2, fa(0xFF8A8A9C), false);
				ctx.drawText(tr, "Sync With Sidebar corrects any drift using your balance.", px + PAD, py + 68 + PITCH * 3 + 14, fa(0xFF8A8A9C), false);
			}
			ctx.getMatrices().popMatrix();
		}
	}

	// ------------------------------------------------------------------ widgets

	private static final class ToggleRow extends ButtonWidget {
		private final String label;
		private final Supplier<Boolean> get;
		private float hover = 0f;
		private float on;

		ToggleRow(int x, int y, int w, int h, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
			super(x, y, w, h, Text.literal(label), b -> set.accept(!get.get()), DEFAULT_NARRATION_SUPPLIER);
			this.label = label;
			this.get = get;
			this.on = get.get() ? 1f : 0f;
		}

		@Override
		public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
			hover = approach(hover, isHovered() ? 1f : 0f);
			on = approach(on, get.get() ? 1f : 0f);
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(0f, (1f - FADE) * 10f);

			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			rowBg(ctx, x, y, w, h, hover);
			ctx.drawText(tr(), label, x + 10, y + (h - 8) / 2, fa(0xFFE8E8F0), false);

			int sw = 32, sh = 14;
			int sx = x + w - sw - 10, sy = y + (h - sh) / 2;
			int track = mix(0xFF3A3A4A, accent(time()), on);
			rrect(ctx, sx, sy, sw, sh, 7, track);
			int kx = sx + 2 + Math.round(on * (sw - 14));
			rrect(ctx, kx, sy + 2, 10, 10, 5, 0xFFFFFFFF);

			ctx.getMatrices().popMatrix();
		}
	}

	private static final class CycleRow extends ButtonWidget {
		private final String label;
		private final String[] options;
		private final Supplier<Integer> get;
		private final IntSupplier dot;
		private float hover = 0f;

		CycleRow(int x, int y, int w, int h, String label, String[] options, Supplier<Integer> get, Consumer<Integer> set, IntSupplier dot) {
			super(x, y, w, h, Text.literal(label), b -> set.accept((get.get() + 1) % options.length), DEFAULT_NARRATION_SUPPLIER);
			this.label = label;
			this.options = options;
			this.get = get;
			this.dot = dot;
		}

		@Override
		public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
			hover = approach(hover, isHovered() ? 1f : 0f);
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(0f, (1f - FADE) * 10f);

			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			TextRenderer tr = tr();
			rowBg(ctx, x, y, w, h, hover);
			ctx.drawText(tr, label, x + 10, y + (h - 8) / 2, fa(0xFFE8E8F0), false);

			String v = options[get.get()];
			int chipW = tr.getWidth(v) + 28 + (dot != null ? 12 : 0);
			int chipH = 16;
			int cx = x + w - chipW - 8, cy = y + (h - chipH) / 2;
			rrect(ctx, cx, cy, chipW, chipH, 8, ((int) (0x30 + hover * 0x20) << 24) | 0xFFFFFF);
			int tx = cx + 8;
			if (dot != null) {
				rrect(ctx, tx, cy + 4, 8, 8, 4, dot.getAsInt());
				tx += 12;
			}
			ctx.drawText(tr, v, tx, cy + 4, fa(0xFFFFFFFF), false);
			ctx.drawText(tr, "›", cx + chipW - 11, cy + 4, fa(0xFFB0B0C0), false);

			ctx.getMatrices().popMatrix();
		}
	}

	private static final class SliderRow extends SliderWidget {
		private final String label;
		private final String unit;
		private final double min, max;
		private final Consumer<Double> set;
		private float hover = 0f;

		SliderRow(int x, int y, int w, int h, String label, double min, double max, double current, Consumer<Double> set, String unit) {
			super(x, y, w, h, Text.literal(label), Math.max(0.0, Math.min(1.0, (current - min) / (max - min))));
			this.label = label;
			this.unit = unit;
			this.min = min;
			this.max = max;
			this.set = set;
		}

		@Override
		protected void updateMessage() {
		}

		@Override
		protected void applyValue() {
			set.accept(min + this.value * (max - min));
		}

		@Override
		public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
			hover = approach(hover, (isHovered() || isFocused()) ? 1f : 0f);
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(0f, (1f - FADE) * 10f);

			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			TextRenderer tr = tr();
			rowBg(ctx, x, y, w, h, hover);
			ctx.drawText(tr, label, x + 10, y + 4, fa(0xFFE8E8F0), false);
			String v = Math.round(min + this.value * (max - min)) + unit;
			ctx.drawText(tr, v, x + w - 10 - tr.getWidth(v), y + 4, fa(0xFFFFFFFF), false);

			int tx = x + 4, tw = w - 8, ty = y + h - 7;
			rrect(ctx, tx, ty, tw, 3, 1, 0x40FFFFFF);
			int fillW = (int) (tw * this.value);
			for (int i = 0; i < fillW; i++) {
				ctx.fill(tx + i, ty, tx + i + 1, ty + 3, fa(accent(time() - i / (float) tw * 0.6f)));
			}
			int kr = Math.round(5f + hover * 1.5f);
			rrect(ctx, tx + fillW - kr, ty + 1 - kr, kr * 2, kr * 2, kr, 0xFFFFFFFF);

			ctx.getMatrices().popMatrix();
		}
	}

	private static final class TabButton extends ButtonWidget {
		private final String label;
		private final Supplier<Boolean> selected;
		private float hover = 0f;
		private float sel;

		TabButton(int x, int y, int w, int h, String label, int index, Supplier<Boolean> selected, Runnable onPress) {
			super(x, y, w, h, Text.literal(label), b -> onPress.run(), DEFAULT_NARRATION_SUPPLIER);
			this.label = label;
			this.selected = selected;
			this.sel = selected.get() ? 1f : 0f;
		}

		@Override
		public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
			hover = approach(hover, isHovered() ? 1f : 0f);
			sel = approach(sel, selected.get() ? 1f : 0f);
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(0f, (1f - FADE) * 10f);

			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			TextRenderer tr = tr();
			if (hover > 0.01f || sel > 0.01f) {
				rrect(ctx, x, y, w, h, 6, ((int) (hover * 0x14 + sel * 0x18) << 24) | 0xFFFFFF);
			}
			int col = mix(0xFF8A8A9C, 0xFFFFFFFF, Math.max(sel, hover * 0.7f));
			ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, fa(col), false);

			int lw = Math.round((w - 14) * sel);
			for (int i = 0; i < lw; i++) {
				ctx.fill(x + (w - lw) / 2 + i, y + h - 2, x + (w - lw) / 2 + i + 1, y + h, fa(accent(time() - i / (float) w)));
			}

			ctx.getMatrices().popMatrix();
		}
	}

	/** style: 0 = normal, 1 = primary (accent filled), 2 = danger. */
	private static final class ActionButton extends ButtonWidget {
		private final String label;
		private final int style;
		private float hover = 0f;

		ActionButton(int x, int y, int w, int h, String label, int style, Runnable onPress) {
			super(x, y, w, h, Text.literal(label), b -> onPress.run(), DEFAULT_NARRATION_SUPPLIER);
			this.label = label;
			this.style = style;
		}

		@Override
		public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
			hover = approach(hover, isHovered() ? 1f : 0f);
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(0f, (1f - FADE) * 10f);

			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			TextRenderer tr = tr();
			int textCol = 0xFFFFFFFF;
			if (style == 1) {
				// gradient fill, rounded by clipping the first/last columns
				for (int i = 0; i < w; i++) {
					int inset = 0;
					if (i < 5) inset = 5 - i;
					else if (i >= w - 5) inset = i - (w - 6);
					int col = mix(accent(time() - i / (float) w * 0.6f), 0xFFFFFFFF, hover * 0.25f);
					ctx.fill(x + i, y + Math.min(inset, 3), x + i + 1, y + h - Math.min(inset, 3), fa(col));
				}
				textCol = 0xFF101018;
			} else if (style == 2) {
				rrect(ctx, x, y, w, h, 5, mix(0x30FF5555, 0x66FF5555, hover));
				textCol = mix(0xFFFF9090, 0xFFFFFFFF, hover);
			} else {
				rrect(ctx, x, y, w, h, 5, ((int) (0x28 + hover * 0x26) << 24) | 0xFFFFFF);
			}
			ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, fa(textCol), false);

			ctx.getMatrices().popMatrix();
		}
	}

	// ------------------------------------------------------------------ helpers

	private static TextRenderer tr() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static float time() {
		return (System.currentTimeMillis() % 5000L) / 5000f;
	}

	private static float approach(float cur, float target) {
		return cur + (target - cur) * (1f - (float) Math.exp(-DT * 16f));
	}

	/** Multiplies a colour's alpha by the screen fade-in. */
	private static int fa(int argb) {
		int a = Math.round(((argb >>> 24) & 0xFF) * FADE);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int hsb(float h, float s, float b) {
		return 0xFF000000 | Color.HSBtoRGB(((h % 1f) + 1f) % 1f, s, b);
	}

	/** UI accent: follows the HUD colour setting (rainbow or the chosen flat colour). */
	private static int accent(float phase) {
		ProfitConfig c = ProfitConfig.get();
		if (c.colorMode == 0) return hsb(phase, 0.75f, 1f);
		return 0xFF000000 | ProfitConfig.COLOR_VALUES[c.colorMode];
	}

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = Math.round(((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		int ar = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int ag = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int ab = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (aa << 24) | (ar << 16) | (ag << 8) | ab;
	}

	private static void rowBg(DrawContext ctx, int x, int y, int w, int h, float hover) {
		rrect(ctx, x, y, w, h, 5, ((int) (0x1A + hover * 0x16) << 24) | 0xFFFFFF);
	}

	/** Anti-aliased-looking rounded rectangle (true circular corners). */
	private static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		int col = fa(color);
		for (int k = 0; k < h; k++) {
			int inset = 0;
			if (k < r) {
				double dy = r - k - 0.5;
				inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			} else if (k >= h - r) {
				double dy = k + 0.5 - (h - r);
				inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			}
			ctx.fill(x + inset, y + k, x + w - inset, y + k + 1, col);
		}
	}
}
