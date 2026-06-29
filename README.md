# Tarkov Inventory Mod — Minecraft 1.20.1 Forge

A Minecraft 1.20.1 Forge mod that adds a full **Escape from Tarkov**-style character inventory screen with grid-based item storage, equipment slots, item search, and multi-mod compatibility.

---

## Features

### Tarkov-Style Character Screen
- **Left panel** — Full character equipment layout:
  - EARPIECE, HEADWEAR, FACE COVER
  - ARMBAND, BODY ARMOR, EYEWEAR
  - ON SLING, HOLSTER (weapon slots)
  - ON BACK, SCABBARD (weapon slots)
  - Real-time stats bar (item count, health, food, saturation)

- **Right panel** — Grid containers:
  - **POCKETS** — 4 quick-access slots
  - **BACKPACK** — 10×12 Tarkov-style grid inventory
  - **POUCH** — 3 secure container slots

- **Live search** — Click "SEARCH" in the backpack section and type to filter items by name (non-matching items are dimmed)

- **Drag & drop** — Pick up items with left-click, place them anywhere
- **Right-click** while dragging to **rotate** multi-cell items

### Grid Item Sizes
Items automatically occupy the correct grid cell count:
| Category | Grid Size |
|---|---|
| Sword / knife | 1×3 |
| Bow / crossbow | 2×3–4 |
| Axe / pickaxe | 2×3 |
| Trident | 1×4 |
| Shield | 2×3 |
| Helmet | 2×2 |
| Chestplate | 3×2 |
| Leggings | 2×3 |
| Boots | 2×2 |
| Bucket | 2×2 |

### Durability Bars
All items with durability show a colour-coded condition bar:
- 🟢 Green — 60%+ condition
- 🟡 Yellow — 30–60%
- 🔴 Red — below 30%

---

## Mod Compatibility

### Curios API ✅
If **Curios API** (`curios-forge:1.20.1-5.3.5+`) is installed, a **CURIOS** row appears at the bottom of the character panel showing all equipped curio slots. Fully interactive — click to swap items.

### TACZ (Timeless and Classics Zero) ✅
If **TACZ** is installed, all firearms are automatically registered with realistic Tarkov grid sizes:

| TACZ Firearm Type | Grid Size |
|---|---|
| Pistol | 1×2 |
| SMG | 2×4 |
| Shotgun | 2×5 |
| Assault Rifle | 2×6 |
| Sniper / DMR | 2×8 |
| LMG | 3×6 |
| Magazine | 1×2 |

Detection is automatic — no configuration needed.

### Sophisticated Backpacks / Traveler's Backpack / Iron Backpacks ✅
Items from these mods are detected and labelled when placed in equipment slots, indicating their origin mod. Compatible with being stored in the grid.

---

## Crafting Recipe

```
L  L  L
S  C  S
L  L  L
```
- **L** = Leather
- **S** = String
- **C** = Chest

Crafts 1× Tactical Backpack.

---

## Usage

1. Craft or obtain a **Tactical Backpack**
2. **Right-click** with it in hand to open the full character screen
3. **Drag items** from your player inventory into the grid, pockets, or pouch
4. **Right-click** while dragging to rotate multi-cell items
5. Click **SEARCH** and type to filter the backpack grid
6. **ESC** closes the screen (or closes search/cancels drag first)

All items are saved in the backpack's NBT — content persists across sessions, deaths, and server restarts.

---

## Setup (Development)

### Requirements
- JDK 17
- Forge MDK 1.20.1-47.2.0

### Build
```bash
./gradlew build
```
Output: `build/libs/tarkov-inventory-1.0.0.jar`

### Run client
```bash
./gradlew runClient
```

### Optional dependencies (place in `run/mods/`)
- `curios-forge-1.20.1-5.3.5+.jar`
- Any TACZ release for 1.20.1
- Sophisticated Backpacks / Traveler's Backpack / Iron Backpacks

---

## File Structure

```
src/main/java/com/tarkovinventory/
  TarkovInventoryMod.java           — @Mod entry point
  capability/
    IPlayerEquipment.java           — Interface for custom eq slots
    PlayerEquipmentCapability.java  — Implementation (NBT serialized)
    ModCapabilities.java            — Forge capability registration
  client/
    ClientSetup.java                — Screen registration
    screen/
      TarkovInventoryScreen.java    — Full Tarkov UI screen
  compat/
    CuriosCompat.java               — Soft Curios integration
    TaczCompat.java                 — TACZ gun size registration
    BackpackCompat.java             — Other backpack mod detection
  container/
    TarkovInventoryMenu.java        — Server-side container menu
  inventory/
    GridInventory.java              — 10×12 grid inventory with NBT
    GridItemSizes.java              — Item → grid size mapping
    GridSize.java                   — Width/height record
  item/
    TarkovBackpackItem.java         — The backpack item
  registry/
    ModItems.java                   — Item DeferredRegister
    ModMenuTypes.java               — MenuType DeferredRegister
```
