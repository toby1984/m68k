package de.codersourcery.m68k.emulator.cpu;

public class MemoryWriteProtectedException extends MemoryAccessException
{
    public MemoryWriteProtectedException(String message, Operation operation, int offendingAddress)
    {
        super(message, operation, offendingAddress, ViolationType.WRITE_PROTECTED);
    }
}
