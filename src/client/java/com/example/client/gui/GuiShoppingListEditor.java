package com.example.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.gui.SliderCallbackInteger;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ISliderCallback;
import fi.dy.masa.malilib.gui.widgets.WidgetSlider;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;
import com.example.Reference;
import com.example.client.config.Configs;
import com.example.client.shoppinglist.ShoppingList;

/**
 * Item picker for a shopping list: a searchable grid of all items on the left,
 * and the items currently in the list on the right.
 * <p>
 * Grid: left-click adds 1, right-click removes 1, hold shift for a full stack,
 * hold ctrl for the configurable {@link Configs.Generic#CTRL_CLICK_AMOUNT}.
 */
public class GuiShoppingListEditor extends GuiBase {
	private static final String KEY = Reference.MOD_ID + ".gui.shopping_list_editor.";
	private static final int SLOT_SIZE = 18;
	private static final int CONTENT_Y = 50;
	private static final int ENTRY_HEIGHT = 20;

	@Nullable private static List<ItemStack> allItems;

	private final ShoppingList list;
	private final List<ItemStack> filteredItems = new ArrayList<>();
	private GuiTextFieldGeneric searchField;
	private GuiTextFieldInteger ctrlAmountField;
	private String searchText = "";
	private boolean searchFocused = true;
	private int gridScroll;
	private int entryScroll;

	public GuiShoppingListEditor(ShoppingList list, @Nullable Screen parent) {
		this.list = list;
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.shopping_list_editor", list.getName());
		this.setParent(parent);
		this.updateFilter();
	}

	private static List<ItemStack> getAllItems() {
		if (allItems == null) {
			allItems = new ArrayList<>();

			for (Item item : BuiltInRegistries.ITEM) {
				if (item != Items.AIR) {
					allItems.add(new ItemStack(item));
				}
			}
		}

		return allItems;
	}

	private void updateFilter() {
		String filter = this.searchText.trim().toLowerCase(Locale.ROOT);
		this.filteredItems.clear();

		for (ItemStack stack : getAllItems()) {
			if (filter.isEmpty()
					|| stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter)
					|| BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(filter)) {
				this.filteredItems.add(stack);
			}
		}

