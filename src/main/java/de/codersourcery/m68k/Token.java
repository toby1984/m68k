package de.codersourcery.m68k;

import org.apache.commons.lang3.Validate;

public class Token
{
    public Token(TokenType type,String value, int offset)
    {
        Validate.notNull(type, "type must not be null");
        Validate.notNull(value, "value must not be null");
        if ( offset < 0 ) {
            throw new IllegalArgumentException("Offset must be >= 0");
        }
        this.value = value;
        this.offset = offset;
        this.type = type;
    }

    public enum TokenType {
        TEXT,
        WHITESPACE;
    }

    public final String value;
    public final int offset;
    public final TokenType type;


}
