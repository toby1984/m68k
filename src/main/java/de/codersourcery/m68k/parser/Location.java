package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.assembler.CompilationUnit;
import org.apache.commons.lang3.Validate;

public class Location
{
    public final CompilationUnit unit;
    public final int lineNo;
    public final int columnNo;
    public final int offset;

    private Location(CompilationUnit unit, int lineNo, int columnNo, int offset)
    {
        Validate.notNull(unit, "unit must not be null");
        if ( lineNo < 1 ) {
            throw new IllegalArgumentException("Invalid line number "+lineNo);
        }
        if ( columnNo < 1 ) {
            throw new IllegalArgumentException("Invalid column number "+columnNo);
        }
        if ( offset < 0 ) {
            throw new IllegalArgumentException("Invalid offset "+offset);
        }
        this.unit = unit;
        this.lineNo = lineNo;
        this.columnNo = columnNo;
        this.offset = offset;
    }

    public static Location of(CompilationUnit unit, int lineNo, int columnNo, int offset) {
        return new Location(unit,lineNo,columnNo,offset);
    }
}
