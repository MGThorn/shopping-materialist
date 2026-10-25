package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.trading.Enchants;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedOffer;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedVillager;
import io.github.mgthorn.shoppingmaterialist.client.trading.TradingCalculator.MissingEnchantment;

/**
 * The base of the trading list screens: a scrollable panel of lines made of text, item icons and experience orbs.
 * A hovered icon shows its name, a hovered line its tooltip. Lines can be expandable or have two columns.
 */
abstract class GuiTradingLines extends GuiBase {
	static final String KEY = Reference.MOD_ID + ".gui.trading.";
	static final int TEXT_LINE_HEIGHT = 11;
	static final int ICON_LINE_HEIGHT = 18;
	static final int CHECKBOX_SIZE = 9;
	private static final Identifier XP_ORB_TEXTURE = Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png");
	private static final int XP_ORB_COLOR = 0xFFB0FF40;

	static final int WHITE = 0xFFFFFFFF;
	static final int GRAY = 0xFFA0A0A0;
	static final int GREEN = 0xFF55FF55;
	static final int RED = 0xFFFF5555;
	static final int YELLOW = 0xFFFFFF55;
	static final int GOLD = 0xFFFFAA00;
	static final int AQUA = 0xFF55FFFF;

	/**
	 * One line of the panel, made of text, item icons and experience orbs
	 * @param toggle what this line expands, null if it can't be expanded
	 * @param tooltip shown when the line (or its left column) is hovered; a hovered icon shows its own tooltip instead
	 * @param right the right column, null for one-column lines
	 */
	record Line(int indent, @Nullable Object toggle, List<Seg> segs, @Nullable String tooltip,
			@Nullable List<Seg> right, @Nullable String rightTooltip) {
		Line(int indent, @Nullable Object toggle, List<Seg> segs, @Nullable String tooltip) {
			this(indent, toggle, segs, tooltip, null, null);
		}

		int height() {
			boolean icons = hasIcon(this.segs) || (this.right != null && hasIcon(this.right));
			return icons ? ICON_LINE_HEIGHT : TEXT_LINE_HEIGHT;
		}

		private static boolean hasIcon(List<Seg> segs) {
			return segs.stream().anyMatch(seg -> seg.text() == null);
		}
	}

	/**
	 * One cell of a two-column line
	 * @param villager the villager the cell's trade comes from, null if none
	 */
	record Cell(Segs segs, @Nullable String tooltip, @Nullable RememberedVillager villager) {
		Cell(Segs segs, @Nullable String tooltip) {
			this(segs, tooltip, null);
		}
	}

	/**
	 * A piece of a line: text (when {@code text} is set), an item icon, or the experience orb
	 * @param count drawn on the icon like an inventory slot, if above 1
	 * @param width the least width of a text, so the following pieces line up between lines
	 */
	record Seg(@Nullable String text, int color, @Nullable ItemStack icon, boolean xp, @Nullable String tooltip,
			@Nullable MissingEnchantment missing, int count, int width) {
	}

	/**
	 * Where a clickable icon was drawn in the last frame
	 */
	record HitBox(int x, int y, int width, int height, MissingEnchantment missing) {
	}

	/**
	 * Builds the pieces of a line
	 */
	static class Segs {
		final List<Seg> list = new ArrayList<>();

		Segs text(String text, int color) {
			return this.text(text, color, 0);
		}

		/**
		 * Text taking at least {@code width}, so what follows starts at the same place on every line
		 */
		Segs text(String text, int color, int width) {
			this.list.add(new Seg(text, color, null, false, null, null, 0, width));
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
			return this.item(stack, tooltip, 1);
		}

		/**
		 * An icon with the count drawn on it like in an inventory slot
		 */
		Segs item(ItemStack stack, String tooltip, int count) {
			this.list.add(new Seg(null, 0, stack, false, tooltip, null, count, 0));
			return this;
		}

