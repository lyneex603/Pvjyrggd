package me.server.pvpsystem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

@SuppressWarnings("deprecation")
public class ContractMenu implements Listener {

    public enum View { ALL, MINE, TAKEN }

    private static final int PER_PAGE = 45;

    private final PvPSystem plugin;

    public ContractMenu(PvPSystem plugin) { this.plugin = plugin; }

    public static class Holder implements InventoryHolder {
        final View view;
        final int page;
        final Map<Integer, Contract> slots = new HashMap<>();
        Inventory inv;

        Holder(View view, int page) { this.view = view; this.page = page; }

        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p, View view, int page) {
        UUID me = p.getUniqueId();
        List<Contract> list = new ArrayList<>();
        for (Contract c : plugin.contracts().all()) {
            switch (view) {
                case ALL -> { if (c.taker == null) list.add(c); }
                case MINE -> { if (c.owner.equals(me)) list.add(c); }
                case TAKEN -> { if (me.equals(c.taker)) list.add(c); }
            }
        }
        list.sort((a, b) -> Double.compare(b.amount, a.amount));

        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));

        String title = switch (view) {
            case ALL -> "§8Заказы на убийство";
            case MINE -> "§8Мои заказы";
            case TAKEN -> "§8Взятые мной";
        } + " §7" + (page + 1) + "/" + pages;

        Holder h = new Holder(view, page);
        Inventory inv = Bukkit.createInventory(h, 54, title);
        h.inv = inv;

        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < list.size(); i++) {
            Contract c = list.get(from + i);
            inv.setItem(i, contractItem(c, view));
            h.slots.put(i, c);
        }

        inv.setItem(45, button(Material.ARROW, "§eНазад"));
        inv.setItem(53, button(Material.ARROW, "§eВперёд"));
        inv.setItem(47, button(Material.PAPER, "§aСоздать заказ",
                "§7Команда:", "§f/contract create <ник> <сумма>",
                "§7Минимум: §e" + plugin.money(plugin.getConfig().getDouble("contracts.min-amount", 50000)),
                "§7Деньги списываются сразу и",
                "§7возвращаются при отмене или",
                "§7через " + plugin.getConfig().getLong("contracts.lifetime-hours", 24) + " ч."));
        String next = switch (view) {
            case ALL -> "§bПоказать: мои заказы";
            case MINE -> "§bПоказать: взятые мной";
            case TAKEN -> "§bПоказать: свободные заказы";
        };
        inv.setItem(49, button(Material.CHEST, next));

        p.openInventory(inv);
    }

    private ItemStack contractItem(Contract c, View view) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(c.target));
        meta.setDisplayName("§cЦель: §f" + c.targetName);

        long left = c.createdAt + plugin.contracts().lifetimeMillis() - System.currentTimeMillis();
        left = Math.max(0, left) / 60_000L;

        List<String> lore = new ArrayList<>();
        lore.add("§7Награда: §e" + plugin.money(c.amount));
        lore.add("§7Заказчик: §f" + c.ownerName);
        lore.add("§7Осталось: §f" + (left / 60) + " ч " + (left % 60) + " мин");
        lore.add(c.taker == null ? "§7Статус: §aсвободен" : "§7Исполнитель: §e" + c.takerName);
        lore.add("");
        switch (view) {
            case ALL -> lore.add("§aНажмите, чтобы взять заказ");
            case MINE -> lore.add("§cНажмите, чтобы отменить и вернуть деньги");
            case TAKEN -> lore.add("§7Убейте цель, чтобы получить награду");
        }
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack button(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getRawSlot();
        if (slot == 45) { open(p, h.view, h.page - 1); return; }
        if (slot == 53) { open(p, h.view, h.page + 1); return; }
        if (slot == 49) {
            View next = switch (h.view) {
                case ALL -> View.MINE;
                case MINE -> View.TAKEN;
                case TAKEN -> View.ALL;
            };
            open(p, next, 0);
            return;
        }

        Contract c = h.slots.get(slot);
        if (c == null) return;

        String err = null;
        if (h.view == View.ALL) {
            err = plugin.contracts().take(p, c);
            if (err == null) p.sendMessage("§aВы взяли заказ на §c" + c.targetName + "§a. Награда: §e" + plugin.money(c.amount));
        } else if (h.view == View.MINE) {
            err = plugin.contracts().cancel(p, c);
        } else {
            return;
        }
        if (err != null) p.sendMessage("§c" + err);
        open(p, h.view, h.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
