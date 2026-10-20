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
import net.minecraft.world.item.ItemStack;
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
import io.github.mgthorn.shoppingmaterialist.client.trading.AnvilOptimizer;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedOffer;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.BookResult;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.PieceResult;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.Visit;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetup;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetupManager;
import io.github.mgthorn.shoppingmaterialist.client.trading.VillagerMemory;

/**
 * The trading list: pick a setup of items with enchantments on the left, and see on the right
 * which remembered villagers to buy the books from, what to bring, and the cheapest anvil order.
 * The sections, items and villagers on the right can be expanded and collapsed.
 */
public class GuiTradingList extends GuiBase {
	private static final String KEY = Reference.MOD_ID + ".gui.trading.";
	private static final int CONTENT_Y = 50;
	private static final int ROW_HEIGHT = 22;
	private static final int TEXT_LINE_HEIGHT = 11;
	private static final int ICON_LINE_HEIGHT = 18;
	private static final Identifier XP_ORB_TEXTURE = Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png");
	private static final int XP_ORB_COLOR = 0xFFB0FF40;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int GOLD = 0xFFFFAA00;
	private static final int AQUA = 0xFF55FFFF;

	// Expanded items (SetupPiece) and villagers (UUID), and collapsed sections. Static, so it is remembered while the game runs.
	private static final Set<Object> EXPANDED = new HashSet<>();
	private static final Set<Section> COLLAPSED = new HashSet<>();

	/**
	 * The expandable sections of the result panel. Unlike items and villagers they start expanded.
	 */
	private enum Section {
		NEEDED("needed"),
		VISITS("visits"),
		ITEMS("item_order");

		private final String translationKey;

		Section(String name) {
			this.translationKey = KEY + name;
		}
	}

	/**
	 * One line of the result panel, made of text, item icons and experience orbs
	 * @param toggle the item or villager this line expands, null if it can't be expanded
	 * @param tooltip shown when the line is hovered (a hovered icon shows its own tooltip instead)
	 */
	private record Line(int indent, @Nullable Object toggle, List<Seg> segs, @Nullable String tooltip) {
		int height() {
			return this.segs.stream().anyMatch(seg -> seg.text() == null) ? ICON_LINE_HEIGHT : TEXT_LINE_HEIGHT;
		}

		String getPlainText() {
			StringBuilder text = new StringBuilder();
			this.segs.stream().filter(seg -> seg.text() != null).forEach(seg -> text.append(seg.text()));
			return text.toString();
		}
	}

	/**
	 * A piece of a line: text (when {@code text} is set), an item icon, or the experience orb
	 */
	private record Seg(@Nullable String text, int color, @Nullable ItemStack icon, boolean xp, @Nullable String tooltip) {
	}

	/**
	 * Builds the pieces of a line
	 */
	private static class Segs {
		final List<Seg> list = new ArrayList<>();

		Segs text(String text, int color) {
			this.list.add(new Seg(text, color, null, false, null));
			return this;
		}

		Segs item(Item item) {
			return this.item(new ItemStack(item));
		}

		Segs item(ItemStack stack) {
			return this.item(stack, stack.getHoverName().getString());
		}

		Segs item(Item item, String tooltip) {
			return this.item(new ItemStack(item), tooltip);
		}

		Segs item(ItemStack stack, String tooltip) {
			this.list.add(new Seg(null, 0, stack, false, tooltip));
			return this;
		}

		/**
		 * "[xp] 21 lvl"
		 */
		Segs levels(int levels, boolean tooExpensive) {
			this.list.add(new Seg(null, 0, null, true, StringUtils.translate(KEY + "levels.hover")));
			// At least two digits (05 lvl), so the rows line up
			return this.text(" " + StringUtils.translate(KEY + "levels", String.format("%02d", levels)), tooExpensive ? RED : WHITE);
		}

		/**
		 * "[emerald] 12 [book] 1 ", for {@code count} trades
		 */
		Segs cost(RememberedOffer offer, int count, int color) {
			this.item(offer.costA).text(" " + offer.costACount * count + " ", color);

			if (offer.hasCostB()) {
				this.item(offer.costB).text(" " + offer.costBCount * count + " ", color);
			}

			return this;
		}
	}

