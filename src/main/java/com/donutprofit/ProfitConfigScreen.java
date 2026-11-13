package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.awt.Color;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Settings menu: flat panel, category sidebar on the left, plain controls on the right.
 * The real HUD is drawn on top of the panel, so every change previews live.
 * Clicks are handled by invisible vanilla widgets; visuals are separate Drawables.
 */
public class ProfitConfigScreen extends Screen {
	public static final String MOD_NAME = "foomc's Profit Mod";
	public static final String YOUTUBE_URL = "https://www.youtube.com/@FooMc2/videos";

	private static final int PW = 410;
	private static final int PH = 214;
	private static final int SBW = 116;     // sidebar width
	private static final int ROW_H = 22;
	private static final int PITCH = 24;
	private static final int TRACK_W = 96;
	private static final String[] TABS = {"Layout", "Style", "Elements", "Tracking", "Appearance", "Behaviour"};

	private static final int[] RADII = {0, 3, 6};
	private static final int[] ACCENTS = {0xFF4C8DFF, 0xFF4CC38A, 0xFFFF6FA8, 0xFFFFA040, 0xFFE6E6E6};
	private static final int[] THEME_BG = {0xFF1C1C20, 0xFF0D0D0F, 0xFF1D222C};
	private static final int[] THEME_SIDEBAR = {0xFF151518, 0xFF08080A, 0xFF161A22};
	private static final int[] THEME_BORDER = {0xFF35353C, 0xFF27272C, 0xFF353D4D};

	private static final int TEXT = 0xFFD9D9DE;
	private static final int TEXT_DIM = 0xFF8D8D96;
	private static final int TEXT_WHITE = 0xFFFFFFFF;

	private static int lastTab = 0;
	private static float FADE = 1f;
	private static long openNanos = 0;

	private final Screen parent;
	private int tab = lastTab;
	private int px, py;

	public ProfitConfigScreen(Screen parent) {
		super(Text.literal(MOD_NAME + " Settings"));
		this.parent = parent;
		openNanos = System.nanoTime();
		FADE = ProfitConfig.get().menuAnimations ? 0f : 1f;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		ProfitConfig c = ProfitConfig.get();
		px = (this.width - PW) / 2;
		py = Math.max(4, (this.height - PH) / 2);

		addDrawable(new PanelDrawable());

		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			addTab(px + 1, py + 40 + i * 20, SBW - 1, 20, TABS[i], () -> tab == idx, () -> {
				tab = idx;
				lastTab = idx;
				this.clearAndInit();
			});
		}

		// YouTube link
		addLink(px + 1, py + PH - 44, SBW - 1, 20, "YouTube", YOUTUBE_URL);

		int x = px + SBW + 14;
		int w = PW - SBW - 28;
		int y = py + 34;

