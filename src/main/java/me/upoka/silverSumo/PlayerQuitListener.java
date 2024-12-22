package me.upoka.silverSumo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public class PlayerQuitListener implements Listener {

    private SilverSumo plugin;

    public PlayerQuitListener(SilverSumo plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.joinedPlayers.contains(player)) {
            plugin.joinedPlayers.remove(player);
            player.teleport(plugin.location_fs.getConfig("locations.yml").getLocation("lobby"));
            player.sendMessage(plugin.messageFormatter(plugin.getConfig().getString("messages.leave-game")
                    .replace("%joinedplayers%", String.valueOf(plugin.joinedPlayers.size()))
                    .replace("%maxplayers%", String.valueOf(plugin.getMaxPlayers())))); // Itt használjuk a getter-t

            for (Player p : plugin.joinedPlayers) {
                p.sendMessage(plugin.messageFormatter(plugin.getConfig().getString("messages.player-left")
                        .replace("%player%", player.getName())
                        .replace("%joinedplayers%", String.valueOf(plugin.joinedPlayers.size()))
                        .replace("%maxplayers%", String.valueOf(plugin.getMaxPlayers())))); // Itt is a getter
            }

            if (plugin.joinedPlayers.size() < plugin.getConfig().getInt("minimum")) {
                plugin.getServer().getScheduler().cancelTask(plugin.startSched);
                for (Player p : plugin.joinedPlayers) {
                    p.sendMessage(plugin.messageFormatter(plugin.getConfig().getString("messages.not-enough-players")));
                }
            }
        }
    }
}
