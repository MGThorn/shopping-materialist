package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.compat.Waypoints;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedVillager;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.MissingEnchantment;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.Visit;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetup;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetupManager;
import io.github.mgthorn.shoppingmaterialist.client.trading.VillagerMemory;

/**
 * The trading list: pick a setup of items with enchantments on the left, and see on the right, as cards,
 * what to bring, which remembered villagers to visit and which enchantments nobody sells. The cards and villagers
 * can be expanded and collapsed; each item's trades and anvil steps are in {@link GuiTradingItemDetails}.
 */
public class GuiTradingList extends GuiTradingLines {
	private static final int CONTENT_Y = 50;
	private static final int ROW_HEIGHT = 22;
	private static final Identifier SETTINGS_ICON = Identifier.fromNamespaceAndPath(Reference.MOD_ID, "textures/gui/settings.png");
	private static final Identifier DETAILS_ICON = Identifier.fromNamespaceAndPath(Reference.MOD_ID, "textures/gui/details.png");
	// The item rows on the left: checkbox, item icon, then the buttons from the right
	private static final int CHECKBOX_X = 15;
	private static final int PIECE_ICON_X = 30;
	// The summary cards: a title bar, then a lighter body
	private static final int CARD_HEADER_BACKGROUND = 0x60806040;
	private static final int CARD_BACKGROUND = 0x20FFFFFF;
	private static final int ICON_GAP = 2;

	// Expanded villagers (UUID) and collapsed sections. Static, so it is remembered while the game runs.
	private static final Set<Object> EXPANDED = new HashSet<>();
	private static final Set<Section> COLLAPSED = new HashSet<>();

	/**
	 * The cards of the result panel. Unlike villagers they start expanded.
	 */
	private enum Section {
		NEEDED,
		VISITS,
		MISSING
	}

	@Nullable private TradingCalculator.Result result;
	// The background color of the card lines, by line index
	private final Map<Integer, Integer> backgrounds = new HashMap<>();
	private int pieceScroll;

	public GuiTradingList(@Nullable Screen parent) {
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.trading_list");
		this.setParent(parent);
	}

	private TradingSetup getSetup() {
		return TradingSetupManager.getInstance().getSelected();
	}

	// ---- Layout ----

	/**
	 * Just wide enough for a row: checkbox, item icon, then the details, enchantments and remove buttons.
	 * Where the items come from is set in the export screen.
	 */
	private int getPiecesWidth() {
		int iconRight = PIECE_ICON_X + 16 + 4;
		return iconRight + 20 + 2 + 20 + 2 + 18 + 2 - 10;
	}

	@Override
	protected int getLinesX() {
		return 10 + this.getPiecesWidth() + 10;
	}

	@Override
	protected int getLinesY() {
		return CONTENT_Y;
	}

	@Override
	protected int getLinesWidth() {
		return this.getScreenWidth() - this.getLinesX() - 10;
	}

	@Override
	protected int getLinesHeight() {
		return this.getContentHeight();
	}

	private int getContentHeight() {
		return this.getScreenHeight() - CONTENT_Y - 32;
	}

	private int getVisiblePieces() {
		return Math.max(1, this.getContentHeight() / ROW_HEIGHT);
	}

	// ---- Widgets ----

	@Override
	public void initGui() {
		super.initGui();

		this.recalculate();
		this.createSetupButtons();
		this.createPieceButtons();
		this.createBottomButtons();
	}

	private void createSetupButtons() {
		int x = 10;
		int y = 26;
		TradingSetupManager manager = TradingSetupManager.getInstance();

		x += this.addButton(x, y, "<", KEY + "previous_setup", () -> this.changeSetup(() -> manager.select(-1)));
		x += this.getStringWidth(this.getSetup().getName()) + 12;
		x += this.addButton(x, y, ">", KEY + "next_setup", () -> this.changeSetup(() -> manager.select(1)));
		x += 6;
		x += this.addButton(x, y, StringUtils.translate(KEY + "new_setup"), KEY + "new_setup.hover", this::openNewSetup);
		x += this.addButton(x, y, StringUtils.translate(KEY + "rename_setup"), null, this::openRenameSetup);
		this.addButton(x, y, StringUtils.translate(KEY + "delete_setup"), null, this::confirmDeleteSetup);
	}

