package id.avalon.commands;

import id.avalon.managers.GameManager;
import id.avalon.models.Role;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DebugRolesCommand implements CommandExecutor {

    private final GameManager gameManager;

    public DebugRolesCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {

        sender.sendMessage("§6=== ROLE DEBUG ===");

        for (Player player : Bukkit.getOnlinePlayers()) {

            Role role = gameManager.getRole(player);

            if (role == null)
                continue;

            sender.sendMessage(
                    "§e" + player.getName()
                            + " §7-> §f"
                            + role.name()
            );
        }

        return true;
    }
}