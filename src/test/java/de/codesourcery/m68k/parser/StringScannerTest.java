package de.codesourcery.m68k.parser;

import junit.framework.TestCase;

import java.util.function.Consumer;

public class StringScannerTest extends TestCase
{
    private StringScanner scanner;

    @Override
    protected void setUp() throws Exception
    {
        scanner = null;
    }

    public void testNullFails() {
        try
        {
            new StringScanner(null);
            fail("Should've failed");
        } catch(NullPointerException e) {
            // ok
        }
    }

    private void assertOOBE(Consumer<IScanner>c)
    {
        try
        {
            c.accept(scanner);
            fail("Should've failed");
        } catch(StringIndexOutOfBoundsException e) {
            // ok
        }
    }

    public void testBlank()
    {
        scanner = new StringScanner("");
        assertEquals(0,scanner.offset());
        assertTrue( scanner.eof() );
        assertOOBE( s -> s.peek() );
        assertOOBE( s -> s.next() );
        assertEquals(0,scanner.offset());
    }

    public void testOneChar()
    {
        scanner = new StringScanner("x");
        assertEquals(0,scanner.offset());
        assertFalse( scanner.eof() );
        assertEquals('x',scanner.peek());
        assertEquals('x',scanner.next());
        assertTrue( scanner.eof() );
        assertEquals(1,scanner.offset());
        assertOOBE( s -> s.peek() );
        assertOOBE( s -> s.next() );
        assertEquals(1,scanner.offset());
    }
}
