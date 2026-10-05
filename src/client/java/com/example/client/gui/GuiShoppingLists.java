package com.example.client.gui;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import com.example.Reference;
import com.example.client.shoppinglist.ShoppingList;
import com.example.client.shoppinglist.ShoppingListManager;
import com.example.client.shoppinglist.ShoppingMaterialList;

/**
 * Lists all shopping lists, and lets the user create, open, edit, rename and delete them.
 */
public class GuiShoppingLists extends GuiBase {
	private static final String KEY = Reference.MOD_ID + ".gui.shopping_lists.";
	private static final int LIST_Y = 50;
	private static final int ROW_HEIGHT = 22;

	private GuiTextFieldGeneric nameField;
	private String newListName = "";
	private int scroll;

	public GuiShoppingLists(@Nullable Screen parent) {
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.shopping_lists");
		this.setParent(parent);
	}

	/**
	 * Shows the list in Litematica's material list GUI, and makes it the list
	 * that Litematica's material list hotkey (M + L) and info HUD use.
	 */
	public static void openMaterialList(ShoppingList list, @Nullable Screen parent) {
		ShoppingMaterialList materialList = list.getMaterialList();
		materialList.reCreateMaterialList();
		DataManager.setMaterialList(materialList);

		GuiMaterialList gui = new GuiMaterialList(materialList);
		gui.setParent(parent);
		GuiBase.openGui(gui);
	}

	@Override
	public void initGui() {
		super.initGui();

		this.createNewListControls();
		this.createListRows();
	}

	private void createNewListControls() {
		int x = 12;
		int y = 26;

		this.nameField = new GuiTextFieldGeneric(x, y + 2, 160, 16, this.font);
		this.nameField.setValue(this.newListName);
		this.addTextField(this.nameField, field -> {
			this.newListName = field.getValue();
			return true;
		});

		String label = StringUtils.translate(KEY + "create");
		ButtonGeneric button = new ButtonGeneric(x + 166, y, this.getStringWidth(label) + 10, 20, label);
		this.addButton(button, (btn, mouseButton) -> this.createList());
	}

	private void createListRows() {
		List<ShoppingList> lists = ShoppingListManager.getInstance().getLists();
		this.scroll = Math.max(0, Math.min(this.scroll, lists.size() - this.getVisibleRows()));
		int end = Math.min(lists.size(), this.scroll + this.getVisibleRows());

		for (int i = this.scroll; i < end; i++) {
			this.createRowButtons(lists.get(i), LIST_Y + (i - this.scroll) * ROW_HEIGHT);
		}
	}

	private void createRowButtons(ShoppingList list, int y) {
		int x = this.getScreenWidth() - 12;

		x -= this.createRowButton(x, y, "delete", () -> this.confirmDelete(list));
		x -= this.createRowButton(x, y, "rename", () -> this.openRename(list));
		x -= this.createRowButton(x, y, "edit", () -> GuiBase.openGui(new GuiShoppingListEditor(list, this)));
		this.createRowButton(x, y, "open", () -> openMaterialList(list, this));
	}

	/**
	 * Creates a button ending at xRight
	 * @return the width used, including the gap to the next button
	 */
	private int createRowButton(int xRight, int y, String name, Runnable action) {
		String label = StringUtils.translate(KEY + name);
		int width = this.getStringWidth(label) + 10;
		ButtonGeneric button = new ButtonGeneric(xRight - width, y + 1, width, 20, label);
		this.addButton(button, (btn, mouseButton) -> action.run());
		return width + 2;
	}

	private void createList() {
		ShoppingListManager manager = ShoppingListManager.getInstance();
		String name = this.newListName.trim();

		if (name.isEmpty()) {
			int i = manager.getLists().size() + 1;

			do {
				name = StringUtils.translate(KEY + "default_name", i++);
			}
			while (manager.getByName(name) != null);
		}

		ShoppingList list = manager.create(name);

		if (list == null) {
			this.addMessage(MessageType.ERROR, KEY + "error.name_exists", name);
			return;
		}

		this.newListName = "";
		GuiBase.openGui(new GuiShoppingListEditor(list, this));
	}

	private void openRename(ShoppingList list) {
		IStringConsumerFeedback consumer = newName -> {
			newName = newName.trim();

			if (newName.isEmpty()) {
				return false;
			}

			return ShoppingListManager.getInstance().rename(list, newName);
		};

		GuiBase.openGui(new GuiTextInput(64, KEY + "rename_title", list.getName(), this, consumer));
	}

	private void confirmDelete(ShoppingList list) {
		IConfirmationListener listener = new IConfirmationListener() {
			@Override
			public boolean onActionConfirmed() {
				ShoppingListManager.getInstance().delete(list);
				return true;
			}

			@Override
			public boolean onActionCancelled() {
				return true;
			}
		};

		GuiBase.openGui(new GuiConfirmAction(320, KEY + "delete_title", listener, this, KEY + "delete_message", list.getName()));
	}

	private int getVisibleRows() {
		return Math.max(1, (this.getScreenHeight() - LIST_Y - 10) / ROW_HEIGHT);
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0 && mouseY >= LIST_Y) {
			this.scroll -= (int) Math.signum(verticalAmount);
			this.initGui();
			return true;
		}

		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// Drawn as part of the background, so that the row buttons render on top of the rows
	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);

		List<ShoppingList> lists = ShoppingListManager.getInstance().getLists();
		int width = this.getScreenWidth() - 20;

		if (lists.isEmpty()) {
			this.drawStringWithShadow(ctx, StringUtils.translate(KEY + "empty"), 12, LIST_Y + 6, 0xFFA0A0A0);
			return;
		}

		int end = Math.min(lists.size(), this.scroll + this.getVisibleRows());

		for (int i = this.scroll; i < end; i++) {
			ShoppingList list = lists.get(i);
			int y = LIST_Y + (i - this.scroll) * ROW_HEIGHT;
			RenderUtils.drawRect(ctx, 10, y, width, ROW_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);

			// Highlight the list that Litematica's material list hotkey currently opens
			boolean active = DataManager.getMaterialList() instanceof ShoppingMaterialList ml && ml.getShoppingList() == list;
			int color = active ? 0xFF55FF55 : 0xFFFFFFFF;
			String info = StringUtils.translate(KEY + "info", list.getItems().size(), list.getTotalCount());

			this.drawStringWithShadow(ctx, list.getName(), 14, y + 7, color);
			this.drawString(ctx, info, 20 + this.getStringWidth(list.getName()), y + 7, 0xFFA0A0A0);
		}
	}
}
