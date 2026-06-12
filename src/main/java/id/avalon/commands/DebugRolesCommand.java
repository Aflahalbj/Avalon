package id.avalon.commands;

import id.avalon.managers.GameManager;
import id.avalon.models.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        sender.sendMessage(Component.text("=== ROLE DEBUG ===", NamedTextColor.GOLD));

        for (Player player : Bukkit.getOnlinePlayers()) {

            Role role = gameManager.getRole(player);

            if (role == null)
                continue;

            sender.sendMessage(
                Component.text(player.getName(), NamedTextColor.YELLOW)
                    .append(Component.text(" -> ", NamedTextColor.GRAY))
                    .append(Component.text(role.name(), NamedTextColor.WHITE))
            );
        }

        return true;
    }
}
