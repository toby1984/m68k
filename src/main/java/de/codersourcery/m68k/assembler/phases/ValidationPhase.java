package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.assembler.Symbol;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.IValueNode;
import de.codersourcery.m68k.parser.ast.IdentifierNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.NumberNode;
import de.codersourcery.m68k.parser.ast.OperandNode;

import java.util.function.BiConsumer;

public class ValidationPhase implements ICompilationPhase
{
    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        final BiConsumer<ASTNode, ASTNode.IterationCtx<Void>> visitor = (node, itCtx) ->
        {
            if ( node instanceof IValueNode)
            {
                final Integer value = ((IValueNode) node).getBits(ctx);
                if ( value == null )
                {
                    if ( node instanceof IdentifierNode) {

                        final Symbol symbol = ctx.symbolTable().lookup(((IdentifierNode) node).getValue());
                        if ( symbol == null )
                        {
                            ctx.error("Failed to resolve " + symbol, node);
                        } else {
                            ctx.error("No value assigned to " + symbol, node);
                        }
                    }
                    else
                    {
                        ctx.error("Failed to determine value for " + node, node);
                    }
                }
            }

            if ( node.is(NodeType.INSTRUCTION) )
            {
                final InstructionNode insn = node.asInstruction();
                insn.getInstructionType().checkSupports(insn,ctx);
                if (insn.hasSource())
                {
                    checkValueSize(insn.source(), ctx);
                }
                if (insn.hasDestination())
                {
                    checkValueSize(insn.destination(), ctx);
                }
            }
        };
        ctx.getCompilationUnit().getAST().visitInOrder(visitor);
    }

    private void checkValueSize(OperandNode op,ICompilationContext ctx)
    {
        switch(op.addressingMode)
        {
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
            case PC_INDIRECT_WITH_DISPLACEMENT:
            case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
            case ABSOLUTE_SHORT_ADDRESSING:
                assertFitsIn16BitsSigned(op,ctx);
                break;
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
            case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                assertFitsIn8BitsSigned(op,ctx);
                break;
        }
    }

    private void assertFitsIn8BitsSigned(OperandNode op,ICompilationContext ctx)
    {
        assertFitsSigned(op,8,ctx);
    }

    private void assertFitsIn16BitsSigned(OperandNode op,ICompilationContext ctx) {
        assertFitsSigned(op,16,ctx);
    }

    private void assertFitsSigned(OperandNode op,int maxBits,ICompilationContext ctx)
    {
        Integer value = op.getBaseDisplacement().getBits(ctx);
        if ( value == null )
        {
            if ( op.getValue() == null || op.getValue().isRegister() )
            {
                return;
            }
            value = op.getValue().getBits(ctx);
        }
        if (value != null && NumberNode.getSizeInBitsSigned(value) > maxBits)
        {
            ctx.error("Value does not fit in "+maxBits+" bits: " + value, op);
        }
    }

    @Override
    public String toString()
    {
        return "Validation phase";
    }
}
