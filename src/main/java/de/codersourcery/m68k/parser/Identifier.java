package de.codersourcery.m68k.parser;

import java.util.Objects;
import java.util.regex.Pattern;

public class Identifier
{
    private static final Pattern PATTERN = Pattern.compile("^[_a-zA-Z]+[_0-9a-zA-Z]*$");
    private final String value;

    public Identifier(String value)
    {
        if ( ! isValid(value) ) {
            throw new IllegalArgumentException("Not a valid identifier: "+value);
        }
        this.value = value;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Identifier)
        {
            final Identifier that = (Identifier) o;
            return this.value.equals(that.value);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return value.hashCode();
    }

    public static boolean isValid(String s)
    {
        return s != null && PATTERN.matcher(s).matches();
    }

    @Override
    public String toString()
    {
        return value;
    }

    public String getValue()
    {
        return value;
    }
}
