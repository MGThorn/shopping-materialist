package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.ShoppingList;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.ShoppingListManager;
import io.github.mgthorn.shoppingmaterialist.client.trading.ExportOptions;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedOffer;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingExport;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingExport.EmeraldTrade;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetup;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetupManager;
import io.github.mgthorn.shoppingmaterialist.client.trading.VillagerMemory;

/**
 * Exports the selected trading setup into a shopping list. The options are on the left: the list name, the estimate
 * for unknown prices, raw materials, where each item comes from, and the emerald trades used instead of emeralds.
 * The right shows the items that will go on the list. The options are saved with the setup.
 */
public class GuiTradingExport extends GuiTradingLines {
	private static final String EXPORT_KEY = KEY + "export.";
	private static final int ROWS_Y = 106;
	private static final int ROW_HEIGHT = 20;
	private static final ItemStack EMERALD = new ItemStack(Items.EMERALD);

	/**
	 * A row of the scrollable list on the left
	 */
	private sealed interface Row permits HeaderRow, NoteRow, PieceRow, TradeRow {
	}

	private record HeaderRow(String text) implements Row {
	}

	private record NoteRow(String text) implements Row {
	}

	private record PieceRow(SetupPiece piece) implements Row {
	}

	private record TradeRow(EmeraldTrade trade) implements Row {
	}

	private final List<Row> rows = new ArrayList<>();
	private List<EmeraldTrade> trades = List.of();
	@Nullable private TradingExport.Preview preview;
	private int rowScroll;

	public GuiTradingExport(@Nullable Screen parent) {
		this.setParent(parent);
	}

	private static TradingSetup getSetup() {
		return TradingSetupManager.getInstance().getSelected();
	}

	private static ExportOptions getOptions() {
		return getSetup().getExportOptions();
	}

	private static String getDefaultListName() {
		return StringUtils.translate(EXPORT_KEY + "list_name", getSetup().getName());
	}

	private static String getListName() {
		String name = getOptions().listName.trim();
		return name.isEmpty() ? getDefaultListName() : name;
	}

	// ---- Layout ----

	private int getLeftWidth() {
		return Math.min(280, (this.getScreenWidth() - 30) * 11 / 20);
	}

	@Override
	protected int getLinesX() {
		return 10 + this.getLeftWidth() + 12;
	}

	@Override
	protected int getLinesY() {
		return 40;
	}

	@Override
	protected int getLinesWidth() {
		return this.getScreenWidth() - this.getLinesX() - 10;
	}

	@Override
	protected int getLinesHeight() {
		return this.getScreenHeight() - 40 - 32;
	}

	private int getVisibleRows() {
		return Math.max(1, (this.getScreenHeight() - ROWS_Y - 32) / ROW_HEIGHT);
	}

	// ---- Widgets ----

	@Override
	public void initGui() {
		super.initGui();

		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.trading_export", getSetup().getName());
		this.recalculate();
		this.createOptionWidgets();
		this.createRowButtons();
		this.createBottomButtons();
	}

	private void createOptionWidgets() {
		ExportOptions options = getOptions();
		int x = 10;
		int y = 26;

		String nameLabel = StringUtils.translate(EXPORT_KEY + "name");
		int labelWidth = this.getStringWidth(nameLabel);
		this.addLabel(x, y + 6, labelWidth, 12, WHITE, nameLabel);

		GuiTextFieldGeneric field = new GuiTextFieldGeneric(x + labelWidth + 6, y + 2, this.getLeftWidth() - labelWidth - 6, 16, this.font);
		field.setValue(getListName());
		this.addTextField(field, textField -> {
			String name = textField.getValue().trim();
			options.listName = name.equals(getDefaultListName()) ? "" : name;
			TradingSetupManager.getInstance().save();
			this.buildLines();
			return true;
		});

		y += 22;
		this.addOptionButton(x, y, StringUtils.translate(EXPORT_KEY + "estimate", options.estimate.getDisplayName()),
				EXPORT_KEY + "estimate.hover", () -> options.estimate = options.estimate.next());

		y += 22;
		x += this.addOptionButton(x, y, StringUtils.translate(EXPORT_KEY + "books_as_raw", onOff(options.booksAsRaw)),
				EXPORT_KEY + "books_as_raw.hover", () -> options.booksAsRaw = !options.booksAsRaw);
		this.addOptionButton(x, y, StringUtils.translate(EXPORT_KEY + "materials_as_raw", onOff(options.materialsAsRaw)),
				EXPORT_KEY + "materials_as_raw.hover", () -> options.materialsAsRaw = !options.materialsAsRaw);
	}

