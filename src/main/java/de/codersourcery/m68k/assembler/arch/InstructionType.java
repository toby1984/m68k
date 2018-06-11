package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.OperandNode;

import java.util.function.Function;

public enum InstructionType
{
    MOVE("move",2, OperandSize.WORD)
            {
        @Override
        public void checkSupports(Operand kind, OperandNode node)
        {
        }
    },
    LEA("lea",2, OperandSize.LONG)
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

    private InstructionType(String mnemonic, int operandCount, OperandSize defaultOperandSize)
    {
        this.mnemonic = mnemonic.toLowerCase();
        this.operandCount = operandCount;
        this.defaultOperandSize = defaultOperandSize;
    }

    public abstract void checkSupports(Operand kind, OperandNode node);

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
    private static final InstructionEncoding MOVE_BYTE = InstructionEncoding.of("0001DDDMMMmmmsss");
    private static final InstructionEncoding MOVE_LONG = InstructionEncoding.of("0010DDDMMMmmmsss");
    private static final InstructionEncoding MOVE_WORD = InstructionEncoding.of("0011DDDMMMmmmsss");
    private static final InstructionEncoding MOVE_SRC_MEM_INDIREC =
            InstructionEncoding.of("0010DDDMMMmmmsss");

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
            case LEA:
                return LEA_ENCODING;
            case MOVE:

                final int sizeBits = node.getValueFor(Field.SIZE, context);
                if ( sizeBits == OperandSize.BYTE.bits ) {
                    return MOVE_BYTE;
                }
                if ( sizeBits == OperandSize.WORD.bits ) {
                    return MOVE_WORD;
                }
                if ( sizeBits == OperandSize.LONG.bits ) {
                    return MOVE_LONG;
                }
                throw new RuntimeException("Internal error, size "+sizeBits);
            default:
                throw new RuntimeException("Internal error,unhandled instruction type "+type);
        }
    }
}