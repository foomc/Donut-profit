package com.donutprofit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Donut Profit: a rainbow "Profit of the Day" HUD.
 * The profit lives in memory only, so it resets whenever the game is closed.
 */
public class ProfitClient implements ClientModInitializer {
	public static final String MOD_ID = "donutprofit";

	private static KeyBinding openMenuKey;

	@Override
	public void onInitializeClient() {
		ProfitConfig.load();

		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.donutprofit.menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ProfitTracker.tick(client);
			while (openMenuKey.wasPressed()) {
				if (client.currentScreen == null) client.setScreen(new ProfitConfigScreen(null));
			}
		});

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
						.then(ClientCommandManager.literal("menu").executes(ctx -> {
							MinecraftClient mc = MinecraftClient.getInstance();
							mc.send(() -> mc.setScreen(new ProfitConfigScreen(null)));
							return 1;
						}))
						.then(ClientCommandManager.literal("reset").executes(ctx -> {
							ProfitTracker.reset();
							ctx.getSource().sendFeedback(Text.literal("Profit of the day reset."));
							return 1;
						}))
						.then(ClientCommandManager.literal("toggle").executes(ctx -> {
							ProfitConfig cfg = ProfitConfig.get();
							cfg.enabled = !cfg.enabled;
							ProfitConfig.save();
							ctx.getSource().sendFeedback(Text.literal("Profit HUD " + (cfg.enabled ? "shown" : "hidden") + "."));
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
