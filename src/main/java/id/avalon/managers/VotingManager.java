package id.avalon.managers;

import id.avalon.AvalonPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.net.URL;
import java.util.*;

/**
 * Mengelola fase voting setelah Raja mengonfirmasi tim.
 *
 * Alur:
 *  1. startVoting() — bagikan item Setuju/Tolak, mulai countdown 10 menit
 *  2. Saat player klik kanan item → catat vote, tampilkan kepala melayang di atas diri sendiri
 *  3. Jika semua sudah vote → langsung selesai (hentikan waktu)
 *  4. Jika waktu habis → player yang belum vote dianggap abstain
 *  5. Evaluasi: mayoritas Setuju → fase misi; seri / lebih banyak Tolak → raja berikutnya
 *
 * Rule 1: 5x Tolak berturut-turut → Kubu Jahat menang. Warning setiap ditolak.
 */
public class VotingManager {

    // ── Texture URLs ──────────────────────────────────────────────────────────
    public static final String TEXTURE_TOLAK =
        "http://textures.minecraft.net/texture/7a254fc044efb84cd576a6c8f1144f83acdb14991232060ab486691a09b";
    public static final String TEXTURE_SETUJU =
        "http://textures.minecraft.net/texture/b5a3b49beec3ab23ae0b60dab56e9cc8fa16769a25830b5d8d6c46378f54430";

    // ── PDC Keys ──────────────────────────────────────────────────────────────
    public static final String PDC_KEY_VOTE_TYPE = "vote_type";
    public static final String VOTE_SETUJU        = "setuju";
    public static final String VOTE_TOLAK         = "tolak";

    private static final int VOTING_SECONDS      = 600; // 10 menit
    private static final int MAX_REJECT_STREAK   = 5;   // Rule 1

    private final AvalonPlugin plugin;
    private final GameManager  gameManager;

    // State voting
    private boolean votingActive = false;
    private List<String> currentTeam = new ArrayList<>();

    /** vote: playerName → "setuju" | "tolak" */
    private final Map<String, String> votes = new LinkedHashMap<>();

    /** ArmorStand kepala melayang per player */
    private final Map<UUID, ArmorStand> voteHeads = new HashMap<>();
    /** Base location tiap kepala untuk animasi */
    private final Map<ArmorStand, Location> headBaseLocations = new HashMap<>();

    private BukkitTask countdownTask;
    private BukkitTask animationTask;
    private int secondsLeft = VOTING_SECONDS;

    // ── Rule 1: Reject Streak ─────────────────────────────────────────────────
    /** Berapa kali berturut-turut tim ditolak dalam ronde saat ini. */
    private int rejectStreak = 0;

