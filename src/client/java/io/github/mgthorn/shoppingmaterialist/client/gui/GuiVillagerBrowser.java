package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedOffer;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedVillager;
import io.github.mgthorn.shoppingmaterialist.client.trading.VillagerMemory;
import io.github.mgthorn.shoppingmaterialist.client.trading.WorldKey;

/**
 * Lists every remembered villager of the current world, nearest first, with its position.
 * Hovering a row shows its trades; dead or moved villagers can be deleted.
 */
public class GuiVillagerBrowser extends GuiBase {
	private static final String KEY = Reference.MOD_ID + ".gui.trading.villager_browser.";
	private static final int CONTENT_Y = 50;
	private static final int ROW_HEIGHT = 22;

	private List<RememberedVillager> villagers = new ArrayList<>();
	private int scroll;

	public GuiVillagerBrowser(@Nullable Screen parent) {
		this.title = StringUtils.translate(Reference.MOD_ID + ".gui.title.villager_browser");
		this.setParent(parent);
	}

	private int getVisibleRows() {
		return Math.max(1, (this.getScreenHeight() - CONTENT_Y - 32) / ROW_HEIGHT);
	}

	@Override
	public void initGui() {
		super.initGui();

		this.villagers = new ArrayList<>(VillagerMemory.getInstance().getVillagers());
		this.villagers.sort(Comparator.comparingInt(villager -> villager.getDistance() >= 0 ? villager.getDistance() : Integer.MAX_VALUE));

		this.scroll = Math.max(0, Math.min(this.scroll, this.villagers.size() - this.getVisibleRows()));
		int end = Math.min(this.villagers.size(), this.scroll + this.getVisibleRows());
		String delete = StringUtils.translate(KEY + "delete");
		int deleteWidth = this.getStringWidth(delete) + 10;

		for (int i = this.scroll; i < end; i++) {
			RememberedVillager villager = this.villagers.get(i);
			int y = CONTENT_Y + (i - this.scroll) * ROW_HEIGHT + 1;
			this.addButton(new ButtonGeneric(this.getScreenWidth() - 12 - deleteWidth, y, deleteWidth, 20, delete),
					(btn, mouseButton) -> {
						VillagerMemory.getInstance().remove(villager);
						this.initGui();
					});
		}

		int y = this.getScreenHeight() - 26;
		String back = StringUtils.translate(Reference.MOD_ID + ".gui.trading.done");
		ButtonGeneric backButton = new ButtonGeneric(10, y, this.getStringWidth(back) + 10, 20, back);
		this.addButton(backButton, (btn, mouseButton) -> GuiBase.openGui(this.getParent()));

		String clear = StringUtils.translate(KEY + "delete_all");
		ButtonGeneric clearButton = new ButtonGeneric(10 + backButton.getWidth() + 4, y, this.getStringWidth(clear) + 10, 20, clear);
		clearButton.setEnabled(!this.villagers.isEmpty());
		this.addButton(clearButton, (btn, mouseButton) -> this.confirmClear());
	}

	private void confirmClear() {
		IConfirmationListener listener = new IConfirmationListener() {
			@Override
			public boolean onActionConfirmed() {
				VillagerMemory.getInstance().clear();
				return true;
			}

			@Override
			public boolean onActionCancelled() {
				return true;
			}
		};

		GuiBase.openGui(new GuiConfirmAction(320, KEY + "delete_all_title", listener, this, KEY + "delete_all_message",
				String.valueOf(WorldKey.getCurrent())));
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

	// Drawn as part of the background, so that the row buttons render on top of the rows
	@Override
	protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
		super.drawScreenBackground(ctx, mouseX, mouseY);

		String world = StringUtils.translate(KEY + "world", String.valueOf(WorldKey.getCurrent()), this.villagers.size());
		this.drawString(ctx, world, 12, 32, 0xFFA0A0A0);

		if (this.villagers.isEmpty()) {
			this.drawString(ctx, StringUtils.translate(KEY + "empty"), 12, CONTENT_Y + 6, 0xFFA0A0A0);
			return;
		}

		int end = Math.min(this.villagers.size(), this.scroll + this.getVisibleRows());
		int width = this.getScreenWidth() - 20;

		for (int i = this.scroll; i < end; i++) {
			RememberedVillager villager = this.villagers.get(i);
			int y = CONTENT_Y + (i - this.scroll) * ROW_HEIGHT;
			RenderUtils.drawRect(ctx, 10, y, width, ROW_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);

			String name = villager.getDisplayName();
			String info = StringUtils.translate(KEY + "info", villager.offers.size(), formatAge(villager.lastSeen));
			this.drawStringWithShadow(ctx, name, 14, y + 7, 0xFFFFFFFF);
			this.drawStringWithShadow(ctx, villager.getLocationText(), 160, y + 7, 0xFF55FFFF);
			this.drawString(ctx, info, 320, y + 7, 0xFFA0A0A0);
		}
	}

	@Override
	protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		int row = (int) Math.floor((double) (mouseY - CONTENT_Y) / ROW_HEIGHT);
		int index = this.scroll + row;

		// The trades of the hovered villager (not while hovering its delete button)
		if (mouseY >= CONTENT_Y && row < this.getVisibleRows() && index < this.villagers.size() && mouseX < this.getScreenWidth() - 80) {
			RenderUtils.drawHoverText(ctx, mouseX, mouseY, getOfferLines(this.villagers.get(index)));
		}
	}

	private static List<String> getOfferLines(RememberedVillager villager) {
		List<String> lines = new ArrayList<>();
		lines.add(villager.getDisplayName());

		for (RememberedOffer offer : villager.offers) {
			String result = offer.resultCount + " " + new ItemStack(offer.result).getHoverName().getString();

			if (!offer.enchantments.isEmpty()) {
				List<String> names = new ArrayList<>();

				for (Map.Entry<String, Integer> entry : offer.enchantments.entrySet()) {
					names.add(Enchants.getName(entry.getKey(), entry.getValue()));
				}

				result += " (" + String.join(", ", names) + ")";
			}

			String uses = StringUtils.translate(KEY + "uses", offer.uses, offer.maxUses);
			lines.add(GuiTradingList.formatCost(offer) + " -> " + result + " " + uses);
		}

		return lines;
	}

	private static String formatAge(long time) {
		long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60000);

		if (minutes < 60) {
			return StringUtils.translate(KEY + "age.minutes", minutes);
		}

		long hours = minutes / 60;
		return hours < 48 ? StringUtils.translate(KEY + "age.hours", hours) : StringUtils.translate(KEY + "age.days", hours / 24);
	}
}
