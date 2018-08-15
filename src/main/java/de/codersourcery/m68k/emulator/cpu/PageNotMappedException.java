package de.codersourcery.m68k.emulator.cpu;

public class PageNotMappedException extends MemoryAccessException
{
    public PageNotMappedException(String message,
                                  Operation operation,
                                  int offendingAddress,
                                  ViolationType violation)
    {
        super( message, operation, offendingAddress, violation );
    }
}
