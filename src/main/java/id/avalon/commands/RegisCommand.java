package id.avalon.commands;

import id.avalon.managers.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RegisCommand implements CommandExecutor {

    private final GameManager gameManager;

    public RegisCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /regis <playername>", NamedTextColor.RED));
            return true;
        }

        String playerName = args[0];

        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null) {
            sender.sendMessage(Component.text(
                    "Player tidak ditemukan atau sedang offline!",
                    NamedTextColor.RED
            ));
            return true;
        }

        if (gameManager.isGameRunning()) {
            sender.sendMessage(Component.text("Tidak bisa register saat game sedang berjalan!", NamedTextColor.RED));
            return true;
        }

        if (gameManager.getRegisteredPlayers().size() >= gameManager.getMaxPlayers()) {
            sender.sendMessage(Component.text(
                    "Arena Avalon sudah penuh! (maksimal "
                    + gameManager.getMaxPlayers()
                    + " player)", NamedTextColor.RED
            ));
            return true;
        }

        if (gameManager.registerPlayer(playerName)) {
            sender.sendMessage(Component.text("✔ " + playerName + " berhasil didaftarkan ke Avalon!", NamedTextColor.GREEN));
            sender.sendMessage(
                Component.text("Total player: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(gameManager.getRegisteredPlayers().size()), NamedTextColor.WHITE))
            );
        } else {
            sender.sendMessage(Component.text(playerName + " sudah terdaftar!", NamedTextColor.YELLOW));
        }

        return true;
    }
}
