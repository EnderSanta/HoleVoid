package ru.holevoid;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class HoleVoid extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<String, Location> warps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<UUID, Location> backs = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> superVanished = new HashSet<>();
    private final List<Listing> listings = new ArrayList<>();
    private YamlConfiguration data;
    private File dataFile;
    private int listingSequence = 1;
    private static final String PREFIX = "§8[§5Hole§dVoid§8] §r";
    private static final String AH_TITLE = "§5§lHOLEVOID §8» §dАукцион";
    private final Map<UUID, AuctionView> auctionViews = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        for (String cmd : List.of("sethome","home","delhome","homes","setwarp","warp","delwarp","warps","spawn","setspawn","back","tpa","tpaccept","tpdeny","balance","bal","pay","baltop","eco","ah","vanish","svanish","board")) {
            PluginCommand command = getCommand(cmd);
            if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        }
        for (Player p : Bukkit.getOnlinePlayers()) updatePlayer(p);
        new BukkitRunnable() { @Override public void run() { for (Player p : Bukkit.getOnlinePlayers()) updateBoard(p); } }.runTaskTimer(this, 20L, 40L);
        getLogger().info("HoleVoid enabled for Paper 1.21.11");
    }

    @Override public void onDisable() { saveData(); }

    private void loadData() {
        ConfigurationSection b = data.getConfigurationSection("balances");
        if (b != null) for (String k : b.getKeys(false)) try { balances.put(UUID.fromString(k), b.getDouble(k)); } catch (IllegalArgumentException ignored) {}
        ConfigurationSection h = data.getConfigurationSection("homes");
        if (h != null) for (String id : h.getKeys(false)) {
            try { UUID uuid = UUID.fromString(id); ConfigurationSection hs = h.getConfigurationSection(id); if (hs == null) continue;
                Map<String, Location> map = new HashMap<>(); for (String name : hs.getKeys(false)) { Location loc = readLocation(hs.getConfigurationSection(name)); if (loc != null) map.put(name.toLowerCase(Locale.ROOT), loc); } homes.put(uuid, map);
            } catch (IllegalArgumentException ignored) {}
        }
        ConfigurationSection w = data.getConfigurationSection("warps");
        if (w != null) for (String name : w.getKeys(false)) { Location loc = readLocation(w.getConfigurationSection(name)); if (loc != null) warps.put(name, loc); }
        ConfigurationSection ls = data.getConfigurationSection("auction");
        if (ls != null) { listingSequence = ls.getInt("next-id", 1); ConfigurationSection entries = ls.getConfigurationSection("listings"); if (entries != null) for (String id : entries.getKeys(false)) {
            ConfigurationSection s = entries.getConfigurationSection(id); if (s == null) continue;
            try { ItemStack item = s.getItemStack("item"); UUID seller = UUID.fromString(s.getString("seller", "")); if (item != null && !item.getType().isAir()) listings.add(new Listing(Integer.parseInt(id), seller, s.getString("seller-name", "Unknown"), s.getDouble("price"), item)); } catch (Exception ignored) {}
        }}
    }
    private void saveData() {
        data.set("balances", null); for (var e : balances.entrySet()) data.set("balances." + e.getKey(), e.getValue());
        data.set("homes", null); for (var e : homes.entrySet()) for (var h : e.getValue().entrySet()) writeLocation("homes." + e.getKey() + "." + h.getKey(), h.getValue());
        data.set("warps", null); for (var e : warps.entrySet()) writeLocation("warps." + e.getKey(), e.getValue());
        data.set("auction", null); data.set("auction.next-id", listingSequence);
        for (Listing l : listings) { String p = "auction.listings." + l.id; data.set(p + ".seller", l.seller.toString()); data.set(p + ".seller-name", l.sellerName); data.set(p + ".price", l.price); data.set(p + ".item", l.item); }
        try { data.save(dataFile); } catch (Exception ex) { getLogger().severe("Could not save data.yml: " + ex.getMessage()); }
    }
    private void writeLocation(String path, Location l) { if (l == null || l.getWorld() == null) return; data.set(path + ".world", l.getWorld().getName()); data.set(path + ".x", l.getX()); data.set(path + ".y", l.getY()); data.set(path + ".z", l.getZ()); data.set(path + ".yaw", l.getYaw()); data.set(path + ".pitch", l.getPitch()); }
    private Location readLocation(ConfigurationSection s) { if (s == null) return null; World w = Bukkit.getWorld(s.getString("world", "")); if (w == null) return null; return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")); }
    private String money(double value) { return new DecimalFormat("#,##0.##").format(value); }
    private double balance(UUID id) { return balances.getOrDefault(id, 0.0); }
    private void setBalance(UUID id, double amount) { balances.put(id, Math.max(0, Math.round(amount * 100.0) / 100.0)); }
    private void msg(CommandSender s, String text) { s.sendMessage(PREFIX + text); }
    private boolean staff(CommandSender s, String permission) { if (s.hasPermission(permission)) return true; msg(s, "§cНедостаточно прав."); return false; }
    private Player player(CommandSender s) { if (s instanceof Player p) return p; msg(s, "§cЭта команда доступна только игроку."); return null; }
    private String arg(String[] a, int i) { return a.length > i ? a[i] : ""; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        switch (c) {
            case "sethome" -> { Player p = player(sender); if (p == null) return true; String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home"; Map<String, Location> hm = homes.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()); int max = p.hasPermission("holevoid.homes.unlimited") ? 999 : getConfig().getInt("homes.default-limit", 3); if (!hm.containsKey(name) && hm.size() >= max) { msg(p, "§cЛимит домов: §f" + max + "§c. Используй §f/delhome <название>§c."); return true; } hm.put(name, p.getLocation().clone()); saveData(); msg(p, "§aДом §d" + name + " §aсохранён."); }
            case "home" -> { Player p = player(sender); if (p == null) return true; String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home"; Location loc = homes.getOrDefault(p.getUniqueId(), Map.of()).get(name); if (loc == null) msg(p, "§cДом не найден. §7Используй /homes."); else { backs.put(p.getUniqueId(), p.getLocation().clone()); p.teleport(loc); msg(p, "§aТелепортация домой: §d" + name); } }
            case "delhome" -> { Player p = player(sender); if (p == null) return true; if (args.length == 0) { msg(p, "§cИспользование: /delhome <название>"); return true; } Map<String, Location> hm = homes.get(p.getUniqueId()); if (hm == null || hm.remove(args[0].toLowerCase(Locale.ROOT)) == null) msg(p, "§cДом не найден."); else { saveData(); msg(p, "§aДом удалён."); } }
            case "homes" -> { Player p = player(sender); if (p == null) return true; Set<String> names = homes.getOrDefault(p.getUniqueId(), Map.of()).keySet(); msg(p, "§dТвои дома: §f" + (names.isEmpty() ? "нет" : String.join("§7, §f", names)) + " §8| §7/home <название>"); }
            case "setwarp" -> { Player p = player(sender); if (p == null || !staff(p, "holevoid.warp.set")) return true; if (args.length == 0) { msg(p, "§cИспользование: /setwarp <название>"); return true; } warps.put(args[0], p.getLocation().clone()); saveData(); msg(p, "§aВарп §d" + args[0] + " §aсохранён."); }
            case "warp" -> { Player p = player(sender); if (p == null) return true; if (args.length == 0) { msg(p, "§dВарпы: §f" + (warps.isEmpty() ? "нет" : String.join("§7, §f", warps.keySet()))); return true; } Location loc = warps.get(args[0]); if (loc == null) msg(p, "§cТакого варпа нет."); else { backs.put(p.getUniqueId(), p.getLocation().clone()); p.teleport(loc); msg(p, "§aТелепортация на варп §d" + args[0]); } }
            case "delwarp" -> { if (!staff(sender, "holevoid.warp.set")) return true; if (args.length == 0) { msg(sender, "§cИспользование: /delwarp <название>"); return true; } if (warps.remove(args[0]) == null) msg(sender, "§cВарп не найден."); else { saveData(); msg(sender, "§aВарп удалён."); } }
            case "warps" -> msg(sender, "§dВарпы: §f" + (warps.isEmpty() ? "нет" : String.join("§7, §f", warps.keySet())));
            case "setspawn" -> { Player p = player(sender); if (p == null || !staff(p, "holevoid.spawn.set")) return true; writeLocation("spawn", p.getLocation()); saveData(); msg(p, "§aСпавн установлен."); }
            case "spawn" -> { Player p = player(sender); if (p == null) return true; Location loc = readLocation(data.getConfigurationSection("spawn")); if (loc == null) msg(p, "§cСпавн ещё не настроен. Администратор: /setspawn"); else { backs.put(p.getUniqueId(), p.getLocation().clone()); p.teleport(loc); msg(p, "§aТы телепортирован на спавн."); } }
            case "back" -> { Player p = player(sender); if (p == null) return true; Location loc = backs.remove(p.getUniqueId()); if (loc == null) msg(p, "§cПредыдущая позиция не найдена."); else { p.teleport(loc); msg(p, "§aВозвращение выполнено."); } }
            case "tpa" -> { Player p = player(sender); if (p == null) return true; if (args.length == 0) { msg(p, "§cИспользование: /tpa <ник>"); return true; } Player target = Bukkit.getPlayerExact(args[0]); if (target == null || target.equals(p)) { msg(p, "§cИгрок не найден."); return true; } tpaRequests.put(target.getUniqueId(), p.getUniqueId()); msg(p, "§aЗапрос отправлен игроку §d" + target.getName()); msg(target, "§d" + p.getName() + " §fхочет телепортироваться к тебе. §a/tpaccept §7или §c/tpdeny"); }
            case "tpaccept" -> { Player p = player(sender); if (p == null) return true; UUID id = tpaRequests.remove(p.getUniqueId()); Player from = id == null ? null : Bukkit.getPlayer(id); if (from == null) msg(p, "§cАктивных запросов нет."); else { backs.put(from.getUniqueId(), from.getLocation().clone()); from.teleport(p); msg(p, "§aЗапрос принят."); msg(from, "§aТелепортация выполнена."); } }
            case "tpdeny" -> { Player p = player(sender); if (p == null) return true; UUID id = tpaRequests.remove(p.getUniqueId()); if (id == null) msg(p, "§cАктивных запросов нет."); else { msg(p, "§cЗапрос отклонён."); Player from = Bukkit.getPlayer(id); if (from != null) msg(from, "§cЗапрос отклонён."); } }
            case "balance", "bal" -> { Player p = sender instanceof Player pl ? pl : null; if (args.length > 0 && sender.hasPermission("holevoid.eco.admin")) { OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]); msg(sender, "§fБаланс §d" + op.getName() + "§f: §a" + money(balance(op.getUniqueId())) + " $"); } else if (p != null) msg(p, "§fТвой баланс: §a" + money(balance(p.getUniqueId())) + " $"); else msg(sender, "§cИспользование: /balance <ник>"); }
            case "pay" -> { Player p = player(sender); if (p == null) return true; if (args.length < 2) { msg(p, "§cИспользование: /pay <ник> <сумма>"); return true; } Player target = Bukkit.getPlayerExact(args[0]); double amount = parseAmount(args[1]); if (target == null || target.equals(p) || amount <= 0) msg(p, "§cУкажи онлайн-игрока и положительную сумму."); else if (balance(p.getUniqueId()) < amount) msg(p, "§cНедостаточно средств."); else { setBalance(p.getUniqueId(), balance(p.getUniqueId()) - amount); setBalance(target.getUniqueId(), balance(target.getUniqueId()) + amount); saveData(); msg(p, "§aТы перевёл §f" + money(amount) + " $ §aигроку §d" + target.getName()); msg(target, "§aПолучено §f" + money(amount) + " $ §aот §d" + p.getName()); } }
            case "eco" -> { if (!staff(sender, "holevoid.eco.admin")) return true; if (args.length < 3) { msg(sender, "§cИспользование: /eco <give|take|set> <ник> <сумма>"); return true; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); double amount = parseAmount(args[2]); if (amount < 0 || !Double.isFinite(amount)) { msg(sender, "§cНеверная сумма."); return true; } double old = balance(target.getUniqueId()); switch (args[0].toLowerCase(Locale.ROOT)) { case "give" -> setBalance(target.getUniqueId(), old + amount); case "take" -> setBalance(target.getUniqueId(), old - amount); case "set" -> setBalance(target.getUniqueId(), amount); default -> { msg(sender, "§cИспользование: /eco <give|take|set> <ник> <сумма>"); return true; } } saveData(); msg(sender, "§aБаланс обновлён: §d" + target.getName() + " §f→ §a" + money(balance(target.getUniqueId())) + " $"); }
            case "baltop" -> showBaltop(sender, args.length > 0 ? parsePage(args[0]) : 1);
            case "vanish" -> toggleVanish(sender, false);
            case "svanish" -> toggleVanish(sender, true);
            case "board" -> { Player p = player(sender); if (p != null) { updateBoard(p); msg(p, "§aТабло HoleVoid обновлено."); } }
            case "ah" -> { Player p = player(sender); if (p == null) return true; if (args.length > 0 && args[0].equalsIgnoreCase("sell")) { if (args.length < 2) { msg(p, "§cИспользование: /ah sell <цена>"); return true; } double price = parseAmount(args[1]); ItemStack hand = p.getInventory().getItemInMainHand(); if (price <= 0 || hand.getType().isAir() || !Double.isFinite(price)) { msg(p, "§cВозьми предмет в руку и укажи цену больше нуля."); return true; } if (listings.size() >= getConfig().getInt("auction.max-listings", 500)) { msg(p, "§cАукцион заполнен."); return true; } listings.add(new Listing(listingSequence++, p.getUniqueId(), p.getName(), price, hand.clone())); p.getInventory().setItemInMainHand(new ItemStack(Material.AIR)); saveData(); msg(p, "§aПредмет выставлен за §d" + money(price) + " $§a."); openAuction(p, "ALL", 0); } else openAuction(p, "ALL", args.length > 0 ? Math.max(0, parsePage(args[0]) - 1) : 0); }
        }
        return true;
    }
    private double parseAmount(String s) { try { return Double.parseDouble(s.replace(",", "")); } catch (Exception e) { return -1; } }
    private int parsePage(String s) { try { return Math.max(1, Integer.parseInt(s)); } catch (Exception e) { return 1; } }
    private void showBaltop(CommandSender sender, int page) {
        List<Map.Entry<UUID, Double>> sorted = balances.entrySet().stream().filter(e -> e.getValue() > 0).sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).toList();
        int perPage = 10, pages = Math.max(1, (int)Math.ceil(sorted.size() / (double)perPage)); page = Math.min(page, pages); int start = (page - 1) * perPage;
        sender.sendMessage("§5§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"); sender.sendMessage("      §5§l✦ HOLEVOID §8• §dТОП БОГАТЕЙШИХ"); sender.sendMessage("§7Страница §d" + page + "§7/§d" + pages + " §8• §7Игроков в рейтинге: §f" + sorted.size()); sender.sendMessage("§5§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (sorted.isEmpty()) sender.sendMessage("§7Пока нет игроков с положительным балансом.");
        for (int i = start; i < Math.min(start + perPage, sorted.size()); i++) { var e = sorted.get(i); OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey()); String name = op.getName() == null ? e.getKey().toString().substring(0, 8) : op.getName(); String rank = switch (i) { case 0 -> "§6♛"; case 1 -> "§f♛"; case 2 -> "§c♛"; default -> "§8#" + (i + 1); }; sender.sendMessage("§8» " + rank + " §d" + name + " §8» §a" + money(e.getValue()) + " $"); }
        if (sender instanceof Player p) { int rank = -1; for (int i = 0; i < sorted.size(); i++) if (sorted.get(i).getKey().equals(p.getUniqueId())) { rank = i + 1; break; } sender.sendMessage("§7Твой баланс: §a" + money(balance(p.getUniqueId())) + " $ §8• §7Место: §d" + (rank < 0 ? "—" : "#" + rank)); }
        sender.sendMessage("§7Навигация: §d/baltop <страница> §8(§f10 игроков на страницу§8)"); sender.sendMessage("§5§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    private void toggleVanish(CommandSender sender, boolean superMode) { Player p = player(sender); if (p == null) return; String perm = superMode ? "holevoid.svanish" : "holevoid.vanish"; if (!staff(p, perm)) return; Set<UUID> set = superMode ? superVanished : vanished; if (set.remove(p.getUniqueId())) { if (superMode) vanished.remove(p.getUniqueId()); msg(p, "§a" + (superMode ? "Суперваниш" : "Ваниш") + " выключен."); } else { if (superMode) vanished.remove(p.getUniqueId()); set.add(p.getUniqueId()); msg(p, "§a" + (superMode ? "Суперваниш" : "Ваниш") + " включён."); } for (Player viewer : Bukkit.getOnlinePlayers()) applyVisibility(viewer, p); refreshTabAndBoards(); }
    private void applyVisibility(Player viewer, Player hidden) { if (viewer.equals(hidden)) return; boolean canSeeSuper = viewer.hasPermission("holevoid.svanish.see"); boolean canSeeNormal = viewer.hasPermission("holevoid.vanish.see"); boolean shouldHide = superVanished.contains(hidden.getUniqueId()) ? !canSeeSuper : vanished.contains(hidden.getUniqueId()) && !canSeeNormal; if (shouldHide) viewer.hidePlayer(this, hidden); else viewer.showPlayer(this, hidden); }
    private void refreshTabAndBoards() { for (Player viewer : Bukkit.getOnlinePlayers()) { for (Player hidden : Bukkit.getOnlinePlayers()) applyVisibility(viewer, hidden); updatePlayer(viewer); } }
    private void updatePlayer(Player p) { p.setPlayerListHeaderFooter("§5§l✦ HOLEVOID ✦\n§7Режим: §dAnarchy", "\n§dTelegram: §7" + getConfig().getString("telegram-url", "ссылка скоро") + "\n§5HoleVoid"); p.setPlayerListName("§d" + p.getName()); updateBoard(p); }
    private int visibleOnline(Player viewer) { int count = 0; for (Player other : Bukkit.getOnlinePlayers()) { if (other.equals(viewer)) { if (!vanished.contains(other.getUniqueId()) && !superVanished.contains(other.getUniqueId())) count++; } else if (!superVanished.contains(other.getUniqueId()) || viewer.hasPermission("holevoid.svanish.see")) { if (!vanished.contains(other.getUniqueId()) || viewer.hasPermission("holevoid.vanish.see")) count++; } } return count; }
    private void updateBoard(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard(); Objective obj = sb.registerNewObjective("hvboard", "dummy", "§5§lHOLEVOID"); obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        String[] lines = {"§8§m----------------", "§dИгрок: §f" + p.getName(), "§dБаланс: §f" + money(balance(p.getUniqueId())) + " $", "§dУбийства: §f" + p.getStatistic(Statistic.PLAYER_KILLS), "§dСмерти: §f" + p.getStatistic(Statistic.DEATHS), "§dОнлайн: §f" + visibleOnline(p), "§dРежим: §fAnarchy", "§8§m----------------", "§dTelegram: §7" + getConfig().getString("telegram-url", "скоро")};
        int score = lines.length; for (String line : lines) { String unique = line; while (sb.getEntries().contains(unique)) unique += "§r"; obj.getScore(unique).setScore(score--); }
        p.setScoreboard(sb);
    }
    private void openAuction(Player p, String category, int page) {
        AuctionView view = new AuctionView(category, page); auctionViews.put(p.getUniqueId(), view);
        Inventory inv = Bukkit.createInventory(null, 54, AH_TITLE + " §8• §d" + categoryTitle(category));
        ItemStack border = item(Material.PURPLE_STAINED_GLASS_PANE, "§5", List.of("§8HoleVoid Auction")); for (int i = 0; i < 9; i++) inv.setItem(i, border); for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(10, item(Material.COMPASS, "§d§lВсе предметы", List.of("§7Показать все лоты", "§8Нажми, чтобы открыть")));
        inv.setItem(11, item(Material.POTION, "§dЗелья", List.of("§7Категория: зелья")));
        inv.setItem(12, item(Material.COOKED_BEEF, "§dЕда", List.of("§7Категория: еда")));
        inv.setItem(13, item(Material.IRON_PICKAXE, "§dИнструменты", List.of("§7Категория: инструменты")));
        inv.setItem(14, item(Material.GRASS_BLOCK, "§dБлоки и ресурсы", List.of("§7Категория: блоки и ресурсы")));
        inv.setItem(15, item(Material.CHEST, "§dМои лоты", List.of("§7Посмотреть выставленные тобой предметы")));
        inv.setItem(22, item(Material.AMETHYST_SHARD, "§5§lHOLEVOID AUCTION", List.of("§7Страница: §d" + (page + 1), "§7Лотов: §f" + filteredListings(p, category).size(), "§8Выбери предмет, чтобы купить его")));
        List<Listing> visible = filteredListings(p, category); int perPage = 27; int start = page * perPage;
        for (int i = start; i < Math.min(start + perPage, visible.size()); i++) { Listing l = visible.get(i); int slot = 18 + (i - start); if (slot >= 45) break; ItemStack display = l.item.clone(); ItemMeta meta = display.getItemMeta(); if (meta != null) { List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>(); lore.add("§8§m----------------"); lore.add("§7Цена: §a" + money(l.price) + " $"); lore.add("§7Продавец: §d" + l.sellerName); lore.add("§8Лот #" + l.id); lore.add("§eНажми, чтобы купить"); meta.setLore(lore); display.setItemMeta(meta); } inv.setItem(slot, display); }
        inv.setItem(48, item(Material.ARROW, "§d← Назад", List.of("§7Предыдущая страница")));
        inv.setItem(49, item(Material.PAPER, "§fСтраница §d" + (page + 1), List.of("§7Используй стрелки для навигации")));
        inv.setItem(50, item(Material.ARROW, "§dВперёд →", List.of("§7Следующая страница")));
        inv.setItem(52, item(Material.BARRIER, "§cЗакрыть", List.of("§7Закрыть аукцион")));
        p.openInventory(inv);
    }
    private List<Listing> filteredListings(Player p, String category) { return listings.stream().filter(l -> { if (category.equals("MINE")) return l.seller.equals(p.getUniqueId()); if (category.equals("ALL")) return true; return switch (category) { case "POTIONS" -> l.item.getType().name().contains("POTION"); case "FOOD" -> l.item.getType().isEdible(); case "TOOLS" -> { String n = l.item.getType().name(); yield n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.equals("SHEARS") || n.equals("FISHING_ROD"); } case "BLOCKS" -> l.item.getType().isBlock() || l.item.getType().name().contains("INGOT") || l.item.getType().name().contains("DIAMOND") || l.item.getType().name().contains("EMERALD"); default -> true; }; }).sorted(Comparator.comparingInt(l -> -l.id)).collect(Collectors.toList()); }
    private String categoryTitle(String c) { return switch (c) { case "POTIONS" -> "Зелья"; case "FOOD" -> "Еда"; case "TOOLS" -> "Инструменты"; case "BLOCKS" -> "Блоки и ресурсы"; case "MINE" -> "Мои лоты"; default -> "Все предметы"; }; }
    private ItemStack item(Material type, String name, List<String> lore) { ItemStack stack = new ItemStack(type); ItemMeta meta = stack.getItemMeta(); if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); stack.setItemMeta(meta); } return stack; }
    private boolean canFitItem(Player player, ItemStack offered) {
        int remaining = offered.getAmount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack existing : contents) {
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(offered)) continue;
            remaining -= Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            if (remaining <= 0) return true;
        }
        int maxStack = offered.getMaxStackSize();
        for (ItemStack existing : contents) {
            if (existing == null || existing.getType().isAir()) remaining -= maxStack;
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }
    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getView().getTitle() == null || !e.getView().getTitle().startsWith(AH_TITLE)) return;
        e.setCancelled(true); if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return; int slot = e.getRawSlot(); AuctionView view = auctionViews.getOrDefault(p.getUniqueId(), new AuctionView("ALL", 0));
        if (slot == 10) { openAuction(p, "ALL", 0); return; } if (slot == 11) { openAuction(p, "POTIONS", 0); return; } if (slot == 12) { openAuction(p, "FOOD", 0); return; } if (slot == 13) { openAuction(p, "TOOLS", 0); return; } if (slot == 14) { openAuction(p, "BLOCKS", 0); return; } if (slot == 15) { openAuction(p, "MINE", 0); return; }
        if (slot == 48) { openAuction(p, view.category, Math.max(0, view.page - 1)); return; } if (slot == 50) { openAuction(p, view.category, view.page + 1); return; } if (slot == 52) { p.closeInventory(); return; }
        if (slot >= 18 && slot < 45) { List<Listing> vis = filteredListings(p, view.category); int idx = view.page * 27 + slot - 18; if (idx < 0 || idx >= vis.size()) return; Listing l = vis.get(idx); if (l.seller.equals(p.getUniqueId())) { msg(p, "§cНельзя купить собственный лот."); return; } if (balance(p.getUniqueId()) < l.price) { msg(p, "§cНедостаточно денег для покупки."); return; }
            if (l.item.getType().isAir()) { listings.remove(l); saveData(); openAuction(p, view.category, view.page); return; }
            if (!canFitItem(p, l.item)) { msg(p, "§cНедостаточно места в инвентаре."); return; } p.getInventory().addItem(l.item.clone());
            setBalance(p.getUniqueId(), balance(p.getUniqueId()) - l.price); setBalance(l.seller, balance(l.seller) + l.price); listings.remove(l); saveData(); msg(p, "§aПокупка успешна за §d" + money(l.price) + " $"); Player seller = Bukkit.getPlayer(l.seller); if (seller != null) msg(seller, "§aТвой лот купили за §d" + money(l.price) + " $"); openAuction(p, view.category, view.page);
        }
    }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView().getTitle() == null || !e.getView().getTitle().startsWith(AH_TITLE)) return;
        UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || p.getOpenInventory().getTitle() == null || !p.getOpenInventory().getTitle().startsWith(AH_TITLE)) auctionViews.remove(id);
        });
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { Player p = e.getPlayer(); for (Player other : Bukkit.getOnlinePlayers()) applyVisibility(p, other); for (Player other : Bukkit.getOnlinePlayers()) applyVisibility(other, p); updatePlayer(p); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { vanished.remove(e.getPlayer().getUniqueId()); superVanished.remove(e.getPlayer().getUniqueId()); refreshTabAndBoards(); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String c = command.getName().toLowerCase(Locale.ROOT); if (args.length == 1) { if (c.equals("ah")) return List.of("sell"); if (c.equals("eco")) return List.of("give", "take", "set"); if (c.equals("baltop")) return List.of("1", "2", "3", "10", "22"); if (c.equals("home") || c.equals("delhome")) return sender instanceof Player p ? new ArrayList<>(homes.getOrDefault(p.getUniqueId(), Map.of()).keySet()) : List.of(); if (c.equals("warp") || c.equals("delwarp")) return new ArrayList<>(warps.keySet()); }
        if (args.length == 2 && (c.equals("eco") || c.equals("pay") || (c.equals("balance") && sender.hasPermission("holevoid.eco.admin")))) return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()); return Collections.emptyList();
    }
    private record Listing(int id, UUID seller, String sellerName, double price, ItemStack item) {}
    private record AuctionView(String category, int page) {}
}
