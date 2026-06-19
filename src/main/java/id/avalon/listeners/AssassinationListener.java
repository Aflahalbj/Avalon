package id.avalon.listeners;

import id.avalon.managers.GameManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Menangani mekanik fase assassination:
 *  1. Klik kanan item skip assassination → handleAssassinationSkip
 *  2. Arrow assassin kena entity → handleAssassinArrowHit
 *  3. Arrow assassin meleset (jatuh ke tanah) → handleAssassinArrowMiss
 *  4. Cegah drop / pindah item assassination
 */
public class AssassinationListener implements Listener {

    private final GameManager gameManager;
    private final NamespacedKey assassinBowKey;
    private final NamespacedKey assassinationSkipKey;

    public AssassinationListener(GameManager gameManager) {
        this.gameManager = gameManager;
        this.assassinBowKey      = new NamespacedKey(gameManager.getPlugin(), GameManager.ASSASSIN_BOW_KEY);
        this.assassinationSkipKey = new NamespacedKey(gameManager.getPlugin(), "assassination_skip");
    }

    // ── Klik kanan item skip ──────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!gameManager.isAssassinationSkipItem(item)) return;

        event.setCancelled(true);
        gameManager.handleAssassinationSkip(event.getPlayer());
    }

    // ── Arrow kena entity ─────────────────────────────────────────────────────

    /**
     * Tangkap saat arrow mengenai entity (player maupun bukan).
     * Cancel damage agar player tidak benar-benar terluka — efek visual saja (petir sudah di GameManager).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!isAssassinArrow(arrow)) return;

        // Cancel damage — assassination hanya efek visual
        event.setCancelled(true);

        Entity target = event.getEntity();
        gameManager.handleAssassinArrowHit(arrow, target);
    }

    // ── Arrow meleset (jatuh ke tanah / kena blok) ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!isAssassinArrow(arrow)) return;

        // Hanya proses jika kena blok (bukan entity) — entity ditangani di EntityDamageByEntityEvent
        if (event.getHitEntity() != null) return;

        gameManager.handleAssassinArrowMiss();
    }

    // ── Cegah drop bow / arrow / skip assassination ───────────────────────────

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isAssassinBowItem(item) || gameManager.isAssassinationSkipItem(item)) {
            event.setCancelled(true);
        }
    }

    // ── Cegah pindah item di inventory ───────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack cur    = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (isAssassinBowItem(cur) || isAssassinBowItem(cursor)
                || gameManager.isAssassinationSkipItem(cur)
                || gameManager.isAssassinationSkipItem(cursor)) {
            event.setCancelled(true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Cek apakah arrow ditembak oleh assassin (shooter adalah ASSASSIN + bow ber-PDC). */
    private boolean isAssassinArrow(Arrow arrow) {
        if (!(arrow.getShooter() instanceof Player shooter)) return false;
        // Cek role shooter
        return gameManager.getRole(shooter) == id.avalon.models.Role.ASSASSIN;
    }

    /** Cek apakah ItemStack adalah bow assassin (punya PDC key assassin_bow). */
    private boolean isAssassinBowItem(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != org.bukkit.Material.BOW) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(assassinBowKey, PersistentDataType.STRING);
    }
}