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
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.Block;
import java.net.URL;
import java.util.*;

public class GameManager {

    private final AvalonPlugin plugin;
    private final List<String> registeredPlayers = new ArrayList<>();
    private final List<BukkitTask> delayedTasks = new ArrayList<>();

    private boolean cutsceneEnabled  = true;
    private boolean cutsceneRunning  = false;
    private boolean gameRunning      = false;

    // Flag: sedang dalam fase reveal — dipakai CutsceneListener
    // supaya eject dari avalon_seat tidak di-cancel saat perlu berdiri
    private boolean revealPhaseActive = false;

    private BukkitTask cutsceneTask;
    private BukkitTask countdownTask;
    // Task countdown reveal — supaya bisa di-cancel saat /stopgame
    private BukkitTask revealCountdownTask;
    // Task action bar "Menunggu raja memilih tim"
    private BukkitTask teamSelectionActionBarTask;
    // VotingManager — diinisialisasi setelah plugin siap
    private VotingManager votingManager;

    private final Map<UUID, Float> lockedYaw   = new HashMap<>();
    private final Map<UUID, Float> lockedPitch = new HashMap<>();
    private final Set<UUID> movementLocked = new HashSet<>();
    private final Map<UUID, Role>  playerRoles = new HashMap<>();

    // ── King (Raja) mechanism ─────────────────────────────────────────────────
    /** Urutan player untuk rotasi Raja. Diset saat game dimulai dan tidak berubah. */
    private final List<String> kingOrder = new ArrayList<>();
    /** Index di kingOrder yang saat ini menjadi Raja. */
    private int currentKingIndex = -1;
    /** Misi yang sedang berjalan (1-5). */
    private int currentMission = 1;
    private int currentRound = 1;
    private final Set<Integer> completedPlants = new HashSet<>();
    private int evilMissionFails = 0;
    /** Session pemilihan tim per Raja (UUID raja -> list nama yang sudah dipilih). */
    private final Map<UUID, List<String>> teamSelectionSessions = new HashMap<>();

    // ── Mission state ─────────────────────────────────────────────────────────
    /** Task untuk sabotage mechanic (45s timer). */
    private BukkitTask sabotageTimerTask;
    /** Task untuk actionbar kubu jahat di fase misi. */
    private BukkitTask missionEvilActionBarTask;
    /** Apakah misi sudah berakhir (dicegah double-finish). */
    private boolean missionActive = false;
    /** Tim yang sedang menjalankan misi (nama player). */
    private List<String> currentMissionTeam = new ArrayList<>();
    /** Koordinat blok tanaman misi saat ini (untuk cek proximity sabotage). */
    private Location missionPlantLocation = null;
    /** Task proximity checker (player mendekat tanaman → trigger end). */
    private BukkitTask proximityTask;
    /** Apakah misi ini sudah disabotase. */
    private boolean missionSabotaged = false;
    private int sabotageCount = 0;
    /** Index tanaman di PLANT_LOCATIONS yang terakhir berhasil dipanen (untuk cutscene). */
    private int lastCollectedPlantIndex = 0;
    private final Set<UUID> sabotagedPlayers = new HashSet<>();
    /** Task countdown end-mission. */
    private BukkitTask endMissionCountdownTask;

    // ── Discussion state ──────────────────────────────────────────────────────
    private static final String SKIP_TEXTURE =
        "http://textures.minecraft.net/texture/65a84e6394baf8bd795fe747efc582cde9414fccf2f1c8608f1be18c0e079138";
    // DEBUG
    private static final int DISCUSSION_SECONDS = 10; // 10 menit
    private BukkitTask discussionTask;
    /** UUID player yang sudah vote skip di fase diskusi. */
    private final Set<UUID> discussionSkipVotes = new HashSet<>();
    private boolean discussionActive = false;
    private boolean discussionAfterSuccess = false;
    /** ArmorStand floating head per player yang sudah vote skip diskusi. */
    private final Map<UUID, ArmorStand> discussionSkipHeads = new HashMap<>();
    private BukkitTask discussionHeadAnimTask;

    // ── Assassination state ───────────────────────────────────────────────────
    // DEBUG
    private static final int ASSASSINATION_SECONDS = 10; // 10 menit
    /** Apakah fase assassination sedang aktif. */
    private boolean assassinationActive = false;
    /** Task countdown fase assassination. */
    private BukkitTask assassinationTask;
    /** UUID player yang sudah vote skip di fase assassination. */
    private final Set<UUID> assassinationSkipVotes = new HashSet<>();
    /** ArmorStand floating head per kubu jahat yang vote skip assassination. */
    private final Map<UUID, ArmorStand> assassinationSkipHeads = new HashMap<>();
    /** Task animasi floating head assassination. */
    private BukkitTask assassinationHeadAnimTask;
    /** PDC key untuk arrow assassin. */
    public static final String ASSASSIN_BOW_KEY = "assassin_bow";
    /** Apakah assassin sudah menembak (untuk prevent double trigger). */
    private boolean assassinShotFired = false;

    // ── Koordinat ────────────────────────────────────────────────────────────

    private static final double MANNEQUIN_X   = -60.5;
    private static final double MANNEQUIN_Y   = 74.6;
    private static final double MANNEQUIN_Z   = -417;
    private static final float  MANNEQUIN_YAW = 270f;

    private static final double SPECTATOR_X     = -61.391;
    private static final double SPECTATOR_Y     = 75.511;
    private static final double SPECTATOR_Z     = -415.740;
    private static final float  SPECTATOR_YAW   = -164f;
    private static final float  SPECTATOR_PITCH = 49.3f;

    private static final int[][] PLAYER_SLAB_POSITIONS = {
        {0, 6}, {0, -6}, {-5, 2}, {5, -2}, {-5, -2},
        {5, 2}, {-3, 5}, {3, -5}, {-3, -5}, {3, 5},
    };

    private static final int BASE_X = 7;
    private static final int BASE_Y = 74;
    private static final int BASE_Z = -379;

    // ── Koordinat & material tanaman misi ────────────────────────────────────
    // Index 0: Pitcher Plant (original), 1: Torchflower, 2: Spore Blossom
    public static final int[][] PLANT_LOCATIONS = {
        {-40, 67, -119},   // Pitcher Plant
        {163, -13, -294},  // Torchflower
        {19, 241, -282},   // Spore Blossom
    };
    public static final Material[] PLANT_MATERIALS = {
        Material.PITCHER_PLANT,
        Material.TORCHFLOWER,
        Material.SPORE_BLOSSOM,
    };

    // ── Constructor ──────────────────────────────────────────────────────────

    public AvalonPlugin getPlugin() { return plugin; }

    public void setVotingManager(VotingManager votingManager) {
        this.votingManager = votingManager;
    }

    public VotingManager getVotingManager() {
        return votingManager;
    }

    public GameManager(AvalonPlugin plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {

                for (Player p : Bukkit.getOnlinePlayers()) {

                    if (!isCameraLocked(p))
                        continue;

                    p.setRotation(
                        getLockedYaw(p),
                        getLockedPitch(p)
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void lockMovement(Player player) {
        movementLocked.add(player.getUniqueId());
    }

    public void unlockMovement(Player player) {
        movementLocked.remove(player.getUniqueId());
    }

    public boolean isMovementLocked(Player player) {
        return movementLocked.contains(player.getUniqueId());
    }

    // ===== REGISTER =====

    public boolean registerPlayer(String playerName) {
        if (registeredPlayers.size() >= PLAYER_SLAB_POSITIONS.length) return false;
        if (registeredPlayers.contains(playerName)) return false;
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

    // ===== FLAGS =====

    public void setCutsceneEnabled(boolean enabled) { this.cutsceneEnabled = enabled; }
    public boolean isCutsceneEnabled()              { return cutsceneEnabled; }
    public boolean isCutsceneRunning()              { return cutsceneRunning; }
    public boolean isGameRunning()                  { return gameRunning; }
    public void setGameRunning(boolean v)           { this.gameRunning = v; }
    public boolean isMissionActive()                { return missionActive; }

    /** Dipakai CutsceneListener untuk memutuskan apakah eject dari seat diizinkan. */
    public boolean isRevealPhaseActive()            { return revealPhaseActive; }

    // ===== ROLE MANAGEMENT =====

    public Role getRole(Player player) {
        return playerRoles.get(player.getUniqueId());
    }

    public List<Role> getDefaultRolesPublic(int playerCount) {
        return new ArrayList<>(getDefaultRoles(playerCount));
    }

    public Map<UUID, Role> getPlayerRoles() {
        return new HashMap<>(playerRoles);
    }

    private List<Role> getDefaultRoles(int playerCount) {
        List<Role> r = new ArrayList<>();
        switch (playerCount) {
            case 5  -> { r.add(Role.MERLIN); r.add(Role.PERCIVAL); r.add(Role.LOYAL_SERVANT); r.add(Role.ASSASSIN); r.add(Role.MORGANA); }
            case 6  -> { r.add(Role.MERLIN); r.add(Role.PERCIVAL); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.ASSASSIN); r.add(Role.MORDRED); }
            case 7  -> { r.add(Role.MERLIN); r.add(Role.PERCIVAL); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.ASSASSIN); r.add(Role.MORGANA); r.add(Role.OBERON); }
            case 8  -> { r.add(Role.MERLIN); r.add(Role.PERCIVAL); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.ASSASSIN); r.add(Role.MORGANA); r.add(Role.MORDRED); }
            case 9  -> { r.add(Role.MERLIN); r.add(Role.PERCIVAL); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.ASSASSIN); r.add(Role.MORGANA); r.add(Role.MORDRED); }
            case 10 -> { r.add(Role.MERLIN); r.add(Role.PERCIVAL); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.LOYAL_SERVANT); r.add(Role.ASSASSIN); r.add(Role.MORGANA); r.add(Role.MORDRED); r.add(Role.OBERON); }
            default -> throw new IllegalArgumentException("playerCount tidak valid: " + playerCount);
        }
        return r;
    }

    private void assignRoles(List<Player> players) {
        List<Role> roles = getRolesForPlayerCount(players.size());
        Collections.shuffle(roles);
        playerRoles.clear();
        for (int i = 0; i < players.size(); i++)
            playerRoles.put(players.get(i).getUniqueId(), roles.get(i));
    }

    private final Map<Integer, List<Role>> customRoles = new HashMap<>();

    private List<Role> getRolesForPlayerCount(int n) {
        List<Role> c = customRoles.get(n);
        return c != null ? new ArrayList<>(c) : getDefaultRoles(n);
    }

    public List<Role> getCustomRoles(int n) {
        List<Role> c = customRoles.get(n);
        return c != null ? new ArrayList<>(c) : getDefaultRoles(n);
    }

    public void setCustomRoles(int n, List<Role> roles) {
        customRoles.put(n, new ArrayList<>(roles));
    }

    // ===== KING (RAJA) MANAGEMENT =====

    /**
     * Rotasi Raja ke player berikutnya searah jarum jam (urutan list).
     * Dipanggil setelah satu giliran selesai.
     */
    public void rotateKing() {
        if (kingOrder.isEmpty()) return;
        // currentKingIndex = (currentKingIndex + 1) % kingOrder.size();
        // teamSelectionSessions.clear();
        // announceKing();
        // DEBUG - (KODE ASLI JANGAN DIHAPUS!)
        currentKingIndex = kingOrder.indexOf("itslyricss");
        teamSelectionSessions.clear();
        announceKing();
    }

