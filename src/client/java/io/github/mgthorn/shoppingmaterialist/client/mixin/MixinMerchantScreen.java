package io.github.mgthorn.shoppingmaterialist.client.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListHudRenderer;
import fi.dy.masa.litematica.render.infohud.InfoHud;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.config.Configs;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.MerchantOfferConverter;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.ShoppingList;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.ShoppingListManager;
import io.github.mgthorn.shoppingmaterialist.client.shoppinglist.ShoppingMaterialList;

/**
 * Adds a "Shopping list" button to the wandering trader's and the villagers' trading screen (if enabled in the configs).
 * It fills the "Wandering Trader" or "Villager" shopping list (or a new numbered one, see the *NewList configs) with everything needed for all of the merchant's trades,
 * and shows that list in Litematica's info HUD.
 */
@Mixin(MerchantScreen.class)
public abstract class MixinMerchantScreen extends AbstractContainerScreen<MerchantMenu> {
	private MixinMerchantScreen(MerchantMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void shoppingMaterialist$addShoppingListButton(CallbackInfo ci) {
		// The trading screen is opened by right-clicking the merchant, so it is the entity under the crosshair
		Entity merchant = Minecraft.getInstance().crosshairPickEntity;
		ConfigBoolean newListOption;
		String nameKey;

		if (merchant instanceof WanderingTrader && Configs.Generic.WANDERING_TRADER_BUTTON.getBooleanValue()) {
			newListOption = Configs.Generic.WANDERING_TRADER_NEW_LIST;
			nameKey = "wandering_trader_name";
		}
		else if (merchant instanceof Villager && Configs.Generic.VILLAGER_BUTTON.getBooleanValue()) {
			newListOption = Configs.Generic.VILLAGER_NEW_LIST;
			nameKey = "villager_name";
		}
		else {
			return;
		}

		String name = Component.translatable(Reference.MOD_ID + ".gui.shopping_lists." + nameKey).getString();

		Component label = Component.translatable(Reference.MOD_ID + ".gui.button.merchant");
		Component hover = Component.translatable(Reference.MOD_ID + ".gui.button.merchant.hover", name);
		int width = this.font.width(label) + 10;

		this.addRenderableWidget(Button.builder(label, button -> this.shoppingMaterialist$createShoppingList(name, newListOption.getBooleanValue()))
				.bounds(this.leftPos, this.topPos + this.imageHeight + 2, width, 20)
				.tooltip(Tooltip.create(hover))
				.build());
	}

	private void shoppingMaterialist$createShoppingList(String name, boolean newList) {
		Map<Item, Integer> items = MerchantOfferConverter.getItems(this.menu.getOffers());
		ShoppingListManager manager = ShoppingListManager.getInstance();
		ShoppingList list = newList ? manager.createNumbered(name) : manager.getOrCreate(name);

		list.setItems(items);

		// Make it Litematica's current material list and show it in the info HUD
		ShoppingMaterialList materialList = list.getMaterialList();
		MaterialListHudRenderer renderer = materialList.getHudRenderer();
		DataManager.setMaterialList(materialList);

		if (!renderer.getShouldRenderCustom()) {
			renderer.toggleShouldRender();
		}

		InfoHud.getInstance().addInfoHudRenderer(renderer, true);

		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.displayClientMessage(Component.translatable(Reference.MOD_ID + ".message.merchant_list",
					list.getName(), items.size(), list.getTotalCount()), true);
		}
	}
}
