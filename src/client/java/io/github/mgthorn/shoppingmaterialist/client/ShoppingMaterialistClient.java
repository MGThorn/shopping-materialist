package io.github.mgthorn.shoppingmaterialist.client;

import net.fabricmc.api.ClientModInitializer;
import fi.dy.masa.malilib.event.InitializationHandler;

public class ShoppingMaterialistClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
	}
}
