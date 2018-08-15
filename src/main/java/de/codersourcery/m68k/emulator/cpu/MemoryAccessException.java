package de.codersourcery.m68k.emulator.cpu;

public abstract class MemoryAccessException extends RuntimeException
{
    public final int offendingAddress;
    public final ViolationType violationType;

    public enum ViolationType {
        BAD_ALIGNMENT,
        WRITE_PROTECTED;
    }

    public enum Operation
    {
        READ_BYTE(true),
        WRITE_BYTE(true),
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

    public MemoryAccessException(String message,Operation operation,int offendingAddress,ViolationType violation)
    {
        super(message);
        this.operation = operation;
        this.offendingAddress = offendingAddress;
        this.violationType = violation;
    }
}
