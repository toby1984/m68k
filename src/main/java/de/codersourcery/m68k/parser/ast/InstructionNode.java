package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.arch.Field;
import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.InstructionType;
import de.codersourcery.m68k.parser.TextRegion;

public class InstructionNode extends ASTNode implements ICodeGeneratingNode
{

    private final InstructionType type;
    private OperandSize operandSize;

    public InstructionNode(InstructionType type, OperandSize operandSize, TextRegion region)
    {
        super(NodeType.MNEMONIC, region);
        if ( type == null ) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
        this.operandSize = operandSize;
    }

    public OperandNode source() {
        return (OperandNode) child(0);
    }

    public OperandNode destination() {
        return (OperandNode) child(1);
    }

    public OperandSize getOperandSize()
    {
        return operandSize;
    }

    public InstructionType getInstructionType()
    {
        return type;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Instruction [").append(type).append("]\n");
        for (ASTNode child : children() )
        {
            child.toString(buffer,depth+1);
        }
    }

    @Override
    public void generateCode(ICompilationContext ctx)
    {
        try
        {
            type.generateCode(this, ctx);
        }
        catch(Exception e)
        {
            ctx.error("Code generation failed: "+e.getMessage(), this,e);
        }
    }

    public int getValue(OperandNode operand)
    {
        final ASTNode astNode = operand.getValue();
        if ( astNode instanceof IValueNode) {
            return  ((IValueNode) astNode).getBits();
        }
        throw new IllegalArgumentException("Don't know how to extract value from "+astNode);
    }

    @Override
    public int getValueFor(Field field, ICompilationContext ctx)
    {
        switch(field)
        {
            case OP_CODE:
                return getInstructionType().getOperationCode(this);
            case SRC_VALUE:
                return getValue(source());
            case SRC_MODE:
                return source().addressingMode.bits;
            case DST_VALUE:
                return getValue(destination());
            case DST_MODE:
                return destination().addressingMode.bits;
            case SIZE:
                return operandSize.bits;
            case NONE:
                return 0;
            default:
                throw new RuntimeException("Internal error,unhandled field "+field);
        }
    }
}