package id.avalon.commands;

import id.avalon.managers.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            sender.sendMessage(Component.text("Usage: /unregis <playername>", NamedTextColor.RED));
            return true;
        }

        String playerName = args[0];

        if (gameManager.unregisterPlayer(playerName)) {
            sender.sendMessage(Component.text("✔ " + playerName + " berhasil di-unregister dari Avalon.", NamedTextColor.GREEN));
            sender.sendMessage(
                Component.text("Total player: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(gameManager.getRegisteredPlayers().size()), NamedTextColor.WHITE))
            );
        } else {
            sender.sendMessage(Component.text(playerName + " tidak ditemukan di daftar.", NamedTextColor.YELLOW));
        }

        return true;
    }
}
