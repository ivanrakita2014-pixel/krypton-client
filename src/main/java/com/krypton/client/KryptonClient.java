
package com.krypton.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class KryptonClient implements ClientModInitializer {
    public static final List<Module> MODULES = new ArrayList<>();
    public static final List<ChunkReport> REPORTS = new ArrayList<>();
    public static final List<BlockPos> ORE_POSITIONS = new ArrayList<>();

    private static KeyMapping clickGuiKey;
    private static KeyMapping scanKey;
    private static int tick;
    private static final int SCAN_RADIUS = 5;

    public static Module module(String name) {
        return MODULES.stream()
                .filter(m -> m.name().equals(name))
                .findFirst().orElse(null);
    }

    @Override
    public void onInitializeClient() {
        MODULES.add(new Module("Storage ESP", "Find loaded storage blocks", Module.Category.RENDER, true));
        MODULES.add(new Module("Entity ESP", "Entity highlighting", Module.Category.RENDER, false));
        MODULES.add(new Module("Block ESP", "Find selected ores", Module.Category.RENDER, false));
        MODULES.add(new Module("Sus Chunk", "Flag dense storage chunks", Module.Category.WORLD, true));
        MODULES.add(new Module("Chunk Finder", "Find storage in loaded chunks", Module.Category.WORLD, true));
        MODULES.add(new Module("Coordinates", "Show the Krypton HUD", Module.Category.HUD, true));

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("krypton", "main"));

        clickGuiKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.krypton.clickgui", GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        scanKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.krypton.scan", GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (clickGuiKey.consumeClick()) client.setScreen(new KryptonScreen());
            while (scanKey.consumeClick()) scanLoadedChunks(client);
            if (++tick % 80 == 0) scanLoadedChunks(client);
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("krypton", "krypton_hud"),
                KryptonClient::renderHud
        );
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        Module coordinates = module("Coordinates");

        if (client.player == null || coordinates == null || !coordinates.enabled()) return;

        var player = client.player;
        BlockPos pos = player.blockPosition();
        ChunkPos chunk = player.chunkPosition();

        int x = 10;
        int y = 10;
        int width = 190;
        int height = 82;

        graphics.fill(x, y, x + width, y + height, 0xB9101420);
        graphics.fill(x, y, x + width, y + 2, 0xFF55D7FF);

        graphics.drawString(client.font, "KRYPTON CLIENT",
                x + 9, y + 8, 0xFF75D7FF, false);
        graphics.drawString(client.font,
                "XYZ: " + pos.getX() + "  " + pos.getY() + "  " + pos.getZ(),
                x + 9, y + 23, 0xFFFFFFFF, false);
        graphics.drawString(client.font,
                "CHUNK: " + chunk.x + ", " + chunk.z,
                x + 9, y + 36, 0xFFD4DCEB, false);
        graphics.drawString(client.font,
                "FACING: " + player.getDirection().getName().toUpperCase(),
                x + 9, y + 49, 0xFFD4DCEB, false);
        graphics.drawString(client.font,
                "FPS: " + client.getFps(),
                x + 9, y + 62, 0xFF65E6A5, false);
    }

    private static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    public static void scanLoadedChunks(Minecraft client) {
        if (client.level == null || client.player == null) return;

        REPORTS.clear();
        ChunkPos center = client.player.chunkPosition();

        for (int cx = center.x - SCAN_RADIUS; cx <= center.x + SCAN_RADIUS; cx++) {
            for (int cz = center.z - SCAN_RADIUS; cz <= center.z + SCAN_RADIUS; cz++) {
                var chunk = client.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                int storage = 0;
                int spawners = 0;
                BlockPos nearest = null;
                double best = Double.MAX_VALUE;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (isStorage(be)) {
                        storage++;
                        double distance = be.getBlockPos()
                                .distToCenterSqr(client.player.position());

                        if (distance < best) {
                            best = distance;
                            nearest = be.getBlockPos().immutable();
                        }
                    }

                    if (be instanceof SpawnerBlockEntity) spawners++;
                }

                if (storage > 0 || spawners > 0) {
                    REPORTS.add(new ChunkReport(
                            cx, cz, storage, spawners, nearest,
                            storage >= 4 || spawners >= 2));
                }
            }
        }

        REPORTS.sort(Comparator.comparingInt(ChunkReport::storageCount).reversed());

        ORE_POSITIONS.clear();
        BlockPos origin = client.player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -24; x <= 24; x++) {
            for (int y = -12; y <= 12; y++) {
                for (int z = -24; z <= 24; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    var block = client.level.getBlockState(cursor).getBlock();

                    if (block == Blocks.DIAMOND_ORE
                            || block == Blocks.DEEPSLATE_DIAMOND_ORE
                            || block == Blocks.ANCIENT_DEBRIS
                            || block == Blocks.EMERALD_ORE
                            || block == Blocks.DEEPSLATE_EMERALD_ORE) {
                        ORE_POSITIONS.add(cursor.immutable());
                    }
                }
            }
        }
    }
}
