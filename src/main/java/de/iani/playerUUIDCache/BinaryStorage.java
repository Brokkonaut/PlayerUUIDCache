package de.iani.playerUUIDCache;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 *
 * Each entry has 56 bytes:
 * 16 byte UUID
 * 32 byte Name (UTF16)
 * 8 byte Last Seen
 */
public class BinaryStorage {
    private static final int ENTRY_LENGTH = 56;
    private final PlayerUUIDCache plugin;
    private final HashMap<UUID, Integer> filePositions;
    private final RandomAccessFile file;
    private boolean loaded = false;
    private int totalEntries = 0;

    public BinaryStorage(PlayerUUIDCache plugin) throws IOException {
        this.plugin = plugin;
        filePositions = new HashMap<UUID, Integer>();
        File dataFile = new File(plugin.getDataFolder(), "players.dat");
        file = new RandomAccessFile(dataFile, "rw");
    }

    public ArrayList<CachedPlayer> loadAllPlayers() throws IOException {
        if (loaded) {
            throw new IllegalStateException("loadAllPlayers can only be called once");
        }
        loaded = true;
        ArrayList<CachedPlayer> players = new ArrayList<CachedPlayer>();
        if (file.length() >= 4) {
            int version = file.readInt();
            if (version != 1) {
                throw new IOException("Invalid data file version: " + version);
            }
            long length = file.length() - 4;
            if (length > 0) {
                long now = System.currentTimeMillis();
                byte[] data = new byte[(int) length];
                file.readFully(data);
                totalEntries = ((int) length) / ENTRY_LENGTH;
                ByteBuffer bb = ByteBuffer.wrap(data);
                char[] nameChars = new char[16];
                for (int i = 0; i < totalEntries; i++) {
                    long msb = bb.getLong();
                    long lsb = bb.getLong();
                    UUID uuid = new UUID(msb, lsb);
                    int nameLength = 16;
                    for (int j = 0; j < 16; j++) {
                        nameChars[j] = bb.getChar();
                        if (nameChars[j] == 0 && nameLength == 16) {
                            nameLength = j;
                        }
                    }
                    String name = new String(nameChars, 0, nameLength);
                    long lastSeen = bb.getLong();
                    players.add(new CachedPlayer(uuid, name, lastSeen, now));
                    // plugin.getLogger().info(uuid + ": " + name);
                    filePositions.put(uuid, i);
                }
            }
        } else {
            file.seek(0);
            file.writeInt(1);// current version
        }
        return players;
    }

    public void addOrUpdate(CachedPlayer player) throws IOException {
        if (!loaded) {
            throw new IllegalStateException("loadAllPlayers must be called first");
        }
        Integer knownPosition = filePositions.get(player.getUUID());
        if (knownPosition == null) {
            knownPosition = totalEntries++;
            filePositions.put(player.getUUID(), knownPosition);
            // plugin.getLogger().info("Add new: " + player.getUUID() + ": " + player.getName());
        } else {
            // plugin.getLogger().info("Found (" + knownPosition + "): " + player.getUUID() + ": " + player.getName());
        }
        file.seek(knownPosition * ENTRY_LENGTH + 4);
        byte[] dataOut = new byte[ENTRY_LENGTH];
        ByteBuffer buffer = ByteBuffer.wrap(dataOut);
        buffer.putLong(player.getUUID().getMostSignificantBits());
        buffer.putLong(player.getUUID().getLeastSignificantBits());
        String name = player.getName();
        for (int i = 0; i < 16; i++) {
            buffer.putChar(name.length() > i ? name.charAt(i) : 0);
        }
        buffer.putLong(player.getLastSeen());
        file.write(dataOut);
    }

    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error while trying to close the data file", e);
        }
    }
}