	private void createPieceButtons() {
		List<SetupPiece> pieces = this.getSetup().getPieces();
		this.pieceScroll = Math.max(0, Math.min(this.pieceScroll, pieces.size() - this.getVisiblePieces()));
		int end = Math.min(pieces.size(), this.pieceScroll + this.getVisiblePieces());
		int right = 10 + this.getPiecesWidth();

		for (int i = this.pieceScroll; i < end; i++) {
			SetupPiece piece = pieces.get(i);
			int y = CONTENT_Y + (i - this.pieceScroll) * ROW_HEIGHT + 1;
			int x = right;

			x -= 20;
			this.addButton(new ButtonGeneric(x, y, 18, 20, "x", StringUtils.translate(KEY + "remove_piece")),
					(btn, mouseButton) -> this.changeSetup(() -> pieces.remove(piece)));

			// The enchantments button only shows a gear icon (drawn in drawContents)
			x -= 20 + 2;
			this.addButton(new ButtonGeneric(x, y, 20, 20, "", StringUtils.translate(KEY + "edit_piece")),
					(btn, mouseButton) -> GuiBase.openGui(new GuiTradingPieceEditor(piece, this)));

			// The details button only shows a magnifier icon; items left out of the calculation have no details
			x -= 20 + 2;
			ButtonGeneric details = new ButtonGeneric(x, y, 20, 20, "", StringUtils.translate(KEY + "details_piece"));
			details.setEnabled(piece.isEnabled());
			this.addButton(details, (btn, mouseButton) -> GuiBase.openGui(new GuiTradingItemDetails(piece, this)));
		}
	}

	private void createBottomButtons() {
		int y = this.getScreenHeight() - 26;
		boolean inWorld = isInWorld();
		List<RememberedVillager> visits = this.getVillagersToVisit();

		this.addButton(10, y, StringUtils.translate(KEY + "add_piece"), null,
				() -> GuiBase.openGui(new GuiTradingItemPicker(this.getSetup(), this)));

		// From the right: villager browser, waypoints, export
		int x = this.getScreenWidth() - 10;
		String villagers = StringUtils.translate(KEY + "villagers", VillagerMemory.getInstance().getVillagers().size());
		x -= this.getStringWidth(villagers) + 10;
		this.addButton(x, y, villagers, null, () -> GuiBase.openGui(new GuiVillagerBrowser(this)));

		String waypointsOn = StringUtils.translate(KEY + "waypoints.on");
		String waypointsOff = StringUtils.translate(KEY + "waypoints.off");
		int waypointsWidth = Math.max(this.getStringWidth(waypointsOn), this.getStringWidth(waypointsOff)) + 10;
		x -= waypointsWidth + 2;
		ButtonGeneric waypoints = Hover.Button.of(x, y, waypointsWidth, 20,
				Waypoints.areAllShown(visits) ? waypointsOn : waypointsOff, KEY + "waypoints.hover");
		waypoints.setEnabled(!visits.isEmpty());
		this.addButton(waypoints, (btn, mouseButton) -> {
			Waypoints.onToggleAll(visits, this);
			// Shows the new state on the button
			this.initGui();
		});

		String exportLabel = StringUtils.translate(KEY + "export");
		x -= this.getStringWidth(exportLabel) + 10 + 2;
		ButtonGeneric export = Hover.Button.of(x, y, this.getStringWidth(exportLabel) + 10, 20, exportLabel, KEY + "export.hover");
		export.setEnabled(inWorld);
		this.addButton(export, (btn, mouseButton) -> GuiBase.openGui(new GuiTradingExport(this)));
	}

	private List<RememberedVillager> getVillagersToVisit() {
		return this.result != null ? this.result.getVisitsByDistance().stream().map(visit -> visit.villager).toList() : List.of();
	}

