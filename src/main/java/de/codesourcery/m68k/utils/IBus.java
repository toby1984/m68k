package de.codesourcery.m68k.utils;

/**
 * A memory bus whose state can be read.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface IBus
{
    /**
     * Returns the name of this bus.
     *
     * @return
     */
    String getName();

    /**
     * Returns names for each pin.
     *
     * A bus cannot have more than 32 pins (as this is the limit of an int value).
     *
     * pin0 = array element #0
     *
     * @return
     */
    String[] getPinNames();

    /**
     * Reads bus state as a bitmask.
     *
     * Bit 0 = pin0, bit 1 = pin1, etc.
     * @return
     */
    int readPins();
}