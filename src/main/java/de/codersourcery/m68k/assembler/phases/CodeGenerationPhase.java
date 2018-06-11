package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.ICodeGeneratingNode;

import java.util.function.BiConsumer;

public class CodeGenerationPhase implements ICompilationPhase
{
    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        final BiConsumer<ASTNode, ASTNode.IterationCtx<Void>> visitor = (node,itCtx) ->
        {
            if ( node instanceof ICodeGeneratingNode)
            {
                ((ICodeGeneratingNode) node).generateCode(ctx);
            }
        };
        ctx.getCompilationUnit().getAST().visitInOrder(visitor);
    }
}