		switch (tab) {
			case 0 -> {
				addToggle(x, y, w, "HUD Enabled", () -> c.enabled, v -> c.enabled = v); y += PITCH;
				addCycle(x, y, w, "Position", ProfitConfig.ANCHORS, () -> c.anchor, v -> c.anchor = v, null); y += PITCH;
				addSlider(x, y, w, "X Offset", -300, 300, c.offsetX, v -> c.offsetX = (int) Math.round(v), "px"); y += PITCH;
				addSlider(x, y, w, "Y Offset", 0, 300, c.offsetY, v -> c.offsetY = (int) Math.round(v), "px"); y += PITCH;
				addSlider(x, y, w, "Size", 50, 250, c.scale * 100.0, v -> c.scale = v.floatValue() / 100f, "%");
			}
			case 1 -> {
				addCycle(x, y, w, "Colour", ProfitConfig.COLOR_MODES, () -> c.colorMode, v -> c.colorMode = v,
						() -> c.colorMode == 0 ? hsb(time(), 0.8f, 1f) : (0xFF000000 | ProfitConfig.COLOR_VALUES[c.colorMode])); y += PITCH;
				addSlider(x, y, w, "Background", 0, 100, c.backgroundOpacity, v -> c.backgroundOpacity = (int) Math.round(v), "%"); y += PITCH;
				addSlider(x, y, w, "Animation Speed", 10, 400, c.rainbowSpeed * 100.0, v -> c.rainbowSpeed = v.floatValue() / 100f, "%");
			}
			case 2 -> {
				addToggle(x, y, w, "Title", () -> c.showTitle, v -> c.showTitle = v); y += PITCH;
				addToggle(x, y, w, "Border", () -> c.showBorder, v -> c.showBorder = v); y += PITCH;
				addToggle(x, y, w, "Accent Bar", () -> c.showAccentBar, v -> c.showAccentBar = v); y += PITCH;
				addToggle(x, y, w, "Plus Sign", () -> c.showPlusSign, v -> c.showPlusSign = v);
			}
			case 3 -> {
				addToggle(x, y, w, "Count Sales", () -> c.countSales, v -> c.countSales = v); y += PITCH;
				addToggle(x, y, w, "Count Payments", () -> c.countPayments, v -> c.countPayments = v); y += PITCH;
				addToggle(x, y, w, "Sync With Sidebar", () -> c.syncWithSidebar, v -> c.syncWithSidebar = v);
			}
			case 4 -> {
				addCycle(x, y, w, "Accent", ProfitConfig.MENU_ACCENTS, () -> c.menuAccent, v -> c.menuAccent = v, () -> accentOf(c.menuAccent)); y += PITCH;
				addSlider(x, y, w, "Custom Hue", 0, 360, c.menuHue, v -> c.menuHue = (int) Math.round(v), "°"); y += PITCH;
				addCycle(x, y, w, "Corners", ProfitConfig.MENU_CORNERS, () -> c.menuCorners, v -> c.menuCorners = v, null); y += PITCH;
				addCycle(x, y, w, "Theme", ProfitConfig.MENU_THEMES, () -> c.menuTheme, v -> c.menuTheme = v, null); y += PITCH;
				addSlider(x, y, w, "Panel Opacity", 40, 100, c.menuOpacity, v -> c.menuOpacity = (int) Math.round(v), "%");
			}
			default -> {
				addToggle(x, y, w, "Animations", () -> c.menuAnimations, v -> c.menuAnimations = v); y += PITCH;
				addToggle(x, y, w, "Dim Background", () -> c.menuDim, v -> c.menuDim = v); y += PITCH;
				addToggle(x, y, w, "HUD Preview", () -> c.menuPreview, v -> c.menuPreview = v); y += PITCH;
				addToggle(x, y, w, "Text Shadow", () -> c.menuShadow, v -> c.menuShadow = v); y += PITCH;
				addToggle(x, y, w, "Row Highlight", () -> c.menuHighlight, v -> c.menuHighlight = v);
			}
		}

		// footer
		int fy = py + PH - 32;
		int fg = 6;
		int fw = (w - fg * 2) / 3;
		addAction(x, fy, fw, 20, "Reset Profit", 2, ProfitTracker::reset);
		addAction(x + fw + fg, fy, fw, 20, "Defaults", 0, () -> {
			ProfitConfig.resetToDefaults();
			this.clearAndInit();
		});
		addAction(x + (fw + fg) * 2, fy, fw, 20, "Done", 1, this::close);

		// live HUD preview, drawn last so it sits on top of the panel
		addDrawable((ctx, mx, my, d) -> {
			if (ProfitConfig.get().menuPreview) ProfitHud.draw(ctx, 0.5f);
		});
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
			ProfitConfig cfg = ProfitConfig.get();
			if (cfg.menuAnimations) {
				float t = Math.min(1f, (System.nanoTime() - openNanos) / 1_000_000_000f / 0.12f);
				FADE = t;
			} else {
				FADE = 1f;
			}
			if (cfg.menuDim) ctx.fill(0, 0, width, height, ((int) (0x60 * FADE) << 24));

