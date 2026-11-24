package com.ethanpierce.cursedtech.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/** Per-player cursed energy state, saved with the player and kept on death. */
public class CursedData implements INBTSerializable<CompoundTag> {
    public static final int SLOTS = 4;

    public Technique technique = Technique.NONE;
    public boolean assigned = false;
    public Grade grade = Grade.GRADE_2;
    public float energy = 100f;
    public final long[] cooldownUntil = new long[SLOTS];
    public final int[] cooldownMax = new int[SLOTS];
    public long burnoutUntil = 0;
    /** After a Black Flash the player is "in the zone": higher Black Flash odds. */
    public long zoneUntil = 0;

    // Progression
    public long xp = 0;
    public int blackFlashes = 0;
    public int bossKills = 0;

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("technique", technique.id);
        tag.putBoolean("assigned", assigned);
        tag.putString("grade", grade.id);
        tag.putFloat("energy", energy);
        tag.putLongArray("cd", cooldownUntil);
        tag.putIntArray("cdMax", cooldownMax);
        tag.putLong("burnout", burnoutUntil);
        tag.putLong("zone", zoneUntil);
        tag.putLong("xp", xp);
        tag.putInt("blackFlashes", blackFlashes);
        tag.putInt("bossKills", bossKills);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        technique = Technique.byId(tag.getString("technique"));
        assigned = tag.getBoolean("assigned");
        // Players from 0.1.0 have no grade yet: they start at Grade 2.
        grade = tag.contains("grade") ? Grade.byId(tag.getString("grade")) : Grade.GRADE_2;
        energy = tag.contains("energy") ? tag.getFloat("energy") : 100f;
        long[] cd = tag.getLongArray("cd");
        for (int i = 0; i < SLOTS && i < cd.length; i++) cooldownUntil[i] = cd[i];
        int[] cdm = tag.getIntArray("cdMax");
        for (int i = 0; i < SLOTS && i < cdm.length; i++) cooldownMax[i] = cdm[i];
        burnoutUntil = tag.getLong("burnout");
        zoneUntil = tag.getLong("zone");
        xp = tag.getLong("xp");
        blackFlashes = tag.getInt("blackFlashes");
        bossKills = tag.getInt("bossKills");
    }

    public float maxEnergy() {
        return ServerConfig.MAX_ENERGY.get().floatValue() * grade.energyMult;
    }

    public float regenPerTick() {
        return ServerConfig.REGEN_PER_SECOND.get().floatValue() * grade.regenMult / 20f;
    }
}
