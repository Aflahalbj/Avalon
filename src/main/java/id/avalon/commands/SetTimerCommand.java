package id.avalon.commands;

import id.avalon.managers.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SetTimerCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;

    public SetTimerCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (args.length != 2) {
            sender.sendMessage("§cPenggunaan:");
            sender.sendMessage("§7/settimer reveal <detik>");
            sender.sendMessage("§7/settimer voting <detik>");
            sender.sendMessage("§7/settimer discuss <detik>");
            sender.sendMessage("§7/settimer evildiscuss <detik>");
            return true;
        }

        int seconds;

        try {
            seconds = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cDetik harus berupa angka.");
            return true;
        }

        if (seconds <= 0) {
            sender.sendMessage("§cDetik harus lebih dari 0.");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reveal" -> {
                gameManager.setRevealSeconds(seconds);
                sender.sendMessage("§aReveal timer diubah menjadi §e" + seconds + "§a detik.");
            }

            case "voting" -> {
                gameManager.setVotingSeconds(seconds);
                sender.sendMessage("§aVoting timer diubah menjadi §e" + seconds + "§a detik.");
            }

            case "discuss" -> {
                gameManager.setDiscussionSeconds(seconds);
                sender.sendMessage("§aDiscussion timer diubah menjadi §e" + seconds + "§a detik.");
            }

            case "evildiscuss" -> {
                gameManager.setEvilDiscussionSeconds(seconds);
                sender.sendMessage("§aEvil discussion timer diubah menjadi §e" + seconds + "§a detik.");
            }

            default -> {
                sender.sendMessage("§cTimer tidak dikenal.");
                return true;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (args.length == 1) {

            List<String> list = Arrays.asList(
                    "reveal",
                    "voting",
                    "discuss",
                    "evildiscuss"
            );

            List<String> result = new ArrayList<>();

            for (String s : list) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }

            return result;
        }

        if (args.length == 2) {

            return Arrays.asList(
                    "10",
                    "20",
                    "30",
                    "60",
                    "120",
                    "300",
                    "600"
            );
        }

        return Collections.emptyList();
    }
}