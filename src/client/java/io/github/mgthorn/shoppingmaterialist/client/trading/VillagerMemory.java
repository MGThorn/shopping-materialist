package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.ShoppingMaterialist;

/**
 * Remembers every villager and wandering trader whose trading screen was opened, per world
 * ({@link WorldKey}). Stored in {@code config/shopping-materialist/villagers/<world key>.json}.
 */
public class VillagerMemory {
	private static final VillagerMemory INSTANCE = new VillagerMemory();

	private final Map<UUID, RememberedVillager> villagers = new LinkedHashMap<>();
	@Nullable private String loadedWorldKey;

	public static VillagerMemory getInstance() {
		return INSTANCE;
	}

	public Path getDirectory() {
		return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("villagers");
	}

	/**
	 * The remembered villagers of the current world, or an empty list outside of a world
	 */
	public List<RememberedVillager> getVillagers() {
		if (!this.ensureLoaded()) {
			return List.of();
		}

		return Collections.unmodifiableList(new ArrayList<>(this.villagers.values()));
	}

	/**
	 * Saves or updates a merchant with the offers of its open trading screen
	 */
	public void record(Entity merchant, MerchantOffers offers, int level) {
		String type;
		String profession = "";

		if (merchant instanceof Villager villager) {
			type = RememberedVillager.TYPE_VILLAGER;
			profession = villager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().toString()).orElse("");
		}
		else if (merchant instanceof WanderingTrader) {
			type = RememberedVillager.TYPE_WANDERING_TRADER;
		}
		else {
			return;
		}

		String dimension = WorldKey.getCurrentDimension();

		if (offers.isEmpty() || dimension == null || !this.ensureLoaded()) {
			return;
		}

		List<RememberedOffer> remembered = new ArrayList<>();

		for (MerchantOffer offer : offers) {
			remembered.add(RememberedOffer.of(offer));
		}

		this.villagers.put(merchant.getUUID(), new RememberedVillager(merchant.getUUID(), type, profession, level,
				dimension, merchant.blockPosition(), System.currentTimeMillis(), remembered));
		this.save();
	}

	public void remove(RememberedVillager villager) {
		if (this.ensureLoaded() && this.villagers.remove(villager.uuid) != null) {
			this.save();
		}
	}

	public void clear() {
		if (this.ensureLoaded()) {
			this.villagers.clear();
			this.save();
		}
	}

	/**
	 * Loads the file of the current world if the world changed
	 * @return false outside of a world
	 */
	private boolean ensureLoaded() {
		String worldKey = WorldKey.getCurrent();

		if (worldKey == null) {
			return false;
		}

		if (!worldKey.equals(this.loadedWorldKey)) {
			this.loadedWorldKey = worldKey;
			this.load();
		}

		return true;
	}

	private Path getFile() {
		return this.getDirectory().resolve(this.loadedWorldKey + ".json");
	}

	private void load() {
		this.villagers.clear();
		Path file = this.getFile();

		if (!Files.isRegularFile(file)) {
			return;
		}

		JsonElement element = JsonUtils.parseJsonFile(file);

		if (element == null || !element.isJsonObject() || !element.getAsJsonObject().has("villagers")) {
			ShoppingMaterialist.LOGGER.warn("Skipping invalid villager file '{}'", file);
			return;
		}

		for (JsonElement entry : element.getAsJsonObject().getAsJsonArray("villagers")) {
			RememberedVillager villager = entry.isJsonObject() ? RememberedVillager.fromJson(entry.getAsJsonObject()) : null;

			if (villager != null) {
				this.villagers.put(villager.uuid, villager);
			}
		}
	}

	private void save() {
		try {
			Files.createDirectories(this.getDirectory());
		}
		catch (IOException e) {
			ShoppingMaterialist.LOGGER.error("Failed to create the villager directory", e);
			return;
		}

		JsonObject root = new JsonObject();
		JsonArray array = new JsonArray();
		this.villagers.values().forEach(villager -> array.add(villager.toJson()));
		root.add("villagers", array);
		JsonUtils.writeJsonToFile(root, this.getFile());
	}
}
