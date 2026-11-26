package fr.skyhub;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/** Générateur de monde totalement vide (void). */
public final class VoidGenerator extends ChunkGenerator {

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public @NotNull Biome getBiome(@NotNull WorldInfo info, int x, int y, int z) {
                return Biome.PLAINS;
            }

            @Override
            public @NotNull List<Biome> getBiomes(@NotNull WorldInfo info) {
                return List.of(Biome.PLAINS);
            }
        };
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 101, 0.5);
    }
}
