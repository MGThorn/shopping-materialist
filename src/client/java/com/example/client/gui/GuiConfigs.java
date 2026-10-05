package com.example.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import com.example.Reference;
import com.example.client.config.Hotkeys;

public class GuiConfigs extends GuiConfigsBase {
	// Static so the last selected tab is remembered when the GUI is reopened
	private static ConfigGuiTab tab = ConfigGuiTab.ALL;

	public GuiConfigs() {
		this(null);
	}

	public GuiConfigs(@Nullable Screen parent) {
		super(10, 50, Reference.MOD_ID, parent, Reference.MOD_ID + ".gui.title.configs");
	}

	@Override
	public void initGui() {
		super.initGui();

		int x = 10;
		int y = 26;

		for (ConfigGuiTab guiTab : ConfigGuiTab.values()) {
			x += this.createTabButton(x, y, guiTab);
		}
	}

	private int createTabButton(int x, int y, ConfigGuiTab guiTab) {
		String label = guiTab.getDisplayName();
		int width = this.getStringWidth(label) + 10;

		ButtonGeneric button = new ButtonGeneric(x, y, width, 20, label);
		button.setEnabled(tab != guiTab);
		this.addButton(button, (btn, mouseButton) -> {
			tab = guiTab;
			this.clearElements();
			this.reCreateListWidget();
			this.initGui();
		});

		return width + 2;
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		List<ConfigOptionWrapper> configs = new ArrayList<>();

		switch (tab) {
			case ALL -> {
				configs.addAll(ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST));
			}
			case GENERIC -> {
				configs.addAll(ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST));
			}
		}

		return configs;
	}

	public enum ConfigGuiTab {
		ALL("all"),
		GENERIC("generic");

		private final String translationKey;

		ConfigGuiTab(String name) {
			this.translationKey = Reference.MOD_ID + ".gui.button.config_gui." + name;
		}

		public String getDisplayName() {
			return StringUtils.translate(this.translationKey);
		}
	}
}
