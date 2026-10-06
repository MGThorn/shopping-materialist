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
  - Every Litematica material list gets an **Edit items** button. On a schematic placement or area analysis it creates a shopping list from that material list. If the name is already taken, hold Shift to overwrite it.

## Hotkeys

| Hotkey | Default |
|---|---|
| Open the main menu | A + C |
| Open the config screen | not set |
| Open the shopping list browser | not set |

You can change the last two in the config screen. The mod also appears in MaLiLib's mod dropdown.

## Storage

Everything is stored on your own computer, including when you play on a server:

- Settings: `config/shopping-materialist.json`
- Lists: `config/shopping-materialist/shopping_lists/*.json`

The same lists are available in every world and on every server.

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
