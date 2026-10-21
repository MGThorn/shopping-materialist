package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.ShoppingMaterialist;
import io.github.mgthorn.shoppingmaterialist.client.compat.Waypoints;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedOffer;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedVillager;
import io.github.mgthorn.shoppingmaterialist.client.trading.VillagerMemory;
import io.github.mgthorn.shoppingmaterialist.client.trading.WorldKey;

/**
 * The remembered villagers of the current world.
 * <p>
 * Top: everything they sell that the trading lists use (enchanted books and gear), each icon with the number of villagers
 * selling it. Hover shows the sellers, a click shows only those villagers (click again to show all).
 * <p>
 * Below: one row per villager, nearest first: its job block, level and distance, then every trade
 * with the number of trades left as the stack size, and the coordinates at the end.
 * Names, coordinates and trades are in the tooltips. Dead or moved villagers can be deleted (with a confirmation).
 */
public class GuiVillagerBrowser extends GuiBase {
	private static final String KEY = Reference.MOD_ID + ".gui.trading.villager_browser.";
	private static final int TOP_Y = 26;
	private static final int CELL = 18;
	private static final int LABEL_HEIGHT = 12;
	private static final int ROW_HEIGHT = 20;
	// Draw the villager itself in front of its job block. Off for now, it didn't fit the rows; the code is kept for later.
	private static final boolean SHOW_VILLAGER_ENTITY = false;
	private static final int ENTITY_WIDTH = 22;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int GOLD = 0xFFFFAA00;
	private static final String[] ROMAN = { "I", "II", "III", "IV", "V" };

	// The offer shown as filter; static, so it is kept when the screen is reopened
	@Nullable private static OfferKey selected;

	/**
	 * What a trade sells: an enchanted book with one enchantment, or an item
	 */
	private record OfferKey(Item item, String enchantment, int level) {
		ItemStack createStack() {
			return this.enchantment.isEmpty() ? new ItemStack(this.item) : Enchants.createBook(this.enchantment, this.level);
		}

		String getName() {
			return this.enchantment.isEmpty() ? new ItemStack(this.item).getHoverName().getString() : Enchants.getName(this.enchantment, this.level);
		}

		boolean isBook() {
			return !this.enchantment.isEmpty();
		}
	}

	private record Seller(RememberedVillager villager, RememberedOffer offer) {
	}

	/**
	 * Where something hoverable or clickable was drawn in the last frame
	 */
	private record HitBox(int x, int y, int width, int height, List<String> tooltip, @Nullable OfferKey offer) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	private final Map<OfferKey, List<Seller>> books = new LinkedHashMap<>();
	private final Map<OfferKey, List<Seller>> gear = new LinkedHashMap<>();
	private final List<HitBox> hitBoxes = new ArrayList<>();
	// The offer key of every trade, so drawing doesn't look up enchantments every frame
	private final Map<RememberedOffer, OfferKey> offerKeys = new IdentityHashMap<>();
	private List<RememberedVillager> villagers = new ArrayList<>();
	// Client-only copies of the villagers, only for drawing them
	private final Map<UUID, LivingEntity> entities = new HashMap<>();
	// Entity ids for those copies. Real entities get positive ids when they join the world, so these can't clash.
	private static int nextEntityId = -1_000_000;
	private static boolean entityDrawingFailed;
	private int offersY;
	private int villagersY;
	private int listY;
	private int scroll;

	public GuiVillagerBrowser(@Nullable Screen parent) {
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.villager_browser");
		this.setParent(parent);
	}

	// ---- Data ----

	/**
	 * The book or gear a trade sells, or null for anything else (bread, glass, emeralds...)
	 */
	@Nullable
	private static OfferKey getOfferKey(RememberedOffer offer) {
		if (offer.result == Items.ENCHANTED_BOOK) {
			if (offer.enchantments.size() != 1) {
				return null;
			}

			Map.Entry<String, Integer> enchantment = offer.enchantments.entrySet().iterator().next();
			return new OfferKey(Items.ENCHANTED_BOOK, enchantment.getKey(), enchantment.getValue());
		}

		return Enchants.getApplicable(new ItemStack(offer.result)).isEmpty() ? null : new OfferKey(offer.result, "", 0);
	}

