package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.OperandNode;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;

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

                @Override
                public void checkSupports(Operand kind, OperandNode node)
                {
                }
            },
    LEA("lea", 2, OperandSize.LONG, 0b0100)
            {
                @Override
                public void checkSupports(Operand kind, OperandNode node)
                {
                    if (kind == Operand.DESTINATION)
                    {
                        if (node.addressingMode != AddressingMode.ADDRESS_REGISTER_DIRECT)
                        {
                            throw new RuntimeException("LEA needs an address register as destination");
                        }
                    }
                    else if (kind == Operand.SOURCE)
                    {
                        if (node.addressingMode != AddressingMode.IMMEDIATE_ADDRESS)
                        {
                            throw new RuntimeException("LEA requires an immediate address value as source");
                        }
                    }
                }
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

    public abstract void checkSupports(Operand kind, OperandNode node);

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

    /*
                case 's': return SRC_REGISTER;
                case 'm': return SRC_MODE;
                case 'D': return DST_REGISTER;
                case 'M': return DST_MODE;
                case 'S': return SIZE;
                case 'o': return OPERATION_CODE;
     */
    private static final InstructionEncoding MOVE_ENCODING = InstructionEncoding.of("ooooDDDMMMmmmsss");
    private static final InstructionEncoding MOVE_SRC_EXTRA_ENCODING = InstructionEncoding.of("ooooDDDMMM111000");
    private static final InstructionEncoding MOVE_DST_EXTRA_ENCODING = InstructionEncoding.of("oooo000111mmmsss");
    private static final InstructionEncoding MOVE_SRC_AND_DST_EXTRA_ENCODING = InstructionEncoding.of("oooo000111111000");

    private static final InstructionEncoding LEA_ENCODING = InstructionEncoding.of("0100DDD111mmmsss");

    protected InstructionEncoding getEncoding(InstructionType type, InstructionNode insn, ICompilationContext context)
    {
        OperandNode operand = insn.source();
        if (operand != null)
        {
            type.checkSupports(Operand.SOURCE, operand);
        }
        operand = insn.destination();
        if (operand != null)
        {
            type.checkSupports(Operand.DESTINATION, operand);
        }

        switch (type)
        {
            case LEA:
                return LEA_ENCODING;
            case MOVE:
                int extraSrcWords = getExtraWordCount(insn.source(), insn,context);
                int extraDstWords = getExtraWordCount(insn.destination(), insn,context);

                if (extraSrcWords > 2)
                {
                    throw new RuntimeException("Not implemented: Generating more than 2 extra src words");
                }
                if (extraDstWords > 2)
                {
                    throw new RuntimeException("Not implemented: Generating more than 2 extra dst words");
                }

                if (extraSrcWords != 0 && extraDstWords == 0)
                {
                    // TODO: Hack, will break if more than 2 extra words (>32 bits) are needed
                    final String srcExtra = StringUtils.repeat(Field.SRC_VALUE.c, extraSrcWords * 16);
                    return MOVE_SRC_EXTRA_ENCODING.append(srcExtra);
                }
                if (extraSrcWords == 0 && extraDstWords != 0)
                {
                    // TODO: Hack, will break if more than 2 extra words (>32 bits) are needed
                    final String dstExtra = StringUtils.repeat(Field.DST_VALUE.c, extraSrcWords * 16);
                    return MOVE_DST_EXTRA_ENCODING.append(dstExtra);
                }
                if (extraSrcWords != 0 && extraDstWords != 0)
                {
                    // TODO: Hack, will break if more than 2 extra words (>32 bits) are needed
                    final String srcExtra = StringUtils.repeat(Field.SRC_VALUE.c, extraSrcWords * 16);
                    final String dstExtra = StringUtils.repeat(Field.DST_VALUE.c, extraSrcWords * 16);
                    return MOVE_SRC_AND_DST_EXTRA_ENCODING.append(srcExtra, dstExtra);
                }
                return MOVE_ENCODING;
            default:
                throw new RuntimeException("Internal error,unhandled instruction type " + type);
        }
    }

    // returns how many extra words will need to be written
    // to accomodate for the given operand and operand size
    private int getExtraWordCount(OperandNode op, InstructionNode insn,ICompilationContext ctx)
    {
        switch (op.addressingMode)
        {
            case DATA_REGISTER_DIRECT: // can be encoded in first instruction word
                return 0;
            case ADDRESS_REGISTER_DIRECT: // can be encoded in first instruction word
                return 0;
            case ADDRESS_REGISTER_INDIRECT: // can be encoded in first instruction word
                return 0;
            case ADDRESS_REGISTER_INDIRECT_POST_INCREMENT:
                return 0; // TODO: Wrong, fix me
            case ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT:
                return 0; // TODO: Wrong, fix me
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                return 0; // TODO: Wrong, fix me
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT_AND_SCALE:
                return 0; // TODO: Wrong, fix me
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT_AND_SCALE_OPTIONAL:
                return 0; // TODO: Wrong, fix me
            case PROGRAM_COUNTER_INDIRECT_WITH_DISPLACEMENT:
                return 0; // TODO: Wrong, fix me
            case IMMEDIATE_VALUE:
                break;
            case IMMEDIATE_ADDRESS: // LEA $1234,A0
                return 2;
            case NO_OPERAND:
                return 0;
            case MEMORY_INDIRECT: // MOVE ($1234).w,D0 / MOVE ($12345678).L,D1
                //  check whether the address fits in 15 bits
                // (16-bit instruction words get sign-extended by the
                // CPU)
                int value = insn.getValue(op);
                if ( (value & 1<<15) == 0 ) { // sign-expansion would not turn this into a negative 32-bit value
                    return 1;
                }
                return 2;
        }
        throw new RuntimeException("Unhandled addressing mode: "+op.addressingMode);
    }
}