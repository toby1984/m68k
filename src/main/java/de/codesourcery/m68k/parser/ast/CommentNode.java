package de.codesourcery.m68k.parser.ast;

import de.codesourcery.m68k.parser.TextRegion;

public class CommentNode extends ASTNode
{
    private final String value;

    public CommentNode(String value,TextRegion region)
    {
        super(NodeType.COMMENT, region);
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
        buffer.append(indent(depth)).append("Comment [").append(value).append("]\n");
    }
}
