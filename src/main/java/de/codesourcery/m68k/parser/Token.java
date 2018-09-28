package de.codesourcery.m68k.parser;

import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.Objects;

public class Token
{
    public final String value;
    public final int offset;
    public final TokenType type;

    public Token(TokenType type,String value, int offset)
    {
        Validate.notNull(type, "type must not be null");
        if ( offset < 0 ) {
            throw new IllegalArgumentException("Offset must be >= 0");
        }
        this.value = value;
        this.offset = offset;
        this.type = type;
    }

    public boolean is(TokenType t)
    {
        Validate.notNull(t, "t must not be null");
        return t == this.type;
    }

    public int length() {
        return value != null ? value.length() : 0;
    }

    public TextRegion getRegion()
    {
        return new TextRegion(this.offset,length());
    }

    public boolean isEOF() {
        return type == TokenType.EOF;
    }

    public boolean isEOL()
    {
        return type == TokenType.EOF;
    }

    @Override
    public String toString()
    {
        return "Token[" +type+"] = "+value+" ("+offset+")";
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Token)
        {
            final Token token = (Token) o;
            return offset == token.offset && Objects.equals(value, token.value) && type == token.type;
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value, offset, type);
    }

    public static TextRegion getMergedRegion(Collection<Token> tokens)
    {
        TextRegion result = null;
        if ( tokens != null )
        {
            for ( Token tok : tokens )
            {
                if ( tok != null ) {
                    if ( result == null ) {
                        result = tok.getRegion();
                    } else {
                        result.merge(tok.getRegion() );
                    }
                }
            }
        }
        return result;
    }

    public static TextRegion getMergedRegion(Token...tokens)
    {
        TextRegion result = null;
        if ( tokens != null ) {
            for ( Token tok : tokens ) {
                if ( tok != null ) {
                    if ( result == null ) {
                        result = tok.getRegion();
                    } else {
                        result.merge(tok.getRegion() );
                    }
                }
            }
        }
        return result;
    }
}
