package me.server.pvpsystem;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class ContractManager {

    private final PvPSystem plugin;
    private final File file;
    private final List<Contract> contracts = new ArrayList<>();

    public ContractManager(PvPSystem plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "contracts.yml");
    }

    public List<Contract> all() { return contracts; }

    public long lifetimeMillis() {
        return plugin.getConfig().getLong("contracts.lifetime-hours", 24) * 3_600_000L;
    }

    // ---------- хранение ----------

    public void load() {
        contracts.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("contracts");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Contract c = new Contract();
                c.id = key;
                c.owner = UUID.fromString(s.getString("owner"));
                c.ownerName = s.getString("owner-name", "?");
                c.target = UUID.fromString(s.getString("target"));
                c.targetName = s.getString("target-name", "?");
                c.amount = s.getDouble("amount");
                c.createdAt = s.getLong("created");
                String t = s.getString("taker");
                if (t != null) {
                    c.taker = UUID.fromString(t);
                    c.takerName = s.getString("taker-name", "?");
                }
                contracts.add(c);
            } catch (Exception ex) {
                plugin.getLogger().warning("Не удалось загрузить заказ " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Contract c : contracts) {
            String p = "contracts." + c.id + ".";
            y.set(p + "owner", c.owner.toString());
            y.set(p + "owner-name", c.ownerName);
            y.set(p + "target", c.target.toString());
            y.set(p + "target-name", c.targetName);
            y.set(p + "amount", c.amount);
            y.set(p + "created", c.createdAt);
            if (c.taker != null) {
                y.set(p + "taker", c.taker.toString());
                y.set(p + "taker-name", c.takerName);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить contracts.yml: " + e.getMessage());
        }
    }

    // ---------- логика ----------

    /** Возвращает текст ошибки или null, если успешно. */
    public String create(Player owner, OfflinePlayer target, double amount) {
        double min = plugin.getConfig().getDouble("contracts.min-amount", 50000);
        int max = plugin.getConfig().getInt("contracts.max-per-player", 2);

        if (amount < min) return "Минимальная сумма заказа: " + plugin.money(min);
        if (target.getUniqueId().equals(owner.getUniqueId())) return "Нельзя сделать заказ на самого себя.";
        long count = contracts.stream().filter(c -> c.owner.equals(owner.getUniqueId())).count();
        if (count >= max) return "У вас уже максимум заказов: " + max;
        if (!plugin.withdraw(owner, amount)) return "Недостаточно денег. Нужно: " + plugin.money(amount);

        Contract c = new Contract();
        c.id = UUID.randomUUID().toString();
        c.owner = owner.getUniqueId();
        c.ownerName = owner.getName();
        c.target = target.getUniqueId();
        c.targetName = target.getName() != null ? target.getName() : "?";
        c.amount = amount;
        c.createdAt = System.currentTimeMillis();
        contracts.add(c);
        save();
        return null;
    }

    public String take(Player p, Contract c) {
        if (!contracts.contains(c)) return "Этот заказ уже недоступен.";
        if (c.taker != null) return "Этот заказ уже взял другой игрок.";
        if (c.owner.equals(p.getUniqueId())) return "Нельзя взять свой заказ.";
        if (c.target.equals(p.getUniqueId())) return "Нельзя взять заказ на самого себя.";
        for (Contract o : contracts) {
            if (o.owner.equals(c.owner) && o.target.equals(p.getUniqueId())) {
                return "Этот игрок заказал убийство вас, его заказ взять нельзя.";
            }
        }
        c.taker = p.getUniqueId();
        c.takerName = p.getName();
        save();
        Player owner = Bukkit.getPlayer(c.owner);
        if (owner != null) owner.sendMessage("§e" + p.getName() + " §7взял ваш заказ на §c" + c.targetName + "§7.");
        return null;
    }

    public String cancel(Player p, Contract c) {
        if (!contracts.contains(c)) return "Этот заказ уже недоступен.";
        if (!c.owner.equals(p.getUniqueId())) return "Это не ваш заказ.";
        contracts.remove(c);
        plugin.deposit(p, c.amount);
        save();
        p.sendMessage("§aЗаказ на §c" + c.targetName + " §aотменён, возвращено §e" + plugin.money(c.amount) + "§a.");
        return null;
    }

    /** Вызывается при убийстве игрока игроком. */
    public void onKill(Player killer, Player victim) {
        boolean changed = false;
        Iterator<Contract> it = contracts.iterator();
        while (it.hasNext()) {
            Contract c = it.next();
            if (c.taker != null && c.taker.equals(killer.getUniqueId()) && c.target.equals(victim.getUniqueId())) {
                plugin.deposit(killer, c.amount);
                killer.sendMessage("§aЗаказ выполнен! §7Вы получили §e" + plugin.money(c.amount)
                        + " §7за убийство §c" + victim.getName() + "§7.");
                Player owner = Bukkit.getPlayer(c.owner);
                if (owner != null) owner.sendMessage("§7Ваш заказ на §c" + c.targetName + " §7выполнил §e" + killer.getName() + "§7.");
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }

    public void startExpiryTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 1200L, 1200L);
    }

    private void expire() {
        long now = System.currentTimeMillis();
        long ttl = lifetimeMillis();
        boolean changed = false;
        Iterator<Contract> it = contracts.iterator();
        while (it.hasNext()) {
            Contract c = it.next();
            if (now - c.createdAt >= ttl) {
                plugin.deposit(Bukkit.getOfflinePlayer(c.owner), c.amount);
                Player owner = Bukkit.getPlayer(c.owner);
                if (owner != null) {
                    owner.sendMessage("§7Заказ на §c" + c.targetName + " §7истёк, возвращено §e" + plugin.money(c.amount) + "§7.");
                }
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }
}
