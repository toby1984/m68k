package de.codesourcery.m68k.parser.ast;

import de.codesourcery.m68k.assembler.ICompilationContext;
import de.codesourcery.m68k.parser.TextRegion;

public class StringNode extends ASTNode implements IValueNode
{
    private final String value;

    public StringNode(String value,TextRegion region)
    {
        super(NodeType.STRING, region);
        if ( value == null ) {
            throw new IllegalArgumentException("value must not be null");
        }
        this.value = value;
    }

    public String getValue()
    {
        return value;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("String [").append(value).append("]\n");
    }

    @Override
    public Integer getBits(ICompilationContext context)
    {
        if ( value == null ) {
            throw new IllegalStateException("No value");
        }
        final int resultBits = Math.max(value.length()*8,32);
        switch( resultBits)
        {
            case 32:
                return (value.charAt(0) & 0xff)<<24 |
                        (value.charAt(1) & 0xff)<<16 |
                        (value.charAt(2) & 0xff)<< 8 |
                        (value.charAt(3) & 0xff);
            case 16:
                return (value.charAt(0) & 0xff)<<8 |
                        (value.charAt(1) & 0xff);
            case  8:
                return (value.charAt(0) & 0xff);
        }
        throw new IllegalArgumentException("bit count must be 8,16 or 32 but was "+resultBits);
    }
}