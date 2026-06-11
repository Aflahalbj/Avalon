package id.avalon.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class RoleInfoTabCompleter implements TabCompleter {

    private static final List<String> ROLES = List.of(
        "merlin",
        "percival",
        "loyal",
        "assassin",
        "morgana",
        "mordred",
        "oberon",
        "minion"
    );

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {

        if (args.length == 1) {

            return ROLES.stream()
                .filter(r -> r.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return List.of();
    }
}