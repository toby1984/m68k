package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;
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
                        if ( node.getValue().isNot(NodeType.REGISTER) || ! node.getValue().asRegister().isAddressRegister() )
                        {
                            throw new RuntimeException("LEA needs an address register as destination");
                        }
                    }
                    else if (kind == Operand.SOURCE)
                    {
                        if ( ! node.hasAbsoluteAddressing() )
                        {
                            throw new RuntimeException("LEA requires an absolute address value as source");
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
    private static final InstructionEncoding MOVE_SRC_EXTRA_ENCODING = InstructionEncoding.of("ooooDDDMMMmmm000");
    private static final InstructionEncoding MOVE_DST_EXTRA_ENCODING = InstructionEncoding.of("oooo000MMMmmmsss");
    private static final InstructionEncoding MOVE_SRC_AND_DST_EXTRA_ENCODING = InstructionEncoding.of("oooo000MMMmmm000");

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
                final String[] extraSrcWords = getExtraWordPatterns(insn.source(), true, insn,context);
                final String[] extraDstWords = getExtraWordPatterns(insn.destination(), false, insn,context);

                if (extraSrcWords != null && extraDstWords == null)
                {
                    return MOVE_SRC_EXTRA_ENCODING.append(extraSrcWords);
                }
                if (extraSrcWords == null && extraDstWords != null)
                {
                    return MOVE_DST_EXTRA_ENCODING.append(extraDstWords);
                }
                if (extraSrcWords != null && extraDstWords != null)
                {
                    return MOVE_SRC_AND_DST_EXTRA_ENCODING.append(extraSrcWords).append(extraDstWords);
                }
                return MOVE_ENCODING;
            default:
                throw new RuntimeException("Internal error,unhandled instruction type " + type);
        }
    }

    // returns any InstructionEncoding patterns
    // needed to accomodate all operand values
    private String[] getExtraWordPatterns(OperandNode op, boolean isSourceOperand,InstructionNode insn,ICompilationContext ctx)
    {
//            case DATA_REGISTER_DIRECT: // can be encoded in first instruction word
//                return null;
//            case ADDRESS_REGISTER_DIRECT: // can be encoded in first instruction word
//                return null;
//            case ADDRESS_REGISTER_INDIRECT: // can be encoded in first instruction word
//                return null;
//            case ADDRESS_REGISTER_INDIRECT_POST_INCREMENT:
//                return null; // TODO: Wrong, fix me
//            case ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT:
//                return null; // TODO: Wrong, fix me
//            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
//                char c = isSourceOperand ? Field.SRC_BASE_DISPLACEMENT.c : Field.DST_BASE_DISPLACEMENT.c;
//                return new String[] { StringUtils.repeat(c,16) };
//            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT_AND_SCALE:
//                return null; // TODO: Wrong, fix me
//            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT_AND_SCALE_OPTIONAL:
//                return null; // TODO: Wrong, fix me
//            case PROGRAM_COUNTER_INDIRECT_WITH_DISPLACEMENT:
//                return null; // TODO: Wrong, fix me
//            case IMMEDIATE_VALUE:
//                return null; // TODO: Wrong, fix me
//            case IMMEDIATE_ADDRESS: // LEA $1234,A0
//                c = isSourceOperand ? Field.SRC_VALUE.c : Field.DST_VALUE.c;
//                return new String[] { StringUtils.repeat(c,32) };
//            case NO_OPERAND:
//                return null;
//            case MEMORY_INDIRECT: // MOVE ($1234).w,D0 / MOVE ($12345678).L,D1
//                //  check whether the address fits in 15 bits
//                // (16-bit instruction words get sign-extended by the
//                // CPU)
//                c = isSourceOperand ? Field.SRC_VALUE.c : Field.DST_VALUE.c;
//                int value = insn.getValue(op.getValue());
//                if ( (value & 1<<15) == 0 ) { // sign-expansion would not turn this into a negative 32-bit value
//                    return new String[] { StringUtils.repeat(c,16) };
//                }
//                return new String[] { StringUtils.repeat(c,32) };
//        }
        final Field displacement = isSourceOperand ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
        final Field value = isSourceOperand ? Field.SRC_VALUE: Field.DST_VALUE;
        switch (op.addressingMode)
        {
            case DATA_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_POST_INCREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                return new String[] { StringUtils.repeat(displacement.c,16) };
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
                // FIXME: 1 extra word
                break;
            case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                // FIXME: 1 extra word
                break;
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
                // FIXME: 1 extra word
                break;
            case ABSOLUTE_LONG_ADDRESSING:
                // FIXME: 2 extra words
                break;
            case IMMEDIATE_VALUE:
                // FIXME: 1-6 extra words
                break;
            case NO_OPERAND: return null; // handled
        }
        throw new RuntimeException("Unhandled addressing mode: "+op.addressingMode);
    }
}