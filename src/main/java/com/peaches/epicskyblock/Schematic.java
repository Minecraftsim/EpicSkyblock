package com.peaches.epicskyblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.jnbt.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class Schematic {

    public enum SchematicVersion {
        v1_13, v_1_8
    }

    private short width;
    private short length;
    private short height;
    private List<Tag> tileEntities;

    private byte[] blocks;
    private byte[] data;
    private byte[] blockdata;
    private List<Tag> entities;
    private Map<String, Tag> palette;

    private Integer version;

    private SchematicVersion schematicVersion;

    public Schematic(short width, short length, short height, List<Tag> tileEntities, byte[] blocks, byte[] data, List<Tag> entities) {
        this.blocks = blocks;
        this.data = data;
        this.width = width;
        this.length = length;
        this.height = height;
        this.tileEntities = tileEntities;
        this.entities = entities;
        this.schematicVersion = SchematicVersion.v_1_8;
    }

    public Schematic(short width, short length, short height, byte[] blockdata, Map<String, Tag> palette, int version) {
        this.width = width;
        this.length = length;
        this.height = height;
        this.palette = palette;
        this.blockdata = blockdata;
        this.schematicVersion = SchematicVersion.v1_13;
        this.version = version;
    }

    public Schematic(short width, short length, short height, List<Tag> tileEntities, byte[] blockdata, Map<String, Tag> palette, int version) {
        this.width = width;
        this.length = length;
        this.height = height;
        this.palette = palette;
        this.blockdata = blockdata;
        this.schematicVersion = SchematicVersion.v1_13;
        this.tileEntities = tileEntities;
        this.version = version;
    }

    /**
     * @return the blocks
     */
    public byte[] getBlocks() {
        return blocks;
    }

    /**
     * @return the data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * @return the width
     */
    public short getWidth() {
        return width;
    }

    /**
     * @return the length
     */
    public short getLength() {
        return length;
    }

    /**
     * @return the height
     */
    public short getHeight() {
        return height;
    }

    public void pasteSchematic(Location loc) {
        short length = getLength();
        short width = getWidth();
        short height = getHeight();
        loc.subtract(width / 2, height / 2, length / 2); // Centers the schematic
        loc.getBlock().setType(Material.STONE, false);
        if (schematicVersion == SchematicVersion.v_1_8) {

            byte[] blocks = getBlocks();
            byte[] blockData = getData();

            //LoadBlocks
            for (int x = 0; x < width; ++x) {
                for (int y = 0; y < height; ++y) {
                    for (int z = 0; z < length; ++z) {
                        int index = y * width * length + z * width + x;
                        Block block = new Location(loc.getWorld(), x + loc.getX(), y + loc.getY(), z + loc.getZ()).getBlock();
                        block.setTypeIdAndData(blocks[index], blockData[index], true);
                    }
                }
            }
            //Tile Entities
            for (Tag tag : tileEntities) {
                if (!(tag instanceof CompoundTag))
                    continue;
                CompoundTag t = (CompoundTag) tag;
                Map<String, Tag> tags = t.getValue();

                int x = getChildTag(tags, "x", IntTag.class).getValue();
                int y = getChildTag(tags, "y", IntTag.class).getValue();
                int z = getChildTag(tags, "z", IntTag.class).getValue();

                String id = getChildTag(tags, "id", StringTag.class).getValue();
                if (id.equalsIgnoreCase("Chest")) {
                    List<Tag> items = getChildTag(tags, "Items", ListTag.class).getValue();
                    Block block = new Location(loc.getWorld(), x + loc.getX(), y + loc.getY(), z + loc.getZ()).getBlock();
                    if (block.getState() instanceof Chest) {
                        Chest chest = (Chest) block.getState();
                        for (Tag item : items) {
                            if (!(item instanceof CompoundTag))
                                continue;
                            Map<String, Tag> itemtag = ((CompoundTag) item).getValue();
                            byte slot = getChildTag(itemtag, "Slot", ByteTag.class).getValue();
                            String name = (getChildTag(itemtag, "id", StringTag.class).getValue()).toLowerCase().replace("minecraft:", "");
                            Byte amount = getChildTag(itemtag, "Count", ByteTag.class).getValue();
                            if (MultiversionMaterials.fromString(name.toUpperCase()) != null) {
                                Material material = MultiversionMaterials.fromString(name.toUpperCase()).parseMaterial();
                                if (material != null) {
                                    chest.getBlockInventory().setItem(slot, new ItemStack(material, amount));
                                }
                            }
                        }
                    }
                }

            }
        } else {
            //LoadBlocks
            if (NMSUtils.getVersionNumber() >= 113) {
                try {
                    Method createBlockData = Bukkit.getServer().getClass().getMethod("createBlockData", String.class);
                    Method setBlockData = Block.class.getMethod("setBlockData", Class.forName("org.bukkit.block.data.BlockData"), boolean.class);
                    for (int x = 0; x < width; ++x) {
                        for (int y = 0; y < height; ++y) {
                            for (int z = 0; z < length; ++z) {
                                int index = y * width * length + z * width + x;
                                Block block = new Location(loc.getWorld(), x + loc.getX(), y + loc.getY(), z + loc.getZ()).getBlock();
                                for (String s : palette.keySet()) {
                                    int i = getChildTag(palette, s, IntTag.class).getValue();
                                    if (blockdata[index] == i) {
                                        setBlockData.invoke(block, createBlockData.invoke(Bukkit.getServer(), s), false);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    EpicSkyblock.getInstance().sendErrorMessage(e);
                }
            }
        }
    }

    public static void debugSchematic(File file) throws IOException {
        FileInputStream stream = new FileInputStream(file);
        NBTInputStream nbtStream = new NBTInputStream(stream);

        CompoundTag schematicTag = (CompoundTag) nbtStream.readTag();
        stream.close();
        nbtStream.close();
        Map<String, Tag> schematic = schematicTag.getValue();

        for (String s : schematic.keySet()) {
            System.out.println(s + " - " + schematic.get(s));
        }
    }

    public static Schematic loadSchematic(File file) throws IOException {
        FileInputStream stream = new FileInputStream(file);
        NBTInputStream nbtStream = new NBTInputStream(stream);

        CompoundTag schematicTag = (CompoundTag) nbtStream.readTag();
        stream.close();
        nbtStream.close();
        Map<String, Tag> schematic = schematicTag.getValue();

        short width = getChildTag(schematic, "Width", ShortTag.class).getValue();
        short length = getChildTag(schematic, "Length", ShortTag.class).getValue();
        short height = getChildTag(schematic, "Height", ShortTag.class).getValue();
        if (!schematic.containsKey("Blocks")) {
            // 1.13 Schematic
            int version = getChildTag(schematic, "Version", IntTag.class).getValue();
            Map<String, Tag> palette = getChildTag(schematic, "Palette", CompoundTag.class).getValue();
            byte[] blockdata = getChildTag(schematic, "BlockData", ByteArrayTag.class).getValue();
            if (version == 1) {
                List<Tag> TileEntities = getChildTag(schematic, "TileEntities", ListTag.class).getValue();
                return new Schematic(width, length, height, TileEntities, blockdata, palette, version);
            } else {
                return new Schematic(width, length, height, blockdata, palette, version);
            }

        } else {

            List<Tag> TileEntities = getChildTag(schematic, "TileEntities", ListTag.class).getValue();
            String materials = getChildTag(schematic, "Materials", StringTag.class).getValue();
            if (!materials.equals("Alpha")) {
                throw new IllegalArgumentException("Schematic file is not an Alpha schematic");
            }

            byte[] blocks = getChildTag(schematic, "Blocks", ByteArrayTag.class).getValue();
            byte[] blockData = getChildTag(schematic, "Data", ByteArrayTag.class).getValue();
            List<Tag> entities = getChildTag(schematic, "Entities", ListTag.class).getValue();
            return new Schematic(width, length, height, TileEntities, blocks, blockData, entities);
        }
    }

    public static <T extends Tag> T getChildTag(Map<String, Tag> items, String key, Class<T> expected) throws IllegalArgumentException {
        if (!items.containsKey(key)) {
            throw new IllegalArgumentException("Schematic file is missing a \"" + key + "\" tag");
        }
        Tag tag = items.get(key);
        if (!expected.isInstance(tag)) {
            throw new IllegalArgumentException(key + " tag is not of tag type " + expected.getName());
        }
        return expected.cast(tag);
    }
}