		/**
		 * A book icon of a missing enchantment that can be clicked to exclude it
		 */
		Segs missing(MissingEnchantment missing) {
			String name = Enchants.getName(missing.id(), missing.level());
			String tooltip = name + "\n" + StringUtils.translate(KEY + (missing.excluded() ? "missing.excluded" : "missing.included"));
			this.list.add(new Seg(null, 0, Enchants.createBook(missing.id(), missing.level()), false, tooltip, missing, 1, 0));
			return this;
		}

		/**
		 * "[xp] 21 lvl"
		 */
		Segs levels(int levels, boolean tooExpensive) {
			this.list.add(new Seg(null, 0, null, true, StringUtils.translate(KEY + "levels.hover"), null, 1, 0));
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

	protected final List<Line> lines = new ArrayList<>();
	protected final List<HitBox> hitBoxes = new ArrayList<>();
	protected int lineScroll;

	// ---- Layout of the line panel ----

	protected abstract int getLinesX();

	protected abstract int getLinesY();

	protected abstract int getLinesWidth();

	protected abstract int getLinesHeight();

	/**
	 * Space kept free at the right end of the left column of two-column lines, e.g. for buttons
	 */
	protected int getLeftColumnReserve() {
		return 0;
	}

	/**
	 * The background color of a line, e.g. for cards; 0 for none
	 */
	protected int getLineBackground(int index) {
		return 0;
	}

	/**
	 * Whether an expandable line is expanded; shown as "-" or "+" in front of it
	 */
	protected boolean isExpanded(Object toggle) {
		return false;
	}

	static boolean isInWorld() {
		return Minecraft.getInstance().level != null;
	}

	// ---- Building lines ----

	protected void addText(String text, int color, int indent) {
		this.lines.add(new Line(indent, null, new Segs().text(text, color).list, null));
	}

	protected void addLine(int indent, Segs segs, @Nullable String tooltip) {
		this.lines.add(new Line(indent, null, segs.list, tooltip));
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
	static String formatEmeralds(int known, int estimateMin, int estimateMax) {
		if (estimateMax <= 0) {
			return String.valueOf(known);
		}

		String estimate = "[" + estimateMin + "~" + estimateMax + "]";
		return known > 0 ? known + " + " + estimate : estimate;
	}

	// ---- Scrolling ----

	/**
	 * The last scroll position that still fills the panel
	 */
	protected int getMaxLineScroll() {
		int height = 0;

		for (int i = this.lines.size() - 1; i >= 0; i--) {
			height += this.lines.get(i).height();

			if (height > this.getLinesHeight()) {
				return i + 1;
			}
		}

		return 0;
	}

	protected void clampLineScroll() {
		this.lineScroll = Math.max(0, Math.min(this.lineScroll, this.getMaxLineScroll()));
	}

	/**
	 * Scrolls 3 lines per mouse wheel notch
	 */
	protected void scrollLines(int delta) {
		this.lineScroll += delta * 3;
		this.clampLineScroll();
	}

	/**
	 * @return the index of the line at this position, or -1
	 */
	protected int getLineAt(double mouseX, double mouseY) {
		int top = this.getLinesY();

		if (mouseX < this.getLinesX() || mouseX >= this.getLinesX() + this.getLinesWidth() || mouseY < top) {
			return -1;
		}

		int y = top;

		for (int i = this.lineScroll; i < this.lines.size(); i++) {
			int height = this.lines.get(i).height();

			if (y + height > top + this.getLinesHeight()) {
				break;
			}

			if (mouseY < y + height) {
				return i;
			}

			y += height;
		}

		return -1;
	}

	/**
	 * @return the top of the line if it is shown at the current scroll position, or -1
	 */
	protected int getVisibleLineY(int index) {
		int y = this.getLinesY();
		int bottom = y + this.getLinesHeight();

		for (int i = this.lineScroll; i < this.lines.size(); i++) {
			int height = this.lines.get(i).height();

			if (y + height > bottom) {
				return -1;
			}

			if (i == index) {
				return y;
			}

			y += height;
		}

		return -1;
	}

	// ---- Rendering ----

	/**
	 * Draws the visible lines, the scroll position above the panel, and the tooltip of what is hovered
	 */
	protected void drawLines(GuiContext ctx, int mouseX, int mouseY) {
		int x = this.getLinesX();
		int top = this.getLinesY();
		int right = x + this.getLinesWidth();
		int bottom = top + this.getLinesHeight();
		int hovered = this.getLineAt(mouseX, mouseY);
		String hoverText = null;
		int y = top;
		int lastShown = this.lineScroll;
		this.hitBoxes.clear();

		for (int i = this.lineScroll; i < this.lines.size(); i++) {
			Line line = this.lines.get(i);

			if (y + line.height() > bottom) {
				break;
			}

			lastShown = i;
			int background = this.getLineBackground(i);

			if (background != 0) {
				RenderUtils.drawRect(ctx, x - 2, y, right - x + 4, line.height(), background);
			}

			if (i == hovered && line.toggle() != null) {
				RenderUtils.drawRect(ctx, x - 1, y, right - x + 2, line.height(), 0x30FFFFFF);
			}

			int segX = x + line.indent() * 8;
			int textY = y + (line.height() - 8) / 2;

			if (line.toggle() != null) {
				this.drawStringWithShadow(ctx, this.isExpanded(line.toggle()) ? "-" : "+", segX, textY, GOLD);
				segX += 8;
			}

			if (line.right() == null) {
				SegHover hover = this.drawSegs(ctx, line.segs(), segX, right, y, line.height(), i == hovered ? mouseX : -1);

				if (i == hovered) {
					hoverText = hover.tooltip() != null ? hover.tooltip() : line.tooltip() != null ? line.tooltip() : hover.cutOffText();
				}
			}
			else {
				// Two columns: the right one starts in the middle of the panel
				int middle = x + this.getLinesWidth() / 2;
				int leftEnd = middle - 6 - this.getLeftColumnReserve();
				SegHover leftHover = this.drawSegs(ctx, line.segs(), segX, leftEnd, y, line.height(), i == hovered && mouseX < middle ? mouseX : -1);
				SegHover rightHover = this.drawSegs(ctx, line.right(), middle, right, y, line.height(), i == hovered && mouseX >= middle ? mouseX : -1);

				// The reserved space has its own widgets with their own tooltips
				if (i == hovered && (mouseX < leftEnd || mouseX >= middle)) {
					SegHover hover = mouseX < middle ? leftHover : rightHover;
					String cellTooltip = mouseX < middle ? line.tooltip() : line.rightTooltip();
					hoverText = hover.tooltip() != null ? hover.tooltip() : cellTooltip != null ? cellTooltip : hover.cutOffText();
				}
			}

			y += line.height();
		}

		if (this.lineScroll > 0 || lastShown < this.lines.size() - 1) {
			String scroll = StringUtils.translate(KEY + "scroll", this.lineScroll + 1, lastShown + 1, this.lines.size());
			this.drawString(ctx, scroll, right - this.getStringWidth(scroll), top - 12, GRAY);
		}

		// The name of a hovered icon, the line's tooltip, or the full text of a cut off line
		if (hoverText != null) {
			RenderUtils.drawHoverText(ctx, mouseX, mouseY, Hover.lines(hoverText));
		}
	}

	/**
	 * @param tooltip the tooltip of the hovered icon
	 * @param cutOffText the full text if the segments didn't fit
	 */
	record SegHover(@Nullable String tooltip, @Nullable String cutOffText) {
	}

	/**
	 * Draws the segments from x to xRight, cutting off what doesn't fit. Text is centered on the line height.
	 * @param mouseX the mouse x if this line is hovered, -1 otherwise
	 */
	protected SegHover drawSegs(GuiContext ctx, List<Seg> segs, int x, int xRight, int y, int lineHeight, int mouseX) {
		int textY = y + (lineHeight - 8) / 2;
		String tooltip = null;
		boolean cutOff = false;

		for (Seg seg : segs) {
			if (seg.text() != null) {
				String text = this.trimToWidth(seg.text(), xRight - x);
				this.drawStringWithShadow(ctx, text, x, textY, seg.color());
				x += Math.max(seg.width(), this.getStringWidth(text));

				if (!text.equals(seg.text())) {
					cutOff = true;
					break;
				}
			}
			else {
				if (x + 16 > xRight) {
					cutOff = true;
					break;
				}

				if (seg.xp()) {
					drawXpOrb(ctx, x, y + 1);
				}
				else if (seg.icon() != null) {
					ctx.renderItem(seg.icon(), x, y + 1);

					if (seg.count() > 1) {
						ctx.renderItemDecorations(this.font, seg.icon().copyWithCount(seg.count()), x, y + 1);
					}
				}

				if (seg.missing() != null) {
					this.hitBoxes.add(new HitBox(x, y, 16, ICON_LINE_HEIGHT, seg.missing()));

					if (seg.missing().excluded()) {
						drawExcludedSign(ctx, x, y + 1);
					}
				}

				if (mouseX >= x && mouseX < x + 16) {
					tooltip = seg.tooltip();
				}

				x += 16;
			}
		}

		String cutOffText = null;

		if (cutOff) {
			StringBuilder text = new StringBuilder();
			segs.stream().filter(seg -> seg.text() != null).forEach(seg -> text.append(seg.text()));
			cutOffText = text.toString();
		}

		return new SegHover(tooltip, cutOffText);
	}

	/**
	 * A small outlined box, with a green check mark when checked
	 */
	static void drawCheckbox(GuiContext ctx, int x, int y, boolean checked) {
		RenderUtils.drawOutline(ctx, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE, checked ? 0xFFC0C0C0 : 0xFF707070);

		if (!checked) {
			return;
		}

		int[][] pixels = { { 2, 4 }, { 3, 5 }, { 4, 6 }, { 5, 5 }, { 6, 4 }, { 7, 3 }, { 7, 2 }, { 2, 5 }, { 3, 6 }, { 4, 7 }, { 5, 6 }, { 6, 5 } };

		for (int[] p : pixels) {
			RenderUtils.drawRect(ctx, x + p[0] - 1, y + p[1] - 1, 1, 1, GREEN);
		}
	}

	/**
	 * A red circle with a diagonal line over a 16 x 16 icon, for excluded enchantments
	 */
	private static void drawExcludedSign(GuiContext ctx, int x, int y) {
		int color = 0xFFFF2020;

		for (int py = 0; py < 16; py++) {
			for (int px = 0; px < 16; px++) {
				double dx = px - 7.5;
				double dy = py - 7.5;
				double distance = Math.sqrt(dx * dx + dy * dy);
				// The ring, and the line from top left to bottom right inside it
				boolean ring = distance >= 6.0 && distance <= 7.6;
				boolean line = distance < 6.0 && Math.abs(dx - dy) <= 1.0;

				if (ring || line) {
					RenderUtils.drawRect(ctx, x + px, y + py, 1, 1, color);
				}
			}
		}
	}

	/**
	 * One frame of the experience orb texture at half size, centered in the 16 x 16 icon space.
	 * The texture is gray, so it is tinted green like the orbs in the world.
	 */
	private static void drawXpOrb(GuiContext ctx, int x, int y) {
		ctx.blit(RenderPipelines.GUI_TEXTURED, XP_ORB_TEXTURE, x + 4, y + 4, 0, 32, 8, 8, 16, 16, 64, 64, XP_ORB_COLOR);
	}

	protected String trimToWidth(String text, int maxWidth) {
		if (this.getStringWidth(text) <= maxWidth) {
			return text;
		}

		while (!text.isEmpty() && this.getStringWidth(text + "...") > maxWidth) {
			text = text.substring(0, text.length() - 1);
		}

		return text + "...";
	}
}
