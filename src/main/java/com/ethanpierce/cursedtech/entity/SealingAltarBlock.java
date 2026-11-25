package com.ethanpierce.cursedtech.entity;

import com.ethanpierce.cursedtech.core.Fx;
import com.ethanpierce.cursedtech.core.ModRegistries;
import com.ethanpierce.cursedtech.core.ServerConfig;
import com.ethanpierce.cursedtech.core.Tasks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Use the seal (by default Draconic Evolution's Chaotic Core) on this altar to
 * summon the Calamity. The seal is not consumed unless the config says so;
 * each altar has a cooldown and only one Calamity can be near it at a time.
 */
public class SealingAltarBlock extends Block {
    private static final Map<GlobalPos, Long> COOLDOWN = new HashMap<>();
    private static final int RITUAL_TICKS = 80;

    public SealingAltarBlock(Properties props) {
        super(props);
    }

    public static Item summonItem() {
        ResourceLocation id = ResourceLocation.tryParse(ServerConfig.SUMMON_ITEM.get());
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
        return Items.NETHER_STAR; // the seal mod isn't installed
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) hint(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void hint(Player player) {
        player.displayClientMessage(Component.literal("The altar waits for the seal: ")
                .withStyle(ChatFormatting.DARK_PURPLE)
                .append(new ItemStack(summonItem()).getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE)), true);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(summonItem())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level instanceof ServerLevel l) || !(player instanceof ServerPlayer sp)) {
            return ItemInteractionResult.SUCCESS;
        }
        GlobalPos key = GlobalPos.of(l.dimension(), pos);
        long now = l.getGameTime();
        Long ready = COOLDOWN.get(key);
        if (ready != null && ready > now) {
            sp.displayClientMessage(Component.literal("The altar is still recovering (" + (ready - now) / 20 + "s)")
                    .withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (!l.getEntitiesOfClass(CalamityEntity.class, new AABB(pos).inflate(96)).isEmpty()) {
            sp.displayClientMessage(Component.literal("The Calamity is already here.").withStyle(ChatFormatting.DARK_RED), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (!l.getBlockState(pos.above()).isAir() || !l.getBlockState(pos.above(2)).isAir()
                || !l.getBlockState(pos.above(3)).isAir()) {
            sp.displayClientMessage(Component.literal("Clear the space above the altar first.").withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.SUCCESS;
        }
        COOLDOWN.put(key, now + RITUAL_TICKS + ServerConfig.ALTAR_COOLDOWN_SECONDS.get() * 20L);
        if (ServerConfig.CONSUME_SUMMON_ITEM.get() && !sp.isCreative()) stack.shrink(1);

        for (ServerPlayer p : l.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(pos)) <= 48 * 48) {
                p.sendSystemMessage(Component.literal(sp.getName().getString()
                        + " presses the seal to the altar. Something deep below answers.").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        Vec3 c = Vec3.atBottomCenterOf(pos.above());
        l.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.5F, 0.5F);
        Tasks.start(l, (level2, age) -> {
            double h = age / (double) RITUAL_TICKS;
            for (int i = 0; i < 3; i++) {
                double a = age * 0.35 + i * Math.PI * 2 / 3;
                double r = 2.5 * (1 - h) + 0.4;
                level2.sendParticles(Fx.dust(CalamityEntity.COLOR, 1.4F), c.x + Math.cos(a) * r, c.y + h * 2.5,
                        c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (age % 10 == 0) level2.sendParticles(ParticleTypes.SOUL, c.x, c.y + 0.5, c.z, 4, 0.6, 0.3, 0.6, 0.02);
            if (age % 20 == 0) level2.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
            if (age < RITUAL_TICKS) return false;
            CalamityEntity boss = ModRegistries.CALAMITY.get().create(level2);
            if (boss == null) return true;
            boss.moveTo(c.x, c.y + 0.5, c.z, sp.getYRot() + 180, 0);
            var hp = boss.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(ServerConfig.CALAMITY_HEALTH.get());
            boss.setHealth(boss.getMaxHealth());
            boss.finalizeSpawn(level2, level2.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            level2.addFreshEntity(boss);
            level2.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            level2.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.6F);
            return true;
        });
        return ItemInteractionResult.SUCCESS;
    }
}
