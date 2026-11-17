package com.krypton.client;

import net.minecraft.core.BlockPos;

public record ChunkReport(int chunkX, int chunkZ, int storageCount, int spawnerCount, BlockPos nearestStorage, boolean suspicious) { }
