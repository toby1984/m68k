package de.codersourcery.m68k.assembler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ObjectCodeWriter implements IObjectCodeWriter
{
    private ByteArrayOutputStream out = new ByteArrayOutputStream();

    private Integer startOffset;
    private int offset = 0;

    @Override
    public void setStartOffset(int address) throws IllegalStateException
    {
        if ( this.startOffset != null ) {
            if ( this.startOffset.intValue() != address ) {
                throw new IllegalStateException("Start offset already set to "+this.startOffset);
            }
            return;
        }
        if ( offset != 0 ) {
            throw new IllegalStateException("Can't set start offset after bytes have been written");
        }
        this.startOffset = address;
        this.offset = address;
    }

    @Override
    public void writeBytes(byte[] bytes)
    {
        try
        {
            out.write(bytes);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        offset+=bytes.length;
    }

    @Override
    public void writeByte(int value)
    {
        out.write(value);
        offset++;
    }

    @Override
    public void allocateBytes(int count)
    {
        offset += count;
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
    public int getStartOffset()
    {
        return startOffset == null ? 0 : startOffset;
    }

    @Override
    public int offset()
    {
        return offset;
    }

    @Override
    public void reset()
    {
        out = new ByteArrayOutputStream();
        offset = 0;
        startOffset = null;
    }

    @Override
    public void close() throws Exception
    {
    }

    public byte[] getBytes() {
        return out.toByteArray();
    }
}
