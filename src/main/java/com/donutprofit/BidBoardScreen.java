package com.donutprofit;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

/** Full-screen bidding board: prize, big countdown, leaderboard and the winner banner. */
public class BidBoardScreen extends Screen {
	private ButtonWidget mainBtn;
	private final float[][] confetti = new float[60][5];
	private long confettiStart = 0;

	public BidBoardScreen() {
		super(Text.literal("Auction Board"));
		Random r = new Random();
		for (float[] c : confetti) {
			c[0] = r.nextFloat();
			c[1] = r.nextFloat();
			c[2] = 0.15f + r.nextFloat() * 0.3f;
			c[3] = r.nextFloat();
			c[4] = 2 + r.nextInt(3);
		}
	}

	@Override
	protected void init() {
		addDrawable((ctx, mx, my, d) -> ctx.fill(0, 0, width, height, 0xEA0A0F1A));
		addDrawable(new Board());

		int cx = this.width / 2;
		int by = this.height - 28;
		mainBtn = addDrawableChild(ButtonWidget.builder(Text.literal("START"), b -> {
			if (BidAuctionManager.state == BidAuctionManager.State.RUNNING) BidAuctionManager.endNow();
			else BidAuctionManager.start();
		}).dimensions(cx - 110, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Menu"), b -> {
			if (this.client != null) this.client.setScreen(new BidMenuScreen());
		}).dimensions(cx + 10, by, 100, 20).build());