		this.gridScroll = 0;
	}

	// ---- Layout ----

	private int getGridColumns() {
		return Math.max(1, (int) ((this.getScreenWidth() - 30) * 0.55) / SLOT_SIZE);
	}

	private int getGridRows() {
		return Math.max(1, (this.getScreenHeight() - CONTENT_Y - 36) / SLOT_SIZE);
	}

	private int getGridWidth() {
		return this.getGridColumns() * SLOT_SIZE;
	}

	private int getMaxGridScroll() {
		int rows = (this.filteredItems.size() + this.getGridColumns() - 1) / this.getGridColumns();
		return Math.max(0, rows - this.getGridRows());
	}

	private int getEntriesX() {
		return 10 + this.getGridWidth() + 12;
	}

	private int getEntriesWidth() {
		return this.getScreenWidth() - this.getEntriesX() - 10;
	}

	private int getVisibleEntries() {
		return Math.max(1, (this.getScreenHeight() - CONTENT_Y - 36) / ENTRY_HEIGHT);
	}

	// ---- Widgets ----

	@Override
	public void initGui() {
		super.initGui();

		this.searchField = new GuiTextFieldGeneric(10, 28, this.getGridWidth(), 16, this.font);
		this.searchField.setValue(this.searchText);
		this.searchField.setFocused(this.searchFocused);
		this.addTextField(this.searchField, field -> {
			if (!field.getValue().equals(this.searchText)) {
				this.searchText = field.getValue();
				this.updateFilter();
			}

			return true;
		});

		this.createEntryButtons();
		this.createBottomButtons();
		this.createCtrlAmountControls();
	}

	private void createEntryButtons() {
		List<Item> items = new ArrayList<>(this.list.getItems().keySet());
		this.entryScroll = Math.max(0, Math.min(this.entryScroll, items.size() - this.getVisibleEntries()));
		int end = Math.min(items.size(), this.entryScroll + this.getVisibleEntries());
		int right = this.getEntriesX() + this.getEntriesWidth();

		for (int i = this.entryScroll; i < end; i++) {
			Item item = items.get(i);
			int y = CONTENT_Y + (i - this.entryScroll) * ENTRY_HEIGHT + 1;

			this.addButton(new ButtonGeneric(right - 18, y, 18, 18, "x", StringUtils.translate(KEY + "remove")),
					(btn, mouseButton) -> this.changeList(() -> this.list.remove(item)));
			this.addButton(new ButtonGeneric(right - 38, y, 18, 18, "+", StringUtils.translate(KEY + "plus")),
					(btn, mouseButton) -> this.changeList(() -> this.list.addCount(item, this.getClickAmount(item))));
			this.addButton(new ButtonGeneric(right - 58, y, 18, 18, "-", StringUtils.translate(KEY + "minus")),
					(btn, mouseButton) -> this.changeList(() -> this.list.addCount(item, -this.getClickAmount(item))));
		}
	}

	private void createBottomButtons() {
		int x = 10;
		int y = this.getScreenHeight() - 26;

		String label = StringUtils.translate(KEY + "open_material_list");
		ButtonGeneric button = new ButtonGeneric(x, y, this.getStringWidth(label) + 10, 20, label);
		this.addButton(button, (btn, mouseButton) -> GuiShoppingLists.openMaterialList(this.list, this.getParent()));
		x += button.getWidth() + 4;

		label = StringUtils.translate(KEY + "done");
		button = new ButtonGeneric(x, y, this.getStringWidth(label) + 10, 20, label);
		this.addButton(button, (btn, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	/**
	 * Slider + number field at the bottom right for the Ctrl+click amount.
	 * Both edit the same config value, so they stay in sync.
	 */
	private void createCtrlAmountControls() {
		ConfigInteger config = Configs.Generic.CTRL_CLICK_AMOUNT;
		int y = this.getScreenHeight() - 26;
		int fieldX = this.getScreenWidth() - 10 - 32;
		int sliderX = fieldX - 4 - 80;

		GuiTextFieldInteger field = new GuiTextFieldInteger(fieldX, y + 2, 32, 16, this.font);
		this.ctrlAmountField = field;
		field.setValue(String.valueOf(config.getIntegerValue()));
		this.addTextField(field, textField -> {
			try {
				config.setIntegerValue(Integer.parseInt(textField.getValue()));
			}
			catch (NumberFormatException ignore) {
			}

			return true;
		});

		SliderCallbackInteger sliderValue = new SliderCallbackInteger(config, null);
		this.addWidget(new WidgetSlider(sliderX, y, 80, 20, new ISliderCallback() {
			@Override
			public int getMaxSteps() {
				return sliderValue.getMaxSteps();
			}

			@Override
			public double getValueRelative() {
				return sliderValue.getValueRelative();
			}

			@Override
			public void setValueRelative(double relativeValue) {
				sliderValue.setValueRelative(relativeValue);
				field.setValue(String.valueOf(config.getIntegerValue()));
			}

			@Override
			public String getFormattedDisplayValue() {
				return sliderValue.getFormattedDisplayValue();
			}
		}));

		String label = StringUtils.translate(KEY + "ctrl_amount");
		int labelWidth = this.getStringWidth(label);
		this.addLabel(sliderX - 6 - labelWidth, y + 6, labelWidth, 12, 0xFFFFFFFF, label);
	}

	@Override
	public void removed() {
		super.removed();

		// Save the Ctrl+click amount
		ConfigManager.getInstance().onConfigsChanged(Reference.MOD_ID);
	}

	/**
	 * Applies a change to the list and rebuilds the entry buttons, keeping the search state.
	 */
	private void changeList(Runnable change) {
		change.run();
		this.searchFocused = this.searchField.isFocused();
		this.initGui();
	}

	/**
	 * Ctrl: the configurable amount, Shift: a full stack, otherwise 1
	 */
	private int getClickAmount(Item item) {
		if (isCtrlDown()) {
			return Configs.Generic.CTRL_CLICK_AMOUNT.getIntegerValue();
		}

		return isShiftDown() ? new ItemStack(item).getMaxStackSize() : 1;
	}

	// ---- Input ----

	@Nullable
	private ItemStack getGridItemAt(double mouseX, double mouseY) {
		int col = (int) Math.floor((mouseX - 10) / SLOT_SIZE);
		int row = (int) Math.floor((mouseY - CONTENT_Y) / SLOT_SIZE);

		if (mouseX < 10 || mouseY < CONTENT_Y || col >= this.getGridColumns() || row >= this.getGridRows()) {
			return null;
		}

		int index = (this.gridScroll + row) * this.getGridColumns() + col;
		return index < this.filteredItems.size() ? this.filteredItems.get(index) : null;
	}

	@Override
	public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
		if (super.onMouseClicked(click, doubleClick)) {
			return true;
		}

		ItemStack stack = this.getGridItemAt(click.x(), click.y());

		// Minecraft 26.x uses SDL mouse button numbers (left = 1, right = 3)
		boolean leftClick = click.button() == ScanCodes.OFFSET_MOUSE_LEFT;
		boolean rightClick = click.button() == ScanCodes.OFFSET_MOUSE_RIGHT;

		if (stack != null && (leftClick || rightClick)) {
			Item item = stack.getItem();
			int amount = leftClick ? this.getClickAmount(item) : -this.getClickAmount(item);
			this.changeList(() -> this.list.addCount(item, amount));
			return true;
		}

		return false;
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		// Ctrl + mouse wheel changes the Ctrl+click amount instead of scrolling
		if (verticalAmount != 0 && isCtrlDown()) {
			ConfigInteger config = Configs.Generic.CTRL_CLICK_AMOUNT;
			config.setIntegerValue(config.getIntegerValue() + (int) Math.signum(verticalAmount));
			this.ctrlAmountField.setValue(String.valueOf(config.getIntegerValue()));
			return true;
		}

		if (verticalAmount != 0 && mouseY >= CONTENT_Y) {
			int delta = -(int) Math.signum(verticalAmount);

			if (mouseX < this.getEntriesX()) {
				this.gridScroll = Math.max(0, Math.min(this.gridScroll + delta, this.getMaxGridScroll()));
			}
			else {
				this.entryScroll += delta;
				this.changeList(() -> {});
			}

			return true;
		}

		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ---- Rendering ----

	// The entry rows are drawn as part of the background, so that their buttons render on top of them
	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);
		this.drawEntries(ctx, mouseX, mouseY);
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		ItemStack hovered = this.drawGrid(ctx, mouseX, mouseY);

		if (hovered != null) {
			ctx.renderTooltip(this.font, ctx.itemTooltips(hovered), mouseX, mouseY);
		}
	}

	@Nullable
	private ItemStack drawGrid(GuiContext ctx, int mouseX, int mouseY) {
		int cols = this.getGridColumns();
		int rows = this.getGridRows();
		ItemStack hovered = this.getGridItemAt(mouseX, mouseY);

		RenderUtils.drawRect(ctx, 9, CONTENT_Y - 1, cols * SLOT_SIZE + 2, rows * SLOT_SIZE + 2, 0xA0000000);

		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				int index = (this.gridScroll + row) * cols + col;

				if (index >= this.filteredItems.size()) {
					break;
				}

				ItemStack stack = this.filteredItems.get(index);
				int x = 10 + col * SLOT_SIZE;
				int y = CONTENT_Y + row * SLOT_SIZE;

				if (stack == hovered) {
					RenderUtils.drawRect(ctx, x, y, SLOT_SIZE, SLOT_SIZE, 0x60FFFFFF);
				}

				// Mark items that are already in the list
				if (this.list.getCount(stack.getItem()) > 0) {
					RenderUtils.drawOutline(ctx, x, y, SLOT_SIZE, SLOT_SIZE, 0xFF55FF55);
				}

				ctx.renderItem(stack, x + 1, y + 1);
			}
		}

		String count = StringUtils.translate(KEY + "item_count", this.filteredItems.size());
		this.drawString(ctx, count, 10, CONTENT_Y + rows * SLOT_SIZE + 4, 0xFFA0A0A0);

		return hovered;
	}

	private void drawEntries(GuiContext ctx, int mouseX, int mouseY) {
		int x = this.getEntriesX();
		int width = this.getEntriesWidth();
		List<Map.Entry<Item, Integer>> entries = new ArrayList<>(this.list.getItems().entrySet());

		String header = StringUtils.translate(KEY + "entries_header", entries.size(), this.list.getTotalCount());
		this.drawStringWithShadow(ctx, header, x, 33, 0xFFFFFFFF);

		if (entries.isEmpty()) {
			this.drawString(ctx, StringUtils.translate(KEY + "empty"), x + 2, CONTENT_Y + 6, 0xFFA0A0A0);
			return;
		}

		int end = Math.min(entries.size(), this.entryScroll + this.getVisibleEntries());

		for (int i = this.entryScroll; i < end; i++) {
			Map.Entry<Item, Integer> entry = entries.get(i);
			ItemStack stack = new ItemStack(entry.getKey());
			int y = CONTENT_Y + (i - this.entryScroll) * ENTRY_HEIGHT;

			RenderUtils.drawRect(ctx, x, y, width, ENTRY_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);
			ctx.renderItem(stack, x + 2, y + 2);

			String countStr = this.formatCount(entry.getValue(), stack.getMaxStackSize());
			int countWidth = this.getStringWidth(countStr);
			int countX = x + width - 62 - countWidth;
			String name = this.trimToWidth(stack.getHoverName().getString(), countX - (x + 22) - 4);

			this.drawStringWithShadow(ctx, name, x + 22, y + 6, 0xFFFFFFFF);
			this.drawStringWithShadow(ctx, countStr, countX, y + 6, 0xFFFFAA00);
		}
	}

	/**
	 * Formats a count like Litematica's material list, e.g. "130 (2 x 64 + 2)"
	 */
	private String formatCount(int count, int maxStackSize) {
		if (maxStackSize <= 1 || count < maxStackSize) {
			return String.valueOf(count);
		}

		int stacks = count / maxStackSize;
		int remainder = count % maxStackSize;
		return remainder > 0
				? String.format("%d (%d x %d + %d)", count, stacks, maxStackSize, remainder)
				: String.format("%d (%d x %d)", count, stacks, maxStackSize);
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
