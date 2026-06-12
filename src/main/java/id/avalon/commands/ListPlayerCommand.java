package id.avalon.commands;

import id.avalon.managers.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        List<String> players = gameManager.getRegisteredPlayers();

        if (players.isEmpty()) {
            sender.sendMessage(Component.text("Belum ada player terdaftar.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("=== List Players ===", NamedTextColor.GOLD));

        for (int i = 0; i < players.size(); i++) {
            sender.sendMessage(
                Component.text((i + 1) + ". ", NamedTextColor.YELLOW)
                    .append(Component.text(players.get(i), NamedTextColor.WHITE))
            );
        }

        sender.sendMessage(
            Component.text("Total: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(players.size()), NamedTextColor.WHITE))
        );

        return true;
    }
}
