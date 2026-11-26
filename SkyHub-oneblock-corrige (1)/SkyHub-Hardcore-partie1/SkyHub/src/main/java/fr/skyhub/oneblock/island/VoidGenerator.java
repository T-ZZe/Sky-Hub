package fr.skyhub.oneblock.island;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/** Monde vide (aucun terrain, biome plaine). */
public final class VoidGenerator extends ChunkGenerator {

    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo info, int x, int y, int z) { return Biome.PLAINS; }
            @Override public List<Biome> getBiomes(WorldInfo info) { return List.of(Biome.PLAINS); }
        };
    }

    @Override public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, 0.5, 65, 0.5); }
}
