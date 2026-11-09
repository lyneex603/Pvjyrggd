package me.server.pvpsystem;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.util.*;

public class StatsCommands implements CommandExecutor {

    private static final String[] CIRCLED = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"};

    private final PvPSystem plugin;

    public StatsCommands(PvPSystem plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "pvpreload" -> {
                plugin.reloadConfig();
                plugin.combat().applyAll();
                sender.sendMessage("§aКонфиг PvPSystem перезагружен.");
            }
            case "topkills" -> top(sender, "Лидеры по убийствам", plugin.getConfig().getString("scoreboard.kills-objective", "kills"), false);
            case "topdeaths" -> top(sender, "Лидеры по смертям", plugin.getConfig().getString("scoreboard.deaths-objective", "deaths"), false);
            case "topkd" -> topKd(sender);
            case "stats" -> stats(sender, args);
        }
        return true;
    }

    private Map<String, Integer> read(String objective) {
        Objective o = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objective);
        if (o == null) return null;
        Map<String, Integer> m = new HashMap<>();
        for (String entry : o.getScoreboard().getEntries()) {
            Score s = o.getScore(entry);
            if (s.isScoreSet()) m.put(entry, s.getScore());
        }
        return m;
    }

    private void top(CommandSender sender, String title, String objective, boolean unused) {
        Map<String, Integer> m = read(objective);
        if (m == null) {
            sender.sendMessage("§cObjective '" + objective + "' не найден. Проверьте config.yml.");
            return;
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(m.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : sorted) rows.add(new String[]{e.getKey(), String.valueOf(e.getValue())});
        print(sender, title, rows);
    }

    private void topKd(CommandSender sender) {
        Map<String, Integer> k = read(plugin.getConfig().getString("scoreboard.kills-objective", "kills"));
        Map<String, Integer> d = read(plugin.getConfig().getString("scoreboard.deaths-objective", "deaths"));
        if (k == null || d == null) {
            sender.sendMessage("§cObjective убийств или смертей не найден. Проверьте config.yml.");
            return;
        }
        Map<String, Double> kd = new HashMap<>();
        for (Map.Entry<String, Integer> e : k.entrySet()) {
            int deaths = d.getOrDefault(e.getKey(), 0);
            kd.put(e.getKey(), e.getValue() / (double) Math.max(1, deaths));
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(kd.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, Double> e : sorted) rows.add(new String[]{e.getKey(), String.format(Locale.US, "%.2f", e.getValue())});
        print(sender, "Лидеры по K/D", rows);
    }

    private void print(CommandSender sender, String title, List<String[]> rows) {
        int size = Math.min(10, plugin.getConfig().getInt("top.size", 10));
        sender.sendMessage("§6§l" + title);
        if (rows.isEmpty()) {
            sender.sendMessage("§7Пока нет данных.");
            return;
        }
        for (int i = 0; i < size && i < rows.size(); i++) {
            String color = switch (i) {
                case 0 -> "§c";
                case 1 -> "§e";
                case 2 -> "§a";
                default -> "§7";
            };
            sender.sendMessage(color + CIRCLED[i] + " §f" + rows.get(i)[0] + " §8- " + color + rows.get(i)[1]);
        }
    }

    private void stats(CommandSender sender, String[] args) {
        String name;
        if (args.length > 0) name = args[0];
        else if (sender instanceof Player p) name = p.getName();
        else { sender.sendMessage("Укажите ник."); return; }

        Map<String, Integer> k = read(plugin.getConfig().getString("scoreboard.kills-objective", "kills"));
        Map<String, Integer> d = read(plugin.getConfig().getString("scoreboard.deaths-objective", "deaths"));
        if (k == null || d == null) {
            sender.sendMessage("§cObjective убийств или смертей не найден. Проверьте config.yml.");
            return;
        }
        int kills = k.getOrDefault(name, 0);
        int deaths = d.getOrDefault(name, 0);
        double kd = kills / (double) Math.max(1, deaths);
        sender.sendMessage("§6§lСтатистика: §f" + name);
        sender.sendMessage("§7Убийства: §a" + kills);
        sender.sendMessage("§7Смерти: §c" + deaths);
        sender.sendMessage("§7K/D: §e" + String.format(Locale.US, "%.2f", kd));
    }
}
