package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;

public class RAMPage extends MemoryPage
{
    private final byte[] data;

    public RAMPage(int sizeInBytes)
    {
        this.data = new byte[sizeInBytes];
    }

    @Override
    public byte readByte(int offset)
    {
        return data[offset];
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {
        data[offset] = (byte) value;
    }
}
