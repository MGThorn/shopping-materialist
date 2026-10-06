package io.github.mgthorn.shoppingmaterialist.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

public class GuiMainMenu extends GuiBase {
	public GuiMainMenu() {
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.main_menu");
	}

	@Override
	public void initGui() {
		super.initGui();

		int x = 12;
		int y = 30;
		int width = this.getButtonWidth();

		this.createButton(x, y, width, ButtonType.SHOPPING_LISTS);
		y += 22;
		this.createButton(x, y, width, ButtonType.CONFIGURATION);
	}

	private void createButton(int x, int y, int width, ButtonType type) {
		ButtonGeneric button = new ButtonGeneric(x, y, width, 20, type.getDisplayName());
		this.addButton(button, (btn, mouseButton) -> type.open(this));
	}

	private int getButtonWidth() {
		int width = 0;

		for (ButtonType type : ButtonType.values()) {
			width = Math.max(width, this.getStringWidth(type.getDisplayName()) + 30);
		}

		return width;
	}

	private enum ButtonType {
		SHOPPING_LISTS("shopping_lists"),
		CONFIGURATION("configuration");

		private final String translationKey;

		ButtonType(String name) {
			this.translationKey = Reference.MOD_ID + ".gui.button.change_menu." + name;
		}

		String getDisplayName() {
			return StringUtils.translate(this.translationKey);
		}

		void open(GuiMainMenu parent) {
			switch (this) {
				case SHOPPING_LISTS -> GuiBase.openGui(new GuiShoppingLists(parent));
				case CONFIGURATION -> GuiBase.openGui(new GuiConfigs(parent));
			}
		}
	}
}
