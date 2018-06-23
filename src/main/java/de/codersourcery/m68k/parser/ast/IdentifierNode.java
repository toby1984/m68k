package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.Symbol;
import de.codersourcery.m68k.parser.Identifier;
import de.codersourcery.m68k.parser.TextRegion;

public class IdentifierNode extends ASTNode implements IValueNode
{
    public final Identifier value;

    public IdentifierNode(Identifier value,TextRegion region)
    {
        super(NodeType.IDENTIFIER, region);
        if ( value == null ) {
            throw new IllegalArgumentException("value must not be null");
        }
        this.value = value;
    }

    public Identifier getValue()
    {
        return value;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Identifier [").append(value).append("]\n");
    }

    @Override
    public Integer getBits(ICompilationContext ctx)
    {
        final Symbol symbol = ctx.symbolTable().lookup(value);
        return symbol == null ? null : symbol.getBits();
    }
}