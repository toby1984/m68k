package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.TextRegion;

public class NumberNode extends ASTNode implements IValueNode
{
    private final long value;
    private final NumberType type;

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
        String num;
        switch(type) {
            case DECIMAL:
                num = Long.toString(value);
                break;
            case BINARY:
                num = "%"+Long.toBinaryString(value);
                break;
            case HEXADECIMAL:
                num = "$"+Long.toHexString(value);
                break;
            default:
                throw new RuntimeException("Unhandled switch/case: "+type);
        }
        buffer.append(indent(depth)).append(type.toString()).append(" number [ ").append(num).append(" ]\n");
    }

    @Override
    public Integer getBits(ICompilationContext context) {
        return (int) value;
    }

    public static int getSizeInBits(int value)
    {
        if ( (value & 0xffffff00) == 0 || ( (value & 0xffffff00) == 0xffffff00 && ( (value & 0x00ff) !=  0 ) ) ) {
            return 8;
        }
        if ( (value & 0xffff0000) == 0 || ( (value & 0xffff0000) == 0xffff0000 && ( (value & 0xffff) !=  0 ) ) ) {
            return 16;
        }
        if ( (value & 0xffffffff00000000L) == 0 || ( (value & 0xffffffff00000000L) == 0xffffffff00000000L &&
            ( (value & 0xffffffff) !=  0 ) ) ) {
            return 32;
        }
        return 64;
    }
}