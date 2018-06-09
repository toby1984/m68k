package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TextRegion;

public class StringNode extends ASTNode
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
}