package id.avalon;

// import com.github.retrooper.packetevents.PacketEvents;
// import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import id.avalon.commands.*;
import id.avalon.listeners.CustomRoleListener;
import id.avalon.listeners.CutsceneListener;
import org.bukkit.Bukkit;
import id.avalon.managers.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AvalonPlugin extends JavaPlugin {

    private static AvalonPlugin instance;
    private GameManager gameManager;

    //@Override
    // public void onLoad() {
    //     PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
    //     PacketEvents.getAPI().load();
    // }

    @Override
    public void onEnable() {
        instance = this;
        // PacketEvents.getAPI().init();

        gameManager = new GameManager(this);
        Bukkit.getPluginManager().registerEvents(
            new CutsceneListener(gameManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new CustomRoleListener(gameManager),
            this
        );

        getCommand("regis").setExecutor(new RegisCommand(gameManager));
        getCommand("unregis").setExecutor(new UnregisCommand(gameManager));
        getCommand("listplayer").setExecutor(new ListPlayerCommand(gameManager));
        getCommand("customrole").setExecutor(new CustomRoleCommand(gameManager));
        getCommand("cutscene").setExecutor(new CutsceneCommand(gameManager));
        getCommand("roleinfo").setExecutor(new RoleInfoCommand());
        getCommand("roleinfo").setTabCompleter(new RoleInfoTabCompleter());
        getCommand("startgame").setExecutor(new StartGameCommand(gameManager));
        getCommand("debugroles").setExecutor(new DebugRolesCommand(gameManager));
        getCommand("stopgame").setExecutor(new StopGameCommand(gameManager));
        

        getLogger().info("Avalon plugin enabled!");
    }

    @Override
    public void onDisable() {

        if (gameManager != null) {
            gameManager.cleanup();
        }
        
        getLogger().info("Avalon plugin disabled!");
    }

    public static AvalonPlugin getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
}