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

    private final Map<UUID, Float> lockedYaw   = new HashMap<>();
    private final Map<UUID, Float> lockedPitch = new HashMap<>();
    private final Set<UUID> movementLocked = new HashSet<>();
    private final Map<UUID, Role>  playerRoles = new HashMap<>();

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

    // ── Constructor ──────────────────────────────────────────────────────────

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
        // setRotation langsung set yaw/pitch client tanpa butuh PlayerMoveEvent,
        // dan bekerja saat player sedang jadi passenger ArmorStand.
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
        }
        Component[] lines = {
            Component.text("Sudah satu bulan lamanya pak fred tidak sadarkan diri", NamedTextColor.YELLOW),
            Component.text("Konon katanya ada satu ramuan yang dapat menyembuhkannya", NamedTextColor.YELLOW),
            Component.text("Ramuan yang dibuat dengan 3 tanaman langka", NamedTextColor.YELLOW),
            Component.text("Pitcher plant, Torch flower, Cactus flower", Style.style(NamedTextColor.GOLD, TextDecoration.ITALIC)),
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
                    for (Player p : getOnlinePlayers()) unlockCamera(p);
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
        broadcast(Component.text(" "));
        broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));

        world.getBlockAt(BASE_X, BASE_Y, BASE_Z).setType(Material.WATER_CAULDRON);
        world.getBlockAt(BASE_X, BASE_Y - 1, BASE_Z).setType(Material.CAMPFIRE);
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

    // ===== UTILS =====

    private void broadcast(Component message) {
        for (Player p : getOnlinePlayers()) p.sendMessage(message);
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
        // Hentikan semua task aktif
        if (cutsceneTask != null)       { cutsceneTask.cancel();       cutsceneTask = null; }
        if (countdownTask != null)      { countdownTask.cancel();      countdownTask = null; }
        if (revealCountdownTask != null){ revealCountdownTask.cancel(); revealCountdownTask = null; }

        // Set gameRunning false SEBELUM operasi lain supaya semua BukkitRunnable
        // yang cek gameRunning langsung berhenti di iterasi berikutnya
        gameRunning       = false;
        cutsceneRunning   = false;
        revealPhaseActive = false;
        for (BukkitTask task : delayedTasks) {
            task.cancel();
        }
        delayedTasks.clear();

        lockedYaw.clear();
        lockedPitch.clear();
        movementLocked.clear();

        // Clear effect reveal + turunkan semua player dari seat
        for (Player p : getOnlinePlayers()) {
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
            p.setGameMode(GameMode.ADVENTURE);
        }

        // Hapus entity dan blok arena
        World gameWorld = getGameWorld();
        if (gameWorld != null) {
            gameWorld.setPVP(true);
            gameWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            for (Entity e : gameWorld.getEntities()) {
                if (e.getScoreboardTags().contains("avalon_seat"))      e.remove();
                if (e.getScoreboardTags().contains("avalon_mannequin")) e.remove();
            }
            for (int[] pos : PLAYER_SLAB_POSITIONS)
                gameWorld.getBlockAt(BASE_X + pos[0], BASE_Y, BASE_Z + pos[1]).setType(Material.AIR);
            gameWorld.getBlockAt(BASE_X, BASE_Y, BASE_Z).setType(Material.AIR);
            gameWorld.getBlockAt(BASE_X, BASE_Y - 1, BASE_Z).setType(Material.ORANGE_TERRACOTTA);
        }
    }
}