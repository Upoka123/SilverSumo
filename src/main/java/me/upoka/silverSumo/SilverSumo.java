package me.upoka.silverSumo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class SilverSumo extends JavaPlugin {

    FileManager location_fs;
    Freeze freeze;
    PlayerMoveEventHandler move;
    LeaveHandler leavehandler;
    ConsoleCommandSender console;
    @Override

    public void onEnable() {
        console = Bukkit.getServer().getConsoleSender();
        Collections.sort(argsList);
        Collections.sort(locationList);

        getCommand("sumo").setTabCompleter(new TabCompletion(this));
        location_fs = new FileManager(this, "locations.yml");
        freeze = new Freeze(this);
        move = new PlayerMoveEventHandler(this);
        leavehandler = new LeaveHandler(this);

        saveDefaultConfig();
        reloadConfig();
        getLogger().info("Start Sumo plugin v" + getDescription().getVersion());
        getServer().getPluginManager().registerEvents(freeze, this);
        getServer().getPluginManager().registerEvents(move, this);
        getServer().getPluginManager().registerEvents(leavehandler, this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
    }

    public void onDisable() {
        getLogger().info("Stop Sumo plugin v" + getDescription().getVersion());
        stop();
    }


    public String messageFormatter(String message) {
        return ChatColor.translateAlternateColorCodes('&', message.replace("%prefix%", getConfig().getString("prefix")));
    }



    private List<String> argsList = Arrays.asList("start", "stop", "join", "set", "info", "reload", "leave");
    private List<String> locationList = Arrays.asList("spawn", "lobby", "arena1", "arena2");
    public ArrayList<Player> joinedPlayers = new ArrayList<>();
    private int maxPlayers = getConfig().getInt("maximum");
    public boolean ingame;
    private int alertSched;
    public int startSched;
    private int countDown;

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(!(sender instanceof Player)) {
            return true;
        }

        Player player = (Player) sender;

        if(!cmd.getLabel().equalsIgnoreCase("sumo")) {
            return true;
        }


        if(args.length == 0) {
            player.sendMessage(messageFormatter("&5&l-------- &6SilverSumo&5&l --------"));
            player.sendMessage(messageFormatter("&e Commands: "));
            player.sendMessage(messageFormatter("&f  - /sumo"));
            for(int i = 0; i < argsList.size(); i++) {
                if(player.hasPermission("sumo.admin")) {
                    player.sendMessage(messageFormatter("&f  - /sumo " + argsList.get(i)));
                }
            }
            player.sendMessage(messageFormatter("&5&l---------------------"));
            return true;
        }

        if(args[0].equalsIgnoreCase("fly"))  {
            if(!player.getAllowFlight()) {
                player.setAllowFlight(true);
                return true;
            }
            player.setAllowFlight(false);
            return true;
        }


        if(args[0].equalsIgnoreCase("info")) {
            player.sendMessage(messageFormatter("&5&l---------------------"));
            player.sendMessage(messageFormatter("&d Sumo Plugin &fv" + getDescription().getVersion()));
            player.sendMessage(messageFormatter("&d Created:  &fUpoka"));
            player.sendMessage(messageFormatter("&d Arena now: " + getArenaState()));
            player.sendMessage(messageFormatter("&5&l---------------------"));
            return true;
        }

        if(args[0].equalsIgnoreCase("join")) {

            if(!ingame) {
                player.sendMessage(messageFormatter(getConfig().getString("messages.not-started")));
                return true;
            }

            if(joinedPlayers.contains(player)) {
                player.sendMessage(messageFormatter(getConfig().getString("messages.already-joined")));
                return true;
            }
            for (Player p : joinedPlayers) {
                p.sendMessage(messageFormatter(getConfig().getString("messages.player-joined")
                        .replace("%player%", player.getName())
                        .replace("%joinedplayers%", String.valueOf(joinedPlayers.size()+1))
                        .replace("%maxplayers%", String.valueOf(maxPlayers))));
            }
            joinedPlayers.add(player);
            player.teleport(location_fs.getConfig("locations.yml").getLocation("lobby"));
            player.sendMessage(messageFormatter(getConfig().getString("messages.join")
                    .replace("%joinedplayers%", String.valueOf(joinedPlayers.size()))
                    .replace("%maxplayers%", String.valueOf(maxPlayers))));


            if(joinedPlayers.size() == getConfig().getInt("minimum")) {
                countDown = getConfig().getInt("waiting");
                List<Integer> alertTimes = getConfig().getIntegerList("notifications");
                startSched = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                    @Override
                    public void run() {
                        if(alertTimes.contains(countDown)) {
                            for (Player p : joinedPlayers) {
                                p.sendMessage(messageFormatter(getConfig().getString("messages.start-soon")
                                        .replace("%mp%", String.valueOf(countDown))));
                            }
                        }
                        countDown--;
                        if(countDown == 0) {
                            Bukkit.getScheduler().cancelTask(startSched);
                            Bukkit.getScheduler().cancelTask(alertSched);
                            Collections.shuffle(joinedPlayers);
                            nextRound();
                        }
                    }

                }, 0, 20);

            }

            return true;
        }

        if(args[0].equalsIgnoreCase("leave")) {
            if (!joinedPlayers.contains(player)) {
                player.sendMessage(messageFormatter(getConfig().getString("messages.not-in-game")));
                return true;
            }

            if (ingame && inArenaPlayers.contains(player)) {
                Player opponent = inArenaPlayers.get(0).equals(player) ? inArenaPlayers.get(1) : inArenaPlayers.get(0);
                lose(opponent);
                player.sendMessage(messageFormatter(getConfig().getString("messages.player-left-during-combat")));
            } else {
                joinedPlayers.remove(player);
                player.teleport(location_fs.getConfig("locations.yml").getLocation("lobby"));
                player.sendMessage(messageFormatter(getConfig().getString("messages.leave-game"))
                        .replace("%joinedplayers%", String.valueOf(joinedPlayers.size()))
                        .replace("%maxplayers%", String.valueOf(maxPlayers)));

                for (Player p : joinedPlayers) {
                    p.sendMessage(messageFormatter(getConfig().getString("messages.player-left")
                            .replace("%player%", player.getName())
                            .replace("%joinedplayers%", String.valueOf(joinedPlayers.size()))
                            .replace("%maxplayers%", String.valueOf(maxPlayers))));
                }

                if (joinedPlayers.size() < getConfig().getInt("minimum")) {
                    Bukkit.getScheduler().cancelTask(startSched);
                    player.sendMessage(messageFormatter(getConfig().getString("messages.not-enough-player")));
                }
            }

            return true;
        }


        if(player.hasPermission("sumo.admin")) {
            if(args[0].equalsIgnoreCase("start")) {

                if(ingame) {
                    player.sendMessage(messageFormatter(getConfig().getString("messages.already-going")));
                    return true;
                }

                ingame = true;
                player.sendMessage(messageFormatter(getConfig().getString("messages.you-started-it")));

                int kesleltetes = getConfig().getInt("delay") * 20;
                alertSched = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                    @Override
                    public void run() {
                        for (Player p : getServer().getOnlinePlayers()) {
                            if(!joinedPlayers.contains(p)) {
                                TextComponent component = new TextComponent(TextComponent.fromLegacyText(messageFormatter(getConfig().getString("messages.advertiser"))));
                                component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sumo join"));
                                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(messageFormatter("&7Click here to join"))));
                                p.spigot().sendMessage(component);
                            }
                        }
                    }
                }, 0, kesleltetes);

                return true;
            }


            if(args[0].equalsIgnoreCase("stop")) {
                if(!ingame) {
                    player.sendMessage(messageFormatter(getConfig().getString("messages.not-started")));
                } else {
                    stop();
                    player.sendMessage(messageFormatter(getConfig().getString("messages.event-ended")));
                }
                return true;
            }



            if(args[0].equalsIgnoreCase("set")) {
                if(args.length > 1) {
                    if(locationList.contains(args[1])) {
                        Location currentLocation = player.getLocation();
                        location_fs.getConfig("locations.yml").set(args[1], currentLocation);
                        location_fs.saveConfig("locations.yml");
                        location_fs.reloadConfig("locations.yml");
                        player.sendMessage(messageFormatter(getConfig().getString("messages.location-set").replace("%place%", args[1])));
                    } else {
                        player.sendMessage(messageFormatter(getConfig().getString("messages.not-exist-arena")));
                    }
                } else {
                    player.sendMessage(messageFormatter(getConfig().getString("messages.not-exist-arena")));
                }
                return true;
            }


            if(args[0].equalsIgnoreCase("reload")) {
                try {
                    reloadConfig();
                    location_fs.reloadConfig("locations.yml");
                    player.sendMessage(messageFormatter(getConfig().getString("messages.reload-successfully")));
                } catch (Exception e) {
                    player.sendMessage(messageFormatter(getConfig().getString("messages.reload-unsuccessful")));
                }
                return true;
            }
        } else {
            player.sendMessage(messageFormatter(getConfig().getString("messages.no-permission")));
        }
        return true;
    }
    public ArrayList<Player> inArenaPlayers = new ArrayList<>();
    private int pos = 0;
    private int arenaStartSched;
    private int arenaStartTimer;
    public void nextRound() {
        inArenaPlayers.clear();
        inArenaPlayers.add(joinedPlayers.get(pos));
        if(pos+1 > joinedPlayers.size()) { pos = 0; }
        inArenaPlayers.add(joinedPlayers.get(pos+1));

        inArenaPlayers.get(0).teleport(location_fs.getConfig("locations.yml").getLocation("arena1"));
        inArenaPlayers.get(1).teleport(location_fs.getConfig("locations.yml").getLocation("arena2"));

        for(Player p : inArenaPlayers) {
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setFlying(false);
            p.setGameMode(GameMode.ADVENTURE);
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 9999, true));
            freeze.freeze(p, true);
        }

        arenaStartTimer = getConfig().getInt("countdown");
        arenaStartSched = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for(Player p : inArenaPlayers) {
                    if(p.getPlayer().getName() == inArenaPlayers.get(0).getName()) {
                        p.sendTitle(messageFormatter("&a" + arenaStartTimer), messageFormatter("&6🗡 &f" + inArenaPlayers.get(1).getName()), 0, 20, 0);
                    }  else {
                        p.sendTitle(messageFormatter("&a" + arenaStartTimer), messageFormatter("&6🗡 &f" + inArenaPlayers.get(0).getName()), 0, 20, 0);
                    }

                }

                if(arenaStartTimer == 0) {
                    for(Player pl : inArenaPlayers) {
                        freeze.freeze(pl, false);
                    }
                    Bukkit.getScheduler().cancelTask(arenaStartSched);
                }
                arenaStartTimer--;
            }
        }, 0, 20);

    }

    public String getArenaState()  {
        if(ingame) {
            return messageFormatter("&aIn process");
        }
        return messageFormatter("&cNot started");
    }


    public void stop() {
        // Leállítja az alert scheduler-t
        if (alertSched != -1) {
            Bukkit.getScheduler().cancelTask(alertSched);
        }

        for (Player p : joinedPlayers) {
            p.teleport(location_fs.getConfig("locations.yml").getLocation("spawn"));
            p.sendMessage(messageFormatter(getConfig().getString("messages.event-end")));
            freeze.freeze(p, false);
        }
        joinedPlayers.clear();
        inArenaPlayers.clear();
        ingame = false;
    }

    public void lose(Player p) {
        if(!inArenaPlayers.contains(p) || !joinedPlayers.contains(p)) { return; }

        joinedPlayers.remove(p);
        inArenaPlayers.remove(p);
        p.teleport(location_fs.getConfig("locations.yml").getLocation("spawn"));
        p.sendMessage(messageFormatter(getConfig().getString("messages.lost")));
        p.removePotionEffect(PotionEffectType.RESISTANCE);

        Player winner = inArenaPlayers.get(0);
        winner.removePotionEffect(PotionEffectType.RESISTANCE);
        if(joinedPlayers.size() == 1) {
            arenaWin(winner);
            return;
        }
        winner.sendMessage(messageFormatter(getConfig().getString("messages.round-won")));
        winner.teleport(location_fs.getConfig("locations.yml").getLocation("lobby"));
        nextRound();
    }


    public void arenaWin(Player p) {
        for(Player allp : getServer().getOnlinePlayers()) {
            allp.sendMessage(messageFormatter(getConfig().getString("messages.won-broadcast")
                    .replace("%winner%", p.getName())));
        }
        List<String> winCommands = getConfig().getStringList("win-commands");
        for(String cmd : winCommands) {
            Bukkit.dispatchCommand(console, cmd.replace("%winner%", p.getName()));
        }
        stop();
    }
    public ArrayList<Player> getInArenaPlayers() {
        return inArenaPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
}