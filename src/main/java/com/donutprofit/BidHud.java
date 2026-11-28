package com.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Small top-right panel shown while a bidding auction is running (and briefly after it ends). */
public final class BidHud {
	private BidHud() {}

	public static void render(DrawContext ctx, RenderTickCounter tick) {
		BidAuctionManager.State s = BidAuctionManager.state;
		if (s == BidAuctionManager.State.IDLE) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen instanceof BidBoardScreen || mc.currentScreen instanceof BidMenuScreen) return;
		if (s == BidAuctionManager.State.ENDED && (System.nanoTime() - BidAuctionManager.finishedAt) / 1_000_000_000L > 12) return;

		TextRenderer tr = mc.textRenderer;
		int w = 150, h = 52;
		int x = mc.getWindow().getScaledWidth() - w - 6, y = 6;
		boolean running = s == BidAuctionManager.State.RUNNING;
		double rem = BidAuctionManager.remainingSeconds();
		int accent = running ? (rem <= 10 ? 0xFFFF5555 : 0xFFE0A030) : 0xFF55FF9A;

		Gfx.rrect(ctx, x - 1, y - 1, w + 2, h + 2, 4, accent);
		Gfx.rrect(ctx, x, y, w, h, 3, 0xE61A2236);

		ctx.drawText(tr, Gfx.fit(tr, BidAuctionManager.prizeName, w - 40), x + 8, y + 6, 0xFFFFFFFF, true);
		if (!BidAuctionManager.prizeStack.isEmpty()) ctx.drawItem(BidAuctionManager.prizeStack, x + w - 22, y + 4);

		String clock = running ? BidAuctionManager.clock(rem) : "SOLD";
		Gfx.text(ctx, tr, clock, x + 8, y + 20, 1.5f, accent, true);

		String line;
		if (running) {
			BidAuctionManager.Bid l = BidAuctionManager.leader();
			line = l == null ? "No bids yet" : l.name + " " + BidAuctionManager.money(l.value);
		} else {
			line = BidAuctionManager.winner == null ? "No bids" : BidAuctionManager.winner + " " + BidAuctionManager.money(BidAuctionManager.winnerValue);
		}
		ctx.drawText(tr, Gfx.fit(tr, line, w - 16), x + 8, y + 38, 0xFFB8C4D8, false);
	}
}
