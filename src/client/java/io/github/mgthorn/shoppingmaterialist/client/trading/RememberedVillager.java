package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * A villager or wandering trader whose trading screen was opened, with its trades and last known position.
 */
public class RememberedVillager {
	public static final String TYPE_VILLAGER = "villager";
	public static final String TYPE_WANDERING_TRADER = "wandering_trader";

	public final UUID uuid;
	public final String type;
	// e.g. minecraft:librarian, empty for wandering traders
	public final String profession;
	public final int level;
	public final String dimension;
	public final BlockPos pos;
	public final long lastSeen;
	public final List<RememberedOffer> offers;

	public RememberedVillager(UUID uuid, String type, String profession, int level, String dimension, BlockPos pos,
			long lastSeen, List<RememberedOffer> offers) {
		this.uuid = uuid;
		this.type = type;
		this.profession = profession;
		this.level = level;
		this.dimension = dimension;
		this.pos = pos;
		this.lastSeen = lastSeen;
		this.offers = offers;
	}

	/**
	 * e.g. "Librarian (level 3)" or "Wandering Trader"
	 */
	public String getDisplayName() {
		if (TYPE_WANDERING_TRADER.equals(this.type)) {
			return Component.translatable("entity.minecraft.wandering_trader").getString();
		}

		Identifier id = Identifier.tryParse(this.profession);
		String name = id != null
				? Component.translatable("entity.minecraft.villager." + id.getPath()).getString()
				: this.profession;

		return StringUtils.translate(Reference.MOD_ID + ".gui.trading.villager_name", name, this.level);
	}

	public String getCoordinates() {
		return this.pos.getX() + " " + this.pos.getY() + " " + this.pos.getZ();
	}

	/**
	 * The distance to the player in blocks, or -1 if the player is in another dimension
	 */
	public int getDistance() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || !this.dimension.equals(WorldKey.getCurrentDimension())) {
			return -1;
		}

		return (int) Math.round(Math.sqrt(mc.player.blockPosition().distSqr(this.pos)));
	}

	/**
	 * e.g. "120 -64 300 (35 m)" or "120 64 300 (minecraft:the_nether)"
	 */
	public String getLocationText() {
		int distance = this.getDistance();
		return this.getCoordinates() + (distance >= 0
				? StringUtils.translate(Reference.MOD_ID + ".gui.trading.distance", distance)
				: " (" + this.dimension + ")");
	}

	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("uuid", this.uuid.toString());
		obj.addProperty("type", this.type);
		obj.addProperty("profession", this.profession);
		obj.addProperty("level", this.level);
		obj.addProperty("dimension", this.dimension);
		obj.addProperty("x", this.pos.getX());
		obj.addProperty("y", this.pos.getY());
		obj.addProperty("z", this.pos.getZ());
		obj.addProperty("lastSeen", this.lastSeen);

		JsonArray offersArray = new JsonArray();
		this.offers.forEach(offer -> offersArray.add(offer.toJson()));
		obj.add("offers", offersArray);
		return obj;
	}

	@Nullable
	public static RememberedVillager fromJson(JsonObject obj) {
		try {
			List<RememberedOffer> offers = new ArrayList<>();

			if (obj.has("offers") && obj.get("offers").isJsonArray()) {
				for (JsonElement element : obj.getAsJsonArray("offers")) {
					RememberedOffer offer = element.isJsonObject() ? RememberedOffer.fromJson(element.getAsJsonObject()) : null;

					if (offer != null) {
						offers.add(offer);
					}
				}
			}

			BlockPos pos = new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
			return new RememberedVillager(UUID.fromString(obj.get("uuid").getAsString()), obj.get("type").getAsString(),
					obj.get("profession").getAsString(), obj.get("level").getAsInt(), obj.get("dimension").getAsString(),
					pos, obj.get("lastSeen").getAsLong(), offers);
		}
		catch (RuntimeException e) {
			return null;
		}
	}
}
