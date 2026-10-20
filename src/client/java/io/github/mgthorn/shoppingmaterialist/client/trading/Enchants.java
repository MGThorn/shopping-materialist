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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Helpers for the data-driven enchantment registry. Enchantments are only available while in a world,
 * so everything is stored by id (e.g. {@code minecraft:sharpness}) and looked up when needed.
 */
public class Enchants {
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
	 * The name cut to at most 4 letters, plus the level: "Effi V", "Mend", "FiAs II" (Fire Aspect), "CuVa" (Curse of Vanishing).
	 * One word: its first 4 letters. Several words: the first 2 letters of the first two words, skipping "of".
	 */
	public static String getShortName(String id, int level) {
		Holder<Enchantment> holder = get(id);

		if (holder == null) {
			return getName(id, level);
		}

		String name = holder.value().description().getString();
		String full = Enchantment.getFullname(holder, level).getString();
		// The level part, e.g. " V" (empty for enchantments with only one level)
		String levelText = full.startsWith(name) ? full.substring(name.length()) : "";

		List<String> words = new ArrayList<>();

		for (String word : name.split("\\s+")) {
			if (!word.isEmpty() && !word.equalsIgnoreCase("of")) {
				words.add(word);
			}
		}

		String shortName;

		if (words.size() >= 2) {
			shortName = prefix(words.get(0), 2) + prefix(words.get(1), 2);
		}
		else {
			shortName = prefix(name, 4);
		}

		return shortName + levelText;
	}

	private static String prefix(String text, int length) {
		return text.substring(0, Math.min(length, text.length()));
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
