package com.ethanpierce.cursedtech.core;

import com.ethanpierce.cursedtech.CursedTech;
import com.ethanpierce.cursedtech.domain.DomainBarrierBlock;
import com.ethanpierce.cursedtech.entity.CalamityEntity;
import com.ethanpierce.cursedtech.entity.SealingAltarBlock;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
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

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CursedTech.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CursedTech.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CalamityEntity>> CALAMITY = ENTITY_TYPES.register("calamity",
            () -> EntityType.Builder.of(CalamityEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.1F).eyeHeight(1.85F).fireImmune().clientTrackingRange(10).build("calamity"));

    public static final DeferredBlock<SealingAltarBlock> SEALING_ALTAR = BLOCKS.register("sealing_altar",
            () -> new SealingAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .lightLevel(s -> 7)
                    .sound(SoundType.DEEPSLATE_BRICKS)));

    public static final DeferredItem<BlockItem> SEALING_ALTAR_ITEM = ITEMS.registerSimpleBlockItem("sealing_altar", SEALING_ALTAR,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<Item> CALAMITY_HEART = ITEMS.registerSimpleItem("calamity_heart",
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant());
    public static final DeferredItem<DeferredSpawnEggItem> CALAMITY_SPAWN_EGG = ITEMS.register("calamity_spawn_egg",
            () -> new DeferredSpawnEggItem(CALAMITY, 0x1A0B2E, 0x9B30FF, new Item.Properties()));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CursedData>> CURSED =
            ATTACHMENTS.register("cursed", () -> AttachmentType.serializable(CursedData::new).copyOnDeath().build());

    public static final ResourceKey<DamageType> TECHNIQUE_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, CursedTech.id("technique"));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) ->
                e.put(CALAMITY.get(), CalamityEntity.createAttributes().build()));
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            var tab = e.getTabKey();
            if (tab == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(SEALING_ALTAR_ITEM);
            if (tab == net.minecraft.world.item.CreativeModeTabs.INGREDIENTS) e.accept(CALAMITY_HEART);
            if (tab == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) e.accept(CALAMITY_SPAWN_EGG);
        });
        ATTACHMENTS.register(modBus);
    }
}
