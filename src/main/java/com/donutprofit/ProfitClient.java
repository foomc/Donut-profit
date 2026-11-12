package com.donutprofit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Donut Profit: a rainbow "Profit of the Day" HUD.
 * All state lives in memory only, so it resets whenever the game is closed.
 */
public class ProfitClient implements ClientModInitializer {
	public static final String MOD_ID = "donutprofit";

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> ProfitTracker.tick(client));

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) ProfitTracker.onChat(message.getString());
		});

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.of(MOD_ID, "profit_hud"),
				ProfitHud::render
		);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("profit")
						.then(ClientCommandManager.literal("reset").executes(ctx -> {
							ProfitTracker.reset();
							ctx.getSource().sendFeedback(Text.literal("Profit of the day reset."));
							return 1;
						}))
						.then(ClientCommandManager.literal("toggle").executes(ctx -> {
							ProfitHud.visible = !ProfitHud.visible;
							ctx.getSource().sendFeedback(Text.literal("Profit HUD " + (ProfitHud.visible ? "shown" : "hidden") + "."));
							return 1;
						}))
						.then(ClientCommandManager.literal("debug").executes(ctx -> {
							List<String> lines = ProfitTracker.sidebarLines(MinecraftClient.getInstance());
							ctx.getSource().sendFeedback(Text.literal("Sidebar lines (" + lines.size() + "):"));
							for (String l : lines) ctx.getSource().sendFeedback(Text.literal(" | " + l));
							ctx.getSource().sendFeedback(Text.literal(ProfitTracker.status()));
							return 1;
						}))));
	}
}
