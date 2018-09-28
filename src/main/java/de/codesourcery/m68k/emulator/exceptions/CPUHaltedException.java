package de.codesourcery.m68k.emulator.exceptions;

/**
 * Thrown when the CPU enters the "halted" state.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CPUHaltedException extends RuntimeException
{
    public CPUHaltedException(String message) {
        super(message);
    }
}
