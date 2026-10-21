package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.ShoppingList;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.ShoppingListManager;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.MissingEnchantment;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.Visit;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetup;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetupManager;
import io.github.mgthorn.shoppingmaterialist.client.trading.VillagerMemory;

/**
 * The trading list: pick a setup of items with enchantments on the left, and see on the right
 * what to bring and which remembered villagers to visit. The sections and villagers on the right can be expanded
 * and collapsed; each item's trades and anvil steps are in {@link GuiTradingItemDetails}.
 */
public class GuiTradingList extends GuiTradingLines {
	private static final int CONTENT_Y = 50;
	private static final int ROW_HEIGHT = 22;
	private static final Identifier SETTINGS_ICON = Identifier.fromNamespaceAndPath(Reference.MOD_ID, "textures/gui/settings.png");
	private static final Identifier DETAILS_ICON = Identifier.fromNamespaceAndPath(Reference.MOD_ID, "textures/gui/details.png");
	// The item rows on the left: checkbox, item icon, then the buttons from the right
	private static final int CHECKBOX_X = 15;
	private static final int CHECKBOX_SIZE = 9;
	private static final int PIECE_ICON_X = 30;

	// Expanded villagers (UUID) and collapsed sections. Static, so it is remembered while the game runs.
	private static final Set<Object> EXPANDED = new HashSet<>();
	private static final Set<Section> COLLAPSED = new HashSet<>();

	/**
	 * The expandable sections of the result panel. Unlike villagers they start expanded.
	 */
	private enum Section {
		NEEDED("needed"),
		VISITS("visits"),
		MISSING("missing_enchantments");

		private final String translationKey;

		Section(String name) {
			this.translationKey = KEY + name;
		}
	}

