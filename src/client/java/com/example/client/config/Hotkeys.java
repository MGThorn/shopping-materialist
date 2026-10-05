package com.example.client.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import com.example.Reference;

public class Hotkeys {
	private static final String HOTKEYS_KEY = Reference.MOD_ID + ".config.hotkeys";

	public static final ConfigHotkey OPEN_GUI_MAIN_MENU = new ConfigHotkey("openGuiMainMenu", "A,C").apply(HOTKEYS_KEY);
	public static final ConfigHotkey OPEN_GUI_CONFIGS   = new ConfigHotkey("openGuiConfigs",  "").apply(HOTKEYS_KEY);
	public static final ConfigHotkey OPEN_GUI_SHOPPING_LIST = new ConfigHotkey("openGuiShoppingList", "").apply(HOTKEYS_KEY);

	// Hotkeys shown in the config GUI and saved to the config file
	public static final List<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
			OPEN_GUI_CONFIGS,
			OPEN_GUI_SHOPPING_LIST
	);

	// Every hotkey that is active in game, including fixed ones like OPEN_GUI_MAIN_MENU
	public static final List<ConfigHotkey> ALL_HOTKEYS = ImmutableList.<ConfigHotkey>builder()
			.add(OPEN_GUI_MAIN_MENU)
			.addAll(HOTKEY_LIST)
			.build();
}
