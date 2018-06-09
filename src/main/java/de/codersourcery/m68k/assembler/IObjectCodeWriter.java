package de.codersourcery.m68k.assembler;

import java.io.IOException;

public interface IObjectCodeWriter extends AutoCloseable
{
    public void writeByte(int value);

    public void writeBytes(byte[] bytes);

    public void writeWord(int value);

    public void writeLong(int value);

    public int offset();

    public void reset();
}
