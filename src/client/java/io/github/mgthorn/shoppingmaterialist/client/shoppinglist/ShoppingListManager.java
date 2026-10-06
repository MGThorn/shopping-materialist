package io.github.mgthorn.shoppingmaterialist.client.shoppinglist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonElement;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import fi.dy.masa.litematica.data.DataManager;
import io.github.mgthorn.shoppingmaterialist.ShoppingMaterialist;
import io.github.mgthorn.shoppingmaterialist.Reference;

/**
 * Holds all shopping lists. Each list is stored in its own file in
 * {@code config/shopping-materialist/shopping_lists/}, in Litematica's custom material list format.
 */
public class ShoppingListManager {
	private static final ShoppingListManager INSTANCE = new ShoppingListManager();
	private static final String FILE_EXTENSION = ".json";

	private final List<ShoppingList> lists = new ArrayList<>();
	private boolean loaded;

	public static ShoppingListManager getInstance() {
		return INSTANCE;
	}

	public Path getDirectory() {
		return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("shopping_lists");
	}

	public List<ShoppingList> getLists() {
		this.ensureLoaded();
		return Collections.unmodifiableList(this.lists);
	}

	@Nullable
	public ShoppingList getByName(String name) {
		for (ShoppingList list : this.getLists()) {
			if (list.getName().equalsIgnoreCase(name)) {
				return list;
			}
		}

		return null;
	}

	/**
	 * @return the new list, or null if a list with that name already exists
	 */
	@Nullable
	public ShoppingList create(String name) {
		this.ensureLoaded();

		if (this.getByName(name) != null) {
			return null;
		}

		ShoppingList list = new ShoppingList(name, this.createUniqueFileName(name));
		this.lists.add(list);
		this.sort();
		this.save(list);

		return list;
	}

	/**
	 * @return false if another list already uses the new name
	 */
	public boolean rename(ShoppingList list, String newName) {
		ShoppingList existing = this.getByName(newName);

		if (existing != null && existing != list) {
			return false;
		}

		list.setName(newName);
		this.sort();
		list.onChanged();

		return true;
	}

	public void delete(ShoppingList list) {
		this.lists.remove(list);

		// Don't let Litematica's material list hotkey reopen a deleted list
		ShoppingMaterialList materialList = list.getMaterialListIfCreated();

		if (materialList != null && DataManager.getMaterialList() == materialList) {
			DataManager.setMaterialList(null);
		}

		try {
			Files.deleteIfExists(this.getDirectory().resolve(list.getFileName()));
		}
		catch (IOException e) {
			ShoppingMaterialist.LOGGER.error("Failed to delete shopping list file '{}'", list.getFileName(), e);
		}
	}

	public void save(ShoppingList list) {
		try {
			Files.createDirectories(this.getDirectory());
		}
		catch (IOException e) {
			ShoppingMaterialist.LOGGER.error("Failed to create the shopping list directory", e);
			return;
		}

		JsonUtils.writeJsonToFile(list.toJson(), this.getDirectory().resolve(list.getFileName()));
	}

	private void ensureLoaded() {
		if (!this.loaded) {
			this.loaded = true;
			this.load();
		}
	}

	private void load() {
		this.lists.clear();
		Path dir = this.getDirectory();

		if (!Files.isDirectory(dir)) {
			return;
		}

		try (Stream<Path> files = Files.list(dir)) {
			files.filter(file -> file.getFileName().toString().endsWith(FILE_EXTENSION)).forEach(file -> {
				JsonElement element = JsonUtils.parseJsonFile(file);

				if (element != null && element.isJsonObject()) {
					this.lists.add(ShoppingList.fromJson(element.getAsJsonObject(), file.getFileName().toString()));
				}
				else {
					ShoppingMaterialist.LOGGER.warn("Skipping invalid shopping list file '{}'", file);
				}
			});
		}
		catch (IOException e) {
			ShoppingMaterialist.LOGGER.error("Failed to read the shopping list directory", e);
		}

		this.sort();
	}

	private void sort() {
		this.lists.sort(Comparator.comparing(ShoppingList::getName, String.CASE_INSENSITIVE_ORDER));
	}

	private String createUniqueFileName(String name) {
		String base = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
		String fileName = base + FILE_EXTENSION;
		int i = 2;

		while (this.isFileNameUsed(fileName)) {
			fileName = base + "_" + i++ + FILE_EXTENSION;
		}

		return fileName;
	}

	private boolean isFileNameUsed(String fileName) {
		for (ShoppingList list : this.lists) {
			if (list.getFileName().equalsIgnoreCase(fileName)) {
				return true;
			}
		}

		return Files.exists(this.getDirectory().resolve(fileName));
	}
}
