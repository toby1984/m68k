package de.codersourcery.m68k.assembler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Responsible for writing generated executable data.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface IObjectCodeWriter extends AutoCloseable
{
    public static final class Buffer
    {
        public final int startOffset;
        private byte[] data;
        private int ptr;

        public Buffer(int startOffset,int initialBufferSize)
        {
            if ( initialBufferSize < 1 ) {
                throw new IllegalArgumentException( "Initial buffer size must be >= 1" );
            }
            data = new byte[initialBufferSize];
            this.startOffset = startOffset;
        }

        public void appendTo(OutputStream out) throws IOException
        {
            if ( ! isEmpty() )
            {
                out.write(data,0,ptr);
            }
        }

        public boolean isEmpty() {
            return ptr==0;
        }

        public int size() {
            return ptr;
        }

        public void allocate(int bytesRequired)
        {
            int available = data.length-ptr;
            if ( bytesRequired >= available )
            {
                growBuffer( 1+bytesRequired-available );
            }
            ptr += bytesRequired;
        }

        private void growBuffer(int increment) {
            byte[] newData = new byte[data.length+increment];
            System.arraycopy( this.data,0,newData,0,data.length );
            data = newData;
        }

        public void writeByte(byte b)
        {
            if ( ptr == data.length ) {
                growBuffer(data.length);
            }
            data[ptr++] = b;
        }
    }

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
     * Sets the offset/address where to write the next byte.
     *
     * @param address
     * @throws IllegalStateException when trying to move the offset backwards
     */
    public void setOffset(int address) throws IllegalStateException;

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

    /**
     * Returns all data written to this object code writer.
     * @return
     */
    public List<Buffer> getBuffers();
}