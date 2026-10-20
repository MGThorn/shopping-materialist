package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece;
import io.github.mgthorn.shoppingmaterialist.client.trading.CraftingRecipes;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetup;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetupManager;

/**
 * A grid of every item that can be enchanted. Clicking one adds it to the setup and opens its enchantment editor.
 */
public class GuiTradingItemPicker extends GuiBase {
	private static final int SLOT_SIZE = 18;
	private static final int CONTENT_Y = 30;

	private final TradingSetup setup;
	private final List<ItemStack> items = new ArrayList<>();

	public GuiTradingItemPicker(TradingSetup setup, @Nullable Screen parent) {
		this.setup = setup;
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.trading_item_picker");
		this.setParent(parent);

		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);

			if (item != Items.BOOK && item != Items.ENCHANTED_BOOK && !Enchants.getApplicable(stack).isEmpty()) {
				this.items.add(stack);
			}
		}
	}

	private int getColumns() {
		return Math.max(1, (this.getScreenWidth() - 20) / SLOT_SIZE);
	}

	@Override
	public void initGui() {
		super.initGui();

		String cancel = StringUtils.translate(Reference.MOD_ID + ".gui.trading.cancel");
		this.addButton(new ButtonGeneric(10, this.getScreenHeight() - 26, this.getStringWidth(cancel) + 10, 20, cancel),
				(btn, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	@Nullable
	private ItemStack getItemAt(double mouseX, double mouseY) {
		int col = (int) Math.floor((mouseX - 10) / SLOT_SIZE);
		int row = (int) Math.floor((mouseY - CONTENT_Y) / SLOT_SIZE);

		if (mouseX < 10 || mouseY < CONTENT_Y || col >= this.getColumns()) {
			return null;
		}

		int index = row * this.getColumns() + col;
		return index < this.items.size() ? this.items.get(index) : null;
	}

	@Override
	public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
		if (super.onMouseClicked(click, doubleClick)) {
			return true;
		}

		ItemStack stack = this.getItemAt(click.x(), click.y());

		// Minecraft 26.x uses SDL mouse button numbers (left = 1)
		if (stack != null && click.button() == ScanCodes.OFFSET_MOUSE_LEFT) {
			SetupPiece.Source source = CraftingRecipes.isCraftable(stack.getItem()) ? SetupPiece.Source.CRAFT : SetupPiece.Source.HAVE;
			SetupPiece piece = new SetupPiece(stack.getItem(), "", source);
			this.setup.getPieces().add(piece);
			TradingSetupManager.getInstance().save();
			GuiBase.openGui(new GuiTradingPieceEditor(piece, this.getParent()));
			return true;
		}

		return false;
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		int cols = this.getColumns();
		ItemStack hovered = this.getItemAt(mouseX, mouseY);

		for (int i = 0; i < this.items.size(); i++) {
			ItemStack stack = this.items.get(i);
			int x = 10 + (i % cols) * SLOT_SIZE;
			int y = CONTENT_Y + (i / cols) * SLOT_SIZE;

			if (stack == hovered) {
				RenderUtils.drawRect(ctx, x, y, SLOT_SIZE, SLOT_SIZE, 0x60FFFFFF);
			}

			ctx.renderItem(stack, x + 1, y + 1);
		}

		if (hovered != null) {
			ctx.renderTooltip(this.font, hovered.getHoverName(), mouseX, mouseY);
		}
	}
}
