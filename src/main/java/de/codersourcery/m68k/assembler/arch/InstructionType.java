package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.IValueNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.OperandNode;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;

/**
 * Enumeration of all M68000 instructions.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum InstructionType
{
    /*
Bits 15 – 12 Operation
0000 Bit Manipulation/MOVEP/Immed iate
0001 Move Byte
0010 Move Long
0011 Move Word
0100 Miscellaneous
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

                @Override
                protected int getMaxSourceOperandSizeInBits()
                {
                    return 32;
                }

                @Override
                protected int getMaxDestinationOperandSizeInBits()
                {
                    return 32;
                }

                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return false;
                }
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

                @Override public boolean supportsExplicitOperandSize() { return false; }
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

    private InstructionType(String mnemonic, int operandCount, OperandSize defaultOperandSize, int operationMode)
    {
        this.mnemonic = mnemonic.toLowerCase();
        this.operandCount = operandCount;
        this.defaultOperandSize = defaultOperandSize;
        this.operationMode = operationMode;
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

    public static InstructionType getType(String value)
    {
        if (value != null)
        {
            final String lValue = value.toLowerCase();
            for (InstructionType t : InstructionType.values())
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
     * @param node
     * @param context
     */
    public void generateCode(InstructionNode node, ICompilationContext context)
    {
        final InstructionEncoding encoding;
        final byte[] data;
        try
        {
            encoding = getEncoding(this, node, context);
            final Function<Field, Integer> func = field -> node.getValueFor(field, context);
            data = encoding.apply(func);
        } catch (Exception e)
        {
            context.error(e.getMessage(), node, e);
            return;
        }
        context.getCodeWriter().writeBytes(data);
    }

    protected InstructionEncoding getEncoding(InstructionType type, InstructionNode insn, ICompilationContext context)
    {
        type.checkSupports(insn);

        switch (type)
        {
            case EXG:
                return EXG_ENCODING;
            case MOVEQ:
                checkOperandSize(insn.source().getValue(), Operand.SOURCE);
                return MOVEQ_ENCODING;
            case LEA:
                if ( insn.source().addressingMode == AddressingMode.ABSOLUTE_SHORT_ADDRESSING ) {
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

                int actualSizeInBits = checkOperandSize(op.getValue(),operandKind);

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

    private int checkOperandSize(IValueNode value,Operand opKind)
    {
        final int max = opKind == Operand.SOURCE ? getMaxSourceOperandSizeInBits() : getMaxDestinationOperandSizeInBits();
        int actualSize = getOperandSizeInBits(value);
        if ( actualSize > max ) {
            throw new RuntimeException("Operand out of range, expected at most "+max+" bits but was "+actualSize);
        }
        return actualSize;
    }

    private static int getOperandSizeInBits(IValueNode node)
    {
        int bits = node.getBits();
        if ( (bits & 0xffffff00) == 0 || ( (bits & 0xffffff00) == 0xffffff00 && ( (bits & 0x00ff) !=  0 ) ) ) {
            return 8;
        }
        if ( (bits & 0xffff0000) == 0 || ( (bits & 0xffff0000) == 0xffff0000 && ( (bits & 0xffff) !=  0 ) ) ) {
            return 16;
        }
        return 32;
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
     * @return
     */
    public boolean supportsExplicitOperandSize() {
        return true;
    }

    private static final String SRC_BRIEF_EXTENSION_WORD = "riiiqee0wwwwwwww";
    private static final String DST_BRIEF_EXTENSION_WORD = "RIIIQEE0WWWWWWWW";

    private static final InstructionEncoding MOVE_ENCODING = InstructionEncoding.of("ooooDDDMMMmmmsss");

    private static final InstructionEncoding MOVEQ_ENCODING = InstructionEncoding.of("0111DDD0vvvvvvvv");

    private static final InstructionEncoding LEA_ENCODING = InstructionEncoding.of("0100DDD111mmmsss",
            "vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    private static final InstructionEncoding LEA_WORD_ENCODING = InstructionEncoding.of("0100DDD111mmmsss",
            "vvvvvvvv_vvvvvvvv");

    private static final InstructionEncoding EXG_ENCODING = InstructionEncoding.of("1100kkk1ooooolll");
}