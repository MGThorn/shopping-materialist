package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.ShoppingMaterialist;

/**
 * Holds all trading setups, saved together in {@code config/shopping-materialist/trading_setups.json}.
 * The same setups are used in every world.
 */
public class TradingSetupManager {
	private static final TradingSetupManager INSTANCE = new TradingSetupManager();

	private final List<TradingSetup> setups = new ArrayList<>();
	private int selected;
	private boolean loaded;

	public static TradingSetupManager getInstance() {
		return INSTANCE;
	}

	public static String getDefaultName() {
		return StringUtils.translate(Reference.MOD_ID + ".gui.trading.default_setup_name");
	}

	private Path getFile() {
		return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("trading_setups.json");
	}

	public List<TradingSetup> getSetups() {
		this.ensureLoaded();
		return this.setups;
	}

	public TradingSetup getSelected() {
		this.ensureLoaded();
		return this.setups.get(this.selected);
	}

	public void select(int offset) {
		this.ensureLoaded();
		this.selected = Math.floorMod(this.selected + offset, this.setups.size());
		this.save();
	}

	@Nullable
	public TradingSetup getByName(String name) {
		for (TradingSetup setup : this.getSetups()) {
			if (setup.getName().equalsIgnoreCase(name)) {
				return setup;
			}
		}

		return null;
	}

	/**
	 * Adds a new setup with the default items and selects it
	 */
	public TradingSetup create(String name) {
		this.ensureLoaded();
		TradingSetup setup = TradingSetup.createDefault(name);
		this.setups.add(setup);
		this.selected = this.setups.size() - 1;
		this.save();
		return setup;
	}

	/**
	 * Deletes the selected setup. The default setup is created again if it was the last one.
	 */
	public void deleteSelected() {
		this.ensureLoaded();
		this.setups.remove(this.selected);

		if (this.setups.isEmpty()) {
			this.setups.add(TradingSetup.createDefault(getDefaultName()));
		}

		this.selected = Math.min(this.selected, this.setups.size() - 1);
		this.save();
	}

	private void ensureLoaded() {
		if (!this.loaded) {
			this.loaded = true;
			this.load();

			if (this.setups.isEmpty()) {
				this.setups.add(TradingSetup.createDefault(getDefaultName()));
			}

			this.selected = Math.max(0, Math.min(this.selected, this.setups.size() - 1));
		}
	}

	private void load() {
		Path file = this.getFile();

		if (!Files.isRegularFile(file)) {
			return;
		}

		JsonElement element = JsonUtils.parseJsonFile(file);

		if (element == null || !element.isJsonObject()) {
			ShoppingMaterialist.LOGGER.warn("Skipping invalid trading setup file '{}'", file);
			return;
		}

		JsonObject root = element.getAsJsonObject();

		if (root.has("setups") && root.get("setups").isJsonArray()) {
			for (JsonElement entry : root.getAsJsonArray("setups")) {
				if (entry.isJsonObject()) {
					this.setups.add(TradingSetup.fromJson(entry.getAsJsonObject()));
				}
			}
		}

		this.selected = root.has("selected") ? root.get("selected").getAsInt() : 0;
	}

	public void save() {
		this.ensureLoaded();

		try {
			Files.createDirectories(this.getFile().getParent());
		}
		catch (IOException e) {
			ShoppingMaterialist.LOGGER.error("Failed to create the config directory", e);
			return;
		}

		JsonObject root = new JsonObject();
		JsonArray array = new JsonArray();
		this.setups.forEach(setup -> array.add(setup.toJson()));
		root.add("setups", array);
		root.addProperty("selected", this.selected);
		JsonUtils.writeJsonToFile(root, this.getFile());
	}
}