	/**
	 * Adds a button with its width fitted to the label
	 * @return the width used, including the gap to the next button
	 */
	private int addButton(int x, int y, String label, @Nullable String hoverKey, Runnable action) {
		int width = this.getStringWidth(label) + 10;
		ButtonGeneric button = hoverKey != null
				? Hover.Button.of(x, y, width, 20, label, hoverKey)
				: new ButtonGeneric(x, y, width, 20, label);
		this.addButton(button, (btn, mouseButton) -> action.run());
		return width + 2;
	}

	/**
	 * Applies a change to the setup, saves it and rebuilds the screen
	 */
	private void changeSetup(Runnable change) {
		change.run();
		TradingSetupManager.getInstance().save();
		this.initGui();
	}

	private void openNewSetup() {
		IStringConsumerFeedback consumer = name -> {
			name = name.trim();

			if (name.isEmpty() || TradingSetupManager.getInstance().getByName(name) != null) {
				return false;
			}

			TradingSetupManager.getInstance().create(name);
			return true;
		};

		GuiBase.openGui(new GuiTextInput(64, KEY + "new_setup_title", "", this, consumer));
	}

	private void openRenameSetup() {
		TradingSetup setup = this.getSetup();
		IStringConsumerFeedback consumer = name -> {
			name = name.trim();
			TradingSetup existing = TradingSetupManager.getInstance().getByName(name);

			if (name.isEmpty() || (existing != null && existing != setup)) {
				return false;
			}

			setup.setName(name);
			TradingSetupManager.getInstance().save();
			return true;
		};

		GuiBase.openGui(new GuiTextInput(64, KEY + "rename_setup_title", setup.getName(), this, consumer));
	}

	private void confirmDeleteSetup() {
		IConfirmationListener listener = new IConfirmationListener() {
			@Override
			public boolean onActionConfirmed() {
				TradingSetupManager.getInstance().deleteSelected();
				return true;
			}

			@Override
			public boolean onActionCancelled() {
				return true;
			}
		};

		GuiBase.openGui(new GuiConfirmAction(320, KEY + "delete_setup_title", listener, this,
				KEY + "delete_setup_message", this.getSetup().getName()));
	}

	private static String formatPurchases(Map<String, Integer> purchases) {
		List<String> parts = new ArrayList<>();
		purchases.forEach((what, count) -> parts.add(count > 1 ? what + " x" + count : what));
		return String.join(", ", parts);
	}

	// ---- Result lines ----

	private void recalculate() {
		if (!isInWorld()) {
			this.result = null;
		}
		else {
			this.result = TradingCalculator.calculate(this.getSetup(), VillagerMemory.getInstance().getVillagers());
		}

		this.buildLines();
	}

	/**
	 * Turns the result into cards: total needed, villagers to visit, missing enchantments.
	 * A card's lines are only added if it is expanded; villager rows expand to their coordinates and purchases.
	 */
	private void buildLines() {
		this.lines.clear();
		this.backgrounds.clear();
		TradingCalculator.Result result = this.result;

		if (result == null) {
			this.addText(StringUtils.translate(KEY + "not_in_world"), YELLOW, 0);
		}
		else {
			this.addNeededCard(result);
			this.addSpacer();
			this.addVisitCard(result);

			if (!result.missingEnchantments.isEmpty()) {
				this.addSpacer();
				this.addMissingCard(result);
			}
		}

		this.clampLineScroll();
	}

