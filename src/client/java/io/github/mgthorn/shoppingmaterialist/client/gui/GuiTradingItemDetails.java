package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.compat.Waypoints;
import io.github.mgthorn.shoppingmaterialist.client.trading.AnvilOptimizer;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedOffer;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedVillager;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.BookResult;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.PieceResult;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetupManager;
import io.github.mgthorn.shoppingmaterialist.client.trading.VillagerMemory;

/**
 * One item of the trading list: its totals at the top, then the trades on the left and the anvil steps on the right.
 * The < and > buttons step through the items included in the calculation.
 * Trades from a remembered villager have the waypoint button of the villager browser at the end of the trades column.
 */
public class GuiTradingItemDetails extends GuiTradingLines {
	private static final int HEADER_Y = 26;
	private static final int CONTENT_Y = 58;

	private SetupPiece piece;
	@Nullable private PieceResult result;
	/**
	 * The villager of each line's trade, null for lines without one
	 */
	private final List<RememberedVillager> lineVillagers = new ArrayList<>();

	public GuiTradingItemDetails(SetupPiece piece, @Nullable Screen parent) {
		this.piece = piece;
		this.setParent(parent);
	}

	/**
	 * The items included in the calculation, in the order of the setup
	 */
	private static List<SetupPiece> getEnabledPieces() {
		return TradingSetupManager.getInstance().getSelected().getPieces().stream().filter(SetupPiece::isEnabled).toList();
	}

	// ---- Layout ----

	@Override
	protected int getLinesX() {
		return 10;
	}

	@Override
	protected int getLinesY() {
		return CONTENT_Y;
	}

	@Override
	protected int getLinesWidth() {
		return this.getScreenWidth() - 20;
	}

	@Override
	protected int getLinesHeight() {
		return this.getScreenHeight() - CONTENT_Y - 32;
	}

	/**
	 * Room for the waypoint buttons, if any line has a villager
	 */
	@Override
	protected int getLeftColumnReserve() {
		return this.lineVillagers.stream().anyMatch(villager -> villager != null) ? Waypoints.getButtonWidth() + 4 : 0;
	}

	// ---- Widgets ----

	@Override
	public void initGui() {
		super.initGui();

		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.trading_item_details", this.piece.getDisplayName());
		this.recalculate();
		this.createButtons();
	}

	/**
	 * The buttons move with the lines, so they are made again after scrolling
	 */
	private void createButtons() {
		this.clearButtons();
		this.createBottomButtons();
		this.createWaypointButtons();
	}

	/**
	 * At the right end of the trades column, for every shown line with a villager
	 */
	private void createWaypointButtons() {
		int width = Waypoints.getButtonWidth();
		int x = this.getLinesX() + this.getLinesWidth() / 2 - 6 - width;

		for (int i = 0; i < this.lineVillagers.size(); i++) {
			RememberedVillager villager = this.lineVillagers.get(i);
			int y = this.getVisibleLineY(i);

			if (villager == null || y < 0) {
				continue;
			}

			ButtonGeneric button = new Hover.Button(x, y + (this.lines.get(i).height() - 16) / 2, width, 16, Waypoints.getButtonLabel(villager),
					getVillagerText(villager) + "\n" + Hover.text(KEY + "villager_browser.waypoint.hover"));
			this.addButton(button, (btn, mouseButton) -> {
				Waypoints.onWaypointButton(villager, this);
				// Shows the new state on the button
				this.createButtons();
			});
		}
	}

