package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** Auction control menu: pick the prize, entry price and wheel settings, then BEGIN / SPIN. */
public class AuctionScreen extends Screen {
	private static final int PW = 400;
	private static final int PH = 246;

	private int px, py;
	private ButtonWidget beginBtn, spinBtn, styleBtn;
	private TextFieldWidget nameField, entryField;

	public AuctionScreen() {
		super(Text.literal("Auction"));
	}

	@Override
	protected void init() {
		ProfitConfig c = ProfitConfig.get();
		px = (this.width - PW) / 2;
		py = Math.max(4, (this.height - PH) / 2);
		int x = px + 14;
		int w = PW - 28;
		int half = (w - 6) / 2;

		addDrawable(new Backdrop(x, w));

		// ---- row 1: prize
		int y1 = py + 36;
		addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> cyclePrize(-1)).dimensions(x, y1, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> cyclePrize(1)).dimensions(x + 50, y1, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Held"), b -> useHeld()).dimensions(x + 76, y1, 44, 20).build());
		nameField = new TextFieldWidget(this.textRenderer, x + 126, y1, w - 126, 20, Text.literal("Prize name"));
		nameField.setMaxLength(32);
		nameField.setText(c.auctionPrizeName == null ? "" : c.auctionPrizeName);
		nameField.setChangedListener(t -> c.auctionPrizeName = t);
		addDrawableChild(nameField);
		addDrawable((ctx, mx, my, d) -> {
			if (nameField.getText().isEmpty() && !nameField.isFocused()) {
				ctx.drawText(this.textRenderer, AuctionManager.baseName() + "  (auto)", x + 126 + 5, y1 + 6, 0xFF707080, false);
			}
		});

		// ---- row 2: entry price + max slots
		int y2 = py + 62;
		entryField = new TextFieldWidget(this.textRenderer, x + 40, y2, half - 40, 20, Text.literal("Entry price"));
		entryField.setMaxLength(16);
		entryField.setText(shortMoney(c.auctionEntry));
		entryField.setChangedListener(t -> {
			Double v = ProfitConfig.parseAmount(t);
			if (v != null && v >= 1) c.auctionEntry = v;
		});
		addDrawableChild(entryField);
		addDrawable((ctx, mx, my, d) -> {
			Double v = ProfitConfig.parseAmount(entryField.getText());
			if (entryField.getText().isEmpty() && !entryField.isFocused()) {
				ctx.drawText(this.textRenderer, "e.g. 1m, 500k", x + 40 + 5, y2 + 6, 0xFF707080, false);
			} else if (v == null || v < 1) {
				ctx.drawText(this.textRenderer, "invalid", x + half - 38, y2 + 6, 0xFFFF6B6B, false);
			}
		});
		addDrawableChild(new ValueSlider(x + half + 6, y2, half, 20, "Max slots each", 1, 20, c.auctionMaxSlots, "", v -> c.auctionMaxSlots = v));

		// ---- row 3: spin time + style
		int y3 = py + 88;
		addDrawableChild(new ValueSlider(x, y3, half, 20, "Spin time", 3, 30, c.auctionSpin, "s", v -> c.auctionSpin = v));
		styleBtn = addDrawableChild(ButtonWidget.builder(styleText(), b -> {
			c.auctionPalette = (c.auctionPalette + 1) % WheelScreen.PALETTE_NAMES.length;
			b.setMessage(styleText());
		}).dimensions(x + half + 6, y3, half, 20).build());

		// ---- row 4: actions
		int y4 = py + 118;
		int gap = 4;
		int bw = (w - gap * 4) / 5;
		beginBtn = addDrawableChild(ButtonWidget.builder(Text.literal("▶ BEGIN").formatted(Formatting.GREEN, Formatting.BOLD), b -> {
			AuctionManager.begin();
			openWheel();
		}).dimensions(x, y4, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Shuffle"), b -> AuctionManager.shuffle())
				.dimensions(x + (bw + gap), y4, bw, 20).build());
		spinBtn = addDrawableChild(ButtonWidget.builder(Text.literal("SPIN!").formatted(Formatting.GOLD, Formatting.BOLD), b -> {
			if (AuctionManager.spin()) openWheel();
		}).dimensions(x + (bw + gap) * 2, y4, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE").formatted(Formatting.RED), b -> AuctionManager.reset())
				.dimensions(x + (bw + gap) * 3, y4, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Demo").formatted(Formatting.AQUA), b -> {
			AuctionManager.demo();
			openWheel();
		}).dimensions(x + (bw + gap) * 4, y4, bw, 20).build());

		// ---- volume
		addDrawableChild(new ValueSlider(x, py + 198, w, 20, "♫ Volume", 0, 100, c.auctionVolume, "%", v -> c.auctionVolume = v));
	}

	private Text styleText() {
		return Text.literal("Style: " + WheelScreen.PALETTE_NAMES[ProfitConfig.get().auctionPalette]);
	}

	private void openWheel() {
		if (this.client != null) this.client.setScreen(new WheelScreen(null));
	}

	private void cyclePrize(int dir) {
		ProfitConfig c = ProfitConfig.get();
		int n = AuctionManager.PRESET_ITEMS.length;
		int cur = c.auctionPrize < 0 ? (dir > 0 ? -1 : 0) : c.auctionPrize;
		c.auctionPrize = ((cur + dir) % n + n) % n;
	}

	private void useHeld() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		ItemStack held = mc.player.getMainHandStack();
		if (held.isEmpty()) {
			AuctionManager.event("Hold an item in your main hand first");
			return;
		}
		AuctionManager.heldPrize = held.copy();
		ProfitConfig.get().auctionPrize = -1;
	}

