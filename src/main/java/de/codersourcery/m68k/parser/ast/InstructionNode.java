package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.parser.TextRegion;

public class InstructionNode extends ASTNode implements ICodeGeneratingNode
{
    public final Instruction instruction;

    public final boolean useImpliedOperandSize;

    // actual operand size of this operation, will be automatically
    // populated by code generation when size was not specified by the user
    private OperandSize operandSize;

    public InstructionNode(Instruction type, OperandSize operandSize, TextRegion region)
    {
        super(NodeType.INSTRUCTION, region);
        if ( operandSize == null ) {
            throw new IllegalArgumentException("Operand size must not be NULL");
        }
        if ( type == null ) {
            throw new IllegalArgumentException("instruction must not be null");
        }
        this.instruction = type;
        this.operandSize = operandSize;
        this.useImpliedOperandSize = operandSize == OperandSize.UNSPECIFIED;
    }

    public int operandCount() {
        return children().size();
    }

    /**
     * Sets implicit operand size if the user didn't explicitly specify one.
     *
     * @param size
     * @return <code>true</code> if the user did <b>NOT</b> specify an operand size
     * and the operand size passed to this function was accepted,otherwise <code>false</code>.
     */
    public boolean setImplicitOperandSize(OperandSize size)
    {
        if ( size == null ) {
            throw new IllegalArgumentException("Operand size must not be NULL");
        }

        if ( useImpliedOperandSize )
        {
            this.operandSize = size;
            return true;
        }
        return false;
    }

    public OperandNode source() {
        return (OperandNode) child(0);
    }

    public OperandNode destination() {
        return (OperandNode) child(1);
    }

    public boolean hasSource() {
        return childCount() > 0;
    }

    public boolean hasOperandSize(OperandSize size) {
        return getOperandSize() == size;
    }

    public boolean hasDestination() {
        return childCount() > 1;
    }

    public OperandSize getOperandSize()
    {
        return operandSize;
    }

    public Instruction getInstructionType()
    {
        return instruction;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Instruction [").append(instruction).append("]\n");
        for (IASTNode child : children() )
        {
            child.toString(buffer,depth+1);
        }
    }

    @Override
    public void generateCode(ICompilationContext ctx,boolean estimateSizeForUnknownOperands)
    {
        try
        {
            instruction.generateCode(this, ctx,estimateSizeForUnknownOperands);
        }
        catch(Exception e)
        {
            ctx.error("Code generation failed: "+e.getMessage(), this,e);
        }
    }

    public boolean hasExplicitOperandSize() {
        return ! useImpliedOperandSize;
    }
}