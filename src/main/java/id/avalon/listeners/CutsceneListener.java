package id.avalon.listeners;

import id.avalon.managers.GameManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class CutsceneListener implements Listener {

    private final GameManager gm;

    public CutsceneListener(GameManager gm) {
        this.gm = gm;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (gm.isMovementLocked(player)) {

            if (event.getTo() == null)
                return;

            if (
                event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()
            ) {

                Location to = event.getTo();

                to.setX(event.getFrom().getX());
                to.setY(event.getFrom().getY());
                to.setZ(event.getFrom().getZ());

                event.setTo(to);
            }
        }
        if (!gm.isCameraLocked(player))
            return;

        Location to = event.getTo();

        if (to == null)
            return;

        to.setYaw(gm.getLockedYaw(player));
        to.setPitch(gm.getLockedPitch(player));

        event.setTo(to);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {

        if (!(event.getEntity() instanceof Player))
            return;

        if (!(event.getDismounted() instanceof ArmorStand stand))
            return;

        if (!stand.getScoreboardTags().contains("avalon_seat"))
            return;

        // Izinkan eject kalau game manager sedang dalam fase reveal
        // (supaya standAsViewer() dan standAsTarget() bisa eject player)
        if (gm.isRevealPhaseActive())
            return;

        event.setCancelled(true);
    }
}