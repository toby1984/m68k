package de.codesourcery.m68k.emulator.exceptions;

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