	private void collectOffers() {
		this.books.clear();
		this.gear.clear();
		this.offerKeys.clear();
		List<RememberedVillager> all = new ArrayList<>(VillagerMemory.getInstance().getVillagers());
		all.sort(Comparator.comparingInt(GuiVillagerBrowser::getSortDistance));

		for (RememberedVillager villager : all) {
			for (RememberedOffer offer : villager.offers) {
				OfferKey key = getOfferKey(offer);

				if (key != null) {
					this.offerKeys.put(offer, key);
					(key.isBook() ? this.books : this.gear).computeIfAbsent(key, k -> new ArrayList<>()).add(new Seller(villager, offer));
				}
			}
		}

		sortByName(this.books);
		sortByName(this.gear);

		// The filter is dropped once nobody sells it any more
		if (selected != null && !this.books.containsKey(selected) && !this.gear.containsKey(selected)) {
			selected = null;
		}

		this.villagers = all.stream().filter(villager -> selected == null || this.sells(villager, selected)).toList();
	}

	private static void sortByName(Map<OfferKey, List<Seller>> map) {
		List<Map.Entry<OfferKey, List<Seller>>> entries = new ArrayList<>(map.entrySet());
		entries.sort(Comparator.comparing((Map.Entry<OfferKey, List<Seller>> entry) -> entry.getKey().getName()));
		map.clear();
		entries.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
	}

	private boolean sells(RememberedVillager villager, OfferKey key) {
		return villager.offers.stream().anyMatch(offer -> key.equals(this.offerKeys.get(offer)));
	}

	private static int getSortDistance(RememberedVillager villager) {
		int distance = villager.getDistance();
		return distance >= 0 ? distance : Integer.MAX_VALUE;
	}

	/**
	 * The block a villager works at, e.g. a lectern for librarians
	 */
	private static ItemStack getJobIcon(RememberedVillager villager) {
		if (RememberedVillager.TYPE_WANDERING_TRADER.equals(villager.type)) {
			return new ItemStack(Items.WANDERING_TRADER_SPAWN_EGG);
		}

		Identifier id = Identifier.tryParse(villager.profession);
		Item item = switch (id != null ? id.getPath() : "") {
			case "armorer" -> Items.BLAST_FURNACE;
			case "butcher" -> Items.SMOKER;
			case "cartographer" -> Items.CARTOGRAPHY_TABLE;
			case "cleric" -> Items.BREWING_STAND;
			case "farmer" -> Items.COMPOSTER;
			case "fisherman" -> Items.BARREL;
			case "fletcher" -> Items.FLETCHING_TABLE;
			case "leatherworker" -> Items.CAULDRON;
			case "librarian" -> Items.LECTERN;
			case "mason" -> Items.STONECUTTER;
			case "shepherd" -> Items.LOOM;
			case "toolsmith" -> Items.SMITHING_TABLE;
			case "weaponsmith" -> Items.GRINDSTONE;
			default -> Items.VILLAGER_SPAWN_EGG;
		};

		return new ItemStack(item);
	}

	/**
	 * e.g. "17 Emerald + 1 Book -> Protection IV (3/12 used)"
	 */
	private String formatTrade(RememberedOffer offer) {
		OfferKey key = this.offerKeys.get(offer);
		String result = key != null ? key.getName() : new ItemStack(offer.result).getHoverName().getString();

		if (offer.resultCount > 1) {
			result = offer.resultCount + " " + result;
		}

		return GuiTradingList.formatCost(offer) + " -> " + result + " " + StringUtils.translate(KEY + "uses", offer.uses, offer.maxUses);
	}

	// ---- Layout ----

	private int getColumns() {
		return Math.max(1, (this.getScreenWidth() - 20) / CELL);
	}

	/**
	 * Books, then one empty cell, then gear, all in the same grid
	 */
	private int getOfferCells() {
		int gap = !this.books.isEmpty() && !this.gear.isEmpty() ? 1 : 0;
		return this.books.size() + gap + this.gear.size();
	}

	private int getOffersHeight() {
		int cells = this.getOfferCells();
		return ((cells + this.getColumns() - 1) / this.getColumns()) * CELL;
	}

	private int getVisibleRows() {
		return Math.max(1, (this.getScreenHeight() - this.listY - 32) / ROW_HEIGHT);
	}

