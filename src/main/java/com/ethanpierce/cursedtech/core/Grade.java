package com.ethanpierce.cursedtech.core;

/**
 * Sorcerer grades, lowest to highest. Each grade scales energy, regen and
 * damage, and unlocks more of a technique:
 *   Grade 2       - abilities 1 and 2
 *   Semi-Grade 1  - bigger reserves
 *   Grade 1       - Domain Expansion
 *   Special Grade - Maximum technique and Reverse Cursed Technique
 */
public enum Grade {
    GRADE_2("grade_2", "Grade 2", 0xB0B0B0, 1.0f, 1.0f, 1.00f),
    SEMI_GRADE_1("semi_grade_1", "Semi-Grade 1", 0x7FD0FF, 1.3f, 1.2f, 1.10f),
    GRADE_1("grade_1", "Grade 1", 0xFFD24A, 1.7f, 1.5f, 1.25f),
    SPECIAL("special", "Special Grade", 0xFF3B6B, 2.5f, 2.0f, 1.50f);

    public final String id;
    public final String displayName;
    public final int color;
    public final float energyMult;
    public final float regenMult;
    public final float damageMult;

    Grade(String id, String displayName, int color, float energyMult, float regenMult, float damageMult) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.energyMult = energyMult;
        this.regenMult = regenMult;
        this.damageMult = damageMult;
    }

    public boolean atLeast(Grade other) {
        return ordinal() >= other.ordinal();
    }

    public Grade next() {
        return this == SPECIAL ? null : values()[ordinal() + 1];
    }

    public static Grade byId(String id) {
        for (Grade g : values()) if (g.id.equals(id)) return g;
        return GRADE_2;
    }
}
