package spearhelper.optin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * SpearHelper Opt-In: allows the SpearHelper client mod on this server.
 * The mod is disabled on every multiplayer server until the server sends it the "spearhelper:allow" plugin message.
 * This plugin sends it to every player with the permission "spearhelper.allow" (default: everyone).
 * Without this plugin the mod does nothing on the server.
 *
 * Paper (and Purpur, Leaf ...): Paper resets the attack charge the moment the held item changes, so the spear attack
 * SpearHelper makes in the same tick is rejected (on Spigot / vanilla the charge carries over to the spear).
 * With "spear-charge-on-paper: true" the plugin keeps the vanilla behaviour for allowed players, only when they switch
 * to a spear: Paper fires PlayerItemHeldEvent before it compares the held item with the remembered one, so the plugin
 * sets the remembered item to the spear and Paper sees no item change. Nothing else changes.
 */
public final class SpearHelperOptIn extends JavaPlugin implements Listener {
    static final String CHANNEL = "spearhelper:allow";

    private boolean spearCharge;
    private boolean reflectionFailed;
    private Field lastItemField;
    private Method getHandle, asNmsCopy;
    // player -> {slot held before switching to the spear, time of that switch}: the mod selects that slot again right after the attack
    private final java.util.Map<java.util.UUID, long[]> spearSwap = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        spearCharge = getConfig().getBoolean("spear-charge-on-paper", true);
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
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        spearSwap.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> allow(player), 20L);
    }

    private boolean allowed(Player player) {
        return player.hasPermission("spearhelper.allow") && player.getListeningPluginChannels().contains(CHANNEL);
    }

    private void allow(Player player) {
        if (!player.isOnline() || !allowed(player)) return; // no SpearHelper on this client
        player.sendPluginMessage(this, CHANNEL, new byte[]{1});
    }

    /** Paper: keep the attack charge when an allowed player switches to a spear (see class comment) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (!spearCharge || reflectionFailed) return;
        Player player = event.getPlayer();
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        boolean toSpear = next != null && next.getType().name().endsWith("_SPEAR");
        long now = System.nanoTime();
        if (toSpear) {
            if (!allowed(player)) return;
            spearSwap.put(player.getUniqueId(), new long[]{event.getPreviousSlot(), now});
        } else {
            // back to the slot held before the spear, right after the spear attack (vanilla / Spigot: both switches in
            // one tick never reset the charge). Any other switch resets it as usual.
            long[] swap = spearSwap.remove(player.getUniqueId());
            if (swap == null || swap[0] != event.getNewSlot() || now - swap[1] > 150_000_000L) return;
        }
        try {
            if (lastItemField == null) init(player);
            Object handle = getHandle.invoke(player);
            Object nmsNext = asNmsCopy.invoke(null, next == null ? new ItemStack(org.bukkit.Material.AIR) : next);
            // Player.lastItemInMainHand: Paper resets the attack charge when it differs from the held item
            lastItemField.set(handle, nmsNext);
            if (toSpear) {
                // LivingEntity.lastEquipmentItems: Paper also swaps the hand's attribute modifiers at once, so the spear's slow
                // attack speed would apply to this attack. Vanilla / Spigot apply them only at the end of the tick, after the
                // spear attack and the switch back. Keep the previous item's attributes for this switch...
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> equipment = (java.util.Map<Object, Object>) lastEquipmentField.get(handle);
                equipment.put(mainHand, nmsNext);
                int spearSlot = event.getNewSlot();
                // ...and if the player stays on the spear, let Paper apply the spear's attributes normally 3 ticks later
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!player.isOnline() || player.getInventory().getHeldItemSlot() != spearSlot) return;
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.Map<Object, Object> eq = (java.util.Map<Object, Object>) lastEquipmentField.get(getHandle.invoke(player));
                        eq.put(mainHand, asNmsCopy.invoke(null, new ItemStack(org.bukkit.Material.AIR)));
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                }, 3L);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // not Paper (Spigot keeps the charge on its own) or another server internals layout
            reflectionFailed = true;
            getLogger().info("spear-charge-on-paper is not available on this server (" + e + "); not needed on Spigot");
        }
    }

    private Field lastEquipmentField;
    private Object mainHand;

    private void init(Player player) throws ReflectiveOperationException {
        getHandle = player.getClass().getMethod("getHandle");
        Object handle = getHandle.invoke(player);
        lastItemField = findField(handle.getClass(), "lastItemInMainHand");
        lastEquipmentField = findField(handle.getClass(), "lastEquipmentItems");
        ClassLoader loader = handle.getClass().getClassLoader();
        Class<?> slotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot", false, loader);
        mainHand = slotClass.getField("MAINHAND").get(null);
        Class<?> craftItem = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
        asNmsCopy = craftItem.getMethod("asNMSCopy", ItemStack.class);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}