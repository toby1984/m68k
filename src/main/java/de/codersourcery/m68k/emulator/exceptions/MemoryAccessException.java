package de.codersourcery.m68k.emulator.exceptions;

public abstract class MemoryAccessException extends RuntimeException
{
    public int offendingAddress;
    public ViolationType violationType;

    public enum ViolationType {
        BAD_ALIGNMENT,
        WRITE_PROTECTED, PAGE_FAULT;
    }

    public enum Operation
    {
        UNSPECIFIED(true), // used during double page-fault
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

    public MemoryAccessException(String message,Operation operation,ViolationType type) {
        this(message,operation,0,type);
    }
}
