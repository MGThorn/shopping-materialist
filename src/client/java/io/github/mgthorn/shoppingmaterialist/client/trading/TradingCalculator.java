package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * Works out what a trading setup costs: which remembered villagers to buy the books (and items) from,
 * what to bring for that, and the cheapest anvil order to put the books on the items.
 */
public class TradingCalculator {
	private static final String KEY = Reference.MOD_ID + ".gui.trading.";
	private static final Map<Item, int[]> VILLAGER_PRICES = createVillagerPrices();

	/**
	 * A trade of a remembered villager
	 */
	public record Purchase(RememberedVillager villager, RememberedOffer offer) {
	}

	/**
	 * Combining two copies of a lower level book into the next level, e.g. 2x Efficiency IV into Efficiency V
	 * @param level the level of the two input books
	 * @param count how many times this is done (e.g. 4x III into 2x IV is 2 times)
	 * @param cost the levels for one combination
	 */
	public record Merge(int level, int count, int cost) {
	}

	public static class BookResult {
		public final String id;
		public final int level;
		@Nullable public final Purchase purchase;
		// The level of the bought book, and how many are needed to combine them into the wanted level
		public final int boughtLevel;
		public final int copies;
		// The anvil steps that combine the copies, lowest level first
		public final List<Merge> merges = new ArrayList<>();
		// {min, max} emeralds if no remembered villager sells it, null if villagers can't sell it at all
		@Nullable public final int[] estimate;

		BookResult(String id, int level, @Nullable Purchase purchase, int boughtLevel, @Nullable int[] estimate) {
			this.id = id;
			this.level = level;
			this.purchase = purchase;
			this.boughtLevel = boughtLevel;
			this.copies = copiesNeeded(boughtLevel, level);
			this.estimate = estimate;

			int bookCost = Enchants.getBookAnvilCost(id);

			// Each round combines pairs of equal books; after j rounds a book went through the anvil j times.
			// The anvil charges the resulting level (IV + IV costs as much as one V) plus both prior work penalties.
			for (int l = boughtLevel, j = 0; l < level; l++, j++) {
				int count = 1 << (level - l - 1);
				int penalty = (1 << j) - 1;
				this.merges.add(new Merge(l, count, (l + 1) * bookCost + 2 * penalty));
			}
		}

		public String getName() {
			return Enchants.getName(this.id, this.level);
		}

		public String getBoughtName() {
			return Enchants.getName(this.id, this.boughtLevel);
		}

		/**
		 * How often the combined book went through the anvil (its prior work penalty)
		 */
		public int getWork() {
			return this.merges.size();
		}

		public int getMergeLevels() {
			return this.merges.stream().mapToInt(merge -> merge.count() * merge.cost()).sum();
		}
	}

	/**
	 * 2^(wanted - bought) books of a lower level combine into one of the wanted level
	 */
	private static int copiesNeeded(int boughtLevel, int wantedLevel) {
		return boughtLevel >= wantedLevel ? 1 : 1 << (wantedLevel - boughtLevel);
	}

	public static class PieceResult {
		public final SetupPiece piece;
		@Nullable public Purchase basePurchase;
		// {min, max} emeralds if no remembered villager sells the item, null if villagers don't sell it at all
		@Nullable public int[] baseEstimate;
		// Materials for crafting the item, null if it isn't crafted
		@Nullable public Map<Item, Integer> craftMaterials;
		// Wanted enchantments the bought item already has
		public final Map<String, Integer> fromBase = new LinkedHashMap<>();
		public final List<BookResult> books = new ArrayList<>();
		public AnvilOptimizer.Result anvil = new AnvilOptimizer.Result(List.of(), 0, false);
		public final List<String> warnings = new ArrayList<>();
		// Anvil levels for this item, including combining lower level books
		public int totalLevels;
		public boolean tooExpensive;
		// Books the librarians want for this item's enchanted books
		public int bookCount;
		// Emeralds for this item: known prices, and the estimate range for books without a remembered villager
		public int emeraldsKnown;
		public int estimateMin;
		public int estimateMax;

		PieceResult(SetupPiece piece) {
			this.piece = piece;
		}
	}

	/**
	 * An enchantment no remembered villager sells (not even at a lower level)
	 * @param excluded left out of the calculation, see {@link TradingSetupManager#isExcluded}
	 */
	public record MissingEnchantment(String id, int level, boolean excluded) {
	}

	/**
	 * A villager to visit and what to buy there
	 */
	public static class Visit {
		public final RememberedVillager villager;
		public final Map<String, Integer> purchases = new LinkedHashMap<>();

		Visit(RememberedVillager villager) {
			this.villager = villager;
		}
	}

