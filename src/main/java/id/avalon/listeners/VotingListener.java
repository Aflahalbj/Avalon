package id.avalon.listeners;

import id.avalon.managers.GameManager;
import id.avalon.managers.VotingManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Menangani interaksi player dengan item voting (Setuju / Tolak).
 */
public class VotingListener implements Listener {

    private final GameManager  gameManager;
    private final VotingManager votingManager;

    public VotingListener(GameManager gameManager, VotingManager votingManager) {
        this.gameManager  = gameManager;
        this.votingManager = votingManager;
    }

    /** Cegah drop item voting. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (votingManager.isVoteItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Cegah klik di inventory untuk item voting. */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (votingManager.isVoteItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    /** Cegah drag item voting. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (votingManager.isVoteItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Klik kanan item voting → catat suara. */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!votingManager.isVoteItem(item)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!gameManager.isGameRunning() || !votingManager.isVotingActive()) return;

        String voteType = votingManager.getVoteType(item);
        if (voteType == null) return;

        votingManager.castVote(player, voteType);
    }
}