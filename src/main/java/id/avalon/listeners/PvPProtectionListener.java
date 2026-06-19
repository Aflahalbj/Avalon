package id.avalon.listeners;

import id.avalon.managers.GameManager;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class PvPProtectionListener implements Listener {

    private final GameManager gameManager;

    public PvPProtectionListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {

        if (!(event.getEntity() instanceof Player))
            return;

        // Arrow assassin boleh lewat
        if (event.getDamager() instanceof Arrow arrow) {
            if (gameManager.isAssassinArrow(arrow)) {
                return;
            }
        }

        // Semua pukulan player diblok
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
            return;
        }

        // Semua projectile player diblok
        if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }
}