package de.codesourcery.m68k.parser.ast;

import de.codesourcery.m68k.parser.Label;
import de.codesourcery.m68k.parser.TextRegion;

public class LabelNode extends ASTNode
{
    private final Label value;

    public LabelNode(Label value, TextRegion region)
    {
        super(NodeType.LABEL, region);
        if ( value == null ) {
            throw new IllegalArgumentException("value must not be null");
        }
        this.value = value;
    }

    public Label getValue()
    {
        return value;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Label [").append(value).append("]\n");
    }
}