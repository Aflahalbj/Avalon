package id.avalon.gui;

import id.avalon.managers.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.List;
import java.util.UUID;

/**
 * GUI untuk Raja memilih anggota tim yang akan menjalankan misi.
 *
 * Layout (54 slot):
 *   Baris 1 (slot 0-8):   Target slots (aktif sesuai kuota misi)
 *   Baris 2 (slot 9-17):  kosong
 *   Baris 3 (slot 18-26): black stained glass pane (divider)
 *   Baris 4 (slot 27-35): daftar player tersedia (rata kiri)
 *   Baris 5 (slot 36-44): daftar player tersedia (lanjutan, rata kiri)
 *   Slot 53:              tombol konfirmasi (checkmark)
 */
public class TeamSelectionGUI {

    // ── Texture URLs ───────────────────────────────────────────────────────────
    private static final String TEXTURE_QUESTION_MARK =
        "http://textures.minecraft.net/texture/6958a4a7a53d343bf672215a49fdc9d7cc444f65166d162cd60872eb58710";
    private static final String TEXTURE_CHECKMARK =
        "http://textures.minecraft.net/texture/d9980c1d211809a9b6565088f56a38f2ef49115c1054fa66245122e9eeedecc2";

    // ── GUI Title (plain string untuk pencocokan di listener) ─────────────────
    public static final String GUI_TITLE = "Pilih Tim Misi";