	private static String onOff(boolean value) {
		return StringUtils.translate(EXPORT_KEY + (value ? "on" : "off"));
	}

	/**
	 * A button that changes an option, saves the setup and shows the new preview
	 * @return the width used, including the gap to the next button
	 */
	private int addOptionButton(int x, int y, String label, String hoverKey, Runnable change) {
		int width = this.getStringWidth(label) + 10;
		this.addButton(Hover.Button.of(x, y, width, 20, label, hoverKey),
				(btn, mouseButton) -> this.change(change));
		return width + 2;
	}

	private void change(Runnable change) {
		change.run();
		TradingSetupManager.getInstance().save();
		this.initGui();
	}

	/**
	 * The source buttons of the visible item rows; emerald trades are ticked by clicking their row
	 */
	private void createRowButtons() {
		this.rowScroll = Math.max(0, Math.min(this.rowScroll, this.rows.size() - this.getVisibleRows()));
		int end = Math.min(this.rows.size(), this.rowScroll + this.getVisibleRows());
		int width = this.getSourceButtonWidth();
		int x = 10 + this.getLeftWidth() - width - 2;

		for (int i = this.rowScroll; i < end; i++) {
			if (this.rows.get(i) instanceof PieceRow(SetupPiece piece)) {
				int y = ROWS_Y + (i - this.rowScroll) * ROW_HEIGHT;
				this.addButton(Hover.Button.of(x, y, width, ROW_HEIGHT, piece.getSource().getDisplayName(), KEY + "source.hover"),
						(btn, mouseButton) -> this.change(() -> piece.setSource(piece.getSource().next())));
			}
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
		int y = this.getScreenHeight() - 26;
		String exportLabel = StringUtils.translate(EXPORT_KEY + "confirm");
		ButtonGeneric export = new ButtonGeneric(10, y, this.getStringWidth(exportLabel) + 10, 20, exportLabel,
				StringUtils.translate(EXPORT_KEY + "confirm.hover"));
		export.setEnabled(this.preview != null && !this.preview.items().isEmpty());
		this.addButton(export, (btn, mouseButton) -> this.export());

		String back = StringUtils.translate(KEY + "details.back");
		this.addButton(new ButtonGeneric(10 + export.getWidth() + 2, y, this.getStringWidth(back) + 10, 20, back),
				(btn, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	// ---- Actions ----

	/**
	 * Puts the previewed items into the list and opens it in Litematica's material list
	 */
	private void export() {
		if (this.preview == null || this.preview.items().isEmpty()) {
			return;
		}

		ShoppingList list = ShoppingListManager.getInstance().getOrCreate(getListName());

		if (list == null) {
			return;
		}

		list.setItems(this.preview.items());
		GuiShoppingLists.openMaterialList(list, this.getParent());
	}

	// ---- Content ----

	private void recalculate() {
		if (!isInWorld()) {
			this.preview = null;
			this.trades = List.of();
		}
		else {
			TradingCalculator.Result result = TradingCalculator.calculate(getSetup(), VillagerMemory.getInstance().getVillagers());
			this.trades = TradingExport.getEmeraldTrades(VillagerMemory.getInstance().getVillagers());
			this.preview = TradingExport.build(result, getOptions(), this.trades);
		}

		this.buildRows();
		this.buildLines();
	}

	private void buildRows() {
		this.rows.clear();
		this.rows.add(new HeaderRow(StringUtils.translate(EXPORT_KEY + "items")));

		List<SetupPiece> pieces = getSetup().getPieces().stream().filter(SetupPiece::isEnabled).toList();

		if (pieces.isEmpty()) {
			this.rows.add(new NoteRow(StringUtils.translate(KEY + "no_pieces")));
		}

		pieces.forEach(piece -> this.rows.add(new PieceRow(piece)));

		this.rows.add(new HeaderRow(StringUtils.translate(EXPORT_KEY + "emerald_trades")));

		if (this.trades.isEmpty()) {
			this.rows.add(new NoteRow(StringUtils.translate(EXPORT_KEY + "no_emerald_trades")));
		}

		this.trades.forEach(trade -> this.rows.add(new TradeRow(trade)));
	}

	/**
	 * The preview on the right: every item of the list with its count
	 */
	private void buildLines() {
		this.lines.clear();

		if (this.preview == null) {
			this.addText(StringUtils.translate(KEY + "not_in_world"), YELLOW, 0);
			return;
		}

		String name = getListName();
		this.addText(StringUtils.translate(EXPORT_KEY + "preview", name), GOLD, 0);

		if (ShoppingListManager.getInstance().getByName(name) != null) {
			this.addText(StringUtils.translate(EXPORT_KEY + "replaces"), YELLOW, 0);
		}

		if (this.preview.items().isEmpty()) {
			this.addText(StringUtils.translate(EXPORT_KEY + "empty"), GRAY, 1);
		}

		for (Map.Entry<Item, Integer> entry : this.preview.items().entrySet()) {
			ItemStack stack = new ItemStack(entry.getKey());
			this.addLine(1, new Segs().item(stack).text(" " + entry.getValue() + "  " + stack.getHoverName().getString(), WHITE), null);
		}

		this.clampLineScroll();
	}

	// ---- Input ----

	/**
	 * The visible row at this position on the left, or -1
	 */
	private int getRowAt(double mouseX, double mouseY) {
		if (mouseX < 10 || mouseX >= 10 + this.getLeftWidth() || mouseY < ROWS_Y) {
			return -1;
		}

		int index = this.rowScroll + (int) ((mouseY - ROWS_Y) / ROW_HEIGHT);
		int end = Math.min(this.rows.size(), this.rowScroll + this.getVisibleRows());
		return index < end ? index : -1;
	}

	@Override
	public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
		if (super.onMouseClicked(click, doubleClick)) {
			return true;
		}

		int row = this.getRowAt(click.x(), click.y());

		// Clicking an emerald trade ticks it, or unticks it
		if (row >= 0 && click.button() == ScanCodes.OFFSET_MOUSE_LEFT && this.rows.get(row) instanceof TradeRow(EmeraldTrade trade)) {
			this.change(() -> {
				if (!getOptions().emeraldTrades.remove(trade.item())) {
					getOptions().emeraldTrades.add(trade.item());
				}
			});
			return true;
		}

		return false;
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0) {
			int delta = -(int) Math.signum(verticalAmount);

			if (mouseX < this.getLinesX() && mouseY >= ROWS_Y) {
				this.rowScroll += delta;
				this.initGui();
				return true;
			}

			if (mouseX >= this.getLinesX()) {
				this.scrollLines(delta);
				return true;
			}
		}

		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ---- Rendering ----

	// The rows are drawn as part of the background, so that their buttons render on top of them
	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);

		int width = this.getLeftWidth();
		int end = Math.min(this.rows.size(), this.rowScroll + this.getVisibleRows());

		for (int i = this.rowScroll; i < end; i++) {
			Row row = this.rows.get(i);
			int y = ROWS_Y + (i - this.rowScroll) * ROW_HEIGHT;
			int textY = y + (ROW_HEIGHT - 8) / 2;

			switch (row) {
				case HeaderRow(String text) -> this.drawStringWithShadow(ctx, this.trimToWidth(text, width), 10, textY, GOLD);
				case NoteRow(String text) -> this.drawString(ctx, this.trimToWidth(text, width - 4), 12, textY, GRAY);
				case PieceRow(SetupPiece piece) -> {
					RenderUtils.drawRect(ctx, 10, y, width, ROW_HEIGHT, 0xA0202020);
					ctx.renderItem(piece.getStack(), 12, y + 2);
					int textWidth = width - 22 - this.getSourceButtonWidth() - 6;
					this.drawStringWithShadow(ctx, this.trimToWidth(piece.getDisplayName(), textWidth), 32, textY, WHITE);
				}
				case TradeRow(EmeraldTrade trade) -> {
					RenderUtils.drawRect(ctx, 10, y, width, ROW_HEIGHT, 0xA0202020);
					boolean ticked = getOptions().emeraldTrades.contains(trade.item());
					drawCheckbox(ctx, 14, y + (ROW_HEIGHT - CHECKBOX_SIZE) / 2, ticked);
				}
			}
		}

		RenderUtils.drawRect(ctx, this.getLinesX() - 2, this.getLinesY() - 2, this.getLinesWidth() + 4, this.getLinesHeight() + 4, 0xA0000000);
	}

	/**
	 * The icons and text of the emerald trade rows
	 * @return the tooltip of the hovered trade row (see {@link Hover}), or null
	 */
	@Nullable
	private String drawTradeRows(GuiContext ctx, int mouseX, int mouseY) {
		int width = this.getLeftWidth();
		int end = Math.min(this.rows.size(), this.rowScroll + this.getVisibleRows());
		int hovered = this.getRowAt(mouseX, mouseY);
		String tooltip = null;

		for (int i = this.rowScroll; i < end; i++) {
			if (!(this.rows.get(i) instanceof TradeRow(EmeraldTrade trade))) {
				continue;
			}

			int y = ROWS_Y + (i - this.rowScroll) * ROW_HEIGHT;
			RememberedOffer cheapest = trade.getCheapest().offer();
			boolean ticked = getOptions().emeraldTrades.contains(trade.item());
			int covered = this.preview != null ? this.preview.covered().getOrDefault(trade.item(), 0) : 0;

			// "covers 12 / 40" at the right end
			String covers = StringUtils.translate(EXPORT_KEY + "covers", covered, trade.available());
			int coversX = 10 + width - 4 - this.getStringWidth(covers);
			this.drawStringWithShadow(ctx, covers, coversX, y + (ROW_HEIGHT - 8) / 2, ticked && covered > 0 ? GREEN : GRAY);

			// "[paper] 24 -> [emerald] 1"
			Segs segs = new Segs().item(trade.item()).text(" " + cheapest.costACount + " → ", WHITE)
					.item(EMERALD, EMERALD.getHoverName().getString()).text(" " + cheapest.resultCount, WHITE);
			this.drawSegs(ctx, segs.list, 28, coversX - 4, y + 1, ROW_HEIGHT - 2, -1);

			if (i == hovered) {
				long villagers = trade.offers().stream().map(offer -> offer.villager().uuid).distinct().count();
				tooltip = new ItemStack(trade.item()).getHoverName().getString() + "\n"
						+ Hover.text(EXPORT_KEY + "trade.hover", Hover.value(cheapest.costACount), Hover.value(cheapest.resultCount),
						Hover.value(covered), Hover.value(trade.available()), villagers);
			}
		}

		return tooltip;
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		String tooltip = this.drawTradeRows(ctx, mouseX, mouseY);

		int end = Math.min(this.rows.size(), this.rowScroll + this.getVisibleRows());

		if (this.rowScroll > 0 || end < this.rows.size()) {
			String scroll = StringUtils.translate(KEY + "scroll", this.rowScroll + 1, end, this.rows.size());
			this.drawString(ctx, scroll, 10 + this.getLeftWidth() - this.getStringWidth(scroll), ROWS_Y - 11, GRAY);
		}

		this.drawLines(ctx, mouseX, mouseY);

		// After the preview, so that it is drawn on top of it
		if (tooltip != null) {
			RenderUtils.drawHoverText(ctx, mouseX, mouseY, Hover.lines(List.of(tooltip.split("\n")),
					StringUtils.translate(EXPORT_KEY + "trade.click")));
		}
	}
}