	@Override
	public void initGui() {
		super.initGui();
		this.collectOffers();

		this.offersY = TOP_Y + LABEL_HEIGHT;
		this.villagersY = this.offersY + this.getOffersHeight() + 4;
		this.listY = this.villagersY + LABEL_HEIGHT;

		this.scroll = Math.max(0, Math.min(this.scroll, this.villagers.size() - this.getVisibleRows()));
		int end = Math.min(this.villagers.size(), this.scroll + this.getVisibleRows());

		for (int i = this.scroll; i < end; i++) {
			RememberedVillager villager = this.villagers.get(i);
			int y = this.listY + (i - this.scroll) * ROW_HEIGHT + (ROW_HEIGHT - 16) / 2;
			int deleteX = this.getScreenWidth() - 12 - 16;
			this.addButton(new ButtonGeneric(deleteX, y, 16, 16, "x", StringUtils.translate(KEY + "delete")),
					(btn, mouseButton) -> this.confirmDelete(villager));

			// Between the coordinates and the delete button
			this.addButton(new ButtonGeneric(deleteX - 4 - this.getWaypointButtonWidth(), y, this.getWaypointButtonWidth(), 16,
							Waypoints.getButtonLabel(villager), StringUtils.translate(KEY + "waypoint.hover")),
					(btn, mouseButton) -> {
						Waypoints.onWaypointButton(villager, this);
						// Shows the new state on the button
						this.initGui();
					});
		}

		int y = this.getScreenHeight() - 26;
		String back = StringUtils.translate(Reference.MOD_ID + ".gui.trading.done");
		ButtonGeneric backButton = new ButtonGeneric(10, y, this.getStringWidth(back) + 10, 20, back);
		this.addButton(backButton, (btn, mouseButton) -> GuiBase.openGui(this.getParent()));

		String clear = StringUtils.translate(KEY + "delete_all");
		ButtonGeneric clearButton = new ButtonGeneric(10 + backButton.getWidth() + 4, y, this.getStringWidth(clear) + 10, 20, clear);
		clearButton.setEnabled(!VillagerMemory.getInstance().getVillagers().isEmpty());
		this.addButton(clearButton, (btn, mouseButton) -> this.confirmClear());

		// Bottom right: turn off all waypoints of the JourneyMap group
		String disableAll = StringUtils.translate(KEY + "waypoints_off");
		int disableAllWidth = this.getStringWidth(disableAll) + 10;
		this.addButton(new ButtonGeneric(this.getScreenWidth() - 12 - disableAllWidth, y, disableAllWidth, 20, disableAll,
						StringUtils.translate(KEY + "waypoints_off.hover")),
				(btn, mouseButton) -> {
					Waypoints.onDisableAll(this);
					this.initGui();
				});
	}

	/**
	 * Wide enough for all its labels, so the coordinates don't move when it changes
	 */
	private int getWaypointButtonWidth() {
		int width = 0;

		for (String label : Waypoints.getButtonLabels()) {
			width = Math.max(width, this.getStringWidth(label));
		}

		return width + 10;
	}

	private void confirmDelete(RememberedVillager villager) {
		IConfirmationListener listener = new IConfirmationListener() {
			@Override
			public boolean onActionConfirmed() {
				VillagerMemory.getInstance().remove(villager);
				return true;
			}

			@Override
			public boolean onActionCancelled() {
				return true;
			}
		};

		GuiBase.openGui(new GuiConfirmAction(320, KEY + "delete_title", listener, this, KEY + "delete_message",
				villager.getDisplayName(), villager.getLocationText()));
	}

	private void confirmClear() {
		IConfirmationListener listener = new IConfirmationListener() {
			@Override
			public boolean onActionConfirmed() {
				VillagerMemory.getInstance().clear();
				return true;
			}

			@Override
			public boolean onActionCancelled() {
				return true;
			}
		};

		GuiBase.openGui(new GuiConfirmAction(320, KEY + "delete_all_title", listener, this, KEY + "delete_all_message",
				String.valueOf(WorldKey.getCurrent())));
	}

	// ---- Input ----

	@Override
	public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
		if (super.onMouseClicked(click, doubleClick)) {
			return true;
		}