	private final List<Line> lines = new ArrayList<>();
	@Nullable private TradingCalculator.Result result;
	private int pieceScroll;
	private int lineScroll;

	public GuiTradingList(@Nullable Screen parent) {
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.trading_list");
		this.setParent(parent);
	}

	private TradingSetup getSetup() {
		return TradingSetupManager.getInstance().getSelected();
	}

	private static boolean isInWorld() {
		return Minecraft.getInstance().level != null;
	}

	// ---- Layout ----

	private int getPiecesWidth() {
		return Math.min(280, (int) ((this.getScreenWidth() - 30) * 0.45));
	}

	private int getLinesX() {
		return 10 + this.getPiecesWidth() + 10;
	}

	private int getLinesWidth() {
		return this.getScreenWidth() - this.getLinesX() - 10;
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

			String edit = StringUtils.translate(KEY + "edit_piece");
			int editWidth = this.getStringWidth(edit) + 10;
			x -= editWidth + 2;
			this.addButton(new ButtonGeneric(x, y, editWidth, 20, edit),
					(btn, mouseButton) -> GuiBase.openGui(new GuiTradingPieceEditor(piece, this)));

			int sourceWidth = this.getSourceButtonWidth();
			x -= sourceWidth + 2;
			this.addButton(new ButtonGeneric(x, y, sourceWidth, 20, piece.getSource().getDisplayName(),
							StringUtils.translate(KEY + "source.hover")),
					(btn, mouseButton) -> this.changeSetup(() -> piece.setSource(piece.getSource().next())));

			this.addButton(new ButtonGeneric(12, y, 20, 20, piece.isEnabled() ? "✔" : " ",
							StringUtils.translate(KEY + "toggle_piece")),
					(btn, mouseButton) -> this.changeSetup(() -> piece.setEnabled(!piece.isEnabled())));
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
	 * Turns the result into lines, showing the children of expanded items and villagers only
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

			if (!result.pieces.isEmpty() && this.addSection(Section.ITEMS)) {
				for (PieceResult piece : result.pieces) {
					this.addPieceLines(piece);
				}
			}
		}

		this.lineScroll = Math.max(0, Math.min(this.lineScroll, this.getMaxLineScroll()));
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

