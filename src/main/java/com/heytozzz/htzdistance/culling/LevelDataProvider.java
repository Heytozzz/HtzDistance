package com.heytozzz.htzdistance.culling;

import com.logisticscraft.occlusionculling.DataProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Block opacity lookups for the occlusion culling library, read from the client level on the
 * culling thread. Reads are lock-free and may observe a chunk mid-update; a wrong answer only
 * makes one ghost visible or hidden for one pass, which the next pass corrects.
 */
final class LevelDataProvider implements DataProvider {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private LevelChunk chunk;

    void setLevel(ClientLevel level) {
        this.level = level;
        this.chunk = null;
    }

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        if (chunk != null && chunk.getPos().x == chunkX && chunk.getPos().z == chunkZ) {
            return true;
        }
        chunk = level == null ? null : level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunk != null;
    }

    @Override
    public boolean isOpaqueFullCube(int x, int y, int z) {
        return chunk != null && chunk.getBlockState(pos.set(x, y, z)).isSolidRender();
    }

    @Override
    public void cleanup() {
        chunk = null;
    }
}
