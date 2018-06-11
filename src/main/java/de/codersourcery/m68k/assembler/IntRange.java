package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.assembler.arch.Field;
import org.apache.commons.lang3.Validate;

public final class IntRange implements Comparable<IntRange>
{
    public final Field field;

    /** Start index (inclusive) */
    public int start;
    public int end;

    public IntRange(Field field, int start, int end)
    {
        Validate.notNull(field, "field must not be null");
        if ( end <= start ) {
            throw new IllegalArgumentException("End ("+end+") needs to be greater than start ("+start+")");
        }
        this.start = start;
        this.end = end;
        this.field = field;
    }

    @Override
    public String toString()
    {
        return "IntRange{" +
                "field=" + field +
                ", start=" + start +
                ", end=" + end +
                '}';
    }

    public boolean contains(int i) {
        return start <= i && i < end;
    }

    public int compareTo(IntRange other) {
        return this.start - other.start;
    }

    public int length()
    {
        return end-start;
    }
}
