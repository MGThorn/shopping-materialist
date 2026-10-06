package io.github.mgthorn.shoppingmaterialist.client.shoppinglist;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.ItemType;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * Exposes a {@link ShoppingList} as a Litematica material list, so it can be shown
 * in Litematica's material list GUI and info HUD, and reopened with Litematica's hotkey.
 */
public class ShoppingMaterialList extends MaterialListBase {
	private final ShoppingList shoppingList;

	public ShoppingMaterialList(ShoppingList shoppingList) {
		this.shoppingList = shoppingList;
	}

	public ShoppingList getShoppingList() {
		return this.shoppingList;
	}

	@Override
	public String getName() {
		return this.shoppingList.getName();
	}

	@Override
	public String getTitle() {
		return StringUtils.translate(Reference.MOD_ID + ".gui.title.material_list", this.shoppingList.getName());
	}

	@Override
	public void reCreateMaterialList() {
		Map<ItemType, Integer> items = new HashMap<>();

		for (Map.Entry<Item, Integer> entry : this.shoppingList.getItems().entrySet()) {
			items.put(new ItemType(new ItemStack(entry.getKey()), false, false), entry.getValue());
		}

		this.setMaterialListEntries(MaterialListUtils.createMaterialListFromItems(items, Minecraft.getInstance().player));
	}
}
