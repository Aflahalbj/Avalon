package id.avalon.managers;

import id.avalon.AvalonPlugin;
import id.avalon.models.Role;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.Style;
import org.bukkit.*;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {

    private final AvalonPlugin plugin;
    private final List<String> registeredPlayers = new ArrayList<>();
    private boolean cutsceneEnabled = true;
    private boolean cutsceneRunning = false;
    private BukkitTask cutsceneTask;
    private BukkitTask countdownTask;
    private boolean gameRunning = false;
    private final Map<UUID, Float> lockedYaw = new HashMap<>();
    private final Map<UUID, Float> lockedPitch = new HashMap<>();
    private final Map<UUID, Role> playerRoles = new HashMap<>();
    public List<Role> getDefaultRolesPublic(int playerCount) {
        return new ArrayList<>(getDefaultRoles(playerCount));
    }
    public Map<UUID, Role> getPlayerRoles() {
        return new HashMap<>(playerRoles);
    }

    // Koordinat mannequin / cutscene
    private static final double MANNEQUIN_X = -60.5;
    private static final double MANNEQUIN_Y = 74.6;
    private static final double MANNEQUIN_Z = -417;
    private static final float MANNEQUIN_YAW = 270f;

    // Koordinat player teleport saat cutscene
    private static final double SPECTATOR_X = -61.391;
    private static final double SPECTATOR_Y = 75.511;
    private static final double SPECTATOR_Z = -415.740;
    private static final float SPECTATOR_YAW = -164f;
    private static final float SPECTATOR_PITCH = 49.3f;

    // Posisi 10 slab player, offset dari BASE_X / BASE_Z
    private static final int[][] PLAYER_SLAB_POSITIONS = {
        {0, 6},   // 1
        {0, -6},  // 2
        {-5, 2},   // 3
        {5, -2},  // 4
        {-5, -2},  // 5
        {5, 2},   // 6
        {-3, 5},   // 7
        {3, -5},  // 8
        {-3, -5},  // 9
        {3, 5},   // 10
    };

    private static final int BASE_X = 7;
    private static final int BASE_Y = 74;
    private static final int BASE_Z = -379;

    public GameManager(AvalonPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== REGISTER =====

    public boolean registerPlayer(String playerName) {

        if (registeredPlayers.size() >= PLAYER_SLAB_POSITIONS.length)
            return false;

        if (registeredPlayers.contains(playerName))
            return false;

        registeredPlayers.add(playerName);
        return true;
    }

    public boolean unregisterPlayer(String playerName) {
        return registeredPlayers.remove(playerName);
    }

    public List<String> getRegisteredPlayers() {
        return Collections.unmodifiableList(registeredPlayers);
    }

    public int getMaxPlayers() {
        return PLAYER_SLAB_POSITIONS.length;
    }

    // ===== CUTSCENE TOGGLE =====

    public void setCutsceneEnabled(boolean enabled) {
        this.cutsceneEnabled = enabled;
    }

    public boolean isCutsceneEnabled() {
        return cutsceneEnabled;
    }

    public boolean isCutsceneRunning() {
        return cutsceneRunning;
    }

    // ===== ROLE MANAGEMENT =====
    public Role getRole(Player player) {
        return playerRoles.get(player.getUniqueId());
    }

    private List<Role> getDefaultRoles(int playerCount) {

        List<Role> roles = new ArrayList<>();

        switch (playerCount) {

            case 5 -> {
                roles.add(Role.MERLIN);
                roles.add(Role.PERCIVAL);
                roles.add(Role.LOYAL_SERVANT);

                roles.add(Role.ASSASSIN);
                roles.add(Role.MORGANA);
            }

            case 6 -> {
                roles.add(Role.MERLIN);
                roles.add(Role.PERCIVAL);
                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);

                roles.add(Role.ASSASSIN);
                roles.add(Role.MORDRED);
            }

            case 7 -> {
                roles.add(Role.MERLIN);
                roles.add(Role.PERCIVAL);
                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);

                roles.add(Role.ASSASSIN);
                roles.add(Role.MORGANA);
                roles.add(Role.OBERON);
            }

            case 8 -> {
                roles.add(Role.MERLIN);
                roles.add(Role.PERCIVAL);

                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);

                roles.add(Role.ASSASSIN);
                roles.add(Role.MORGANA);
                roles.add(Role.MORDRED);
            }

            case 9 -> {
                roles.add(Role.MERLIN);
                roles.add(Role.PERCIVAL);

                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);

                roles.add(Role.ASSASSIN);
                roles.add(Role.MORGANA);
                roles.add(Role.MORDRED);
            }

            case 10 -> {
                roles.add(Role.MERLIN);
                roles.add(Role.PERCIVAL);

                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);
                roles.add(Role.LOYAL_SERVANT);

                roles.add(Role.ASSASSIN);
                roles.add(Role.MORGANA);
                roles.add(Role.MORDRED);
                roles.add(Role.OBERON);
            }

            default -> {
                // Tidak seharusnya terjadi karena startGame() sudah validasi 5-10,
                // tapi kalau sampai ke sini lempar exception supaya ketahuan saat dev.
                throw new IllegalArgumentException(
                    "getDefaultRoles() dipanggil dengan playerCount tidak valid: " + playerCount
                    + ". Harus antara 5-10."
                );
            }
        }

        return roles;
    }

    private void assignRoles(List<Player> players) {

        List<Role> roles = getRolesForPlayerCount(players.size());

        Collections.shuffle(roles);

        playerRoles.clear();

        for (int i = 0; i < players.size(); i++) {
            playerRoles.put(
                    players.get(i).getUniqueId(),
                    roles.get(i)
            );
        }
    }

    private final Map<Integer, List<Role>> customRoles = new HashMap<>();

    private List<Role> getRolesForPlayerCount(int playerCount) {

        List<Role> custom = customRoles.get(playerCount);

        if (custom != null) {
            return new ArrayList<>(custom);
        }

        return getDefaultRoles(playerCount);
    }

    public List<Role> getCustomRoles(int playerCount) {

        List<Role> custom = customRoles.get(playerCount);

        if (custom != null) {
            return new ArrayList<>(custom);
        }

        return getDefaultRoles(playerCount);
    }

    public void setCustomRoles(
            int playerCount,
            List<Role> roles
    ) {
        customRoles.put(
                playerCount,
                new ArrayList<>(roles)
        );
    }

    private void startRoleReveal(List<Player> players) {

        List<Role> availableRoles = getRolesForPlayerCount(players.size());

        new BukkitRunnable() {

            int ticks = 0;

            @Override
            public void run() {

                if (ticks >= 40) {

                    for (Player p : players) {

                        Role realRole = getRole(p);

                        p.sendTitle(
                                "§6Mengocok Peran",
                                "§e" + realRole.name(),
                                10,
                                60,
                                20
                        );
                        p.playSound(
                                p.getLocation(),
                                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                                1f,
                                1f
                        );

                        p.sendMessage(Component.text(" "));
                        p.sendMessage(Component.text("══════════════════════", NamedTextColor.GOLD));

                        for (Component line : getRoleDescription(realRole)) {
                            p.sendMessage(line);
                        }

                        p.sendMessage(Component.text("══════════════════════", NamedTextColor.GOLD));
                    }

                    cancel();
                    return;
                }

                for (Player p : players) {

                    Role randomRole =
                        availableRoles.get(
                                (int) (Math.random() * availableRoles.size())
                        );

                    p.sendTitle(
                            "§6Mengocok Peran",
                            "§f" + randomRole.name(),
                            0,
                            10,
                            0
                    );
                    p.playSound(
                            p.getLocation(),
                            Sound.UI_BUTTON_CLICK,
                            1f,
                            1.2f
                    );
                }

                ticks++;

            }

        }.runTaskTimer(plugin, 0L, 2L);
    }

    private List<Component> getRoleDescription(Role role) {

        return switch (role) {

            case MERLIN -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Merlin", NamedTextColor.AQUA)),
                    Component.text("  Anda dapat melihat semua kubu jahat kecuali Mordred.", NamedTextColor.WHITE),
                    Component.text("  Tuntun kubu baik dalam memilih orang yang akan menjalankan misi!", NamedTextColor.WHITE),
                    Component.text("  Jangan sampai kubu jahat mengetahui siapa Anda!", NamedTextColor.RED)
            );

            case PERCIVAL -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Percival", NamedTextColor.AQUA)),
                    Component.text("  Anda melihat Merlin dan Morgana", NamedTextColor.WHITE),
                    Component.text("  tetapi tidak tahu siapa Merlin yang asli.", NamedTextColor.WHITE)
            );

            case LOYAL_SERVANT -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Loyal Servant", NamedTextColor.AQUA)),
                    Component.text("  Bantu kubu baik menyelesaikan misi.", NamedTextColor.WHITE)
            );

            case ASSASSIN -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Assassin", NamedTextColor.RED)),
                    Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                    Component.text("  Jika kubu baik menang, bunuh Merlin untuk mencuri kemenangan.", NamedTextColor.WHITE)
            );

            case MORGANA -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Morgana", NamedTextColor.RED)),
                    Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                    Component.text("  Anda terlihat seperti Merlin bagi Percival.", NamedTextColor.WHITE)
            );

            case MORDRED -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Mordred", NamedTextColor.RED)),
                    Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                    Component.text("  Merlin tidak dapat melihat Anda.", NamedTextColor.WHITE)
            );

            case OBERON -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Oberon", NamedTextColor.RED)),
                    Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                    Component.text("  Anda tidak tahu kubu jahat lainnya.", NamedTextColor.WHITE),
                    Component.text("  Kubu jahat lainnya pun tidak tahu bahwa anda bagian dari mereka.", NamedTextColor.WHITE)
            );

            case MINION_OF_MORDRED -> List.of(
                    Component.text("  Anda adalah ", NamedTextColor.GREEN)
                        .append(Component.text("Minion of Mordred", NamedTextColor.RED)),
                    Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE)
            );
        };
    }

    // ===== START GAME =====

    private void startCountdown(List<Player> activePlayers) {

        countdownTask = new BukkitRunnable() {

            int seconds = 5;

            @Override
            public void run() {

                if (seconds <= 0) {

                    for (Player p : activePlayers) {
                        p.sendTitle(
                            "§a§lMULAI!",
                            "",
                            0, 20, 10
                        );
                    }

                    countdownTask = null;

                    if (cutsceneEnabled) {
                        playCutscene(getGameWorld(), activePlayers);
                    } else {
                        startGamePhase(activePlayers);
                    }

                    cancel();
                    return;
                }

                for (Player p : activePlayers) {

                    p.sendTitle(
                        "Game dimulai dalam...",
                        "§e§l" + String.valueOf(seconds),
                        0, 25, 0
                    );

                    p.playSound(
                        p.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        1f,
                        1f
                    );
                }

                seconds--;
            }

        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void startGame(Player initiator) {
        if (gameRunning) {
            initiator.sendMessage(Component.text("Game sudah berjalan!", NamedTextColor.RED));
            return;
        }

        List<Player> activePlayers = getOnlinePlayers();
        if (activePlayers.size() < 5) {
            initiator.sendMessage(Component.text(
                    "Minimal 5 player untuk memulai Avalon!",
                    NamedTextColor.RED
            ));
            return;
        }

        if (activePlayers.size() > PLAYER_SLAB_POSITIONS.length) {
            initiator.sendMessage(Component.text(
                    "Terlalu banyak player untuk arena ini!",
                    NamedTextColor.RED
            ));
            return;
        }

        gameRunning = true;
        spawnMannequin(initiator.getWorld());
        startCountdown(activePlayers);
    }

    private void spawnMannequin(World world) {
        world.spawn(
                new Location(world,
                        MANNEQUIN_X,
                        MANNEQUIN_Y,
                        MANNEQUIN_Z,
                        MANNEQUIN_YAW,
                        0f),
                Mannequin.class,
                m -> {
                    ResolvableProfile profile = ResolvableProfile.resolvableProfile()
                            .name("fredganteng")
                            .build();

                    m.setProfile(profile);
                    m.setImmovable(true);
                    m.setInvulnerable(true);
                    m.setGravity(false);
                    m.setAI(false);
                    m.setPersistent(true);
                    m.setPose(Pose.SLEEPING, true);
                    m.setCustomNameVisible(false);
                    m.setDescription(null);
                    m.addScoreboardTag("avalon_mannequin");
                }
        );
    }

    // ===== STOP GAME =====

    public void stopGame(Player initiator) {
        if (!gameRunning) {
            initiator.sendMessage(Component.text("Tidak ada game yang berjalan!", NamedTextColor.RED));
            return;
        }

        cleanup();
        broadcast(Component.text("Game dihentikan oleh admin.", NamedTextColor.RED)
            .decorate(TextDecoration.BOLD));
        initiator.sendMessage(Component.text("Game berhasil dihentikan. Player masih terdaftar.", NamedTextColor.GREEN));
    }

    // ===== CUTSCENE =====

    private void playCutscene(World world, List<Player> activePlayers) {
        cutsceneRunning = true;

        // Teleport semua player ke spectator + lock kamera
        for (Player p : activePlayers) {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(new Location(world, SPECTATOR_X, SPECTATOR_Y, SPECTATOR_Z, SPECTATOR_YAW, SPECTATOR_PITCH));
            lockCamera(p, SPECTATOR_YAW, SPECTATOR_PITCH);
        }

        // Cutscene messages tiap 9 detik
        Component[] lines = {
            Component.text("Sudah satu bulan lamanya pak fred tidak sadarkan diri", NamedTextColor.YELLOW),
            Component.text("Konon katanya ada satu ramuan yang dapat menyembuhkannya", NamedTextColor.YELLOW),
            Component.text("Ramuan yang dibuat dengan 3 tanaman langka", NamedTextColor.YELLOW),
            Component.text("Pitcher plant, Torch flower, Cactus flower",
                Style.style(NamedTextColor.GOLD, TextDecoration.ITALIC)),
            Component.text("Hanya ada satu orang yang dapat menyembuhkannya", NamedTextColor.YELLOW),
            Component.text("MERLIN", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text(", sang penyihir terhebat", NamedTextColor.GOLD)),
        };

        cutsceneTask = new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= lines.length) {
                    cancel();
                    cutsceneRunning = false;
                    for (Player p : getOnlinePlayers()) unlockCamera(p);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            startGamePhase(getOnlinePlayers());
                        }
                    }.runTaskLater(plugin, 10L);
                    return;
                }
                Component line = lines[index++];
                for (Player p : getOnlinePlayers()) p.sendMessage(line);
            }
        }.runTaskTimer(plugin, 0L, 180L);
    }

    // ===== CAMERA LOCK =====

    public void lockCamera(Player player, float yaw, float pitch) {
        lockedYaw.put(player.getUniqueId(), yaw);
        lockedPitch.put(player.getUniqueId(), pitch);
    }

    public void unlockCamera(Player player) {
        lockedYaw.remove(player.getUniqueId());
        lockedPitch.remove(player.getUniqueId());
    }

    public boolean isCameraLocked(Player player) {
        return lockedYaw.containsKey(player.getUniqueId());
    }

    public float getLockedYaw(Player player) {
        return lockedYaw.get(player.getUniqueId());
    }

    public float getLockedPitch(Player player) {
        return lockedPitch.get(player.getUniqueId());
    }

    // ===== GAME PHASE =====

    private void startGamePhase(List<Player> activePlayers) {
        World world = getGameWorld();
        if (world == null) return;

        broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));
        broadcast(Component.text(" "));
        broadcast(Component.text("  🤫 GAME DIMULAI 🤫", NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Jaga & bantu merlin mendapatkan 3 tanaman untuk menang!", NamedTextColor.YELLOW));
        broadcast(Component.text("  Jangan biarkan kubu jahat menggagalkan misi!", NamedTextColor.RED));
        broadcast(Component.text(" "));
        broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));

        world.getBlockAt(BASE_X, BASE_Y, BASE_Z)
            .setType(Material.WATER_CAULDRON);

        world.getBlockAt(BASE_X, BASE_Y - 1, BASE_Z)
            .setType(Material.CAMPFIRE);
        placeSlabsAndSeat(world, activePlayers);

        new BukkitRunnable() {
            @Override
            public void run() {
                assignRoles(activePlayers);
                startRoleReveal(activePlayers);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void placeSlabsAndSeat(World world, List<Player> activePlayers) {
        for (int i = 0; i < Math.min(activePlayers.size(), PLAYER_SLAB_POSITIONS.length); i++) {
            int[] pos = PLAYER_SLAB_POSITIONS[i];
            int x = BASE_X + pos[0];
            int z = BASE_Z + pos[1];

            Location slabLoc = new Location(world, x, BASE_Y, z);
            world.getBlockAt(slabLoc).setType(Material.JUNGLE_SLAB);
            org.bukkit.block.Block block = world.getBlockAt(slabLoc);
            if (block.getBlockData() instanceof Slab slab) {
                slab.setType(Slab.Type.BOTTOM);
                block.setBlockData(slab);
            }

            final int fx = x, fz = z;
            final double dx = (BASE_X + 0.5) - (fx + 0.5);
            final double dz = (BASE_Z + 0.5) - (fz + 0.5);
            final float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            Player p = activePlayers.get(i);
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(new Location(world, x + 0.5, BASE_Y, z + 0.5, yaw, 0));

            final Player fp = p;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location seatLoc = new Location(
                        world,
                        fx + 0.5,
                        BASE_Y + 0.5,
                        fz + 0.5,
                        yaw,
                        0
                    );
                    ArmorStand seat = world.spawn(seatLoc, ArmorStand.class, as -> {
                        as.setVisible(false);
                        as.setGravity(false);
                        as.setInvulnerable(true);
                        as.setMarker(true);
                        as.setCustomNameVisible(false);
                        as.addScoreboardTag("avalon_seat");
                    });

                    seat.setRotation(yaw, 0);
                    seat.addPassenger(fp);
                }
            }.runTaskLater(plugin, 5L);
        }
    }

    // ===== UTILS =====

    private void broadcast(Component message) {
        for (Player p : getOnlinePlayers()) p.sendMessage(message);
    }

    private List<Player> getOnlinePlayers() {
        List<Player> players = new ArrayList<>();
        for (String name : registeredPlayers) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) players.add(p);
        }
        return players;
    }

    private World getGameWorld() {
        for (String name : registeredPlayers) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) return p.getWorld();
        }
        return Bukkit.getWorlds().get(0);
    }

    public boolean isGameRunning() { return gameRunning; }
    public void setGameRunning(boolean running) { this.gameRunning = running; }

    public void cleanup() {

        if (cutsceneTask != null) {
            cutsceneTask.cancel();
            cutsceneTask = null;
        }

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        lockedYaw.clear();
        lockedPitch.clear();

        // Turunkan semua player dari seat
        for (Player p : Bukkit.getOnlinePlayers()) {

            if (p.getVehicle() != null) {

                Entity vehicle = p.getVehicle();

                vehicle.eject();

                if (vehicle.getScoreboardTags().contains("avalon_seat")) {
                    vehicle.remove();
                }
            }

            p.setGameMode(GameMode.SURVIVAL);
        }

        // Hapus semua entity Avalon dan slab di world game
        World gameWorld = getGameWorld();
        if (gameWorld != null) {

            for (Entity e : gameWorld.getEntities()) {

                if (e.getScoreboardTags().contains("avalon_seat")) {
                    e.remove();
                }

                if (e.getScoreboardTags().contains("avalon_mannequin")) {
                    e.remove();
                }
            }

            for (int[] pos : PLAYER_SLAB_POSITIONS) {
                int x = BASE_X + pos[0];
                int z = BASE_Z + pos[1];
                gameWorld.getBlockAt(x, BASE_Y, z).setType(Material.AIR);
            }

            gameWorld.getBlockAt(BASE_X, BASE_Y, BASE_Z).setType(Material.AIR);
            gameWorld.getBlockAt(BASE_X, BASE_Y - 1, BASE_Z).setType(Material.ORANGE_TERRACOTTA);
        }

        gameRunning = false;
        cutsceneRunning = false;
    }
}
