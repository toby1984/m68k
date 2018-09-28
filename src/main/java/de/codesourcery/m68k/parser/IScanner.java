package de.codesourcery.m68k.parser;

public interface IScanner
{
    public boolean eof();

    public char peek();

    public char next();

    int offset();

    public void setOffset(int offset);
}
