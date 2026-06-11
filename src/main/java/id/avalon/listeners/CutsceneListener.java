package id.avalon.listeners;

import id.avalon.managers.GameManager;
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

        if (!gm.isCameraLocked(player))
            return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null)
            return;

        to.setX(from.getX());
        to.setY(from.getY());
        to.setZ(from.getZ());

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

        event.setCancelled(true);
    }
}