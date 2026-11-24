package com.ethanpierce.cursedtech.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/** Per-player cursed energy state, saved with the player and kept on death. */
public class CursedData implements INBTSerializable<CompoundTag> {
    public Technique technique = Technique.NONE;
    public boolean assigned = false;
    public float energy = 100f;
    public final long[] cooldownUntil = new long[3];
    public final int[] cooldownMax = new int[3];
    public long burnoutUntil = 0;
    /** After a Black Flash the player is "in the zone": higher Black Flash odds. */
    public long zoneUntil = 0;

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("technique", technique.id);
        tag.putBoolean("assigned", assigned);
        tag.putFloat("energy", energy);
        tag.putLongArray("cd", cooldownUntil);
        tag.putIntArray("cdMax", cooldownMax);
        tag.putLong("burnout", burnoutUntil);
        tag.putLong("zone", zoneUntil);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        technique = Technique.byId(tag.getString("technique"));
        assigned = tag.getBoolean("assigned");
        energy = tag.contains("energy") ? tag.getFloat("energy") : 100f;
        long[] cd = tag.getLongArray("cd");
        for (int i = 0; i < 3 && i < cd.length; i++) cooldownUntil[i] = cd[i];
        int[] cdm = tag.getIntArray("cdMax");
        for (int i = 0; i < 3 && i < cdm.length; i++) cooldownMax[i] = cdm[i];
        burnoutUntil = tag.getLong("burnout");
        zoneUntil = tag.getLong("zone");
    }
}
