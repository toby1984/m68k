package de.codersourcery.m68k.emulator.cpu;

public abstract class MemoryPage
{
    public static final byte FLAG_WRITE_PROTECTED = 1<<0;

    public byte flags;

    public final boolean isWriteable() {
        return (flags & FLAG_WRITE_PROTECTED) == 0;
    }

    /**
     * Reads a byte from this page.
     *
     * @param offset offset inside this page
     * @return
     */
    public abstract byte readByte(int offset);

    /**
     * Write a byte to this page.
     *
     * @param offset offset inside this page
     * @param value byte to write (only lower 8 bits are being used)
     */
    public abstract void writeByte(int offset,int value) throws MemoryAccessException;
}