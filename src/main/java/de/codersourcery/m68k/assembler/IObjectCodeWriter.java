package de.codersourcery.m68k.assembler;

import java.io.IOException;

/**
 * Responsible for writing generated executable data.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface IObjectCodeWriter extends AutoCloseable
{
    /**
     * Writes a byte.
     *
     * @param value
     */
    public void writeByte(int value);

    /**
     * Writes all bytes from a byte array.
     *
     * @param bytes
     */
    public void writeBytes(byte[] bytes);

    /**
     * Writes a word (16 bits).
     *
     * @param value
     */
    public void writeWord(int value);

    /**
     * Writes a long word (32 bits).
     *
     * @param value
     */
    public void writeLong(int value);

    /**
     * Returns the current write offset (in bytes) for this writer.
     *
     * @return
     */
    public int offset();

    /**
     * Resets this writer to the initial settings and clears
     * any already written data.
     */
    public void reset();
}
