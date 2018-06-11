package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TextRegion;

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
    public int getBits(int inputBits, int outputBits)
    {
        if ( value == null ) {
            throw new IllegalStateException("No value");
        }
        int input;
        switch( inputBits)
        {
            case 32:
                input = (value.charAt(0) & 0xff)<<24 |
                        (value.charAt(0) & 0xff)<<16 |
                        (value.charAt(0) & 0xff)<< 8 |
                        (value.charAt(1) & 0xff);
                break;
            case 16:
                input = (value.charAt(0) & 0xff)<<8 |
                        (value.charAt(1) & 0xff);
                break;
            case  8:
                input = (value.charAt(0) & 0xff);
                break;
            default:
                throw new IllegalArgumentException("bit count must be 8,16 or 32 but was "+inputBits);
        }
        return signExtend(input,inputBits,outputBits);
    }
}