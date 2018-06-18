package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.ICodeGeneratingNode;

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
        // TODO: Add parameter to generateCode() so
        // TODO: it knows it's only invoked to calculate label addresses
        final BiConsumer<ASTNode, ASTNode.IterationCtx<Void>> visitor = (node,itCtx) ->
        {
            if ( node instanceof ICodeGeneratingNode)
            {
                ((ICodeGeneratingNode) node).generateCode(ctx);
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