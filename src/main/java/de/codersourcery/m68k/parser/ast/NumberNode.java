package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TextRegion;

public class NumberNode extends ASTNode implements IValueNode
{
    private final long value;
    private final NumberType type;

    @Override
    public int getBits(int inputBits, int outputBits)
    {
        int input;
        switch(inputBits)
        {
            case 8:
                if ( value <-128 || value > 255 ) {
                    throw new IllegalStateException("Value not inside 8-bit range: "+value);
                }
                input = (byte) value;
                break;
            case 16:
                if ( value < -32768 || value > 65535) {
                    throw new IllegalStateException("Value not inside 16-bit range: "+value);
                }
                input = (short) value;
                break;
            case 32:
                if ( value < (1<<31) || value > 0x000000ffffffff ) {
                    throw new IllegalStateException("Value not inside 32-bit range: "+value);
                }
                input = (int) value;
                break;
            default:
                throw new IllegalArgumentException("Unsupported input bit count "+inputBits);
        }
        return signExtend(input,inputBits,outputBits);
    }

    public enum NumberType
    {
        DECIMAL,BINARY,HEXADECIMAL
    }

    public NumberNode(long value,NumberType type,TextRegion region)
    {
        super(NodeType.NUMBER, region);
        this.value = value;
        this.type = type;
    }

    public long getValue()
    {
        return value;
    }

    public static long parse(String s,NumberType type)
    {
        switch(type)
        {
            case BINARY:
                return Long.parseLong(s.substring(1), 2); // %1010101
            case DECIMAL:
                return Long.parseLong(s);
            case HEXADECIMAL:
                return Long.parseLong(s.substring(1),16); // $ff
            default:
                throw new RuntimeException("Unhandled switch/case: "+type);
        }
    }

    public static boolean isBinaryNumber(String s)
    {
        if (s != null && s.length() > 1 && s.charAt(0) == '%')
        {
            for (int i = s.length() - 1; i > 0; i--)
            {
                switch (s.charAt(i))
                {
                    case '0':
                    case '1':
                        break;
                    default:
                        return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean isHexNumber(String s)
    {
        if (s != null && s.length() > 1 && s.charAt(0) == '$')
        {
            for ( int i=s.length()-1 ; i> 0 ; i-- )
            {
                switch(s.charAt(i))
                {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                    case 'a':
                    case 'b':
                    case 'c':
                    case 'd':
                    case 'e':
                    case 'f':
                    case 'A':
                    case 'B':
                    case 'C':
                    case 'D':
                    case 'E':
                    case 'F':
                        break;
                    default:
                        return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append(type.toString()).append(" number [").append(value).append("]\n");
    }
}