package de.codersourcery.m68k.emulator.memory;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;

/**
 * Address range containing all custom-chip registers.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CustomChipPage extends MemoryPage
{
    private final int startAddress;

    public CustomChipPage(int startAddress) {
        this.startAddress = startAddress;
    }

    @Override
    public byte readByte(int offset)
    {
        return 0;
    }

    @Override
    public byte readByteNoSideEffects(int offset)
    {
        return 0;
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {

    }
}