    public VotingManager(AvalonPlugin plugin, GameManager gameManager) {
        this.plugin      = plugin;
        this.gameManager = gameManager;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isVotingActive() { return votingActive; }

    /** Reset reject streak — dipanggil saat misi sukses / game dimulai. */
    public void resetRejectStreak() { rejectStreak = 0; }

    /**
     * Mulai fase voting.
     * @param team daftar nama player yang dipilih Raja untuk misi
     */
    public void startVoting(List<String> team) {
        if (votingActive) return;
        votingActive  = true;
        currentTeam   = new ArrayList<>(team);
        votes.clear();
        voteHeads.clear();
        headBaseLocations.clear();
        secondsLeft = VOTING_SECONDS;

        List<Player> allPlayers = getRegisteredOnlinePlayers();

        // Umumkan fase voting
        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_AQUA));
        broadcast(
            Component.text("  🗳 FASE VOTING DIMULAI!", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
        );
        broadcast(
            Component.text("  Tim yang dipilih: ", NamedTextColor.WHITE)
                .append(Component.text(String.join(", ", team), NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD))
        );
        broadcast(
            Component.text("  Klik kanan untuk memberikan suara.", NamedTextColor.GRAY)
        );
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_AQUA));
        broadcast(Component.text(" "));

        // Bagikan item ke semua player
        for (Player p : allPlayers) {
            giveVoteItems(p);
        }

        // Animasi kepala
        startAnimation();

        // Countdown 5 menit
        startCountdown(allPlayers);
    }

    /**
     * Dipanggil dari VotingListener saat player klik kanan item Setuju/Tolak.
     * Player bisa ganti suara kapan saja selama waktu belum habis.
     */
    public void castVote(Player player, String voteType) {
        if (!votingActive) return;
        String name = player.getName();
        if (!gameManager.getRegisteredPlayers().contains(name)) return;

        boolean isChangingVote = votes.containsKey(name);
        String oldVote = votes.get(name);

        // Jika pilihan sama persis, abaikan
        if (isChangingVote && oldVote.equals(voteType)) {
            player.sendMessage(Component.text("Kamu sudah memilih itu!", NamedTextColor.GRAY));
            return;
        }

        votes.put(name, voteType);

        // ITEM TIDAK DIHAPUS — biarkan player bisa ganti suara kapan saja

        // Update kepala melayang (hapus lama, spawn baru sesuai pilihan baru)
        spawnVoteHead(player, voteType);

        // Feedback
        if (voteType.equals(VOTE_SETUJU)) {
            player.sendMessage(Component.text(isChangingVote ? "↺ Suara diubah ke " : "✔ Kamu memilih ", NamedTextColor.GREEN)
                .append(Component.text("SETUJU", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        } else {
            player.sendMessage(Component.text(isChangingVote ? "↺ Suara diubah ke " : "✘ Kamu memilih ", NamedTextColor.RED)
                .append(Component.text("TOLAK", NamedTextColor.RED).decorate(TextDecoration.BOLD)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1.0f);
        }

        // Broadcast ke semua hanya saat pertama kali vote (bukan ganti suara)
        int totalVoted = votes.size();
        int totalPlayers = getRegisteredOnlinePlayers().size();
        if (!isChangingVote) {
            broadcast(
                Component.text("  » ", NamedTextColor.GRAY)
                    .append(Component.text(name, NamedTextColor.YELLOW))
                    .append(Component.text(" telah memberikan suara. (" + totalVoted + "/"
                        + totalPlayers + ")", NamedTextColor.GRAY))
            );
        }

        // Cek apakah semua sudah vote
        if (totalVoted >= totalPlayers) {
            finishVoting();
        }
    }

    /** Hentikan voting (cleanup) — dipanggil saat stopGame. */
    public void cancelVoting() {
        votingActive = false;
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (animationTask != null) { animationTask.cancel(); animationTask = null; }
        clearAllVoteHeads();
        removeVoteItemsFromAll();
        votes.clear();
        currentTeam.clear();
        rejectStreak = 0;
    }

    // ── Countdown ────────────────────────────────────────────────────────────

    private void startCountdown(List<Player> allPlayers) {
        if (countdownTask != null) countdownTask.cancel();

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!votingActive || !gameManager.isGameRunning()) {
                    cancel();
                    return;
                }

                if (secondsLeft < 0) {
                    cancel();
                    finishVoting();
                    return;
                }

                // Format waktu mm:ss
                int minutes = secondsLeft / 60;
                int seconds = secondsLeft % 60;
                String timeStr = String.format("%d:%02d", minutes, seconds);

                // Warna berdasarkan sisa waktu
                NamedTextColor timeColor = secondsLeft > 60
                    ? NamedTextColor.GREEN
                    : (secondsLeft > 30 ? NamedTextColor.YELLOW : NamedTextColor.RED);

                Component actionBar = Component.text("🗳 Voting | ", NamedTextColor.AQUA)
                    .append(Component.text(timeStr, timeColor).decorate(TextDecoration.BOLD))
                    .append(Component.text(" | Vote: " + votes.size() + "/"
                        + getRegisteredOnlinePlayers().size(), NamedTextColor.GRAY));

                for (Player p : getRegisteredOnlinePlayers()) {
                    if (p.isOnline()) p.sendActionBar(actionBar);
                }

                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ── Finish Voting ────────────────────────────────────────────────────────

    private void finishVoting() {
        if (!votingActive) return;
        votingActive = false;

        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (animationTask != null) { animationTask.cancel(); animationTask = null; }

        // Bersihkan kepala vote & item
        clearAllVoteHeads();
        removeVoteItemsFromAll();

        // Hapus floating head king dari TeamSelectionListener
        AvalonPlugin.getInstance().getTeamSelectionListener().clearAllFloatingHeads();

        // Clear actionbar
        for (Player p : getRegisteredOnlinePlayers()) {
            p.sendActionBar(Component.text(" "));
        }

        // Hitung suara
        int setuju = 0, tolak = 0;
        for (String v : votes.values()) {
            if (v.equals(VOTE_SETUJU)) setuju++;
            else tolak++;
        }

        int totalVoted = votes.size();

        broadcast(Component.text(" "));
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        broadcast(Component.text("  📊 HASIL VOTING", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        broadcast(
            Component.text("  ✔ Setuju: ", NamedTextColor.GREEN)
                .append(Component.text(setuju, NamedTextColor.WHITE))
        );
        broadcast(
            Component.text("  ✘ Tolak: ", NamedTextColor.RED)
                .append(Component.text(tolak, NamedTextColor.WHITE))
        );
        if (totalVoted == 0) {
            broadcast(Component.text("  ⚠ Tidak ada yang memberikan suara.", NamedTextColor.GRAY));
        } else {
            // Tampilkan siapa vote apa
            for (Map.Entry<String, String> entry : votes.entrySet()) {
                NamedTextColor c = entry.getValue().equals(VOTE_SETUJU)
                    ? NamedTextColor.GREEN : NamedTextColor.RED;
                String label = entry.getValue().equals(VOTE_SETUJU) ? "✔ Setuju" : "✘ Tolak";
                broadcast(
                    Component.text("  " + entry.getKey() + ": ", NamedTextColor.GRAY)
                        .append(Component.text(label, c))
                );
            }
        }
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        broadcast(Component.text(" "));

        // Evaluasi hasil
        if (totalVoted == 0 || setuju <= tolak) {
            // Tim ditolak
            rejectStreak++;

            // ── Rule 1: Warning setiap ditolak ────────────────────────────────
            int remaining = MAX_REJECT_STREAK - rejectStreak;
            broadcast(Component.text(" "));
            broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED));
            broadcast(
                Component.text("  ❌ Tim ditolak! Giliran raja berikutnya.", NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
            );

            if (rejectStreak >= MAX_REJECT_STREAK) {
                // ── Rule 1: 5x berturut-turut → Kubu Jahat menang ────────────
                broadcast(
                    Component.text("  ☠ 5 PENOLAKAN BERTURUT-TURUT!", NamedTextColor.DARK_RED)
                        .decorate(TextDecoration.BOLD)
                );
                broadcast(
                    Component.text("  KUBU JAHAT MENANG!", NamedTextColor.DARK_RED)
                        .decorate(TextDecoration.BOLD)
                );
                broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED));
                broadcast(Component.text(" "));

                // Trigger evil win
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!gameManager.isGameRunning()) return;
                        gameManager.triggerEvilWin("5x penolakan berturut-turut");
                    }
                }.runTaskLater(plugin, 60L);
            } else {
                // Warning sisa penolakan
                broadcast(
                    Component.text("  ⚠ Penolakan ke-" + rejectStreak + " dari " + MAX_REJECT_STREAK + ".",
                        NamedTextColor.YELLOW)
                );
                broadcast(
                    Component.text("  Jika ditolak " + remaining + "x lagi, Kubu Jahat menang!",
                        NamedTextColor.YELLOW)
                );
                broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED));
                broadcast(Component.text(" "));

                // Delay 3 detik lalu rotasi raja
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!gameManager.isGameRunning()) return;
                        gameManager.rotateKing();
                    }
                }.runTaskLater(plugin, 60L);
            }
        } else {
            // Tim disetujui — reset reject streak
            rejectStreak = 0;

            broadcast(
                Component.text("  ✅ Tim disetujui! Memulai fase misi...", NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
            );
            broadcast(Component.text(" "));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!gameManager.isGameRunning()) return;
                    gameManager.startMissionPhase(currentTeam);
                }
            }.runTaskLater(plugin, 60L);
        }
    }

    // ── Item Voting ──────────────────────────────────────────────────────────

    private void giveVoteItems(Player player) {
        // Hotbar 1 (slot 0) = Tolak, Hotbar 2 (slot 1) = Setuju
        player.getInventory().setItem(0, makeTolakHead());
        player.getInventory().setItem(1, makeSetujuHead());
    }

    private void removeVoteItems(Player player) {
        ItemStack s0 = player.getInventory().getItem(0);
        ItemStack s1 = player.getInventory().getItem(1);
        if (s0 != null && isVoteItem(s0)) player.getInventory().setItem(0, null);
        if (s1 != null && isVoteItem(s1)) player.getInventory().setItem(1, null);
    }

    private void removeVoteItemsFromAll() {
        for (Player p : getRegisteredOnlinePlayers()) {
            removeVoteItems(p);
        }
    }

    public boolean isVoteItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_VOTE_TYPE);
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public String getVoteType(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return null;
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return null;
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_VOTE_TYPE);
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private ItemStack makeTolakHead() {
        return makeTextureHead(
            TEXTURE_TOLAK,
            Component.text("✘ TOLAK", NamedTextColor.RED).decorate(TextDecoration.BOLD),
            List.of(
                Component.text("Klik kanan untuk menolak tim.", NamedTextColor.GRAY),
                Component.text("Tim tidak akan menjalankan misi.", NamedTextColor.DARK_RED),
                Component.text("Kamu bisa mengganti suara kapan saja.", NamedTextColor.GRAY)
            ),
            VOTE_TOLAK
        );
    }

    private ItemStack makeSetujuHead() {
        return makeTextureHead(
            TEXTURE_SETUJU,
            Component.text("✔ SETUJU", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
            List.of(
                Component.text("Klik kanan untuk menyetujui tim.", NamedTextColor.GRAY),
                Component.text("Tim akan menjalankan misi.", NamedTextColor.DARK_GREEN),
                Component.text("Kamu bisa mengganti suara kapan saja.", NamedTextColor.GRAY)
            ),
            VOTE_SETUJU
        );
    }

    private ItemStack makeTextureHead(String textureUrl, Component displayName,
                                       List<Component> lore, String voteType) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(textureUrl));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Avalon] Gagal set texture vote: " + e.getMessage());
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        meta.displayName(displayName);
        meta.lore(lore);

        // Tag PDC
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_VOTE_TYPE);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, voteType);

        skull.setItemMeta(meta);
        return skull;
    }

    // ── Kepala Melayang ──────────────────────────────────────────────────────

    /**
     * Spawn kepala melayang di atas player yang sudah vote.
     * Raja: Y+2.0, player lain: Y+1.5
     */
    private void spawnVoteHead(Player voter, String voteType) {
        // Hapus kepala lama jika ada (re-vote tidak mungkin, tapi buat aman)
        ArmorStand old = voteHeads.remove(voter.getUniqueId());
        if (old != null && !old.isDead()) {
            headBaseLocations.remove(old);
            old.remove();
        }

        boolean isKing = gameManager.isKing(voter);
        double heightOffset = isKing ? 2.15 : 1.5;

        Location base = voter.getLocation().clone().add(0, heightOffset, 0);

        ArmorStand stand = (ArmorStand) voter.getWorld().spawnEntity(base, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.addScoreboardTag("avalon_vote_head");

        ItemStack headItem = voteType.equals(VOTE_SETUJU) ? makeSetujuHead() : makeTolakHead();
        stand.getEquipment().setHelmet(headItem);

        voteHeads.put(voter.getUniqueId(), stand);
        headBaseLocations.put(stand, base.clone());
    }

    private void startAnimation() {
        if (animationTask != null) animationTask.cancel();

        animationTask = new BukkitRunnable() {
            double tick = 0;

            @Override
            public void run() {
                if (!votingActive && headBaseLocations.isEmpty()) {
                    cancel();
                    return;
                }

                tick += 0.25;

                // Update posisi semua kepala agar mengikuti player dan animasi
                List<UUID> uuids = new ArrayList<>(voteHeads.keySet());
                for (UUID uid : uuids) {
                    ArmorStand stand = voteHeads.get(uid);
                    if (stand == null || stand.isDead()) {
                        voteHeads.remove(uid);
                        continue;
                    }

                    Player owner = Bukkit.getPlayer(uid);
                    if (owner == null || !owner.isOnline()) continue;

                    boolean isKing = gameManager.isKing(owner);
                    double heightOffset = isKing ? 2.15 : 1.5;

                    // Kepala mengikuti posisi player (naik-turun + rotasi)
                    Location base = owner.getLocation().clone().add(0, heightOffset, 0);
                    double offsetY = Math.sin(tick + uid.hashCode() * 0.1) * 0.08;
                    Location loc = base.clone().add(0, offsetY, 0);
                    loc.setYaw(stand.getLocation().getYaw() + 3.0f);

                    stand.teleport(loc);
                    headBaseLocations.put(stand, base);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void clearAllVoteHeads() {
        for (ArmorStand stand : voteHeads.values()) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        voteHeads.clear();
        headBaseLocations.clear();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Player> getRegisteredOnlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (String name : gameManager.getRegisteredPlayers()) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    private void broadcast(Component message) {
        for (Player p : getRegisteredOnlinePlayers()) p.sendMessage(message);
    }
}