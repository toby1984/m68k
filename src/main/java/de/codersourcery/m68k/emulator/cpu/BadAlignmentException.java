package de.codersourcery.m68k.emulator.cpu;

import de.codersourcery.m68k.utils.Misc;

/**
 * Thrown when word or long accesses are attempted on an odd memory address.
 * @author tobias.gierke@code-sourcery.de
 */
public class BadAlignmentException extends MemoryAccessException
{
    public final Integer pc;

    public BadAlignmentException(Operation operation,int offendingAddress) {
        super("Misaligned "+operation+" access,offending address: "+Misc.hex(offendingAddress),operation,offendingAddress);
        this.pc=null;
    }

    public BadAlignmentException(String message,Operation operation,int pc,int offendingAddress) {
        super(message,operation,offendingAddress);
        this.pc=pc;
    }
}
