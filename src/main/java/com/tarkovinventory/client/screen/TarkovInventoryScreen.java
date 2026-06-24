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
            menu.getRigCols(), menu.getRigRows(),
            menu.getRigContainer().isItemEquipped(),
            menu.getBackpackContainer().isItemEquipped()
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
        return RightInventoryPanelRenderer.slotAt(mouseX, mouseY, rightScreenX, screenTopY);
    }

    // ── Click handling ────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // left-click only

            // ── Unified loot panel click ──────────────────────────
            int slot = RightInventoryPanelRenderer.slotAt(mouseX, mouseY, rightScreenX, screenTopY);
            if (slot >= 0) {
                // Loot ALL button
                String lootAllText = "[LOOT ALL]";
                int lootAllW = Minecraft.getInstance().font.width(lootAllText);
                if (!lootItems.isEmpty() && RightInventoryPanelRenderer.isLootAllButton(
                        mouseX, mouseY, rightScreenX, screenTopY, lootAllW)) {
                    CorpseClientCache.CorpseEntry e = activeLootPos != null
                            ? CorpseClientCache.all().get(activeLootPos) : null;
                    if (e != null && e.isCorpse()) {
                        ModNetwork.CHANNEL.sendToServer(C2STakeFromCorpsePacket.takeAll(activeLootPos));
                    } else if (activeLootPos != null) {
                        ModNetwork.CHANNEL.sendToServer(C2STakeFromContainerPacket.takeAll(activeLootPos));
                    }
                    return true;
                }

                // Click on a loot item (comes before vicinity in the merged list)
                if (slot < lootItems.size() && !lootItems.get(slot).isEmpty()
                        && activeLootPos != null) {
                    CorpseClientCache.CorpseEntry entry = CorpseClientCache.all().get(activeLootPos);
                    boolean isCorpse = entry != null && entry.isCorpse();
                    if (isCorpse) {
                        int slottedCount = entry.slottedItems().size();
                        if (slot < slottedCount) {
                            ModNetwork.CHANNEL.sendToServer(C2STakeFromCorpsePacket.takeAll(activeLootPos));
                        } else {
                            ModNetwork.CHANNEL.sendToServer(
                                C2STakeFromCorpsePacket.inventorySlot(activeLootPos, slot - slottedCount));
                        }
                    } else {
                        ModNetwork.CHANNEL.sendToServer(
                            C2STakeFromContainerPacket.inventorySlot(activeLootPos, slot));
                    }
                    return true;
                }

                // Click on a vicinity item (packed in after loot items)
                int vicIdx = slot - lootItems.size();
                if (vicIdx >= 0 && vicIdx < vicinityEntities.size()) {
                    ModNetwork.CHANNEL.sendToServer(
                            new C2SPickupItemPacket(vicinityEntities.get(vicIdx).getId()));
                    return true;
                }
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
