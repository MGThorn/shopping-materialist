package io.github.mgthorn.shoppingmaterialist.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.locale.Language;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * Tooltips with a short part that is always shown and details that are only shown while Shift is held.
 * The details follow a line that is just {@link #MORE}; without Shift a gray hint replaces them.
 * Colors use the formatting codes ({@link GuiBase#TXT_GRAY}...), sparingly: values aqua, hints gray.
 */
final class Hover {
	private static final String KEY = Reference.MOD_ID + ".gui.hover.";
	/**
	 * The line between the short part and the details
	 */
	static final String MORE = "\u0001";

	private Hover() {
	}

	/**
	 * The translation of {@code key}, followed by the details from {@code key + ".more"} if that exists
	 */
	static String text(String key, Object... args) {
		String text = StringUtils.translate(key, args);
		return Language.getInstance().has(key + ".more") ? text + "\n" + MORE + "\n" + StringUtils.translate(key + ".more", args) : text;
	}

	static List<String> lines(String text) {
		return lines(List.of(text.split("\n")), null);
	}

	static List<String> lines(List<String> raw) {
		return lines(raw, null);
	}

	/**
	 * The lines to draw: the short part with the details while Shift is held, otherwise the short part and a hint
	 * @param click what clicking does, e.g. "show only its sellers", shown in the hint; null if it can't be clicked
	 */
	static List<String> lines(List<String> raw, @Nullable String click) {
		int more = raw.indexOf(MORE);
		List<String> lines = new ArrayList<>(more >= 0 ? raw.subList(0, more) : raw);

		if (more >= 0 && GuiBase.isShiftDown()) {
			raw.subList(more + 1, raw.size()).forEach(line -> lines.add(GuiBase.TXT_GRAY + line));
		}
		else if (more >= 0) {
			lines.add(GuiBase.TXT_GRAY + (click != null ? StringUtils.translate(KEY + "click_shift", click) : StringUtils.translate(KEY + "shift")));
		}

		if (click != null && (more < 0 || GuiBase.isShiftDown())) {
			lines.add(GuiBase.TXT_GRAY + StringUtils.translate(KEY + "click", click));
		}

		return lines;
	}

	/**
	 * A value in aqua, e.g. a count or coordinates
	 */
	static String value(Object value) {
		return GuiBase.TXT_AQUA + value + GuiBase.TXT_RST;
	}

	/**
	 * A malilib button whose tooltip shows its details only while Shift is held
	 */
	static class Button extends ButtonGeneric {
		Button(int x, int y, int width, int height, String label, String hoverText) {
			super(x, y, width, height, label, hoverText);
		}

		/**
		 * @param hoverKey translated with {@link Hover#text}, so a {@code .more} key adds details
		 */
		static Button of(int x, int y, int width, int height, String label, String hoverKey, Object... args) {
			return new Button(x, y, width, height, label, text(hoverKey, args));
		}

		@Override
		public List<String> getHoverStrings() {
			return lines(super.getHoverStrings());
		}
	}
}
