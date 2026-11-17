package com.krypton.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
        return MODULES.stream().filter(m -> m.name().equals(name)).findFirst().orElse(null);
    }

    @Override
    public void onInitializeClient() {
        MODULES.add(new Module("Storage ESP", "Outline loaded chests, barrels and shulkers", Module.Category.RENDER, true));
        MODULES.add(new Module("Entity ESP", "Outline visible living entities", Module.Category.RENDER, false));
        MODULES.add(new Module("Block ESP", "Highlight selected ore blocks in loaded chunks", Module.Category.RENDER, false));
        MODULES.add(new Module("Sus Chunk", "Flag loaded chunks with unusually dense storage blocks", Module.Category.WORLD, true));
        MODULES.add(new Module("Chunk Finder", "List loaded chunks containing storage blocks", Module.Category.WORLD, true));
        MODULES.add(new Module("Coordinates", "Show player and chunk coordinates", Module.Category.HUD, true));

        KeyMapping.Category category = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("krypton", "main"));
        clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.krypton.clickgui", GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        scanKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.krypton.scan", GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (clickGuiKey.consumeClick()) client.setScreen(new KryptonScreen());
            while (scanKey.consumeClick()) scanLoadedChunks(client);
            if (++tick % 80 == 0) scanLoadedChunks(client);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(KryptonClient::renderWorld);
    }

    private static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    public static void scanLoadedChunks(Minecraft client) {
        if (client.level == null || client.player == null) return;
        REPORTS.clear();
        ChunkPos center = client.player.chunkPosition();
        for (int cx = center.x - SCAN_RADIUS; cx <= center.x + SCAN_RADIUS; cx++) {
            for (int cz = center.z - SCAN_RADIUS; cz <= center.z + SCAN_RADIUS; cz++) {
                var chunk = client.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                int storage = 0, spawners = 0;
                BlockPos nearest = null;
                double best = Double.MAX_VALUE;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (isStorage(be)) {
                        storage++;
                        double d = be.getBlockPos().distToCenterSqr(client.player.position());
                        if (d < best) { best = d; nearest = be.getBlockPos().immutable(); }
                    }
                    if (be instanceof SpawnerBlockEntity) spawners++;
                }
                if (storage > 0 || spawners > 0) {
                    REPORTS.add(new ChunkReport(cx, cz, storage, spawners, nearest, storage >= 4 || spawners >= 2));
                }
            }
        }
        REPORTS.sort(Comparator.comparingInt(ChunkReport::storageCount).reversed());

        // Cache ore positions on the tick scan, never scan thousands of blocks every render frame.
        ORE_POSITIONS.clear();
        BlockPos origin = client.player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -24; x <= 24; x++) {
            for (int y = -12; y <= 12; y++) {
                for (int z = -24; z <= 24; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    var block = client.level.getBlockState(cursor).getBlock();
                    if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.ANCIENT_DEBRIS || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
                        ORE_POSITIONS.add(cursor.immutable());
                    }
                }
            }
        }
    }

    private static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || context.consumers() == null) return;
        PoseStack pose = context.matrices();
        Vec3 camera = context.gameRenderer().getMainCamera().position();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource buffers = context.consumers();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        if (module("Storage ESP").enabled()) {
            for (var chunk : REPORTS) {
                var loaded = client.level.getChunkSource().getChunk(chunk.chunkX(), chunk.chunkZ(), false);
                if (loaded == null) continue;
                for (BlockEntity be : loaded.getBlockEntities().values()) {
                    if (isStorage(be)) {
                        BlockPos p = be.getBlockPos();
                        float r = chunk.suspicious() ? 1.0f : 0.15f;
                        float g = chunk.suspicious() ? 0.18f : 0.85f;
                        LevelRenderer.renderLineBox(pose, lines, new AABB(p).inflate(0.015), r, g, 0.95f, 1.0f);
                    }
                }
            }
        }
        if (module("Block ESP").enabled()) {
            for (BlockPos p : ORE_POSITIONS) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(p).inflate(0.005), 0.15f, 0.9f, 1.0f, 1.0f);
            }
        }
        if (module("Entity ESP").enabled()) {
            for (var entity : client.level.entitiesForRendering()) {
                if (entity == client.player || !entity.isAlive()) continue;
                AABB box = entity.getBoundingBox().inflate(0.025);
                LevelRenderer.renderLineBox(pose, lines, box, 1.0f, 0.35f, 0.25f, 1.0f);
            }
        }
        pose.popPose();
    }
}
