package com.tarkovinventory.client.screen;

import com.tarkovinventory.client.CorpseClientCache;
import com.tarkovinventory.inventory.RigContainer;
import com.tarkovinventory.network.C2SLootAllPacket;
import com.tarkovinventory.network.C2SPickupItemPacket;
import com.tarkovinventory.network.C2STakeFromCorpsePacket;
import com.tarkovinventory.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tarkovinventory.client.screen.modules.EquipmentPanelRenderer;
import com.tarkovinventory.client.screen.modules.InventoryGridRenderer;
import com.tarkovinventory.client.screen.modules.RightInventoryPanelRenderer;
import com.tarkovinventory.container.TarkovInventoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tarkov inventory UI backed by the Forge menu system.
 *
 * All slot positions are set in the menu constructor (Slot.x/y are final).
 * This screen renders backgrounds, manages hover, draws tooltips, and
 * dispatches loot/vicinity network packets on click.
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    private final EquipmentPanelRenderer     equipment  = new EquipmentPanelRenderer();
    private final InventoryGridRenderer      grid       = new InventoryGridRenderer();
    private final RightInventoryPanelRenderer rightPanel = new RightInventoryPanelRenderer();

    // Screen-space positions (set in init())
    private int equipScreenX, centerScreenX, rightScreenX, screenTopY;

    // ── Right-panel state (refreshed every render frame) ─────────────
    /** BlockPos of the corpse/container whose items are shown in the loot grid. */
    private BlockPos activeLootPos  = null;
    /** Display name for the loot header (owner name). */
    private String activeLootOwner  = "";
    /** Flat item list from the active loot source. */
    private final List<ItemStack>  lootItems         = new ArrayList<>();
    /** Live ItemEntity references for the vicinity grid (refreshed from level). */
    private final List<ItemEntity> vicinityEntities  = new ArrayList<>();

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth      = menu.getTotalImageWidth();
        this.imageHeight     = menu.getTotalImageHeight();
        this.inventoryLabelY = -1000;
        this.titleLabelY     = -1000;
    }

    @Override
    protected void init() {
        super.init();

        this.equipScreenX  = leftPos;
        this.centerScreenX = leftPos + menu.getCenterPanelX();
        this.rightScreenX  = leftPos + menu.getRightPanelX();
        this.screenTopY    = topPos;

        equipment.init(equipScreenX, screenTopY);
        grid.init(
            centerScreenX, screenTopY,
            menu.getBpCols(), menu.getBpRows(),
            menu.getRigCols(), menu.getRigRows()
        );
    }

    // ── Render ───────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        grid.updateHover(mouseX, mouseY);
        equipment.updateHover(mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.disableDepthTest();

        // Refresh right-panel data from cache / live level
        refreshRightPanel();

        // Equipment panel
        equipment.render(g, true);

        // Centre panel (pockets + rig + backpack grid)
        grid.renderWithBackground(g);

        // Rig background
        int rigW = menu.getRigCols() * TarkovInventoryMenu.CELL;
        int rigH = menu.getRigRows() * TarkovInventoryMenu.CELL;
        int rigY = topPos + menu.getRigGridY();
        g.fill(centerScreenX - 4, rigY - 4, centerScreenX + rigW + 4, rigY + rigH + 4, 0xFF0D0D0D);

        // Right panel — vicinity / loot
        int hoveredSlot = computeRightPanelHover(mouseX, mouseY);
        List<ItemStack> vicinityStacks = new ArrayList<>();
        for (ItemEntity ie : vicinityEntities) vicinityStacks.add(ie.getItem());
        rightPanel.render(g, rightScreenX, screenTopY,
                lootItems, activeLootOwner, vicinityStacks, hoveredSlot);

        RenderSystem.enableDepthTest();
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {
        g.fill(0, 0, width, height, 0xCC000000);
    }

    // ── Right-panel data refresh ──────────────────────────────────────

    /**
     * Rebuilds lootItems from CorpseClientCache and vicinityEntities from the
     * local Level. Called every frame from renderBg so it stays up to date.
     */
    private void refreshRightPanel() {
        // Loot: first entry in CorpseClientCache (nearest corpse/container)
        lootItems.clear();
        activeLootPos   = null;
        activeLootOwner = "";
        Map<BlockPos, CorpseClientCache.CorpseEntry> corpses = CorpseClientCache.all();
        if (!corpses.isEmpty()) {
            var first = corpses.entrySet().iterator().next();
            activeLootPos   = first.getKey();
            activeLootOwner = first.getValue().ownerName();
            // Slotted items first, then inventory items — all as a flat list
            lootItems.addAll(first.getValue().slottedItems().values());
            lootItems.addAll(first.getValue().inventoryItems());
        }

        // Vicinity: ItemEntities within 6 blocks of the player
        vicinityEntities.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            AABB box = mc.player.getBoundingBox().inflate(6.0);
            mc.level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)
                    .forEach(vicinityEntities::add);
        }
    }

    /**
     * Returns the hovered slot index for the right panel:
     *   0–79   → loot slot
     *   80–119 → vicinity slot (index 80 = vicinity[0])
     *   -1     → none
     */
    private int computeRightPanelHover(int mouseX, int mouseY) {
        int lootSlot = RightInventoryPanelRenderer.lootSlotAt(
                mouseX, mouseY, rightScreenX, screenTopY);
        if (lootSlot >= 0) return lootSlot;

        int vicSlot = RightInventoryPanelRenderer.vicinitySlotAt(
                mouseX, mouseY, rightScreenX, screenTopY);
        if (vicSlot >= 0) return 80 + vicSlot;

        return -1;
    }

    // ── Click handling ────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // left-click only

            // ── "LOOT ALL" button ─────────────────────────────────
            String lootAllText = "[LOOT ALL]";
            int lootAllW = Minecraft.getInstance().font.width(lootAllText);
            if (!lootItems.isEmpty() &&
                    RightInventoryPanelRenderer.isLootAllButton(
                            mouseX, mouseY, rightScreenX, screenTopY, lootAllW)) {
                if (activeLootPos != null) {
                    ModNetwork.CHANNEL.sendToServer(C2STakeFromCorpsePacket.takeAll(activeLootPos));
                } else {
                    ModNetwork.CHANNEL.sendToServer(new C2SLootAllPacket());
                }
                return true;
            }

            // ── Individual loot slot ──────────────────────────────
            int lootSlot = RightInventoryPanelRenderer.lootSlotAt(
                    mouseX, mouseY, rightScreenX, screenTopY);
            if (lootSlot >= 0 && lootSlot < lootItems.size()
                    && activeLootPos != null && !lootItems.get(lootSlot).isEmpty()) {
                // Offset past the slotted items to get inventory index
                int slottedCount = (int) CorpseClientCache.all()
                        .getOrDefault(activeLootPos,
                                new CorpseClientCache.CorpseEntry("", Map.of(), List.of()))
                        .slottedItems().size();
                if (lootSlot < slottedCount) {
                    // Slotted item — use takeAll (no individual named-slot key available here)
                    ModNetwork.CHANNEL.sendToServer(C2STakeFromCorpsePacket.takeAll(activeLootPos));
                } else {
                    int invIdx = lootSlot - slottedCount;
                    ModNetwork.CHANNEL.sendToServer(
                            C2STakeFromCorpsePacket.inventorySlot(activeLootPos, invIdx));
                }
                return true;
            }

            // ── Vicinity slot ─────────────────────────────────────
            int vicSlot = RightInventoryPanelRenderer.vicinitySlotAt(
                    mouseX, mouseY, rightScreenX, screenTopY);
            if (vicSlot >= 0 && vicSlot < vicinityEntities.size()) {
                ModNetwork.CHANNEL.sendToServer(
                        new C2SPickupItemPacket(vicinityEntities.get(vicSlot).getId()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Rig sync (called by S2CRigSyncPacket) ────────────────────────

    /**
     * Updates rig (mode=0) or backpack (mode=1) inventory from a server sync.
     */
    public void applyRigSync(int mode, CompoundTag data, int cols, int rows) {
        RigContainer container = mode == 0 ? menu.getRigContainer() : menu.getBackpackContainer();
        if (container.getRigInventory() != null) {
            container.getRigInventory().deserializeNBT(data);
        }
        if (mode == 0) {
            grid.setRigDimensions(cols, rows);
        } else {
            grid.setBackpackDimensions(cols, rows);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