		// An offer: show only its sellers, or everybody again
		if (click.button() == ScanCodes.OFFSET_MOUSE_LEFT) {
			for (HitBox box : this.hitBoxes) {
				if (box.offer() != null && box.contains(click.x(), click.y())) {
					selected = box.offer().equals(selected) ? null : box.offer();
					this.scroll = 0;
					this.initGui();
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0 && mouseY >= this.listY) {
			this.scroll -= (int) Math.signum(verticalAmount);
			this.initGui();
			return true;
		}

		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ---- Rendering ----

	// Row backgrounds are drawn as part of the background, so the delete buttons render on top of them
	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);

		int end = Math.min(this.villagers.size(), this.scroll + this.getVisibleRows());

		for (int i = this.scroll; i < end; i++) {
			int y = this.listY + (i - this.scroll) * ROW_HEIGHT;
			RenderUtils.drawRect(ctx, 10, y, this.getScreenWidth() - 20, ROW_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);
		}
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		this.hitBoxes.clear();

		if (VillagerMemory.getInstance().getVillagers().isEmpty()) {
			this.drawString(ctx, StringUtils.translate(KEY + "empty"), 12, TOP_Y + 6, GRAY);
			return;
		}

		String offersLabel = StringUtils.translate(selected != null ? KEY + "offers.filtered" : KEY + "offers");
		this.drawString(ctx, offersLabel, 12, TOP_Y + 2, GOLD);
		this.drawOffers(ctx);

		String world = StringUtils.translate(KEY + "villagers", this.villagers.size(), String.valueOf(WorldKey.getCurrent()));
		this.drawString(ctx, world, 12, this.villagersY + 2, GOLD);
		this.drawVillagerRows(ctx);

		for (HitBox box : this.hitBoxes) {
			if (box.contains(mouseX, mouseY)) {
				RenderUtils.drawHoverText(ctx, mouseX, mouseY, box.tooltip());
				break;
			}
		}
	}

	/**
	 * The books, then the gear, as icons with the number of villagers selling them
	 */
	private void drawOffers(GuiContext ctx) {
		int cols = this.getColumns();
		int i = 0;
		List<Map.Entry<OfferKey, List<Seller>>> entries = new ArrayList<>(this.books.entrySet());

		// One empty cell between the books and the gear
		if (!this.books.isEmpty() && !this.gear.isEmpty()) {
			entries.add(null);
		}

		entries.addAll(this.gear.entrySet());

		for (Map.Entry<OfferKey, List<Seller>> entry : entries) {
			int x = 10 + (i % cols) * CELL;
			int cellY = this.offersY + (i / cols) * CELL;
			i++;

			if (entry == null) {
				continue;
			}

			OfferKey key = entry.getKey();
			List<Seller> sellers = entry.getValue();

			if (key.equals(selected)) {
				RenderUtils.drawRect(ctx, x, cellY, CELL, CELL, 0x8055FF55);
			}

			ItemStack stack = key.createStack();
			ctx.renderItem(stack, x + 1, cellY + 1);
			// The number of villagers selling it, drawn like a stack size
			ctx.renderItemDecorations(this.font, stack.copyWithCount(sellers.size()), x + 1, cellY + 1);

			List<String> tooltip = new ArrayList<>();
			tooltip.add(key.getName());

			for (Seller seller : sellers) {
				tooltip.add(StringUtils.translate(KEY + "seller", seller.villager().getDisplayName(),
						GuiTradingList.formatCost(seller.offer()), seller.villager().getLocationText()));
			}

			tooltip.add(StringUtils.translate(key.equals(selected) ? KEY + "click.show_all" : KEY + "click.filter"));
			this.hitBoxes.add(new HitBox(x, cellY, CELL, CELL, tooltip, key));
		}
	}