		final int xx = this.width - 28, xy = 8;
		addDrawableChild(ButtonWidget.builder(Text.empty(), b -> close()).dimensions(xx, xy, 20, 20).build()).setAlpha(0f);
		addDrawable(new CloseVisual(xx, xy, 20, 20));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(null);
	}

	private static final class CloseVisual implements Drawable {
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

	private final class Board implements Drawable {
		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			BidAuctionManager.update();
			TextRenderer tr = textRenderer;
			float t = (System.currentTimeMillis() % 6000L) / 6000f;
			BidAuctionManager.State st = BidAuctionManager.state;
			boolean running = st == BidAuctionManager.State.RUNNING;
			double rem = BidAuctionManager.remainingSeconds();

			// title
			String title = "★ AUCTION ★";
			float ts = 2.2f;
			Gfx.rainbow(ctx, tr, title, (width - Gfx.textWidth(tr, title, ts)) / 2f, 12, ts, t, 0.6f);

			int mid = width / 2;
			int top = 56;

			// ---- left: prize
			int lx = Math.max(20, mid - 250);
			int lw = Math.min(200, mid - 30);
			ItemStack prize = running || st == BidAuctionManager.State.ENDED ? BidAuctionManager.prizeStack : AuctionManager.prizeStack();
			String pname = running || st == BidAuctionManager.State.ENDED ? BidAuctionManager.prizeName : AuctionManager.prizeName();
			Gfx.rrect(ctx, lx, top, lw, 150, 6, 0xFF2A3350);
			Gfx.rrect(ctx, lx + 1, top + 1, lw - 2, 148, 5, 0xFF141B2E);
			if (!prize.isEmpty()) {
				float bob = (float) Math.sin(System.currentTimeMillis() / 400.0) * 3f;
				float sc = 5f;
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(lx + lw / 2f - 8 * sc, top + 26 + bob);
				ctx.getMatrices().scale(sc, sc);
				ctx.drawItem(prize, 0, 0);
				ctx.getMatrices().popMatrix();
			}
			String pn = Gfx.fit(tr, pname, lw - 12);
			ctx.drawText(tr, pn, lx + (lw - tr.getWidth(pn)) / 2, top + 126, 0xFFFFFFFF, true);

			// ---- right: countdown + leader
			int rx = mid - 30;
			int rw = Math.min(280, width - rx - 20);
			String clock;
			int col;
			if (running) {
				clock = BidAuctionManager.clock(rem);
				col = rem <= 10 ? 0xFFFF5555 : rem <= 30 ? 0xFFFFD34D : 0xFF55FF9A;
				if (rem <= 10 && (System.currentTimeMillis() / 250) % 2 == 0) col = 0xFFFFAAAA;
			} else if (st == BidAuctionManager.State.ENDED) {
				clock = "SOLD!";
				col = 0xFFFFD34D;
			} else {
				clock = BidAuctionManager.clock(ProfitConfig.get().bidDuration);
				col = 0xFF8090B0;
			}
			float cs = 4f;
			Gfx.text(ctx, tr, clock, rx, top, cs, col, true);

			BidAuctionManager.Bid lead = BidAuctionManager.leader();
			String sub;
			if (st == BidAuctionManager.State.ENDED) {
				sub = BidAuctionManager.winner == null ? "No bids" : BidAuctionManager.winner + " wins with " + BidAuctionManager.money(BidAuctionManager.winnerValue);
			} else if (lead == null) {
				sub = "Starting bid " + BidAuctionManager.money(ProfitConfig.get().bidStart);
			} else {
				sub = "Top bid " + BidAuctionManager.money(lead.value) + " by " + lead.name;
			}
			ctx.drawText(tr, Gfx.fit(tr, sub, rw), rx, top + 38, 0xFFE8F0FF, true);

			// ---- leaderboard
			List<BidAuctionManager.Bid> list = BidAuctionManager.top(8);
			double best = list.isEmpty() ? 1 : Math.max(1, list.get(0).value);
			int rowY = top + 56;
			for (int i = 0; i < 8; i++) {
				int y = rowY + i * 13;
				ctx.fill(rx, y, rx + rw, y + 11, 0x20FFFFFF);
				if (i < list.size()) {
					BidAuctionManager.Bid b = list.get(i);
					int barW = (int) Math.max(2, (rw - 2) * (b.value / best));
					int bc = i == 0 ? 0xFFE0A030 : Gfx.hsb(0.55f + i * 0.04f, 0.5f, 0.75f);
					ctx.fill(rx + 1, y + 1, rx + 1 + barW, y + 10, Gfx.withAlpha(bc, 0.55f));
					ctx.drawText(tr, (i + 1) + ". " + Gfx.fit(tr, b.name, rw - 90), rx + 4, y + 2, 0xFFFFFFFF, true);
					String v = BidAuctionManager.money(b.value);
					ctx.drawText(tr, v, rx + rw - 4 - tr.getWidth(v), y + 2, 0xFFFFFFFF, true);
				}
			}
			if (list.isEmpty()) {
				ctx.drawText(tr, "Waiting for bids...", rx + 6, rowY + 3, 0xFF6F7C95, false);
			}

			// ---- hint + ticker
			String me = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
			String hint = "Bid with /pay " + me + " <amount>";
			ctx.drawText(tr, hint, (width - tr.getWidth(hint)) / 2, height - 64, 0xFFFFD34D, true);
			long age = (System.nanoTime() - BidAuctionManager.lastEventNanos) / 1_000_000_000L;
			if (!BidAuctionManager.lastEvent.isEmpty() && age < 8) {
				String ev = Gfx.fit(tr, BidAuctionManager.lastEvent, width - 40);
				ctx.drawText(tr, ev, (width - tr.getWidth(ev)) / 2, height - 48, 0xFFB8C4D8, false);
			}

			// ---- winner banner + confetti
			if (st == BidAuctionManager.State.ENDED && BidAuctionManager.winner != null) {
				if (BidAuctionManager.justFinished) {
					BidAuctionManager.justFinished = false;
					confettiStart = System.nanoTime();
				}
				String w = BidAuctionManager.winner;
				int bw = Math.min(width - 40, Math.max(220, tr.getWidth(w) * 3 + 40));
				int bx = (width - bw) / 2, by = top + 160;
				Gfx.rrect(ctx, bx - 2, by - 2, bw + 4, 44, 7, 0xFFE0A030);
				Gfx.rrect(ctx, bx, by, bw, 40, 6, 0xF0141B2E);
				String l1 = "WINNER";
				ctx.drawText(tr, l1, (width - tr.getWidth(l1)) / 2, by + 5, 0xFFFFD34D, true);
				float ws = 2f;
				Gfx.text(ctx, tr, w, (width - Gfx.textWidth(tr, w, ws)) / 2f, by + 18, ws, 0xFFFFFFFF, true);

				float el = (System.nanoTime() - confettiStart) / 1_000_000_000f;
				if (el < 6f) {
					for (float[] c : confetti) {
						float cy = ((c[1] + el * c[2]) % 1f) * height;
						float cx = c[0] * width + (float) Math.sin(el * 3 + c[3] * 6) * 14;
						ctx.fill((int) cx, (int) cy, (int) cx + (int) c[4], (int) cy + (int) c[4], Gfx.hsb(c[3], 0.8f, 1f));
					}
				}
			}

			mainBtn.setMessage(running
					? Text.literal("END NOW").formatted(Formatting.GOLD, Formatting.BOLD)
					: Text.literal("START").formatted(Formatting.GREEN, Formatting.BOLD));
		}
	}
}
