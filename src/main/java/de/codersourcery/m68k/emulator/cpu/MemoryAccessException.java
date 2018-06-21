package de.codersourcery.m68k.emulator.cpu;

public abstract class MemoryAccessException extends RuntimeException
{
    public final int offendingAddress;
    public enum Operation {
        READ_WORD(true),
        READ_LONG(true),
        WRITE_WORD(false),
        WRITE_LONG(false);

        public final boolean isRead;

        Operation(boolean isRead) {
            this.isRead =isRead;
        }
    }

    public final Operation operation;

    public MemoryAccessException(String message,Operation operation,int offendingAddress)
    {
        super(message);
        this.operation = operation;
        this.offendingAddress = offendingAddress;
    }
}
