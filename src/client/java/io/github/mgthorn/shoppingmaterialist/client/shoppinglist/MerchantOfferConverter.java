package io.github.mgthorn.shoppingmaterialist.client.shoppinglist;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Turns the trades of a merchant (wandering trader, villager...) into a shopping list.
 */
public class MerchantOfferConverter {
	/**
	 * Everything needed to use up every trade that is not out of stock yet:
	 * both cost items of each trade, times the uses the trade has left.
	 */
	public static Map<Item, Integer> getItems(MerchantOffers offers) {
		Map<Item, Integer> items = new LinkedHashMap<>();

		for (MerchantOffer offer : offers) {
			int usesLeft = offer.getMaxUses() - offer.getUses();

			if (usesLeft > 0) {
				addCost(items, offer.getCostA(), usesLeft);
				addCost(items, offer.getCostB(), usesLeft);
			}
		}

		return items;
	}

	private static void addCost(Map<Item, Integer> items, ItemStack cost, int usesLeft) {
		if (!cost.isEmpty()) {
			int count = (int) Math.min((long) cost.getCount() * usesLeft, Integer.MAX_VALUE);
			items.merge(cost.getItem(), count, (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
		}
	}
}
