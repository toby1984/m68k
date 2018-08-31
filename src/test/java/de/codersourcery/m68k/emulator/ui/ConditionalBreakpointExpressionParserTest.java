package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Amiga;
import de.codersourcery.m68k.emulator.CPU;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.IBreakpointCondition;
import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ConditionalBreakpointExpressionParserTest extends TestCase
{
    private Emulator emulator;

    @Override
    protected void setUp()
    {
        byte[] kickRom = new byte[ Amiga.AMIGA_500.getKickRomSize() ];
        emulator = new Emulator( Amiga.AMIGA_500,
                kickRom );
    }

    @Override
    protected void tearDown() throws Exception
    {
        if ( emulator != null )
        {
            emulator.destroy();
        }
    }

    public void testNullExpression()
    {
        assertTrue(parse(null).matches( emulator ));
    }

    public void testBlankExpression1()
    {
        assertTrue(parse("").matches( emulator ));
    }

    public void testBlankExpression2()
    {
        assertTrue(parse("   ").matches( emulator ));
    }

    public void testFailures()
    {
        assertParsingFails( "a8" );
        assertParsingFails( "a8=" );
        assertParsingFails( "d8" );
        assertParsingFails( "d8!=" );
        assertParsingFails( "a" );
        assertParsingFails( "d" );
        assertParsingFails( "d0==abc" );
        assertParsingFails( "sr==blubb" );
        assertParsingFails( "ccr==blubb" );
        assertParsingFails( "ssr" );
        assertParsingFails( "cr" );
    }

    public void testParseSuccess()
    {
        parse("a0=1");
        parse("d7!=1");
        parse("ccr=znv");
        parse("sr!=znv");
        parse("d0=$f000");
    }

    public void testConditions()
    {
        assertTrue( emu -> emu.cpu.addressRegisters[4] = 123 , parse( "a4=123" ) );
        assertFalse( emu -> emu.cpu.addressRegisters[4] = 123 , parse( "a4=$123" ) );
        assertTrue( emu -> emu.cpu.addressRegisters[4] = 0x123 , parse( "a4=$123" ) );

        assertTrue( emu -> emu.cpu.dataRegisters[4] = 123 , parse( "d4 = 123" ) );
        assertFalse( emu -> emu.cpu.dataRegisters[4] = 123 , parse( "d4 = $123" ) );
        assertTrue( emu -> emu.cpu.dataRegisters[4] = 0x123 , parse( "d4 = $123" ) );

        assertTrue( emu -> emu.cpu.statusRegister = CPU.FLAG_CARRY , parse( "sr = c" ) );
        assertFalse( emu -> emu.cpu.statusRegister = 0 , parse( "sr = c" ) );

        assertTrue( emu -> emu.cpu.statusRegister = CPU.FLAG_CARRY|CPU.FLAG_ZERO, parse( "sr = cz" ) );
        assertFalse( emu -> emu.cpu.statusRegister = CPU.FLAG_ZERO, parse( "sr = cz" ) );
        assertFalse( emu -> emu.cpu.statusRegister = CPU.FLAG_CARRY, parse( "sr = cz" ) );

        assertTrue( emu -> emu.cpu.statusRegister = CPU.FLAG_CARRY|CPU.FLAG_ZERO, parse( "ccr = cz" ) );
        assertFalse( emu -> emu.cpu.statusRegister = CPU.FLAG_ZERO, parse( "ccr = cz" ) );
        assertFalse( emu -> emu.cpu.statusRegister = CPU.FLAG_CARRY, parse( "ccr = cz" ) );
    }

    private void assertTrue(Consumer<Emulator> setupFunc, IBreakpointCondition condition) {
        assertTrue( "Condition should've evaluated to true", evaluate( setupFunc, condition ) );
    }

    private void assertFalse(Consumer<Emulator> setupFunc, IBreakpointCondition condition) {
        assertFalse( "Condition should've evaluated to false", evaluate( setupFunc, condition ) );
    }

    private boolean evaluate(Consumer<Emulator> setupFunc, IBreakpointCondition condition)
    {
        final AtomicBoolean result = new AtomicBoolean(false);

        final AtomicReference<RuntimeException> ex =
                new AtomicReference<>();

        emulator.runOnThread( () -> setupFunc.accept( emulator ),true );
        emulator.runOnThread( () -> {
            try {
                result.set( condition.matches( emulator ) );
            } catch(RuntimeException e) {
                ex.set( e );
            }
        } ,true );
        if ( ex.get() != null ) {
            throw ex.get();
        }
        return result.get();
    }

    private void assertParsingFails(String s) {
        try {
            parse(s);
            fail("Should've failed: "+s);
        } catch(IllegalArgumentException e) {
            // ok
        }
    }

    private IBreakpointCondition parse(String s) {
        return ConditionalBreakpointExpressionParser.parse( s );
    }

}
