package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.IValueNode;
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
                protected int getMaxOperandSize(InstructionNode insn, OperandNode operand)
                {
                    return 32;
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

                @Override
                protected int getMaxOperandSize(InstructionNode insn, OperandNode operand)
                {
                    return 32;
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
    private String[] getExtraWordPatterns(OperandNode op, boolean isSourceOperand,InstructionNode insn,ICompilationContext ctx)
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
                Field field = isSourceOperand ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
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
                field = isSourceOperand ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,16) };
            case ABSOLUTE_LONG_ADDRESSING:
                field = isSourceOperand ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,32) };
            case IMMEDIATE_VALUE:
                field = isSourceOperand ? Field.SRC_VALUE : Field.DST_VALUE;

                final int actualSizeInBits = checkOperandSize(insn,op,op.getValue());

                final int words;
                switch( actualSizeInBits )
                {
                    case 8:  words = 1; break;
                    case 16: words = 1; break;
                    case 32: words = 2; break;
                    default:
                        throw new RuntimeException("Unhandled operand size: "+actualSizeInBits);
                }
                return new String[] { StringUtils.repeat(field.c,words*16) };
            case NO_OPERAND: return null; // handled
        }
        throw new RuntimeException("Unhandled addressing mode: "+op.addressingMode);
    }

    private int checkOperandSize(InstructionNode insn, OperandNode op, IValueNode value)
    {
        int max = getMaxOperandSize(insn,op);
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

    protected abstract int getMaxOperandSize(InstructionNode insn,OperandNode operand);
}