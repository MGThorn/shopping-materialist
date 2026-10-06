package io.github.mgthorn.shoppingmaterialist.client.shoppinglist;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import fi.dy.masa.litematica.materials.MaterialListAreaAnalyzer;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import io.github.mgthorn.shoppingmaterialist.client.mixin.MaterialListAreaAnalyzerAccessor;

/**
 * Turns any Litematica material list (placement, schematic, area analysis...) into a shopping list.
 */
public class MaterialListConverter {
	/**
	 * The shopping list name for a material list: the placement/schematic name,
	 * or "analyse_x_y_z" (the selection origin) for an area analysis.
	 */
	public static String getShoppingListName(MaterialListBase materialList) {
		if (materialList instanceof MaterialListAreaAnalyzer analyzer) {
			BlockPos origin = ((MaterialListAreaAnalyzerAccessor) analyzer).shoppingMaterialist$getSelection().getEffectiveOrigin();
			return String.format("analyse_%d_%d_%d", origin.getX(), origin.getY(), origin.getZ());
		}

		String name = materialList.getName();
		return name == null || name.isBlank() ? "Material List" : name.trim();
	}

	/**
	 * The required items of the material list, including the multiplier set in the material list GUI.
	 */
	public static Map<Item, Integer> getItems(MaterialListBase materialList) {
		Map<Item, Integer> items = new LinkedHashMap<>();
		long multiplier = materialList.getMultiplier();

		for (MaterialListEntry entry : materialList.getMaterialsAll()) {
			Item item = entry.getStack().getItem();
			long count = entry.getCountTotal() * multiplier;

			if (item != Items.AIR && count > 0) {
				items.merge(item, (int) Math.min(count, Integer.MAX_VALUE), (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
			}
		}

		return items;
	}
}
