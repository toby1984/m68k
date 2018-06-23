package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.arch.Field;
import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.assembler.arch.Register;
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

    public void setImplicitOperandSize(OperandSize size)
    {
        if ( size == null ) {
            throw new IllegalArgumentException("Operand size must not be NULL");
        }

        if ( useImpliedOperandSize )
        {
            this.operandSize = size;
        }
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
        for (ASTNode child : children() )
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

    private int getIndexRegisterSizeBit(RegisterNode register)
    {
        OperandSize size = register.operandSize;
        if ( size == null )  {
            size = OperandSize.WORD;
        }
        switch(size) {
            case WORD:
                return 0;
            case LONG:
                return 1;
        }
        throw new RuntimeException("Invalid index register operand size "+size);
    }
    @Override
    public int getValueFor(Field field, ICompilationContext ctx)
    {
        switch(field)
        {
            case SRC_REGISTER_KIND:
                return source().getIndexRegister().isDataRegister() ? 0 : 1;
            case SRC_INDEX_SIZE:
                return getIndexRegisterSizeBit( source().getIndexRegister() );
            case SRC_SCALE:
                return source().getIndexRegister().scaling.bits;
            case SRC_8_BIT_DISPLACEMENT:
                return source().getBaseDisplacement().getBits(ctx);
            case DST_REGISTER_KIND:
                return destination().getIndexRegister().isDataRegister() ? 0 : 1;
            case DST_INDEX_SIZE:
                return getIndexRegisterSizeBit( destination().getIndexRegister() );
            case DST_SCALE:
                return destination().getIndexRegister().scaling.bits;
            case DST_8_BIT_DISPLACEMENT:
                return destination().getBaseDisplacement().getBits(ctx);
            case OP_CODE:
                return getInstructionType().getOperationCode(this);
            case SRC_VALUE:
                return source().getValue().getBits(ctx);
            case SRC_BASE_REGISTER:
                if ( source().addressingMode.eaRegisterField.isFixedValue() ) {
                    return source().addressingMode.eaRegisterField.value();
                }
                return source().getValue().asRegister().getBits(ctx);
            case SRC_INDEX_REGISTER:
                return source().getIndexRegister().getBits(ctx);
            case SRC_BASE_DISPLACEMENT:
                return source().getBaseDisplacement().getBits(ctx);
            case SRC_OUTER_DISPLACEMENT:
                return source().getOuterDisplacement().getBits(ctx);
            case SRC_MODE:
                return source().addressingMode.eaModeField;
            case DST_VALUE:
                return destination().getValue().getBits(ctx);
            case DST_BASE_REGISTER:
                if ( destination().addressingMode.eaRegisterField.isFixedValue() ) {
                    return destination().addressingMode.eaRegisterField.value();
                }
                return destination().getValue().asRegister().getBits(ctx);
            case DST_INDEX_REGISTER:
                return destination().getIndexRegister().getBits(ctx);
            case DST_BASE_DISPLACEMENT:
                return destination().getBaseDisplacement().getBits(ctx);
            case DST_OUTER_DISPLACEMENT:
                return destination().getOuterDisplacement().getBits(ctx);
            case DST_MODE:
                return destination().addressingMode.eaModeField;
            case SIZE:
                if ( operandSize == OperandSize.UNSPECIFIED ) {
                    throw new RuntimeException("Operand size not specified");
                }
                return operandSize.bits;
            case EXG_DATA_REGISTER:
            case EXG_ADDRESS_REGISTER:
                final Register srcReg = source().getValue().asRegister().register;
                final Register dstReg = destination().getValue().asRegister().register;
                // data register if EXG used with registers of different types, otherwise either the src data or src address register
                if ( field == Field.EXG_DATA_REGISTER )
                {
                    if ( srcReg.isData() != dstReg.isData() )
                    {
                        return srcReg.isData() ? srcReg.bits : dstReg.bits;
                    }
                    return srcReg.bits;
                }
                // field == Field.EXG_ADDRESS_REGISTER;
                // address register if EXG used with registers of different types, otherwise either the dst data or dst address register
                if ( srcReg.isAddress() != dstReg.isAddress() )
                {
                    return dstReg.isAddress() ? dstReg.bits : srcReg.bits;
                }
                return dstReg.bits;
            case NONE:
                return 0;
            case CONDITION_CODE: // encoded branch condition,stored as operationMode on Instruction
                return getInstructionType().getOperationMode();
            default:
                throw new RuntimeException("Internal error,unhandled field "+field);
        }
    }
}