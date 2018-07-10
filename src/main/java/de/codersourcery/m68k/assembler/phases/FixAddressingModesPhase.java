package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.NumberNode;
import de.codersourcery.m68k.parser.ast.OperandNode;

import static de.codersourcery.m68k.assembler.arch.AddressingMode.ABSOLUTE_LONG_ADDRESSING;
import static de.codersourcery.m68k.assembler.arch.AddressingMode.PC_INDIRECT_WITH_INDEX_DISPLACEMENT;

/**
 * Responsible for picking the actual addressing mode to use for operands
 * after all symbols have been resolved.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class FixAddressingModesPhase implements ICompilationPhase
{
    private static final String PARAM_MODES_CHANGED = "modes_changed";

    public static boolean isAddressingModesUpdated(ICompilationContext ctx) {
        return (Boolean) ctx.getBlackboard(FixAddressingModesPhase.class).getOrDefault(PARAM_MODES_CHANGED, Boolean.FALSE);
    }

    private void addressModesChanged(boolean yesNo,ICompilationContext ctx)
    {
        if ( ctx.isDebugModeEnabled() ) {
            System.out.println("===> Addressing mode changed: "+yesNo);
        }
        ctx.getBlackboard(FixAddressingModesPhase.class).put(PARAM_MODES_CHANGED,yesNo);
    }

    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        final boolean[] modesChanged = {false};

        ctx.getCompilationUnit().getAST().visitInOrder((node,itCtx) ->
        {
            if ( ! node.is(NodeType.OPERAND ) )
            {
                return;
            }

            final OperandNode op = (OperandNode) node;

            Integer value;
            int sizeInBits;

            if ( ctx.isDebugModeEnabled() ) {
                System.out.println("Checking address mode: "+op.addressingMode);
            }
            switch( op.addressingMode )
            {
                case ABSOLUTE_LONG_ADDRESSING:
                    value = op.getValue().getBits(ctx );
                    sizeInBits = NumberNode.getSizeInBitsUnsigned( value );
                    break;
                case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    value = op.getBaseDisplacement().getBits(ctx );
                    sizeInBits = NumberNode.getSizeInBitsSigned( value );
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
                        if ( ctx.isDebugModeEnabled() ) {
                            System.out.println("Changing to ABSOLUTE_SHORT_ADDRESSING");
                        }
                        modesChanged[0] = true;
                    }
                    break;
                case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    if ( sizeInBits <= 8 )
                    {
                        op.addressingMode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                        modesChanged[0] = true;
                    }
                    break;
                case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    if ( sizeInBits <= 8 )
                    {
                        op.addressingMode = AddressingMode.PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                        modesChanged[0] = true;
                    }
                    break;
                default:
                    throw new RuntimeException("Unhandled switch/case: "+op.addressingMode);
            }
        });

        addressModesChanged(modesChanged[0],ctx);
    }
}
