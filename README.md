# Shopping Materialist

A client-side Fabric mod that adds **shopping lists** to [Litematica](https://github.com/sakura-ryoko/litematica).

A shopping list works like a Litematica material list, but you build it by picking items yourself instead of selecting an area. It is shown in Litematica's own material list screen and info HUD, so everything you already know from material lists works the same way.

## Features

- **Multiple named lists:** create, open, edit, rename and delete lists in the list browser.
- **Item picker editor:** search all items and click to add them.

  | Input | Effect |
  |---|---|
  | Left-click / `+` | Add 1 |
  | Right-click / `-` | Remove 1 |
  | Shift + click | A full stack |
  | Ctrl + click | A custom amount (default 8, range 2–256) |
  | Ctrl + mouse wheel | Change the custom amount |

- **Litematica integration:**
  - Litematica's **M + L** hotkey reopens the last viewed list, including shopping lists.
  - Every Litematica material list gets an **Edit items** button. On a schematic placement or area analysis it creates a shopping list from that material list. If the name is already taken, hold Shift to overwrite it, or enable `materialListNewList` to always create a new list (`name_2`, `name_3`...) instead.

- **Wandering trader shopping list** (on by default, turn off with `wanderingTraderButton` in the config screen): the wandering trader's trading screen gets a **Shopping list** button below its bottom-left corner. It fills the `Wandering Trader` list with everything needed for all trades that are not sold out yet, both cost items times the uses left, and shows that list in Litematica's info HUD. This is useful with datapacks like Vanilla Tweaks' *Wandering Trades*, where every mini block costs an emerald plus the full block.
- **Villager shopping list** (off by default, enable `villagerButton`): the same button for villagers, filling the `Villager` list. The villager's current prices are used, so discounts and price increases are included.
- By default both buttons overwrite their list. Enable `wanderingTraderNewList` / `villagerNewList` to create a new numbered list each time instead (`Villager`, `Villager_2`, `Villager_3`...).

### Trading lists

Plan the gear you want to trade for, and the mod works out what it costs:

- **Setups:** a setup is a list of items with the enchantments you want on them. The default setup is full diamond armor, a sword, a Silk Touch and a Fortune pickaxe, an axe, a shovel and a Silk Touch and a Fortune hoe, all with the best enchantments villagers sell. Add, remove and edit items, and save as many setups as you like.
- **Item source:** each item is either **crafted** (adds diamonds and sticks), **bought** from the cheapest remembered smith (its random enchantments are used if they help), or one you **have** already.
- **Villager memory:** every villager and wandering trader whose trading screen you open is remembered per world (server address or save name), with its trades, profession, level and position. Turn this off with `rememberVillagers`.
- **Calculation:** for every book the cheapest remembered librarian is picked (the nearer one if prices are equal). Books no remembered villager sells are shown in red with the vanilla price range. The result shows the emeralds, everything to bring, the villagers to visit with their coordinates, and the anvil order that costs the fewest levels, with a warning for steps of 40 levels or more ("Too Expensive!").
- **Export:** puts everything to bring into the shopping list `Trading: <setup>` and opens it, so it shows in Litematica's info HUD.
- **Coordinates to chat:** writes the villagers to visit into your own chat. Click a line to copy its coordinates.
- **Villager browser:** lists the remembered villagers of the world with their trades. Delete villagers that died or moved.

Prices and positions are the ones from the last time you opened a villager's trading screen. Open it again to update them.

## Hotkeys

| Hotkey | Default |
|---|---|
| Open the main menu | A + C |
| Open the config screen | not set |
| Open the shopping list browser | not set |
| Open the trading lists | not set |

You can change all but the first in the config screen. The mod also appears in MaLiLib's mod dropdown.

## Storage

Everything is stored on your own computer, including when you play on a server:

- Settings: `config/shopping-materialist.json`
- Lists: `config/shopping-materialist/shopping_lists/*.json`
- Trading setups: `config/shopping-materialist/trading_setups.json`
- Remembered villagers: `config/shopping-materialist/villagers/<world>.json`, one file per server or singleplayer world

The same lists and setups are available in every world and on every server.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- MaLiLib, Litematica, MiniHUD and Tweakeroo

## Building

```
./gradlew build
```

The mod jar ends up in `build/libs/`.

## License

[LGPL-3.0](LICENSE), the same license as MaLiLib and Litematica.
