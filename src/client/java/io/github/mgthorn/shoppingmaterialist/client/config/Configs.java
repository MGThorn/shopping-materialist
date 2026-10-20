package io.github.mgthorn.shoppingmaterialist.client.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import io.github.mgthorn.shoppingmaterialist.Reference;

public class Configs {
	public static class Generic {
		private static final String GENERIC_KEY = Reference.MOD_ID + ".config.generic";

		// Amount added/removed per Ctrl+click in the shopping list editor
		public static final ConfigInteger CTRL_CLICK_AMOUNT = new ConfigInteger("ctrlClickAmount", 8, 2, 256, true).apply(GENERIC_KEY);

		// Adds a button to the wandering trader's trading screen that creates a shopping list from its trades
		public static final ConfigBoolean WANDERING_TRADER_BUTTON = new ConfigBoolean("wanderingTraderButton", true).apply(GENERIC_KEY);

		// The same button for villagers
		public static final ConfigBoolean VILLAGER_BUTTON = new ConfigBoolean("villagerButton", false).apply(GENERIC_KEY);

		// Create a new numbered list (name_2, name_3...) instead of overwriting the existing one
		public static final ConfigBoolean WANDERING_TRADER_NEW_LIST = new ConfigBoolean("wanderingTraderNewList", false).apply(GENERIC_KEY);
		public static final ConfigBoolean VILLAGER_NEW_LIST = new ConfigBoolean("villagerNewList", false).apply(GENERIC_KEY);
		public static final ConfigBoolean MATERIAL_LIST_NEW_LIST = new ConfigBoolean("materialListNewList", false).apply(GENERIC_KEY);

		public static final List<IConfigBase> OPTIONS = ImmutableList.of(
				CTRL_CLICK_AMOUNT,
				WANDERING_TRADER_BUTTON,
				VILLAGER_BUTTON,
				WANDERING_TRADER_NEW_LIST,
				VILLAGER_NEW_LIST,
				MATERIAL_LIST_NEW_LIST
		);
	}
}
