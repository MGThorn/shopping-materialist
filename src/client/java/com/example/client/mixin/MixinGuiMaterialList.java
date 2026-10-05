package com.example.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import com.example.Reference;
import com.example.client.gui.GuiShoppingListEditor;
import com.example.client.shoppinglist.MaterialListConverter;
import com.example.client.shoppinglist.ShoppingList;
import com.example.client.shoppinglist.ShoppingListManager;
import com.example.client.shoppinglist.ShoppingMaterialList;

/**
 * Adds an "Edit items" button to Litematica's material list GUI.
 * For a shopping list it opens the editor; for any other material list
 * (placement, schematic, area analysis...) it first creates a shopping list from it.
 */
@Mixin(GuiMaterialList.class)
public abstract class MixinGuiMaterialList {
	@Shadow @Final private MaterialListBase materialList;

	@Inject(method = "initGui", at = @At("TAIL"))
	private void shoppingMaterialist$addEditButton(CallbackInfo ci) {
		GuiBase gui = (GuiBase) (Object) this;
		boolean isShoppingList = this.materialList instanceof ShoppingMaterialList;
		String label = StringUtils.translate(Reference.MOD_ID + ".gui.button.edit_shopping_list");
		String hover = StringUtils.translate(Reference.MOD_ID + ".gui.button.edit_shopping_list.hover."
				+ (isShoppingList ? "shopping_list" : "material_list"));
		int width = gui.getStringWidth(label) + 10;
		int x = gui.getScreenWidth() - width - 10;
		int y = gui.getScreenHeight() - 22;

		gui.addButton(new ButtonGeneric(x, y, width, 20, label, hover), (btn, mouseButton) -> {
			if (this.materialList instanceof ShoppingMaterialList shoppingMaterialList) {
				GuiBase.openGui(new GuiShoppingListEditor(shoppingMaterialList.getShoppingList(), gui));
			}
			else {
				this.shoppingMaterialist$createShoppingList(gui);
			}
		});
	}

	private void shoppingMaterialist$createShoppingList(GuiBase gui) {
		ShoppingListManager manager = ShoppingListManager.getInstance();
		String name = MaterialListConverter.getShoppingListName(this.materialList);
		ShoppingList list = manager.getByName(name);

		// Like removing a locked schematic placement: overwriting needs Shift
		if (list != null && !GuiBase.isShiftDown()) {
			gui.addMessage(MessageType.WARNING, Reference.MOD_ID + ".message.shopping_list_exists", list.getName());
			return;
		}

		if (list == null) {
			list = manager.create(name);
		}

		list.setItems(MaterialListConverter.getItems(this.materialList));
		GuiBase.openGui(new GuiShoppingListEditor(list, gui));
	}
}
