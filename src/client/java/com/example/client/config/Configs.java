package com.example.client.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import com.example.Reference;

public class Configs {
	public static class Generic {
		private static final String GENERIC_KEY = Reference.MOD_ID + ".config.generic";

		// Amount added/removed per Ctrl+click in the shopping list editor
		public static final ConfigInteger CTRL_CLICK_AMOUNT = new ConfigInteger("ctrlClickAmount", 8, 2, 256, true).apply(GENERIC_KEY);

		public static final List<IConfigBase> OPTIONS = ImmutableList.of(
				CTRL_CLICK_AMOUNT
		);
	}
}
