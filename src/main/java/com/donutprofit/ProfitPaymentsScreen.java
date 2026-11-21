package com.donutprofit;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * An empty screen that just gives you a mouse cursor so you can tick off payments.
 * The HUD (with the payments list) is drawn on top of it by {@link ProfitHud#drawOnScreen}.
 */
public class ProfitPaymentsScreen extends Screen {
	public ProfitPaymentsScreen() {
		super(Text.literal("Payments"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		String hint = "Click a box to tick a payment off  -  Esc to close";
		ctx.drawText(this.textRenderer, hint, (this.width - this.textRenderer.getWidth(hint)) / 2, this.height - 40, 0xFFAAAAAA, true);
	}
}
