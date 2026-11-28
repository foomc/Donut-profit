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

/** Bidding auction control menu: pick the prize, starting bid, time and rules, then START. */
public class BidMenuScreen extends Screen {
	private static final int PW = 400;
	private static final int PH = 250;

	private int px, py;
	private ButtonWidget startBtn, endBtn, modeBtn, snipeBtn;
	private TextFieldWidget nameField, startField;

	public BidMenuScreen() {
		super(Text.literal("Bidding Auction"));
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

		// ---- row 2: starting bid + time
		int y2 = py + 62;
		startField = new TextFieldWidget(this.textRenderer, x + 62, y2, half - 62, 20, Text.literal("Starting bid"));
		startField.setMaxLength(16);
		startField.setText(shortMoney(c.bidStart));
		startField.setChangedListener(t -> {
			Double v = ProfitConfig.parseAmount(t);
			if (v != null && v >= 1) c.bidStart = v;
		});
		addDrawableChild(startField);
		addDrawable((ctx, mx, my, d) -> {
			Double v = ProfitConfig.parseAmount(startField.getText());
			if (startField.getText().isEmpty() && !startField.isFocused()) {
				ctx.drawText(this.textRenderer, "e.g. 100k", x + 62 + 5, y2 + 6, 0xFF707080, false);
			} else if (v == null || v < 1) {
				ctx.drawText(this.textRenderer, "invalid", x + half - 38, y2 + 6, 0xFFFF6B6B, false);
			}
		});
		addDrawableChild(new TimeSlider(x + half + 6, y2, half, 20, c.bidDuration, v -> c.bidDuration = v));

		// ---- row 3: rules
		int y3 = py + 88;
		modeBtn = addDrawableChild(ButtonWidget.builder(modeText(), b -> {
			c.bidMode = c.bidMode == 0 ? 1 : 0;
			b.setMessage(modeText());
		}).dimensions(x, y3, half, 20).build());
		snipeBtn = addDrawableChild(ButtonWidget.builder(snipeText(), b -> {
			c.bidAntiSnipe = !c.bidAntiSnipe;
			b.setMessage(snipeText());
		}).dimensions(x + half + 6, y3, half, 20).build());

		// ---- row 4: actions
		int y4 = py + 118;
		int gap = 4;
		int bw = (w - gap * 4) / 5;
		startBtn = addDrawableChild(ButtonWidget.builder(Text.literal("▶ START").formatted(Formatting.GREEN, Formatting.BOLD), b -> {
			BidAuctionManager.start();
			openBoard();
		}).dimensions(x, y4, bw, 20).build());
		endBtn = addDrawableChild(ButtonWidget.builder(Text.literal("END NOW").formatted(Formatting.GOLD, Formatting.BOLD), b -> BidAuctionManager.endNow())
				.dimensions(x + (bw + gap), y4, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE").formatted(Formatting.RED), b -> BidAuctionManager.reset())
				.dimensions(x + (bw + gap) * 2, y4, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Board").formatted(Formatting.AQUA), b -> openBoard())
				.dimensions(x + (bw + gap) * 3, y4, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("‹ Wheel"), b -> {
			if (this.client != null) this.client.setScreen(new AuctionScreen());
		}).dimensions(x + (bw + gap) * 4, y4, bw, 20).build());

		// ---- volume
		addDrawableChild(new ValueSlider(x, py + 206, w, 20, "♫ Volume", 0, 100, c.auctionVolume, "%", v -> c.auctionVolume = v));
	}

	private Text modeText() {
		return Text.literal(ProfitConfig.get().bidMode == 1 ? "Rule: Highest single payment" : "Rule: Payments add up");
	}

	private Text snipeText() {
		return Text.literal("Anti-snipe +" + BidAuctionManager.SNIPE_SECONDS + "s: " + (ProfitConfig.get().bidAntiSnipe ? "ON" : "OFF"));
	}

	private void openBoard() {
		if (this.client != null) this.client.setScreen(new BidBoardScreen());
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
			BidAuctionManager.event("Hold an item in your main hand first");
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
		private final java.util.function.Consumer<Integer> set;

		ValueSlider(int x, int y, int w, int h, String label, double min, double max, double cur, String unit, java.util.function.Consumer<Integer> set) {
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

	/** Seconds, 15..900 in 5 second steps, shown as m:ss. */
	private static final class TimeSlider extends SliderWidget {
		private static final int MIN = 15, MAX = 900;
		private final java.util.function.Consumer<Integer> set;

		TimeSlider(int x, int y, int w, int h, int cur, java.util.function.Consumer<Integer> set) {
			super(x, y, w, h, Text.empty(), Math.max(0.0, Math.min(1.0, (cur - MIN) / (double) (MAX - MIN))));
			this.set = set;
			updateMessage();
		}

		private int seconds() {
			int s = (int) Math.round((MIN + this.value * (MAX - MIN)) / 5.0) * 5;
			return Math.max(MIN, Math.min(MAX, s));
		}

		@Override
		protected void updateMessage() {
			if (set == null) return;
			setMessage(Text.literal("Time: " + BidAuctionManager.clock(seconds())));
		}

		@Override
		protected void applyValue() {
			set.accept(seconds());
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
			BidAuctionManager.update();
			TextRenderer tr = textRenderer;
			ProfitConfig c = ProfitConfig.get();

			ctx.fill(0, 0, width, height, 0x66000000);
			Gfx.rrect(ctx, px - 2, py - 2, PW + 4, PH + 4, 6, 0xFFE0A030);
			Gfx.rrect(ctx, px, py, PW, PH, 5, 0xF01A2236);

			String title = "★ FOOMC'S BIDDING AUCTION ★";
			ctx.drawText(tr, title, px + (PW - tr.getWidth(title)) / 2, py + 10, 0xFFFFD34D, true);
			ctx.fill(x, py + 26, x + w, py + 27, 0x33E0A030);

			ItemStack st = AuctionManager.prizeStack();
			if (!st.isEmpty()) ctx.drawItem(st, x + 27, py + 38);

			ctx.drawText(tr, "Start $", x, py + 62 + 6, 0xFFE8F0FF, false);

			String start = BidAuctionManager.money(c.bidStart);
			String rule = c.bidMode == 1
					? "Highest single payment wins. Bids must be at least " + start + "."
					: "Every payment adds to that person's total. Highest total wins. Min " + start + ".";
			ctx.drawText(tr, Gfx.fit(tr, rule, w), x, py + 146, 0xFFB8C4D8, false);
			ctx.drawText(tr, Gfx.fit(tr, "Ties go to whoever reached the amount first. Money paid still counts as profit.", w), x, py + 158, 0xFF6F7C95, false);

			String me = MinecraftClient.getInstance().getSession().getUsername();
			String status;
			int col;
			switch (BidAuctionManager.state) {
				case RUNNING -> {
					BidAuctionManager.Bid l = BidAuctionManager.leader();
					status = "LIVE " + BidAuctionManager.clock(BidAuctionManager.remainingSeconds()) + " • "
							+ (l == null ? "no bids yet" : l.name + " leads with " + BidAuctionManager.money(l.value));
					col = 0xFF55FF9A;
				}
				case ENDED -> {
					status = BidAuctionManager.winner == null ? "Ended with no bids"
							: "Sold to " + BidAuctionManager.winner + " for " + BidAuctionManager.money(BidAuctionManager.winnerValue);
					col = 0xFFFFD34D;
				}
				default -> {
					status = "Ready. Viewers bid with /pay " + me + " <amount>";
					col = 0xFFE8F0FF;
				}
			}
			String shown = Gfx.fit(tr, status, w);
			ctx.drawText(tr, shown, px + (PW - tr.getWidth(shown)) / 2, py + 182, col, true);

			String foot = "B menu • /profit bid";
			ctx.drawText(tr, foot, px + (PW - tr.getWidth(foot)) / 2, py + PH - 14, 0xFF6F7C95, false);

			boolean running = BidAuctionManager.state == BidAuctionManager.State.RUNNING;
			startBtn.active = !running;
			endBtn.active = running;
		}
	}
}
