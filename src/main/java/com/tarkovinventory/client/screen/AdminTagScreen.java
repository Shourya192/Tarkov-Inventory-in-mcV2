package com.tarkovinventory.client.screen;

import com.tarkovinventory.config.ItemTypeConfig;
import com.tarkovinventory.network.C2SSetItemTypePacket;
import com.tarkovinventory.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Admin-only screen for assigning slot types (and grid sizes) to any item on the
 * server. Browse/search all registered items, click one to select it, choose a
 * type, set cols×rows for rigs/backpacks, and Save — which sends the assignment
 * to the server (op-gated) to persist server-wide.
 */
public class AdminTagScreen extends Screen {

    private static final String[] TYPES = {
            "NONE", "RIG", "BACKPACK", "HEAD", "ARMOR", "PANTS", "BOOTS", "FACE", "EAR", "KNEE"
    };

    private EditBox searchBox;
    private final List<Item> allItems = new ArrayList<>();
    private final List<Item> filtered = new ArrayList<>();

    private int scroll = 0;
    private Item selected = null;
    private int typeIndex = 0;
    private int cols = 3, rows = 3;

    // Layout
    private int guiLeft, guiTop;
    private static final int GUI_W = 360, GUI_H = 240;
    private static final int GRID_COLS = 9, GRID_ROWS = 6, CELL = 18;

    public AdminTagScreen() {
        super(Component.literal("Item Tagger (Admin)"));
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - GUI_W) / 2;
        guiTop = (this.height - GUI_H) / 2;

        allItems.clear();
        for (Item it : ForgeRegistries.ITEMS) {
            if (it != null && it != net.minecraft.world.item.Items.AIR) allItems.add(it);
        }
        applyFilter("");

        searchBox = new EditBox(this.font, guiLeft + 8, guiTop + 20, 160, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search items..."));
        searchBox.setResponder(this::applyFilter);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        // Type cycle button
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.literal("Type: " + TYPES[typeIndex]),
                b -> { typeIndex = (typeIndex + 1) % TYPES.length; b.setMessage(Component.literal("Type: " + TYPES[typeIndex])); })
                .bounds(guiLeft + GUI_W - 150, guiTop + 60, 140, 18).build());

        // Cols -/+
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Cols -"),
                b -> cols = Math.max(1, cols - 1)).bounds(guiLeft + GUI_W - 150, guiTop + 84, 66, 18).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Cols +"),
                b -> cols = Math.min(12, cols + 1)).bounds(guiLeft + GUI_W - 76, guiTop + 84, 66, 18).build());
        // Rows -/+
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Rows -"),
                b -> rows = Math.max(1, rows - 1)).bounds(guiLeft + GUI_W - 150, guiTop + 106, 66, 18).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Rows +"),
                b -> rows = Math.min(12, rows + 1)).bounds(guiLeft + GUI_W - 76, guiTop + 106, 66, 18).build());

        // Save button
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.literal("Save Assignment"),
                b -> saveSelected())
                .bounds(guiLeft + GUI_W - 150, guiTop + GUI_H - 28, 140, 20).build());
    }

    private void applyFilter(String query) {
        filtered.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (Item it : allItems) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
            if (id == null) continue;
            if (q.isEmpty()
                    || id.toString().toLowerCase(Locale.ROOT).contains(q)
                    || new ItemStack(it).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(it);
            }
        }
        scroll = 0;
    }

    private void saveSelected() {
        if (selected == null) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(selected);
        if (id == null) return;
        String type = TYPES[typeIndex];
        int c = (type.equals("RIG") || type.equals("BACKPACK")) ? cols : 0;
        int r = (type.equals("RIG") || type.equals("BACKPACK")) ? rows : 0;
        ModNetwork.CHANNEL.sendToServer(new C2SSetItemTypePacket(id.toString(), type, c, r));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g);
        // Panel
        g.fill(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, 0xF0101010);
        g.fill(guiLeft, guiTop, guiLeft + GUI_W, guiTop + 14, 0xFF1C1C1C);
        g.drawString(this.font, "ITEM TAGGER (ADMIN)", guiLeft + 8, guiTop + 3, 0xFFD0D0A0, false);

        // Item grid
        int gridLeft = guiLeft + 8, gridTop = guiTop + 44;
        int startIdx = scroll * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int idx = startIdx + row * GRID_COLS + col;
                if (idx >= filtered.size()) continue;
                Item it = filtered.get(idx);
                int cx = gridLeft + col * CELL, cy = gridTop + row * CELL;
                boolean isSel = it == selected;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, isSel ? 0xFF4A4A2A : 0xFF222222);
                g.renderItem(new ItemStack(it), cx + 1, cy + 1);
            }
        }

        // Selected info
        int infoX = guiLeft + GUI_W - 150, infoY = guiTop + 20;
        if (selected != null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(selected);
            String name = new ItemStack(selected).getHoverName().getString();
            g.drawString(this.font, trim(name, 22), infoX, infoY, 0xFFFFFFFF, false);
            g.drawString(this.font, trim(id != null ? id.toString() : "?", 26), infoX, infoY + 10, 0xFF888888, false);
            String cur = id != null ? ItemTypeConfig.getType(id.toString()) : null;
            g.drawString(this.font, "Current: " + (cur == null ? "(none)" : cur), infoX, infoY + 22, 0xFFAAAAAA, false);
        } else {
            g.drawString(this.font, "Click an item →", infoX, infoY, 0xFF888888, false);
        }

        // Size labels (only relevant for rig/backpack)
        String t = TYPES[typeIndex];
        boolean sized = t.equals("RIG") || t.equals("BACKPACK");
        g.drawString(this.font, "Size: " + (sized ? cols + " x " + rows : "(n/a)"),
                infoX, guiTop + 130, sized ? 0xFFD0D0A0 : 0xFF666666, false);

        super.render(g, mouseX, mouseY, delta);

        // Hover tooltip on grid
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int idx = startIdx + row * GRID_COLS + col;
                if (idx >= filtered.size()) continue;
                int cx = gridLeft + col * CELL, cy = gridTop + row * CELL;
                if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                    g.renderTooltip(this.font, new ItemStack(filtered.get(idx)), mouseX, mouseY);
                }
            }
        }
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int gridLeft = guiLeft + 8, gridTop = guiTop + 44;
        int startIdx = scroll * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int idx = startIdx + row * GRID_COLS + col;
                if (idx >= filtered.size()) continue;
                int cx = gridLeft + col * CELL, cy = gridTop + row * CELL;
                if (mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL) {
                    selected = filtered.get(idx);
                    // Pre-fill current type/size for convenience
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(selected);
                    if (id != null) {
                        String cur = ItemTypeConfig.getType(id.toString());
                        if (cur != null) {
                            for (int i = 0; i < TYPES.length; i++)
                                if (TYPES[i].equalsIgnoreCase(cur)) typeIndex = i;
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, (filtered.size() + GRID_COLS - 1) / GRID_COLS - GRID_ROWS);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
