package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.IValueNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.NumberNode;
import de.codersourcery.m68k.parser.ast.OperandNode;
import org.apache.commons.lang3.StringUtils;

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
    /*
Bits 15 – 12 Operation
0000 Bit Manipulation/MOVEP/Immediate
0001 Move Byte
0010 Move Long
0011 Move Word
0100 Miscellaneous (TRAP)
0101 ADDQ/SUBQ/Scc/DBcc/TRAPc c
0110 Bcc/BSR/BRA
0111 MOVEQ
1000 OR/DIV/SBCD
1001 SUB/SUBX
1010 (Unassigned, Reserved)
1011 CMP/EOR
1100 AND/MUL/ABCD/EXG
1101 ADD/ADDX
1110 Shift/Rotate/Bit Field
1111 Coprocessor Interface/MC68040 and CPU32 Extensions
     */

    /*
     * Branch instructions.
     */
    TRAP("TRAP",1,OperandSize.BYTE,0b0100)
     {
        @Override
        public void checkSupports(InstructionNode node)
        {
            if ( node.hasDestination() ) {
                throw new RuntimeException("TRAP only supports one operand");
            }
            if ( node.source().addressingMode != IMMEDIATE_VALUE ) {
                throw new RuntimeException("TRAP requires an immediate mode value as operand");
            }
        }

        @Override
        protected int getMaxSourceOperandSizeInBits()
        {
            return 0;
        }

        @Override
        protected int getMaxDestinationOperandSizeInBits()
        {
            return 0;
        }
    },
    RTE("RTE",0,OperandSize.WORD,0b0000)
            {
        @Override
        public void checkSupports(InstructionNode node)
        {
        }

        @Override
        protected int getMaxSourceOperandSizeInBits()
        {
            return 0;
        }

        @Override
        protected int getMaxDestinationOperandSizeInBits()
        {
            return 0;
        }
    },
    ILLEGAL("ILLEGAL",0,OperandSize.WORD,0b0000) {
        @Override
        public void checkSupports(InstructionNode node)
        {
        }

        @Override
        protected int getMaxSourceOperandSizeInBits()
        {
            return 0;
        }

        @Override
        protected int getMaxDestinationOperandSizeInBits()
        {
            return 0;
        }
    },
    BRA("BRA",1,OperandSize.LONG,0b0000) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
