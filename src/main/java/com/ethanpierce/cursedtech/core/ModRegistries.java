package com.ethanpierce.cursedtech.core;

import com.ethanpierce.cursedtech.CursedTech;
import com.ethanpierce.cursedtech.domain.DomainBarrierBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CursedTech.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CursedTech.MODID);

    public static final DeferredBlock<DomainBarrierBlock> DOMAIN_BARRIER = BLOCKS.register("domain_barrier",
            () -> new DomainBarrierBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false)));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CursedData>> CURSED =
            ATTACHMENTS.register("cursed", () -> AttachmentType.serializable(CursedData::new).copyOnDeath().build());

    public static final ResourceKey<DamageType> TECHNIQUE_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, CursedTech.id("technique"));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
