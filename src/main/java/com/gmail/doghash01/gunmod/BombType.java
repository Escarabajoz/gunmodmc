package com.gmail.doghash01.gunmod;

/**
 * Immutable stat block for a Tsar-class bomb.
 *
 * <p>The in-game blast radius is a playable translation of the fictional yield — a literal
 * 100-million-megaton crater would be larger than the world — so radii are hand-tuned tiers.</p>
 *
 * @param radius       blast radius in blocks; the shockwave expands one-or-more rings per tick
 * @param fuseTicks    delay between arming and detonation (20 ticks = 1 second)
 * @param maxDamage    damage dealt at ground zero, falling off linearly to the blast edge
 * @param ringsPerTick how many shockwave rings advance each tick (higher = faster, heavier ticks)
 */
public record BombType(int radius, int fuseTicks, float maxDamage, int ringsPerTick) {
}