//    BRF("BRF",1,OperandSize.LONG,0b0001) { // TODO: this is essentially "never branch" .... not very useful...
//        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
//        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
//        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
//        @Override public boolean isRelativeBranch() { return true; }
//    },
    BHI("BHI",1,OperandSize.LONG,0b0010) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BLS("BLS",1,OperandSize.LONG,0b0011) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BCC("BCC",1,OperandSize.LONG,0b0100) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BCS("BCS",1,OperandSize.LONG,0b0101) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BNE("BNE",1,OperandSize.LONG,0b0110) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BEQ("BEQ",1,OperandSize.LONG,0b0111) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BVC("BVC",1,OperandSize.LONG,0b1000) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BVS("BVS",1,OperandSize.LONG,0b1001) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BPL("BPL",1,OperandSize.LONG,0b1010) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BMI("BMI",1,OperandSize.LONG,0b1011) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BGE("BGE",1,OperandSize.LONG,0b1100) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BLT("BLT",1,OperandSize.LONG,0b1101) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BGT("BGT",1,OperandSize.LONG,0b1110) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    BLE("BLE",1,OperandSize.LONG,0b1111) {
        @Override public void checkSupports(InstructionNode node) { checkBranchInstructionValid(node); }
        @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
        @Override public boolean isRelativeBranch() { return true; }
    },
    NOP("nop",0,OperandSize.WORD,0b0100)
    {
        @Override
        public void checkSupports(InstructionNode node)
        {
            if ( node.hasChildren() ) {
                throw new RuntimeException("NOP does not accept operands");
            }
        }

        @Override protected int getMaxSourceOperandSizeInBits() { return 0; }
        @Override protected int getMaxDestinationOperandSizeInBits() { return 0; }
    },
    EXG("exg",2,OperandSize.LONG,0b1100)
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
                public void checkSupports(InstructionNode node)
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

                @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
                @Override protected int getMaxDestinationOperandSizeInBits() { return 32; }
            },
    MOVEQ("moveq", 2, OperandSize.BYTE, 0b0111)
            {

                @Override
                public void checkSupports(InstructionNode node)
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
                }

                @Override protected int getMaxDestinationOperandSizeInBits() { return 32; }
                @Override protected int getMaxSourceOperandSizeInBits() { return 8; }

            },
    MOVE("move", 2, OperandSize.WORD, 0b0000)
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
                @Override protected int getMaxDestinationOperandSizeInBits() { return 32; }
                @Override protected int getMaxSourceOperandSizeInBits() { return 32; }

                @Override
                public void checkSupports(InstructionNode node)
                {
                }
            },
    LEA("lea", 2, OperandSize.LONG, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node)
                {
                    final OperandNode source = node.source();
                    final OperandNode destination = node.destination();
                    if ( destination.getValue().isNot(NodeType.REGISTER) || ! destination.getValue().asRegister().isAddressRegister() )
                    {
                        throw new RuntimeException("LEA needs an address register as destination");
                    }
                    if ( ! source.hasAbsoluteAddressing() )
                    {
                        throw new RuntimeException("LEA requires an absolute address value as source");
                    }
                }
                @Override protected int getMaxDestinationOperandSizeInBits() { return 32; }
                @Override protected int getMaxSourceOperandSizeInBits() { return 32; }
            };

    public final OperandSize defaultOperandSize;
    private final String mnemonic;
    private final int operandCount;
    private final int operationMode; // bits 15-12 of first instruction word

    private Instruction(String mnemonic, int operandCount, OperandSize defaultOperandSize, int operationMode)
    {
        this.mnemonic = mnemonic.toLowerCase();
        this.operandCount = operandCount;
        this.defaultOperandSize = defaultOperandSize;
        this.operationMode = operationMode;
    }

    public int getOperationMode()
    {
        return operationMode;
    }

    public abstract void checkSupports(InstructionNode node);

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
                context.getCodeWriter().allocateBytes(encoding.getSizeInBytes());
                return;
            }

            final Function<Field, Integer> func;
            if ( insn.getInstructionType().isRelativeBranch() )
            {
                final int instructionAddress = context.getCodeWriter().offset();
                func = field ->
                {
                    if ( field == Field.CONDITION_CODE )
                    {
                        // operation mode field in enum is abused for condition code encoding
                        return insn.getInstructionType().getOperationMode();
                    }
                    if ( field == Field.RELATIVE_OFFSET )
                    {
                        final Integer branchTargetAddress = insn.source().getValue().getBits(context);
                        if ( (branchTargetAddress & 1) != 0 ) {
                            throw new RuntimeException("Relative branche needs an even target address but got "+branchTargetAddress);
                        }
                        return branchTargetAddress - instructionAddress;
                    }
                    throw new RuntimeException("Internal error,unhandled field "+field);
                };
            } else {
                func = field -> insn.getValueFor(field, context);
            }
            data = encoding.apply(func);
            context.getCodeWriter().writeBytes(data);
        }
        catch (Exception e)
        {
            context.error(e.getMessage(), insn, e);
        }
    }

    protected InstructionEncoding getEncoding(Instruction type, InstructionNode insn, ICompilationContext context, boolean estimateSizeOnly)
    {
        type.checkSupports(insn);

        switch (type)
        {
            case TRAP:
                if ( ! estimateSizeOnly )
                {
                    final int value = insn.source().getValue().getBits(context);
                    if ( value <= 0 || value > 15 ) {
                        throw new RuntimeException("TRAP # out-of-range (0-15), was "+value);
                    }
                }
                return TRAP_ENCODING;
            case RTE:
                return RTE_ENCODING;
            case ILLEGAL:
                return ILLEGAL_ENCODING;
            // Relative branch instructions
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
                checkOperandSizeUnsigned(insn.source().getValue(), Operand.SOURCE,context);
                return MOVEQ_ENCODING;
            case LEA:
                if ( insn.source().addressingMode == ABSOLUTE_SHORT_ADDRESSING ) {
                    return LEA_WORD_ENCODING;
                }
                return LEA_ENCODING;
            case MOVE:
                final String[] extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                final String[] extraDstWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);

                if (extraSrcWords != null && extraDstWords == null)
                {
                    return MOVE_ENCODING.append(extraSrcWords);
                }
                if (extraSrcWords == null && extraDstWords != null)
                {
                    return MOVE_ENCODING.append(extraDstWords);
                }
                if (extraSrcWords != null && extraDstWords != null)
                {
                    return MOVE_ENCODING.append(extraSrcWords).append(extraDstWords);
                }
                return MOVE_ENCODING;
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

                int actualSizeInBits = checkOperandSizeUnsigned(op.getValue(),operandKind,ctx);

                if ( actualSizeInBits > insn.getOperandSize().sizeInBits() ) {
                    throw new RuntimeException("Operand has "+actualSizeInBits+" bits but instruction specifies to use only "+
                            insn.getOperandSize().sizeInBits());
                }
                // TODO: 8-bit immediate values could actually be stored in-line (MOVEQ) instead of wasting a byte here
                // TODO: Maybe add optimization pass that turns regular MOVE into MOVEQ when possible?
                final int words = insn.getOperandSize() == OperandSize.LONG ? 2 : 1;
                return new String[] { StringUtils.repeat(field.c,words*16) };
            case NO_OPERAND: return null; // handled
        }
        throw new RuntimeException("Unhandled addressing mode: "+op.addressingMode);
    }

    private int checkOperandSizeUnsigned(IValueNode value,Operand opKind,ICompilationContext ctx)
    {
        final int max = opKind == Operand.SOURCE ? getMaxSourceOperandSizeInBits() : getMaxDestinationOperandSizeInBits();
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return max;
        }
        int actualSize = NumberNode.getSizeInBitsUnsigned(nodeValue);
        if ( actualSize > max ) {
            throw new RuntimeException("Operand out of range, expected at most "+max+" bits but was "+actualSize);
        }
        return actualSize;
    }

    private int checkOperandSizeSigned(IValueNode value,Operand opKind,ICompilationContext ctx)
    {
        final int max = opKind == Operand.SOURCE ? getMaxSourceOperandSizeInBits() : getMaxDestinationOperandSizeInBits();
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return max;
        }
        int actualSize = NumberNode.getSizeInBitsSigned(nodeValue);
        if ( actualSize > max ) {
            throw new RuntimeException("Operand out of range, expected at most "+max+" bits but was "+actualSize);
        }
        return actualSize;
    }

    public boolean isRelativeBranch() {
        return false;
    }

    /**
     * Returns the maximum size (in bits) this instruction supports for the source operand.
     * @return
     */
    protected abstract int getMaxSourceOperandSizeInBits();

    /**
     * Returns the maximum size (in bits) this instruction supports for the source operand.
     * @return
     */
    protected abstract int getMaxDestinationOperandSizeInBits();

    /**
     * Whether this instruction supports explicit .b/.w/.l suffixes or not.
     *
     * Defaults to <code>false</code>
     * @return
     */
    public boolean supportsExplicitOperandSize() {
        return false;
    }

    private static final String SRC_BRIEF_EXTENSION_WORD = "riiiqee0wwwwwwww";
    private static final String DST_BRIEF_EXTENSION_WORD = "RIIIQEE0WWWWWWWW";

    private static final InstructionEncoding TRAP_ENCODING = InstructionEncoding.of("010011100100vvvv");

    private static final InstructionEncoding RTE_ENCODING = InstructionEncoding.of( "0100111001110011");

    private static final InstructionEncoding MOVE_ENCODING = InstructionEncoding.of("ooooDDDMMMmmmsss");

    private static final InstructionEncoding MOVEQ_ENCODING = InstructionEncoding.of("0111DDD0vvvvvvvv");

    private static final InstructionEncoding LEA_ENCODING = InstructionEncoding.of("0100DDD111mmmsss",
            "vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    private static final InstructionEncoding LEA_WORD_ENCODING = InstructionEncoding.of("0100DDD111mmmsss",
            "vvvvvvvv_vvvvvvvv");

    private static final InstructionEncoding ILLEGAL_ENCODING = InstructionEncoding.of("0100101011111100");

    private static final InstructionEncoding EXG_ENCODING = InstructionEncoding.of("1100kkk1ooooolll");

    private static final InstructionEncoding NOP_ENCODING = InstructionEncoding.of("0100111001110001");

    private static final InstructionEncoding BCC_8BIT_ENCODING = InstructionEncoding.of(  "0110ccccCCCCCCCC");

    private static final InstructionEncoding BCC_16BIT_ENCODING = InstructionEncoding.of( "0110cccc00000000","CCCCCCCC_CCCCCCCC");

    private static final InstructionEncoding BCC_32BIT_ENCODING = InstructionEncoding.of( "0110cccc11111111",
            "CCCCCCCC_CCCCCCCC_CCCCCCCC_CCCCCCCC");

    private static final void checkBranchInstructionValid(InstructionNode node)
    {
        switch( node.source().addressingMode )
        {
            case ABSOLUTE_LONG_ADDRESSING:
            case ABSOLUTE_SHORT_ADDRESSING:
                break;
            default:
                throw new RuntimeException("Unsupported addressing mode: "+node.source().addressingMode );
        }
    }
}