	@Nullable private TradingCalculator.Result result;
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
	 * Just wide enough for a row: checkbox, item icon, then the source, details, enchantments and remove buttons
	 */
	private int getPiecesWidth() {
		int iconRight = PIECE_ICON_X + 16 + 4;
		return iconRight + this.getSourceButtonWidth() + 2 + 20 + 2 + 20 + 2 + 18 + 2 - 10;
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

			int sourceWidth = this.getSourceButtonWidth();
			x -= sourceWidth + 2;
			this.addButton(new ButtonGeneric(x, y, sourceWidth, 20, piece.getSource().getDisplayName(),
							StringUtils.translate(KEY + "source.hover")),
					(btn, mouseButton) -> this.changeSetup(() -> piece.setSource(piece.getSource().next())));

		}
	}

	private int getSourceButtonWidth() {
		int width = 0;

		for (SetupPiece.Source source : SetupPiece.Source.values()) {
			width = Math.max(width, this.getStringWidth(source.getDisplayName()) + 10);
		}

		return width;
	}

	private void createBottomButtons() {
		int x = 10;
		int y = this.getScreenHeight() - 26;
		boolean inWorld = isInWorld();

		x += this.addButton(x, y, StringUtils.translate(KEY + "add_piece"), null,
				() -> GuiBase.openGui(new GuiTradingItemPicker(this.getSetup(), this)));

		ButtonGeneric export = new ButtonGeneric(x, y, this.getStringWidth(StringUtils.translate(KEY + "export")) + 10, 20,
				StringUtils.translate(KEY + "export"), StringUtils.translate(KEY + "export.hover"));
		export.setEnabled(inWorld);
		this.addButton(export, (btn, mouseButton) -> this.export());
		x += export.getWidth() + 2;

		ButtonGeneric chat = new ButtonGeneric(x, y, this.getStringWidth(StringUtils.translate(KEY + "chat")) + 10, 20,
				StringUtils.translate(KEY + "chat"), StringUtils.translate(KEY + "chat.hover"));
		chat.setEnabled(inWorld && this.result != null && !this.result.visits.isEmpty());
		this.addButton(chat, (btn, mouseButton) -> this.sendCoordinatesToChat());
		x += chat.getWidth() + 2;

		String villagers = StringUtils.translate(KEY + "villagers", VillagerMemory.getInstance().getVillagers().size());
		x += this.addButton(x, y, villagers, null, () -> GuiBase.openGui(new GuiVillagerBrowser(this)));

		this.addButton(x, y, StringUtils.translate(KEY + "done"), null, () -> GuiBase.openGui(this.getParent()));
	}

	/**
	 * Adds a button with its width fitted to the label
	 * @return the width used, including the gap to the next button
	 */
	private int addButton(int x, int y, String label, @Nullable String hoverKey, Runnable action) {
		int width = this.getStringWidth(label) + 10;
		ButtonGeneric button = hoverKey != null
				? new ButtonGeneric(x, y, width, 20, label, StringUtils.translate(hoverKey))
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

	// ---- Actions ----

	/**
	 * Puts everything to bring into the shopping list "Trading: setup name" and opens it in Litematica's material list
	 */
	private void export() {
		if (this.result == null || this.result.inputs.isEmpty()) {
			this.addMessage(MessageType.WARNING, KEY + "export.empty");
			return;
		}

		String name = StringUtils.translate(KEY + "export.list_name", this.getSetup().getName());
		ShoppingList list = ShoppingListManager.getInstance().getOrCreate(name);

		if (list == null) {
			return;
		}

		list.setItems(this.result.inputs);
		GuiShoppingLists.openMaterialList(list, this);
	}

	/**
	 * Writes the villagers to visit into the own chat (only visible to the player); clicking a line copies the coordinates
	 */
	private void sendCoordinatesToChat() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || this.result == null) {
			return;
		}

		mc.player.sendSystemMessage(Component.literal(StringUtils.translate(KEY + "chat.header", this.getSetup().getName()))
				.withStyle(style -> style.withColor(0xFFAA00)));

		for (Visit visit : this.result.getVisitsByDistance()) {
			String coordinates = visit.villager.getCoordinates();
			Style style = Style.EMPTY
					.withClickEvent(new ClickEvent.CopyToClipboard(coordinates))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(StringUtils.translate(KEY + "chat.copy"))));

			MutableComponent line = Component.literal(" " + visit.villager.getDisplayName() + ": ").withStyle(s -> s.withColor(0xFFFFFF))
					.append(Component.literal(visit.villager.getLocationText()).withStyle(style.withColor(0x55FFFF).withUnderlined(true)))
					.append(Component.literal(" - " + formatPurchases(visit.purchases)).withStyle(s -> s.withColor(0xA0A0A0)));
			mc.player.sendSystemMessage(line);
		}
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
	 * Turns the result into lines, showing the children of expanded sections and villagers only
	 */
	private void buildLines() {
		this.lines.clear();
		TradingCalculator.Result result = this.result;

		if (result == null) {
			this.addText(StringUtils.translate(KEY + "not_in_world"), YELLOW, 0);
		}
		else {
			this.addNeededLines(result);
			this.addVisitLines(result);
			this.addMissingLines(result);
		}

		this.clampLineScroll();
	}

	private void addNeededLines(TradingCalculator.Result result) {
		if (result.missingTrades > 0) {
			this.addText(StringUtils.translate(KEY + "summary.missing_trades", result.missingTrades), RED, 0);
		}

		if (result.tooExpensive) {
			this.addText(StringUtils.translate(KEY + "summary.too_expensive"), RED, 0);
		}

		if (!this.addSection(Section.NEEDED)) {
			return;
		}

		// The anvil levels always come first
		this.addLine(1, new Segs().levels(result.totalLevels, result.tooExpensive), StringUtils.translate(KEY + "levels.hover"));

		if (result.emeraldsKnown > 0 || result.estimateMax > 0) {
			String hover = result.estimateMax > 0
					? StringUtils.translate(KEY + "emeralds.hover_estimate", result.emeraldsKnown, result.estimateMin, result.estimateMax)
					: StringUtils.translate(KEY + "emeralds.hover", result.emeraldsKnown);
			Segs segs = new Segs().item(Items.EMERALD, hover)
					.text(" " + formatEmeralds(result.emeraldsKnown, result.estimateMin, result.estimateMax), result.estimateMax > 0 ? YELLOW : WHITE);
			this.addLine(1, segs, hover);
		}

		for (Map.Entry<Item, Integer> entry : result.inputs.entrySet()) {
			// Emeralds are shown above, with the estimate separated
			if (entry.getKey() != Items.EMERALD) {
				this.addLine(1, new Segs().item(entry.getKey()).text(" " + entry.getValue(), WHITE), null);
			}
		}
	}

	private void addVisitLines(TradingCalculator.Result result) {
		if (result.visits.isEmpty()) {
			this.addText(StringUtils.translate(KEY + "no_visits"), GRAY, 0);
		}
		else {
			if (!this.addSection(Section.VISITS)) {
				return;
			}

			for (Visit visit : result.getVisitsByDistance()) {
				UUID uuid = visit.villager.uuid;
				String text = visit.villager.getDisplayName() + ": " + visit.villager.getLocationText();
				this.lines.add(new Line(1, uuid, new Segs().text(text, AQUA).list, null));

				if (isExpanded(uuid)) {
					this.addText(formatPurchases(visit.purchases), GRAY, 3);
				}
			}
		}
	}

	/**
	 * All enchantments no remembered villager sells, most used first, as book icons only, wrapped to the panel width.
	 * Clicking one excludes it from all calculations (shown with a red "no" sign) or includes it again.
	 */
	private void addMissingLines(TradingCalculator.Result result) {
		if (result.missingEnchantments.isEmpty() || !this.addSection(Section.MISSING)) {
			return;
		}

		int perLine = Math.max(1, (this.getLinesWidth() - 8) / 16);
		Segs segs = new Segs();

		// Most used enchantments first
		List<MissingEnchantment> sorted = new ArrayList<>(result.missingEnchantments.values());
		sorted.sort((a, b) -> Enchants.compareByUsage(a.id(), a.level(), b.id(), b.level()));

		for (MissingEnchantment missing : sorted) {
			if (segs.list.size() == perLine) {
				this.addLine(1, segs, null);
				segs = new Segs();
			}

			segs.missing(missing);
		}

		this.addLine(1, segs, null);
	}

	/**
	 * Adds an expandable section header
	 * @return true if the section is expanded, so its lines should be added
	 */
	private boolean addSection(Section section) {
		this.lines.add(new Line(0, section, new Segs().text(StringUtils.translate(section.translationKey), GOLD).list, null));
		return isExpanded(section);
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
			RenderUtils.drawOutline(ctx, CHECKBOX_X, boxY, CHECKBOX_SIZE, CHECKBOX_SIZE, piece.isEnabled() ? 0xFFC0C0C0 : 0xFF707070);

			if (piece.isEnabled()) {
				drawCheckMark(ctx, CHECKBOX_X, boxY);
			}

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
	 * A small green check mark inside the checkbox
	 */
	private static void drawCheckMark(GuiContext ctx, int x, int y) {
		int color = 0xFF55FF55;
		int[][] pixels = { { 2, 4 }, { 3, 5 }, { 4, 6 }, { 5, 5 }, { 6, 4 }, { 7, 3 }, { 7, 2 }, { 2, 5 }, { 3, 6 }, { 4, 7 }, { 5, 6 }, { 6, 5 } };

		for (int[] p : pixels) {
			RenderUtils.drawRect(ctx, x + p[0] - 1, y + p[1] - 1, 1, 1, color);
		}
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
