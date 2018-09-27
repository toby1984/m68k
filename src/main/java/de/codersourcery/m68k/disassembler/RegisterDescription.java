package de.codersourcery.m68k.disassembler;

import java.util.HashSet;
import java.util.Set;

public final class RegisterDescription
{
    public static final int FLAG_DMA_ONLY = 1<<0; // Register used by DMA channel only.
    public static final int FLAG_DMA_MOSTLY = 1<<1; // Register used by DMA channel usually, processors sometimes.
    public static final int FLAG_REGISTER_PAIR = 1<<2; // Address register pair.  Must be an even address pointing to chip memory.
    public static final int FLAG_COPPER_NOT_WRITEABLE    = 1<<3; // Address not writable by the Copper.
    public static final int FLAG_COPPER_WRITE_PROTECTED = 1<<4; //  Address not writable by the Copper unless the "copper danger bit",  COPCON  is set true.
    public static final int FLAG_WRITE_ONLY = 1<<5;
    public static final int FLAG_READ_ONLY = 1<<6;
    public static final int FLAG_STROBE = 1<<7;
    /*
     * This is a DMA data transfer to RAM, from either the disk or the blitter.
     * RAM timing requires data to be on the bus earlier than microprocessor read cycles.
     * These transfers are therefore initiated by Agnus timing, rather than a read address
     * on the destination address bus.
     */
    public static final int FLAG_EARLY_READ = 1<<8;

    public enum RegisterFlag
    {
        DMA_ONLY(RegisterDescription.FLAG_DMA_ONLY),
        DMA_MOSTLY(RegisterDescription.FLAG_DMA_MOSTLY),
        REGISTER_PAIR(RegisterDescription.FLAG_REGISTER_PAIR),
        COPPER_NOT_WRITEABLE(RegisterDescription.FLAG_COPPER_NOT_WRITEABLE),
        COPPER_WRITE_PROTECTED(RegisterDescription.FLAG_COPPER_WRITE_PROTECTED),
        WRITE_ONLY(RegisterDescription.FLAG_WRITE_ONLY),
        READ_ONLY(RegisterDescription.FLAG_READ_ONLY),
        STROBE(RegisterDescription.FLAG_STROBE);

        public final int bitmask;

        RegisterFlag(int bitmask)
        {
            this.bitmask = bitmask;
        }
    }

    public final int address;
    public final String name;
    public final String description;
    public final int flags;

    public RegisterDescription(int address, String name, int flags,String description)
    {
        this.address = address;
        this.name = name.toUpperCase();
        this.description = description;
        this.flags = flags;
    }

    public boolean hasFlag(RegisterFlag flag)
    {
        return ( this.flags & flag.bitmask) != 0;
    }

    public Set<RegisterFlag> getFlags()
    {
        final Set<RegisterFlag> result = new HashSet<>( Integer.bitCount(this.flags ) );
        final RegisterFlag[] values = RegisterFlag.values();
        for (int i = 0, valuesLength = values.length; i < valuesLength; i++)
        {
            RegisterFlag f = values[i];
            if (hasFlag(f))
            {
                result.add(f);
            }
        }
        return result;
    }

    @Override
    public String toString()
    {
        return "RegisterDescription{" +
            "address=" + address +
            ", name='" + name + '\'' +
            ", flags=" + getFlags() +
            ", description='" + description + '\'' +
            '}';
    }
}
