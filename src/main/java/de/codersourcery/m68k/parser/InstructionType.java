package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.OperandNode;

public enum InstructionType
{
    MOVE("move",2)
    {
        @Override
        public void generateCode(InstructionNode node, ICompilationContext context)
        {
            final OperandNode srcOp = node.source();
            final OperandNode dstOp = node.destination();

            final ASTNode src = srcOp.child(0);
            final ASTNode dst = dstOp.child(1);
            if ( ! src.is(NodeType.REGISTER) ) {
                context.error("Instruction "+this+" has unsupported source "+src,src);
                return;
            }
            if ( ! dst.is(NodeType.REGISTER) ) {
                context.error("Instruction "+this+" has unsupported destination "+dst,dst);
                return;
            }
            final Register srcReg = src.asRegister().register;
            final Register dstReg = dst.asRegister().register;

            final InstructionNode.OperandSize size = node.getOperandSize(InstructionNode.OperandSize.WORD);
            int word0 = size.bits() << 12 | dstReg.index << 8 | dstOp.mode.bits << 6 |
                                           srcOp.mode.bits << 3 | srcReg.index;
            context.getCodeWriter().writeWord(word0);
        }
    };

    private final String mnemonic;
    private final int operandCount;

    private InstructionType(String mnemonic,int operandCount)
    {
        this.mnemonic = mnemonic.toLowerCase();
        this.operandCount = operandCount;
    }

    public int getOperandCount()
    {
        return operandCount;
    }

    public String getMnemonic()
    {
        return mnemonic;
    }

    public static InstructionType getType(String value)
    {
        if ( value != null )
        {
            final String lValue = value.toLowerCase();
            for ( InstructionType t : InstructionType.values() ) {
                if ( t.mnemonic.equals( lValue ) ) {
                    return t;
                }
            }
        }
        return null;
    }

    public abstract void generateCode(InstructionNode node,ICompilationContext context);
}
