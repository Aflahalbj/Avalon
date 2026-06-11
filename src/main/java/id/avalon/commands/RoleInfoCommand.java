package id.avalon.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class RoleInfoCommand implements CommandExecutor {

    private static final Map<String, String> ROLE_INFO = Map.ofEntries(

        Map.entry("merlin",
            """
            §6═══════════════════════

              §b§lMERLIN
            
              §rMerlin adalah kunci dari penyembuhan Pak Fred.

              §rHanya Merlin yang dapat membaca mantra penyembuhan.

              §aKemampuan:
               §r• Melihat semua kubu jahat
               §r• Tidak dapat melihat Mordred

              §cPenting:
               §e• Jaga identitas Merlin.
               §e• Jangan sampai kubu jahat mengetahui siapa Merlin.
            """
        ),

        Map.entry("percival",
            """
            §6═══════════════════════
            
              §b§lPERCIVAL
            
              §rMengetahui siapa Merlin dan Morgana, tetapi tidak tahu mana Merlin yang asli.

              §aTugas:
               §r• Melindungi Merlin
               §r• Membingungkan kubu jahat
               §r• Bisa berpura-pura menjadi Merlin
            """
        ),

        Map.entry("loyal",
            """
            §6═══════════════════════
            
              §b§lLOYAL SERVANT OF ARTHUR

              §rTidak memiliki kemampuan khusus.

              §aTugas:
              §r• Menggunakan logika
              §r• Mengidentifikasi pemain jahat
              §r• Membantu menyelesaikan misi
            """
        ),

        Map.entry("assassin",
            """
            §6═══════════════════════
              §c§lASSASSIN

              §rMengetahui semua pemain jahat (kecuali Oberon).

              §aKemampuan:
              §r• Sabotase misi
              §r• Membunuh Merlin di akhir permainan
              
              §eJika kubu jahat gagal menyabotase misi, Assassin dapat menebak siapa Merlin.
              §eJika benar, kubu jahat tetap menang.
            """
        ),

        Map.entry("morgana",
            """
            §6═══════════════════════
              §c§lMORGANA

              §rMengetahui semua pemain jahat (kecuali Oberon).

              §aKemampuan:
              §r• Sabotase misi
              §r• Menipu Percival

              §eMorgana akan terlihat sebagai Merlin di mata Percival.
            """
        ),

        Map.entry("mordred",
            """
            §6═══════════════════════
              §4§lMORDRED

              §rMengetahui semua kubu jahat (kecuali Oberon).

              §aKemampuan:
              §r• Sabotase misi
              §r• Tidak terlihat oleh Merlin

              §eMerlin tidak mengetahui identitas Mordred.
            """
        ),

        Map.entry("oberon",
            """
            §6═══════════════════════
              §c§lOBERON

              §rOberon tidak mengetahui siapa teman-teman jahatnya.

              §rKubu jahat lainnya juga tidak tahu bahwa Oberon adalah rekan mereka.

              §aKemampuan:
              §r• Sabotase misi
              §r• Mengetahui siapa Merlin

              §ePada akhir permainan, Oberon harus meyakinkan kubu jahat lainnya bahwa dia Oberon.
            """
        ),

        Map.entry("minion",
            """
            §6═══════════════════════
            
              §c§lMINIONS OF MORDRED

              §rMengetahui semua pemain jahat (kecuali Oberon).

              §aKemampuan:
              §r• Sabotase misi

              §eTidak memiliki kekuatan khusus lainnya.
            
            """
        )
    );

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0) {
            sender.sendMessage("""
                §eGunakan:
                §f/roleinfo <role>

                §aRole tersedia:
                Merlin
                Percival
                Loyal
                Assassin
                Morgana
                Mordred
                Oberon
                Minion
                """);
            return true;
        }

        String role = args[0].toLowerCase();

        String info = ROLE_INFO.get(role);

        if (info == null) {
            sender.sendMessage("§cRole tidak ditemukan.");
            return true;
        }

        sender.sendMessage(info);
        return true;
    }
}