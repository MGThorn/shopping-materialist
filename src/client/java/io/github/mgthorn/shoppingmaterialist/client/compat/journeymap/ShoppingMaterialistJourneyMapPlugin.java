package io.github.mgthorn.shoppingmaterialist.client.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * Registered through the {@code journeymap} entrypoint in fabric.mod.json, so JourneyMap only loads it when it's installed.
 * It just keeps the API for {@link JourneyMapWaypoints}.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class ShoppingMaterialistJourneyMapPlugin implements IClientPlugin {
	@Override
	public String getModId() {
		return Reference.MOD_ID;
	}

	@Override
	public void initialize(IClientAPI api) {
		JourneyMapWaypoints.setApi(api);
	}
}
