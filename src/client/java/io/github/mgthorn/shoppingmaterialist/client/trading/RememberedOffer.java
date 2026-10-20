package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * One trade of a remembered villager, as it was the last time its trading screen was open.
 * The prices already include the player's discounts and the demand at that time.
 */
public class RememberedOffer {
	public final Item costA;
	public final int costACount;
	public final Item costB;
	public final int costBCount;
	public final Item result;
	public final int resultCount;
	// Enchantments of the result (stored enchantments for enchanted books), by id
	public final Map<String, Integer> enchantments;
	public final int uses;
	public final int maxUses;

	public RememberedOffer(Item costA, int costACount, Item costB, int costBCount, Item result, int resultCount,
			Map<String, Integer> enchantments, int uses, int maxUses) {
		this.costA = costA;
		this.costACount = costACount;
		this.costB = costB;
		this.costBCount = costBCount;
		this.result = result;
		this.resultCount = resultCount;
		this.enchantments = enchantments;
		this.uses = uses;
		this.maxUses = maxUses;
	}

	public static RememberedOffer of(MerchantOffer offer) {
		ItemStack costA = offer.getCostA();
		ItemStack costB = offer.getCostB();
		ItemStack result = offer.getResult();

		return new RememberedOffer(costA.getItem(), costA.getCount(), costB.getItem(), costB.getCount(),
				result.getItem(), result.getCount(), Enchants.fromStack(result), offer.getUses(), offer.getMaxUses());
	}

	public int getUsesLeft() {
		return Math.max(0, this.maxUses - this.uses);
	}

	/**
	 * The emeralds this trade costs (0 if it doesn't cost emeralds)
	 */
	public int getEmeraldCost() {
		return (this.costA == Items.EMERALD ? this.costACount : 0) + (this.costB == Items.EMERALD ? this.costBCount : 0);
	}

	/**
	 * How many of this item the trade costs
	 */
	public int getCount(Item item) {
		return (this.costA == item ? this.costACount : 0) + (this.costB == item ? this.costBCount : 0);
	}

	public boolean hasCostB() {
		return this.costB != Items.AIR && this.costBCount > 0;
	}

	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("costA", itemId(this.costA));
		obj.addProperty("costACount", this.costACount);

		if (this.hasCostB()) {
			obj.addProperty("costB", itemId(this.costB));
			obj.addProperty("costBCount", this.costBCount);
		}

		obj.addProperty("result", itemId(this.result));
		obj.addProperty("resultCount", this.resultCount);

		if (!this.enchantments.isEmpty()) {
			JsonObject enchants = new JsonObject();
			this.enchantments.forEach(enchants::addProperty);
			obj.add("enchantments", enchants);
		}

		obj.addProperty("uses", this.uses);
		obj.addProperty("maxUses", this.maxUses);
		return obj;
	}

	@Nullable
	public static RememberedOffer fromJson(JsonObject obj) {
		Item costA = item(obj, "costA");
		Item result = item(obj, "result");

		if (costA == Items.AIR || result == Items.AIR) {
			return null;
		}

		Map<String, Integer> enchantments = new LinkedHashMap<>();

		if (obj.has("enchantments") && obj.get("enchantments").isJsonObject()) {
			obj.getAsJsonObject("enchantments").entrySet().forEach(e -> enchantments.put(e.getKey(), e.getValue().getAsInt()));
		}

		return new RememberedOffer(costA, getInt(obj, "costACount"), item(obj, "costB"), getInt(obj, "costBCount"),
				result, getInt(obj, "resultCount"), enchantments, getInt(obj, "uses"), getInt(obj, "maxUses"));
	}

	static String itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	static Item itemFromId(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier != null ? BuiltInRegistries.ITEM.getValue(identifier) : Items.AIR;
	}

	private static Item item(JsonObject obj, String key) {
		return obj.has(key) ? itemFromId(obj.get(key).getAsString()) : Items.AIR;
	}

	private static int getInt(JsonObject obj, String key) {
		return obj.has(key) ? obj.get(key).getAsInt() : 0;
	}
}
