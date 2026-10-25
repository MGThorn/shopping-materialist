package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * How a trading setup is put into a shopping list, see {@link TradingExport}. Saved with the setup.
 */
public class ExportOptions {
	/**
	 * Which emeralds go on the list for books and items without a remembered villager (only a price range is known)
	 */
	public enum EstimateMode {
		NONE, MIN, AVG, MAX;

		public String getDisplayName() {
			return StringUtils.translate(Reference.MOD_ID + ".gui.trading.export.estimate." + this.name().toLowerCase(Locale.ROOT));
		}

		public EstimateMode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}

		public int pick(int min, int max) {
			return switch (this) {
				case NONE -> 0;
				case MIN -> min;
				case AVG -> (min + max + 1) / 2;
				case MAX -> max;
			};
		}
	}

	// Empty for the default name "Trading: <setup>"
	public String listName = "";
	public EstimateMode estimate = EstimateMode.MAX;
	public boolean booksAsRaw;
	public boolean materialsAsRaw;
	// The items of the remembered emerald trades (villagers buy them for emeralds) used instead of emeralds
	public final Set<Item> emeraldTrades = new LinkedHashSet<>();

	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("listName", this.listName);
		obj.addProperty("estimate", this.estimate.name());
		obj.addProperty("booksAsRaw", this.booksAsRaw);
		obj.addProperty("materialsAsRaw", this.materialsAsRaw);

		JsonArray trades = new JsonArray();
		this.emeraldTrades.forEach(item -> trades.add(RememberedOffer.itemId(item)));
		obj.add("emeraldTrades", trades);
		return obj;
	}

	public static ExportOptions fromJson(JsonObject obj) {
		ExportOptions options = new ExportOptions();

		try {
			if (obj.has("listName")) {
				options.listName = obj.get("listName").getAsString();
			}

			if (obj.has("estimate")) {
				options.estimate = EstimateMode.valueOf(obj.get("estimate").getAsString());
			}

			options.booksAsRaw = obj.has("booksAsRaw") && obj.get("booksAsRaw").getAsBoolean();
			options.materialsAsRaw = obj.has("materialsAsRaw") && obj.get("materialsAsRaw").getAsBoolean();

			if (obj.has("emeraldTrades") && obj.get("emeraldTrades").isJsonArray()) {
				for (JsonElement element : obj.getAsJsonArray("emeraldTrades")) {
					Item item = RememberedOffer.itemFromId(element.getAsString());

					if (item != Items.AIR) {
						options.emeraldTrades.add(item);
					}
				}
			}
		}
		catch (RuntimeException ignored) {
			// Keeps what was read so far, the rest stays at the defaults
		}

		return options;
	}
}
