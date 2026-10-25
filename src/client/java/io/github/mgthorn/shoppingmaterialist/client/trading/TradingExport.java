package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Turns the result of a trading setup into the items of a shopping list, with the {@link ExportOptions} of the setup:
 * the estimate for unknown prices, emerald trades instead of emeralds, and raw materials instead of crafted ones.
 */
public class TradingExport {
	/**
	 * A trade of a remembered villager that gives emeralds for an item
	 */
	public record EmeraldOffer(RememberedVillager villager, RememberedOffer offer) {
		/**
		 * How often it can still be done; all uses if the villager may have restocked since
		 */
		int getTradesLeft() {
			return this.offer.uses > 0 && this.villager.isMaybeRestocked() ? this.offer.maxUses : this.offer.getUsesLeft();
		}

		double getItemsPerEmerald() {
			return (double) this.offer.costACount / Math.max(1, this.offer.resultCount);
		}
	}

	/**
	 * All remembered trades that buy the same item for emeralds, cheapest first
	 * @param available the emeralds all of them can still give
	 */
	public record EmeraldTrade(Item item, List<EmeraldOffer> offers, int available) {
		public EmeraldOffer getCheapest() {
			return this.offers.getFirst();
		}
	}

	/**
	 * @param items what goes on the shopping list
	 * @param covered the emeralds each ticked emerald trade covers
	 */
	public record Preview(Map<Item, Integer> items, Map<Item, Integer> covered) {
	}

	/**
	 * The emerald trades of the remembered villagers, the ones giving the most emeralds first
	 */
	public static List<EmeraldTrade> getEmeraldTrades(List<RememberedVillager> villagers) {
		Map<Item, List<EmeraldOffer>> byItem = new LinkedHashMap<>();

		for (RememberedVillager villager : villagers) {
			for (RememberedOffer offer : villager.offers) {
				if (offer.result == Items.EMERALD && offer.costA != Items.EMERALD) {
					byItem.computeIfAbsent(offer.costA, item -> new ArrayList<>()).add(new EmeraldOffer(villager, offer));
				}
			}
		}

		List<EmeraldTrade> trades = new ArrayList<>();

		byItem.forEach((item, offers) -> {
			offers.sort(Comparator.comparingDouble(EmeraldOffer::getItemsPerEmerald));
			int available = offers.stream().mapToInt(o -> o.getTradesLeft() * o.offer().resultCount).sum();
			trades.add(new EmeraldTrade(item, offers, available));
		});

		trades.sort(Comparator.comparingInt(EmeraldTrade::available).reversed());
		return trades;
	}

	public static Preview build(TradingCalculator.Result result, ExportOptions options, List<EmeraldTrade> trades) {
		Map<Item, Integer> inputs = new LinkedHashMap<>(result.inputs);
		Map<Item, Integer> covered = new LinkedHashMap<>();

		// The inputs bring the most expensive price for unknown trades; swap that for the chosen estimate
		int emeralds = inputs.getOrDefault(Items.EMERALD, 0) - result.estimateMax
				+ options.estimate.pick(result.estimateMin, result.estimateMax);
		inputs.remove(Items.EMERALD);

		if (options.estimate == ExportOptions.EstimateMode.NONE) {
			add(inputs, Items.BOOK, -result.estimateBooks);
		}

		// The ticked emerald trades cover the emeralds, in the order they are listed, each one's cheapest offers first
		for (EmeraldTrade trade : trades) {
			if (emeralds <= 0 || !options.emeraldTrades.contains(trade.item())) {
				continue;
			}

			int before = emeralds;

			for (EmeraldOffer emeraldOffer : trade.offers()) {
				RememberedOffer offer = emeraldOffer.offer();
				int perTrade = Math.max(1, offer.resultCount);
				int count = Math.min(emeraldOffer.getTradesLeft(), (emeralds + perTrade - 1) / perTrade);

				if (count <= 0) {
					continue;
				}

				add(inputs, offer.costA, offer.costACount * count);

				if (offer.hasCostB()) {
					add(inputs, offer.costB, offer.costBCount * count);
				}

				emeralds -= count * perTrade;

				if (emeralds <= 0) {
					break;
				}
			}

			covered.put(trade.item(), before - Math.max(0, emeralds));
		}

		Map<Item, Integer> items = new LinkedHashMap<>();
		add(items, Items.EMERALD, emeralds);
		inputs.forEach((item, count) -> add(items, item, count));

		if (options.booksAsRaw) {
			int books = items.getOrDefault(Items.BOOK, 0);
			items.remove(Items.BOOK);
			add(items, Items.LEATHER, books);
			add(items, Items.PAPER, books * 3);
		}

		if (options.materialsAsRaw) {
			toRaw(items);
		}

		return new Preview(items, covered);
	}

	/**
	 * One crafting step back: 2 planks make 4 sticks, 3 sugar cane make 3 paper, a netherite ingot is 4 scrap and 4 gold
	 */
	private static void toRaw(Map<Item, Integer> items) {
		Integer sticks = items.remove(Items.STICK);

		if (sticks != null) {
			add(items, Items.OAK_PLANKS, (sticks + 3) / 4 * 2);
		}

		Integer paper = items.remove(Items.PAPER);

		if (paper != null) {
			add(items, Items.SUGAR_CANE, (paper + 2) / 3 * 3);
		}

		Integer netherite = items.remove(Items.NETHERITE_INGOT);

		if (netherite != null) {
			add(items, Items.NETHERITE_SCRAP, netherite * 4);
			add(items, Items.GOLD_INGOT, netherite * 4);
		}
	}

	private static void add(Map<Item, Integer> items, Item item, int count) {
		if (item == Items.AIR || count == 0) {
			return;
		}

		int total = items.getOrDefault(item, 0) + count;

		if (total > 0) {
			items.put(item, total);
		}
		else {
			items.remove(item);
		}
	}
}
