package io.github.mgthorn.shoppingmaterialist.client.compat.journeymap;

import java.util.Set;
import org.jetbrains.annotations.Nullable;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import io.github.mgthorn.shoppingmaterialist.Reference;
import io.github.mgthorn.shoppingmaterialist.client.trading.RememberedVillager;

/**
 * JourneyMap waypoints for remembered villagers, in the waypoint group "Villagers".
 * Only use this class after checking {@code Waypoints.isJourneyMapLoaded()}, it needs the JourneyMap API classes.
 */
public class JourneyMapWaypoints {
	public static final String GROUP_NAME = "Villagers";
	// Emerald green
	private static final int COLOR = 0x17DD62;

	// The professions JourneyMap has a face icon for; others (and none) use the plain villager face
	private static final Set<String> PROFESSION_ICONS = Set.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
			"fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");

	@Nullable private static IClientAPI api;

	public enum State {
		// The API isn't ready (JourneyMap hasn't initialised its plugins yet)
		NOT_READY,
		// No waypoint at the villager's position
		NONE,
		// The waypoint is shown: it and its group are enabled
		ON,
		OFF
	}

	public enum Result {
		NOT_READY,
		ADDED,
		ENABLED,
		DISABLED
	}

	static void setApi(IClientAPI clientApi) {
		api = clientApi;
	}

	/**
	 * The waypoint at the villager's position (any group, any mod)
	 */
	public static State getState(RememberedVillager villager) {
		if (api == null) {
			return State.NOT_READY;
		}

		Waypoint waypoint = findAt(villager);

		if (waypoint == null) {
			return State.NONE;
		}

		WaypointGroup group = getGroup(waypoint);
		return waypoint.isEnabled() && (group == null || group.isEnabled()) ? State.ON : State.OFF;
	}

	/**
	 * Turns the waypoint at the villager's position off if it's on, on if it's off (also its group),
	 * or adds one to the "Villagers" group if there is none
	 */
	public static Result toggle(RememberedVillager villager) {
		if (api == null) {
			return Result.NOT_READY;
		}

		Waypoint waypoint = findAt(villager);

		if (waypoint == null) {
			waypoint = WaypointFactory.createWaypoint(Reference.MOD_ID, villager.pos, villager.getDisplayName(), getDimension(villager), true);
			waypoint.setColor(COLOR);
			waypoint.setIconIdentifier(getIcon(villager));
			waypoint.setIconTextureSize(16, 16);
			// White, so the face isn't tinted with the waypoint color
			waypoint.setIconColor(0xFFFFFF);
			api.addWaypoint(Reference.MOD_ID, waypoint);
			getOrCreateGroup().addWaypoint(waypoint);
			return Result.ADDED;
		}

		if (getState(villager) == State.ON) {
			waypoint.setEnabled(false);
			return Result.DISABLED;
		}

		waypoint.setEnabled(true);
		WaypointGroup group = getGroup(waypoint);

		if (group != null && !group.isEnabled()) {
			group.setEnabled(true);
		}

		return Result.ENABLED;
	}

	/**
	 * Shows or hides the waypoint at the villager's position, adding one to the "Villagers" group if needed
	 * @return false if the API isn't ready
	 */
	public static boolean setShown(RememberedVillager villager, boolean shown) {
		State state = getState(villager);

		if (state == State.NOT_READY) {
			return false;
		}

		// Toggling adds a missing waypoint, or turns an existing one on or off
		if ((state == State.ON) != shown) {
			toggle(villager);
		}

		return true;
	}

	/**
	 * Turns off every waypoint in the "Villagers" group
	 * @return how many were turned off, or -1 if the API isn't ready
	 */
	public static int disableAll() {
		if (api == null) {
			return -1;
		}

		WaypointGroup group = api.getWaypointGroupByName(Reference.MOD_ID, GROUP_NAME);

		if (group == null) {
			return 0;
		}

		int count = 0;

		for (Waypoint waypoint : api.getWaypoints(Reference.MOD_ID)) {
			if (group.getGuid().equals(waypoint.getGroupId()) && waypoint.isEnabled()) {
				waypoint.setEnabled(false);
				count++;
			}
		}

		return count;
	}

	@Nullable
	private static Waypoint findAt(RememberedVillager villager) {
		for (Waypoint waypoint : api.getAllWaypoints(getDimension(villager))) {
			if (waypoint.getX() == villager.pos.getX() && waypoint.getY() == villager.pos.getY() && waypoint.getZ() == villager.pos.getZ()) {
				return waypoint;
			}
		}

		return null;
	}

	@Nullable
	private static WaypointGroup getGroup(Waypoint waypoint) {
		return waypoint.getGroupId() != null ? api.getWaypointGroup(waypoint.getGroupId()) : null;
	}

	private static WaypointGroup getOrCreateGroup() {
		WaypointGroup group = api.getWaypointGroupByName(Reference.MOD_ID, GROUP_NAME);

		if (group == null) {
			group = WaypointFactory.createWaypointGroup(Reference.MOD_ID, GROUP_NAME);
			api.addWaypointGroup(group);
		}

		return group;
	}

	/**
	 * The villager's face for its profession, from JourneyMap's mob icons, e.g. textures/entity_icon/villager/librarian.png
	 */
	private static Identifier getIcon(RememberedVillager villager) {
		if (RememberedVillager.TYPE_WANDERING_TRADER.equals(villager.type)) {
			return Identifier.withDefaultNamespace("textures/entity_icon/wandering_trader.png");
		}

		Identifier profession = Identifier.tryParse(villager.profession);
		String name = profession != null && PROFESSION_ICONS.contains(profession.getPath()) ? profession.getPath() : "villager";
		return Identifier.withDefaultNamespace("textures/entity_icon/villager/" + name + ".png");
	}

	private static ResourceKey<Level> getDimension(RememberedVillager villager) {
		Identifier id = Identifier.tryParse(villager.dimension);
		return ResourceKey.create(Registries.DIMENSION, id != null ? id : Level.OVERWORLD.identifier());
	}
}
