package de.codersourcery.m68k.assembler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ObjectCodeWriter implements IObjectCodeWriter
{
    private ByteArrayOutputStream out = new ByteArrayOutputStream();

    private int offset = 0;

    @Override
    public void writeByte(int value)
    {
        out.write(value);
    }

    @Override
    public void writeBytes(byte[] bytes) throws IOException
    {
        out.write(bytes);
        offset++;
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
        return offset;
    }

    @Override
    public void reset()
    {
        out = new ByteArrayOutputStream();
        offset = 0;
    }

    @Override
    public void close() throws Exception
    {
    }

    public byte[] getBytes() {
        return out.toByteArray();
    }
}
