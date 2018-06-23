package de.codersourcery.m68k.emulator.cpu;

import javax.management.RuntimeMBeanException;

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
