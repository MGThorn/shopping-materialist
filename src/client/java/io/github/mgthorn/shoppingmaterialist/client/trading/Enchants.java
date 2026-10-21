package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Helpers for the data-driven enchantment registry. Enchantments are only available while in a world,
 * so everything is stored by id (e.g. {@code minecraft:sharpness}) and looked up when needed.
 */
public class Enchants {
	// The order of enchanted books in lists: most used first
	private static final List<String> USAGE_ORDER = List.of(
			"mending", "unbreaking", "efficiency", "protection", "sharpness", "fortune", "silk_touch", "feather_falling",
			"looting", "power", "respiration", "aqua_affinity", "depth_strider", "infinity", "sweeping_edge", "fire_aspect",
			"thorns", "projectile_protection", "blast_protection", "fire_protection", "knockback", "punch", "flame", "smite",
			"bane_of_arthropods", "frost_walker", "luck_of_the_sea", "lure", "loyalty", "riptide", "channeling", "impaling",
			"multishot", "piercing", "quick_charge", "density", "breach", "lunge", "wind_burst", "soul_speed", "swift_sneak");

	@Nullable
	public static Registry<Enchantment> getRegistry() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT) : null;
	}

	@Nullable
	public static Holder<Enchantment> get(String id) {
		Registry<Enchantment> registry = getRegistry();
		Identifier identifier = Identifier.tryParse(id);

		if (registry == null || identifier == null) {
			return null;
		}

		Optional<Holder.Reference<Enchantment>> holder = registry.get(identifier);
		return holder.orElse(null);
	}

	public static String getId(Holder<Enchantment> holder) {
		return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
	}

	/**
	 * e.g. "Sharpness V", or the raw id if the enchantment doesn't exist in this world
	 */
	public static String getName(String id, int level) {
		Holder<Enchantment> holder = get(id);
		return holder != null ? Enchantment.getFullname(holder, level).getString() : id + " " + level;
	}

	/**
	 * An enchanted book storing this enchantment, or a plain enchanted book if it doesn't exist in this world.
	 * Item model definitions can pick a texture by the stored enchantment, so resource packs can show one icon per enchantment.
	 */
	public static ItemStack createBook(String id, int level) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		Holder<Enchantment> holder = get(id);

		if (holder != null) {
			ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			enchantments.set(holder, level);
			book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
		}

		return book;
	}

	public static int getMaxLevel(String id) {
		Holder<Enchantment> holder = get(id);
		return holder != null ? holder.value().getMaxLevel() : 1;
	}

	/**
	 * The anvil level cost per enchantment level when it comes from a book (half the item cost, at least 1)
	 */
	public static int getBookAnvilCost(String id) {
		Holder<Enchantment> holder = get(id);
		return holder != null ? Math.max(1, holder.value().getAnvilCost() / 2) : 1;
	}

	public static boolean areCompatible(String idA, String idB) {
		if (idA.equals(idB)) {
			return true;
		}

		Holder<Enchantment> a = get(idA);
		Holder<Enchantment> b = get(idB);
		return a == null || b == null || Enchantment.areCompatible(a, b);
	}

	/**
	 * The emerald price range of an enchanted book from a librarian: {@code 2 + random(5 + 10 * level) + 3 * level},
	 * doubled for treasure enchantments like Mending, at most 64.
	 * @return {min, max}, or null if villagers can't sell this enchantment
	 */
	@Nullable
	public static int[] getBookPriceRange(String id, int level) {
		Holder<Enchantment> holder = get(id);

		if (holder == null || !holder.is(EnchantmentTags.TRADEABLE)) {
			return null;
		}

		int min = 2 + 3 * level;
		int max = min + 4 + 10 * level;

		if (holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
			min *= 2;
			max *= 2;
		}

		return new int[] { Math.min(min, 64), Math.min(max, 64) };
	}

	/**
	 * Sorts enchanted books by how much they are used: the {@link #USAGE_ORDER} list first, then other (modded)
	 * enchantments by name, then the ones villagers don't sell, then curses. Levels of the same enchantment: highest first.
	 */
	public static int compareByUsage(String idA, int levelA, String idB, int levelB) {
		if (idA.equals(idB)) {
			return Integer.compare(levelB, levelA);
		}

		int result = Integer.compare(getUsageGroup(idA), getUsageGroup(idB));

		if (result == 0) {
			result = Integer.compare(getUsageRank(idA), getUsageRank(idB));
		}

		return result != 0 ? result : getName(idA, 1).compareTo(getName(idB, 1));
	}

	/**
	 * 0: sold by villagers, 1: not sold by villagers (e.g. Soul Speed), 2: curses
	 */
	private static int getUsageGroup(String id) {
		Holder<Enchantment> holder = get(id);

		if (holder != null && holder.is(EnchantmentTags.CURSE)) {
			return 2;
		}

		return holder != null && !holder.is(EnchantmentTags.TRADEABLE) ? 1 : 0;
	}

	/**
	 * The position in {@link #USAGE_ORDER}, or after it for enchantments that aren't in it
	 */
	private static int getUsageRank(String id) {
		Identifier identifier = Identifier.tryParse(id);
		int index = identifier != null && identifier.getNamespace().equals("minecraft") ? USAGE_ORDER.indexOf(identifier.getPath()) : -1;
		return index >= 0 ? index : USAGE_ORDER.size();
	}

	public static boolean isTradeable(String id) {
		Holder<Enchantment> holder = get(id);
		return holder != null && holder.is(EnchantmentTags.TRADEABLE);
	}

	/**
	 * All enchantments that can go on this item (anvil rules), sorted by name
	 */
	public static List<Holder<Enchantment>> getApplicable(ItemStack stack) {
		List<Holder<Enchantment>> list = new ArrayList<>();
		Registry<Enchantment> registry = getRegistry();

		if (registry != null) {
			registry.listElements().forEach(holder -> {
				if (holder.value().canEnchant(stack)) {
					list.add(holder);
				}
			});
		}

		list.sort(Comparator.comparing(holder -> holder.value().description().getString()));
		return list;
	}

	/**
	 * The enchantments on an item, or the stored enchantments of an enchanted book, by id
	 */
	public static Map<String, Integer> fromStack(ItemStack stack) {
		Map<String, Integer> map = new LinkedHashMap<>();
		addAll(map, stack.get(DataComponents.ENCHANTMENTS));
		addAll(map, stack.get(DataComponents.STORED_ENCHANTMENTS));
		return map;
	}

	private static void addAll(Map<String, Integer> map, @Nullable ItemEnchantments enchantments) {
		if (enchantments != null) {
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
				map.put(getId(entry.getKey()), entry.getIntValue());
			}
		}
	}
}
