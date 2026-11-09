package me.server.pvpsystem;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class CombatListener implements Listener {

    private final PvPSystem plugin;
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, ArrayDeque<Long>> hits = new HashMap<>();
    private final Map<UUID, Integer> critCounter = new HashMap<>();

    public CombatListener(PvPSystem plugin) { this.plugin = plugin; }

    // ---------- Бедрок-ПВП: без КД на удар ----------

    public void applyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    public void restoreAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance as = p.getAttribute(Attribute.ATTACK_SPEED);
            if (as != null) as.setBaseValue(as.getDefaultValue());
            p.setMaximumNoDamageTicks(20);
        }
    }

    private void apply(Player p) {
        FileConfiguration c = plugin.getConfig();
        boolean on = c.getBoolean("bedrock-pvp.enabled", true);

        AttributeInstance as = p.getAttribute(Attribute.ATTACK_SPEED);
        if (as != null) {
            if (on && c.getBoolean("bedrock-pvp.no-attack-cooldown", true)) as.setBaseValue(1024.0);
            else as.setBaseValue(as.getDefaultValue());
        }
        p.setMaximumNoDamageTicks(on ? c.getInt("bedrock-pvp.invulnerability-ticks", 10) : 20);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { apply(e.getPlayer()); }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) { apply(e.getPlayer()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> apply(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        hits.remove(id);
        critCounter.remove(id);
        pending.remove(id);
    }

    // ---------- Тихий лимит КПС + без sweep-атаки ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttackEarly(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!(e.getDamager() instanceof Player damager)) return;
        FileConfiguration c = plugin.getConfig();

        if (c.getBoolean("bedrock-pvp.enabled", true)
                && c.getBoolean("bedrock-pvp.disable-sweep", true)
                && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            e.setCancelled(true);
            return;
        }

        int limit = c.getInt("cps-limit", 18);
        if (limit <= 0) return;

        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = hits.computeIfAbsent(damager.getUniqueId(), k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
        if (q.size() >= limit) {
            e.setCancelled(true); // молча, без сообщений, кика и бана
            return;
        }
        q.addLast(now);
    }

    // ---------- Крит каждый 4-й удар ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrit(EntityDamageByEntityEvent e) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("crit.enabled", true)) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player damager) || damager.equals(victim)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        // удар пришёл во время неуязвимости жертвы, урона не будет, крит не тратим
        if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2f) return;

        int every = Math.max(1, c.getInt("crit.every-hits", 4));
        int count = critCounter.getOrDefault(damager.getUniqueId(), 0) + 1;
        if (count < every) {
            critCounter.put(damager.getUniqueId(), count);
            return;
        }
        critCounter.put(damager.getUniqueId(), 0);

        e.setDamage(e.getDamage() * c.getDouble("crit.damage-multiplier", 1.5));

        Location loc = victim.getLocation().add(0, 1, 0);
        victim.getWorld().spawnParticle(Particle.CRIT, loc, 30, 0.3, 0.4, 0.3, 0.3);
        victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 15, 0.3, 0.4, 0.3, 0.2);
        victim.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
    }

    // ---------- Слабая отдача ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("knockback.enabled", true)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        Player damager = null;
        if (e.getDamager() instanceof Player p) damager = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) damager = p;
        if (damager == null || damager.equals(victim)) return;

        UUID id = victim.getUniqueId();
        pending.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> pending.remove(id), 3L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        if (!pending.remove(e.getPlayer().getUniqueId())) return;
        double h = plugin.getConfig().getDouble("knockback.horizontal-multiplier", 0.6);
        double v = plugin.getConfig().getDouble("knockback.vertical-multiplier", 0.8);
        Vector vel = e.getVelocity();
        vel.setX(vel.getX() * h);
        vel.setZ(vel.getZ() * h);
        vel.setY(vel.getY() * v);
        e.setVelocity(vel);
    }

    // ---------- Награда за убийство + заказы ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        double reward = plugin.getConfig().getDouble("reward.kill-amount", 100);
        if (reward > 0) {
            plugin.deposit(killer, reward);
            killer.sendMessage("§a+" + plugin.money(reward) + " §7за убийство §c" + victim.getName());
        }
        plugin.contracts().onKill(killer, victim);
    }
}
