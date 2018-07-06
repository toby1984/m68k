package de.codersourcery.m68k.assembler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjectCodeWriter implements IObjectCodeWriter
{
    private final int initialBufferSize;
    private final List<Buffer> buffers = new ArrayList<>();

    public ObjectCodeWriter() {
        this(1024);
    }

    public ObjectCodeWriter(int initialBufferSize)
    {
        if ( initialBufferSize < 1 ) {
            throw new IllegalArgumentException( "Initial buffer size must be >= 1" );
        }
        this.initialBufferSize = initialBufferSize;
        reset();
    }

    private Buffer currentBuffer()
    {
        return buffers.get( buffers.size()-1 );
    }

    @Override
    public void setOffset(int address) throws IllegalStateException
    {
        if ( currentBuffer().isEmpty() ) {
            buffers.remove( currentBuffer() );
        }
        else {
            if ( address < offset() ) {
                throw new IllegalStateException("Offset must not be smaller than current offset");
            }
        }
        buffers.add( new Buffer(address,initialBufferSize) );
    }

    @Override
    public void writeBytes(byte[] bytes)
    {
        for ( byte b : bytes )
        {
            currentBuffer().writeByte( b );
        }
    }

    @Override
    public void writeByte(int value)
    {
        currentBuffer().writeByte((byte) value);
    }

    @Override
    public void allocateBytes(int count)
    {
        currentBuffer().allocate( count );
    }

    @Override
    public void writeWord(int value)
    {
        writeByte( value >> 8 ); // big endian
        writeByte( value );
    }

    @Override
    public void writeLong(int value)
    {
        writeWord( (short) (value >> 16) ); // big endian
        writeWord( (short) value );
    }

    @Override
    public int offset()
    {
        return currentBuffer().startOffset + currentBuffer().size();
    }

    @Override
    public void reset()
    {
        buffers.clear();
        buffers.add( new Buffer(0, initialBufferSize ) );
    }

    public List<Buffer> getBuffers() {
        return buffers;
    }

    @Override
    public void close() throws Exception
    {
    }

    /**
     * Returns the data written to this object code writer.
     *
     * @param padUpToStartOffset whether to zero-pad the beginning of the result if
     *                           the start address of the first {@link IObjectCodeWriter.Buffer}
     *                           is greater than zero.
     * @return
     * @deprecated Useful for unit testing only, real code will most likely want to be aware
     * of different offsets being used and thus should use {@link #getBuffers()} instead.
     */
    @Deprecated
    public byte[] getBytes(boolean padUpToStartOffset)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ptr = 0;
        for ( Buffer buf : buffers)
        {
            if ( ! buf.isEmpty() )
            {
                if ( ptr != buf.startOffset )
                {
                    if ( ptr != 0 || padUpToStartOffset )
                    {
                        int delta = buf.startOffset - ptr;
                        ptr += delta;
                        for (; delta > 0; delta--)
                        {
                            out.write( 0 );
                        }
                    }
                }
                try
                {
                    buf.appendTo(out);
                }
                catch (IOException e)
                {
                    // cannot happen
                }
                ptr += buf.size();
            }
        }
        return out.toByteArray();
    }
}