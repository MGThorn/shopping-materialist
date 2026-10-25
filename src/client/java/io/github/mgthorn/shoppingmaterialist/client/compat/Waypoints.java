package io.github.mgthorn.shoppingmaterialist.client.compat;

import java.util.Collection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
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
	 * Wide enough for all its labels, so nothing next to it moves when it changes
	 */
	public static int getButtonWidth() {
		return StringUtils.getMaxStringRenderWidth(getButtonLabels()) + 10;
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
	 * The chat button in the villager browser: closes the screen and opens the chat with the villager as a shared
	 * Xaero's Minimap waypoint. The player sends it with Enter; Xaero's Minimap makes it clickable for everyone who has it.
	 */
	public static void onChatButton(RememberedVillager villager) {
		GuiBase.openGui(new ChatScreen(getXaeroShareText(villager), false));
	}

	/**
	 * The format of Xaero's Minimap's own "Share" button:
	 * {@code xaero-waypoint:<name>:<initials>:<x>:<y>:<z>:<color>:<rotation>:<yaw>:Internal-<dimension>-waypoints},
	 * e.g. "xaero-waypoint:Librarian (level 3):L:120:64:-300:10:false:0:Internal-overworld-waypoints".
	 * Colons in the name are written as "^col^"; color 10 is green.
	 */
	public static String getXaeroShareText(RememberedVillager villager) {
		String name = villager.getDisplayName().replace(":", "^col^");
		String initials = name.isEmpty() ? "V" : name.substring(0, 1).toUpperCase();

		return "xaero-waypoint:" + name + ":" + initials + ":" + villager.pos.getX() + ":" + villager.pos.getY() + ":" + villager.pos.getZ()
				+ ":10:false:0:Internal-" + getXaeroDimension(villager.dimension) + "-waypoints";
	}

	/**
	 * Xaero's folder name of a dimension: "overworld", "the_nether", "the_end", or "dim%namespace$path" for others
	 */
	private static String getXaeroDimension(String dimension) {
		Identifier id = Identifier.tryParse(dimension);

		if (id == null) {
			return "overworld";
		}

		if (id.getNamespace().equals("minecraft") && (id.getPath().equals("overworld") || id.getPath().equals("the_nether") || id.getPath().equals("the_end"))) {
			return id.getPath();
		}

		return "dim%" + id.getNamespace() + "$" + id.getPath().replace("/", "%");
	}

	/**
	 * Whether all these villagers have a waypoint that is shown (false for none, or without JourneyMap)
	 */
	public static boolean areAllShown(Collection<RememberedVillager> villagers) {
		return isJourneyMapLoaded() && !villagers.isEmpty()
				&& villagers.stream().allMatch(villager -> JourneyMapWaypoints.getState(villager) == JourneyMapWaypoints.State.ON);
	}

	/**
	 * The waypoints button of the trading list: hides the waypoints of these villagers if all are shown,
	 * otherwise shows all of them (adding missing ones to the group "Villagers")
	 */
	public static void onToggleAll(Collection<RememberedVillager> villagers, GuiBase gui) {
		if (!isJourneyMapLoaded()) {
			gui.addMessage(MessageType.INFO, KEY + "no_journeymap");
			return;
		}

		boolean show = !areAllShown(villagers);

		for (RememberedVillager villager : villagers) {
			if (!JourneyMapWaypoints.setShown(villager, show)) {
				gui.addMessage(MessageType.WARNING, KEY + "not_ready");
				return;
			}
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
