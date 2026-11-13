package com.donutprofit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, saved to config/donutprofit.json. (The money itself is never saved.) */
public final class ProfitConfig {
	public static final String[] ANCHORS = {"Top Center", "Top Left", "Top Right"};
	public static final String[] COLOR_MODES = {"Rainbow", "Green", "Gold", "White", "Cyan", "Pink"};
	public static final int[] COLOR_VALUES = {0, 0x55FF55, 0xFFC400, 0xFFFFFF, 0x55FFFF, 0xFF66CC};

	public boolean enabled = true;
	public int anchor = 0;            // index into ANCHORS
	public int offsetX = 0;           // pixels from the anchor
	public int offsetY = 6;           // pixels from the top
	public float scale = 1.0f;        // whole-panel scale
	public int backgroundOpacity = 82; // 0-100
	public float rainbowSpeed = 1.0f;
	public int colorMode = 0;         // index into COLOR_MODES
	public boolean showTitle = true;
	public boolean showBorder = true;
	public boolean showAccentBar = true;
	public boolean showPlusSign = true;
	public boolean countSales = true;
	public boolean countPayments = true;
	public boolean syncWithSidebar = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ProfitConfig instance;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("donutprofit.json");
	}

	public static ProfitConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				instance = GSON.fromJson(Files.readString(f), ProfitConfig.class);
			}
		} catch (Exception ignored) {
		}
		if (instance == null) instance = new ProfitConfig();
		instance.clamp();
	}

	public static void save() {
		try {
			get().clamp();
			Files.writeString(file(), GSON.toJson(get()));
		} catch (IOException ignored) {
		}
	}

	public static void resetToDefaults() {
		instance = new ProfitConfig();
		save();
	}

	private void clamp() {
		anchor = Math.floorMod(anchor, ANCHORS.length);
		colorMode = Math.floorMod(colorMode, COLOR_MODES.length);
		scale = Math.max(0.5f, Math.min(2.5f, scale));
		backgroundOpacity = Math.max(0, Math.min(100, backgroundOpacity));
		rainbowSpeed = Math.max(0.1f, Math.min(4f, rainbowSpeed));
		offsetX = Math.max(-1000, Math.min(1000, offsetX));
		offsetY = Math.max(0, Math.min(1000, offsetY));
	}
}
