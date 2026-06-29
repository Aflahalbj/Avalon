package id.avalon.listeners;

import id.avalon.managers.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import id.avalon.AvalonPlugin;

/**
 * Menangani player yang disconnect / reconnect di tengah game.
 *
 * Alur disconnect:
 *  - Spawn mannequin di posisi terakhir player
 *  - Broadcast pesan disconnect
 *  - Logika per fase:
 *      King selection → grace 90 detik lalu auto-rotasi raja
 *      Voting         → cek apakah semua online sudah vote (auto-finish)
 *      Mission        → cek apakah semua anggota tim offline (abort misi)
 *      Assassination  → grace 90 detik lalu kubu baik menang default
 *      Discussion     → tidak ada aksi khusus (timer terus berjalan)
 *
 * Alur reconnect:
 *  - Hapus mannequin
 *  - Broadcast pesan reconnect
 *  - Cancel grace timer jika ada
 *  - Restore item/state sesuai fase aktif
 */
public class PlayerOfflineHandler implements Listener {

    private final GameManager gameManager;

    public PlayerOfflineHandler(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isGameRunning()) return;
        if (!gameManager.getRegisteredPlayers().contains(player.getName())) return;

        gameManager.handlePlayerOffline(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isGameRunning()) return;
        if (!gameManager.getRegisteredPlayers().contains(player.getName())) return;

        // Delay 20 tick agar client sepenuhnya loaded sebelum restore state
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameManager.isGameRunning()) return;
                gameManager.handlePlayerOnline(player);
            }
        }.runTaskLater(AvalonPlugin.getInstance(), 20L);
    }
}