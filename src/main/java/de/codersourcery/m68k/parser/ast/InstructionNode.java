package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.InstructionType;
import de.codersourcery.m68k.parser.TextRegion;
import javafx.css.Size;

public class InstructionNode extends ASTNode implements ICodeGeneratingNode
{
    public enum OperandSize {
        BYTE(0b00),
        WORD(0b01),
        LONG(0b10),
        DEFAULT(0b11);

        private int bits;

        private OperandSize(int bits) {
            this.bits = bits;
        }
        public int bits() {
            if ( this == DEFAULT ) {
                throw new IllegalStateException("DEFAULT operand size has no encoding");
            }
            return bits;
        }
    }

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

    public OperandSize getOperandSize(OperandSize defaultValue)
    {
        return operandSize == OperandSize.DEFAULT ? defaultValue : operandSize;
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
}