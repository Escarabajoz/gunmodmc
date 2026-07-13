package com.gmail.doghash01.gunmod;

/**
 * Immutable stat block for a Tsar-class bomb or a black hole bomb.
 *
 * <p>The in-game blast radius is a playable translation of the fictional yield — a literal
 * 100-million-megaton crater would be larger than the world — so radii are hand-tuned tiers.</p>
 *
 * @param radius       blast radius in blocks; the shockwave expands one-or-more rings per tick
 * @param fuseTicks    delay between arming and detonation (20 ticks = 1 second)
 * @param maxDamage    damage dealt at ground zero, falling off linearly to the blast edge
 * @param ringsPerTick how many shockwave rings advance each tick (higher = faster, heavier ticks)
 * @param heat         thermal intensity 0..1: drives how far the scorch ring extends past the
 *                     crater, the odds of melting blocks (sand to glass, stone to magma, ice to
 *                     water, clay to terracotta), lava pooling at ground zero, surface fires,
 *                     and how long entities burn
 * @param style        NUKE detonates outward (blast, heat, mushroom cloud); BLACK_HOLE pulls
 *                     entities inward and silently absorbs the terrain sphere
 */
public record BombType(int radius, int fuseTicks, float maxDamage, int ringsPerTick, float heat,
                       Style style) {

    public enum Style {
        NUKE,
        BLACK_HOLE
    }

    public static BombType nuke(int radius, int fuseTicks, float maxDamage, int ringsPerTick, float heat) {
        return new BombType(radius, fuseTicks, maxDamage, ringsPerTick, heat, Style.NUKE);
    }

    public static BombType blackHole(int radius, int fuseTicks, float maxDamage) {
        // Black holes have no heat: they absorb rather than burn, one ring every other tick.
        return new BombType(radius, fuseTicks, maxDamage, 1, 0.0F, Style.BLACK_HOLE);
    }

    /** Radius of the molten (lava-pooling) core around ground zero. */
    public int moltenRadius() {
        return (int) (radius * 0.30F * heat);
    }

    /** How many blocks past the crater the thermal scorch ring extends. */
    public int scorchWidth() {
        return (int) (radius * 0.35F * heat);
    }
}
