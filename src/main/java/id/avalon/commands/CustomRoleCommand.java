package id.avalon.commands;

import id.avalon.gui.CustomRoleGUI;
import id.avalon.gui.RoleEditorSession;
import id.avalon.listeners.CustomRoleListener;

import java.util.ArrayList;
import id.avalon.managers.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CustomRoleCommand implements CommandExecutor {

    private final GameManager gameManager;

    public CustomRoleCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {

        if (!(sender instanceof Player player))
            return true;

        if (args.length > 0) {
            player.sendMessage("§cGunakan: /customrole");
            return true;
        }

        int playerCount =
        gameManager.getRegisteredPlayers().size();

        if (playerCount < 5 || playerCount > 10) {
            player.sendMessage("§cJumlah player harus 5-10");
            return true;
        }

        RoleEditorSession session =
                new RoleEditorSession(
                        playerCount,
                        new ArrayList<>(
                                gameManager.getCustomRoles(
                                        playerCount
                                )
                        )
                );

        CustomRoleListener
                .getSessions()
                .put(
                        player.getUniqueId(),
                        session
                );

        CustomRoleGUI gui =
                new CustomRoleGUI(gameManager);

        player.openInventory(
                gui.create(session)
        );

        return true;
    }
}