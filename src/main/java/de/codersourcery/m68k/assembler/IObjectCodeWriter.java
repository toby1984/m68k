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
     * Allocate (skip) the given number of bytes.
     *
     * @param count
     */
    public void allocateBytes(int count);

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
     * Sets the start address where the first byte of data should be stored.
     *
     * Calling this method more than once with different values or
     * calling it after bytes have been {@link #allocateBytes(int) allocated}
     * or {@link #writeBytes(byte[]) written} will throw an {@link IllegalStateException}.
     * @param address
     * @throws IllegalStateException
     */
    public void setStartOffset(int address) throws IllegalStateException;

    /**
     * Returns the start offset for this object writer.
     *
     * @return
     */
    public int getStartOffset();

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
