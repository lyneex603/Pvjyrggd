package me.server.pvpsystem;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ContractCommand implements CommandExecutor, TabCompleter {

    private final PvPSystem plugin;
    private final ContractMenu menu;

    public ContractCommand(PvPSystem plugin, ContractMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (args.length == 0) {
            menu.open(p, ContractMenu.View.ALL, 0);
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 3) {
                p.sendMessage("§cИспользуйте: /" + label + " create <ник> <сумма>");
                return true;
            }
            OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
            if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                p.sendMessage("§cИгрок не найден. Он должен хотя бы раз заходить на сервер.");
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException ex) {
                p.sendMessage("§cСумма должна быть числом.");
                return true;
            }
            if (!Double.isFinite(amount) || amount <= 0) {
                p.sendMessage("§cНеверная сумма.");
                return true;
            }
            String err = plugin.contracts().create(p, target, amount);
            if (err != null) {
                p.sendMessage("§c" + err);
            } else {
                p.sendMessage("§aЗаказ на §c" + target.getName() + " §aсоздан. Списано §e" + plugin.money(amount)
                        + "§a. Деньги вернутся, если вы отмените заказ или он истечёт.");
                Bukkit.broadcastMessage("§6[Заказы] §e" + p.getName() + " §7назначил награду §e"
                        + plugin.money(amount) + " §7за голову §c" + target.getName() + "§7. Откройте §f/contract");
            }
            return true;
        }
        menu.open(p, ContractMenu.View.ALL, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("create");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