			int r = rad();
			int r1 = Math.max(0, r - 1);
			int th = cfg.menuTheme;
			int border = THEME_BORDER[th];
			ring(ctx, px, py, PW, PH, r, border);
			rrectSides(ctx, px + 1, py + 1, SBW - 1, PH - 2, r1, withOpacity(THEME_SIDEBAR[th]), true, false);
			ctx.fill(px + SBW, py + 1, px + SBW + 1, py + PH - 1, fa(border));
			rrectSides(ctx, px + SBW + 1, py + 1, PW - SBW - 2, PH - 2, r1, withOpacity(THEME_BG[th]), false, true);

			TextRenderer tr = tr();
			txt(ctx, tr, MOD_NAME, px + 12, py + 11, TEXT_WHITE);
			txt(ctx, tr, "v1.0.0", px + 12, py + 23, TEXT_DIM);
			txt(ctx, tr, "Right Shift", px + 12, py + PH - 18, TEXT_DIM);

			int cx = px + SBW + 14;
			int cw = PW - SBW - 28;
			txt(ctx, tr, TABS[tab], cx, py + 12, TEXT_WHITE);
			ctx.fill(cx, py + 25, cx + cw, py + 26, fa(border));

			if (tab == 3) {
				int hy = py + 34 + PITCH * 3 + 2;
				txt(ctx, tr, "Sales & payments are read from chat messages", cx, hy, TEXT_DIM);
				txt(ctx, tr, "about you. Sync fixes drift using the sidebar.", cx, hy + 11, TEXT_DIM);
			}
			if (tab >= 4) {
				txt(ctx, tr, "Changes save automatically.", cx, py + 34 + PITCH * 5 + 2, TEXT_DIM);
			}
		}
	}

	// ------------------------------------------------------------------ widgets

	private ButtonWidget hit(int x, int y, int w, int h, Runnable action) {
		ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> action.run()).dimensions(x, y, w, h).build();
		b.setAlpha(0f);
		return addDrawableChild(b);
	}

	private void addToggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		hit(x, y, w, ROW_H, () -> set.accept(!get.get()));
		addDrawable(new ToggleVisual(x, y, w, ROW_H, label, get));
	}

	private void addCycle(int x, int y, int w, String label, String[] options,
						  Supplier<Integer> get, Consumer<Integer> set, IntSupplier swatch) {
		hit(x, y, w, ROW_H, () -> set.accept((get.get() + 1) % options.length));
		addDrawable(new CycleVisual(x, y, w, ROW_H, label, options, get, swatch));
	}

	private void addSlider(int x, int y, int w, String label, double min, double max,
						   double current, Consumer<Double> set, String unit) {
		int sx = x + w - (TRACK_W + 8) - 4;
		SliderRow s = new SliderRow(sx, y, TRACK_W + 8, ROW_H, min, max, current, set);
		addDrawableChild(s);
		addDrawable(new SliderVisual(s, x, y, w, ROW_H, label, min, max, unit));
	}

	private void addTab(int x, int y, int w, int h, String label, Supplier<Boolean> selected, Runnable action) {
		hit(x, y, w, h, action);
		addDrawable(new TabVisual(x, y, w, h, label, selected));
	}

	private void addLink(int x, int y, int w, int h, String label, String url) {
		hit(x, y, w, h, () -> {
			try {
				Util.getOperatingSystem().open(URI.create(url));
			} catch (Exception ignored) {
			}
		});
		addDrawable(new LinkVisual(x, y, w, h, label));
	}

	private void addAction(int x, int y, int w, int h, String label, int style, Runnable action) {
		hit(x, y, w, h, action);
		addDrawable(new ActionVisual(x, y, w, h, label, style));
	}

	private static final class SliderRow extends SliderWidget {
		private final double min, max;
		private final Consumer<Double> set;

		SliderRow(int x, int y, int w, int h, double min, double max, double current, Consumer<Double> set) {
			super(x, y, w, h, Text.empty(), Math.max(0.0, Math.min(1.0, (current - min) / (max - min))));
			this.min = min;
			this.max = max;
			this.set = set;
			this.setAlpha(0f);
		}

		@Override
		protected void updateMessage() {
		}

		@Override
		protected void applyValue() {
			set.accept(min + this.value * (max - min));
		}

		double val() {
			return this.value;
		}
	}

	private static boolean over(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void rowHover(DrawContext ctx, int x, int y, int w, int h, boolean hovered) {
		if (hovered && ProfitConfig.get().menuHighlight) ctx.fill(x - 4, y, x + w + 4, y + h, fa(0x0FFFFFFF));
	}

	private record Row(int x, int y, int w, int h) {}

	private static final class ToggleVisual implements Drawable {
		private final Row r;
		private final String label;
		private final Supplier<Boolean> get;

		ToggleVisual(int x, int y, int w, int h, String label, Supplier<Boolean> get) {
			this.r = new Row(x, y, w, h);
			this.label = label;
			this.get = get;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			rowHover(ctx, r.x, r.y, r.w, r.h, over(mx, my, r.x, r.y, r.w, r.h));
			txt(ctx, tr(), label, r.x, r.y + (r.h - 8) / 2, TEXT);

			int bs = 12;
			int bx = r.x + r.w - bs - 2, by = r.y + (r.h - bs) / 2;
			boolean on = get.get();
			int accent = menuAccent();
			ctx.fill(bx, by, bx + bs, by + bs, fa(on ? accent : 0xFF5A5A63));
			ctx.fill(bx + 1, by + 1, bx + bs - 1, by + bs - 1, fa(on ? accent : themeBg()));
			if (on) {
				int[][] tick = {{2, 6}, {3, 7}, {4, 8}, {5, 7}, {6, 6}, {7, 5}, {8, 4}, {9, 3}};
				for (int[] p : tick) {
					ctx.fill(bx + p[0], by + p[1] - 1, bx + p[0] + 1, by + p[1] + 1, fa(0xFF101014));
				}
			}
		}
	}

	private static final class CycleVisual implements Drawable {
		private final Row r;
		private final String label;
		private final String[] options;
		private final Supplier<Integer> get;
		private final IntSupplier swatch;

		CycleVisual(int x, int y, int w, int h, String label, String[] options, Supplier<Integer> get, IntSupplier swatch) {
			this.r = new Row(x, y, w, h);
			this.label = label;
			this.options = options;
			this.get = get;
			this.swatch = swatch;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			boolean hov = over(mx, my, r.x, r.y, r.w, r.h);
			rowHover(ctx, r.x, r.y, r.w, r.h, hov);
			TextRenderer tr = tr();
			int ty = r.y + (r.h - 8) / 2;
			txt(ctx, tr, label, r.x, ty, TEXT);

			String v = options[get.get()];
			int right = r.x + r.w - 2;
			int arrow = hov ? TEXT_WHITE : TEXT_DIM;
			txt(ctx, tr, ">", right - 5, ty, arrow);
			int vx = right - 5 - 6 - tr.getWidth(v);
			txt(ctx, tr, v, vx, ty, TEXT_WHITE);
			txt(ctx, tr, "<", vx - 11, ty, arrow);
			if (swatch != null) {
				ctx.fill(vx - 24, ty, vx - 15, ty + 9, fa(0xFF000000));
				ctx.fill(vx - 23, ty + 1, vx - 16, ty + 8, fa(swatch.getAsInt()));
			}
		}
	}

	private static final class SliderVisual implements Drawable {
		private final SliderRow w;
		private final Row r;
		private final String label, unit;
		private final double min, max;

		SliderVisual(SliderRow w, int x, int y, int rw, int h, String label, double min, double max, String unit) {
			this.w = w;
			this.r = new Row(x, y, rw, h);
			this.label = label;
			this.min = min;
			this.max = max;
			this.unit = unit;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			boolean hov = over(mx, my, r.x, r.y, r.w, r.h) || w.isFocused();
			rowHover(ctx, r.x, r.y, r.w, r.h, hov);
			TextRenderer tr = tr();
			int ty = r.y + (r.h - 8) / 2;
			txt(ctx, tr, label, r.x, ty, TEXT);

			double value = w.val();
			String v = Math.round(min + value * (max - min)) + unit;
			txt(ctx, tr, v, w.getX() - 4 - tr.getWidth(v), ty, TEXT_WHITE);

			int tx = w.getX() + 4;
			int tw = w.getWidth() - 8;
			int cy = r.y + r.h / 2;
			ctx.fill(tx, cy - 1, tx + tw, cy + 1, fa(0xFF3C3C45));
			int fillW = (int) (tw * value);
			ctx.fill(tx, cy - 1, tx + fillW, cy + 1, fa(menuAccent()));
			ctx.fill(tx + fillW - 2, cy - 5, tx + fillW + 2, cy + 5, fa(hov ? TEXT_WHITE : 0xFFC8C8CE));
		}
	}

	private static final class TabVisual implements Drawable {
		private final Row r;
		private final String label;
		private final Supplier<Boolean> selected;

		TabVisual(int x, int y, int w, int h, String label, Supplier<Boolean> selected) {
			this.r = new Row(x, y, w, h);
			this.label = label;
			this.selected = selected;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			boolean sel = selected.get();
			boolean hov = over(mx, my, r.x, r.y, r.w, r.h);
			if (sel) {
				ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, fa(0x14FFFFFF));
				ctx.fill(r.x, r.y, r.x + 2, r.y + r.h, fa(menuAccent()));
			} else if (hov) {
				ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, fa(0x0AFFFFFF));
			}
			txt(ctx, tr(), label, r.x + 12, r.y + (r.h - 8) / 2, sel ? TEXT_WHITE : (hov ? TEXT : TEXT_DIM));
		}
	}

	/** Sidebar link with a small YouTube-style badge. */
	private static final class LinkVisual implements Drawable {
		private final Row r;
		private final String label;

		LinkVisual(int x, int y, int w, int h, String label) {
			this.r = new Row(x, y, w, h);
			this.label = label;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			boolean hov = over(mx, my, r.x, r.y, r.w, r.h);
			if (hov) ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, fa(0x0AFFFFFF));
			int bx = r.x + 12, by = r.y + (r.h - 9) / 2;
			ctx.fill(bx, by + 1, bx + 13, by + 8, fa(0xFFE02B20));
			ctx.fill(bx + 1, by, bx + 12, by + 9, fa(0xFFE02B20));
			ctx.fill(bx + 5, by + 2, bx + 6, by + 7, fa(0xFFFFFFFF));
			ctx.fill(bx + 6, by + 3, bx + 7, by + 6, fa(0xFFFFFFFF));
			ctx.fill(bx + 7, by + 4, bx + 8, by + 5, fa(0xFFFFFFFF));
			txt(ctx, tr(), label, bx + 19, r.y + (r.h - 8) / 2, hov ? TEXT_WHITE : TEXT_DIM);
		}
	}

	/** style: 0 = normal, 1 = primary (accent), 2 = danger. */
	private static final class ActionVisual implements Drawable {
		private final Row r;
		private final String label;
		private final int style;

		ActionVisual(int x, int y, int w, int h, String label, int style) {
			this.r = new Row(x, y, w, h);
			this.label = label;
			this.style = style;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			boolean hov = over(mx, my, r.x, r.y, r.w, r.h);
			int rad = Math.min(rad(), 3);
			int border, bg, text;
			if (style == 1) {
				int a = menuAccent();
				border = a;
				bg = hov ? mix(a, 0xFFFFFFFF, 0.15f) : a;
				text = 0xFF101014;
			} else if (style == 2) {
				border = hov ? 0xFFB5504F : 0xFF6E3C3C;
				bg = hov ? 0xFF3A2424 : 0xFF2A1E1E;
				text = 0xFFE59A98;
			} else {
				border = hov ? 0xFF5A5A63 : 0xFF3F3F47;
				bg = hov ? 0xFF2E2E34 : 0xFF25252A;
				text = TEXT_WHITE;
			}
			rrect(ctx, r.x, r.y, r.w, r.h, rad, border);
			rrect(ctx, r.x + 1, r.y + 1, r.w - 2, r.h - 2, Math.max(0, rad - 1), bg);
			TextRenderer tr = tr();
			txt(ctx, tr, label, r.x + (r.w - tr.getWidth(label)) / 2, r.y + (r.h - 8) / 2, text);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static TextRenderer tr() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static void txt(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		ctx.drawText(tr, s, x, y, fa(color), ProfitConfig.get().menuShadow);
	}

	private static float time() {
		return (System.currentTimeMillis() % 5000L) / 5000f;
	}

	private static int rad() {
		return RADII[ProfitConfig.get().menuCorners];
	}

	private static int themeBg() {
		return THEME_BG[ProfitConfig.get().menuTheme];
	}

	private static int menuAccent() {
		return accentOf(ProfitConfig.get().menuAccent);
	}

	/** 0-4 presets, 5 = custom hue, 6 = follow the HUD colour setting. */
	private static int accentOf(int idx) {
		ProfitConfig c = ProfitConfig.get();
		if (idx < ACCENTS.length) return ACCENTS[idx];
		if (idx == ACCENTS.length) return hsb(c.menuHue / 360f, 0.65f, 1f);
		if (c.colorMode == 0) return hsb(time(), 0.7f, 1f);
		return 0xFF000000 | ProfitConfig.COLOR_VALUES[c.colorMode];
	}

	/** Applies the panel opacity setting to a colour. */
	private static int withOpacity(int argb) {
		int a = Math.round(255 * ProfitConfig.get().menuOpacity / 100f);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Multiplies a colour's alpha by the fade-in. */
	private static int fa(int argb) {
		int a = Math.round(((argb >>> 24) & 0xFF) * FADE);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int hsb(float h, float s, float b) {
		return 0xFF000000 | Color.HSBtoRGB(((h % 1f) + 1f) % 1f, s, b);
	}

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = Math.round(((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		int ar = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int ag = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int ab = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (aa << 24) | (ar << 16) | (ag << 8) | ab;
	}

	private static int insetAt(int k, int h, int r) {
		if (r <= 0) return 0;
		double dy;
		if (k < r) dy = r - k - 0.5;
		else if (k >= h - r) dy = k + 0.5 - (h - r);
		else return 0;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	/** Filled rectangle; each side can be rounded or square, so adjacent panels never overlap. */
	private static void rrectSides(DrawContext ctx, int x, int y, int w, int h, int r, int color, boolean left, boolean right) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		int col = fa(color);
		for (int k = 0; k < h; k++) {
			int inset = insetAt(k, h, r);
			ctx.fill(x + (left ? inset : 0), y + k, x + w - (right ? inset : 0), y + k + 1, col);
		}
	}

	private static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
		rrectSides(ctx, x, y, w, h, r, color, true, true);
	}

	/** 1px outline with rounded corners. */
	private static void ring(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		int col = fa(color);
		for (int k = 0; k < h; k++) {
			int ins = insetAt(k, h, r);
			if (k == 0 || k == h - 1) {
				ctx.fill(x + ins, y + k, x + w - ins, y + k + 1, col);
				continue;
			}
			int ref = k < h / 2 ? insetAt(k - 1, h, r) : insetAt(k + 1, h, r);
			int run = Math.max(ins + 1, ref);
			ctx.fill(x + ins, y + k, x + run, y + k + 1, col);
			ctx.fill(x + w - run, y + k, x + w - ins, y + k + 1, col);
		}
	}
}