	public static class Result {
		public final List<PieceResult> pieces = new ArrayList<>();
		// Everything to bring to the villagers and for crafting (for the shopping list export)
		public final Map<Item, Integer> inputs = new LinkedHashMap<>();
		public final Map<UUID, Visit> visits = new LinkedHashMap<>();
		public int emeraldsKnown;
		public int estimateMin;
		public int estimateMax;
		// Books and bought items no remembered villager sells
		public int missingTrades;
		// Every wanted enchantment no remembered villager sells, including the excluded ones
		public final Map<String, MissingEnchantment> missingEnchantments = new LinkedHashMap<>();
		public int totalLevels;
		public boolean tooExpensive;

		public List<Visit> getVisitsByDistance() {
			List<Visit> list = new ArrayList<>(this.visits.values());
			list.sort(Comparator.comparingInt(visit -> distanceOrMax(visit.villager)));
			return list;
		}
	}

	public static Result calculate(TradingSetup setup, List<RememberedVillager> villagers) {
		Result result = new Result();

		for (SetupPiece piece : setup.getPieces()) {
			if (piece.isEnabled()) {
				result.pieces.add(calculatePiece(piece, villagers, result));
			}
		}

		return result;
	}

	private static PieceResult calculatePiece(SetupPiece piece, List<RememberedVillager> villagers, Result result) {
		PieceResult pr = new PieceResult(piece);
		ItemStack stack = piece.getStack();
		Map<String, Integer> wanted = getValidEnchantments(piece, stack, pr.warnings);

		// Enchantments no remembered villager sells are listed as missing; excluded ones are left out of everything
		wanted.entrySet().removeIf(entry -> {
			if (findBook(entry.getKey(), entry.getValue(), villagers).purchase != null) {
				return false;
			}

			boolean excluded = TradingSetupManager.getInstance().isExcluded(entry.getKey());
			result.missingEnchantments.putIfAbsent(entry.getKey() + " " + entry.getValue(),
					new MissingEnchantment(entry.getKey(), entry.getValue(), excluded));
			return excluded;
		});

		switch (piece.getSource()) {
			case CRAFT -> {
				pr.craftMaterials = CraftingRecipes.get(piece.getItem());

				if (pr.craftMaterials != null) {
					pr.craftMaterials.forEach((item, count) -> addInput(result, item, count));
				}
				else {
					pr.warnings.add(StringUtils.translate(KEY + "warning.not_craftable"));
				}
			}
			case BUY -> {
				pr.basePurchase = findBestBase(piece, wanted, villagers);

				if (pr.basePurchase != null) {
					RememberedOffer offer = pr.basePurchase.offer();
					addPurchase(result, pr.basePurchase, piece.getDisplayName(), 1);
					pr.emeraldsKnown += offer.getEmeraldCost();

					offer.enchantments.forEach((id, level) -> {
						if (wanted.getOrDefault(id, 0) > 0 && level >= wanted.get(id)) {
							pr.fromBase.put(id, level);
						}
					});
				}
				else {
					pr.baseEstimate = VILLAGER_PRICES.get(piece.getItem());

					result.missingTrades++;

					if (pr.baseEstimate != null) {
						result.estimateMin += pr.baseEstimate[0];
						result.estimateMax += pr.baseEstimate[1];
						pr.estimateMin += pr.baseEstimate[0];
						pr.estimateMax += pr.baseEstimate[1];
						// Bring enough for the most expensive price
						addInput(result, Items.EMERALD, pr.baseEstimate[1]);
					}
					else {
						pr.warnings.add(StringUtils.translate(KEY + "warning.no_seller"));
					}
				}
			}
			case HAVE -> {
			}
		}

		for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
			if (pr.fromBase.containsKey(entry.getKey())) {
				continue;
			}

			BookResult book = findBook(entry.getKey(), entry.getValue(), villagers);
			pr.books.add(book);

			if (book.purchase != null) {
				addPurchase(result, book.purchase, book.getBoughtName(), book.copies);
				pr.emeraldsKnown += book.purchase.offer().getEmeraldCost() * book.copies;
				pr.bookCount += book.purchase.offer().getCount(Items.BOOK) * book.copies;
			}
			else if (book.estimate != null) {
				result.estimateMin += book.estimate[0];
				result.estimateMax += book.estimate[1];
				pr.estimateMin += book.estimate[0];
				pr.estimateMax += book.estimate[1];
				result.missingTrades++;
				// Bring enough for the most expensive price, plus the book the librarian wants
				addInput(result, Items.EMERALD, book.estimate[1]);
				addInput(result, Items.BOOK, 1);
				pr.bookCount++;
			}
			else {
				result.missingTrades++;
				pr.warnings.add(StringUtils.translate(KEY + "warning.not_tradeable", book.getName()));
			}
		}

