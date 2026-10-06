package io.github.mgthorn.shoppingmaterialist.client.shoppinglist;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A named list of items and the amount wanted of each.
 * Saved in the same JSON format as Litematica's custom material lists.
 */
public class ShoppingList {
	private final String fileName;
	private final Map<Item, Integer> items = new LinkedHashMap<>();
	private String name;
	@Nullable private ShoppingMaterialList materialList;

	public ShoppingList(String name, String fileName) {
		this.name = name;
		this.fileName = fileName;
	}

	public String getName() {
		return this.name;
	}

	void setName(String name) {
		this.name = name;
	}

	public String getFileName() {
		return this.fileName;
	}

	public Map<Item, Integer> getItems() {
		return Collections.unmodifiableMap(this.items);
	}

	public int getCount(Item item) {
		return this.items.getOrDefault(item, 0);
	}

	public long getTotalCount() {
		long total = 0;

		for (int count : this.items.values()) {
			total += count;
		}

		return total;
	}

	/**
	 * Changes the wanted amount of an item. The item is removed once its amount drops to 0.
	 */
	public void addCount(Item item, int amount) {
		long newCount = (long) this.getCount(item) + amount;

		if (newCount <= 0) {
			this.items.remove(item);
		}
		else {
			this.items.put(item, (int) Math.min(newCount, Integer.MAX_VALUE));
		}

		this.onChanged();
	}

	/**
	 * Replaces the whole content of the list
	 */
	public void setItems(Map<Item, Integer> items) {
		this.items.clear();
		this.items.putAll(items);
		this.onChanged();
	}

	public void remove(Item item) {
		if (this.items.remove(item) != null) {
			this.onChanged();
		}
	}

	/**
	 * The Litematica material list view of this shopping list.
	 * The same instance is returned every time, so Litematica's
	 * "last viewed material list" (M + L) and the info HUD keep following edits.
	 */
	public ShoppingMaterialList getMaterialList() {
		if (this.materialList == null) {
			this.materialList = new ShoppingMaterialList(this);
			this.materialList.reCreateMaterialList();
		}

		return this.materialList;
	}

	@Nullable
	ShoppingMaterialList getMaterialListIfCreated() {
		return this.materialList;
	}

	void onChanged() {
		ShoppingListManager.getInstance().save(this);

		if (this.materialList != null) {
			this.materialList.reCreateMaterialList();
		}
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		JsonArray itemsArray = new JsonArray();

		for (Map.Entry<Item, Integer> entry : this.items.entrySet()) {
			JsonObject itemObj = new JsonObject();
			itemObj.add("id", new JsonPrimitive(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString()));
			itemObj.add("count", new JsonPrimitive(entry.getValue()));
			itemsArray.add(itemObj);
		}

		root.add("name", new JsonPrimitive(this.name));
		root.add("items", itemsArray);

		return root;
	}

	public static ShoppingList fromJson(JsonObject root, String fileName) {
		String name = root.has("name") ? root.get("name").getAsString() : fileName;
		ShoppingList list = new ShoppingList(name, fileName);

		if (root.has("items") && root.get("items").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("items")) {
				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject itemObj = element.getAsJsonObject();

				if (!itemObj.has("id") || !itemObj.has("count")) {
					continue;
				}

				Identifier id = Identifier.tryParse(itemObj.get("id").getAsString());
				Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
				int count = itemObj.get("count").getAsInt();

				if (item != Items.AIR && count > 0) {
					list.items.merge(item, count, Integer::sum);
				}
			}
		}

		return list;
	}
}
