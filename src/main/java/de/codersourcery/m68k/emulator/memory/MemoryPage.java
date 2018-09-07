package de.codersourcery.m68k.emulator.memory;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;

/**
 * Abstract base-class for all memory pages.
 *
 * The only state hold by this class are this pages permission bits.
 *
 * @author tobias.gierke@code-sourcery.de
 * @see MMU
 */
public abstract class MemoryPage
{
    /**
     * Memory page permission bit: Write protected.
     */
    public static final byte FLAG_WRITE_PROTECTED = 1<<0;

    public byte flags;

    /**
     * Returns whether writes to this page are permitted.
     *
     * @return
     */
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

    public short readWord(int offset) {
        int hi = readByte(offset);
        int lo = readByte(offset+1);
        return (short) ( ( hi<< 8) | (lo & 0xff) );
    }

    /**
     * Reads a byte from this page without triggering any side-effects.
     *
     * This method is used to inspect the state of the emulation without
     * actually changing it.
     *
     * @param offset offset inside this page
     * @return
     */
    public abstract byte readByteNoSideEffects(int offset);

    /**
     * Write a byte to this page.
     *
     * @param offset offset inside this page
     * @param value byte to write (only lower 8 bits are being used)
     */
    public abstract void writeByte(int offset,int value) throws MemoryAccessException;

    /**
     * Write a word to this page.
     *
     * @param offset offset inside this page. If offset+1 crosses the page boundary an exception will be thrown.
     * @param value word to write (only lower 16 bits are being used)
     */
    public void writeWord(int offset,int value) throws MemoryAccessException
    {
        writeByte(offset,value>> 8); // hi
        writeByte(offset+1,value); // lo
    }
}