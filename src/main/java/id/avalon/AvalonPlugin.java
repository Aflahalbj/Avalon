package id.avalon;

// import com.github.retrooper.packetevents.PacketEvents;
// import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import id.avalon.commands.*;
import id.avalon.listeners.AssassinationListener;
import id.avalon.listeners.CustomRoleListener;
import id.avalon.listeners.CutsceneListener;
import id.avalon.listeners.MissionListener;
import id.avalon.listeners.TeamSelectionListener;
import id.avalon.listeners.VotingListener;
import id.avalon.listeners.PvPProtectionListener;
import id.avalon.managers.VotingManager;
import org.bukkit.Bukkit;
import id.avalon.managers.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AvalonPlugin extends JavaPlugin {

    private static AvalonPlugin instance;
    private GameManager gameManager;
    private TeamSelectionListener teamSelectionListener;
    private VotingManager votingManager;
    
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
        teamSelectionListener = new TeamSelectionListener(gameManager);

        // VotingManager — dibuat setelah gameManager siap
        votingManager = new VotingManager(this, gameManager);
        gameManager.setVotingManager(votingManager);

        getServer().getPluginManager().registerEvents(
                teamSelectionListener,
                this
        );
        Bukkit.getPluginManager().registerEvents(
            new CutsceneListener(gameManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new CustomRoleListener(gameManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new id.avalon.listeners.TeamBookListener(gameManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new VotingListener(gameManager, votingManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new MissionListener(gameManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new AssassinationListener(gameManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new PvPProtectionListener(gameManager),
            this
        );

        getCommand("regis").setExecutor(new RegisCommand(gameManager));
        getCommand("unregis").setExecutor(new UnregisCommand(gameManager));
        getCommand("listplayer").setExecutor(new ListPlayerCommand(gameManager));
        getCommand("customrole").setExecutor(new CustomRoleCommand(gameManager));
        getCommand("cutscene").setExecutor(new CutsceneCommand(gameManager));
        getCommand("roleinfo").setExecutor(new RoleInfoCommand(gameManager));
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

    public static AvalonPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public TeamSelectionListener getTeamSelectionListener() {
        return teamSelectionListener;
    }

    public VotingManager getVotingManager() {
        return votingManager;
    }
}