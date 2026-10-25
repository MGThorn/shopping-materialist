package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
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
 * Top: everything they sell, in three groups (enchanted books, gear, other items), each icon with the number of villagers
 * selling it (small top left for books and gear, the stack size for other items). Hover shows the villagers, a click shows only those villagers (click again to show all).
 * In their own row below: the emeralds all of them can get, then the items they buy for emeralds. Each of those shows
 * the villagers buying it (top left), the trades left and the items per trade of the cheapest one (top right, stack size),
 * and "= " all items that can be sold.
 * <p>
 * Below: one row per villager, nearest first: its job block, level and distance, then every trade with the trades left
 * (small top right for books, gear and emerald trades, which also show the items per trade as the stack size;
 * the stack size for other items; the full uses in yellow if it may have restocked since), and the coordinates at the end.
 * Names, coordinates and trades are in the tooltips. Dead or moved villagers can be deleted (with a confirmation).
 */
public class GuiVillagerBrowser extends GuiBase {
	private static final String KEY = Reference.MOD_ID + ".gui.trading.villager_browser.";
	// Sellers listed in the tooltip of an offer; clicking it shows all of them
	private static final int MAX_SELLERS = 3;
	private static final int TOP_Y = 26;
	private static final int CELL = 18;
	private static final int LABEL_HEIGHT = 12;
	private static final int ROW_HEIGHT = 20;
	// Draw the villager itself in front of its job block. Off for now, it didn't fit the rows; the code is kept for later.
	private static final boolean SHOW_VILLAGER_ENTITY = false;
	private static final int ENTITY_WIDTH = 22;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int GOLD = 0xFFFFAA00;
	private static final ItemStack EMERALD = new ItemStack(Items.EMERALD);
	// The emerald row: scale of the small numbers on the icons, and the colors of the numbers
	private static final float SMALL = 0.6f;
	private static final int AQUA = 0xFF55FFFF;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int SHULKER_BOX = 27 * 64;
	private static final String[] ROMAN = { "I", "II", "III", "IV", "V" };

	// The offer shown as filter; static, so it is kept when the screen is reopened
	@Nullable private static OfferKey selected;

	/**
	 * The groups of the offers overview, in this order. The emerald trades start a row of their own.
	 */
	private enum Group {
		BOOKS,
		GEAR,
		ITEMS,
		EMERALDS
	}

	private enum CellKind {
		OFFER,
		GAP,
		// The emeralds of all emerald trades together, at the start of their row
		EMERALD_TOTAL,
		// A line between the total and the emerald trades
		SEPARATOR
	}

	/**
	 * One cell of the offers grid; key and sellers are only set for offers
	 */
	private record GridCell(CellKind kind, @Nullable OfferKey key, List<Seller> sellers) {
		static final GridCell GAP = new GridCell(CellKind.GAP, null, List.of());
	}

	/**
	 * A part of the emerald row, which has parts of different widths and wraps into more lines when full
	 */
	private record EmeraldPlace(CellKind kind, int x, int line, int width, @Nullable OfferKey key, List<Seller> sellers) {
	}

	/**
	 * What a trade is about: an enchanted book with one enchantment, or an item
	 * @param buys the villager buys the item for emeralds, instead of selling it
	 */
	private record OfferKey(Item item, String enchantment, int level, boolean buys, Group group) {
		ItemStack createStack() {
			return this.enchantment.isEmpty() ? new ItemStack(this.item) : Enchants.createBook(this.enchantment, this.level);
		}

