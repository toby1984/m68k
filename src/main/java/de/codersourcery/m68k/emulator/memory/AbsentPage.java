package de.codersourcery.m68k.emulator.memory;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;

/**
 * Address range (page) that is not backed by anything (no RAM,no ROM,no I/O registers, just nothing).
 * @author tobias.gierke@code-sourcery.de
 */
public class AbsentPage extends MemoryPage
{
    public static final AbsentPage SINGLETON = new AbsentPage();

    private AbsentPage() {
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
