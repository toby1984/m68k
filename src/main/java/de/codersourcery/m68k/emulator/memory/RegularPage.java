package de.codersourcery.m68k.emulator.memory;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;

/**
 * A regular memory page (either RAM or ROM).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class RegularPage extends MemoryPage
{
    private final byte[] data;

    public RegularPage(int sizeInBytes)
    {
        this.data = new byte[sizeInBytes];
    }

    @Override
    public byte readByte(int offset)
    {
        return data[offset];
    }

    @Override
    public byte readByteNoSideEffects(int offset)
    {
        return data[offset];
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {
        data[offset] = (byte) value;
    }
}
