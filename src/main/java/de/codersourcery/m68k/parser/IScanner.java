package de.codersourcery.m68k.parser;

public interface IScanner
{
    public boolean eof();

    public char peek();

    public char next();

    int offset();
}
