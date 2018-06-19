package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.assembler.Symbol;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.DirectiveNode;
import de.codersourcery.m68k.parser.ast.ICodeGeneratingNode;
import de.codersourcery.m68k.parser.ast.LabelNode;
import de.codersourcery.m68k.parser.ast.StatementNode;

import java.util.List;
import java.util.function.BiConsumer;

public class CodeGenerationPhase implements ICompilationPhase
{
    public boolean isFirstPass;

    public CodeGenerationPhase(boolean isFirstPass) {
        this.isFirstPass = isFirstPass;
    }

    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        ctx.getCodeWriter().reset();

        final BiConsumer<ASTNode, ASTNode.IterationCtx<Void>> visitor = (node,itCtx) ->
        {
            if ( node instanceof DirectiveNode )
            {
                final DirectiveNode dn = (DirectiveNode) node;
                if ( dn.directive == DirectiveNode.Directive.ORG)
                {
                    ctx.getCodeWriter().setStartOffset( dn.getOrigin() );
                }
            }
            if ( node instanceof StatementNode)
            {
                final StatementNode stmt = (StatementNode) node;
                for ( var label : stmt.getLabels() )
                {
                    final Symbol symbol = ctx.symbolTable().lookup(label.getValue().identifier);
                    symbol.setValue( ctx.getCodeWriter().offset() );
                }
            }
            if ( node instanceof ICodeGeneratingNode)
            {
                ((ICodeGeneratingNode) node).generateCode(ctx,isFirstPass);
            }
        };
        ctx.getCompilationUnit().getAST().visitInOrder(visitor);
    }

    @Override
    public String toString()
    {
        return "code generation";
    }
}