	/**
	 * The warnings, the levels and emeralds, then everything else to bring as icons with their counts
	 */
	private void addNeededCard(TradingCalculator.Result result) {
		if (!this.addCardHeader(Section.NEEDED, StringUtils.translate(KEY + "needed"))) {
			return;
		}

		List<String> warnings = new ArrayList<>();
		List<String> warningTooltips = new ArrayList<>();

		if (result.missingTrades > 0) {
			warnings.add(StringUtils.translate(KEY + "summary.missing_trades", result.missingTrades));
			warningTooltips.add(StringUtils.translate(KEY + "summary.missing_trades.hover"));
		}

		if (result.tooExpensive) {
			warnings.add(StringUtils.translate(KEY + "summary.too_expensive.short"));
			warningTooltips.add(StringUtils.translate(KEY + "summary.too_expensive"));
		}

		if (!warnings.isEmpty()) {
			this.addCardLine(new Segs().text(String.join(" \u00b7 ", warnings), RED), String.join("\n", warningTooltips));
		}

		// The anvil levels always come first, then the emeralds with the estimate separated
		Segs first = new Segs().levels(result.totalLevels, result.tooExpensive);

		if (result.emeraldsKnown > 0 || result.estimateMax > 0) {
			String hover = result.estimateMax > 0
					? Hover.text(KEY + "emeralds.hover_estimate", result.emeraldsKnown, result.estimateMin, result.estimateMax)
					: StringUtils.translate(KEY + "emeralds.hover", result.emeraldsKnown);
			first.text("    ", WHITE).item(Items.EMERALD, hover)
					.text(" " + formatEmeralds(result.emeraldsKnown, result.estimateMin, result.estimateMax), result.estimateMax > 0 ? YELLOW : WHITE);
		}

		this.addCardLine(first, null);

		// Like inventory slots: the icons with their counts, wrapped to the panel width
		int perLine = Math.max(1, (this.getLinesWidth() - 16) / (16 + ICON_GAP));
		Segs segs = new Segs();
		int count = 0;

		for (Map.Entry<Item, Integer> entry : result.inputs.entrySet()) {
			if (entry.getKey() == Items.EMERALD) {
				continue;
			}

			if (count == perLine) {
				this.addCardLine(segs, null);
				segs = new Segs();
				count = 0;
			}

			ItemStack stack = new ItemStack(entry.getKey());
			segs.item(stack, stack.getHoverName().getString() + ": " + Hover.value(entry.getValue()), entry.getValue()).text("", WHITE, ICON_GAP);
			count++;
		}

		if (count > 0) {
			this.addCardLine(segs, null);
		}
	}

	/**
	 * One row per villager, nearest first: job block, level, distance, and what to buy there as icons
	 */
	private void addVisitCard(TradingCalculator.Result result) {
		String title = StringUtils.translate(KEY + "visits", result.visits.size());

		if (!this.addCardHeader(Section.VISITS, title)) {
			return;
		}

		if (result.visits.isEmpty()) {
			this.addCardText(StringUtils.translate(KEY + "no_visits"), GRAY, 1);
			return;
		}

		int levelWidth = this.getStringWidth("III") + 6;
		int distanceWidth = this.getStringWidth("9999 m") + 6;

		for (Visit visit : result.getVisitsByDistance()) {
			RememberedVillager villager = visit.villager;
			int distance = villager.getDistance();
			String distanceText = distance >= 0 ? distance + " m" : getDimensionName(villager.dimension);
			Segs segs = new Segs().item(GuiVillagerBrowser.getJobIcon(villager), villager.getDisplayName())
					.text(" " + GuiVillagerBrowser.getLevelText(villager), GOLD, levelWidth)
					.text(distanceText, AQUA, distanceWidth);

			visit.purchases.forEach((what, count) -> segs.item(visit.icons.get(what), count > 1 ? what + " x" + count : what, count));

			String tooltip = villager.getDisplayName() + "\n" + Hover.value(villager.getLocationText());
			this.lines.add(new Line(1, villager.uuid, segs.list, tooltip));
			this.backgrounds.put(this.lines.size() - 1, CARD_BACKGROUND);

			if (isExpanded(villager.uuid)) {
				this.addCardText(villager.getLocationText(), AQUA, 3);
				this.addCardText(formatPurchases(visit.purchases), GRAY, 3);
			}
		}
	}

	private static String getDimensionName(String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id != null ? id.getPath() : dimension;
	}

