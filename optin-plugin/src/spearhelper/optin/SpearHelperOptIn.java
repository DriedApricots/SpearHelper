package spearhelper.optin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SpearHelper Opt-In: allows the SpearHelper client mod on this server.
 * The mod is disabled on every multiplayer server until the server sends it the "spearhelper:allow" plugin message.
 * This plugin sends it to every player with the permission "spearhelper.allow" (default: everyone).
 * Without this plugin the mod does nothing on the server.
 */
public final class SpearHelperOptIn extends JavaPlugin implements Listener {
    static final String CHANNEL = "spearhelper:allow";

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) allow(p); // after /reload
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    /** the client announces the channels it listens to; answer as soon as SpearHelper's channel shows up */
    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        if (CHANNEL.equals(event.getChannel())) allow(event.getPlayer());
    }

    /** fallback, in case the channel was announced before the player fully joined */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> allow(player), 20L);
    }

    private void allow(Player player) {
        if (!player.isOnline() || !player.hasPermission("spearhelper.allow")) return;
        if (!player.getListeningPluginChannels().contains(CHANNEL)) return; // no SpearHelper on this client
        player.sendPluginMessage(this, CHANNEL, new byte[]{1});
    }
}