package de.codersourcery.m68k.parser;

import java.util.Objects;

public class Label
{
    public final Identifier identifier;

    public Label(Identifier identifier)
    {
        if ( identifier == null ) {
            throw new IllegalArgumentException("id must not be NULL");
        }
        this.identifier = identifier;
    }

    @Override
    public String toString()
    {
        return identifier.getValue();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Label)
        {
            final Label label = (Label) o;
            return this.identifier.equals(label.identifier);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return identifier.hashCode();
    }
}
