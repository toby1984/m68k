package de.codesourcery.m68k.assembler;

import de.codesourcery.m68k.assembler.arch.Field;
import org.apache.commons.lang3.Validate;

/**
 * Instruction-encoding helper class: A comparable range of two integer numbers (start,end) associated with a {@link Field}.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class IntRange implements Comparable<IntRange>
{
    public final Field field;

    /** Start index (inclusive) */
    public int start;
    /** End index (exclusive) */
    public int end;

    /**
     * Create instance.
     *
     * @param field Field associated with this int range.
     * @param start start (inclusive)
     * @param end end (exclusive)
     */
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