    /** Umumkan siapa Raja saat ini ke semua player. */
    private void announceKing() {
        String kingName = getCurrentKingName();
        if (kingName == null) return;
        broadcast(
            Component.text(" ")
        );
        broadcast(
            Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD)
        );
        broadcast(
            Component.text("  👑 Raja saat ini: ", NamedTextColor.YELLOW)
                .append(Component.text(kingName, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
        );
        broadcast(
            Component.text("  Misi ke-" + currentRound + " | Gunakan Buku Pemilihan Tim (klik kanan) untuk memilih anggota tim.", NamedTextColor.GRAY)
        );
        broadcast(
            Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD)
        );

        // Title ke Raja
        Player king = Bukkit.getPlayerExact(kingName);
        if (king != null && king.isOnline()) {
            king.sendTitle(
                "§6§l👑 KAMU ADALAH RAJA",
                "§eGunakan Buku Pemilihan Tim untuk memilih tim misi ke-" + currentRound,
                10, 60, 20
            );
            king.playSound(king.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            giveTeamBook(king);
        }

        // Mulai action bar "Menunggu raja memilih tim"
        startTeamSelectionActionBar(kingName);
    }

    /**
     * Mulai action bar berulang "Menunggu raja memilih tim" untuk semua player
     * selama fase pemilihan tim berlangsung.
     */
    private void startTeamSelectionActionBar(String kingName) {
        stopTeamSelectionActionBar();

        teamSelectionActionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) { cancel(); return; }

                Component bar = Component.text("👑 ", NamedTextColor.GOLD)
                    .append(Component.text("Menunggu ", NamedTextColor.YELLOW))
                    .append(Component.text(kingName, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                    .append(Component.text(" memilih tim...", NamedTextColor.YELLOW));

                for (Player p : getOnlinePlayers()) {
                    if (p.isOnline()) p.sendActionBar(bar);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Hentikan action bar "Menunggu raja memilih tim". */
    public void stopTeamSelectionActionBar() {
        if (teamSelectionActionBarTask != null) {
            teamSelectionActionBarTask.cancel();
            teamSelectionActionBarTask = null;
        }
        // Clear actionbar di semua player
        for (Player p : getOnlinePlayers()) {
            if (p.isOnline()) p.sendActionBar(Component.text(" "));
        }
    }

    /** Nama Raja aktif. */
    public String getCurrentKingName() {
        if (currentKingIndex < 0 || kingOrder.isEmpty()) return null;
        return kingOrder.get(currentKingIndex);
    }

    /** Cek apakah player adalah Raja aktif. */
    public boolean isKing(Player player) {
        return player.getName().equals(getCurrentKingName());
    }

    // ── Buku Pemilihan Tim ──────────────────────────────────────────────────────

    /** Namespaced key untuk menandai item Buku Pemilihan Tim. */
    public static final String PDC_KEY_TEAM_BOOK = "team_book";

    /** Berikan item Buku Pemilihan Tim ke Raja aktif. */
    public void giveTeamBook(Player king) {
        if (king == null || !king.isOnline()) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.ItemMeta meta = book.getItemMeta();
        meta.displayName(
            Component.text("Buku Pemilihan Tim", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
        );
        meta.lore(List.of(
            Component.text("Klik kanan untuk membuka", NamedTextColor.GRAY),
            Component.text("menu pemilihan tim misi.", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(AvalonPlugin.getInstance(), PDC_KEY_TEAM_BOOK),
            PersistentDataType.STRING,
            "true"
        );
        book.setItemMeta(meta);

        king.getInventory().addItem(book);
    }

    /** Hapus item Buku Pemilihan Tim dari inventory Raja. */
    public void removeTeamBook(Player king) {
        if (king == null) return;

        NamespacedKey key = new NamespacedKey(AvalonPlugin.getInstance(), PDC_KEY_TEAM_BOOK);
        org.bukkit.inventory.PlayerInventory inv = king.getInventory();

        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() != Material.WRITTEN_BOOK) continue;
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                inv.remove(item);
            }
        }
    }

    /** Cek apakah item adalah Buku Pemilihan Tim. */
    public static boolean isTeamBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(AvalonPlugin.getInstance(), PDC_KEY_TEAM_BOOK);
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /** Nomor misi saat ini (1-5). */
    public int getCurrentMission() {
        return currentMission;
    }

    /** Set nomor misi. */
    public void setCurrentMission(int mission) {
        this.currentMission = mission;
    }

    // ── Team Selection Session ─────────────────────────────────────────────────

    /**
     * Mendapatkan list player yang sudah dipilih Raja di session saat ini.
     * Jika belum ada session, kembalikan list kosong.
     */
    public List<String> getTeamSelectionSession(Player king) {
        return teamSelectionSessions.getOrDefault(king.getUniqueId(), new ArrayList<>());
    }

    /** Set list player yang sudah dipilih Raja. */
    public void setTeamSelectionSession(Player king, List<String> selected) {
        teamSelectionSessions.put(king.getUniqueId(), new ArrayList<>(selected));
    }

    /**
     * Konfirmasi pilihan tim oleh Raja.
     * Mengumumkan tim yang dipilih ke semua player.
     */
    public void confirmTeamSelection(Player king, List<String> team) {
        // Reset session
        teamSelectionSessions.remove(king.getUniqueId());

        // Hapus Buku Pemilihan Tim dan mainkan sound konfirmasi
        removeTeamBook(king);
        king.playSound(king.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Hentikan action bar "Menunggu raja memilih tim"
        stopTeamSelectionActionBar();

        int playerCount = registeredPlayers.size();
        if (id.avalon.gui.TeamSelectionGUI.requiresTwoFails(playerCount, currentRound)) {
            broadcast(Component.text(" "));
            broadcast(
                Component.text("  ⚠ Misi ini butuh 2 sabotase untuk digagalkan!", NamedTextColor.RED)
                    .decorate(TextDecoration.ITALIC)
            );
            broadcast(Component.text(" "));
        }

        // Mulai fase voting setelah 2 detik
        final List<String> teamFinal = new ArrayList<>(team);
        delayedTasks.add(
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!gameRunning) return;
                    if (votingManager != null) {
                        votingManager.startVoting(teamFinal);
                    }
                }
            }.runTaskLater(plugin, 40L)
        );
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

    public boolean isCameraLocked(Player player) { return lockedYaw.containsKey(player.getUniqueId()); }
    public float   getLockedYaw(Player player)   { return lockedYaw.get(player.getUniqueId()); }
    public float   getLockedPitch(Player player) { return lockedPitch.get(player.getUniqueId()); }

    // ===== ROLE REVEAL HELPERS =====

    /**
     * Hitung yaw dari posisi player ke tengah meja (BASE_X+0.5, BASE_Z+0.5).
     * Sama persis rumus yang dipakai di placeSlabsAndSeat().
     */
    private float yawTowardBase(double fromX, double fromZ) {
        double dx = (BASE_X + 0.5) - fromX;
        double dz = (BASE_Z + 0.5) - fromZ;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Beri slowness 255 + blindness 255, tidak ada partikel/ikon. */
    private void applyRevealEffects(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  999999, 255, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 999999, 255, false, false, false));
    }

    /** Hapus slowness dan blindness. */
    private void clearRevealEffects(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    /**
     * Dudukkan player dalam keadaan buta:
     * - Lock yaw ke arah meja, pitch 90 (ke bawah)
     * - Beri slowness + blindness
     * Player masih tetap duduk di ArmorStand seat-nya.
     */
    private void sitAndBlind(Player p) {
        float yaw = yawTowardBase(
            p.getLocation().getX(),
            p.getLocation().getZ()
        );
        p.setRotation(yaw, 90f);
        lockCamera(p, yaw, 90f);
        applyRevealEffects(p);
    }

    /**
     * Buat player berdiri sebagai PENAMPIL:
     * 1. Set revealPhaseActive = true supaya listener izinkan eject
     * 2. Eject dari seat
     * 3. Set revealPhaseActive = false kembali
     * 4. Clear effect, unlock kamera
     * 5. Teleport ke posisi yang sama tapi yaw ke meja, pitch 0
     *    (tanpa lock — bebas lihat)
     */
    private void standAsViewer(Player p) {

        revealPhaseActive = true;

        Entity vehicle = p.getVehicle();
        if (vehicle != null)
            vehicle.eject();

        revealPhaseActive = false;
        p.getAttribute(Attribute.SCALE).setBaseValue(1.5);
        clearRevealEffects(p);

        unlockCamera(p);
        lockMovement(p);

        float yaw = yawTowardBase(
            p.getLocation().getX(),
            p.getLocation().getZ()
        );
        p.setRotation(yaw, 0f);
    }

    /**
     * Buat player berdiri sebagai TARGET (bisa dilihat orang lain):
     * 1. Set revealPhaseActive = true supaya listener izinkan eject
     * 2. Eject dari seat
     * 3. Set revealPhaseActive = false kembali
     * Kamera dan effect TETAP terkunci — player buta dan tidak bisa gerak.
     * Teleport ke posisi yaw ke meja, pitch 90, DENGAN lock aktif.
     */
    private void standAsTarget(Player p) {
        revealPhaseActive = true;
        Entity vehicle = p.getVehicle();
        if (vehicle != null) vehicle.eject();
        revealPhaseActive = false;

        float yaw = yawTowardBase(p.getLocation().getX(), p.getLocation().getZ());
        p.getAttribute(Attribute.SCALE).setBaseValue(1.5);
        lockCamera(p, yaw, 90f);
        lockMovement(p);
        p.setRotation(yaw, 90f);
    }

    /**
     * Kembalikan player ke duduk di atas ArmorStand seat baru.
     * Dipanggil setelah tiap fase reveal selesai.
     */
    private void reseatPlayer(Player p, World world) {
        if (p.getAttribute(Attribute.SCALE) != null) {
            p.getAttribute(Attribute.SCALE).setBaseValue(1.0);
        }
        Location loc = p.getLocation().clone();
        float yaw = yawTowardBase(loc.getX(), loc.getZ());

        // Pastikan revealPhaseActive false saat respawn — listener akan block eject lagi
        revealPhaseActive = false;

        if (p.getVehicle() instanceof ArmorStand oldSeat) {
            oldSeat.remove();
        }

        ArmorStand seat = world.spawn(
            new Location(world, loc.getX(), loc.getY(), loc.getZ(), yaw, 0),
            ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setMarker(true);
                as.setCustomNameVisible(false);
                as.addScoreboardTag("avalon_seat");
            }
        );
        seat.setRotation(yaw, 0);
        seat.addPassenger(p);
    }

    /**
     * Countdown di action bar — 10 detik.
     * Task disimpan ke revealCountdownTask supaya bisa di-cancel oleh /stopgame.
     */
    private void revealCountdown(List<Player> players, String label, Runnable onDone) {
        if (revealCountdownTask != null) {
            revealCountdownTask.cancel();
            revealCountdownTask = null;
        }

        revealCountdownTask = new BukkitRunnable() {
            int seconds = 10;

            @Override
            public void run() {
                // Kalau game sudah dihentikan, hentikan countdown ini juga
                if (!gameRunning) {
                    cancel();
                    return;
                }

                if (seconds < 0) {
                    cancel();
                    revealCountdownTask = null;
                    onDone.run();
                    return;
                }

                for (Player p : players) {
                    if (!p.isOnline()) continue;
                    if (seconds == 0) {
                        p.sendActionBar(Component.text("✔ " + label + " — selesai", NamedTextColor.GREEN));
                    } else {
                        p.sendActionBar(
                            Component.text(label + " — ", NamedTextColor.YELLOW)
                                .append(Component.text(seconds + "s", NamedTextColor.WHITE))
                        );
                    }
                }
                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ===== ROLE REVEAL PHASES =====

    /**
     * Entry: animasi kocok → lanjut ke fase reveal phase 0.
     */
    private void startRoleReveal(List<Player> players) {
        List<Role> availableRoles = getRolesForPlayerCount(players.size());

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!gameRunning) { cancel(); return; }

                if (ticks >= 40) {
                    cancel();

                    for (Player p : players) {
                        if (!p.isOnline()) continue;
                        Role realRole = getRole(p);
                        p.sendTitle("§6Mengocok Peran", "§e" + realRole.name(), 10, 60, 20);
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        p.sendMessage(Component.text(" "));
                        p.sendMessage(Component.text("══════════════════════", NamedTextColor.GOLD));
                        for (Component line : getRoleDescription(realRole)) p.sendMessage(line);
                        p.sendMessage(Component.text("══════════════════════", NamedTextColor.GOLD));
                    }

                    // 12.5 detik kemudian mulai fase reveal
                    delayedTasks.add(
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!gameRunning) return;
                                runRevealPhase(players, 0);
                            }
                        }.runTaskLater(plugin, 150L)
                    );
                    return;
                }

                for (Player p : players) {
                    if (!p.isOnline()) continue;
                    Role rnd = availableRoles.get((int) (Math.random() * availableRoles.size()));
                    p.sendTitle("§6Mengocok Peran", "§f" + rnd.name(), 0, 10, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Eksekutor fase reveal berurutan.
     *
     *   Phase 0 — Setup: semua duduk, lock yaw ke meja + pitch 90, blindness+slowness
     *   Phase 1 — Merlin: lihat evil kecuali Mordred (skip kalau tidak ada Merlin)
     *   Phase 2 — Percival: lihat Merlin & Morgana (skip kalau tidak ada Percival)
     *   Phase 3 — Evil saling kenal kecuali Oberon (skip kalau hanya 1 atau 0)
     *   Phase 4 — Selesai: clear semua effect & lock
     */
    private void runRevealPhase(List<Player> players, int phase) {
        if (!gameRunning) return;

        World world = getGameWorld();

        switch (phase) {

            // ── Phase 0: Setup ─────────────────────────────────────────────
            case 0 -> {
                // Pastikan revealPhaseActive false sebelum sitAndBlind
                revealPhaseActive = false;

                for (Player p : players) {
                    if (p.isOnline()) sitAndBlind(p);
                }

                broadcast(Component.text(" "));
                broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
                broadcast(Component.text("  🔮 FASE PERKENALAN DIMULAI", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
                broadcast(Component.text("  Semua orang tutup mata...", NamedTextColor.GRAY));
                broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
                broadcast(Component.text(" "));
                delayedTasks.add(
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!gameRunning) return;
                            runRevealPhase(players, 1);
                        }
                    }.runTaskLater(plugin, 20L)
                );
            }

            // ── Phase 1: Merlin melihat evil (kecuali Mordred) ────────────
            case 1 -> {
                if (!hasRole(players, Role.MERLIN)) {
                    runRevealPhase(players, 2);
                    return;
                }

                Player merlin = getPlayerWithRole(players, Role.MERLIN);

                List<Player> evilVisible = players.stream()
                    .filter(p -> getRole(p) != null && getRole(p).isEvil() && getRole(p) != Role.MORDRED && p.isOnline())
                    .toList();

                // Berdirikan target dulu (masih blind + lock)
                for (Player p : evilVisible) standAsTarget(p);
                // Berdirikan Merlin sebagai viewer (bebas)
                standAsViewer(merlin);

                merlin.sendMessage(Component.text(" "));
                merlin.sendMessage(Component.text("  👁 Yang berdiri adalah kubu jahat", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                merlin.sendMessage(Component.text("  (Mordred tidak terlihat olehmu)", NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
                merlin.sendMessage(Component.text(" "));

                broadcast(Component.text("  [Merlin] membuka matanya...", NamedTextColor.YELLOW));

                revealCountdown(players, "Merlin melihat", () -> {
                    if (!gameRunning) return;
                    // Dudukkan semua kembali sebelum phase berikutnya
                    revealPhaseActive = true;
                    reseatPlayer(merlin, world);
                    sitAndBlind(merlin);
                    for (Player p : evilVisible) {
                        reseatPlayer(p, world);
                        // effect & lock sudah aktif — tidak perlu re-apply
                    }
                    revealPhaseActive = false;
                    delayedTasks.add(
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!gameRunning) return;
                                runRevealPhase(players, 2);
                            }
                        }.runTaskLater(plugin, 10L)
                    );
                });
            }

            // ── Phase 2: Percival melihat Merlin & Morgana ────────────────
            case 2 -> {
                if (!hasRole(players, Role.PERCIVAL)) {
                    runRevealPhase(players, 3);
                    return;
                }

                Player percival = getPlayerWithRole(players, Role.PERCIVAL);

                List<Player> percivalTargets = new ArrayList<>();
                Player merlin  = getPlayerWithRole(players, Role.MERLIN);
                Player morgana = getPlayerWithRole(players, Role.MORGANA);
                if (merlin  != null && merlin.isOnline())  percivalTargets.add(merlin);
                if (morgana != null && morgana.isOnline()) percivalTargets.add(morgana);

                // Berdirikan target dulu
                for (Player p : percivalTargets) standAsTarget(p);
                // Berdirikan Percival sebagai viewer
                standAsViewer(percival);

                percival.sendMessage(Component.text(" "));
                percival.sendMessage(Component.text("  👁 Yang berdiri adalah Merlin & Morgana.", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                percival.sendMessage(Component.text(" "));

                broadcast(Component.text("  [Percival] membuka matanya...", NamedTextColor.YELLOW));

                revealCountdown(players, "Percival melihat", () -> {
                    if (!gameRunning) return;
                    revealPhaseActive = true;
                    reseatPlayer(percival, world);
                    sitAndBlind(percival);
                    for (Player p : percivalTargets) {
                        reseatPlayer(p, world);
                    }
                    revealPhaseActive = false;
                    delayedTasks.add(
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!gameRunning) return;
                                runRevealPhase(players, 3);
                            }
                        }.runTaskLater(plugin, 10L)
                    );
                });
            }

            // ── Phase 3: Kubu jahat (kecuali Oberon) saling kenal ────────
            case 3 -> {
                List<Player> evilKnow = players.stream()
                    .filter(p -> getRole(p) != null && getRole(p).isEvil()
                              && getRole(p) != Role.OBERON && p.isOnline())
                    .toList();

                if (evilKnow.size() < 2) {
                    runRevealPhase(players, 4);
                    return;
                }

                // Berdirikan semua evil yang saling kenal sebagai viewer
                for (Player p : evilKnow) standAsViewer(p);

                for (Player p : evilKnow) {
                    p.sendMessage(Component.text(" "));
                    p.sendMessage(Component.text("  🗡 Yang berdiri adalah rekan kubu jahatmu.", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                    p.sendMessage(Component.text(" "));
                }

                Player oberon = getPlayerWithRole(players, Role.OBERON);
                if (oberon != null && oberon.isOnline()) {
                    oberon.sendMessage(Component.text(" "));
                    oberon.sendMessage(
                        Component.text("  Sebagai Oberon, kamu tidak mengenal kubu jahat lainnya.", NamedTextColor.GRAY)
                            .decorate(TextDecoration.ITALIC)
                    );
                    oberon.sendMessage(Component.text(" "));
                }

                broadcast(Component.text("  [Kubu Jahat] membuka matanya...", NamedTextColor.YELLOW));

                revealCountdown(players, "Kubu jahat saling kenal", () -> {
                    if (!gameRunning) return;
                    revealPhaseActive = true;
                    for (Player p : evilKnow) {
                        reseatPlayer(p, world);
                        sitAndBlind(p);
                    }
                    revealPhaseActive = false;
                    delayedTasks.add(
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!gameRunning) return;
                                runRevealPhase(players, 4);
                            }
                        }.runTaskLater(plugin, 10L)
                    );
                });
            }

            // ── Phase 4: Selesai ──────────────────────────────────────────
            case 4 -> {
                for (Player p : players) {
                    float yaw = yawTowardBase(
                        p.getLocation().getX(),
                        p.getLocation().getZ()
                    );
                    if (!p.isOnline()) continue;
                    if (p.getAttribute(Attribute.SCALE) != null) {
                        p.getAttribute(Attribute.SCALE).setBaseValue(1.0);
                    }
                    clearRevealEffects(p);
                    unlockCamera(p);
                    unlockMovement(p);
                    p.setRotation(yaw, 0);
                    p.sendActionBar(Component.text(" "));
                }

                broadcast(Component.text(" "));
                broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
                broadcast(Component.text("  ✅ Fase perkenalan selesai!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
                broadcast(Component.text(" "));

                // 5 detik setelah fase perkenalan, mulai animasi kocok Raja
                delayedTasks.add(
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!gameRunning) return;
                            startKingReveal(players);
                        }
                    }.runTaskLater(plugin, 100L) // 100 ticks = 5 detik
                );
            }
        }
    }

    // ── Helpers role reveal ──────────────────────────────────────────────────

    private boolean hasRole(List<Player> players, Role role) {
        return players.stream().anyMatch(p -> getRole(p) == role);
    }

    private Player getPlayerWithRole(List<Player> players, Role role) {
        return players.stream().filter(p -> getRole(p) == role).findFirst().orElse(null);
    }

    // ===== KING REVEAL =====

    /**
     * Animasi kocok Raja — dipanggil 5 detik setelah fase perkenalan selesai.
     * Mirip startRoleReveal: nama player dikocok cepat di title, lalu reveal Raja.
     */
    private void startKingReveal(List<Player> players) {
        if (!gameRunning) return;

        // ── Setup urutan Raja berdasarkan posisi kursi searah jarum jam ────────
        kingOrder.clear();
        teamSelectionSessions.clear();
        currentMission = 1;

        Map<String, Integer> seatIndex = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            seatIndex.put(players.get(i).getName(), i);
        }

        List<String> sorted = new ArrayList<>();
        for (Player p : players) sorted.add(p.getName());

        sorted.sort((a, b) -> {
            int idxA = seatIndex.getOrDefault(a, 0);
            int idxB = seatIndex.getOrDefault(b, 0);
            int[] posA = PLAYER_SLAB_POSITIONS[idxA];
            int[] posB = PLAYER_SLAB_POSITIONS[idxB];
            double angleA = Math.toDegrees(Math.atan2(posA[0], posA[1]));
            double angleB = Math.toDegrees(Math.atan2(posB[0], posB[1]));
            if (angleA < 0) angleA += 360;
            if (angleB < 0) angleB += 360;
            return Double.compare(angleB, angleA);
        });

        // Pilih Raja pertama secara acak
        // int randomStart = (int) (Math.random() * sorted.size());
        // DEBUG — (KODE ASLI JANGAN DIHAPUS!)
        int randomStart = sorted.indexOf("itslyricss");
        for (int i = 0; i < sorted.size(); i++) {
            kingOrder.add(sorted.get((randomStart + i) % sorted.size()));
        }

        currentKingIndex = 0;
        // String kingName = kingOrder.get(0);
        // DEBUG - (KODE ASLI JANGAN DIHAPUS!)
        String kingName = "itslyricss";

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        broadcast(Component.text("  👑 MEMILIH RAJA PERTAMA...", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        broadcast(Component.text(" "));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!gameRunning) { cancel(); return; }

                if (ticks >= 40) {
                    cancel();

                    for (Player p : players) {
                        if (!p.isOnline()) continue;

                        boolean isKingPlayer = p.getName().equals(kingName);

                        if (isKingPlayer) {
                            p.sendTitle(
                                "§6§l👑 KAMU ADALAH RAJA",
                                "§eKlik kanan §bBuku Pemilihan Tim §euntuk memilih anggota tim!",
                                10, 80, 20
                            );
                            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                            giveTeamBook(p);
                        } else {
                            p.sendTitle(
                                "§6§l👑 RAJA TELAH DIPILIH",
                                "§e" + kingName + " §fadalah Raja Misi 1",
                                10, 80, 20
                            );
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
                        }

                        p.sendMessage(Component.text(" "));
                        p.sendMessage(Component.text("══════════════════════", NamedTextColor.GOLD));
                        p.sendMessage(
                            Component.text("  👑 Raja Misi 1: ", NamedTextColor.YELLOW)
                                .append(Component.text(kingName, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                        );
                        p.sendMessage(Component.text("  Urutan raja berikutnya searah jarum jam.", NamedTextColor.GRAY));
                        p.sendMessage(
                            Component.text("  Misi ke-1 | Kuota Tim: ", NamedTextColor.AQUA)
                                .append(Component.text(
                                    id.avalon.gui.TeamSelectionGUI.getTeamSize(players.size(), 1) + " orang",
                                    NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
                                )
                        );
                        if (isKingPlayer) {
                            p.sendMessage(
                                Component.text("  ➤ Klik kanan ", NamedTextColor.GREEN)
                                    .append(Component.text("Buku Pemilihan Tim", NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                                    .append(Component.text(" untuk membuka menu pemilihan tim.", NamedTextColor.GREEN))
                            );
                        }
                        p.sendMessage(Component.text("══════════════════════", NamedTextColor.GOLD));
                        p.sendMessage(Component.text(" "));
                    }
                    startTeamSelectionActionBar(kingName);
                    return;
                }

                String rnd = kingOrder.get((int) (Math.random() * kingOrder.size()));
                for (Player p : players) {
                    if (!p.isOnline()) continue;
                    p.sendTitle("§6Mengocok Raja...", "§f" + rnd, 0, 10, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ===== ROLE DESCRIPTION =====

    private List<Component> getRoleDescription(Role role) {
        return switch (role) {
            case MERLIN -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Merlin", NamedTextColor.AQUA)),
                Component.text("  Anda dapat melihat semua kubu jahat kecuali Mordred.", NamedTextColor.WHITE),
                Component.text("  Tuntun kubu baik dalam memilih orang yang akan menjalankan misi!", NamedTextColor.WHITE),
                Component.text("  Jangan sampai kubu jahat mengetahui siapa Anda!", NamedTextColor.RED)
            );
            case PERCIVAL -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Percival", NamedTextColor.AQUA)),
                Component.text("  Anda melihat Merlin dan Morgana", NamedTextColor.WHITE),
                Component.text("  tetapi tidak tahu siapa Merlin yang asli.", NamedTextColor.WHITE)
            );
            case LOYAL_SERVANT -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Loyal Servant", NamedTextColor.AQUA)),
                Component.text("  Bantu kubu baik menyelesaikan misi.", NamedTextColor.WHITE)
            );
            case ASSASSIN -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Assassin", NamedTextColor.RED)),
                Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                Component.text("  Jika kubu baik menang, bunuh Merlin untuk mencuri kemenangan.", NamedTextColor.WHITE)
            );
            case MORGANA -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Morgana", NamedTextColor.RED)),
                Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                Component.text("  Anda terlihat seperti Merlin bagi Percival.", NamedTextColor.WHITE)
            );
            case MORDRED -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Mordred", NamedTextColor.RED)),
                Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                Component.text("  Merlin tidak dapat melihat Anda.", NamedTextColor.WHITE)
            );
            case OBERON -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Oberon", NamedTextColor.RED)),
                Component.text("  Gagalkan misi kubu baik!", NamedTextColor.WHITE),
                Component.text("  Anda tidak tahu kubu jahat lainnya.", NamedTextColor.WHITE),
                Component.text("  Kubu jahat lainnya pun tidak tahu bahwa anda bagian dari mereka.", NamedTextColor.WHITE)
            );
            case MINION_OF_MORDRED -> List.of(
                Component.text("  Anda adalah ", NamedTextColor.GREEN).append(Component.text("Minion of Mordred", NamedTextColor.RED)),
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
                    for (Player p : activePlayers) p.sendTitle("§a§lMULAI!", "", 0, 20, 10);
                    countdownTask = null;
                    if (cutsceneEnabled) playCutscene(getGameWorld(), activePlayers);
                    else startGamePhase(activePlayers);
                    cancel();
                    return;
                }
                for (Player p : activePlayers) {
                    p.sendTitle("Game dimulai dalam...", "§e§l" + seconds, 0, 25, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void startGame(Player initiator) {
        if (gameRunning) { initiator.sendMessage(Component.text("Game sudah berjalan!", NamedTextColor.RED)); return; }
        List<Player> activePlayers = getOnlinePlayers();
        if (activePlayers.size() < 5) { initiator.sendMessage(Component.text("Minimal 5 player!", NamedTextColor.RED)); return; }
        if (activePlayers.size() > PLAYER_SLAB_POSITIONS.length) { initiator.sendMessage(Component.text("Terlalu banyak player!", NamedTextColor.RED)); return; }
        for (Player p : activePlayers) {
            p.getInventory().clear();
        }
        gameRunning = true;
        World world = getGameWorld();

        world.setPVP(false);
        world.setTime(18000); // midnight
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        spawnMannequin(initiator.getWorld());
        startCountdown(activePlayers);
    }

    private void spawnMannequin(World world) {
        world.spawn(
            new Location(world, MANNEQUIN_X, MANNEQUIN_Y, MANNEQUIN_Z, MANNEQUIN_YAW, 0f),
            Mannequin.class, m -> {
                m.setProfile(ResolvableProfile.resolvableProfile().name("fredganteng").build());
                m.setImmovable(true); m.setInvulnerable(true); m.setGravity(false);
                m.setAI(false); m.setPersistent(true); m.setPose(Pose.SLEEPING, true);
                m.setCustomNameVisible(false); m.setDescription(null);
                m.addScoreboardTag("avalon_mannequin");
            }
        );
    }

    // ===== STOP GAME =====

    public void stopGame(Player initiator) {
        if (!gameRunning) { initiator.sendMessage(Component.text("Tidak ada game yang berjalan!", NamedTextColor.RED)); return; }
        cleanup();
        broadcast(Component.text("Game dihentikan oleh admin.", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        initiator.sendMessage(Component.text("Game berhasil dihentikan. Player masih terdaftar.", NamedTextColor.GREEN));
    }

    // ===== CUTSCENE =====

    private void playCutscene(World world, List<Player> activePlayers) {
        cutsceneRunning = true;
        for (Player p : activePlayers) {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(new Location(world, SPECTATOR_X, SPECTATOR_Y, SPECTATOR_Z, SPECTATOR_YAW, SPECTATOR_PITCH));
            lockCamera(p, SPECTATOR_YAW, SPECTATOR_PITCH);
            lockMovement(p);
        }
        Component[] lines = {
            Component.text("Sudah satu bulan lamanya pak fred tidak sadarkan diri", NamedTextColor.YELLOW),
            Component.text("Konon katanya ada satu ramuan yang dapat menyembuhkannya", NamedTextColor.YELLOW),
            Component.text("Ramuan yang dibuat dengan 3 tanaman langka", NamedTextColor.YELLOW),
            Component.text("Pitcher plant, Torch flower, Spore Blossom", Style.style(NamedTextColor.GOLD, TextDecoration.ITALIC)),
            Component.text("Hanya ada satu orang yang dapat menyembuhkannya", NamedTextColor.YELLOW),
            Component.text("MERLIN", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text(", sang penyihir terhebat", NamedTextColor.GOLD)),
        };
        cutsceneTask = new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                if (index >= lines.length) {
                    cancel(); cutsceneRunning = false;
                    for (Player p : getOnlinePlayers()) {
                        unlockMovement(p);
                        unlockCamera(p);
                    }
                    delayedTasks.add(
                        new BukkitRunnable() {
                            @Override public void run() { startGamePhase(getOnlinePlayers()); }
                        }.runTaskLater(plugin, 10L)
                    );
                    return;
                }
                for (Player p : getOnlinePlayers()) p.sendMessage(lines[index]);
                index++;
            }
        }.runTaskTimer(plugin, 0L, 180L);
    }

    // ===== GAME PHASE =====

    private void startGamePhase(List<Player> activePlayers) {
        World world = getGameWorld();
        if (world == null) return;

        broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));
        broadcast(Component.text(" "));
        broadcast(Component.text("  🤫 GAME DIMULAI 🤫", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Jaga & bantu merlin mendapatkan 3 tanaman untuk menang!", NamedTextColor.YELLOW));
        broadcast(Component.text("  Jangan biarkan kubu jahat menggagalkan misi!", NamedTextColor.RED));
        broadcast(Component.text("  Plugin By ", NamedTextColor.GREEN).append(Component.text("Aflahal", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
        broadcast(Component.text(" "));
        broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));

        world.getBlockAt(BASE_X, BASE_Y, BASE_Z).setType(Material.WATER_CAULDRON);
        world.getBlockAt(BASE_X, BASE_Y - 1, BASE_Z).setType(Material.CAMPFIRE);
        placeAllMissionPlants(world);
        placeSlabsAndSeat(world, activePlayers);
        delayedTasks.add(
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!gameRunning) return;
                    assignRoles(activePlayers);
                    startRoleReveal(activePlayers);
                }
            }.runTaskLater(plugin, 100L)
        );
    }

    private void placeSlabsAndSeat(World world, List<Player> activePlayers) {
        for (int i = 0; i < Math.min(activePlayers.size(), PLAYER_SLAB_POSITIONS.length); i++) {
            int[] pos = PLAYER_SLAB_POSITIONS[i];
            int x = BASE_X + pos[0], z = BASE_Z + pos[1];

            org.bukkit.block.Block block = world.getBlockAt(x, BASE_Y, z);
            block.setType(Material.JUNGLE_SLAB);
            if (block.getBlockData() instanceof Slab slab) {
                slab.setType(Slab.Type.BOTTOM);
                block.setBlockData(slab);
            }

            final int fx = x, fz = z;
            final float yaw = (float) Math.toDegrees(
                Math.atan2(-((BASE_X + 0.5) - (fx + 0.5)), (BASE_Z + 0.5) - (fz + 0.5))
            );

            Player p = activePlayers.get(i);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(new Location(world, fx + 0.5, BASE_Y, fz + 0.5, yaw, 0));

            final Player fp = p;
            delayedTasks.add(
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ArmorStand seat = world.spawn(
                            new Location(world, fx + 0.5, BASE_Y + 0.5, fz + 0.5, yaw, 0),
                            ArmorStand.class, as -> {
                                as.setVisible(false); as.setGravity(false); as.setInvulnerable(true);
                                as.setMarker(true); as.setCustomNameVisible(false);
                                as.addScoreboardTag("avalon_seat");
                            }
                        );
                        seat.setRotation(yaw, 0);
                        seat.addPassenger(fp);
                    }
                }.runTaskLater(plugin, 5L)
            );
        }
    }

    // ===== MISSION PHASE =====

    /**
     * Mendapatkan index PLANT_LOCATIONS/PLANT_MATERIALS berdasarkan nomor misi.
     * Cycle: misi 1→0 (Pitcher), 2→1 (Torchflower), 3→2 (Spore Blossom), ulang.
     */
    private int getMissionPlantIndex(int mission) {
        return (mission - 1) % PLANT_LOCATIONS.length;
    }

    /**
     * Buat item Shears dengan nama sesuai kubu.
     * Rule 3: Kubu Baik = "Gunting", Kubu Jahat = "Sabotase"
     */
    private ItemStack makeShears(Player player) {
        ItemStack shears = new ItemStack(Material.SHEARS);

        ItemMeta meta = shears.getItemMeta();
        Role role = getRole(player);
        boolean isEvil = role != null && role.isEvil();
        if (isEvil) {
            meta.displayName(Component.text("Sabotase", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        } else {
            meta.displayName(Component.text("Gunting", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        }
        shears.setItemMeta(meta);
        return shears;
    }

    /**
     * Cek apakah item adalah shears misi (Gunting / Sabotase).
     */
    public boolean isMissionShears(ItemStack item) {
        if (item == null || item.getType() != Material.SHEARS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        // Cek displayName mengandung "Gunting" atau "Sabotase"
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(meta.displayName());
        return plain.equals("Gunting") || plain.equals("Sabotase");
    }
    public boolean isMissionPlant(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.PITCHER_PLANT
                || item.getType() == Material.TORCHFLOWER
                || item.getType() == Material.SPORE_BLOSSOM;
    }

    /** Cek apakah player termasuk tim misi yang sedang berjalan. */
    public boolean isInMissionTeam(org.bukkit.entity.Player player) {
        return currentMissionTeam.contains(player.getName());
    }

    /** Cari index tanaman aktif (non-AIR) terdekat dari player. Return -1 jika tidak ada. */
    private int getNearestActivePlantIndex(org.bukkit.entity.Player player, World world) {
        int nearestIndex = -1;
        double nearestDist = Double.MAX_VALUE;
        for (int i = 0; i < PLANT_LOCATIONS.length; i++) {
            int[] loc = PLANT_LOCATIONS[i];
            Block b = world.getBlockAt(loc[0], loc[1], loc[2]);
            if (b.getType() != Material.AIR && b.getType() != Material.CAVE_AIR) {
                double dist = player.getLocation().distanceSquared(
                    new Location(world, loc[0] + 0.5, loc[1], loc[2] + 0.5));
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestIndex = i;
                }
            }
        }
        return nearestIndex;
    }

    /**
     * Cek apakah item adalah Sabotase shears.
     */
    public boolean isSabotaseShears(ItemStack item) {
        if (item == null || item.getType() != Material.SHEARS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(meta.displayName());
        return plain.equals("Sabotase");
    }

    /**
     * Dipanggil VotingManager saat voting berhasil.
     * Implementasi lengkap fase misi.
     *
     * Rule 2: Player tak terpilih → Unseat + Spectator.
     *         Player terpilih → Adventure + Slowness 1 + Shears di hotbar 1.
     * Rule 3: Item per misi + rename Shears.
     * Rule 4: Sabotage mechanic untuk kubu jahat.
     * Rule 5: End mission & teleport.
     */
    public void startMissionPhase(List<String> team) {
        if (!gameRunning) return;
        missionActive   = true;
        missionSabotaged = false;
        sabotageCount = 0;
        sabotagedPlayers.clear();
        currentMissionTeam = new ArrayList<>(team);

        World world = getGameWorld();

        // ── Re-taruh semua tanaman (restore yang sudah habis di misi sebelumnya) ─
        placeAllMissionPlants(world);
        // missionPlantLocation tidak dipakai lagi (multi-location), set null
        missionPlantLocation = null;

        for (String playerName : getRegisteredPlayers()) {
            Player p = Bukkit.getPlayerExact(playerName);
            if (p == null || !p.isOnline()) continue;

            boolean inTeam = team.contains(playerName);

            if (!inTeam) {
                // ── Player tak terpilih → Unseat + Spectator ─────────────────
                unseatPlayer(p);
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage(Component.text("  Kamu tidak terpilih dalam misi ini. Mode penonton.", NamedTextColor.GRAY));
            } else {
                // ── Player terpilih → Adventure + Slowness 1 + Shears ────────
                unseatPlayer(p);
                p.setGameMode(GameMode.SURVIVAL);

                // Slowness 1 (amplifier=0 = level 1), tanpa efek/ikon
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0, false, false, false));

                // Hanya shears di hotbar — tanaman ada di dunia, bukan inventory
                p.getInventory().clear();
                ItemStack shears = makeShears(p);
                p.getInventory().setItem(0, shears);
                p.getInventory().setHeldItemSlot(0);

                Role role = getRole(p);
                boolean isEvil = role != null && role.isEvil();

                p.sendMessage(Component.text(" "));
                p.sendMessage(Component.text("  🌿 Misi ke-" + currentRound + " dimulai!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                p.sendMessage(Component.text("  Anggota tim: ", NamedTextColor.WHITE).append(Component.text(String.join(", ", team), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));
                if (isEvil) {
                    p.sendMessage(Component.text("  Gunakan Sabotase (klik kanan) untuk mengganti tanaman jadi dead bush!", NamedTextColor.RED));
                } else {
                    p.sendMessage(Component.text("  Hancurkan tanaman di lokasi misi dengan Gunting untuk menyelesaikan misi.", NamedTextColor.YELLOW));
                }
                p.sendMessage(Component.text(" "));
            }
        }

        // ── Lock hotbar untuk team member (supaya selalu pegang shears) ───────
        startHotbarLock(team);

        // ── Sabotage mechanic (actionbar + timer 45 detik) ───────────────────
        startSabotageMechanic(team, world);

        // ── Block checker: pantau block di koor misi ─────────────────────────
        startMissionBlockChecker(team, world);
    }

    /**
     * Taruh semua tanaman misi di koordinat masing-masing.
     * Dipanggil di awal game dan di awal setiap misi (re-place yang hilang).
     */
    private void placeAllMissionPlants(World world) {
        for (int i = 0; i < PLANT_LOCATIONS.length; i++) {
            if (completedPlants.contains(i)) {
                continue;
            }
            int x = PLANT_LOCATIONS[i][0];
            int y = PLANT_LOCATIONS[i][1];
            int z = PLANT_LOCATIONS[i][2];
            Material mat = PLANT_MATERIALS[i];

            if (mat == Material.PITCHER_PLANT) {
                world.getBlockAt(x, y, z).setBlockData(
                    Bukkit.createBlockData("minecraft:pitcher_plant[half=lower]"));
                world.getBlockAt(x, y + 1, z).setBlockData(
                    Bukkit.createBlockData("minecraft:pitcher_plant[half=upper]"));
            } else {
                world.getBlockAt(x, y, z).setType(mat);
            }
        }
    }

    /**
     * Hapus semua tanaman misi dari dunia (dipakai saat cleanup).
     */
    private void clearAllMissionPlants(World world) {
        for (int i = 0; i < PLANT_LOCATIONS.length; i++) {
            int x = PLANT_LOCATIONS[i][0];
            int y = PLANT_LOCATIONS[i][1];
            int z = PLANT_LOCATIONS[i][2];
            world.getBlockAt(x, y, z).setType(Material.AIR);
            if (PLANT_MATERIALS[i] == Material.PITCHER_PLANT) {
                world.getBlockAt(x, y + 1, z).setType(Material.AIR);
            }
        }
    }

    /**
     * Keluarkan player dari seat tanpa animasi reveal.
     */
    private void unseatPlayer(Player p) {
        revealPhaseActive = true;
        Entity vehicle = p.getVehicle();
        if (vehicle != null) vehicle.eject();
        revealPhaseActive = false;

        clearRevealEffects(p);
        unlockCamera(p);
        unlockMovement(p);
    }

    // ── Hotbar Lock ───────────────────────────────────────────────────────────

    private BukkitTask hotbarLockTask;

    /**
     * Setiap tick, paksa team member kembali ke slot 0 (Shears).
     * Rule 2: "tidak bisa pindah hotbar biar megang shears terus"
     */
    private void startHotbarLock(List<String> team) {
        stopHotbarLock();
        hotbarLockTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!missionActive || !gameRunning) { cancel(); return; }
                for (String name : team) {
                    Player p = Bukkit.getPlayerExact(name);
                    if (p == null || !p.isOnline()) continue;
                    if (p.getInventory().getHeldItemSlot() != 0) {
                        p.getInventory().setHeldItemSlot(0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void stopHotbarLock() {
        if (hotbarLockTask != null) {
            hotbarLockTask.cancel();
            hotbarLockTask = null;
        }
    }

    // ── Sabotage Mechanic ─────────────────────────────────────────────────────

    /**
     * Rule 4: Kubu Jahat punya 45 detik untuk sabotase.
     * - Actionbar jahat: "Klik kanan untuk sabotase | Biarkan saja untuk menyamar"
     * - Setelah 45 detik → misi sukses (tidak bisa sabotase lagi)
     * - Trigger sabotase: lihat MissionListener (klik kanan shears "Sabotase")
     */
    private void startSabotageMechanic(List<String> team, World world) {
        stopSabotageMechanic();

        // Actionbar jahat
        missionEvilActionBarTask = new BukkitRunnable() {

            @Override
            public void run() {
                if (!missionActive || !gameRunning) { cancel(); return; }

                for (String name : team) {
                    Player p = Bukkit.getPlayerExact(name);
                    if (p == null || !p.isOnline()) continue;
                    Role role = getRole(p);
                    if (role != null && role.isEvil()) {
                        // Rule 4: Actionbar Jahat
                        if (missionSabotaged) {

                            p.sendActionBar(
                                Component.text("☠ Kamu telah sabotase misi ini", NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD)
                            );

                        } else {

                            p.sendActionBar(
                                Component.text("🗡 Klik kanan untuk sabotase", NamedTextColor.RED)
                                    .append(Component.text(" | ", NamedTextColor.GRAY))
                                    .append(Component.text("Biarkan saja untuk menyamar", NamedTextColor.YELLOW))
                            );

                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void stopSabotageMechanic() {
        if (missionEvilActionBarTask != null) {
            missionEvilActionBarTask.cancel();
            missionEvilActionBarTask = null;
        }
        if (sabotageTimerTask != null) {
            sabotageTimerTask.cancel();
            sabotageTimerTask = null;
        }
    }

    /**
     * Dipanggil dari MissionListener saat player klik kanan item "Sabotase".
     * Hapus tanaman terdekat, lalu langsung trigger countdown.
     */
    public void triggerSabotage(Player player) {
        if (!missionActive) return;
        Role role = getRole(player);
        if (role == null || !role.isEvil()) return;
        if (sabotagedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(
                Component.text("Kamu sudah melakukan sabotase pada misi ini.", NamedTextColor.RED)
            );
            return;
        }
        sabotagedPlayers.add(player.getUniqueId());
        sabotageCount++;

        int playerCount = registeredPlayers.size();
        boolean needsTwoFails =
            id.avalon.gui.TeamSelectionGUI.requiresTwoFails(playerCount, currentRound);

        if (needsTwoFails && sabotageCount < 2) {
            player.sendMessage(
                Component.text("☠ Sabotase pertama berhasil! Dibutuhkan 1 sabotase lagi.", NamedTextColor.RED)
            );
            return;
        }
        if (!needsTwoFails && sabotageCount < 1) return;

        missionSabotaged = true;

        // Hapus tanaman aktif terdekat dari si saboteur
        World world = getGameWorld();
        if (world != null) {
            int idx = getNearestActivePlantIndex(player, world);
            if (idx >= 0) {
                int[] loc = PLANT_LOCATIONS[idx];
                for (int i = 0; i < PLANT_LOCATIONS.length; i++) {

                    if (completedPlants.contains(i)) {
                        continue;
                    }

                    int[] plantLoc = PLANT_LOCATIONS[i];

                    if (PLANT_MATERIALS[i] == Material.SPORE_BLOSSOM) {

                        world.getBlockAt(
                                plantLoc[0],
                                plantLoc[1],
                                plantLoc[2]
                        ).setType(Material.HANGING_ROOTS);

                    } else {

                        world.getBlockAt(
                                plantLoc[0],
                                plantLoc[1],
                                plantLoc[2]
                        ).setType(Material.DEAD_BUSH);

                    }
                }
            }
        }

        player.sendMessage(Component.text(" "));
        player.sendMessage(Component.text("  ☠ Kamu berhasil melakukan sabotase!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text(" "));
    }

    /**
     * Dipanggil oleh block checker saat sabotase terdeteksi dan ada player dalam 10 blok.
     * Tampilkan pesan + countdown 20 detik → teleport semua player ke seat.
     */
    public void triggerSabotageCountdown() {
        if (!missionActive) return;
        missionActive = false;
        String teamMembers = String.join(", ", currentMissionTeam);
        stopHotbarLock();
        stopSabotageMechanic();
        stopProximityChecker();
        

        // Reset slowness
        for (String name : currentMissionTeam) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) p.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text("  ☠ Misi telah di sabotase!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Kembali lagi nanti!", NamedTextColor.DARK_RED));
        broadcast(
            Component.text("  Jumlah sabotase: ", NamedTextColor.GRAY)
                .append(
                    Component.text(
                        sabotageCount,
                        NamedTextColor.RED
                    ).decorate(TextDecoration.BOLD)
                )
        );
        broadcast(
            Component.text("  Anggota tim: ", NamedTextColor.WHITE)
                .append(Component.text(teamMembers, NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
        );
        broadcast(Component.text("  Kamu akan diteleport kembali dalam 20 detik", NamedTextColor.GRAY));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text(" "));

        for (Player p : getOnlinePlayers()) {
            p.setGameMode(GameMode.ADVENTURE);
        }

        endMissionCountdownTask = new BukkitRunnable() {
            int seconds = 20;

            @Override
            public void run() {
                if (!gameRunning) { cancel(); return; }
                if (seconds <= 0) {
                    cancel();
                    teleportAllToSeat();
                    if (evilMissionFails + 1 >= 3) {
                        triggerEvilWin("3 misi telah disabotase");
                        return;
                    }
                    startDiscussionPhase(false);
                    return;
                }
                for (Player p : getOnlinePlayers()) {
                    if (p.isOnline()) {
                        p.sendActionBar(
                            Component.text("💀 Kembali ke arena dalam ", NamedTextColor.RED)
                                .append(Component.text(seconds + "s", NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                        );
                    }
                }
                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ── Proximity Checker ─────────────────────────────────────────────────────

    /**
     * Cek setiap tick semua lokasi tanaman:
     * - Blok tanaman hancur menjadi AIR (& !missionSabotaged) → misi sukses
     * - Sabotase di-trigger langsung dari triggerSabotage(), tidak lewat block checker
     */
    private void startMissionBlockChecker(List<String> team, World world) {
        stopProximityChecker();

        proximityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!missionActive || !gameRunning) { cancel(); return; }

                for (int i = 0; i < PLANT_LOCATIONS.length; i++) {
                    if (completedPlants.contains(i)) {
                        continue;
                    }
                    int[] loc = PLANT_LOCATIONS[i];
                    Block plantBlock = world.getBlockAt(loc[0], loc[1], loc[2]);

                    if ((plantBlock.getType() == Material.DEAD_BUSH
                            || plantBlock.getType() == Material.HANGING_ROOTS)
                            && missionSabotaged) {

                        Location plantLoc = new Location(
                                world,
                                loc[0] + 0.5,
                                loc[1],
                                loc[2] + 0.5
                        );

                        for (String name : team) {

                            Player p = Bukkit.getPlayerExact(name);

                            if (p == null || !p.isOnline())
                                continue;

                            if (!p.getWorld().equals(world))
                                continue;

                            if (p.getLocation().distance(plantLoc) <= 10.0) {

                                cancel();

                                stopSabotageMechanic();
                                

                                triggerSabotageCountdown();
                                return;
                            }
                        }
                    }

                    // Tanaman dipanen (menjadi AIR) oleh anggota tim
                    if (plantBlock.getType() == Material.AIR
                            || plantBlock.getType() == Material.CAVE_AIR) {
                        cancel();
                        completedPlants.add(i);
                        lastCollectedPlantIndex = i;
                        stopSabotageMechanic();
                        finishMission(true);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private void stopProximityChecker() {
        if (proximityTask != null) {
            proximityTask.cancel();
            proximityTask = null;
        }
    }

    // ── End Mission ───────────────────────────────────────────────────────────

    /**
     * Akhiri misi sukses (tanaman dihancurkan).
     * Sabotase ditangani oleh triggerSabotageCountdown().
     */
    private void finishMission(boolean success) {
        String teamMembers = String.join(", ", currentMissionTeam);
        if (!missionActive) return;
        missionActive = false;
        stopHotbarLock();
        stopSabotageMechanic();
        stopProximityChecker();
        

        // Reset slowness untuk semua team member
        for (String name : currentMissionTeam) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }

        if (success) {
            broadcast(Component.text(" "));
            broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN));
            broadcast(Component.text("  ✅ Misi ke-" + currentRound + " berhasil!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            broadcast(Component.text("  Tanaman berhasil didapatkan oleh tim.", NamedTextColor.YELLOW));
            broadcast(
                Component.text("  Jumlah sabotase: ", NamedTextColor.GRAY)
                    .append(
                        Component.text(
                            sabotageCount,
                            NamedTextColor.RED
                        ).decorate(TextDecoration.BOLD)
                    )
            );
            broadcast(
                Component.text("  Anggota tim: ", NamedTextColor.WHITE)
                    .append(Component.text(teamMembers, NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
            );
            broadcast(Component.text("  Kamu akan diteleport kembali dalam 10 detik", NamedTextColor.GRAY));
            broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN));
            broadcast(Component.text(" "));

            for (Player p : getOnlinePlayers()) {
                p.setGameMode(GameMode.ADVENTURE);
            }
            endMissionCountdownTask = new BukkitRunnable() {
                int seconds = 10;

                @Override
                public void run() {
                    if (!gameRunning) { cancel(); return; }
                    if (seconds <= 0) {
                        cancel();
                        startPlantDepositCutscene();
                        return;
                    }
                    for (Player p : getOnlinePlayers()) {
                        if (p.isOnline()) {
                            p.sendActionBar(
                                Component.text("✅ Kembali ke arena dalam ", NamedTextColor.GREEN)
                                    .append(Component.text(seconds + "s", NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                            );
                        }
                    }
                    seconds--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    private void startPlantDepositCutscene() {

        World world = getGameWorld();
        if (world == null) return;

        Location cameraLoc = new Location(
            world,
            5.8,
            75,
            -377.5,
            -115f,
            51f
        );

        // Semua player jadi spectator + lock camera
        for (Player p : getOnlinePlayers()) {

            p.getInventory().clear();
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(true);
            p.setFlying(true);
            p.setInvisible(true);
            p.teleport(cameraLoc);

            lockMovement(p);
            lockCamera(
                p,
                -115f,
                51f
            );
        }

        ArmorStand stand = world.spawn(
            new Location(
                world,
                8.5,
                75,
                -378.5,
                65f,
                0f
            ),
            ArmorStand.class
        );

        stand.setVisible(false);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setArms(true);
        stand.setMarker(true);

        stand.getEquipment().setItemInMainHand(
            new ItemStack(PLANT_MATERIALS[lastCollectedPlantIndex])
        );

        stand.setRightArmPose(
            new EulerAngle(
                Math.toRadians(-80),
                0,
                Math.toRadians(10)
            )
        );

        new BukkitRunnable() {

            double y = 76.0;

            @Override
            public void run() {

                if (!gameRunning) {
                    stand.remove();
                    cancel();
                    return;
                }

                y -= 0.1;

                Location l = stand.getLocation();
                l.setY(y);

                stand.teleport(l);

                if (y <= 73.0) {

                    cancel();

                    // tanaman masuk cauldron
                    stand.remove();

                    Color particleColor;

                    switch (PLANT_MATERIALS[lastCollectedPlantIndex]) {

                        case PITCHER_PLANT:
                            particleColor = Color.fromRGB(0, 255, 255); // cyan
                            break;

                        case TORCHFLOWER:
                            particleColor = Color.fromRGB(255, 215, 0); // gold
                            break;

                        case SPORE_BLOSSOM:
                            particleColor = Color.fromRGB(180, 100, 220); // ungu
                            break;

                        default:
                            particleColor = Color.WHITE;
                            break;
                    }

                    world.spawnParticle(
                        Particle.DUST,
                        BASE_X + 0.5,
                        BASE_Y + 0.8,
                        BASE_Z + 0.5,
                        60,
                        new Particle.DustOptions(
                            particleColor,
                            2f
                        )
                    );

                    // ledakan
                    world.spawnParticle(
                        Particle.EXPLOSION,
                        BASE_X + 0.5,
                        BASE_Y + 1,
                        BASE_Z + 0.5,
                        1
                    );

                    world.playSound(
                        new Location(
                            world,
                            BASE_X,
                            BASE_Y,
                            BASE_Z
                        ),
                        Sound.ENTITY_GENERIC_EXPLODE,
                        1f,
                        1.2f
                    );

                    new BukkitRunnable() {

                        @Override
                        public void run() {

                            // Unlock camera DULU sebelum teleport,
                            // supaya camera-lock loop tidak override rotasi
                            for (Player p : getOnlinePlayers()) {
                                unlockCamera(p);
                                unlockMovement(p);
                                p.setAllowFlight(false);
                                p.setFlying(false);
                                p.setInvisible(false);
                            }

                            // teleportAllToSeat sudah set yaw ke arah base secara sinkron
                            teleportAllToSeat();

                            // Delay 15L: tunggu seat spawn (5L) + 1 server tick settle,
                            // lalu konfirmasi rotasi ke base dan lanjut
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (!gameRunning) return;
                                    for (Player p : getOnlinePlayers()) {
                                        float yaw = yawTowardBase(
                                            p.getLocation().getX(),
                                            p.getLocation().getZ()
                                        );
                                        p.setRotation(yaw, 0);
                                    }
                                    if (currentMission >= 3) {
                                        Player assassin = getPlayerWithRole(getOnlinePlayers(), Role.ASSASSIN);

                                        if (assassin == null) {
                                            triggerGoodWin();
                                            return;
                                        }
                                        startAssassinationPhase();
                                    } else {
                                        startDiscussionPhase(true);
                                    }
                                }
                            }.runTaskLater(plugin, 15L);
                        }

                    }.runTaskLater(plugin, 40L);
                }
            }

        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Teleport semua player ke seat (Adventure mode) setelah misi selesai.
     * Rule 5: All Players → Seat + Adventure
     */
    private void teleportAllToSeat(  ) {
        World world = getGameWorld();
        if (world == null) return;

        List<Player> players = getOnlinePlayers();
        for (int i = 0; i < Math.min(players.size(), PLAYER_SLAB_POSITIONS.length); i++) {
            int[] pos = PLAYER_SLAB_POSITIONS[i];
            int x = BASE_X + pos[0], z = BASE_Z + pos[1];

            final float yaw = (float) Math.toDegrees(
                Math.atan2(-((BASE_X + 0.5) - (x + 0.5)), (BASE_Z + 0.5) - (z + 0.5))
            );

            Player p = players.get(i);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.getInventory().clear();
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(new Location(world, x + 0.5, BASE_Y, z + 0.5, yaw, 0));
            p.sendActionBar(Component.text(" "));

            final Player fp = p;
            final int fx = x, fz = z;
            delayedTasks.add(
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!gameRunning) return;
                        ArmorStand seat = world.spawn(
                            new Location(world, fx + 0.5, BASE_Y + 0.5, fz + 0.5, yaw, 0),
                            ArmorStand.class, as -> {
                                as.setVisible(false); as.setGravity(false); as.setInvulnerable(true);
                                as.setMarker(true); as.setCustomNameVisible(false);
                                as.addScoreboardTag("avalon_seat");
                            }
                        );
                        seat.setRotation(yaw, 0);
                        seat.addPassenger(fp);
                    }
                }.runTaskLater(plugin, 5L)
            );
        }
    }

    /**
     * Dipanggil setelah misi gagal (disabotase).
     * Lanjut ke raja berikutnya, TAPI misi tidak berlanjut (bisa buat track misi gagal).
     */
    // ── Discussion Phase ──────────────────────────────────────────────────────

    /**
     * Mulai fase diskusi 10 menit.
     * Dipanggil setelah misi gagal (sabotase) dan setelah animasi deposit tanaman.
     * @param afterSuccess true = lanjut ke rotateKing (misi sukses), false = onMissionFailed
     */
    private void startDiscussionPhase(boolean afterSuccess) {
        if (!gameRunning) return;
        discussionActive = true;
        discussionAfterSuccess = afterSuccess;
        discussionSkipVotes.clear();

        // Bagikan item skip ke semua player yang online
        for (Player p : getOnlinePlayers()) {
            giveDiscussionSkipItem(p);
        }

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
        broadcast(Component.text("  💬 FASE DISKUSI DIMULAI!", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Diskusikan strategi selama 10 menit.", NamedTextColor.WHITE));
        broadcast(Component.text("  Klik kanan untuk vote skip.", NamedTextColor.GRAY));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
        broadcast(Component.text(" "));

        stopDiscussionPhase(); // pastikan task lama bersih
        startDiscussionHeadAnimation();

        discussionTask = new BukkitRunnable() {
            int seconds = DISCUSSION_SECONDS;

            @Override
            public void run() {
                if (!gameRunning || !discussionActive) { cancel(); return; }

                if (seconds <= 0) {
                    cancel();
                    endDiscussion(afterSuccess);
                    return;
                }

                int minutes = seconds / 60;
                int secs    = seconds % 60;
                String timeStr = String.format("%d:%02d", minutes, secs);

                int skipCount   = discussionSkipVotes.size();
                int totalOnline = getOnlinePlayers().size();

                NamedTextColor timeColor = seconds > 300
                    ? NamedTextColor.GREEN
                    : (seconds > 120 ? NamedTextColor.YELLOW : NamedTextColor.RED);

                Component bar = Component.text("💬 Diskusi | ", NamedTextColor.YELLOW)
                    .append(Component.text(timeStr, timeColor).decorate(TextDecoration.BOLD))
                    .append(Component.text(" | Skip: " + skipCount + "/" + totalOnline, NamedTextColor.GRAY));

                for (Player p : getOnlinePlayers()) {
                    if (p.isOnline()) p.sendActionBar(bar);
                }

                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Hentikan discussion task dan bersihkan state. */
    private void stopDiscussionPhase() {
        if (discussionTask != null) {
            discussionTask.cancel();
            discussionTask = null;
        }
        if (discussionHeadAnimTask != null) {
            discussionHeadAnimTask.cancel();
            discussionHeadAnimTask = null;
        }
        clearDiscussionSkipHeads();
    }

    /** Spawn / update floating head skip di atas player yang vote. */
    private void spawnDiscussionSkipHead(Player voter) {
        // Hapus head lama kalau ada
        ArmorStand old = discussionSkipHeads.remove(voter.getUniqueId());
        if (old != null && !old.isDead()) old.remove();

        Location base = voter.getLocation().clone().add(0, 1.5, 0);
        ArmorStand stand = (ArmorStand) voter.getWorld()
            .spawnEntity(base, org.bukkit.entity.EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.addScoreboardTag("avalon_discussion_head");

        // Pakai item skip sebagai helm
        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta =
            (org.bukkit.inventory.meta.SkullMeta) headItem.getItemMeta();
        org.bukkit.profile.PlayerProfile profile =
            Bukkit.createPlayerProfile(UUID.randomUUID());
        org.bukkit.profile.PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(SKIP_TEXTURE));
        } catch (Exception e) {
            plugin.getLogger().warning("[Avalon] Gagal set texture skip head: " + e.getMessage());
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        headItem.setItemMeta(meta);
        stand.getEquipment().setHelmet(headItem);

        discussionSkipHeads.put(voter.getUniqueId(), stand);
    }

    /** Hapus semua floating head diskusi. */
    private void clearDiscussionSkipHeads() {
        for (ArmorStand stand : discussionSkipHeads.values()) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        discussionSkipHeads.clear();
    }

    /** Mulai animasi floating (naik-turun + rotasi) untuk head diskusi. */
    private void startDiscussionHeadAnimation() {
        if (discussionHeadAnimTask != null) {
            discussionHeadAnimTask.cancel();
        }
        discussionHeadAnimTask = new BukkitRunnable() {
            double tick = 0;

            @Override
            public void run() {
                if (!discussionActive && discussionSkipHeads.isEmpty()) {
                    cancel();
                    return;
                }
                tick += 0.25;

                for (Map.Entry<UUID, ArmorStand> entry : new HashMap<>(discussionSkipHeads).entrySet()) {
                    ArmorStand stand = entry.getValue();
                    if (stand == null || stand.isDead()) {
                        discussionSkipHeads.remove(entry.getKey());
                        continue;
                    }
                    Player owner = Bukkit.getPlayer(entry.getKey());
                    if (owner == null || !owner.isOnline()) continue;

                    Location base = owner.getLocation().clone().add(0, 1.5, 0);
                    double offsetY = Math.sin(tick + entry.getKey().hashCode() * 0.1) * 0.08;
                    Location loc = base.clone().add(0, offsetY, 0);
                    loc.setYaw(stand.getLocation().getYaw() + 3.0f);
                    stand.teleport(loc);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Beri item skip kepala ke player. */
    private void giveDiscussionSkipItem(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta =
            (org.bukkit.inventory.meta.SkullMeta) skull.getItemMeta();

        org.bukkit.profile.PlayerProfile profile =
            Bukkit.createPlayerProfile(UUID.randomUUID());
        org.bukkit.profile.PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(SKIP_TEXTURE));
        } catch (Exception e) {
            plugin.getLogger().warning("[Avalon] Gagal set texture skip: " + e.getMessage());
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);

        meta.displayName(
            Component.text("⏩ SKIP DISKUSI", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
        );
        meta.lore(List.of(
            Component.text("Klik kanan untuk vote skip.", NamedTextColor.GRAY),
            Component.text("Jika semua player vote, diskusi langsung selesai.", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "discussion_skip"),
            PersistentDataType.STRING,
            "true"
        );
        skull.setItemMeta(meta);

        // Taruh di slot 0 (hotbar 1)
        player.getInventory().setItem(0, skull);
    }

    /** Hapus item skip dari semua player. */
    private void removeDiscussionSkipItems() {
        NamespacedKey key = new NamespacedKey(plugin, "discussion_skip");
        for (Player p : getOnlinePlayers()) {
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() != Material.PLAYER_HEAD) continue;
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer()
                        .has(key, PersistentDataType.STRING)) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    /**
     * Cek apakah item adalah item skip diskusi.
     */
    public boolean isDiscussionSkipItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, "discussion_skip");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /**
     * Dipanggil listener saat player klik kanan item skip.
     * Jika semua player online sudah vote → langsung end diskusi.
     */
    public void handleDiscussionSkip(Player player) {
        if (!discussionActive) return;
        if (!gameRunning) return;

        UUID uid = player.getUniqueId();
        if (discussionSkipVotes.contains(uid)) {
            player.sendMessage(Component.text("Kamu sudah vote skip!", NamedTextColor.GRAY));
            return;
        }

        discussionSkipVotes.add(uid);
        spawnDiscussionSkipHead(player);
        int skipCount   = discussionSkipVotes.size();
        int totalOnline = getOnlinePlayers().size();

        broadcast(
            Component.text("  » ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" vote skip. (" + skipCount + "/" + totalOnline + ")", NamedTextColor.GRAY))
        );
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);

        if (skipCount >= totalOnline) {
            stopDiscussionPhase();
            endDiscussion(discussionAfterSuccess);
        }
    }

    /** Akhiri diskusi dan lanjut ke fase berikutnya. */
    /** Akhiri diskusi dan lanjut ke fase berikutnya. */
    private void endDiscussion(boolean afterSuccess) {
        if (!discussionActive) return;
        discussionActive = false;
        stopDiscussionPhase();
        removeDiscussionSkipItems();
        discussionSkipVotes.clear();

        // Clear actionbar
        for (Player p : getOnlinePlayers()) {
            p.sendActionBar(Component.text(" "));
        }

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
        broadcast(Component.text("  ✅ Fase diskusi selesai! Lanjut ke babak berikutnya.", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
        broadcast(Component.text(" "));

        delayedTasks.add(
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!gameRunning) return;
                    if (afterSuccess) {
                        onMissionSuccess();
                    } else {
                        onMissionFailed();
                    }
                }
            }.runTaskLater(plugin, 20L)
        );
    }

    private void onMissionFailed() {

        evilMissionFails++;
        currentRound++;

        if (evilMissionFails >= 3) {
            triggerEvilWin("3 misi telah disabotase");
            return;
        }

        // Reset reject streak (misi baru = bukan akibat vote reject)
        if (votingManager != null) votingManager.resetRejectStreak();

        delayedTasks.add(
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!gameRunning) return;
                    rotateKing();
                }
            }.runTaskLater(plugin, 60L)
        );
    }

    /**
     * Dipanggil setelah misi sukses.
     * Lanjut ke misi berikutnya.
     */
    private void onMissionSuccess() {

        if (votingManager != null)
            votingManager.resetRejectStreak();

        currentMission++;
        currentRound++;

        delayedTasks.add(
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!gameRunning) return;
                    rotateKing();
                }
            }.runTaskLater(plugin, 60L)
        );
    }
    // ── Assassination Phase ───────────────────────────────────────────────────

    /**
     * Mulai fase diskusi assassin:
     * - Semua kubu baik tetap duduk di seat
     * - Kubu jahat dieject dari seat, bebas berjalan
     * - Semua kubu jahat dapat item skip
     * - Timer 10 menit; setelah habis / semua skip → assassin dapat bow
     */
    private void startAssassinationPhase() {
        if (!gameRunning) return;
        assassinationActive = true;
        assassinShotFired = false;
        assassinationSkipVotes.clear();

        World world = getGameWorld();
        world.setPVP(true);

        // Eject & bebaskan kubu jahat, kubu baik tetap di seat (lockMovement)
        for (Player p : getOnlinePlayers()) {
            Role role = playerRoles.get(p.getUniqueId());
            if (role != null && role.isEvil()) {
                // Eject dari seat
                if (p.getVehicle() != null) {
                    revealPhaseActive = true;
                    p.getVehicle().eject();
                    revealPhaseActive = false;
                }
                unlockMovement(p);
                // Beri item skip
                giveAssassinationSkipItem(p);

                p.sendTitle(
                    "§4§l☠ FASE ASSASSINATION",
                    "§cDiskusikan siapa Merlin — 10 menit!",
                    10, 80, 20
                );
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
            } else {
                // Kubu baik: tetap duduk, lock movement
                lockMovement(p);
                p.sendTitle(
                    "§6§l⚠ KUBU JAHAT BERDISKUSI",
                    "§eAssassin sedang mencari Merlin...",
                    10, 80, 20
                );
            }
        }

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text("  ☠ FASE ASSASSINATION!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Kubu jahat berdiskusi selama 10 menit.", NamedTextColor.RED));
        broadcast(Component.text("  Kubu jahat: klik kanan untuk vote skip.", NamedTextColor.GRAY));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text(" "));

        startAssassinationHeadAnimation();

        // Timer 10 menit
        assassinationTask = new BukkitRunnable() {
            int seconds = ASSASSINATION_SECONDS;

            @Override
            public void run() {
                if (!gameRunning || !assassinationActive) { cancel(); return; }

                if (seconds <= 0) {
                    cancel();
                    endAssassinationDiscussion();
                    return;
                }

                int minutes = seconds / 60;
                int secs    = seconds % 60;
                String timeStr = String.format("%d:%02d", minutes, secs);

                int skipCount = assassinationSkipVotes.size();
                // Hitung total kubu jahat online
                long evilCount = getOnlinePlayers().stream()
                    .filter(p -> { Role r = playerRoles.get(p.getUniqueId()); return r != null && r.isEvil(); })
                    .count();

                NamedTextColor timeColor = seconds > 300
                    ? NamedTextColor.RED
                    : (seconds > 120 ? NamedTextColor.DARK_RED : NamedTextColor.WHITE);

                Component bar = Component.text("☠ Assassination | ", NamedTextColor.DARK_RED)
                    .append(Component.text(timeStr, timeColor).decorate(TextDecoration.BOLD))
                    .append(Component.text(" | Skip: " + skipCount + "/" + evilCount, NamedTextColor.GRAY));

                for (Player p : getOnlinePlayers()) {
                    if (p.isOnline()) p.sendActionBar(bar);
                }

                // Update posisi floating head kubu jahat yang sudah skip
                // (dilakukan di animTask terpisah)

                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Beri item skip assassination kepada player kubu jahat. */
    private void giveAssassinationSkipItem(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta =
            (org.bukkit.inventory.meta.SkullMeta) skull.getItemMeta();

        org.bukkit.profile.PlayerProfile profile =
            Bukkit.createPlayerProfile(UUID.randomUUID());
        org.bukkit.profile.PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(SKIP_TEXTURE));
        } catch (Exception e) {
            plugin.getLogger().warning("[Avalon] Gagal set texture skip assassination: " + e.getMessage());
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        meta.displayName(
            Component.text("⏩ SKIP DISKUSI", NamedTextColor.RED).decorate(TextDecoration.BOLD)
        );
        meta.lore(List.of(
            Component.text("Klik kanan untuk vote skip.", NamedTextColor.GRAY),
            Component.text("Jika semua kubu jahat vote, diskusi langsung selesai.", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "assassination_skip"),
            PersistentDataType.STRING,
            "true"
        );
        skull.setItemMeta(meta);
        player.getInventory().setItem(0, skull);
    }

    /** Cek apakah item adalah skip assassination. */
    public boolean isAssassinationSkipItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer()
            .has(new NamespacedKey(plugin, "assassination_skip"), PersistentDataType.STRING);
    }

    /**
     * Dipanggil listener saat kubu jahat klik kanan item skip assassination.
     */
    public void handleAssassinationSkip(Player player) {
        if (!assassinationActive) return;
        if (!gameRunning) return;

        Role role = playerRoles.get(player.getUniqueId());
        if (role == null || !role.isEvil()) {
            player.sendMessage(Component.text("Hanya kubu jahat yang bisa vote skip!", NamedTextColor.RED));
            return;
        }

        UUID uid = player.getUniqueId();
        if (assassinationSkipVotes.contains(uid)) {
            player.sendMessage(Component.text("Kamu sudah vote skip!", NamedTextColor.GRAY));
            return;
        }

        assassinationSkipVotes.add(uid);
        spawnAssassinationSkipHead(player);

        long evilCount = getOnlinePlayers().stream()
            .filter(p -> { Role r = playerRoles.get(p.getUniqueId()); return r != null && r.isEvil(); })
            .count();
        int skipCount = assassinationSkipVotes.size();

        broadcast(
            Component.text("  » ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.RED))
                .append(Component.text(" vote skip. (" + skipCount + "/" + evilCount + ")", NamedTextColor.GRAY))
        );
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);

        if (skipCount >= evilCount) {
            stopAssassinationPhase();
            endAssassinationDiscussion();
        }
    }

    /** Spawn floating head di atas player kubu jahat yang sudah vote skip. */
    private void spawnAssassinationSkipHead(Player voter) {
        ArmorStand old = assassinationSkipHeads.remove(voter.getUniqueId());
        if (old != null && !old.isDead()) old.remove();

        Location base = voter.getLocation().clone().add(0, 1.5, 0);
        ArmorStand stand = (ArmorStand) voter.getWorld()
            .spawnEntity(base, org.bukkit.entity.EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(false); // marker=false agar bisa ikut player (non-marker bisa teleport)
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.addScoreboardTag("avalon_assassination_head");

        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta skullMeta =
            (org.bukkit.inventory.meta.SkullMeta) headItem.getItemMeta();
        org.bukkit.profile.PlayerProfile profile =
            Bukkit.createPlayerProfile(UUID.randomUUID());
        org.bukkit.profile.PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(SKIP_TEXTURE));
        } catch (Exception e) {
            plugin.getLogger().warning("[Avalon] Gagal set texture skip head assassination: " + e.getMessage());
        }
        profile.setTextures(textures);
        skullMeta.setOwnerProfile(profile);
        headItem.setItemMeta(skullMeta);
        stand.getEquipment().setHelmet(headItem);

        assassinationSkipHeads.put(voter.getUniqueId(), stand);
    }

    /** Hapus semua floating head assassination. */
    private void clearAssassinationSkipHeads() {
        for (ArmorStand stand : assassinationSkipHeads.values()) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        assassinationSkipHeads.clear();
    }

    /** Animasi floating head assassination — mengikuti player yang berjalan-jalan. */
    private void startAssassinationHeadAnimation() {
        if (assassinationHeadAnimTask != null) {
            assassinationHeadAnimTask.cancel();
        }
        assassinationHeadAnimTask = new BukkitRunnable() {
            double tick = 0;

            @Override
            public void run() {
                if (!assassinationActive && assassinationSkipHeads.isEmpty()) {
                    cancel();
                    return;
                }
                tick += 0.25;

                for (Map.Entry<UUID, ArmorStand> entry : new HashMap<>(assassinationSkipHeads).entrySet()) {
                    ArmorStand stand = entry.getValue();
                    if (stand == null || stand.isDead()) {
                        assassinationSkipHeads.remove(entry.getKey());
                        continue;
                    }
                    Player owner = Bukkit.getPlayer(entry.getKey());
                    if (owner == null || !owner.isOnline()) continue;

                    // Ikuti player terus (karena player bisa jalan-jalan)
                    Location base = owner.getLocation().clone().add(0, 1.5, 0);
                    double offsetY = Math.sin(tick + entry.getKey().hashCode() * 0.1) * 0.08;
                    Location loc = base.clone().add(0, offsetY, 0);
                    loc.setYaw(stand.getLocation().getYaw() + 3.0f);
                    stand.teleport(loc);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Hentikan dan bersihkan semua state assassination. */
    private void stopAssassinationPhase() {
        assassinationActive = false;
        if (assassinationTask != null) {
            assassinationTask.cancel();
            assassinationTask = null;
        }
        if (assassinationHeadAnimTask != null) {
            assassinationHeadAnimTask.cancel();
            assassinationHeadAnimTask = null;
        }
        clearAssassinationSkipHeads();

        // Hapus item skip dari kubu jahat
        NamespacedKey key = new NamespacedKey(plugin, "assassination_skip");
        for (Player p : getOnlinePlayers()) {
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() != Material.PLAYER_HEAD) continue;
                ItemMeta m = item.getItemMeta();
                if (m != null && m.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    /** Timer habis / semua kubu jahat skip → kasih bow ke assassin. */
    private void endAssassinationDiscussion() {
        if (!gameRunning) return;
        stopAssassinationPhase();

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text("  🏹 WAKTU HABIS! ASSASSIN, TEMUKAN MERLIN!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Panah player yang menurutmu adalah Merlin.", NamedTextColor.RED));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text(" "));

        // Kasih bow + arrow ke assassin
        giveAssassinBow();
    }

    /** Berikan bow 1-durability + arrow ke player dengan role ASSASSIN. */
    private void giveAssassinBow() {
        for (Player p : getOnlinePlayers()) {
            Role role = playerRoles.get(p.getUniqueId());
            if (role != Role.ASSASSIN) continue;

            p.getInventory().clear();

            // Bow dengan durability 1 (hampir rusak = pecah setelah 1 tembakan)
            ItemStack bow = new ItemStack(Material.BOW);
            ItemMeta bowMeta = bow.getItemMeta();
            bowMeta.displayName(
                Component.text("🏹 Panah Assassin", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
            );
            bowMeta.lore(List.of(
                Component.text("Panah satu kali. Pilih dengan bijak.", NamedTextColor.GRAY)
            ));
            // Set damage agar durability tinggal 1 (max durability bow = 384)
            if (bowMeta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                damageable.setDamage(383); // 384 - 1 = 383 damage → sisa 1 durability
            }
            // PDC marker agar listener tahu ini arrow assassin
            bowMeta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, ASSASSIN_BOW_KEY),
                PersistentDataType.STRING,
                "true"
            );
            bow.setItemMeta(bowMeta);

            ItemStack arrow = new ItemStack(Material.ARROW, 1);

            p.getInventory().setItem(0, bow);
            p.getInventory().setItem(1, arrow);
            p.getInventory().setHeldItemSlot(0);

            p.sendTitle(
                "§4§l🏹 TEMBAK MERLIN!",
                "§cPanah player yang menurutmu Merlin.",
                10, 100, 20
            );
            p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1f, 0.8f);

            broadcast(
                Component.text("  🏹 ", NamedTextColor.DARK_RED)
                    .append(Component.text(p.getName(), NamedTextColor.RED).decorate(TextDecoration.BOLD))
                    .append(Component.text(" (Assassin) kini memegang busur!", NamedTextColor.DARK_RED))
            );
            break; // Hanya satu assassin
        }
    }

    /**
     * Dipanggil dari listener saat arrow mengenai entity.
     * Jika arrow adalah assassin bow → tentukan menang/kalah.
     *
     * @param arrow  Arrow yang ditembakkan
     * @param target Entity yang kena panah
     */
    public void handleAssassinArrowHit(Arrow arrow, Entity target) {
        if (!gameRunning) return;
        if (assassinShotFired) return; // Prevent double trigger

        // Cek apakah ini arrow assassin (shooter memegang bow ber-PDC)
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        // Cek PDC di item bow yang dipakai shooter
        // (Arrow sudah ditembak, jadi kita cek tag di arrow PDC yang kita set saat giveAssassinBow)
        // Lebih aman: cek apakah shooter adalah assassin
        Role shooterRole = playerRoles.get(shooter.getUniqueId());
        if (shooterRole != Role.ASSASSIN) return;

        assassinShotFired = true;

        // Summon petir di target + kill jika player (PVP off)
        World targetWorld = target.getWorld();
        targetWorld.strikeLightningEffect(target.getLocation());

        if (target instanceof Player hitPlayer) {
            // Kill target — sementara set survival agar setHealth(0) bekerja
            hitPlayer.setGameMode(GameMode.SURVIVAL);
            hitPlayer.setHealth(0);
        }

        if (!(target instanceof Player targetPlayer)) {
            // Kena entity bukan player → salah
            triggerAssassinFail();
            return;
        }

        Role targetRole = playerRoles.get(targetPlayer.getUniqueId());

        if (targetRole == Role.MERLIN) {
            // BENAR: Assassin berhasil menemukan Merlin → kubu jahat menang
            broadcast(Component.text(" "));
            broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
            broadcast(Component.text("  ☠ ASSASSIN MENEMUKAN MERLIN!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
            broadcast(
                Component.text("  🏹 ", NamedTextColor.RED)
                    .append(Component.text(targetPlayer.getName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                    .append(Component.text(" adalah MERLIN!", NamedTextColor.RED))
            );
            broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
            broadcast(Component.text(" "));

            delayedTasks.add(
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!gameRunning) return;
                        triggerEvilWin("Assassin berhasil menemukan Merlin!");
                    }
                }.runTaskLater(plugin, 60L)
            );
        } else {
            // SALAH: Bukan Merlin → kubu jahat kalah, animasi meledak
            broadcast(Component.text(" "));
            broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
            broadcast(Component.text("  🏆 ASSASSIN SALAH MENEBAK!", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            broadcast(
                Component.text("  ", NamedTextColor.WHITE)
                    .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                    .append(Component.text(" bukan Merlin. Kubu baik menang!", NamedTextColor.GREEN))
            );
            broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
            broadcast(Component.text(" "));

            triggerAssassinFail();
        }
    }

    /**
     * Dipanggil saat arrow assassin meleset (jatuh ke tanah tanpa kena player).
     * Dianggap salah tebak.
     */
    public void handleAssassinArrowMiss() {
        if (!gameRunning) return;
        if (assassinShotFired) return;
        assassinShotFired = true;

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
        broadcast(Component.text("  🏆 ASSASSIN MELESET!", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Panah tidak mengenai siapapun. Kubu baik menang!", NamedTextColor.GREEN));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
        broadcast(Component.text(" "));

        triggerAssassinFail();
    }

    /**
     * Assassin salah tebak → animasi scale 1.2 ↔ 1.0 beberapa kali,
     * lalu partikel ledakan + suara + kill semua kubu jahat, lalu good win.
     */
    private void triggerAssassinFail() {
        if (!gameRunning) return;

        // Kumpulkan semua player kubu jahat
        List<Player> evilPlayers = new ArrayList<>();
        for (Player p : getOnlinePlayers()) {
            Role role = playerRoles.get(p.getUniqueId());
            if (role != null && role.isEvil()) {
                evilPlayers.add(p);
            }
        }

        // Animasi scale 1.2 ↔ 1.0 × 5 kali (10 toggle, interval 5 tick = 0.25 detik)
        new BukkitRunnable() {
            int toggle = 0;
            final int TOTAL_TOGGLES = 10;

            @Override
            public void run() {
                if (!gameRunning) { cancel(); return; }

                double scale = (toggle % 2 == 0) ? 1.2 : 1.0;
                for (Player p : evilPlayers) {
                    if (p.isOnline()) {
                        var attr = p.getAttribute(Attribute.SCALE);
                        if (attr != null) attr.setBaseValue(scale);
                    }
                }

                toggle++;

                if (toggle >= TOTAL_TOGGLES) {
                    cancel();

                    // Reset scale ke 1.0
                    for (Player p : evilPlayers) {
                        if (p.isOnline()) {
                            var attr = p.getAttribute(Attribute.SCALE);
                            if (attr != null) attr.setBaseValue(1.0);
                        }
                    }

                    // Partikel ledakan + suara + kill semua kubu jahat
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!gameRunning) return;
                            for (Player p : evilPlayers) {
                                if (!p.isOnline()) continue;
                                World w = p.getWorld();
                                Location loc = p.getLocation().clone().add(0, 1, 0);

                                // Partikel ledakan besar
                                w.spawnParticle(Particle.EXPLOSION, loc, 3, 0.3, 0.3, 0.3, 0);
                                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
                                w.spawnParticle(Particle.DUST,
                                    loc, 40,
                                    new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2f));

                                // Suara ledakan
                                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
                                w.playSound(loc, Sound.ENTITY_WITHER_DEATH, 0.7f, 1.2f);

                                // Kill player (PVP off, pakai setHealth 0 di mode survival sementara)
                                GameMode prev = p.getGameMode();
                                p.setGameMode(GameMode.SURVIVAL);
                                p.setHealth(0);
                            }

                            // Lanjut ke good win setelah 1.5 detik
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (!gameRunning) return;
                                    triggerGoodWin();
                                }
                            }.runTaskLater(plugin, 30L);
                        }
                    }.runTaskLater(plugin, 5L);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * Kubu baik menang (assassin salah tebak Merlin).
     */
    private void triggerGoodWin() {
        if (!gameRunning) return;

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
        broadcast(Component.text("  🏆 KUBU BAIK MENANG!", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Merlin berhasil menyembuhkan pak fred!", NamedTextColor.GREEN));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
        broadcast(Component.text(" "));

        // Reveal semua role
        for (Map.Entry<UUID, Role> entry : playerRoles.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                NamedTextColor color = entry.getValue().isGood() ? NamedTextColor.AQUA : NamedTextColor.RED;
                broadcast(
                    Component.text("  ✨ ", color)
                        .append(Component.text(p.getName(), NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                        .append(Component.text(" adalah " + entry.getValue().name(), color))
                );
            }
        }
        broadcast(Component.text(" "));

        delayedTasks.add(
            new BukkitRunnable() {
                @Override
                public void run() {
                    cleanup();
                }
            }.runTaskLater(plugin, 100L)
        );
    }


    /**
     * Dipanggil VotingManager saat kubu jahat menang karena 5x reject.
     */
    public void triggerEvilWin(String reason) {
        if (!gameRunning) return;

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text("  ☠ KUBU JAHAT MENANG!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        broadcast(Component.text("  Alasan: " + reason, NamedTextColor.RED));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        broadcast(Component.text(" "));

        // Reveal siapa saja kubu jahat
        for (Map.Entry<UUID, Role> entry : playerRoles.entrySet()) {
            if (entry.getValue().isEvil()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null) {
                    broadcast(
                        Component.text("  🗡 ", NamedTextColor.RED)
                            .append(Component.text(p.getName(), NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD))
                            .append(Component.text(" adalah " + entry.getValue().name(), NamedTextColor.RED))
                    );
                }
            }
        }
        broadcast(Component.text(" "));

        // Akhiri game setelah 5 detik
        delayedTasks.add(
            new BukkitRunnable() {
                @Override
                public void run() {
                    cleanup();
                }
            }.runTaskLater(plugin, 100L)
        );
    }

    // ===== UTILS =====

    private void broadcast(Component message) {
        for (Player p : getOnlinePlayers()) p.sendMessage(message);
    }

    public boolean isAssassinArrow(Arrow arrow) {

        if (!(arrow.getShooter() instanceof Player shooter))
            return false;

        Role role = playerRoles.get(shooter.getUniqueId());

        return role == Role.ASSASSIN;
    }

    private List<Player> getOnlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (String name : registeredPlayers) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    private World getGameWorld() {
        for (String name : registeredPlayers) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) return p.getWorld();
        }
        return Bukkit.getWorlds().get(0);
    }

    public void cleanup() {
        AvalonPlugin.getInstance()
                .getTeamSelectionListener()
                .clearAllFloatingHeads();
        // Hentikan semua task aktif
        if (cutsceneTask != null)               { cutsceneTask.cancel();               cutsceneTask = null; }
        if (countdownTask != null)              { countdownTask.cancel();              countdownTask = null; }
        if (revealCountdownTask != null)        { revealCountdownTask.cancel();        revealCountdownTask = null; }
        if (teamSelectionActionBarTask != null) { teamSelectionActionBarTask.cancel(); teamSelectionActionBarTask = null; }
        if (endMissionCountdownTask != null)    { endMissionCountdownTask.cancel();    endMissionCountdownTask = null; }

        stopDiscussionPhase();
        stopHotbarLock();
        stopSabotageMechanic();
        stopProximityChecker();
        
        stopAssassinationPhase();

        // Cancel voting jika sedang berjalan
        if (votingManager != null) {
            votingManager.cancelVoting();
        }

        // Set gameRunning false SEBELUM operasi lain supaya semua BukkitRunnable
        // yang cek gameRunning langsung berhenti di iterasi berikutnya
        gameRunning       = false;
        cutsceneRunning   = false;
        revealPhaseActive = false;
        missionActive     = false;
        missionSabotaged  = false;
        missionPlantLocation = null;
        completedPlants.clear();
        currentMissionTeam.clear();
        discussionActive = false;
        discussionAfterSuccess = false;
        discussionSkipVotes.clear();
        removeDiscussionSkipItems();
        assassinationActive = false;
        assassinShotFired = false;
        assassinationSkipVotes.clear();

        for (BukkitTask task : delayedTasks) {
            task.cancel();
        }
        delayedTasks.clear();

        lockedYaw.clear();
        lockedPitch.clear();
        movementLocked.clear();

        // Reset King state
        kingOrder.clear();
        currentKingIndex = -1;
        currentMission   = 1;
        currentRound     = 1;
        evilMissionFails = 0;
        teamSelectionSessions.clear();

        // Clear effect reveal + turunkan semua player dari seat
        for (Player p : getOnlinePlayers()) {
            p.getInventory().clear();
            clearRevealEffects(p);

            unlockCamera(p);
            unlockMovement(p);

            if (p.getAttribute(Attribute.SCALE) != null) {
                p.getAttribute(Attribute.SCALE).setBaseValue(1.0);
            }
            if (p.getVehicle() != null) {
                Entity v = p.getVehicle();
                // Bypass listener untuk eject
                revealPhaseActive = true;
                v.eject();
                revealPhaseActive = false;
                if (v.getScoreboardTags().contains("avalon_seat")) v.remove();
            }
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.setGameMode(GameMode.ADVENTURE);
        }

        // Hapus entity dan blok arena
        World gameWorld = getGameWorld();
        if (gameWorld != null) {
            gameWorld.setPVP(true);
            gameWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            for (Entity e : gameWorld.getEntities()) {
                if (e.getScoreboardTags().contains("avalon_seat"))            e.remove();
                if (e.getScoreboardTags().contains("avalon_mannequin"))       e.remove();
                if (e.getScoreboardTags().contains("avalon_vote_head"))       e.remove();
                if (e.getScoreboardTags().contains("avalon_discussion_head")) e.remove();
                if (e.getScoreboardTags().contains("avalon_assassination_head")) e.remove();
            }
            for (int[] pos : PLAYER_SLAB_POSITIONS)
                gameWorld.getBlockAt(BASE_X + pos[0], BASE_Y, BASE_Z + pos[1]).setType(Material.AIR);
            gameWorld.getBlockAt(BASE_X, BASE_Y, BASE_Z).setType(Material.AIR);
            gameWorld.getBlockAt(BASE_X, BASE_Y - 1, BASE_Z).setType(Material.ORANGE_TERRACOTTA);
            clearAllMissionPlants(gameWorld);
        }
    }
}