		int[] bookValues = new int[pr.books.size()];
		int[] bookWork = new int[pr.books.size()];
		int mergeLevels = 0;
		boolean mergeTooExpensive = false;

		for (int i = 0; i < bookValues.length; i++) {
			BookResult book = pr.books.get(i);
			int level = book.purchase != null && book.copies == 1
					? book.purchase.offer().enchantments.getOrDefault(book.id, book.level)
					: book.level;
			bookValues[i] = level * Enchants.getBookAnvilCost(book.id);
			// A book combined from lower level copies already has prior work
			bookWork[i] = book.getWork();
			mergeLevels += book.getMergeLevels();
			mergeTooExpensive |= book.merges.stream().anyMatch(merge -> merge.cost() >= AnvilOptimizer.TOO_EXPENSIVE);
		}

		pr.anvil = AnvilOptimizer.optimize(bookValues, bookWork, 0);
		pr.totalLevels = pr.anvil.totalLevels() + mergeLevels;
		pr.tooExpensive = pr.anvil.tooExpensive() || mergeTooExpensive;
		result.totalLevels += pr.totalLevels;
		result.tooExpensive |= pr.tooExpensive;

		return pr;
	}

	/**
	 * The wanted enchantments that exist in this world and fit on the item
	 */
	private static Map<String, Integer> getValidEnchantments(SetupPiece piece, ItemStack stack, List<String> warnings) {
		Map<String, Integer> valid = new LinkedHashMap<>();

		for (Map.Entry<String, Integer> entry : piece.getEnchantments().entrySet()) {
			Holder<Enchantment> holder = Enchants.get(entry.getKey());

			if (holder == null || !holder.value().canEnchant(stack)) {
				warnings.add(StringUtils.translate(KEY + "warning.not_applicable", Enchants.getName(entry.getKey(), entry.getValue())));
				continue;
			}

			for (String other : valid.keySet()) {
				if (!Enchants.areCompatible(other, entry.getKey())) {
					warnings.add(StringUtils.translate(KEY + "warning.incompatible",
							Enchants.getName(entry.getKey(), entry.getValue()), Enchants.getName(other, valid.get(other))));
				}
			}

			valid.put(entry.getKey(), Math.min(entry.getValue(), holder.value().getMaxLevel()));
		}

		return valid;
	}

	/**
	 * The cheapest way to get this enchantment at this level from remembered villagers: one book of this level or higher,
	 * or several lower level books combined in the anvil (2x IV = V, 4x III = V...).
	 * A known price always wins over the estimate. On equal price the nearest villager is used.
	 */
	private static BookResult findBook(String id, int level, List<RememberedVillager> villagers) {
		Purchase best = null;
		int bestLevel = 0;
		int bestCost = Integer.MAX_VALUE;

		for (RememberedVillager villager : villagers) {
			for (RememberedOffer offer : villager.offers) {
				int offerLevel = offer.enchantments.getOrDefault(id, 0);

				if (offer.result != Items.ENCHANTED_BOOK || offer.enchantments.size() != 1 || offerLevel <= 0) {
					continue;
				}

				int boughtLevel = Math.min(offerLevel, level);
				int cost = offer.getEmeraldCost() * copiesNeeded(boughtLevel, level);

				if (best == null || cost < bestCost
						|| (cost == bestCost && distanceOrMax(villager) < distanceOrMax(best.villager()))) {
					best = new Purchase(villager, offer);
					bestLevel = boughtLevel;
					bestCost = cost;
				}
			}
		}

		if (best == null) {
			return new BookResult(id, level, null, level, Enchants.getBookPriceRange(id, level));
		}

		return new BookResult(id, level, best, bestLevel, null);
	}

	/**
	 * The cheapest smith trade for this item, counting the books still needed on top of the enchantments it comes with.
	 * Trades with an enchantment that conflicts with a wanted one are skipped.
	 */
	@Nullable
	private static Purchase findBestBase(SetupPiece piece, Map<String, Integer> wanted, List<RememberedVillager> villagers) {
		Purchase best = null;
		int bestCost = Integer.MAX_VALUE;

		for (RememberedVillager villager : villagers) {
			for (RememberedOffer offer : villager.offers) {
				if (offer.result != piece.getItem() || conflicts(offer.enchantments, wanted)) {
					continue;
				}

				int cost = offer.getEmeraldCost();

				for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
					if (offer.enchantments.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
						cost += getBookCostForComparison(entry.getKey(), entry.getValue(), villagers);
					}
				}

				Purchase purchase = new Purchase(villager, offer);

				if (best == null || cost < bestCost || (cost == bestCost && distanceOrMax(villager) < distanceOrMax(best.villager()))) {
					best = purchase;
					bestCost = cost;
				}
			}
		}

		return best;
	}

	private static boolean conflicts(Map<String, Integer> existing, Map<String, Integer> wanted) {
		for (String a : existing.keySet()) {
			for (String b : wanted.keySet()) {
				if (!Enchants.areCompatible(a, b)) {
					return true;
				}
			}
		}

		return false;
	}

	private static int getBookCostForComparison(String id, int level, List<RememberedVillager> villagers) {
		BookResult book = findBook(id, level, villagers);

		if (book.purchase != null) {
			return book.purchase.offer().getEmeraldCost() * book.copies;
		}

		return book.estimate != null ? (book.estimate[0] + book.estimate[1]) / 2 : 64;
	}

	private static int distanceOrMax(RememberedVillager villager) {
		int distance = villager.getDistance();
		return distance >= 0 ? distance : Integer.MAX_VALUE;
	}

	private static void addPurchase(Result result, Purchase purchase, String what, int count) {
		RememberedOffer offer = purchase.offer();
		result.emeraldsKnown += offer.getEmeraldCost() * count;
		addInput(result, offer.costA, offer.costACount * count);

		if (offer.hasCostB()) {
			addInput(result, offer.costB, offer.costBCount * count);
		}

		result.visits.computeIfAbsent(purchase.villager().uuid, uuid -> new Visit(purchase.villager()))
				.purchases.merge(what, count, Integer::sum);
	}

	private static void addInput(Result result, Item item, int count) {
		if (item != Items.AIR && count > 0) {
			result.inputs.merge(item, count, Integer::sum);
		}
	}

	/**
	 * The emerald price range of gear from villagers (26.3 trade data, {@code data/minecraft/villager_trade/}),
	 * before discounts and demand. Enchanted gear costs the base price plus the enchantment level it was rolled with (5 - 19).
	 * If several villagers sell an item, the cheapest trade is used.
	 */
	private static Map<Item, int[]> createVillagerPrices() {
		Map<Item, int[]> map = new LinkedHashMap<>();
		// Armorer
		map.put(Items.DIAMOND_HELMET, enchanted(8));
		map.put(Items.DIAMOND_CHESTPLATE, enchanted(16));
		map.put(Items.DIAMOND_LEGGINGS, enchanted(14));
		map.put(Items.DIAMOND_BOOTS, enchanted(8));
		map.put(Items.IRON_HELMET, fixed(5));
		map.put(Items.IRON_CHESTPLATE, fixed(9));
		map.put(Items.IRON_LEGGINGS, fixed(7));
		map.put(Items.IRON_BOOTS, fixed(4));
		map.put(Items.CHAINMAIL_HELMET, fixed(1));
		map.put(Items.CHAINMAIL_CHESTPLATE, fixed(4));
		map.put(Items.CHAINMAIL_LEGGINGS, fixed(3));
		map.put(Items.CHAINMAIL_BOOTS, fixed(1));
		map.put(Items.SHIELD, fixed(5));
		// Toolsmith
		map.put(Items.DIAMOND_PICKAXE, enchanted(13));
		map.put(Items.DIAMOND_AXE, enchanted(12));
		map.put(Items.DIAMOND_SHOVEL, enchanted(5));
		map.put(Items.DIAMOND_HOE, fixed(4));
		map.put(Items.IRON_PICKAXE, enchanted(3));
		map.put(Items.IRON_SHOVEL, enchanted(2));
		map.put(Items.STONE_PICKAXE, fixed(1));
		map.put(Items.STONE_AXE, fixed(1));
		map.put(Items.STONE_SHOVEL, fixed(1));
		map.put(Items.STONE_HOE, fixed(1));
		// Weaponsmith
		map.put(Items.DIAMOND_SWORD, enchanted(8));
		map.put(Items.IRON_SWORD, enchanted(2));
		map.put(Items.IRON_AXE, fixed(3));
		// Fletcher, fisherman, shepherd, leatherworker
		map.put(Items.BOW, fixed(2));
		map.put(Items.CROSSBOW, fixed(3));
		map.put(Items.FISHING_ROD, enchanted(3));
		map.put(Items.SHEARS, fixed(2));
		map.put(Items.LEATHER_HELMET, fixed(5));
		map.put(Items.LEATHER_CHESTPLATE, fixed(7));
		map.put(Items.LEATHER_LEGGINGS, fixed(3));
		map.put(Items.LEATHER_BOOTS, fixed(4));
		return map;
	}

	private static int[] fixed(int price) {
		return new int[] { price, price };
	}

	private static int[] enchanted(int basePrice) {
		return new int[] { basePrice + 5, basePrice + 19 };
	}
}
