package id.avalon.listeners;

import id.avalon.managers.GameManager;
import id.avalon.models.Role;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Menangani mekanik misi:
 *  1. Klik kanan "Sabotase" → triggerSabotage (kubu jahat)
 *  2. Block break tanaman misi dengan shears di Adventure mode → izinkan (finishMission sukses)
 *  3. Cegah drop/pindah shears misi
 */
public class MissionListener implements Listener {

    private static final int SABOTAGE_BLOCK_X = -40;
    private static final int SABOTAGE_BLOCK_Y = 67;
    private static final int SABOTAGE_BLOCK_Z = -119;

    private final GameManager gameManager;

    public MissionListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // ── Cegah drop shears misi ────────────────────────────────────────────────

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (gameManager.isMissionShears(event.getItemDrop().getItemStack())
                || gameManager.isMissionPlant(event.getItemDrop().getItemStack())
                || gameManager.isDiscussionSkipItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ── Cegah pindah shears misi di inventory ─────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (gameManager.isMissionShears(event.getCurrentItem())
                || gameManager.isMissionShears(event.getCursor())
                || gameManager.isMissionPlant(event.getCurrentItem())
                || gameManager.isMissionPlant(event.getCursor())
                || gameManager.isDiscussionSkipItem(event.getCurrentItem())
                || gameManager.isDiscussionSkipItem(event.getCursor())) {

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (gameManager.isMissionPlant(event.getMainHandItem())
                || gameManager.isMissionPlant(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (gameManager.isMissionShears(event.getMainHandItem())
                || gameManager.isMissionShears(event.getOffHandItem())
                || gameManager.isMissionPlant(event.getMainHandItem())
                || gameManager.isMissionPlant(event.getOffHandItem())) {

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(InventoryClickEvent event) {
        if (event.getClick().isKeyboardClick()) {
            event.setCancelled(true);
        }
    }

    // ── Klik kanan shears "Sabotase" → sabotase misi ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // ── Skip diskusi ───────────────────────────────────────────────────────
        if (gameManager.isDiscussionSkipItem(item)) {
            event.setCancelled(true);
            gameManager.handleDiscussionSkip(player);
            return;
        }

        if (!gameManager.isSabotaseShears(item)) return;
        if (!gameManager.isGameRunning()) return;
        if (!gameManager.isMissionActive()) return;

        event.setCancelled(true);

        // Hanya kubu jahat yang bisa sabotase
        Role role = gameManager.getRole(player);
        if (role == null || !role.isEvil()) return;

        gameManager.triggerSabotage(player);
    }

    // ── Block break tanaman misi dengan shears di Adventure ──────────────────

    /**
     * Di mode Adventure, player tidak bisa menghancurkan blok kecuali item
     * memiliki tag can_destroy. Kita override dengan membatalkan cancel di event,
     * tapi hanya untuk:
     *  - Player membawa shears misi (Gunting / Sabotase)
     *  - Blok yang dihancurkan adalah blok tanaman misi di koor yang tepat
     *  - Misi sedang aktif
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isGameRunning()) return;
        if (!gameManager.isMissionActive()) return;

        // Hanya proses jika player membawa shears misi
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!gameManager.isMissionShears(item)) return;

        // Hanya izinkan untuk blok tepat di koor misi
        org.bukkit.block.Block block = event.getBlock();
        boolean isMissionPlantPos =
                block.getX() == SABOTAGE_BLOCK_X
                && block.getZ() == SABOTAGE_BLOCK_Z
                && (
                    block.getY() == SABOTAGE_BLOCK_Y
                    || block.getY() == SABOTAGE_BLOCK_Y + 1
                );

        if (!isMissionPlantPos) {
            event.setCancelled(true);
            return;
        }

        // Cek bahwa blok ini memang tanaman misi yang valid (bukan dead bush)
        Material blockType = block.getType();
        if (blockType == Material.PITCHER_PLANT
                || blockType == Material.TORCHFLOWER
                || blockType == Material.CACTUS_FLOWER) {

            // Izinkan block break — tidak batalkan event
            event.setCancelled(false);
            // Drop item dimatikan supaya tidak drop ke ground
            event.setDropItems(false);
            player.getInventory().addItem(
                new ItemStack(blockType)
            );

        } else {
            // Blok bukan tanaman misi yang valid (misal dead bush) — cancel
            event.setCancelled(true);
        }
    }
}