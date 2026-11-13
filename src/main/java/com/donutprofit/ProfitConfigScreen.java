package com.donutprofit;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Settings menu. The HUD keeps rendering behind it, so changes preview live. */
public class ProfitConfigScreen extends Screen {
	private static final int COL_W = 150;
	private static final int ROW_H = 24;

	private final Screen parent;

	public ProfitConfigScreen(Screen parent) {
		super(Text.literal("Donut Profit Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ProfitConfig c = ProfitConfig.get();
		int left = this.width / 2 - COL_W - 5;
		int right = this.width / 2 + 5;
		int top = this.height / 2 - 66;
		int y;

		// ----- left column: look -----
		y = top;
		addDrawableChild(toggle(left, y, "HUD", () -> c.enabled, v -> c.enabled = v)); y += ROW_H;
		addDrawableChild(cycle(left, y, "Position", ProfitConfig.ANCHORS, () -> c.anchor, v -> c.anchor = v)); y += ROW_H;
		addDrawableChild(cycle(left, y, "Colour", ProfitConfig.COLOR_MODES, () -> c.colorMode, v -> c.colorMode = v)); y += ROW_H;
		addDrawableChild(slider(left, y, "X Offset", -300, 300, () -> c.offsetX, v -> c.offsetX = (int) Math.round(v), "px")); y += ROW_H;
		addDrawableChild(slider(left, y, "Y Offset", 0, 300, () -> c.offsetY, v -> c.offsetY = (int) Math.round(v), "px")); y += ROW_H;
		addDrawableChild(slider(left, y, "Size", 50, 250, () -> c.scale * 100, v -> c.scale = (float) v / 100f, "%")); y += ROW_H;

		// ----- right column: style + tracking -----
		y = top;
		addDrawableChild(slider(right, y, "Background", 0, 100, () -> c.backgroundOpacity, v -> c.backgroundOpacity = (int) Math.round(v), "%")); y += ROW_H;
		addDrawableChild(slider(right, y, "Anim Speed", 10, 400, () -> c.rainbowSpeed * 100, v -> c.rainbowSpeed = (float) v / 100f, "%")); y += ROW_H;
		addDrawableChild(toggle(right, y, "Title", () -> c.showTitle, v -> c.showTitle = v)); y += ROW_H;
		addDrawableChild(toggle(right, y, "Border", () -> c.showBorder, v -> c.showBorder = v)); y += ROW_H;
		addDrawableChild(toggle(right, y, "Accent Bar", () -> c.showAccentBar, v -> c.showAccentBar = v)); y += ROW_H;
		addDrawableChild(toggle(right, y, "Plus Sign", () -> c.showPlusSign, v -> c.showPlusSign = v)); y += ROW_H;

		// ----- tracking row (full width) -----
		y = top + ROW_H * 6 + 8;
		int third = (COL_W * 2 + 10 - 8) / 3;
		int x0 = left;
		addDrawableChild(toggle(x0, y, third, "Count Sales", () -> c.countSales, v -> c.countSales = v));
		addDrawableChild(toggle(x0 + third + 4, y, third, "Count Payments", () -> c.countPayments, v -> c.countPayments = v));
		addDrawableChild(toggle(x0 + (third + 4) * 2, y, third, "Sync Sidebar", () -> c.syncWithSidebar, v -> c.syncWithSidebar = v));
		y += ROW_H + 4;

		// ----- bottom buttons -----
		int bw = (COL_W * 2 + 10 - 8) / 3;
		addDrawableChild(ButtonWidget.builder(Text.literal("Reset Profit"), b -> ProfitTracker.reset())
				.dimensions(left, y, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Defaults"), b -> {
			ProfitConfig.resetToDefaults();
			this.clearAndInit();
		}).dimensions(left + bw + 4, y, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
				.dimensions(left + (bw + 4) * 2, y, bw, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 92, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("Changes save automatically. Profit resets when the game closes."),
				this.width / 2, this.height / 2 + 90, 0xFFAAAAAA);
	}

	@Override
	public void close() {
		ProfitConfig.save();
		if (this.client != null) this.client.setScreen(parent);
	}

	// ---------- widget helpers ----------

	private ButtonWidget toggle(int x, int y, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		return toggle(x, y, COL_W, label, get, set);
	}

	private ButtonWidget toggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		return ButtonWidget.builder(toggleText(label, get.get()), b -> {
			set.accept(!get.get());
			b.setMessage(toggleText(label, get.get()));
			ProfitConfig.save();
		}).dimensions(x, y, w, 20).build();
	}

	private static Text toggleText(String label, boolean on) {
		return Text.literal(label + ": " + (on ? "ON" : "OFF"));
	}

	private ButtonWidget cycle(int x, int y, String label, String[] options, Supplier<Integer> get, Consumer<Integer> set) {
		return ButtonWidget.builder(Text.literal(label + ": " + options[get.get()]), b -> {
			set.accept((get.get() + 1) % options.length);
			b.setMessage(Text.literal(label + ": " + options[get.get()]));
			ProfitConfig.save();
		}).dimensions(x, y, COL_W, 20).build();
	}

	private SliderWidget slider(int x, int y, String label, double min, double max,
								Supplier<Number> get, Consumer<Double> set, String unit) {
		return new SliderWidget(x, y, COL_W, 20, Text.empty(), (get.get().doubleValue() - min) / (max - min)) {
			{ updateMessage(); }

			private double actual() {
				return min + this.value * (max - min);
			}

			@Override
			protected void updateMessage() {
				setMessage(Text.literal(label + ": " + Math.round(actual()) + unit));
			}

			@Override
			protected void applyValue() {
				set.accept(actual());
			}
		};
	}
}
