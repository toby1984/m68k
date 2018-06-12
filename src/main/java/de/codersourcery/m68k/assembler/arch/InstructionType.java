package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.OperandNode;

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
    MOVE("move",2, OperandSize.WORD,0b0000)
            {
                @Override
                public int getOperationCode(InstructionNode insn)
                {
                    switch( insn.getOperandSize() ) {
                        case BYTE: return 0b0001;
                        case WORD: return 0b0011;
                        case LONG: return 0b0010;
                    }
                    throw new RuntimeException("Unhandled switch/case: "+ insn.getOperandSize());
                }

                @Override
        public void checkSupports(Operand kind, OperandNode node)
        {
        }
    },
    LEA("lea",2, OperandSize.LONG,0b0100)
            {
        @Override
        public void checkSupports(Operand kind, OperandNode node)
        {
            if ( kind == Operand.DESTINATION)
            {
                if ( node.addressingMode != AddressingMode.ADDRESS_REGISTER_DIRECT )
                {
                    throw new RuntimeException("LEA needs an address register as destination");
                }
            }
            else if ( kind == Operand.SOURCE )
            {
                if ( node.addressingMode != AddressingMode.IMMEDIATE_ADDRESS ) {
                    throw new RuntimeException("LEA requires an immediate address value as source");
                }
            }
        }
    };

    public final OperandSize defaultOperandSize;
    private final String mnemonic;
    private final int operandCount;
    private final int operationMode; // bits 15-12 of first instruction word

    private InstructionType(String mnemonic, int operandCount, OperandSize defaultOperandSize,int operationMode)
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
        if ( value != null )
        {
            final String lValue = value.toLowerCase();
            for ( InstructionType t : InstructionType.values() ) {
                if ( t.mnemonic.equals( lValue ) ) {
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
            final Function<Field, Integer> func = field -> node.getValueFor(field,context);
            data = encoding.apply(func);
        }
        catch(Exception e)
        {
            context.error( e.getMessage() , node,e );
            return;
        }
        context.getCodeWriter().writeBytes( data );
    }

    /*
                case 's': return SRC_REGISTER;
                case 'm': return SRC_MODE;
                case 'D': return DST_REGISTER;
                case 'M': return DST_MODE;
                case 'S': return SIZE;
     */
    private static final InstructionEncoding MOVE_ENCODING = InstructionEncoding.of("ooooDDDMMMmmmsss");

    private static final InstructionEncoding LEA_ENCODING = InstructionEncoding.of("0100DDD111mmmsss");

    protected InstructionEncoding getEncoding(InstructionType type,InstructionNode node, ICompilationContext context)
    {
        OperandNode operand = node.source();
        if ( operand != null ) {
            type.checkSupports(Operand.SOURCE,operand);
        }
        operand = node.destination();
        if ( operand != null ) {
            type.checkSupports(Operand.DESTINATION,operand);
        }

        switch(type)
        {
            case LEA:  return LEA_ENCODING;
            case MOVE: return MOVE_ENCODING;
            default:
                throw new RuntimeException("Internal error,unhandled instruction type "+type);
        }
    }
}