		String getName() {
			return this.enchantment.isEmpty() ? new ItemStack(this.item).getHoverName().getString() : Enchants.getName(this.enchantment, this.level);
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

	private final Map<Group, Map<OfferKey, List<Seller>>> groups = new EnumMap<>(Group.class);
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
	 * The enchanted book, gear or item a trade sells, or the item it buys for emeralds (wheat, paper...)
	 */
	private static OfferKey getOfferKey(RememberedOffer offer) {
		if (offer.result == Items.ENCHANTED_BOOK && offer.enchantments.size() == 1) {
			Map.Entry<String, Integer> enchantment = offer.enchantments.entrySet().iterator().next();
			return new OfferKey(Items.ENCHANTED_BOOK, enchantment.getKey(), enchantment.getValue(), false, Group.BOOKS);
		}

		if (offer.result == Items.EMERALD && offer.costA != Items.EMERALD) {
			return new OfferKey(offer.costA, "", 0, true, Group.EMERALDS);
		}

		Group group = Enchants.getApplicable(new ItemStack(offer.result)).isEmpty() ? Group.ITEMS : Group.GEAR;
		return new OfferKey(offer.result, "", 0, false, group);
	}

	private void collectOffers() {
		this.groups.clear();
		this.offerKeys.clear();

		for (Group group : Group.values()) {
			this.groups.put(group, new LinkedHashMap<>());
		}

		List<RememberedVillager> all = new ArrayList<>(VillagerMemory.getInstance().getVillagers());
		all.sort(Comparator.comparingInt(GuiVillagerBrowser::getSortDistance));

		for (RememberedVillager villager : all) {
			for (RememberedOffer offer : villager.offers) {
				OfferKey key = getOfferKey(offer);
				this.offerKeys.put(offer, key);
				this.groups.get(key.group()).computeIfAbsent(key, k -> new ArrayList<>()).add(new Seller(villager, offer));
			}
		}

		this.groups.values().forEach(GuiVillagerBrowser::sortByName);
		sortBooksByUsage(this.groups.get(Group.BOOKS));

		// The filter is dropped once nobody trades it any more
		if (selected != null && !this.groups.get(selected.group()).containsKey(selected)) {
			selected = null;
		}

		this.villagers = all.stream().filter(villager -> selected == null || this.sells(villager, selected)).toList();
	}

	/**
	 * Most used enchantments first, see {@link Enchants#compareByUsage}
	 */
	private static void sortBooksByUsage(Map<OfferKey, List<Seller>> map) {
		List<Map.Entry<OfferKey, List<Seller>>> entries = new ArrayList<>(map.entrySet());
		entries.sort((a, b) -> Enchants.compareByUsage(a.getKey().enchantment(), a.getKey().level(), b.getKey().enchantment(), b.getKey().level()));
		map.clear();
		entries.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
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
	 * The level as I - V, empty if unknown
	 */
	static String getLevelText(RememberedVillager villager) {
		return villager.level >= 1 && villager.level <= ROMAN.length ? ROMAN[villager.level - 1] : "";
	}

	/**
	 * The block a villager works at, e.g. a lectern for librarians
	 */
	static ItemStack getJobIcon(RememberedVillager villager) {
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
	 * e.g. "17 Emerald + 1 Book -> Protection IV" or "20 Wheat -> 1 Emerald"
	 */
	private String formatDeal(RememberedOffer offer) {
		OfferKey key = this.offerKeys.get(offer);
		String result = key != null && !key.buys() ? key.getName() : new ItemStack(offer.result).getHoverName().getString();

		if (offer.resultCount > 1) {
			result = offer.resultCount + " " + result;
		}

		return GuiTradingLines.formatCost(offer) + " -> " + result;
	}

	/**
	 * The deal and how often it was used, e.g. "17 Emerald + 1 Book -> Protection IV (3/12 used)",
	 * plus a note if the villager may have restocked since
	 */
	private List<String> getTradeTooltip(RememberedVillager villager, RememberedOffer offer) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add(this.formatDeal(offer) + " " + StringUtils.translate(KEY + "uses", offer.uses, offer.maxUses));

		if (isMaybeRestocked(villager, offer)) {
			tooltip.add(GuiBase.TXT_YELLOW + StringUtils.translate(KEY + "maybe_restocked", formatAge(villager.lastSeen)));
		}

		return tooltip;
	}

	/**
	 * A trade that was used before, of a villager that may have restocked since
	 */
	private static boolean isMaybeRestocked(RememberedVillager villager, RememberedOffer offer) {
		return offer.uses > 0 && villager.isMaybeRestocked();
	}

	/**
	 * How often the trade can still be done: the trades left, or all of them if the villager may have restocked
	 */
	private static int getTradesLeft(Seller seller) {
		return isMaybeRestocked(seller.villager(), seller.offer()) ? seller.offer().maxUses : seller.offer().getUsesLeft();
	}

	/**
	 * The emeralds an emerald trade can still give
	 */
	private static int getEmeralds(Seller seller) {
		return getTradesLeft(seller) * seller.offer().resultCount;
	}

	/**
	 * How many of the item an emerald trade can still take
	 */
	private static int getItemsNeeded(Seller seller) {
		return getTradesLeft(seller) * seller.offer().costACount;
	}

	private static int sum(List<Seller> sellers, ToIntFunction<Seller> value) {
		return sellers.stream().mapToInt(value).sum();
	}

	/**
	 * The villager that wants the fewest items per trade; with the same price, the one with more trades left
	 */
	private static Seller getCheapest(List<Seller> sellers) {
		return sellers.stream().min(Comparator.comparingInt((Seller seller) -> seller.offer().costACount)
				.thenComparing(Comparator.comparingInt(GuiVillagerBrowser::getTradesLeft).reversed())).orElseThrow();
	}

	/**
	 * The villager that sells the item for the lowest price per item; with the same price, the one with more trades left
	 */
	private static Seller getCheapestSeller(List<Seller> sellers) {
		return sellers.stream().min(Comparator.comparingDouble((Seller seller) -> (double) seller.offer().costACount / seller.offer().resultCount)
				.thenComparing(Comparator.comparingInt(GuiVillagerBrowser::getTradesLeft).reversed())).orElseThrow();
	}

	private int getTotalEmeralds() {
		int total = 0;

		for (List<Seller> sellers : this.groups.get(Group.EMERALDS).values()) {
			total += sum(sellers, GuiVillagerBrowser::getEmeralds);
		}

		return total;
	}

	/**
	 * Stacks of 64 and the rest, like Litematica's material list: "2 x 64 + 1", null for less than a stack
	 */
	@Nullable
	private static String formatStacks(int count) {
		int stacks = count / 64;
		int rest = count % 64;

		if (stacks == 0) {
			return null;
		}

		return rest > 0 ? stacks + " x 64 + " + rest : stacks + " x 64";
	}

	// ---- Layout ----

	private int getColumns() {
		return Math.max(1, (this.getScreenWidth() - 20) / CELL);
	}

	/**
	 * The cells of the offers grid: books, gear, then items, with one empty cell between the groups
	 */
	private List<GridCell> getGridCells() {
		List<GridCell> cells = new ArrayList<>();

		for (Group group : List.of(Group.BOOKS, Group.GEAR, Group.ITEMS)) {
			Map<OfferKey, List<Seller>> offers = this.groups.get(group);

			if (offers.isEmpty()) {
				continue;
			}

			if (!cells.isEmpty()) {
				cells.add(GridCell.GAP);
			}

			offers.forEach((key, sellers) -> cells.add(new GridCell(CellKind.OFFER, key, sellers)));
		}

		return cells;
	}

	private int getGridLines() {
		return (this.getGridCells().size() + this.getColumns() - 1) / this.getColumns();
	}

	/**
	 * The emerald row below the grid: the total, a separator, then the emerald trades, each as wide as its icon
	 * and "= N" text. When a line is full, the trades continue below the first one.
	 */
	private List<EmeraldPlace> getEmeraldPlaces() {
		List<EmeraldPlace> places = new ArrayList<>();
		Map<OfferKey, List<Seller>> emeraldTrades = this.groups.get(Group.EMERALDS);

		if (emeraldTrades.isEmpty()) {
			return places;
		}

		int x = 10;
		int totalWidth = CELL + this.getStringWidth(String.valueOf(this.getTotalEmeralds())) + 4;
		places.add(new EmeraldPlace(CellKind.EMERALD_TOTAL, x, 0, totalWidth, null, List.of()));
		x += totalWidth;
		places.add(new EmeraldPlace(CellKind.SEPARATOR, x, 0, 8, null, List.of()));
		x += 8;

		int lineStart = x;
		int right = this.getScreenWidth() - 10;
		int line = 0;

		for (Map.Entry<OfferKey, List<Seller>> entry : emeraldTrades.entrySet()) {
			int width = CELL + this.getStringWidth(getItemTotalText(entry.getValue())) + 8;

			if (x + width > right && x > lineStart) {
				line++;
				x = lineStart;
			}

			places.add(new EmeraldPlace(CellKind.OFFER, x, line, width, entry.getKey(), entry.getValue()));
			x += width;
		}

		return places;
	}

	/**
	 * "= 528": all items of this kind the villagers can still take
	 */
	private static String getItemTotalText(List<Seller> sellers) {
		return "= " + sum(sellers, GuiVillagerBrowser::getItemsNeeded);
	}

	private int getOffersHeight() {
		List<EmeraldPlace> places = this.getEmeraldPlaces();
		int emeraldLines = places.isEmpty() ? 0 : places.getLast().line() + 1;
		return (this.getGridLines() + emeraldLines) * CELL;
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

			// Between the coordinates and the delete button: chat, then waypoint
			int waypointX = deleteX - 4 - Waypoints.getButtonWidth();
			this.addButton(Hover.Button.of(waypointX - 4 - this.getChatButtonWidth(), y, this.getChatButtonWidth(), 16,
							StringUtils.translate(KEY + "chat"), KEY + "chat.hover"),
					(btn, mouseButton) -> Waypoints.onChatButton(villager));

			this.addButton(Hover.Button.of(waypointX, y, Waypoints.getButtonWidth(), 16,
							Waypoints.getButtonLabel(villager), KEY + "waypoint.hover"),
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
		this.addButton(Hover.Button.of(this.getScreenWidth() - 12 - disableAllWidth, y, disableAllWidth, 20, disableAll,
						KEY + "waypoints_off.hover"),
				(btn, mouseButton) -> {
					Waypoints.onDisableAll(this);
					this.initGui();
				});
	}

	private int getChatButtonWidth() {
		return this.getStringWidth(StringUtils.translate(KEY + "chat")) + 10;
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
				String click = box.offer() == null ? null
						: StringUtils.translate(box.offer().equals(selected) ? KEY + "click.show_all" : KEY + "click.filter");
				RenderUtils.drawHoverText(ctx, mouseX, mouseY, Hover.lines(box.tooltip(), click));
				break;
			}
		}
	}

	/**
	 * The books, gear and items as icons with the number of villagers selling them, then the emerald row
	 */
	private void drawOffers(GuiContext ctx) {
		int cols = this.getColumns();
		int i = 0;

		for (GridCell cell : this.getGridCells()) {
			int x = 10 + (i % cols) * CELL;
			int cellY = this.offersY + (i / cols) * CELL;
			i++;

			if (cell.kind() == CellKind.OFFER) {
				this.drawOffer(ctx, x, cellY, cell.key(), cell.sellers());
			}
		}

		int emeraldY = this.offersY + this.getGridLines() * CELL;

		for (EmeraldPlace place : this.getEmeraldPlaces()) {
			int y = emeraldY + place.line() * CELL;

			switch (place.kind()) {
				case EMERALD_TOTAL -> this.drawEmeraldTotal(ctx, place.x(), y, place.width());
				case SEPARATOR -> RenderUtils.drawRect(ctx, place.x() + 3, y + 1, 1, CELL - 2, 0xFF707070);
				case OFFER -> this.drawEmeraldTrade(ctx, place.x(), y, place.width(), place.key(), place.sellers());
				default -> {
				}
			}
		}
	}

	private void drawOffer(GuiContext ctx, int x, int cellY, OfferKey key, List<Seller> sellers) {
		if (key.equals(selected)) {
			RenderUtils.drawRect(ctx, x, cellY, CELL, CELL, 0x8055FF55);
		}

		ItemStack stack = key.createStack();
		ctx.renderItem(stack, x + 1, cellY + 1);

		// The number of villagers selling it small top left; other items also the items per trade (stack size)
		// of the cheapest one
		this.drawSmallNumber(ctx, sellers.size(), x, cellY, false, AQUA);

		List<String> tooltip = new ArrayList<>();
		List<String> details = new ArrayList<>();
		tooltip.add(key.getName());

		if (key.group() == Group.ITEMS) {
			int perTrade = getCheapestSeller(sellers).offer().resultCount;
			this.drawCount(ctx, stack, perTrade, x + 1, cellY + 1);

			details.add(StringUtils.translate(KEY + "legend.villagers_sell", sellers.size()));
			details.add(StringUtils.translate(KEY + "legend.per_trade", perTrade, key.getName()));
		}
		else {
			details.add(StringUtils.translate(KEY + "legend.villagers_sell", sellers.size()));
		}

		// The nearest sellers; the others are shown by clicking (the sellers are sorted by distance)
		for (Seller seller : sellers.subList(0, Math.min(MAX_SELLERS, sellers.size()))) {
			tooltip.add(StringUtils.translate(KEY + "seller", seller.villager().getDisplayName(),
					GuiTradingLines.formatCost(seller.offer()), Hover.value(seller.villager().getLocationText())));
		}

		if (sellers.size() > MAX_SELLERS) {
			tooltip.add(GuiBase.TXT_GRAY + StringUtils.translate(KEY + "more_sellers", sellers.size() - MAX_SELLERS));
		}

		tooltip.add(Hover.MORE);
		tooltip.addAll(details);
		this.hitBoxes.add(new HitBox(x, cellY, CELL, CELL, tooltip, key));
	}

	/**
	 * An item the villagers buy for emeralds: villagers buying it (top left), trades left (top right) and items per trade
	 * (stack size) of the cheapest one, then "= " all items they can still take. The tooltip explains the numbers.
	 */
	private void drawEmeraldTrade(GuiContext ctx, int x, int y, int width, OfferKey key, List<Seller> sellers) {
		Seller cheapest = getCheapest(sellers);
		int tradesLeft = getTradesLeft(cheapest);
		int perTrade = cheapest.offer().costACount;
		int total = sum(sellers, GuiVillagerBrowser::getItemsNeeded);

		if (key.equals(selected)) {
			RenderUtils.drawRect(ctx, x, y, width - 6, CELL, 0x8055FF55);
		}

		ItemStack stack = key.createStack();
		ctx.renderItem(stack, x + 1, y + 1);
		this.drawCount(ctx, stack, perTrade, x + 1, y + 1);
		this.drawSmallNumber(ctx, sellers.size(), x, y, false, AQUA);
		this.drawSmallNumber(ctx, tradesLeft, x + CELL, y, true, YELLOW);
		this.drawStringWithShadow(ctx, getItemTotalText(sellers), x + CELL + 1, y + 5, WHITE);

		String name = key.getName();
		List<String> tooltip = new ArrayList<>();
		tooltip.add(StringUtils.translate(KEY + "buys", name));
		tooltip.add(StringUtils.translate(KEY + "summary", Hover.value(sellers.size()), Hover.value(tradesLeft), Hover.value(perTrade)));
		tooltip.add(StringUtils.translate(KEY + "legend.total", Hover.value(total), name));
		tooltip.add(Hover.MORE);
		tooltip.add(StringUtils.translate(KEY + "legend.villagers", sellers.size()));
		tooltip.add(StringUtils.translate(KEY + "legend.trades", tradesLeft));
		tooltip.add(StringUtils.translate(KEY + "legend.per_trade", perTrade, name));
		this.hitBoxes.add(new HitBox(x, y, width - 6, CELL, tooltip, key));
	}

	/**
	 * An emerald with all emeralds the emerald trades can still give next to it.
	 * The tooltip shows them as a number, in stacks and in shulker boxes.
	 */
	private void drawEmeraldTotal(GuiContext ctx, int x, int y, int width) {
		int total = this.getTotalEmeralds();
		ctx.renderItem(EMERALD, x + 1, y + 1);
		this.drawStringWithShadow(ctx, String.valueOf(total), x + CELL + 1, y + 5, WHITE);

		List<String> tooltip = new ArrayList<>();
		tooltip.add(StringUtils.translate(KEY + "emeralds.total", Hover.value(total)));
		String stacks = formatStacks(total);

		if (stacks != null) {
			tooltip.add(stacks);
		}

		tooltip.add(StringUtils.translate(KEY + "emeralds.shulker_boxes", String.format(Locale.ROOT, "%.2f", (double) total / SHULKER_BOX)));
		this.hitBoxes.add(new HitBox(x, y, width - 4, CELL, tooltip, null));
	}

	/**
	 * A small number in a top corner of an icon, like a superscript
	 * @param alignRight x is the right end of the number instead of the left
	 */
	private void drawSmallNumber(GuiContext ctx, int number, int x, int y, boolean alignRight, int color) {
		String text = String.valueOf(number);

		if (alignRight) {
			x -= Math.round(this.getStringWidth(text) * SMALL);
		}

		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.pose().scale(SMALL, SMALL);
		this.drawStringWithShadow(ctx, text, 0, 0, color);
		ctx.pose().popMatrix();
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
			List<String> info = List.of(villager.getDisplayName(), Hover.value(villager.getCoordinates()) + " (" + villager.dimension + ")",
					GuiBase.TXT_GRAY + StringUtils.translate(KEY + "info", villager.offers.size(), formatAge(villager.lastSeen)));

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

			this.drawStringWithShadow(ctx, getLevelText(villager), x, iconY + 4, GOLD);
			x += 22;

			int distance = villager.getDistance();
			this.drawStringWithShadow(ctx, distance >= 0 ? distance + " m" : "-", x, iconY + 4, 0xFF55FFFF);
			x += 42;

			// The coordinates at the end of the row, left of the chat, waypoint and delete buttons
			String coordinates = villager.getCoordinates();
			int coordinatesX = deleteX - 4 - Waypoints.getButtonWidth() - 4 - this.getChatButtonWidth() - 6 - this.getStringWidth(coordinates);
			this.drawStringWithShadow(ctx, coordinates, coordinatesX, iconY + 4, 0xFF55FFFF);
			this.hitBoxes.add(new HitBox(coordinatesX, y, this.getStringWidth(coordinates), ROW_HEIGHT, info, null));

			for (RememberedOffer offer : villager.offers) {
				if (x + 16 > coordinatesX - 6) {
					break;
				}

				OfferKey key = this.offerKeys.get(offer);
				ItemStack stack = key.createStack();
				ctx.renderItem(stack, x, iconY);

				if (key.buys()) {
					drawBuysMarker(ctx, x, iconY);
				}

				this.drawTradesLeft(ctx, villager, offer, key, stack, x, iconY);

				if (key.equals(selected)) {
					RenderUtils.drawOutline(ctx, x - 1, iconY - 1, 18, 18, 0xFF55FF55);
				}

				this.hitBoxes.add(new HitBox(x, iconY, 16, 16, this.getTradeTooltip(villager, offer), null));
				x += 18;
			}
		}
	}

