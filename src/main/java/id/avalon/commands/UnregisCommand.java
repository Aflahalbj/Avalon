package id.avalon.commands;

import id.avalon.managers.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class UnregisCommand implements CommandExecutor {

    private final GameManager gameManager;

    public UnregisCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /unregis <playername>");
            return true;
        }

        String playerName = args[0];

        if (gameManager.unregisterPlayer(playerName)) {
            sender.sendMessage("§a✔ " + playerName + " berhasil di-unregister dari Avalon.");
            sender.sendMessage("§7Total player: §f" + gameManager.getRegisteredPlayers().size());
        } else {
            sender.sendMessage("§e" + playerName + " tidak ditemukan di daftar.");
        }

        return true;
    }
}
