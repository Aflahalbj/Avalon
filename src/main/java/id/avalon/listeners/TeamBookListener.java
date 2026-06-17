package id.avalon.listeners;

import id.avalon.gui.TeamSelectionGUI;
import id.avalon.managers.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Menangani klik kanan pada item "Buku Pemilihan Tim".
 * Membuka GUI pemilihan tim, hanya untuk Raja Aktif saat game berjalan.
 */
public class TeamBookListener implements Listener {

    private final GameManager gameManager;

    public TeamBookListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {

        ItemStack item = event.getItemDrop().getItemStack();

        if (!GameManager.isTeamBook(item))
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        ItemStack item = event.getCurrentItem();

        if (!GameManager.isTeamBook(item))
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {

        for (ItemStack item : event.getNewItems().values()) {

            if (!GameManager.isTeamBook(item))
                continue;

            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!GameManager.isTeamBook(item)) return;

        event.setCancelled(true);

        if (!gameManager.isGameRunning()) {
            player.sendMessage(Component.text("Game belum berjalan!", NamedTextColor.RED));
            return;
        }

        if (!gameManager.isKing(player)) {
            player.sendMessage(
                Component.text("Hanya ", NamedTextColor.RED)
                    .append(Component.text("Raja Aktif", NamedTextColor.GOLD))
                    .append(Component.text(" yang bisa membuka menu ini!", NamedTextColor.RED))
            );
            return;
        }

        int missionNumber = gameManager.getCurrentMission();
        int playerCount   = gameManager.getRegisteredPlayers().size();
        int teamSize      = TeamSelectionGUI.getTeamSize(playerCount, missionNumber);

        List<String> registered    = new ArrayList<>(gameManager.getRegisteredPlayers());
        List<String> alreadyPicked = gameManager.getTeamSelectionSession(player);
        List<String> available     = new ArrayList<>(registered);
        available.removeAll(alreadyPicked);

        TeamSelectionGUI gui = new TeamSelectionGUI(id.avalon.AvalonPlugin.getInstance());
        Inventory inv = gui.create(teamSize, available, alreadyPicked);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
    }
}
