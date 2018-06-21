package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.arch.Field;
import de.codersourcery.m68k.parser.TextRegion;

public class DirectiveNode extends ASTNode implements ICodeGeneratingNode
{
    public static enum Directive
    {
        ORG;
    }

    public final Directive directive;

    @Override
    public void generateCode(ICompilationContext ctx,boolean estimateSizeForUnknownOperands)
    {
        if ( directive == Directive.ORG )
        {
            ctx.getCodeWriter().setStartOffset( getOrigin() );
        }
    }

    public void setOrigin(NumberNode child)
    {
        removeAllChildren();
        add(child);
    }

    public int getOrigin() {
        if ( directive != Directive.ORG )
        {
            throw new IllegalStateException("Should not have been called, directive is not ORG but "+directive);
        }

        return (int) ((NumberNode) child(0)).getValue();
    }

    @Override
    public int getValueFor(Field field, ICompilationContext ctx)
    {
        // TODO: Implement me
        throw new UnsupportedOperationException("Method getValueFor not implemented");
    }

    public DirectiveNode(Directive directive,TextRegion region)
    {
        super(NodeType.DIRECTIVE,region);
        this.directive = directive;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("directive { "+directive+" }");
    }
}
