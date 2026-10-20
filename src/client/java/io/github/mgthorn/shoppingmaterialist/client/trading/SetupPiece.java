package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * One item of a trading setup: the item, where it comes from, and the enchantments wanted on it.
 */
public class SetupPiece {
	public enum Source {
		CRAFT, BUY, HAVE;

		public String getDisplayName() {
			return StringUtils.translate(Reference.MOD_ID + ".gui.trading.source." + this.name().toLowerCase(Locale.ROOT));
		}

		public Source next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	private final Item item;
	// Optional label, e.g. "Silk Touch", shown after the item name
	private String label;
	private Source source;
	private boolean enabled = true;
	private final Map<String, Integer> enchantments = new LinkedHashMap<>();

	public SetupPiece(Item item, String label, Source source) {
		this.item = item;
		this.label = label;
		this.source = source;
	}

	public Item getItem() {
		return this.item;
	}

	public ItemStack getStack() {
		return new ItemStack(this.item);
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	/**
	 * e.g. "Diamond Pickaxe (Silk Touch)"
	 */
	public String getDisplayName() {
		String name = this.getStack().getHoverName().getString();
		return this.label.isEmpty() ? name : name + " (" + this.label + ")";
	}

	public Source getSource() {
		return this.source;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Map<String, Integer> getEnchantments() {
		return this.enchantments;
	}

	public SetupPiece enchant(String id, int level) {
		if (level <= 0) {
			this.enchantments.remove(id);
		}
		else {
			this.enchantments.put(id, level);
		}

		return this;
	}

	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("item", RememberedOffer.itemId(this.item));
		obj.addProperty("label", this.label);
		obj.addProperty("source", this.source.name());
		obj.addProperty("enabled", this.enabled);

		JsonObject enchants = new JsonObject();
		this.enchantments.forEach(enchants::addProperty);
		obj.add("enchantments", enchants);
		return obj;
	}

	@Nullable
	public static SetupPiece fromJson(JsonObject obj) {
		try {
			Item item = RememberedOffer.itemFromId(obj.get("item").getAsString());

			if (item == Items.AIR) {
				return null;
			}

			Source source = Source.valueOf(obj.get("source").getAsString());
			SetupPiece piece = new SetupPiece(item, obj.has("label") ? obj.get("label").getAsString() : "", source);
			piece.enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();

			if (obj.has("enchantments") && obj.get("enchantments").isJsonObject()) {
				obj.getAsJsonObject("enchantments").entrySet().forEach(e -> piece.enchant(e.getKey(), e.getValue().getAsInt()));
			}

			return piece;
		}
		catch (RuntimeException e) {
			return null;
		}
	}
}