	private static String shortMoney(double v) {
		if (v <= 0) return "1";
		return ProfitTracker.format(v).substring(2);
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
		if (this.client != null) this.client.setScreen(null);
	}

	// ------------------------------------------------------------------ pieces

	private static final class ValueSlider extends SliderWidget {
		private final String label, unit;
		private final double min, max;
		private final Consumer<Integer> set;

		ValueSlider(int x, int y, int w, int h, String label, double min, double max, double cur, String unit, Consumer<Integer> set) {
			super(x, y, w, h, Text.empty(), Math.max(0.0, Math.min(1.0, (cur - min) / (max - min))));
			this.label = label;
			this.unit = unit;
			this.min = min;
			this.max = max;
			this.set = set;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (label == null) return;
			setMessage(Text.literal(label + ": " + Math.round(min + this.value * (max - min)) + unit));
		}

		@Override
		protected void applyValue() {
			set.accept((int) Math.round(min + this.value * (max - min)));
		}
	}

	private final class Backdrop implements Drawable {
		private final int x, w;

		Backdrop(int x, int w) {
			this.x = x;
			this.w = w;
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			AuctionManager.update();
			TextRenderer tr = textRenderer;
			ProfitConfig c = ProfitConfig.get();
			float t = (System.currentTimeMillis() % 4000L) / 4000f;

			// panel
			ctx.fill(0, 0, width, height, 0x66000000);
			Gfx.rrect(ctx, px - 2, py - 2, PW + 4, PH + 4, 6, 0xFF2FD0B0);
			Gfx.rrect(ctx, px, py, PW, PH, 5, 0xF01A2236);

			// title
			String title = "\u2605 FOOMC'S AUCTION \u2605";
			ctx.drawText(tr, title, px + (PW - tr.getWidth(title)) / 2, py + 10, 0xFF55FF9A, true);
			ctx.fill(x, py + 26, x + w, py + 27, 0x332FD0B0);

			// prize icon between the arrows
			ItemStack st = AuctionManager.prizeStack();
			if (!st.isEmpty()) ctx.drawItem(st, x + 27, py + 38);

			ctx.drawText(tr, "Entry $", x, py + 62 + 6, 0xFFE8F0FF, false);

			String entry = ProfitTracker.format(c.auctionEntry).substring(1);
			ctx.drawText(tr, "Every " + entry + " paid = 1 slot (max " + c.auctionMaxSlots + " each). Paying more = more slots.", x, py + 146, 0xFFB8C4D8, false);
			ctx.drawText(tr, "BEGIN opens entries live • SPIN (or K) closes them and spins", x, py + 158, 0xFF6F7C95, false);

			// status
			String me = MinecraftClient.getInstance().getSession().getUsername();
			String status;
			int col;
			switch (AuctionManager.state) {
				case OPEN -> {
					status = "OPEN — " + AuctionManager.slotsBy.size() + " players, " + AuctionManager.totalSlots() + " slots. SPIN when ready.";
					col = 0xFF55FF9A;
				}
				case SPINNING -> {
					status = "Spinning...";
					col = 0xFFFFD34D;
				}
				case DONE -> {
					status = "Winner: " + AuctionManager.winner + "  (BEGIN starts a new round)";
					col = 0xFFFFD34D;
				}
				default -> {
					status = "Ready. Viewers bid with /pay " + me + " <amount>";
					col = 0xFFE8F0FF;
				}
			}
			String shown = Gfx.fit(tr, status, w);
			ctx.drawText(tr, shown, px + (PW - tr.getWidth(shown)) / 2, py + 182, col, true);

			String foot = "J menu • K begin / spin";
			ctx.drawText(tr, foot, px + (PW - tr.getWidth(foot)) / 2, py + PH - 14, 0xFF6F7C95, false);

			// button states
			AuctionManager.State s = AuctionManager.state;
			beginBtn.active = s != AuctionManager.State.OPEN && s != AuctionManager.State.SPINNING;
			spinBtn.active = s == AuctionManager.State.OPEN && AuctionManager.totalSlots() > 0;
		}
	}
}
