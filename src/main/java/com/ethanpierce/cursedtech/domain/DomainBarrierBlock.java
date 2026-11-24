package com.ethanpierce.cursedtech.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temporary domain wall. The domain removes it when it ends; as a fallback
 * (server crash mid-domain) every block also schedules its own removal.
 */
public class DomainBarrierBlock extends Block {
    public static final int FALLBACK_LIFETIME = 20 * 150;

    public DomainBarrierBlock(Properties props) {
        super(props);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, FALLBACK_LIFETIME);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
