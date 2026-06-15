package id.avalon.listeners;

import id.avalon.AvalonPlugin;
import id.avalon.gui.TeamSelectionGUI;
import id.avalon.managers.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Menangani interaksi klik di dalam GUI pemilihan tim.
 *
 * Logika:
 *  - Klik di TARGET_SLOTS (berisi player head): kembalikan ke pool rata kiri
 *  - Klik di POOL_SLOTS (berisi player head): pindahkan ke target slot ? pertama
 *  - Klik di CONFIRM_SLOT: konfirmasi jika semua slot terisi
 *  - Lainnya (divider, question mark, null): diabaikan
 */
public class TeamSelectionListener implements Listener {

    private final GameManager gameManager;
    private final Plugin plugin;
    private final Map<UUID, List<ArmorStand>> floatingHeads = new HashMap<>();
    private final Map<ArmorStand, Location> headBaseLocations = new HashMap<>();
    private final Map<ArmorStand, Double> headPhases = new HashMap<>();

    public TeamSelectionListener(GameManager gameManager) {
        this.gameManager = gameManager;
        this.plugin = AvalonPlugin.getInstance();

        new BukkitRunnable() {

            double tick = 0;

            @Override
            public void run() {

                tick += 0.25;

                int index = 0;

                for (Map.Entry<ArmorStand, Location> entry : headBaseLocations.entrySet()) {

                    ArmorStand stand = entry.getKey();

                    if (stand == null || stand.isDead())
                        continue;

                    Location base = entry.getValue();

                    double phase =
                        headPhases.getOrDefault(
                                stand,
                                0.0
                        );

                    double offsetY =
                            Math.sin(tick + phase) * 0.03;

                    Location loc = base.clone();
                    loc.add(0, offsetY, 0);

                    loc.setYaw(
                            stand.getLocation().getYaw() + 1.5f
                    );

                    stand.teleport(loc);

                    index++;
                }
            }

        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ── Cek judul GUI dengan plain-text comparison ─────────────────────────
        String titlePlain = PlainTextComponentSerializer.plainText()
            .serialize(event.getView().title());
        if (!titlePlain.equals(TeamSelectionGUI.GUI_TITLE)) return;

        // Selalu cancel semua aksi di GUI ini
        event.setCancelled(true);

        Inventory inv = event.getInventory();
        int slot = event.getRawSlot();

        // Abaikan klik di luar 54 slot GUI (mis. inventory pemain di bawah)
        if (slot < 0 || slot >= 54) return;

        ItemStack clicked = inv.getItem(slot);

        // Abaikan slot kosong dan divider
        if (clicked == null || clicked.getType().isAir()) return;
        if (TeamSelectionGUI.isDivider(clicked)) return;

        // Abaikan question mark (klik di slot kosong target)
        if (TeamSelectionGUI.isQuestionMark(clicked, plugin)) return;

        // Hitung teamSize dari target slot yang aktif (berisi item apapun)
        int teamSize = countActiveTargetSlots(inv);

        // ── Klik CONFIRM ────────────────────────────────────────────────────────
        if (slot == TeamSelectionGUI.CONFIRM_SLOT) {
            if (!TeamSelectionGUI.isConfirmButton(clicked, plugin)) return;

            int emptySlot = findFirstQuestionMarkSlot(inv, teamSize);
            if (emptySlot != -1) {
                player.sendMessage(Component.text("Isi semua slot terlebih dahulu!", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            List<String> selectedTeam = collectSelectedPlayers(inv, teamSize);
            if (selectedTeam.size() < teamSize) {
                player.sendMessage(Component.text("Error: slot tidak valid!", NamedTextColor.RED));
                return;
            }

            player.closeInventory();
            gameManager.confirmTeamSelection(player, selectedTeam);
            return;
        }

        // ── Klik TARGET SLOT (baris 1) yang berisi player head ─────────────────
        if (isInTargetRange(slot, teamSize)) {
            String playerName = TeamSelectionGUI.getPlayerNameFromItem(clicked, plugin);
            if (playerName == null) return;

            // Reset slot ini ke question mark
            TeamSelectionGUI gui = new TeamSelectionGUI(plugin);
            inv.setItem(slot, gui.makeQuestionMark());

            // Kembalikan player ke pool (rata kiri)
            addBackToPool(inv, playerName, gui);

            // Update confirm button
            updateConfirmButton(inv, teamSize, gui);
            syncSession(player, inv, teamSize);
            updateFloatingHeads(player, inv, teamSize);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            return;
        }

        // ── Klik POOL SLOT (baris 4-5) yang berisi player head ─────────────────
        if (isPoolSlot(slot)) {
            String playerName = TeamSelectionGUI.getPlayerNameFromItem(clicked, plugin);
            if (playerName == null) return;

            // Cari question mark pertama di target slots
            int targetSlot = findFirstQuestionMarkSlot(inv, teamSize);
            if (targetSlot == -1) {
                player.sendMessage(Component.text("Semua slot sudah terisi!", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            TeamSelectionGUI gui = new TeamSelectionGUI(plugin);

            // Pindahkan ke target slot
            inv.setItem(targetSlot, gui.makePlayerHead(playerName));

            // Hapus dari pool dan geser rata kiri
            removeFromPoolAndShift(inv, slot, gui);

            // Update confirm button
            updateConfirmButton(inv, teamSize, gui);
            syncSession(player, inv, teamSize);
            updateFloatingHeads(player, inv, teamSize);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Hitung berapa target slot aktif (slot yang berisi item — baik player head
     * maupun question mark). Slot aktif selalu berurutan dari kiri (index 0..n-1).
     */
    private int countActiveTargetSlots(Inventory inv) {
        int count = 0;
        for (int s : TeamSelectionGUI.TARGET_SLOTS) {
            ItemStack item = inv.getItem(s);
            if (item != null && !item.getType().isAir()) count++;
            else break;
        }
        return Math.max(count, 1);
    }

    /** Cek apakah slot ada di dalam target range yang aktif. */
    private boolean isInTargetRange(int slot, int teamSize) {
        for (int i = 0; i < teamSize; i++) {
            if (TeamSelectionGUI.TARGET_SLOTS[i] == slot) return true;
        }
        return false;
    }

    /** Cek apakah slot adalah pool slot. */
    private boolean isPoolSlot(int slot) {
        for (int s : TeamSelectionGUI.POOL_SLOTS) {
            if (s == slot) return true;
        }
        return false;
    }

    /**
     * Cari slot target pertama yang berisi question mark.
     * Return -1 jika semua terisi player head.
     */
    private int findFirstQuestionMarkSlot(Inventory inv, int teamSize) {
        for (int i = 0; i < teamSize; i++) {
            int s = TeamSelectionGUI.TARGET_SLOTS[i];
            ItemStack item = inv.getItem(s);
            if (item == null || item.getType().isAir()
                    || TeamSelectionGUI.isQuestionMark(item, plugin)) {
                return s;
            }
        }
        return -1;
    }

    /**
     * Kembalikan playerName ke pool — tambahkan di akhir, render rata kiri.
     */
    private void addBackToPool(Inventory inv, String playerName, TeamSelectionGUI gui) {
        List<String> current = readPool(inv);
        current.add(playerName);
        renderPool(inv, current, gui);
    }

    /**
     * Hapus item di removedSlot dari pool, geser sisa ke kiri.
     */
    private void removeFromPoolAndShift(Inventory inv, int removedSlot, TeamSelectionGUI gui) {
        List<String> remaining = new ArrayList<>();
        for (int s : TeamSelectionGUI.POOL_SLOTS) {
            if (s == removedSlot) continue;
            String name = TeamSelectionGUI.getPlayerNameFromItem(inv.getItem(s), plugin);
            if (name != null) remaining.add(name);
        }
        renderPool(inv, remaining, gui);
    }

    /** Baca semua nama player di pool (dari kiri ke kanan, skip null). */
    private List<String> readPool(Inventory inv) {
        List<String> names = new ArrayList<>();
        for (int s : TeamSelectionGUI.POOL_SLOTS) {
            String name = TeamSelectionGUI.getPlayerNameFromItem(inv.getItem(s), plugin);
            if (name != null) names.add(name);
        }
        return names;
    }

    /** Hapus semua pool slot lalu render ulang daftar nama rata kiri. */
    private void renderPool(Inventory inv, List<String> names, TeamSelectionGUI gui) {
        for (int s : TeamSelectionGUI.POOL_SLOTS) inv.setItem(s, null);
        for (int i = 0; i < names.size() && i < TeamSelectionGUI.POOL_SLOTS.length; i++) {
            inv.setItem(TeamSelectionGUI.POOL_SLOTS[i], gui.makePlayerHead(names.get(i)));
        }
    }

    /** Update tombol konfirmasi berdasarkan apakah semua slot terisi. */
    private void updateConfirmButton(Inventory inv, int teamSize, TeamSelectionGUI gui) {
        boolean allFilled = (findFirstQuestionMarkSlot(inv, teamSize) == -1);
        inv.setItem(TeamSelectionGUI.CONFIRM_SLOT, gui.makeConfirmButton(allFilled));
    }

    /** Kumpulkan nama player dari target slots yang terisi. */
    private List<String> collectSelectedPlayers(Inventory inv, int teamSize) {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < teamSize; i++) {
            String name = TeamSelectionGUI.getPlayerNameFromItem(
                inv.getItem(TeamSelectionGUI.TARGET_SLOTS[i]), plugin);
            if (name != null) selected.add(name);
        }
        return selected;
    }

    /** Sinkronkan state target slots ke session GameManager. */
    private void syncSession(Player player, Inventory inv, int teamSize) {
        List<String> selected = collectSelectedPlayers(inv, teamSize);
        gameManager.setTeamSelectionSession(player, selected);
    }

    private void updateFloatingHeads(Player king, Inventory inv, int teamSize) {

        List<ArmorStand> old = floatingHeads.remove(king.getUniqueId());

        if (old != null) {
            old.forEach(ArmorStand::remove);
        }

        List<String> selected = collectSelectedPlayers(inv, teamSize);

        if (selected.isEmpty())
            return;

        World world = king.getWorld();

        Location center = king.getLocation().clone().add(0, 1.5, 0);

        Location base = new Location(
                world,
                7.5,
                74,
                -378.5
        );

        Vector forward = base.toVector()
                .subtract(king.getLocation().toVector())
                .setY(0)
                .normalize();

        Vector right = new Vector(
                -forward.getZ(),
                0,
                forward.getX()
        );

        double spacing = 0.75;

        List<ArmorStand> spawned = new ArrayList<>();

        double start = -(selected.size() - 1) / 2.0;

        for (int i = 0; i < selected.size(); i++) {

            String playerName = selected.get(i);

            Location pos = center.clone().add(
                    right.clone().multiply((start + i) * spacing)
            );

            Location standLoc = pos.clone();
            standLoc.setDirection(forward);

            ArmorStand stand = (ArmorStand) world.spawnEntity(
                    standLoc,
                    EntityType.ARMOR_STAND
            );

            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);

            ItemStack skull = new TeamSelectionGUI(plugin)
                    .makePlayerHead(playerName);

            stand.getEquipment().setHelmet(skull);

            spawned.add(stand);

            headBaseLocations.put(
                    stand,
                    stand.getLocation().clone()
            );
            double phase = (i % 2 == 0)
                    ? 0
                    : Math.PI;

            headPhases.put(stand, phase);
        }

        floatingHeads.put(
                king.getUniqueId(),
                spawned
        );
    }

    public void clearAllFloatingHeads() {

        for (List<ArmorStand> stands : floatingHeads.values()) {

            for (ArmorStand stand : stands) {

                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
        }

        floatingHeads.clear();
        headBaseLocations.clear();
    }
}