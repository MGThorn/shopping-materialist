package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.nio.file.Path;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Identifies the world the player is in, so remembered villagers are kept apart per world.
 * Multiplayer: the server address. Singleplayer: the save folder name.
 * The dimension is stored with each villager.
 */
public class WorldKey {
	/**
	 * @return e.g. {@code mp_play.example.com_25565} or {@code sp_New_World}, or null outside of a world
	 */
	@Nullable
	public static String getCurrent() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			return null;
		}

		IntegratedServer server = mc.getSingleplayerServer();

		if (server != null) {
			// The save folder, e.g. saves/New World
			Path worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			return "sp_" + sanitize(String.valueOf(worldDir.getFileName()));
		}

		ServerData serverData = mc.getCurrentServer();
		return serverData != null ? "mp_" + sanitize(serverData.ip) : null;
	}

	@Nullable
	public static String getCurrentDimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.dimension().identifier().toString() : null;
	}

	private static String sanitize(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-.]", "_");
	}
}
