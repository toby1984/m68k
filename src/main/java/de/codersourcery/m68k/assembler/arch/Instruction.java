package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.IValueNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.NumberNode;
import de.codersourcery.m68k.parser.ast.OperandNode;
import de.codersourcery.m68k.parser.ast.RegisterNode;
import de.codersourcery.m68k.utils.Misc;
import javafx.scene.transform.Rotate;
import org.apache.commons.lang3.StringUtils;

import java.util.IdentityHashMap;
import java.util.function.Function;

import static de.codersourcery.m68k.assembler.arch.AddressingMode.ABSOLUTE_SHORT_ADDRESSING;
import static de.codersourcery.m68k.assembler.arch.AddressingMode.IMMEDIATE_VALUE;

/**
 * Enumeration of all M68000 instructions.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum Instruction
{
    RTS("RTS",0)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {

        }
    },
    JSR("JSR",1)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingMode(node,AddressingModeKind.CONTROL);
        }
    },
    SWAP("SWAP",1)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            if ( ! node.source().getValue().isDataRegister() ) {
                throw new RuntimeException("SWAP requires a data requires");
            }
            if ( ! node.useImpliedOperandSize && node.getOperandSize() != OperandSize.WORD ) {
                throw new RuntimeException("SWAP only supports .w");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    JMP("JMP",1)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    Instruction.checkSourceAddressingMode(node,AddressingModeKind.CONTROL);
                }
            },
    AND("AND",2)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                }
            },
    TRAP("TRAP",1, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    if ( node.hasDestination() ) {
                        throw new RuntimeException("TRAP only supports one operand");
                    }
                    if ( ! node.source().hasAddressingMode(AddressingMode.IMMEDIATE_VALUE ) ) {
                        throw new RuntimeException("TRAP requires an immediate mode value as operand but was "+node.source().addressingMode);
                    }
                }
            },
    RTE("RTE",0)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                }
            },
    ILLEGAL("ILLEGAL",0) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
        }
    },
    /*
     * DBcc instructions
     */
    DBT("DBT",2, 0b0000,Condition.BRT,ConditionalInstructionType.DBCC) { // aka 'always branch'
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBRA("DBRA",2, 0b0001,Condition.BRF,ConditionalInstructionType.DBCC) { // ignores condition check
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBHI("DBHI",2, 0b0010,Condition.BHI,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBLS("DBLS",2, 0b0011,Condition.BLS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBCC("DBCC",2, 0b0100,Condition.BCC,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBCS("DBCS",2, 0b0101,Condition.BCS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBNE("DBNE",2, 0b0110,Condition.BNE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBEQ("DBEQ",2, 0b0111,Condition.BEQ,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBVC("DBVC",2, 0b1000,Condition.BVC,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBVS("DBVS",2, 0b1001,Condition.BVS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBPL("DBPL",2, 0b1010,Condition.BPL,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBMI("DBMI",2, 0b1011,Condition.BMI,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBGE("DBGE",2, 0b1100,Condition.BGE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBLT("DBLT",2, 0b1101,Condition.BLT,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBGT("DBGT",2, 0b1110,Condition.BGT,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBLE("DBLE",2, 0b1111,Condition.BLE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    /*
     * Bcc instructions.
     */
    BRA("BRA",1, 0b0000,Condition.BRT,ConditionalInstructionType.BCC) { // aka 'always branch'
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BRF("BRF",1, 0b0001,Condition.BRF,ConditionalInstructionType.BCC) { // TODO: this is essentially "never branch" .... not very useful as NOP exists as well...
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BHI("BHI",1, 0b0010,Condition.BHI,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BLS("BLS",1, 0b0011,Condition.BLS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BCC("BCC",1, 0b0100,Condition.BCC,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BCS("BCS",1, 0b0101,Condition.BCS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BNE("BNE",1, 0b0110,Condition.BNE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BEQ("BEQ",1, 0b0111,Condition.BEQ,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BVC("BVC",1, 0b1000,Condition.BVC,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BVS("BVS",1, 0b1001,Condition.BVS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BPL("BPL",1, 0b1010,Condition.BPL,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BMI("BMI",1, 0b1011,Condition.BMI,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BGE("BGE",1, 0b1100,Condition.BGE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BLT("BLT",1, 0b1101,Condition.BLT,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BGT("BGT",1, 0b1110,Condition.BGT,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BLE("BLE",1, 0b1111,Condition.BLE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    // Misc
    NOP("nop",0, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    if ( node.hasChildren() ) {
                        throw new RuntimeException("NOP does not accept operands");
                    }
                }
            },
    EXG("exg",2, 0b1100)
            {
                @Override
                public int getOperationCode(InstructionNode insn)
                {
                /*
01000—Data registers
01001—Address registers
10001—Data register and address register
                 */
                    boolean isData1 = insn.source().getValue().isDataRegister();
                    boolean isData2 = insn.destination().getValue().asRegister().isDataRegister();
                    if ( isData1 != isData2 ) {
                        return 0b10001;
                    }
                    return isData1 ? 0b01000 : 0b01001;
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    final OperandNode source = node.source();

                    if ( ! source.getValue().isRegister() ) {
                        throw new RuntimeException("Bad operand type, EXG needs a address or data register");
                    }
                    if ( ! ( source.getValue().isDataRegister() || source.getValue().isAddressRegister() ) ) {
                        throw new RuntimeException("Unsupported register, EXG supports address or data registers");
                    }
                    final OperandNode destination = node.destination();
                    if ( ! destination.getValue().isRegister() ) {
                        throw new RuntimeException("Bad operand type, EXG needs a address or data register");
                    }
                    if ( ! ( destination.getValue().isDataRegister() || destination.getValue().isAddressRegister() ) ) {
                        throw new RuntimeException("Unsupported register, EXG supports address or data registers");
                    }
                }
            },
    MOVEA("movea", 2, 0b0000) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            if ( ! node.destination().getValue().isAddressRegister() ) {
                throw new RuntimeException("MOVEA requires an address register as destination");
            }
            if ( ! node.useImpliedOperandSize && node.getOperandSize() == OperandSize.BYTE) {
                throw new RuntimeException("MOVEA only supports .w or .l");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    MOVEQ("moveq", 2, 0b0111)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    final OperandNode source = node.source();
                    if ( ! source.hasAddressingMode(AddressingMode.IMMEDIATE_VALUE) )
                    {
                        throw new RuntimeException("MOVEQ requires an immediate value as source operand");
                    }
                    final OperandNode destination = node.destination();
                    if ( ! destination.hasAddressingMode(AddressingMode.DATA_REGISTER_DIRECT) )
                    {
                        throw new RuntimeException("MOVEQ requires a data register as destination operand");
                    }
                    Instruction.checkOperandSizeUnsigned(node.source().getValue(), 8,ctx);
                }

            },
    MOVE("move", 2, 0b0000)
            {
                @Override
                public int getOperationCode(InstructionNode insn)
                {
                    switch (insn.getOperandSize())
                    {
                        case BYTE:
                            return 0b0001;
                        case WORD:
                            return 0b0011;
                        case LONG:
                            return 0b0010;
                    }
                    throw new RuntimeException("Unhandled switch/case: " + insn.getOperandSize());
                }

                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                }
            },
    LEA("lea", 2, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    Instruction.checkSourceAddressingMode(node,AddressingModeKind.CONTROL);

                    final OperandNode source = node.source();
                    final OperandNode destination = node.destination();
                    if ( destination.getValue().isNot(NodeType.REGISTER) || ! destination.getValue().asRegister().isAddressRegister() )
                    {
                        throw new RuntimeException("LEA needs an address register as destination");
                    }
                }
            };

    public final ConditionalInstructionType conditionalType;
    public final Condition condition;
    private final String mnemonic;
    private final int operandCount;
    private final int operationMode; // bits 15-12 of first instruction word

    Instruction(String mnemonic, int operandCount) {
        this(mnemonic,operandCount, 0,null,ConditionalInstructionType.NONE);
    }

    Instruction(String mnemonic, int operandCount, int operationMode) {
        this(mnemonic,operandCount, operationMode,null,ConditionalInstructionType.NONE);
    }

    Instruction(String mnemonic, int operandCount, int operationMode, Condition condition, ConditionalInstructionType conditionalType)
    {
        this.mnemonic = mnemonic.toLowerCase();
        this.operandCount = operandCount;
        this.operationMode = operationMode;
        this.condition = condition;
        this.conditionalType = conditionalType;
    }

    public int getOperationMode()
    {
        return operationMode;
    }

    public abstract void checkSupports(InstructionNode node, ICompilationContext ctx);

    public int getOperationCode(InstructionNode insn)
    {
        return operationMode;
    }

    public int getOperandCount()
    {
        return operandCount;
    }

    public String getMnemonic()
    {
        return mnemonic;
    }

    public static Instruction getType(String value)
    {
        if (value != null)
        {
            final String lValue = value.toLowerCase();
            for (Instruction t : Instruction.values())
            {
                if (t.mnemonic.equals(lValue))
                {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Generates the code for a given instruction and writes it
     * using the context's current {@link de.codersourcery.m68k.assembler.IObjectCodeWriter}.
     *
     * @param insn
     * @param context
     * @param estimateSizeForUnknownOperands set to <code>true</code> to call {@link de.codersourcery.m68k.assembler.IObjectCodeWriter#allocateBytes(int)}
     *                         instead of writing the actual data bytes. Used while operands may still be unknown due to forward references
     */
    public void generateCode(InstructionNode insn, ICompilationContext context,boolean estimateSizeForUnknownOperands)
    {
        final InstructionEncoding encoding;
        final byte[] data;
        try
        {
            encoding = getEncoding(this, insn, context,estimateSizeForUnknownOperands);
            if ( estimateSizeForUnknownOperands ) {
                final int sizeInBytes = encoding.getSizeInBytes();
                System.out.println( insn.instruction+" with encoding "+encoding+" has size "+sizeInBytes);
                context.getCodeWriter().allocateBytes(sizeInBytes);
                return;
            }

            final Function<Field, Integer> func;
            final Condition condition = insn.instruction.condition;
            if ( condition != null )
            {
                final int instructionAddress = context.getCodeWriter().offset();
                func = field ->
                {
                    switch( insn.instruction.conditionalType )
                    {
                        case DBCC:
                            if (field == Field.SRC_BASE_REGISTER)
                            { // DBcc Dx,...
                                return insn.source().getValue().getBits(context);
                            }
                            if ( field == Field.CONDITION_CODE)
                            {
                                return condition.bits;
                            }
                            if ( field == Field.RELATIVE_OFFSET )
                            {
                                final Integer branchTargetAddress = insn.destination().getValue().getBits(context);
                                if ( (branchTargetAddress & 1) != 0 ) {
                                    throw new RuntimeException("Relative branch needs an even target address but got "+branchTargetAddress);
                                }
                                return branchTargetAddress - instructionAddress - 2;
                            }
                            break;
                        case BCC:
                            if ( field == Field.CONDITION_CODE)
                            {
                                return condition.bits;
                            }
                            if ( field == Field.RELATIVE_OFFSET )
                            {
                                final Integer branchTargetAddress = insn.source().getValue().getBits(context);
                                if ( (branchTargetAddress & 1) != 0 ) {
                                    throw new RuntimeException("Relative branch needs an even target address but got "+branchTargetAddress);
                                }
                                return branchTargetAddress - instructionAddress;
                            }
                            break;
                        case NONE:
                            throw new RuntimeException("Internal error - Instruction "+insn.instruction+" has non-NULL CC value but conditional instruction type NONE ?");
                        default:
                            throw new RuntimeException("Internal error - Instruction "+insn.instruction+" has unhandled conditional instruction type "+insn.instruction.conditionalType);
                    }
                    throw new RuntimeException("Internal error,unhandled field "+field);
                };
            } else {
                func = field -> getValueFor(insn, field, context);
            }
            data = encoding.apply(func);
            context.getCodeWriter().writeBytes(data);
        }
        catch (Exception e)
        {
            context.error(e.getMessage(), insn, e);
        }
    }

    protected InstructionEncoding getEncoding(Instruction type,
                                              InstructionNode insn,
                                              ICompilationContext context,
                                              boolean estimateSizeOnly)
    {
        type.checkSupports(insn, context);

        switch (type)
        {
            case RTS:
                return RTS_ENCODING;
            case JSR:
                String[] extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( extraSrcWords != null ) {
                    return JSR_ENCODING.append(extraSrcWords);
                }
                return JSR_ENCODING;
            case SWAP:
                return SWAP_ENCODING;
            case JMP:
                switch( insn.source().addressingMode )
                {
                    case ADDRESS_REGISTER_INDIRECT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    case MEMORY_INDIRECT_POSTINDEXED:
                    case MEMORY_INDIRECT_PREINDEXED:
                    case PC_INDIRECT_WITH_DISPLACEMENT:
                    case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                    case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    case PC_MEMORY_INDIRECT_POSTINDEXED:
                    case PC_MEMORY_INDIRECT_PREINDEXED:
                        return JMP_INDIRECT_ENCODING;
                    case ABSOLUTE_SHORT_ADDRESSING:
                        return JMP_SHORT_ENCODING;
                    case ABSOLUTE_LONG_ADDRESSING:
                        return JMP_LONG_ENCODING;
                    default:
                        throw new RuntimeException("Unsupported addressing mode for JMP: "+insn.source().addressingMode);
                }
            case AND:
                if ( insn.source().addressingMode == IMMEDIATE_VALUE &&
                        insn.destination().getValue().isRegister(Register.SR) )
                {
                    // ANDI #xx,SR
                    if ( insn.useImpliedOperandSize )
                    {
                        insn.setImplicitOperandSize(OperandSize.WORD);
                    }
                    else if (insn.getOperandSize() != OperandSize.WORD)
                    {
                        throw new RuntimeException("ANDI to SR needs a 16-bit operand");
                    }
                    if ( ! estimateSizeOnly )
                    {
                        final int bits = insn.source().getValue().getBits(context);
                        if ( NumberNode.getSizeInBitsUnsigned(bits) > 16 ) {
                            throw new RuntimeException("ANDI to SR needs a 16-bit operand, was: "+Misc.hex(bits));
                        }
                    }
                    return ANDI_TO_SR_ENCODING;
                }
                // TODO: Implement the other variants of AND ...
                throw new RuntimeException("Sorry, AND operation not fully implemented");
            case TRAP:
                if ( ! estimateSizeOnly )
                {
                    final int value = insn.source().getValue().getBits(context);
                    if ( value < 0 || value > 15 ) {
                        throw new RuntimeException("TRAP # out-of-range (0-15), was "+value);
                    }
                }
                return TRAP_ENCODING;
            case RTE:
                return RTE_ENCODING;
            case ILLEGAL:
                return ILLEGAL_ENCODING;
            // DBcc instructions
            case DBRA:
            case DBT:
            case DBHI:
            case DBLS:
            case DBCC:
            case DBCS:
            case DBNE:
            case DBEQ:
            case DBVC:
            case DBVS:
            case DBPL:
            case DBMI:
            case DBGE:
            case DBLT:
            case DBGT:
            case DBLE:
                return DBCC_ENCODING;
            // Bcc branch instructions
            case BRA:
                // case BRF:
            case BHI:
            case BLS:
            case BCC:
            case BCS:
            case BNE:
            case BEQ:
            case BVC:
            case BVS:
            case BPL:
            case BMI:
            case BGE:
            case BLT:
            case BGT:
            case BLE:
                Integer targetAddress = insn.source().getValue().getBits(context);
                if ( targetAddress == null )
                {
                    if ( estimateSizeOnly ) {
                        return BCC_32BIT_ENCODING; // worst-case scenario
                    }
                    throw new RuntimeException("Failed to get displacement from "+insn.source());
                }

                if ( (targetAddress & 1) != 0 ) {
                    throw new RuntimeException("Relative branch needs an even target address but got "+targetAddress);
                }

                final int currentAddress = context.getCodeWriter().offset();
                int relativeOffset = targetAddress - currentAddress;
                if ( relativeOffset == 0 ) {
                    /* A branch to the immediately following instruction automatically
                     * uses the 16-bit displacement format because the 8-bit
                     * displacement field contains $00 (zero offset) which indicates
                     * 16-bit displacement format.
                     */
                    return BCC_16BIT_ENCODING;
                }
                int size = NumberNode.getSizeInBitsSigned(relativeOffset);
                if ( size <= 8 )
                {
                    return BCC_8BIT_ENCODING;
                }
                if ( size <= 16 )
                {
                    return BCC_16BIT_ENCODING;
                }
                if ( size <= 32 )
                {
                    return BCC_32BIT_ENCODING;
                }
                throw new RuntimeException("Branch offset larger than 32 bits?");
            case NOP:
                return NOP_ENCODING;
            case EXG:
                return EXG_ENCODING;
            case MOVEQ:
                return MOVEQ_ENCODING;
            case LEA:
                if ( insn.source().addressingMode == ABSOLUTE_SHORT_ADDRESSING ) {
                    return LEA_WORD_ENCODING;
                }
                return LEA_LONG_ENCODING;
            case MOVEA:
            case MOVE:
                // check for MOVE USP
                final int srcIsUSP = insn.source().getValue().isRegister(Register.USP)      ? 0b01 : 0b00;
                final int dstIsUSP = insn.destination().getValue().isRegister(Register.USP) ? 0b10 : 0b00;
                switch( srcIsUSP | dstIsUSP )
                {
                    case 0b00:
                        break;
                    case 0b01:
                        if (!insn.destination().getValue().isAddressRegister())
                        {
                            throw new RuntimeException("MOVE USP,Ax requires  an address register as destination");
                        }
                        if ( insn.useImpliedOperandSize ) {
                            insn.setImplicitOperandSize( OperandSize.LONG );
                        }
                        if (insn.getOperandSize() != OperandSize.LONG ) {
                            throw new RuntimeException("MOVE USP,Ax only works on long-sized operands");
                        }
                        return MOVE_USP_TO_AX_ENCODING;
                    case 0b10:
                        if ( !insn.source().getValue().isAddressRegister())
                        {
                            throw new RuntimeException("MOVE Ax,USP requires an address register as source");
                        }
                        if ( insn.useImpliedOperandSize ) {
                            insn.setImplicitOperandSize( OperandSize.LONG );
                        }
                        if (insn.getOperandSize() != OperandSize.LONG ) {
                            throw new RuntimeException("MOVE Ax,USP only works on long-sized operands");
                        }
                        return MOVE_AX_TO_USP_ENCODING;
                    case 0b11:
                        throw new RuntimeException("MOVE USP,USP does not exist");
                }

                if ( insn.useImpliedOperandSize ) {
                    insn.setImplicitOperandSize( OperandSize.WORD );
                }

                // regular move instruction
                extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( insn.instruction == MOVEA )
                {
                    final InstructionEncoding encoding =
                            insn.getOperandSize() == OperandSize.WORD ? MOVEA_WORD_ENCODING : MOVEA_LONG_ENCODING;
                    return extraSrcWords != null ? encoding.append( extraSrcWords ) : encoding;
                }
                final String[] extraDstWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);

                InstructionEncoding encoding;
                switch( insn.getOperandSize() ) {
                    case BYTE:
                        encoding = MOVE_BYTE_ENCODING;
                        break;
                    case WORD:
                        encoding = MOVE_WORD_ENCODING;
                        break;
                    case LONG:
                        encoding = MOVE_LONG_ENCODING;
                        break;
                    default:
                        throw new RuntimeException("MOVE without operand size?");
                }
                if (extraSrcWords != null && extraDstWords == null)
                {
                    return encoding.append(extraSrcWords);
                }
                if (extraSrcWords == null && extraDstWords != null)
                {
                    return encoding.append(extraDstWords);
                }
                if (extraSrcWords != null && extraDstWords != null)
                {
                    return encoding.append(extraSrcWords).append(extraDstWords);
                }
                return encoding;
            default:
                throw new RuntimeException("Internal error,unhandled instruction type " + type);
        }
    }

    // returns any InstructionEncoding patterns
    // needed to accomodate all operand values
    private String[] getExtraWordPatterns(OperandNode op, Operand operandKind,InstructionNode insn,ICompilationContext ctx)
    {
        if ( op.addressingMode.maxExtensionWords == 0 ) {
            return null;
        }

        switch (op.addressingMode)
        {
            case DATA_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_POST_INCREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                Field field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
                return new String[] { StringUtils.repeat(field.c,16) };
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                // FIXME: 1 extra word
                break;
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                // FIXME: 1-3 extra words
                break;
            case MEMORY_INDIRECT_POSTINDEXED:
                // FIXME: 1-5 extra words
                break;
            case MEMORY_INDIRECT_PREINDEXED:
                // FIXME: 1-5 extra words
                break;
            case PC_INDIRECT_WITH_DISPLACEMENT:
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT: Field.DST_BASE_DISPLACEMENT;
                return new String[] { StringUtils.repeat(field.c,16) };
            case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:

                /*
                    DST_REGISTER_KIND('R'), // (bd,br,{Rx},od) D or A
    DST_INDEX_SIZE('Q'), // (bd,br,Rx{.w|.l},od)
    DST_SCALE('E'), // (bd,br,Rx.w{*4},od)
    DST_8_BIT_DISPLACEMENT('W'), // (bd,br,Rx,{od}

BRIEF EXTENSION WORD FORMAT
15 14 13 12 11 10 9 8 7 6 5 4 3 2 1 0
|   +-+--+  |  +--+ 0 +-------------+
D/A   |     |   |           |
    REGISTER|  SCALE      DISPLACEMENT
            W/L
                 */
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT: Field.DST_BASE_DISPLACEMENT;
                return new String[] { operandKind == Operand.SOURCE ? SRC_BRIEF_EXTENSION_WORD : DST_BRIEF_EXTENSION_WORD};
            case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                // FIXME: 1-3 extra words
                break;
            case PC_MEMORY_INDIRECT_POSTINDEXED:
                // FIXME: 1-5 extra words
                break;
            case PC_MEMORY_INDIRECT_PREINDEXED:
                // FIXME: 1-5 extra words
                break;
            case ABSOLUTE_SHORT_ADDRESSING:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,16) };
            case ABSOLUTE_LONG_ADDRESSING:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,32) };
            case IMMEDIATE_VALUE:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;

                // TODO: 8-bit immediate values could actually be stored in-line (MOVEQ) instead of wasting a byte here
                // TODO: Maybe add optimization pass that turns regular MOVE into MOVEQ when possible?
                final int words = insn.getOperandSize() == OperandSize.LONG ? 2 : 1;
                return new String[] { StringUtils.repeat(field.c,words*16) };
            case IMPLIED: return null; // handled
        }
        throw new RuntimeException("Unhandled addressing mode: "+op.addressingMode);
    }

    private static void checkOperandSizeUnsigned(IValueNode value, int maxSizeInBits, ICompilationContext ctx)
    {
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return;
        }
        int actualSize = NumberNode.getSizeInBitsUnsigned(nodeValue);
        if ( actualSize > maxSizeInBits ) {
            throw new RuntimeException("Operand out of range, expected at most "+maxSizeInBits+" bits but was "+actualSize);
        }
    }

    private static void checkOperandSizeSigned(IValueNode value, int maxSizeInBits, ICompilationContext ctx)
    {
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return;
        }
        int actualSize = NumberNode.getSizeInBitsSigned(nodeValue);
        if ( actualSize > maxSizeInBits ) {
            throw new RuntimeException("Operand out of range, expected at most "+maxSizeInBits+" bits but was "+actualSize);
        }
    }

    public Condition getCondition()
    {
        return condition;
    }

    /**
     * Whether this instruction supports explicit .b/.w/.l suffixes or not.
     *
     * Defaults to <code>false</code>
     * @return
     */
    public boolean supportsExplicitOperandSize() {
        return false;
    }

    public static final String SRC_BRIEF_EXTENSION_WORD = "riiiqee0wwwwwwww";
    public static final String DST_BRIEF_EXTENSION_WORD = "RIIIQEE0WWWWWWWW";

    public static final InstructionEncoding ANDI_TO_SR_ENCODING =
            InstructionEncoding.of("0000001001111100","vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding TRAP_ENCODING = InstructionEncoding.of("010011100100vvvv");

    public static final InstructionEncoding RTE_ENCODING = InstructionEncoding.of( "0100111001110011");

    public static final InstructionEncoding JMP_INDIRECT_ENCODING = InstructionEncoding.of( "0100111011mmmsss");
    public static final InstructionEncoding JMP_SHORT_ENCODING = InstructionEncoding.of(    "0100111011mmmsss","vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding JMP_LONG_ENCODING = InstructionEncoding.of(     "0100111011mmmsss","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding MOVE_BYTE_ENCODING = InstructionEncoding.of("0001DDDMMMmmmsss");
    public static final InstructionEncoding MOVE_WORD_ENCODING = InstructionEncoding.of("0011DDDMMMmmmsss");
    public static final InstructionEncoding MOVE_LONG_ENCODING = InstructionEncoding.of("0010DDDMMMmmmsss");

    public static final InstructionEncoding MOVEQ_ENCODING = InstructionEncoding.of("0111DDD0vvvvvvvv");

    public static final InstructionEncoding LEA_LONG_ENCODING = InstructionEncoding.of("0100DDD111mmmsss", "vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding LEA_WORD_ENCODING = InstructionEncoding.of("0100DDD111mmmsss", "vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding MOVE_AX_TO_USP_ENCODING = InstructionEncoding.of("0100111001100sss");

    public static final InstructionEncoding MOVE_USP_TO_AX_ENCODING = InstructionEncoding.of("0100111001101DDD");

    public static final InstructionEncoding ILLEGAL_ENCODING = InstructionEncoding.of( "0100101011111100");

    public static final InstructionEncoding EXG_ENCODING = InstructionEncoding.of("1100kkk1ooooolll");

    public static final InstructionEncoding NOP_ENCODING = InstructionEncoding.of("0100111001110001");

    public static final InstructionEncoding DBCC_ENCODING = InstructionEncoding.of( "0101cccc11001sss","CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding BCC_8BIT_ENCODING = InstructionEncoding.of(  "0110ccccCCCCCCCC");

    public static final InstructionEncoding BCC_16BIT_ENCODING = InstructionEncoding.of( "0110cccc00000000","CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding BCC_32BIT_ENCODING = InstructionEncoding.of( "0110cccc11111111",
            "CCCCCCCC_CCCCCCCC_CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding MOVEA_WORD_ENCODING = InstructionEncoding.of(  "0011DDD001mmmsss");
    public static final InstructionEncoding MOVEA_LONG_ENCODING = InstructionEncoding.of(  "0010DDD001mmmsss");

    public static final InstructionEncoding SWAP_ENCODING = InstructionEncoding.of(  "0100100001000sss");

    public static final InstructionEncoding JSR_ENCODING = InstructionEncoding.of( "0100111010mmmsss");

    public static final InstructionEncoding RTS_ENCODING = InstructionEncoding.of( "0100111001110101");

    public static final IdentityHashMap<InstructionEncoding,Instruction> ALL_ENCODINGS = new IdentityHashMap<>()
    {{
        put(ANDI_TO_SR_ENCODING,AND);
        put(JMP_INDIRECT_ENCODING,JMP);
        put(JMP_SHORT_ENCODING,JMP);
        put(JMP_LONG_ENCODING,JMP);
        put(TRAP_ENCODING,TRAP);
        put(RTE_ENCODING,RTE);
        put(MOVE_BYTE_ENCODING,MOVE);
        put(MOVE_WORD_ENCODING,MOVE);
        put(MOVE_LONG_ENCODING,MOVE);
        put(MOVEQ_ENCODING,MOVEQ);
        put(LEA_LONG_ENCODING,LEA);
        put(LEA_WORD_ENCODING,LEA);
        put(MOVE_AX_TO_USP_ENCODING,MOVE);
        put(MOVE_USP_TO_AX_ENCODING,MOVE);
        put(ILLEGAL_ENCODING,ILLEGAL);
        put(EXG_ENCODING,EXG);
        put(NOP_ENCODING,NOP);
        put(DBCC_ENCODING,DBCC);
        put(BCC_8BIT_ENCODING,BCC);
        put(BCC_16BIT_ENCODING,BCC);
        put(BCC_32BIT_ENCODING,BCC);
        put(MOVEA_LONG_ENCODING,MOVEA);
        put(MOVEA_WORD_ENCODING,MOVEA);
        put(JSR_ENCODING,JSR);
        put(RTS_ENCODING,RTS);
    }};


    private static void checkDBccInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        if ( node.source().addressingMode != AddressingMode.DATA_REGISTER_DIRECT ) {
            throw new RuntimeException("Unsupported addressing mode: "+node.source().addressingMode );
        }
        switch( node.destination().addressingMode )
        {
            case ABSOLUTE_LONG_ADDRESSING:
            case ABSOLUTE_SHORT_ADDRESSING:
                break;
            default:
                throw new RuntimeException("Unsupported addressing mode: "+node.destination().addressingMode );
        }
        checkOperandSizeSigned(node.destination().getValue(), 16,ctx);
    }

    private static void checkBranchInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        switch (node.source().addressingMode)
        {
            case ABSOLUTE_LONG_ADDRESSING:
            case ABSOLUTE_SHORT_ADDRESSING:
                break;
            default:
                throw new RuntimeException("Unsupported addressing mode: " + node.source().addressingMode);
        }
        checkOperandSizeSigned(node.source().getValue(), 32, ctx);
    }

    private int getValueFor(InstructionNode insn,Field field, ICompilationContext ctx)
    {
        switch(field)
        {
            case SRC_REGISTER_KIND:
                return insn.source().getIndexRegister().isDataRegister() ? 0 : 1;
            case SRC_INDEX_SIZE:
                return getIndexRegisterSizeBit( insn.source().getIndexRegister() );
            case SRC_SCALE:
                return insn.source().getIndexRegister().scaling.bits;
            case SRC_8_BIT_DISPLACEMENT:
                return insn.source().getBaseDisplacement().getBits(ctx);
            case DST_REGISTER_KIND:
                return insn.destination().getIndexRegister().isDataRegister() ? 0 : 1;
            case DST_INDEX_SIZE:
                return getIndexRegisterSizeBit( insn.destination().getIndexRegister() );
            case DST_SCALE:
                return insn.destination().getIndexRegister().scaling.bits;
            case DST_8_BIT_DISPLACEMENT:
                return insn.destination().getBaseDisplacement().getBits(ctx);
            case OP_CODE:
                return insn.getInstructionType().getOperationCode(insn );
            case SRC_VALUE:
                return insn.source().getValue().getBits(ctx);
            case SRC_BASE_REGISTER:
                if ( insn.source().addressingMode.eaRegisterField.isFixedValue() ) {
                    return insn.source().addressingMode.eaRegisterField.value();
                }
                return insn.source().getValue().asRegister().getBits(ctx);
            case SRC_INDEX_REGISTER:
                return insn.source().getIndexRegister().getBits(ctx);
            case SRC_BASE_DISPLACEMENT:
                return insn.source().getBaseDisplacement().getBits(ctx);
            case SRC_OUTER_DISPLACEMENT:
                return insn.source().getOuterDisplacement().getBits(ctx);
            case SRC_MODE:
                return insn.source().addressingMode.eaModeField;
            case DST_VALUE:
                return insn.destination().getValue().getBits(ctx);
            case DST_BASE_REGISTER:
                if ( insn.destination().addressingMode.eaRegisterField.isFixedValue() ) {
                    return insn.destination().addressingMode.eaRegisterField.value();
                }
                return insn.destination().getValue().asRegister().getBits(ctx);
            case DST_INDEX_REGISTER:
                return insn.destination().getIndexRegister().getBits(ctx);
            case DST_BASE_DISPLACEMENT:
                return insn.destination().getBaseDisplacement().getBits(ctx);
            case DST_OUTER_DISPLACEMENT:
                return insn.destination().getOuterDisplacement().getBits(ctx);
            case DST_MODE:
                return insn.destination().addressingMode.eaModeField;
            case SIZE:
                if ( insn.getOperandSize() == OperandSize.UNSPECIFIED ) {
                    throw new RuntimeException("Operand size not specified");
                }
                if ( insn.instruction == MOVEA ) {
                    // MOVEA uses non-standard encoding....
                    switch(insn.getOperandSize()) {
                        case WORD: return 0b11;
                        case LONG: return 0b10;
                    }
                    throw new RuntimeException("Unhandled operand size for MOVEA: "+insn.getOperandSize());
                }
                return insn.getOperandSize().bits;
            case EXG_DATA_REGISTER:
            case EXG_ADDRESS_REGISTER:
                final Register srcReg = insn.source().getValue().asRegister().register;
                final Register dstReg = insn.destination().getValue().asRegister().register;
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
                return insn.getInstructionType().getOperationMode();
            default:
                throw new RuntimeException("Internal error,unhandled field "+field);
        }
    }

    public static int getIndexRegisterSizeBit(RegisterNode register)
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

    private static void checkSourceAddressingMode(InstructionNode insn,AddressingModeKind kind)
    {
        if( ! insn.source().addressingMode.hasKind(kind ) )
        {
            throw new RuntimeException("Instruction "+insn.instruction+" only supports addressing modes of kind "+kind);
        }
    }

    private static void checkDestinationAddressingMode(InstructionNode insn,AddressingModeKind kind)
    {
        if( ! insn.destination().addressingMode.hasKind(kind ) )
        {
            throw new RuntimeException("Instruction "+insn.instruction+" only supports addressing modes of kind "+kind);
        }
    }
}