	/**
	 * All enchantments no remembered villager sells, most used first, as book icons only, wrapped to the panel width.
	 * Clicking one excludes it from all calculations (shown with a red "no" sign) or includes it again.
	 */
	private void addMissingCard(TradingCalculator.Result result) {
		String title = StringUtils.translate(KEY + "missing_enchantments", result.missingEnchantments.size());

		if (!this.addCardHeader(Section.MISSING, title)) {
			return;
		}

		int perLine = Math.max(1, (this.getLinesWidth() - 16) / 16);
		Segs segs = new Segs();

		// Most used enchantments first
		List<MissingEnchantment> sorted = new ArrayList<>(result.missingEnchantments.values());
		sorted.sort((a, b) -> Enchants.compareByUsage(a.id(), a.level(), b.id(), b.level()));

		for (MissingEnchantment missing : sorted) {
			if (segs.list.size() == perLine) {
				this.addCardLine(segs, null);
				segs = new Segs();
			}

			segs.missing(missing);
		}

		this.addCardLine(segs, null);
	}

	/**
	 * Adds the title bar of a card, which expands or collapses it
	 * @return true if the card is expanded, so its lines should be added
	 */
	private boolean addCardHeader(Section section, String title) {
		this.lines.add(new Line(0, section, new Segs().text(title, GOLD).list, null));
		this.backgrounds.put(this.lines.size() - 1, CARD_HEADER_BACKGROUND);
		return isExpanded(section);
	}

	private void addCardLine(Segs segs, @Nullable String tooltip) {
		this.addLine(1, segs, tooltip);
		this.backgrounds.put(this.lines.size() - 1, CARD_BACKGROUND);
	}

	private void addCardText(String text, int color, int indent) {
		this.addText(text, color, indent);
		this.backgrounds.put(this.lines.size() - 1, CARD_BACKGROUND);
	}

	/**
	 * A small gap between two cards
	 */
	private void addSpacer() {
		this.addText("", WHITE, 0);
	}

	@Override
	protected int getLineBackground(int index) {
		return this.backgrounds.getOrDefault(index, 0);
	}

	@Override
	protected boolean isExpanded(Object toggle) {
		return toggle instanceof Section section ? !COLLAPSED.contains(section) : EXPANDED.contains(toggle);
	}

	private static void toggle(Object toggle) {
		if (toggle instanceof Section section) {
			if (!COLLAPSED.remove(section)) {
				COLLAPSED.add(section);
			}
		}
		else if (!EXPANDED.remove(toggle)) {
			EXPANDED.add(toggle);
		}
	}

	// ---- Input ----

	@Override
	public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
		if (super.onMouseClicked(click, doubleClick)) {
			return true;
		}

		// The checkbox of an item row: include it in the calculation or not
		int row = this.getPieceRowAt(click.x(), click.y());

		if (row >= 0 && click.button() == ScanCodes.OFFSET_MOUSE_LEFT && click.x() >= CHECKBOX_X - 2 && click.x() < CHECKBOX_X + CHECKBOX_SIZE + 2) {
			SetupPiece piece = this.getSetup().getPieces().get(row);
			this.changeSetup(() -> piece.setEnabled(!piece.isEnabled()));
			return true;
		}

		// A missing enchantment: exclude it from the calculation, or include it again
		if (click.button() == ScanCodes.OFFSET_MOUSE_LEFT) {
			for (HitBox box : this.hitBoxes) {
				if (click.x() >= box.x() && click.x() < box.x() + box.width() && click.y() >= box.y() && click.y() < box.y() + box.height()) {
					TradingSetupManager.getInstance().toggleExcluded(box.missing().id());
					this.recalculate();
					return true;
				}
			}
		}

		int index = this.getLineAt(click.x(), click.y());

		// Clicking an item or villager expands or collapses it
		if (index >= 0 && this.lines.get(index).toggle() != null && click.button() == ScanCodes.OFFSET_MOUSE_LEFT) {
			toggle(this.lines.get(index).toggle());
			this.buildLines();
			return true;
		}