    // ── Slot ranges ────────────────────────────────────────────────────────────
    public static final int[] TARGET_SLOTS  = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    public static final int[] DIVIDER_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};
    public static final int[] POOL_SLOTS    = {27, 28, 29, 30, 31, 32, 33, 34, 35,
                                                36, 37, 38, 39, 40, 41, 42, 43};
    public static final int   CONFIRM_SLOT  = 53;

    // ── PersistentDataContainer keys ──────────────────────────────────────────
    // Tag untuk menandai item internal agar tidak terdeteksi sebagai player head biasa
    private static final String PDC_NAMESPACE = "avalon";
    public  static final String PDC_KEY_TYPE  = "gui_slot_type";
    public  static final String PDC_TYPE_QUESTION = "question_mark";
    public  static final String PDC_TYPE_CONFIRM  = "confirm_button";
    public  static final String PDC_KEY_PLAYER_NAME = "player_name";

    // ── Tabel komposisi tim ────────────────────────────────────────────────────
    private static final int[][] MISSION_TEAM_SIZE = {
        // 5P:  M1 M2 M3 M4 M5
        {2, 3, 2, 3, 3},
        // 6P:
        {2, 3, 4, 3, 4},
        // 7P:
        {2, 3, 3, 4, 4},
        // 8P:
        {3, 4, 4, 5, 5},
        // 9P:
        {3, 4, 4, 5, 5},
        // 10P:
        {3, 4, 4, 5, 5},
    };

    private final Plugin plugin;

    public TeamSelectionGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    // Konstruktor compat untuk kode lama yang passing GameManager
    public TeamSelectionGUI(GameManager gameManager) {
        this.plugin = gameManager != null ? gameManager.getPlugin() : null;
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    public Inventory create(int teamSize, List<String> availablePlayers, List<String> selectedPlayers) {
        Inventory inv = Bukkit.createInventory(
            null,
            54,
            Component.text(GUI_TITLE, NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD)
        );

        // Divider baris 3
        ItemStack divider = makeDivider();
        for (int slot : DIVIDER_SLOTS) inv.setItem(slot, divider);

        // Target slots baris 1
        for (int i = 0; i < TARGET_SLOTS.length; i++) {
            if (i < teamSize) {
                if (i < selectedPlayers.size()) {
                    inv.setItem(TARGET_SLOTS[i], makePlayerHead(selectedPlayers.get(i)));
                } else {
                    inv.setItem(TARGET_SLOTS[i], makeQuestionMark());
                }
            }
        }

        // Pool player baris 4-5 (rata kiri)
        for (int i = 0; i < availablePlayers.size() && i < POOL_SLOTS.length; i++) {
            inv.setItem(POOL_SLOTS[i], makePlayerHead(availablePlayers.get(i)));
        }

        // Tombol konfirmasi
        boolean allFilled = (selectedPlayers.size() >= teamSize);
        inv.setItem(CONFIRM_SLOT, makeConfirmButton(allFilled));

        return inv;
    }

    // ── Item builders ──────────────────────────────────────────────────────────

    private ItemStack makeDivider() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    /** Question mark skull dengan texture kustom. PDC tag menandai sebagai question mark. */
    public ItemStack makeQuestionMark() {
        ItemStack skull = makeTextureHead(
            TEXTURE_QUESTION_MARK,
            Component.text("[ Slot Kosong ]", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
            List.of(Component.text("Pilih player dari bawah", NamedTextColor.GRAY))
        );

        if (plugin != null) {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_TYPE);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, PDC_TYPE_QUESTION);
            skull.setItemMeta(meta);
        }

        return skull;
    }

    /** Kepala player — nama disimpan di PDC dan di display name. */
    public ItemStack makePlayerHead(String playerName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        meta.displayName(
            Component.text(playerName, NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
        );
        meta.lore(List.of(
            Component.text("Klik untuk memilih / mengembalikan", NamedTextColor.GRAY)
        ));

        // Set skin via OfflinePlayer (cached profile, no I/O)
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        meta.setOwningPlayer(op);

        // Simpan nama player di PDC agar bisa dibaca kembali tanpa ambiguitas
        if (plugin != null) {
            NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_PLAYER_NAME);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, playerName);
        }

        skull.setItemMeta(meta);
        return skull;
    }

    /** Checkmark skull dengan texture kustom. */
    public ItemStack makeConfirmButton(boolean ready) {
        Component displayName = ready
            ? Component.text("✔ KONFIRMASI TIM", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
            : Component.text("✔ KONFIRMASI TIM", NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD);

        List<Component> lore = ready
            ? List.of(
                Component.text("Apakah kamu yakin? ini tidak bisa diubah!", NamedTextColor.YELLOW),
                Component.text("Klik untuk mengonfirmasi tim.", NamedTextColor.WHITE))
            : List.of(
                Component.text("Isi semua slot terlebih dahulu.", NamedTextColor.RED));

        ItemStack skull = makeTextureHead(TEXTURE_CHECKMARK, displayName, lore);

        if (plugin != null) {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_TYPE);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, PDC_TYPE_CONFIRM);
            skull.setItemMeta(meta);
        }

        return skull;
    }

    /** Helper: buat skull dengan PlayerProfile texture kustom. */
    private ItemStack makeTextureHead(String textureUrl, Component displayName, List<Component> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(textureUrl));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Avalon] Gagal set texture: " + e.getMessage());
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);

        meta.displayName(displayName);
        if (lore != null) meta.lore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    // ── Static utility ─────────────────────────────────────────────────────────

    /**
     * Ambil nama player dari item head.
     * Cek PDC tag dulu (paling reliable); fallback ke display name.
     */
    public static String getPlayerNameFromItem(ItemStack item, Plugin plugin) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return null;
        if (isQuestionMark(item, plugin)) return null;
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return null;

        // Jika ada PDC key player_name — pakai itu
        if (plugin != null) {
            NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_PLAYER_NAME);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(key, PersistentDataType.STRING)) {
                return pdc.get(key, PersistentDataType.STRING);
            }
        }

        // Fallback: baca dari display name (strip formatting)
        Component dn = meta.displayName();
        if (dn == null) return null;
        String plain = PlainTextComponentSerializer.plainText().serialize(dn).trim();
        if (plain.isEmpty()) return null;
        return plain;
    }

    /** Cek apakah item adalah question mark (via PDC). */
    public static boolean isQuestionMark(ItemStack item, Plugin plugin) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return false;
        if (plugin == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_TYPE);
        String val = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return PDC_TYPE_QUESTION.equals(val);
    }

    /** Cek apakah item adalah confirm button (via PDC). */
    public static boolean isConfirmButton(ItemStack item, Plugin plugin) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return false;
        if (plugin == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_TYPE);
        String val = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return PDC_TYPE_CONFIRM.equals(val);
    }

    /** Cek apakah item adalah divider. */
    public static boolean isDivider(ItemStack item) {
        return item != null && item.getType() == Material.BLACK_STAINED_GLASS_PANE;
    }

    // ── Tabel komposisi ────────────────────────────────────────────────────────

    public static int getTeamSize(int playerCount, int missionNumber) {
        if (playerCount < 5 || playerCount > 10) return 2;
        if (missionNumber < 1 || missionNumber > 5) return 2;
        return MISSION_TEAM_SIZE[playerCount - 5][missionNumber - 1];
    }

    public static boolean requiresTwoFails(int playerCount, int missionNumber) {
        return missionNumber == 4 && playerCount >= 7;
    }

    // ── Legacy compat (dipanggil dari listener lama tanpa plugin ref) ──────────
    /** @deprecated Gunakan {@link #getPlayerNameFromItem(ItemStack, Plugin)} */
    @Deprecated
    public static String getPlayerNameFromSkull(ItemStack item) {
        return getPlayerNameFromItem(item, null);
    }

    /** @deprecated Gunakan {@link #isQuestionMark(ItemStack, Plugin)} */
    @Deprecated
    public static boolean isQuestionMark(ItemStack item) {
        return false; // tidak bisa cek tanpa plugin ref
    }
}