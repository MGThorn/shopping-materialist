package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece.Source;

/**
 * A named set of items with the enchantments wanted on them, e.g. "Best diamond gear".
 */
public class TradingSetup {
	private static final String PROTECTION = "minecraft:protection";
	private static final String UNBREAKING = "minecraft:unbreaking";
	private static final String MENDING = "minecraft:mending";
	private static final String EFFICIENCY = "minecraft:efficiency";
	private static final String SILK_TOUCH = "minecraft:silk_touch";
	private static final String FORTUNE = "minecraft:fortune";

	private String name;
	private final List<SetupPiece> pieces = new ArrayList<>();

	public TradingSetup(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<SetupPiece> getPieces() {
		return this.pieces;
	}

	/**
	 * Full diamond armor and tools with the best enchantments villagers can sell
	 */
	public static TradingSetup createDefault(String name) {
		TradingSetup setup = new TradingSetup(name);
		List<SetupPiece> p = setup.pieces;

		p.add(armor(new SetupPiece(Items.DIAMOND_HELMET, "", Source.BUY))
				.enchant("minecraft:respiration", 3).enchant("minecraft:aqua_affinity", 1));
		p.add(armor(new SetupPiece(Items.DIAMOND_CHESTPLATE, "", Source.BUY)));
		p.add(armor(new SetupPiece(Items.DIAMOND_LEGGINGS, "", Source.BUY)));
		p.add(armor(new SetupPiece(Items.DIAMOND_BOOTS, "", Source.BUY))
				.enchant("minecraft:feather_falling", 4).enchant("minecraft:depth_strider", 3));

		p.add(new SetupPiece(Items.DIAMOND_SWORD, "", Source.BUY)
				.enchant("minecraft:sharpness", 5).enchant("minecraft:looting", 3).enchant("minecraft:sweeping_edge", 3)
				.enchant("minecraft:fire_aspect", 2).enchant(UNBREAKING, 3).enchant(MENDING, 1));

		p.add(tool(Items.DIAMOND_PICKAXE, "Silk Touch").enchant(SILK_TOUCH, 1));
		p.add(tool(Items.DIAMOND_PICKAXE, "Fortune").enchant(FORTUNE, 3));
		p.add(tool(Items.DIAMOND_AXE, "").enchant(SILK_TOUCH, 1));
		p.add(tool(Items.DIAMOND_SHOVEL, "").enchant(SILK_TOUCH, 1));
		p.add(tool(Items.DIAMOND_HOE, "Fortune").enchant(FORTUNE, 3));
		p.add(tool(Items.DIAMOND_HOE, "Silk Touch").enchant(SILK_TOUCH, 1));

		return setup;
	}

	private static SetupPiece armor(SetupPiece piece) {
		return piece.enchant(PROTECTION, 4).enchant(UNBREAKING, 3).enchant(MENDING, 1);
	}

	private static SetupPiece tool(Item item, String label) {
		return new SetupPiece(item, label, Source.BUY).enchant(EFFICIENCY, 5).enchant(UNBREAKING, 3).enchant(MENDING, 1);
	}

	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("name", this.name);

		JsonArray array = new JsonArray();
		this.pieces.forEach(piece -> array.add(piece.toJson()));
		obj.add("pieces", array);
		return obj;
	}

	public static TradingSetup fromJson(JsonObject obj) {
		TradingSetup setup = new TradingSetup(obj.has("name") ? obj.get("name").getAsString() : "?");

		if (obj.has("pieces") && obj.get("pieces").isJsonArray()) {
			for (JsonElement element : obj.getAsJsonArray("pieces")) {
				SetupPiece piece = element.isJsonObject() ? SetupPiece.fromJson(element.getAsJsonObject()) : null;

				if (piece != null) {
					setup.pieces.add(piece);
				}
			}
		}

		return setup;
	}
}
