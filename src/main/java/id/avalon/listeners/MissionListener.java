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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Menangani mekanik misi:
 *  1. Klik kanan "Sabotase" → triggerSabotage (kubu jahat)
 *  2. Block break tanaman misi dengan shears di Adventure mode → izinkan (finishMission sukses)
 *  3. Cegah drop/pindah shears misi
 *  4. Cancel fall damage untuk anggota tim misi
 */
public class MissionListener implements Listener {

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
     *  - Blok yang dihancurkan adalah blok tanaman misi di salah satu koor PLANT_LOCATIONS
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

        org.bukkit.block.Block block = event.getBlock();
        int bx = block.getX(), by = block.getY(), bz = block.getZ();

        // Cek apakah blok berada di salah satu lokasi tanaman misi
        boolean isMissionPlantPos = false;
        for (int i = 0; i < GameManager.PLANT_LOCATIONS.length; i++) {
            int[] loc = GameManager.PLANT_LOCATIONS[i];
            if (bx == loc[0] && bz == loc[2]
                    && (by == loc[1] || by == loc[1] + 1)) {
                isMissionPlantPos = true;
                break;
            }
        }

        if (!isMissionPlantPos) {
            event.setCancelled(true);
            return;
        }

        // Cek bahwa blok ini memang tanaman misi yang valid
        Material blockType = block.getType();
        boolean isValidPlant = false;
        for (Material mat : GameManager.PLANT_MATERIALS) {
            if (blockType == mat) { isValidPlant = true; break; }
        }

        if (isValidPlant) {
            // Izinkan block break, beri item tanaman ke player
            event.setCancelled(false);
            event.setDropItems(false);
            // Berikan item dari material dasar (bukan block state half)
            Material dropMat = blockType;
            player.getInventory().addItem(new ItemStack(dropMat));
        } else {
            event.setCancelled(true);
        }
    }

    // ── Cancel fall damage untuk anggota tim misi ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!gameManager.isGameRunning()) return;
        event.setCancelled(true);
    }
}