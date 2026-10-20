package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Crafting materials of every enchantable item that can be crafted, generated from the Minecraft 26.3 recipe files
 * ({@code data/minecraft/recipe/}). Recipes aren't sent to the client, so they are stored here.
 * <ul>
 *     <li>Tag ingredients use one item of the tag, e.g. oak planks for wooden tools, cobblestone for stone tools</li>
 *     <li>Netherite gear (smithing) includes the materials of the diamond item, the netherite ingot and the upgrade template</li>
 * </ul>
 * Not craftable: chainmail armor, trident, elytra, heads and the carved pumpkin.
 */
public class CraftingRecipes {
	private static final Map<Item, Map<Item, Integer>> RECIPES = create();

	@Nullable
	public static Map<Item, Integer> get(Item item) {
		return RECIPES.get(item);
	}

	public static boolean isCraftable(Item item) {
		return RECIPES.containsKey(item);
	}

	private static Map<Item, Map<Item, Integer>> create() {
		Map<Item, Map<Item, Integer>> map = new LinkedHashMap<>();
		map.put(Items.BOW, Map.of(Items.STICK, 3, Items.STRING, 3));
		map.put(Items.BRUSH, Map.of(Items.FEATHER, 1, Items.COPPER_INGOT, 1, Items.STICK, 1));
		map.put(Items.CARROT_ON_A_STICK, Map.of(Items.FISHING_ROD, 1, Items.CARROT, 1));
		map.put(Items.COMPASS, Map.of(Items.IRON_INGOT, 4, Items.REDSTONE, 1));
		map.put(Items.COPPER_AXE, Map.of(Items.COPPER_INGOT, 3, Items.STICK, 2));
		map.put(Items.COPPER_BOOTS, Map.of(Items.COPPER_INGOT, 4));
		map.put(Items.COPPER_CHESTPLATE, Map.of(Items.COPPER_INGOT, 8));
		map.put(Items.COPPER_HELMET, Map.of(Items.COPPER_INGOT, 5));
		map.put(Items.COPPER_HOE, Map.of(Items.COPPER_INGOT, 2, Items.STICK, 2));
		map.put(Items.COPPER_LEGGINGS, Map.of(Items.COPPER_INGOT, 7));
		map.put(Items.COPPER_PICKAXE, Map.of(Items.COPPER_INGOT, 3, Items.STICK, 2));
		map.put(Items.COPPER_SHOVEL, Map.of(Items.COPPER_INGOT, 1, Items.STICK, 2));
		map.put(Items.COPPER_SPEAR, Map.of(Items.COPPER_INGOT, 1, Items.STICK, 2));
		map.put(Items.COPPER_SWORD, Map.of(Items.COPPER_INGOT, 2, Items.STICK, 1));
		map.put(Items.CROSSBOW, Map.of(Items.STICK, 3, Items.IRON_INGOT, 1, Items.STRING, 2, Items.TRIPWIRE_HOOK, 1));
		map.put(Items.DIAMOND_AXE, Map.of(Items.DIAMOND, 3, Items.STICK, 2));
		map.put(Items.DIAMOND_BOOTS, Map.of(Items.DIAMOND, 4));
		map.put(Items.DIAMOND_CHESTPLATE, Map.of(Items.DIAMOND, 8));
		map.put(Items.DIAMOND_HELMET, Map.of(Items.DIAMOND, 5));
		map.put(Items.DIAMOND_HOE, Map.of(Items.DIAMOND, 2, Items.STICK, 2));
		map.put(Items.DIAMOND_LEGGINGS, Map.of(Items.DIAMOND, 7));
		map.put(Items.DIAMOND_PICKAXE, Map.of(Items.DIAMOND, 3, Items.STICK, 2));
		map.put(Items.DIAMOND_SHOVEL, Map.of(Items.DIAMOND, 1, Items.STICK, 2));
		map.put(Items.DIAMOND_SPEAR, Map.of(Items.DIAMOND, 1, Items.STICK, 2));
		map.put(Items.DIAMOND_SWORD, Map.of(Items.DIAMOND, 2, Items.STICK, 1));
		map.put(Items.FISHING_ROD, Map.of(Items.STICK, 3, Items.STRING, 2));
		map.put(Items.FLINT_AND_STEEL, Map.of(Items.IRON_INGOT, 1, Items.FLINT, 1));
		map.put(Items.GOLDEN_AXE, Map.of(Items.GOLD_INGOT, 3, Items.STICK, 2));
		map.put(Items.GOLDEN_BOOTS, Map.of(Items.GOLD_INGOT, 4));
		map.put(Items.GOLDEN_CHESTPLATE, Map.of(Items.GOLD_INGOT, 8));
		map.put(Items.GOLDEN_HELMET, Map.of(Items.GOLD_INGOT, 5));
		map.put(Items.GOLDEN_HOE, Map.of(Items.GOLD_INGOT, 2, Items.STICK, 2));
		map.put(Items.GOLDEN_LEGGINGS, Map.of(Items.GOLD_INGOT, 7));
		map.put(Items.GOLDEN_PICKAXE, Map.of(Items.GOLD_INGOT, 3, Items.STICK, 2));
		map.put(Items.GOLDEN_SHOVEL, Map.of(Items.GOLD_INGOT, 1, Items.STICK, 2));
		map.put(Items.GOLDEN_SPEAR, Map.of(Items.GOLD_INGOT, 1, Items.STICK, 2));
		map.put(Items.GOLDEN_SWORD, Map.of(Items.GOLD_INGOT, 2, Items.STICK, 1));
		map.put(Items.IRON_AXE, Map.of(Items.IRON_INGOT, 3, Items.STICK, 2));
		map.put(Items.IRON_BOOTS, Map.of(Items.IRON_INGOT, 4));
		map.put(Items.IRON_CHESTPLATE, Map.of(Items.IRON_INGOT, 8));
		map.put(Items.IRON_HELMET, Map.of(Items.IRON_INGOT, 5));
		map.put(Items.IRON_HOE, Map.of(Items.IRON_INGOT, 2, Items.STICK, 2));
		map.put(Items.IRON_LEGGINGS, Map.of(Items.IRON_INGOT, 7));
		map.put(Items.IRON_PICKAXE, Map.of(Items.IRON_INGOT, 3, Items.STICK, 2));
		map.put(Items.IRON_SHOVEL, Map.of(Items.IRON_INGOT, 1, Items.STICK, 2));
		map.put(Items.IRON_SPEAR, Map.of(Items.IRON_INGOT, 1, Items.STICK, 2));
		map.put(Items.IRON_SWORD, Map.of(Items.IRON_INGOT, 2, Items.STICK, 1));
		map.put(Items.LEATHER_BOOTS, Map.of(Items.LEATHER, 4));
		map.put(Items.LEATHER_CHESTPLATE, Map.of(Items.LEATHER, 8));
		map.put(Items.LEATHER_HELMET, Map.of(Items.LEATHER, 5));
		map.put(Items.LEATHER_LEGGINGS, Map.of(Items.LEATHER, 7));
		map.put(Items.MACE, Map.of(Items.HEAVY_CORE, 1, Items.BREEZE_ROD, 1));
		map.put(Items.NETHERITE_AXE, Map.of(Items.DIAMOND, 3, Items.STICK, 2, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_BOOTS, Map.of(Items.DIAMOND, 4, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_CHESTPLATE, Map.of(Items.DIAMOND, 8, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_HELMET, Map.of(Items.DIAMOND, 5, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_HOE, Map.of(Items.DIAMOND, 2, Items.STICK, 2, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_LEGGINGS, Map.of(Items.DIAMOND, 7, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_PICKAXE, Map.of(Items.DIAMOND, 3, Items.STICK, 2, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_SHOVEL, Map.of(Items.DIAMOND, 1, Items.STICK, 2, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_SPEAR, Map.of(Items.DIAMOND, 1, Items.STICK, 2, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.NETHERITE_SWORD, Map.of(Items.DIAMOND, 2, Items.STICK, 1, Items.NETHERITE_INGOT, 1, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		map.put(Items.SHEARS, Map.of(Items.IRON_INGOT, 2));
		map.put(Items.SHIELD, Map.of(Items.OAK_PLANKS, 6, Items.IRON_INGOT, 1));
		map.put(Items.STONE_AXE, Map.of(Items.COBBLESTONE, 3, Items.STICK, 2));
		map.put(Items.STONE_HOE, Map.of(Items.COBBLESTONE, 2, Items.STICK, 2));
		map.put(Items.STONE_PICKAXE, Map.of(Items.COBBLESTONE, 3, Items.STICK, 2));
		map.put(Items.STONE_SHOVEL, Map.of(Items.COBBLESTONE, 1, Items.STICK, 2));
		map.put(Items.STONE_SPEAR, Map.of(Items.COBBLESTONE, 1, Items.STICK, 2));
		map.put(Items.STONE_SWORD, Map.of(Items.COBBLESTONE, 2, Items.STICK, 1));
		map.put(Items.TURTLE_HELMET, Map.of(Items.TURTLE_SCUTE, 5));
		map.put(Items.WARPED_FUNGUS_ON_A_STICK, Map.of(Items.FISHING_ROD, 1, Items.WARPED_FUNGUS, 1));
		map.put(Items.WOODEN_AXE, Map.of(Items.OAK_PLANKS, 3, Items.STICK, 2));
		map.put(Items.WOODEN_HOE, Map.of(Items.OAK_PLANKS, 2, Items.STICK, 2));
		map.put(Items.WOODEN_PICKAXE, Map.of(Items.OAK_PLANKS, 3, Items.STICK, 2));
		map.put(Items.WOODEN_SHOVEL, Map.of(Items.OAK_PLANKS, 1, Items.STICK, 2));
		map.put(Items.WOODEN_SPEAR, Map.of(Items.OAK_PLANKS, 1, Items.STICK, 2));
		map.put(Items.WOODEN_SWORD, Map.of(Items.OAK_PLANKS, 2, Items.STICK, 1));
		return map;
	}
}