	private void addPieceLines(PieceResult pr) {
		// Only the icon (the name is in the tooltip), directly followed by the item's levels and emeralds
		SetupPiece piece = pr.piece;
		Segs header = new Segs().item(piece.getStack(), piece.getDisplayName()).text("  ", WHITE).levels(pr.totalLevels, pr.tooExpensive);

		if (pr.emeraldsKnown > 0 || pr.estimateMax > 0) {
			header.text("  ", WHITE).item(Items.EMERALD)
					.text(" " + formatEmeralds(pr.emeraldsKnown, pr.estimateMin, pr.estimateMax), pr.estimateMax > 0 ? YELLOW : WHITE);
		}

		if (pr.bookCount > 0) {
			header.text("  ", WHITE).item(Items.BOOK).text(" " + pr.bookCount, WHITE);
		}

		this.lines.add(new Line(1, piece, header.list, piece.getDisplayName()));

		if (!isExpanded(piece)) {
			return;
		}

		switch (piece.getSource()) {
			case CRAFT -> {
				if (pr.craftMaterials != null) {
					String craft = StringUtils.translate(KEY + "craft.hover");
					Segs segs = new Segs().item(Items.CRAFTING_TABLE, craft).text("  ", GRAY);
					pr.craftMaterials.forEach((item, count) -> segs.item(item).text(" " + count + "  ", GRAY));
					this.addLine(3, segs, craft);
				}
			}
			case BUY -> {
				if (pr.basePurchase != null) {
					RememberedOffer offer = pr.basePurchase.offer();
					Segs segs = new Segs().cost(offer, 1, GREEN).text(this.getVillagerText(pr.basePurchase), GREEN);
					this.addLine(3, segs, null);

					if (!offer.enchantments.isEmpty()) {
						List<String> names = new ArrayList<>();
						offer.enchantments.forEach((id, level) -> names.add(Enchants.getName(id, level)));
						this.addText(StringUtils.translate(KEY + "base.comes_with", String.join(", ", names)), GRAY, 4);
					}
				}
				else if (pr.baseEstimate != null) {
					String hover = StringUtils.translate(KEY + "estimate.hover");
					this.addLine(3, new Segs().item(Items.EMERALD, hover).text(" " + pr.baseEstimate[0] + "~" + pr.baseEstimate[1], RED), hover);
				}
			}
			case HAVE -> this.addText(StringUtils.translate(KEY + "base.have"), GRAY, 3);
		}

		for (String warning : pr.warnings) {
			this.addText(warning, YELLOW, 3);
		}

		for (BookResult book : pr.books) {
			if (book.purchase != null) {
				// e.g. "Efficiency V: [emerald] 28 [book] 2" for all copies; the villager and the copies are in the tooltip
				Segs segs = new Segs().text(book.getName() + ": ", GREEN).cost(book.purchase.offer(), book.copies, GREEN);
				String hover = book.purchase.villager().getDisplayName() + ": " + book.purchase.villager().getLocationText();

				if (book.copies > 1) {
					hover = StringUtils.translate(KEY + "copies.hover", book.copies, book.getBoughtName(),
							formatCost(book.purchase.offer())) + "\n" + hover;
				}

				this.addLine(3, segs, hover);
			}
			else if (book.estimate != null) {
				String hover = StringUtils.translate(KEY + "estimate.hover");
				Segs segs = new Segs().text(book.getName() + ": ", RED).item(Items.EMERALD, hover)
						.text(" " + book.estimate[0] + "~" + book.estimate[1] + " ", RED).item(Items.BOOK).text(" 1", RED);
				this.addLine(3, segs, hover);
			}
		}

		AnvilOptimizer.Result anvil = pr.anvil;

		if (!anvil.steps().isEmpty()) {
			this.addLine(3, new Segs().levels(pr.totalLevels, pr.tooExpensive), StringUtils.translate(KEY + "anvil.hover"));
			int i = 1;

			// First combine lower level copies, e.g. "1. 2x [book] (Efficiency IV) + [book] (Efficiency IV): [xp] 5 lvl"
			for (BookResult book : pr.books) {
				for (TradingCalculator.Merge merge : book.merges) {
					boolean tooExpensive = merge.cost() >= AnvilOptimizer.TOO_EXPENSIVE;
					int color = tooExpensive ? RED : GRAY;
					String shortName = Enchants.getShortName(book.id, merge.level());
					String fullName = Enchants.getName(book.id, merge.level());
					Segs segs = new Segs().text(i++ + ". " + (merge.count() > 1 ? merge.count() + "x " : ""), color);
					this.addBook(segs, shortName, fullName, color);
					segs.text(" + ", color);
					this.addBook(segs, shortName, fullName, color);
					segs.text(": ", color).levels(merge.cost(), tooExpensive);
					this.addLine(4, segs, merge.count() > 1 ? StringUtils.translate(KEY + "merge.hover", merge.count()) : null);
				}
			}

			for (AnvilOptimizer.Step step : anvil.steps()) {
				int color = step.cost() >= AnvilOptimizer.TOO_EXPENSIVE ? RED : GRAY;
				Segs segs = new Segs().text(i++ + ". ", color);
				this.addInput(segs, pr, step.target(), color);
				segs.text(" + ", color);
				this.addInput(segs, pr, step.sacrifice(), color);
				segs.text(": ", color).levels(step.cost(), step.cost() >= AnvilOptimizer.TOO_EXPENSIVE);
				this.addLine(4, segs, null);
			}
		}
	}

	/**
	 * An anvil step input: the item's icon, or an enchanted book icon with its enchantments, e.g. "[book] (Sharpness V, Looting III)"
	 * @param mask bit 0 is the item, bit i the book i - 1
	 */
	private void addInput(Segs segs, PieceResult pr, int mask, int color) {
		if ((mask & 1) != 0) {
			segs.item(pr.piece.getStack(), pr.piece.getDisplayName());
			return;
		}

		List<String> shortNames = new ArrayList<>();
		List<String> fullNames = new ArrayList<>();

		for (int i = 0; i < pr.books.size(); i++) {
			if ((mask & (1 << (i + 1))) != 0) {
				BookResult book = pr.books.get(i);
				shortNames.add(Enchants.getShortName(book.id, book.level));
				fullNames.add(book.getName());
			}
		}

		this.addBook(segs, String.join(", ", shortNames), String.join(", ", fullNames), color);
	}

