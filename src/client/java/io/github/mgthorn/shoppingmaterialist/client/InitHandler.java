package io.github.mgthorn.shoppingmaterialist.client;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.config.ConfigHandler;
import io.github.mgthorn.shoppingmaterialist.client.config.Hotkeys;
import io.github.mgthorn.shoppingmaterialist.client.gui.GuiConfigs;
import io.github.mgthorn.shoppingmaterialist.client.gui.GuiMainMenu;
import io.github.mgthorn.shoppingmaterialist.client.gui.GuiShoppingLists;

public class InitHandler implements IInitializationHandler {
	@Override
	public void registerModHandlers() {
		ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new ConfigHandler());

		// Adds this mod to the mod switcher dropdown at the top of every MaLiLib config screen
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(
				new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new)
		);

		InputEventHandler.getKeybindManager().registerKeybindProvider(new KeybindProvider());

		Hotkeys.OPEN_GUI_MAIN_MENU.getKeybind().setCallback((action, key) -> {
			GuiBase.openGui(new GuiMainMenu());
			return true;
		});

		Hotkeys.OPEN_GUI_CONFIGS.getKeybind().setCallback((action, key) -> {
			GuiBase.openGui(new GuiConfigs());
			return true;
		});

		Hotkeys.OPEN_GUI_SHOPPING_LIST.getKeybind().setCallback((action, key) -> {
			GuiBase.openGui(new GuiShoppingLists(null));
			return true;
		});
	}
}
