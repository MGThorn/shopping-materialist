package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.SetupPiece;
import io.github.mgthorn.shoppingmaterialist.client.trading.CraftingRecipes;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingSetupManager;

/**
 * Picks the enchantments (and level) wanted on one item of a trading setup, its label and where the item comes from.
 * Choosing an enchantment removes the ones it conflicts with, e.g. Fortune replaces Silk Touch.
 */
public class GuiTradingPieceEditor extends GuiBase {
	private static final String KEY = Reference.MOD_ID + ".gui.trading.editor.";
	private static final int CONTENT_Y = 74;
	private static final int ROW_HEIGHT = 22;
	private static final int LEVEL_BUTTON_WIDTH = 22;

	private final SetupPiece piece;
	private final List<Holder<Enchantment>> enchantments;
	private String labelText;
	private int scroll;

	public GuiTradingPieceEditor(SetupPiece piece, @Nullable Screen parent) {
		this.piece = piece;
		this.labelText = piece.getLabel();
		this.enchantments = Enchants.getApplicable(piece.getStack());
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.trading_piece_editor", piece.getStack().getHoverName().getString());
		this.setParent(parent);
	}

	private int getVisibleRows() {
		return Math.max(1, (this.getScreenHeight() - CONTENT_Y - 32) / ROW_HEIGHT);
	}

	private int getRowWidth() {
		return Math.min(420, this.getScreenWidth() - 20);
	}

	@Override
	public void initGui() {
		super.initGui();

		int x = 10;
		int y = 26;

		String labelLabel = StringUtils.translate(KEY + "label");
		int labelWidth = this.getStringWidth(labelLabel);
		this.addLabel(x, y + 6, labelWidth, 12, 0xFFFFFFFF, labelLabel);
		x += labelWidth + 6;

		GuiTextFieldGeneric field = new GuiTextFieldGeneric(x, y + 2, 120, 16, this.font);
		field.setValue(this.labelText);
		this.addTextField(field, textField -> {
			this.labelText = textField.getValue();
			this.piece.setLabel(this.labelText.trim());
			TradingSetupManager.getInstance().save();
			return true;
		});
		x += 126;

		String source = StringUtils.translate(KEY + "source", this.piece.getSource().getDisplayName());
		ButtonGeneric sourceButton = Hover.Button.of(x, y, this.getStringWidth(source) + 10, 20, source,
				Reference.MOD_ID + ".gui.trading.source.hover");
		this.addButton(sourceButton, (btn, mouseButton) -> this.change(() -> this.piece.setSource(this.piece.getSource().next())));

		if (this.piece.getSource() == SetupPiece.Source.CRAFT && !CraftingRecipes.isCraftable(this.piece.getItem())) {
			this.addLabel(x + sourceButton.getWidth() + 6, y + 6, 200, 12, 0xFFFFFF55,
					StringUtils.translate(Reference.MOD_ID + ".gui.trading.warning.not_craftable"));
		}

		this.createEnchantmentButtons();

		String done = StringUtils.translate(Reference.MOD_ID + ".gui.trading.done");
		this.addButton(new ButtonGeneric(10, this.getScreenHeight() - 26, this.getStringWidth(done) + 10, 20, done),
				(btn, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	private void createEnchantmentButtons() {
		this.scroll = Math.max(0, Math.min(this.scroll, this.enchantments.size() - this.getVisibleRows()));
		int end = Math.min(this.enchantments.size(), this.scroll + this.getVisibleRows());
		int right = 10 + this.getRowWidth();

		for (int i = this.scroll; i < end; i++) {
			Holder<Enchantment> holder = this.enchantments.get(i);
			String id = Enchants.getId(holder);
			int maxLevel = holder.value().getMaxLevel();
			int current = this.piece.getEnchantments().getOrDefault(id, 0);
			int y = CONTENT_Y + (i - this.scroll) * ROW_HEIGHT + 1;
			int x = right - (maxLevel + 1) * (LEVEL_BUTTON_WIDTH + 2);

			for (int level = 0; level <= maxLevel; level++) {
				int newLevel = level;
				String label = level == 0 ? "-" : toRoman(level);
				ButtonGeneric button = new ButtonGeneric(x, y, LEVEL_BUTTON_WIDTH, 20, label);
				// The selected level is shown as a disabled button
				button.setEnabled(level != current);
				this.addButton(button, (btn, mouseButton) -> this.change(() -> this.setLevel(id, newLevel)));
				x += LEVEL_BUTTON_WIDTH + 2;
			}
		}
	}

	/**
	 * Sets the level, removing enchantments that conflict with it
	 */
	private void setLevel(String id, int level) {
		if (level > 0) {
			this.piece.getEnchantments().keySet().removeIf(other -> !Enchants.areCompatible(id, other));
		}

		this.piece.enchant(id, level);
	}

	private void change(Runnable change) {
		change.run();
		TradingSetupManager.getInstance().save();
		this.initGui();
	}

	private static String toRoman(int level) {
		String[] numerals = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
		return level <= numerals.length ? numerals[level - 1] : String.valueOf(level);
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0 && mouseY >= CONTENT_Y) {
			this.scroll -= (int) Math.signum(verticalAmount);
			this.initGui();
			return true;
		}

		return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// Drawn as part of the background, so that the level buttons render on top of the rows
	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);

		ctx.renderItem(this.piece.getStack(), 10, 52);
		this.drawStringWithShadow(ctx, this.piece.getDisplayName(), 30, 56, 0xFFFFFFFF);

		if (this.enchantments.isEmpty()) {
			this.drawString(ctx, StringUtils.translate(KEY + "none"), 12, CONTENT_Y + 6, 0xFFA0A0A0);
			return;
		}

		int end = Math.min(this.enchantments.size(), this.scroll + this.getVisibleRows());

		for (int i = this.scroll; i < end; i++) {
			Holder<Enchantment> holder = this.enchantments.get(i);
			String id = Enchants.getId(holder);
			int y = CONTENT_Y + (i - this.scroll) * ROW_HEIGHT;
			boolean selected = this.piece.getEnchantments().containsKey(id);
			boolean conflicts = !selected && this.piece.getEnchantments().keySet().stream().anyMatch(other -> !Enchants.areCompatible(id, other));

			RenderUtils.drawRect(ctx, 10, y, this.getRowWidth(), ROW_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);

			int color = selected ? 0xFF55FF55 : conflicts ? 0xFF808080 : 0xFFFFFFFF;
			String name = holder.value().description().getString();
			this.drawStringWithShadow(ctx, name, 14, y + 7, color);

			// Villagers can't sell e.g. Swift Sneak or Soul Speed
			if (!Enchants.isTradeable(id)) {
				this.drawString(ctx, StringUtils.translate(KEY + "not_tradeable"), 20 + this.getStringWidth(name), y + 7, 0xFFFF5555);
			}
			else if (conflicts) {
				this.drawString(ctx, StringUtils.translate(KEY + "conflicts"), 20 + this.getStringWidth(name), y + 7, 0xFF808080);
			}
		}
	}
}