		return false;
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0 && mouseY >= CONTENT_Y) {
			int delta = -(int) Math.signum(verticalAmount);

			if (mouseX < this.getLinesX()) {
				this.pieceScroll += delta;
				this.initGui();
			}
			else {
				this.scrollLines(delta);
			}

			return true;
		}

		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ---- Rendering ----

	// The piece rows are drawn as part of the background, so that their buttons render on top of them
	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);

		// Setup name between the < and > buttons
		this.drawStringWithShadow(ctx, this.getSetup().getName(), 10 + 22 + 6, 32, GOLD);

		List<SetupPiece> pieces = this.getSetup().getPieces();
		int width = this.getPiecesWidth();

		if (pieces.isEmpty()) {
			this.drawString(ctx, this.trimToWidth(StringUtils.translate(KEY + "no_pieces"), width - 4), 12, CONTENT_Y + 6, GRAY);
		}

		int end = Math.min(pieces.size(), this.pieceScroll + this.getVisiblePieces());

		for (int i = this.pieceScroll; i < end; i++) {
			SetupPiece piece = pieces.get(i);
			int y = CONTENT_Y + (i - this.pieceScroll) * ROW_HEIGHT;
			RenderUtils.drawRect(ctx, 10, y, width, ROW_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);

			// Include in the calculation: a small outline, with a check mark when included
			int boxY = this.getCheckboxY(y);
			drawCheckbox(ctx, CHECKBOX_X, boxY, piece.isEnabled());

			// Only the icon; the name is the tooltip
			ctx.renderItem(piece.getStack(), PIECE_ICON_X, y + 3);

			if (!piece.isEnabled()) {
				RenderUtils.drawRect(ctx, PIECE_ICON_X, y + 3, 16, 16, 0x90202020);
			}
		}

		RenderUtils.drawRect(ctx, this.getLinesX() - 2, CONTENT_Y - 2, this.getLinesWidth() + 4, this.getContentHeight() + 4, 0xA0000000);
	}

	private int getCheckboxY(int rowY) {
		return rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
	}

	/**
	 * The visible item row at this position on the left, or -1
	 */
	private int getPieceRowAt(double mouseX, double mouseY) {
		if (mouseX < 10 || mouseX >= 10 + this.getPiecesWidth() || mouseY < CONTENT_Y) {
			return -1;
		}

		int index = this.pieceScroll + (int) ((mouseY - CONTENT_Y) / ROW_HEIGHT);
		int end = Math.min(this.getSetup().getPieces().size(), this.pieceScroll + this.getVisiblePieces());
		return index < end ? index : -1;
	}

	/**
	 * The magnifier icons on the details buttons, the gear icons on the enchantments buttons, and the tooltips of the item rows
	 */
	private void drawPieceRowOverlays(GuiContext ctx, int mouseX, int mouseY) {
		List<SetupPiece> pieces = this.getSetup().getPieces();
		int end = Math.min(pieces.size(), this.pieceScroll + this.getVisiblePieces());
		int gearX = 10 + this.getPiecesWidth() - 20 - 20 - 2 + 2;

		for (int i = this.pieceScroll; i < end; i++) {
			int y = CONTENT_Y + (i - this.pieceScroll) * ROW_HEIGHT + 1;
			ctx.blit(RenderPipelines.GUI_TEXTURED, SETTINGS_ICON, gearX, y + 2, 0, 0, 16, 16, 16, 16);
			ctx.blit(RenderPipelines.GUI_TEXTURED, DETAILS_ICON, gearX - 22, y + 2, 0, 0, 16, 16, 16, 16);
		}

		int row = this.getPieceRowAt(mouseX, mouseY);

		if (row < 0) {
			return;
		}

		SetupPiece piece = pieces.get(row);
		int y = CONTENT_Y + (row - this.pieceScroll) * ROW_HEIGHT;

		if (mouseX >= PIECE_ICON_X && mouseX < PIECE_ICON_X + 16) {
			RenderUtils.drawHoverText(ctx, mouseX, mouseY, List.of(piece.getDisplayName()));
		}
		else if (mouseX >= CHECKBOX_X - 2 && mouseX < CHECKBOX_X + CHECKBOX_SIZE + 2 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
			RenderUtils.drawHoverText(ctx, mouseX, mouseY, List.of(StringUtils.translate(KEY + "toggle_piece")));
		}
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		this.drawPieceRowOverlays(ctx, mouseX, mouseY);
		this.drawLines(ctx, mouseX, mouseY);
	}
}
