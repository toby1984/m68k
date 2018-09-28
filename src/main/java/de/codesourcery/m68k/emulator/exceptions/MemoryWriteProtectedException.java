package de.codesourcery.m68k.emulator.exceptions;

public class MemoryWriteProtectedException extends MemoryAccessException
{
    public MemoryWriteProtectedException(String message, Operation operation, int offendingAddress)
    {
        super(message, operation, offendingAddress, ViolationType.WRITE_PROTECTED);
    }
}
