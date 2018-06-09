package de.codersourcery.m68k.parser;

public class ParseException extends RuntimeException
{
    public final int offset;

    public ParseException(String message,int offset) {
        super(message+" (at "+offset+")");
        this.offset = offset;
    }
}
