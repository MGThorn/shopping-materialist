package io.github.mgthorn.shoppingmaterialist.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.compat.journeymap.JourneyMapWaypoints;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedVillager;

/**
 * Map mod waypoints for remembered villagers. JourneyMap is optional: its classes are only touched when it's installed.
 * <p>
 * The waypoint button toggles the waypoint at the villager's position, or adds one to the JourneyMap group "Villagers".
 */
public class Waypoints {
	private static final String KEY = Reference.MOD_ID + ".waypoint.";

	public static boolean isJourneyMapLoaded() {
		return FabricLoader.getInstance().isModLoaded("journeymap");
	}

	/**
	 * "Waypoint: On" (green), "Waypoint: Off" (red), or "Waypoint" if there is none yet
	 */
	public static String getButtonLabel(RememberedVillager villager) {
		JourneyMapWaypoints.State state = isJourneyMapLoaded() ? JourneyMapWaypoints.getState(villager) : JourneyMapWaypoints.State.NONE;

		return switch (state) {
			case ON -> StringUtils.translate(KEY + "button.on");
			case OFF -> StringUtils.translate(KEY + "button.off");
			default -> StringUtils.translate(KEY + "button");
		};
	}

	/**
	 * All labels the button can have, to give it one width
	 */
	public static String[] getButtonLabels() {
		return new String[] { StringUtils.translate(KEY + "button"), StringUtils.translate(KEY + "button.on"), StringUtils.translate(KEY + "button.off") };
	}

	/**
	 * The waypoint button in the villager browser. Turning a waypoint on or off shows no message,
	 * the button label shows the new state; only problems are shown in the open screen.
	 */
	public static void onWaypointButton(RememberedVillager villager, GuiBase gui) {
		if (!isJourneyMapLoaded()) {
			gui.addMessage(MessageType.INFO, KEY + "no_journeymap");
			return;
		}

		if (JourneyMapWaypoints.toggle(villager) == JourneyMapWaypoints.Result.NOT_READY) {
			gui.addMessage(MessageType.WARNING, KEY + "not_ready");
		}
	}

	/**
	 * Turns off all waypoints in the JourneyMap group "Villagers"
	 */
	public static void onDisableAll(GuiBase gui) {
		if (!isJourneyMapLoaded()) {
			gui.addMessage(MessageType.INFO, KEY + "no_journeymap");
			return;
		}

		int count = JourneyMapWaypoints.disableAll();

		if (count < 0) {
			gui.addMessage(MessageType.WARNING, KEY + "not_ready");
		}
		else {
			gui.addMessage(MessageType.INFO, KEY + "disabled_all", count, JourneyMapWaypoints.GROUP_NAME);
		}
	}
}
