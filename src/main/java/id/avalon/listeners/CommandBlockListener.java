package id.avalon.listeners;

import id.avalon.managers.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

public class CommandBlockListener implements Listener {

    private final GameManager gm;

    private static final Set<String> BLOCKED = Set.of(
            "/msg",
            "/tell",
            "/w",
            "/whisper"
    );

    public CommandBlockListener(GameManager gm) {
        this.gm = gm;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {

        if (!gm.isGameRunning()) {
            return;
        }

        String cmd = event.getMessage().toLowerCase();

        for (String blocked : BLOCKED) {
            if (cmd.equals(blocked) || cmd.startsWith(blocked + " ")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(
                        ChatColor.RED + "Ciee mau kirim pesan ke siapa tuh."
                );
                return;
            }
        }
    }
}