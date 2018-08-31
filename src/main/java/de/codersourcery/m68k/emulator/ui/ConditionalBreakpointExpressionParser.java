package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.CPU;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.IBreakpointCondition;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ConditionalBreakpointExpressionParser
{
    private static final Pattern REGISTER_PATTERN =
            Pattern.compile("(a|d)(\\d)",Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN =
            Pattern.compile("\\s*(ccr|sr|a\\d|d\\d)\\s*(=|==|!=|<>)\\s*(\\$[0-9a-f]+|[0-9]+|[xcznv]+)\\s*",Pattern.CASE_INSENSITIVE);

    private enum Operator
    {
        EQUALS,NOT_EQUALS;
    }

    public static IBreakpointCondition parse(String s) throws IllegalArgumentException
    {
        if ( StringUtils.isBlank(s) )
        {
            return IBreakpointCondition.TRUE;
        }

        final Matcher m = PATTERN.matcher( s );

        if ( ! m.matches() ) {
            throw new IllegalArgumentException("Not a valid expression: "+s);
        }
        final String lhs = m.group(1);
        final Operator operator = parseOperator(m.group(2));
        final String rhs = m.group( 3 );

        final String normalized = lhs+operator+rhs;
        if ( isStatusRegister( lhs ) )
        {
            final int mask = flagStringToBitMask( rhs );
            switch(operator)
            {
                case EQUALS:
                    return emu -> (emu.cpu.statusRegister & mask) == mask;
                case NOT_EQUALS:
                    return emu -> (emu.cpu.statusRegister & mask) == mask;
            }
            throw new IllegalArgumentException( "Unsupported operator "+operator+" for status register" );
        }
        if ( ! isAddressOrDataRegister( lhs ) ) {
            throw new IllegalArgumentException( "Unrecognized LHS: '"+lhs+"'" );
        }
        final int value = parseNumber( rhs );
        final int regNum = parseRegisterNumber( lhs );
        if ( lhs.toLowerCase().startsWith("d") )
        {
            switch(operator)
            {
                case EQUALS:
                    return emu -> emu.cpu.dataRegisters[regNum] == value;
                case NOT_EQUALS:
                    return emu -> emu.cpu.dataRegisters[regNum] != value;
            }
            throw new IllegalArgumentException( "Unsupported operator "+operator+" for data register" );
        }
        if ( lhs.toLowerCase().startsWith("a") )
        {
            switch(operator)
            {
                case EQUALS:
                    return emu -> emu.cpu.addressRegisters[regNum] == value;
                case NOT_EQUALS:
                    return emu -> emu.cpu.addressRegisters[regNum] == value;
            }
            throw new IllegalArgumentException( "Unsupported operator "+operator+" for data register" );
        }
        throw new IllegalArgumentException( "Syntax error");
    }

    private static int flagStringToBitMask(String s)
    {
        if ( StringUtils.isBlank( s ) ) {
            throw new IllegalArgumentException( "need at least one CPU flag" );
        }
        int mask = 0;
        for ( char c : s.toLowerCase().toCharArray() )
        {
            switch(c) {
                case 'n': mask |= CPU.FLAG_NEGATIVE; break;
                case 'z': mask |= CPU.FLAG_ZERO; break;
                case 'v': mask |= CPU.FLAG_OVERFLOW; break;
                case 'c': mask |= CPU.FLAG_CARRY; break;
                case 'x': mask |= CPU.FLAG_EXTENDED; break;
                default:
                    throw new IllegalArgumentException( "Unknown flag '"+c+"'" );
            }
        }
        return mask;
    }

    private static boolean isStatusRegister(String s) {
        return "sr".equalsIgnoreCase( s ) || "ccr".equalsIgnoreCase( s );
    }

    private static boolean isAddressOrDataRegister(String expression) {

        final Matcher matcher = REGISTER_PATTERN.matcher( expression );
        if ( matcher.matches() )
        {
            final char firstChar = expression.toLowerCase().charAt(0);
            if ( firstChar == 'd' || firstChar == 'a' )
            {
                try
                {
                    parseRegisterNumber( expression );
                    return true;
                } catch(IllegalArgumentException e) {
                    // fall-through
                }
            }
        }
        return false;
    }

    private static int parseRegisterNumber(String expression)
    {
        final Matcher matcher = REGISTER_PATTERN.matcher( expression );
        if ( matcher.matches() )
        {
            final int regNum = Integer.parseInt(matcher.group(2));
            if ( regNum < 0 || regNum > 7) {
                throw new IllegalArgumentException( "Register number "+regNum+" is out of range 0...7" );

            }
            return regNum;
        }
        throw new IllegalArgumentException("Not a valid register number");
    }


    private static Operator parseOperator(String s)
    {
        switch(s)
        {
            case "=":
            case "==": return Operator.EQUALS;
            case "<>":
            case "!=": return Operator.NOT_EQUALS;
        }
        throw new IllegalArgumentException("Unknown operator: '"+s+"'");
    }

    private static int parseNumber(String number)
    {
        if ( number.startsWith("$") ) {
            return Integer.parseInt( number.substring( 1 ),16 );
        }
        return Integer.parseInt( number );
    }

}
