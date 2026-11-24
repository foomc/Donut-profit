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

	public static final String[] MENU_ACCENTS = {"Blue", "Green", "Pink", "Orange", "White", "Custom", "Match HUD"};
	public static final String[] MENU_CORNERS = {"Square", "Soft", "Round"};
	public static final String[] MENU_THEMES = {"Dark", "Black", "Slate"};

	public static final String[] MIN_PAYMENT_LABELS = {"Off", "$10", "$100", "$1K", "$10K", "$100K", "$1M", "$10M", "$100M"};
	public static final double[] MIN_PAYMENT_VALUES = {0, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

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
	public boolean showPayments = true;   // payments list under the profit panel
	public int paymentRows = 5;           // how many payments show at once
	// wheel auction
	public double auctionEntry = 1_000_000;  // price of one slot
	public int auctionMaxSlots = 5;          // max slots per person
	public int auctionSpin = 10;             // seconds
	public int auctionPalette = 0;           // wheel colours
	public int auctionVolume = 70;
	public int auctionPrize = 0;             // preset item index (-1 = held item, runtime only)
	public String auctionPrizeName = "";     // custom prize name, empty = item name

	public double minPayment = 1_000;     // payments below this are not listed (profit still counts them)

	// menu appearance
	public int menuAccent = 0;        // index into MENU_ACCENTS
	public int menuCorners = 1;       // index into MENU_CORNERS
	public int menuTheme = 0;         // index into MENU_THEMES
	public int menuHue = 210;         // 0-360, used when accent = Custom
	public int menuOpacity = 96;      // panel opacity, 40-100
	public boolean menuAnimations = true;
	public boolean menuDim = true;
	public boolean menuPreview = true;   // draw the HUD on top of the menu
	public boolean menuShadow = false;   // text shadow in the menu
	public boolean menuHighlight = true; // row hover highlight

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

	private static final java.util.regex.Pattern AMOUNT_TEXT =
			java.util.regex.Pattern.compile("^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?|\\.[0-9]+)\\s*([kKmMbBtT])?$");

	/** Parses "500", "$5k", "2.5m", "1,000", "1b"... Returns null when the text isn't an amount. */
	public static Double parseAmount(String text) {
		if (text == null) return null;
		java.util.regex.Matcher m = AMOUNT_TEXT.matcher(text.trim());
		if (!m.matches()) return null;
		try {
			double v = Double.parseDouble(m.group(1).replace(",", ""));
			String suf = m.group(2);
			if (suf != null) {
				switch (Character.toUpperCase(suf.charAt(0))) {
					case 'K' -> v *= 1_000d;
					case 'M' -> v *= 1_000_000d;
					case 'B' -> v *= 1_000_000_000d;
					case 'T' -> v *= 1_000_000_000_000d;
					default -> {}
				}
			}
			return v;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Index of the preset closest to the given minimum payment. */
	public static int minPaymentIndex(double v) {
		int best = 0;
		for (int i = 0; i < MIN_PAYMENT_VALUES.length; i++) {
			if (Math.abs(MIN_PAYMENT_VALUES[i] - v) < Math.abs(MIN_PAYMENT_VALUES[best] - v)) best = i;
		}
		return best;
	}

	private void clamp() {
		anchor = Math.floorMod(anchor, ANCHORS.length);
		colorMode = Math.floorMod(colorMode, COLOR_MODES.length);
		menuAccent = Math.floorMod(menuAccent, MENU_ACCENTS.length);
		menuCorners = Math.floorMod(menuCorners, MENU_CORNERS.length);
		menuTheme = Math.floorMod(menuTheme, MENU_THEMES.length);
		paymentRows = Math.max(1, Math.min(8, paymentRows));
		minPayment = Math.max(0, minPayment);
		auctionEntry = Math.max(1, auctionEntry);
		auctionMaxSlots = Math.max(1, Math.min(20, auctionMaxSlots));
		auctionSpin = Math.max(3, Math.min(30, auctionSpin));
		auctionPalette = Math.floorMod(auctionPalette, 5);
		auctionVolume = Math.max(0, Math.min(100, auctionVolume));
		if (auctionPrize < 0 || auctionPrize > 6) auctionPrize = 0;
		if (auctionPrizeName == null) auctionPrizeName = "";
		menuHue = Math.max(0, Math.min(360, menuHue));
		menuOpacity = Math.max(40, Math.min(100, menuOpacity));
		scale = Math.max(0.5f, Math.min(2.5f, scale));
		backgroundOpacity = Math.max(0, Math.min(100, backgroundOpacity));
		rainbowSpeed = Math.max(0.1f, Math.min(4f, rainbowSpeed));
		offsetX = Math.max(-1000, Math.min(1000, offsetX));
		offsetY = Math.max(0, Math.min(1000, offsetY));
	}
}
