package fr.skyhub.claim;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Copie des blocs d'une zone (palette + indices), sauvegardée dans claims/<nom>.snap. */
public final class Snapshot {
    public final int minX, minY, minZ, sx, sy, sz;
    public final String[] paletteStrings;
    public final int[] data;
    private BlockData[] palette;

    public Snapshot(int minX, int minY, int minZ, int sx, int sy, int sz, String[] paletteStrings, int[] data) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.paletteStrings = paletteStrings;
        this.data = data;
    }

    /** Palette en BlockData (à appeler sur le thread principal). */
    public synchronized BlockData[] palette() {
        if (palette == null) {
            palette = new BlockData[paletteStrings.length];
            for (int i = 0; i < palette.length; i++) {
                try {
                    palette[i] = Bukkit.createBlockData(paletteStrings[i]);
                } catch (IllegalArgumentException e) {
                    palette[i] = Bukkit.createBlockData(org.bukkit.Material.AIR);
                }
            }
        }
        return palette;
    }

    /** Bloc d'origine à cette position (coordonnées monde), ou null si hors de la zone. */
    public BlockData at(int x, int y, int z) {
        int dx = x - minX, dy = y - minY, dz = z - minZ;
        if (dx < 0 || dy < 0 || dz < 0 || dx >= sx || dy >= sy || dz >= sz) return null;
        return palette()[data[(dx * sy + dy) * sz + dz]];
    }

    public void write(File f) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(f))))) {
            out.writeInt(1);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);
            out.writeInt(sx);
            out.writeInt(sy);
            out.writeInt(sz);
            out.writeInt(paletteStrings.length);
            for (String s : paletteStrings) out.writeUTF(s);
            for (int v : data) out.writeInt(v);
        }
    }

    public static Snapshot read(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))))) {
            in.readInt(); // version
            int minX = in.readInt(), minY = in.readInt(), minZ = in.readInt();
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            String[] pal = new String[in.readInt()];
            for (int i = 0; i < pal.length; i++) pal[i] = in.readUTF();
            int[] data = new int[sx * sy * sz];
            for (int i = 0; i < data.length; i++) data[i] = in.readInt();
            return new Snapshot(minX, minY, minZ, sx, sy, sz, pal, data);
        }
    }
}
