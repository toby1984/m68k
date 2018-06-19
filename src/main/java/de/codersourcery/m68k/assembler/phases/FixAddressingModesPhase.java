package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.NumberNode;
import de.codersourcery.m68k.parser.ast.OperandNode;

import static de.codersourcery.m68k.assembler.arch.AddressingMode.ABSOLUTE_LONG_ADDRESSING;
import static de.codersourcery.m68k.assembler.arch.AddressingMode.PC_INDIRECT_WITH_INDEX_DISPLACEMENT;

public class FixAddressingModesPhase implements ICompilationPhase
{
    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        ctx.getCompilationUnit().getAST().visitInOrder((node,itCtx) ->
        {
            if ( ! node.is(NodeType.OPERAND ) )
            {
                return;
            }

            final OperandNode op = (OperandNode) node;

            Integer value;
            int sizeInBits;

            switch( op.addressingMode )
            {
                case ABSOLUTE_LONG_ADDRESSING:
                    value = op.getValue().getBits(ctx );
                    sizeInBits = NumberNode.getSizeInBits( value );
                    break;
                case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    value = op.getBaseDisplacement().getBits(ctx );
                    sizeInBits = NumberNode.getSizeInBits( value );
                    break;
                default:
                    return;
            }

            switch(op.addressingMode)
            {
                case ABSOLUTE_LONG_ADDRESSING:
                    if ( sizeInBits <= 16 )
                    {
                        op.addressingMode = AddressingMode.ABSOLUTE_SHORT_ADDRESSING;
                    }
                    break;
                case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    if ( sizeInBits <= 8 )
                    {
                        op.addressingMode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                    }
                    break;
                case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    if ( sizeInBits <= 8 )
                    {
                        op.addressingMode = AddressingMode.PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                    }
                    break;
                default:
                    throw new RuntimeException("Unhandled switch/case: "+op.addressingMode);
            }
        });
    }
}