	/**
	 * The villager, its job block, level and distance, then every trade with the number of trades left below it
	 */
	private void drawVillagerRows(GuiContext ctx) {
		int end = Math.min(this.villagers.size(), this.scroll + this.getVisibleRows());
		int deleteX = this.getScreenWidth() - 12 - 16;

		for (int i = this.scroll; i < end; i++) {
			RememberedVillager villager = this.villagers.get(i);
			int y = this.listY + (i - this.scroll) * ROW_HEIGHT;
			int iconY = y + (ROW_HEIGHT - 16) / 2;
			int x = 12;
			List<String> info = List.of(villager.getDisplayName(), villager.getCoordinates() + " (" + villager.dimension + ")",
					StringUtils.translate(KEY + "info", villager.offers.size(), formatAge(villager.lastSeen)));

			LivingEntity entity = SHOW_VILLAGER_ENTITY ? this.getEntity(villager) : null;

			if (entity != null && !entityDrawingFailed) {
				try {
					// Looking straight ahead: the "mouse" is level with its eyes
					InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, x, y + 1, x + 20, y + ROW_HEIGHT - 1, 13, 0.0625f,
							x + 10, y + 8, entity);
				}
				catch (RuntimeException e) {
					// Don't let the picture break the whole screen; the rows still work without it
					entityDrawingFailed = true;
					ShoppingMaterialist.LOGGER.error("Failed to draw a villager in the villager browser", e);
				}
			}

			if (SHOW_VILLAGER_ENTITY) {
				this.hitBoxes.add(new HitBox(x, y, 20, ROW_HEIGHT, info, null));
				x += ENTITY_WIDTH;
			}

			ctx.renderItem(getJobIcon(villager), x, iconY);
			this.hitBoxes.add(new HitBox(x, iconY, 16, 16, info, null));
			x += 20;

			String level = villager.level >= 1 && villager.level <= ROMAN.length ? ROMAN[villager.level - 1] : "";
			this.drawStringWithShadow(ctx, level, x, iconY + 4, GOLD);
			x += 22;

			int distance = villager.getDistance();
			this.drawStringWithShadow(ctx, distance >= 0 ? distance + " m" : "-", x, iconY + 4, 0xFF55FFFF);
			x += 42;

			// The coordinates at the end of the row, left of the waypoint and delete buttons
			String coordinates = villager.getCoordinates();
			int coordinatesX = deleteX - 4 - this.getWaypointButtonWidth() - 6 - this.getStringWidth(coordinates);
			this.drawStringWithShadow(ctx, coordinates, coordinatesX, iconY + 4, 0xFF55FFFF);
			this.hitBoxes.add(new HitBox(coordinatesX, y, this.getStringWidth(coordinates), ROW_HEIGHT, info, null));

			for (RememberedOffer offer : villager.offers) {
				if (x + 16 > coordinatesX - 6) {
					break;
				}

				OfferKey key = this.offerKeys.get(offer);
				ItemStack stack = key != null ? key.createStack() : new ItemStack(offer.result);
				ctx.renderItem(stack, x, iconY);

				// How many more times the trade can be done before the villager restocks, drawn like a stack size
				int usesLeft = offer.getUsesLeft();

				if (usesLeft > 0) {
					ctx.renderItemDecorations(this.font, stack.copyWithCount(usesLeft), x, iconY);
				}
				else {
					// Sold out until the villager restocks
					RenderUtils.drawRect(ctx, x, iconY, 16, 16, 0x90000000);
				}

				if (key != null && key.equals(selected)) {
					RenderUtils.drawOutline(ctx, x - 1, iconY - 1, 18, 18, 0xFF55FF55);
				}

				this.hitBoxes.add(new HitBox(x, iconY, 16, 16, List.of(this.formatTrade(offer)), null));
				x += 18;
			}
		}
	}

	/**
	 * A villager or wandering trader that only exists for drawing, dressed like the remembered one
	 */
	@Nullable
	private LivingEntity getEntity(RememberedVillager villager) {
		return this.entities.computeIfAbsent(villager.uuid, uuid -> createEntity(villager));
	}

	@Nullable
	private static LivingEntity createEntity(RememberedVillager villager) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			return null;
		}

		if (RememberedVillager.TYPE_WANDERING_TRADER.equals(villager.type)) {
			WanderingTrader trader = new WanderingTrader(EntityTypes.WANDERING_TRADER, mc.level);
			trader.setId(nextEntityId--);
			return trader;
		}

		// Rendering needs an entity id, which is otherwise only assigned when an entity joins the world
		Villager entity = new Villager(EntityTypes.VILLAGER, mc.level);
		entity.setId(nextEntityId--);
		Optional<Holder.Reference<VillagerProfession>> profession = lookup(mc, Registries.VILLAGER_PROFESSION, villager.profession);
		Optional<Holder.Reference<VillagerType>> type = lookup(mc, Registries.VILLAGER_TYPE, villager.villagerType);

		if (profession.isPresent() && type.isPresent()) {
			entity.setVillagerData(new VillagerData(type.get(), profession.get(), Math.max(1, villager.level)));
		}

		return entity;
	}

	private static <T> Optional<Holder.Reference<T>> lookup(Minecraft mc, ResourceKey<? extends Registry<T>> registry, String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier != null ? mc.level.registryAccess().lookupOrThrow(registry).get(identifier) : Optional.empty();
	}

	private static String formatAge(long time) {
		long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60000);

		if (minutes < 60) {
			return StringUtils.translate(KEY + "age.minutes", minutes);
		}

		long hours = minutes / 60;
		return hours < 48 ? StringUtils.translate(KEY + "age.hours", hours) : StringUtils.translate(KEY + "age.days", hours / 24);
	}
}