	/**
	 * How many more times a trade can be done before the villager restocks.
	 * <ul>
	 * <li>Emerald trades: top right, and the items per trade as the stack size</li>
	 * <li>Other items: top right, and the items per trade as the stack size</li>
	 * <li>Books and gear: top right only</li>
	 * </ul>
	 * A villager that may have restocked shows the full uses in yellow; a sold out trade is darkened, without a number.
	 */
	private void drawTradesLeft(GuiContext ctx, RememberedVillager villager, RememberedOffer offer, OfferKey key, ItemStack stack, int x, int y) {
		boolean restocked = isMaybeRestocked(villager, offer);
		int tradesLeft = restocked ? offer.maxUses : offer.getUsesLeft();
		int color = restocked ? YELLOW : WHITE;

		if (key.buys()) {
			this.drawCount(ctx, stack, offer.costACount, x, y);
		}
		else if (key.group() == Group.ITEMS) {
			this.drawCount(ctx, stack, offer.resultCount, x, y);
		}

		if (tradesLeft <= 0) {
			// Sold out until the villager restocks
			RenderUtils.drawRect(ctx, x, y, 16, 16, 0x90000000);
		}
		else {
			this.drawSmallNumber(ctx, tradesLeft, x + 17, y - 1, true, color);
		}
	}

	/**
	 * A number drawn like a stack size; unlike a stack size also 0 and 1
	 */
	private void drawCount(GuiContext ctx, ItemStack stack, int count, int x, int y) {
		if (count > 1) {
			ctx.renderItemDecorations(this.font, stack.copyWithCount(count), x, y);
		}
		else {
			String text = String.valueOf(count);
			this.drawStringWithShadow(ctx, text, x + 17 - this.getStringWidth(text), y + 9, 0xFFFFFFFF);
		}
	}

	/**
	 * A half size emerald in the top left corner of an icon: the villager buys this item for emeralds
	 */
	private static void drawBuysMarker(GuiContext ctx, int x, int y) {
		ctx.pose().pushMatrix();
		ctx.pose().translate(x - 1, y - 1);
		ctx.pose().scale(0.5f, 0.5f);
		ctx.renderItem(EMERALD, 0, 0);
		ctx.pose().popMatrix();
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
