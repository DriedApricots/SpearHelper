package com.s0ftpulse.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * SpearHelper: while the key is held, every time the attack is fully charged it selects the spear from the hotbar,
 * attacks with it and selects the previous slot again.
 * Port of Spear-Swapper by s0ftpulse (https://github.com/shuraa-lox/Spear-Swapper, CC0-1.0) to Minecraft 26.x.
 *
 * Server-side opt-in (Modrinth content rules 3.3): the mod always works in singleplayer, but on a multiplayer server it
 * stays disabled until the server sends the "spearhelper:allow" payload (for example with the SpearHelper Opt-In
 * plugin). The permission is reset on every new connection.
 */
public class SpearHelperClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("spearhelper");

    /** sent by the server to allow the mod; its content is ignored */
    public record AllowPayload() implements CustomPacketPayload {
        public static final Type<AllowPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("spearhelper", "allow"));
        public static final StreamCodec<FriendlyByteBuf, AllowPayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeByte(1),
                buf -> {
                    buf.skipBytes(buf.readableBytes());
                    return new AllowPayload();
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private KeyMapping swapAndClickKey;
    private int originalSlot = -1;
    private Method startAttack;
    private boolean attackFailed;
    private Set<Item> spears;

    private volatile boolean serverAllowed;
    private boolean warned;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("spearhelper", "main"));
        swapAndClickKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.spearhelper.swap_click", GLFW.GLFW_KEY_R, category));

        PayloadTypeRegistry.clientboundPlay().register(AllowPayload.TYPE, AllowPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(AllowPayload.TYPE, (payload, context) -> {
            serverAllowed = true;
            LOGGER.info("SpearHelper: allowed by the server");
        });
        ClientPlayConnectionEvents.INIT.register((handler, client) -> serverAllowed = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverAllowed = false);

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    /** singleplayer (own integrated server) or a server that opted in */
    private boolean allowed(Minecraft client) {
        return client.hasSingleplayerServer() || serverAllowed;
    }

    private int findSpear(Minecraft client) {
        if (spears == null) {
            spears = Set.of(Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.GOLDEN_SPEAR,
                    Items.IRON_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR);
        }
        Inventory inventory = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (spears.contains(stack.getItem())) return i;
        }
        return -1;
    }

    private void attack(Minecraft client) {
        if (attackFailed) return;
        try {
            if (startAttack == null) {
                startAttack = Minecraft.class.getDeclaredMethod("startAttack");
                startAttack.setAccessible(true);
            }
            startAttack.invoke(client);
        } catch (ReflectiveOperationException | RuntimeException e) {
            attackFailed = true; // log once, not every tick
            LOGGER.error("SpearHelper: could not start an attack, the mod is disabled for this session", e);
        }
    }

    private void onEndTick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (!swapAndClickKey.isDown()) {
            originalSlot = -1;
            warned = false;
            return;
        }
        if (!allowed(client)) {
            if (!warned) {
                warned = true; // once per key press
                client.player.sendOverlayMessage(Component.translatable("spearhelper.not_allowed"));
            }
            return;
        }
        Inventory inventory = client.player.getInventory();
        if (originalSlot == -1) originalSlot = inventory.getSelectedSlot();
        if (client.player.getAttackStrengthScale(0.0F) < 1.0F) return;
        int spearSlot = findSpear(client);
        if (spearSlot == -1) return;
        inventory.setSelectedSlot(spearSlot);
        attack(client);
        inventory.setSelectedSlot(originalSlot);
    }
}
