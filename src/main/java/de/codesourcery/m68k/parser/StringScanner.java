package de.codesourcery.m68k.parser;

import org.apache.commons.lang3.Validate;

public class StringScanner implements IScanner
{
    private final String input;
    private int ptr;

    public StringScanner(String input)
    {
        Validate.notNull(input, "input must not be null");
        this.input = input;
    }

    @Override
    public boolean eof()
    {
        return ptr == input.length();
    }

    @Override
    public char peek()
    {
        return input.charAt(ptr);
    }

    @Override
    public char next()
    {
        final char c = input.charAt(ptr);
        ptr++;
        return c;
    }

    @Override
    public int offset()
    {
        return ptr;
    }

    @Override
    public void setOffset(int offset)
    {
        if ( offset < 0 || offset > input.length() ) {
            throw new IllegalArgumentException("Offset "+offset+" is out-of-range 0-"+input.length());
        }
        this.ptr = offset;
    }
}