package id.avalon.commands;

import id.avalon.managers.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class CutsceneCommand implements CommandExecutor {

    private final GameManager gameManager;

    public CutsceneCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /cutscene <on|off>");
            return true;
        }

        String toggle = args[0].toLowerCase();
        if (toggle.equals("on")) {
            gameManager.setCutsceneEnabled(true);
            sender.sendMessage("§a✔ Cutscene diaktifkan.");
        } else if (toggle.equals("off")) {
            gameManager.setCutsceneEnabled(false);
            sender.sendMessage("§e✔ Cutscene dinonaktifkan.");
        } else {
            sender.sendMessage("§cUsage: /cutscene <on|off>");
        }

        return true;
    }
}