	/**
	 * "[enchanted book] (Shar V, Loot III)". The short names keep the step on one line, the icon's tooltip has the full names.
	 */
	private void addBook(Segs segs, String shortNames, String fullNames, int color) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		segs.item(book, book.getHoverName().getString() + ": " + fullNames).text(" (" + shortNames + ")", color);
	}

	/**
	 * e.g. "- Librarian (level 3) at 59 73 89 (12 m)"
	 */
	private String getVillagerText(TradingCalculator.Purchase purchase) {
		return StringUtils.translate(KEY + "villager_at", purchase.villager().getDisplayName(), purchase.villager().getLocationText());
	}

	/**
	 * e.g. "12 Emerald + 1 Book"
	 */
	static String formatCost(RememberedOffer offer) {
		String cost = offer.costACount + " " + new ItemStack(offer.costA).getHoverName().getString();

		if (offer.hasCostB()) {
			cost += " + " + offer.costBCount + " " + new ItemStack(offer.costB).getHoverName().getString();
		}

		return cost;
	}

	/**
	 * The known emeralds, plus the range for books without a remembered villager: "9 + [38~147]", "[38~147]" or "9"
	 */
	private static String formatEmeralds(int known, int estimateMin, int estimateMax) {
		if (estimateMax <= 0) {
			return String.valueOf(known);
		}

		String estimate = "[" + estimateMin + "~" + estimateMax + "]";
		return known > 0 ? known + " + " + estimate : estimate;
	}

	/**
	 * Adds an expandable section header
	 * @return true if the section is expanded, so its lines should be added
	 */
	private boolean addSection(Section section) {
		this.lines.add(new Line(0, section, new Segs().text(StringUtils.translate(section.translationKey), GOLD).list, null));
		return isExpanded(section);
	}

	private static boolean isExpanded(Object toggle) {
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

	private void addText(String text, int color, int indent) {
		this.lines.add(new Line(indent, null, new Segs().text(text, color).list, null));
	}

	private void addLine(int indent, Segs segs, @Nullable String tooltip) {
		this.lines.add(new Line(indent, null, segs.list, tooltip));
	}

	// ---- Line layout ----

	/**
	 * The last scroll position that still fills the panel
	 */
	private int getMaxLineScroll() {
		int height = 0;

		for (int i = this.lines.size() - 1; i >= 0; i--) {
			height += this.lines.get(i).height();

			if (height > this.getContentHeight()) {
				return i + 1;
			}
		}

		return 0;
	}

	/**
	 * @return the index of the line at this y position, or -1
	 */
	private int getLineAt(double mouseX, double mouseY) {
		if (mouseX < this.getLinesX() || mouseX >= this.getLinesX() + this.getLinesWidth() || mouseY < CONTENT_Y) {
			return -1;
		}

		int y = CONTENT_Y;

		for (int i = this.lineScroll; i < this.lines.size(); i++) {
			int height = this.lines.get(i).height();

			if (y + height > CONTENT_Y + this.getContentHeight()) {
				break;
			}

			if (mouseY < y + height) {
				return i;
			}

			y += height;
		}

		return -1;
	}

	// ---- Input ----

	@Override
	public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
		if (super.onMouseClicked(click, doubleClick)) {
			return true;
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
				// 3 lines per notch
				this.lineScroll = Math.max(0, Math.min(this.lineScroll + delta * 3, this.getMaxLineScroll()));
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
			this.drawString(ctx, StringUtils.translate(KEY + "no_pieces"), 12, CONTENT_Y + 6, GRAY);
		}

		int end = Math.min(pieces.size(), this.pieceScroll + this.getVisiblePieces());
		int nameRight = 10 + width - 20 - 2 - (this.getStringWidth(StringUtils.translate(KEY + "edit_piece")) + 10) - 2
				- this.getSourceButtonWidth() - 4;

		for (int i = this.pieceScroll; i < end; i++) {
			SetupPiece piece = pieces.get(i);
			int y = CONTENT_Y + (i - this.pieceScroll) * ROW_HEIGHT;
			RenderUtils.drawRect(ctx, 10, y, width, ROW_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);
			ctx.renderItem(piece.getStack(), 36, y + 3);

			int color = piece.isEnabled() ? WHITE : GRAY;
			this.drawStringWithShadow(ctx, this.trimToWidth(piece.getDisplayName(), nameRight - 56), 56, y + 7, color);
		}

		RenderUtils.drawRect(ctx, this.getLinesX() - 2, CONTENT_Y - 2, this.getLinesWidth() + 4, this.getContentHeight() + 4, 0xA0000000);
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		int x = this.getLinesX();
		int right = x + this.getLinesWidth();
		int bottom = CONTENT_Y + this.getContentHeight();
		int hovered = this.getLineAt(mouseX, mouseY);
		String hoverText = null;
		int y = CONTENT_Y;
		int lastShown = this.lineScroll;

		for (int i = this.lineScroll; i < this.lines.size(); i++) {
			Line line = this.lines.get(i);

			if (y + line.height() > bottom) {
				break;
			}

			lastShown = i;

			if (i == hovered && line.toggle() != null) {
				RenderUtils.drawRect(ctx, x - 1, y, right - x + 2, line.height(), 0x30FFFFFF);
			}

			int segX = x + line.indent() * 8;
			int textY = y + (line.height() - 8) / 2;

			if (line.toggle() != null) {
				this.drawStringWithShadow(ctx, isExpanded(line.toggle()) ? "-" : "+", segX, textY, GOLD);
				segX += 8;
			}

			int segRight = right;

			String segTooltip = null;
			boolean cutOff = false;

			for (Seg seg : line.segs()) {
				if (seg.text() != null) {
					String text = this.trimToWidth(seg.text(), segRight - segX);
					this.drawStringWithShadow(ctx, text, segX, textY, seg.color());
					segX += this.getStringWidth(text);

					if (!text.equals(seg.text())) {
						cutOff = true;
						break;
					}
				}
				else {
					if (segX + 16 > segRight) {
						cutOff = true;
						break;
					}

					if (seg.xp()) {
						drawXpOrb(ctx, segX, y + 1);
					}
					else if (seg.icon() != null) {
						ctx.renderItem(seg.icon(), segX, y + 1);
					}

					if (i == hovered && mouseX >= segX && mouseX < segX + 16) {
						segTooltip = seg.tooltip();
					}

					segX += 16;
				}
			}

			if (i == hovered) {
				hoverText = segTooltip != null ? segTooltip : line.tooltip() != null ? line.tooltip() : cutOff ? line.getPlainText() : null;
			}

			y += line.height();
		}

		if (this.lineScroll > 0 || lastShown < this.lines.size() - 1) {
			String scroll = StringUtils.translate(KEY + "scroll", this.lineScroll + 1, lastShown + 1, this.lines.size());
			this.drawString(ctx, scroll, right - this.getStringWidth(scroll), CONTENT_Y - 12, GRAY);
		}

		// The name of a hovered icon, the line's tooltip, or the full text of a cut off line
		if (hoverText != null) {
			RenderUtils.drawHoverText(ctx, mouseX, mouseY, List.of(hoverText.split("\n")));
		}
	}

	/**
	 * One frame of the experience orb texture at half size, centered in the 16 x 16 icon space.
	 * The texture is gray, so it is tinted green like the orbs in the world.
	 */
	private static void drawXpOrb(GuiContext ctx, int x, int y) {
		ctx.blit(RenderPipelines.GUI_TEXTURED, XP_ORB_TEXTURE, x + 4, y + 4, 0, 32, 8, 8, 16, 16, 64, 64, XP_ORB_COLOR);
	}

	private String trimToWidth(String text, int maxWidth) {
		if (this.getStringWidth(text) <= maxWidth) {
			return text;
		}

		while (!text.isEmpty() && this.getStringWidth(text + "...") > maxWidth) {
			text = text.substring(0, text.length() - 1);
		}

		return text + "...";
	}
}
