package io.github.mgthorn.shoppingmaterialist.client.config;

import java.nio.file.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

public class ConfigHandler implements IConfigHandler {
	private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

	@Override
	public void load() {
		Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);
		JsonElement element = JsonUtils.parseJsonFile(configFile);

		if (element != null && element.isJsonObject()) {
			JsonObject root = element.getAsJsonObject();
			ConfigUtils.readConfigBase(root, "Generic", Configs.Generic.OPTIONS);
			ConfigUtils.readHotkeys(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
		}
	}

	@Override
	public void save() {
		Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);
		JsonObject root = new JsonObject();
		ConfigUtils.writeConfigBase(root, "Generic", Configs.Generic.OPTIONS);
		ConfigUtils.writeHotkeys(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
		JsonUtils.writeJsonToFile(root, configFile);
	}
}
