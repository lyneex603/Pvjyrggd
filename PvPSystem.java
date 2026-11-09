package me.server.pvpsystem;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class PvPSystem extends JavaPlugin {

    private Economy economy;
    private ContractManager contracts;
    private CombatListener combat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault или экономика (DualEco) не найдены. Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        contracts = new ContractManager(this);
        contracts.load();
        contracts.startExpiryTask();

        ContractMenu menu = new ContractMenu(this);
        combat = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combat, this);
        combat.applyAll();
        getServer().getPluginManager().registerEvents(menu, this);

        ContractCommand cc = new ContractCommand(this, menu);
        getCommand("contract").setExecutor(cc);
        getCommand("contract").setTabCompleter(cc);

        StatsCommands sc = new StatsCommands(this);
        for (String name : new String[]{"topkills", "topdeaths", "topkd", "stats", "pvpreload"}) {
            getCommand(name).setExecutor(sc);
        }
    }

    @Override
    public void onDisable() {
        if (combat != null) combat.restoreAll();
    }

    public CombatListener combat() { return combat; }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public ContractManager contracts() { return contracts; }

    public boolean has(OfflinePlayer p, double amount) { return economy.has(p, amount); }

    public boolean withdraw(OfflinePlayer p, double amount) {
        return economy.has(p, amount) && economy.withdrawPlayer(p, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer p, double amount) { economy.depositPlayer(p, amount); }

    public String money(double amount) {
        return String.format(Locale.US, "%,.0f", amount).replace(',', ' ') + "$";
    }
}