	private void createBottomButtons() {
		int y = this.getScreenHeight() - 26;
		List<SetupPiece> pieces = getEnabledPieces();
		int index = pieces.indexOf(this.piece);

		ButtonGeneric previous = new ButtonGeneric(10, y, 20, 20, "<", StringUtils.translate(KEY + "details.previous"));
		previous.setEnabled(index > 0);
		this.addButton(previous, (btn, mouseButton) -> this.show(pieces.get(index - 1)));

		// The position ("2 / 5") is drawn between the buttons
		ButtonGeneric next = new ButtonGeneric(10 + 20 + 2 + this.getPositionWidth() + 2, y, 20, 20, ">",
				StringUtils.translate(KEY + "details.next"));
		next.setEnabled(index >= 0 && index < pieces.size() - 1);
		this.addButton(next, (btn, mouseButton) -> this.show(pieces.get(index + 1)));

		String back = StringUtils.translate(KEY + "details.back");
		int backWidth = this.getStringWidth(back) + 10;
		this.addButton(new ButtonGeneric(this.getScreenWidth() - 10 - backWidth, y, backWidth, 20, back),
				(btn, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	@Nullable
	private String getPositionText() {
		List<SetupPiece> pieces = getEnabledPieces();
		int index = pieces.indexOf(this.piece);
		return index >= 0 ? (index + 1) + " / " + pieces.size() : null;
	}

	/**
	 * Wide enough for the position text of any item, so the > button doesn't move
	 */
	private int getPositionWidth() {
		int count = getEnabledPieces().size();
		return this.getStringWidth(count + " / " + count) + 8;
	}

	private void show(SetupPiece piece) {
		this.piece = piece;
		this.lineScroll = 0;
		this.initGui();
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0 && mouseY >= CONTENT_Y) {
			this.scrollLines(-(int) Math.signum(verticalAmount));
			this.createButtons();
			return true;
		}

		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ---- Result lines ----

	/**
	 * Calculates the whole setup, so the item's trades are the same as in the trading list
	 */
	private void recalculate() {
		this.result = null;

		if (isInWorld()) {
			TradingCalculator.Result all = TradingCalculator.calculate(TradingSetupManager.getInstance().getSelected(),
					VillagerMemory.getInstance().getVillagers());

			for (PieceResult pr : all.pieces) {
				if (pr.piece == this.piece) {
					this.result = pr;
				}
			}
		}

		this.buildLines();
	}

	private void buildLines() {
		this.lines.clear();
		this.lineVillagers.clear();

		if (!isInWorld()) {
			this.addText(StringUtils.translate(KEY + "not_in_world"), YELLOW, 0);
		}
		else if (this.result == null) {
			this.addText(StringUtils.translate(KEY + "details.not_included"), GRAY, 0);
		}
		else {
			List<Cell> trades = getTradeCells(this.result);
			List<Cell> combining = getCombiningCells(this.result);
			int rows = Math.max(trades.size(), combining.size());

			for (int i = 0; i < rows; i++) {
				Cell left = i < trades.size() ? trades.get(i) : new Cell(new Segs(), null);
				Cell right = i < combining.size() ? combining.get(i) : new Cell(new Segs(), null);
				this.lines.add(new Line(0, null, left.segs().list, left.tooltip(), right.segs().list, right.tooltip()));
				this.lineVillagers.add(left.villager());
			}
		}

		this.clampLineScroll();
	}

	/**
	 * The item icon, directly followed by its levels, emeralds and books
	 */
	private Segs getHeaderSegs() {
		Segs header = new Segs().item(this.piece.getStack(), this.piece.getDisplayName()).text("  ", WHITE);
		PieceResult pr = this.result;

		if (pr == null) {
			return header.text(this.piece.getDisplayName(), WHITE);
		}

		header.levels(pr.totalLevels, pr.tooExpensive);

		if (pr.emeraldsKnown > 0 || pr.estimateMax > 0) {
			String hover = pr.estimateMax > 0
					? StringUtils.translate(KEY + "emeralds.hover_estimate", pr.emeraldsKnown, pr.estimateMin, pr.estimateMax)
					: StringUtils.translate(KEY + "emeralds.hover", pr.emeraldsKnown);
			header.text("  ", WHITE).item(Items.EMERALD, hover)
					.text(" " + formatEmeralds(pr.emeraldsKnown, pr.estimateMin, pr.estimateMax), pr.estimateMax > 0 ? YELLOW : WHITE);
		}

		if (pr.bookCount > 0) {
			header.text("  ", WHITE).item(Items.BOOK).text(" " + pr.bookCount, WHITE);
		}

		return header;
	}

	/**
	 * The left column: the item itself (craft, buy or have), then one row per book: its icon and cost.
	 * Names and villagers are in the tooltips.
	 */
	private static List<Cell> getTradeCells(PieceResult pr) {
		List<Cell> cells = new ArrayList<>();
		cells.add(new Cell(new Segs().text(StringUtils.translate(KEY + "column.trades"), GRAY), null));

		switch (pr.piece.getSource()) {
			case CRAFT -> {
				if (pr.craftMaterials != null) {
					String craft = StringUtils.translate(KEY + "craft.hover");
					Segs segs = new Segs().item(Items.CRAFTING_TABLE, craft).text("  ", GRAY);
					pr.craftMaterials.forEach((item, count) -> segs.item(item).text(" " + count + "  ", GRAY));
					cells.add(new Cell(segs, craft));
				}
			}
			case BUY -> {
				if (pr.basePurchase != null) {
					RememberedOffer offer = pr.basePurchase.offer();
					String villager = getVillagerText(pr.basePurchase);
					cells.add(new Cell(new Segs().item(pr.piece.getStack(), pr.piece.getDisplayName()).text("  ", GREEN)
							.cost(offer, 1, GREEN), villager, pr.basePurchase.villager()));

					// The enchantments the bought item comes with
					if (!offer.enchantments.isEmpty()) {
						List<String> names = new ArrayList<>();
						Segs segs = new Segs().text("+ ", GRAY);
						offer.enchantments.forEach((id, level) -> {
							addBook(segs, id, level);
							names.add(Enchants.getName(id, level));
						});
						cells.add(new Cell(segs, StringUtils.translate(KEY + "base.comes_with", String.join(", ", names))));
					}
				}
				else if (pr.baseEstimate != null) {
					String hover = StringUtils.translate(KEY + "estimate.hover");
					cells.add(new Cell(new Segs().item(pr.piece.getStack(), pr.piece.getDisplayName()).text("  ", RED)
							.item(Items.EMERALD, hover).text(" " + pr.baseEstimate[0] + "~" + pr.baseEstimate[1], RED), hover));
				}
			}
			case HAVE -> cells.add(new Cell(new Segs().text(StringUtils.translate(KEY + "base.have"), GRAY), null));
		}

		for (String warning : pr.warnings) {
			cells.add(new Cell(new Segs().text(warning, YELLOW), warning));
		}

		for (BookResult book : pr.books) {
			Segs segs = new Segs();
			addBook(segs, book.id, book.level);
			segs.text("  ", WHITE);

			if (book.purchase != null) {
				// The cost of all copies; the villager and the copies are in the tooltip
				segs.cost(book.purchase.offer(), book.copies, GREEN);
				String hover = getVillagerText(book.purchase);

				if (book.copies > 1) {
					hover = StringUtils.translate(KEY + "copies.hover", book.copies, book.getBoughtName(),
							formatCost(book.purchase.offer())) + "\n" + hover;
				}

				cells.add(new Cell(segs, hover, book.purchase.villager()));
			}
			else if (book.estimate != null) {
				String hover = StringUtils.translate(KEY + "estimate.hover");
				segs.item(Items.EMERALD, hover).text(" " + book.estimate[0] + "~" + book.estimate[1] + " ", RED).item(Items.BOOK).text(" 1", RED);
				cells.add(new Cell(segs, hover));
			}
		}

		return cells;
	}

	/**
	 * The right column: combining lower level copies first, then the cheapest anvil order
	 */
	private static List<Cell> getCombiningCells(PieceResult pr) {
		List<Cell> cells = new ArrayList<>();

		if (pr.anvil.steps().isEmpty()) {
			return cells;
		}

		cells.add(new Cell(new Segs().text(StringUtils.translate(KEY + "column.combining"), GRAY), StringUtils.translate(KEY + "anvil.hover")));
		int i = 1;

		// e.g. "1. 2x [Efficiency IV] + [Efficiency IV]: [xp] 05 lvl"
		for (BookResult book : pr.books) {
			for (TradingCalculator.Merge merge : book.merges) {
				boolean tooExpensive = merge.cost() >= AnvilOptimizer.TOO_EXPENSIVE;
				int color = tooExpensive ? RED : GRAY;
				Segs segs = new Segs().text(i++ + ". " + (merge.count() > 1 ? merge.count() + "x " : ""), color);
				addBook(segs, book.id, merge.level());
				segs.text(" + ", color);
				addBook(segs, book.id, merge.level());
				segs.text(": ", color).levels(merge.cost(), tooExpensive);
				cells.add(new Cell(segs, merge.count() > 1 ? StringUtils.translate(KEY + "merge.hover", merge.count()) : null));
			}
		}

		for (AnvilOptimizer.Step step : pr.anvil.steps()) {
			boolean tooExpensive = step.cost() >= AnvilOptimizer.TOO_EXPENSIVE;
			int color = tooExpensive ? RED : GRAY;
			Segs segs = new Segs().text(i++ + ". ", color);
			addInput(segs, pr, step.target());
			segs.text(" + ", color);
			addInput(segs, pr, step.sacrifice());
			segs.text(": ", color).levels(step.cost(), tooExpensive);
			cells.add(new Cell(segs, null));
		}

		return cells;
	}

	/**
	 * An anvil step input: the item's icon, or one book icon per enchantment for books that were combined before.
	 * The names are only in the tooltips.
	 * @param mask bit 0 is the item, bit i the book i - 1
	 */
	private static void addInput(Segs segs, PieceResult pr, int mask) {
		if ((mask & 1) != 0) {
			segs.item(pr.piece.getStack(), pr.piece.getDisplayName());
			return;
		}

		for (int i = 0; i < pr.books.size(); i++) {
			if ((mask & (1 << (i + 1))) != 0) {
				BookResult book = pr.books.get(i);
				addBook(segs, book.id, book.level);
			}
		}
	}

	/**
	 * An enchanted book that stores this enchantment, so resource packs with a texture per enchantment
	 * (e.g. Re-covered) show their icon. The full name is the tooltip.
	 */
	private static void addBook(Segs segs, String id, int level) {
		segs.item(Enchants.createBook(id, level), Enchants.getName(id, level));
	}

	/**
	 * e.g. "Librarian (level 3): 59 73 89 (12 m)"
	 */
	private static String getVillagerText(TradingCalculator.Purchase purchase) {
		return getVillagerText(purchase.villager());
	}

	private static String getVillagerText(RememberedVillager villager) {
		return villager.getDisplayName() + ": " + Hover.value(villager.getLocationText());
	}

	// ---- Rendering ----

	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);

		RenderUtils.drawRect(ctx, 8, HEADER_Y - 2, this.getScreenWidth() - 16, ICON_LINE_HEIGHT + 4, 0xA0303030);
		RenderUtils.drawRect(ctx, this.getLinesX() - 2, CONTENT_Y - 2, this.getLinesWidth() + 4, this.getLinesHeight() + 4, 0xA0000000);

		// Between the < and > buttons
		String position = this.getPositionText();

		if (position != null) {
			int x = 10 + 20 + 2 + (this.getPositionWidth() - this.getStringWidth(position)) / 2;
			this.drawStringWithShadow(ctx, position, x, this.getScreenHeight() - 20, WHITE);
		}
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		int right = 10 + this.getLinesWidth();
		boolean headerHovered = mouseY >= HEADER_Y && mouseY < HEADER_Y + ICON_LINE_HEIGHT;
		SegHover hover = this.drawSegs(ctx, this.getHeaderSegs().list, 10, right, HEADER_Y, ICON_LINE_HEIGHT, headerHovered ? mouseX : -1);

		this.drawLines(ctx, mouseX, mouseY);

		if (hover.tooltip() != null) {
			RenderUtils.drawHoverText(ctx, mouseX, mouseY, Hover.lines(hover.tooltip()));
		}
	}
}
