package id.avalon.listeners;

import id.avalon.gui.CustomRoleGUI;
import id.avalon.gui.RoleEditorSession;
import id.avalon.managers.GameManager;
import id.avalon.models.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CustomRoleListener implements Listener {

    private final GameManager gameManager;
    private final CustomRoleGUI gui;

    // Session per player
    private static final Map<UUID, RoleEditorSession> sessions = new HashMap<>();

    public CustomRoleListener(GameManager gameManager) {
        this.gameManager = gameManager;
        this.gui = new CustomRoleGUI(gameManager);
    }

    public static Map<UUID, RoleEditorSession> getSessions() {
        return sessions;
    }

    // ── Slot set untuk deteksi cepat ─────────────────────────────────────────

    private static final Set<Integer> GOOD_ACTIVE_SET = toSet(CustomRoleGUI.GOOD_ACTIVE);
    private static final Set<Integer> EVIL_ACTIVE_SET = toSet(CustomRoleGUI.EVIL_ACTIVE);
    private static final Set<Integer> GOOD_POOL_SET   = toSet(CustomRoleGUI.GOOD_POOL);
    private static final Set<Integer> EVIL_POOL_SET   = toSet(CustomRoleGUI.EVIL_POOL);

    private static Set<Integer> toSet(int[] arr) {
        Set<Integer> s = new HashSet<>();
        for (int v : arr) s.add(v);
        return s;
    }

    // ── Event handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        String title = event.getView().getTitle();
        if (!title.contains("Custom Role")) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (event.getCurrentItem() == null) return;

        RoleEditorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        // ── SAVE ────────────────────────────────────────────────────────────
        if (slot == CustomRoleGUI.SAVE_SLOT) {
            handleSave(player, session);
            return;
        }

        // ── GOOD ACTIVE ──────────────────────────────────────────────────────
        if (GOOD_ACTIVE_SET.contains(slot)) {
            handleGoodActiveClick(player, session, slot);
            return;
        }

        // ── EVIL ACTIVE ──────────────────────────────────────────────────────
        if (EVIL_ACTIVE_SET.contains(slot)) {
            handleEvilActiveClick(player, session, slot);
            return;
        }

        // ── GOOD POOL ────────────────────────────────────────────────────────
        if (GOOD_POOL_SET.contains(slot)) {
            handlePoolClick(player, session, slot, true);
            return;
        }

        // ── EVIL POOL ────────────────────────────────────────────────────────
        if (EVIL_POOL_SET.contains(slot)) {
            handlePoolClick(player, session, slot, false);
        }
    }

    // ── Handler: klik good active ─────────────────────────────────────────────

    private void handleGoodActiveClick(Player player, RoleEditorSession session, int slot) {

        int index = slotIndex(CustomRoleGUI.GOOD_ACTIVE, slot);
        if (index < 0 || index >= session.getNeededGoodCount()) return;

        Role role = session.getGoodSlot(index);

        // Slot kosong (question mark) → tandai sebagai pending
        if (role == null) {
            session.selectGoodSlot(index);
            player.playSound(
                    player.getLocation(),
                    Sound.UI_BUTTON_CLICK,
                    1.0f,
                    1.0f
            );
            player.openInventory(gui.create(session));
            return;
        }

        // Merlin tidak bisa dihapus
        if (role == Role.MERLIN) {
            player.sendMessage(Component.text("Merlin tidak bisa dihapus.", NamedTextColor.RED));
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1.0f,
                    1.0f
            );
            return;
        }

        // Hapus role, jadikan null (posisi slot tidak bergeser)
        session.removeGoodSlot(index);
        session.clearPendingSlot();
        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                1.0f,
                1.0f
        );
        player.openInventory(gui.create(session));
    }

    // ── Handler: klik evil active ─────────────────────────────────────────────

    private void handleEvilActiveClick(Player player, RoleEditorSession session, int slot) {

        int index = slotIndex(CustomRoleGUI.EVIL_ACTIVE, slot);
        if (index < 0 || index >= session.getNeededEvilCount()) return;

        Role role = session.getEvilSlot(index);

        // Slot kosong → tandai sebagai pending
        if (role == null) {
            session.selectEvilSlot(index);
            player.playSound(
                    player.getLocation(),
                    Sound.UI_BUTTON_CLICK,
                    1.0f,
                    1.0f
            );
            player.openInventory(gui.create(session));
            return;
        }

        // Hapus role
        session.removeEvilSlot(index);
        session.clearPendingSlot();
        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                1.0f,
                1.0f
        );
        player.openInventory(gui.create(session));
    }

    // ── Handler: klik pool ────────────────────────────────────────────────────

    private void handlePoolClick(Player player, RoleEditorSession session, int slot, boolean isGoodPool) {

        // Harus ada pending slot dulu
        if (!session.hasPendingSlot()) return;

        // Pastikan pool yang diklik sesuai kubu pending
        if (isGoodPool != session.isPendingGood()) return;

        // Identifikasi role dari posisi di pool — pakai session sebagai single source of truth
        Role role = resolvePoolRole(session, slot, isGoodPool);
        if (role == null) return;

        // Cek: role unik yang sudah aktif tidak bisa dipilih lagi
        if (RoleEditorSession.isUnique(role) && session.isUniqueRoleActive(role)) return;

        // Isi pending slot
        session.fillPendingSlot(role);
        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                1.0f,
                1.0f
        );
        player.openInventory(gui.create(session));
    }

    // ── Handler: save ────────────────────────────────────────────────────────

    private void handleSave(Player player, RoleEditorSession session) {

        if (!session.isComplete()) {
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1.0f,
                    1.0f
            );
            player.sendMessage(Component.text("Masih ada role kosong.", NamedTextColor.RED));
            return;
        }

        gameManager.setCustomRoles(session.getPlayerCount(), session.toRoleList());
        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.0f
        );
        player.sendMessage(Component.text("Custom role berhasil disimpan.", NamedTextColor.GREEN));
        sessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    // ── Util: cari role dari slot pool ────────────────────────────────────────

    /**
     * Tentukan Role berdasarkan slot yang diklik di pool.
     * Menggunakan session.buildGoodPool() / buildEvilPool() sebagai single source of truth.
     */
    private Role resolvePoolRole(RoleEditorSession session, int slot, boolean isGood) {

        if (isGood) {
            List<Role> pool = session.buildGoodPool();
            int poolSlotIndex = slotIndex(CustomRoleGUI.GOOD_POOL, slot);
            if (poolSlotIndex < 0 || poolSlotIndex >= pool.size()) return null;
            return pool.get(poolSlotIndex);

        } else {
            List<Role> pool = session.buildEvilPool();
            // Evil pool rata kanan: evilStart = EVIL_POOL.length - pool.size()
            int evilStart = CustomRoleGUI.EVIL_POOL.length - pool.size();
            int poolSlotIndex = slotIndex(CustomRoleGUI.EVIL_POOL, slot) - evilStart;
            if (poolSlotIndex < 0 || poolSlotIndex >= pool.size()) return null;
            return pool.get(poolSlotIndex);
        }
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    /** Cari index dari nilai dalam array; -1 kalau tidak ketemu. */
    private int slotIndex(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) return i;
        }
        return -1;
    }
}
