package id.avalon.commands;

import id.avalon.managers.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ListPlayerCommand implements CommandExecutor {

    private final GameManager gameManager;

    public ListPlayerCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        List<String> players =
                gameManager.getRegisteredPlayers();

        if (players.isEmpty()) {
            sender.sendMessage("§cBelum ada player terdaftar.");
            return true;
        }

        sender.sendMessage("§6=== List Players ===");

        for (int i = 0; i < players.size(); i++) {
            sender.sendMessage(
                "§e" + (i + 1) + ". §f" + players.get(i)
            );
        }

        sender.sendMessage(
            "§7Total: §f" + players.size()
        );

        return true;
    }
}