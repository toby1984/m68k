package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;

import java.util.function.BiConsumer;

public class ValidationPhase implements ICompilationPhase
{
    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        final BiConsumer<ASTNode, ASTNode.IterationCtx<Void>> visitor = (node, itCtx) ->
        {
            if ( node.is(NodeType.INSTRUCTION) )
            {
                final InstructionNode insn = node.asInstruction();
                insn.getInstructionType().checkSupports(insn);
            }
        };
        ctx.getCompilationUnit().getAST().visitInOrder(visitor);
    }

    @Override
    public String toString()
    {
        return "Validation phase";
    }
}
