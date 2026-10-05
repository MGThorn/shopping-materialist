package com.example.client;

import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import com.example.Reference;
import com.example.client.config.Hotkeys;

public class KeybindProvider implements IKeybindProvider {
	@Override
	public void addKeysToMap(IKeybindManager manager) {
		for (ConfigHotkey hotkey : Hotkeys.ALL_HOTKEYS) {
			manager.addKeybindToMap(hotkey.getKeybind());
		}
	}

	@Override
	public void addHotkeys(IKeybindManager manager) {
		manager.addHotkeysForCategory(Reference.MOD_NAME, Reference.MOD_ID + ".config.hotkeys", Hotkeys.HOTKEY_LIST);
	}
}
