package de.codersourcery.m68k;

import junit.framework.TestCase;

public class MemoryTest extends TestCase
{
    private Memory memory;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        memory = new Memory(1024);
    }

    public void testBytes() {

        memory.writeByte(0,0x12 );
        assertEquals(0x12,memory.readByte(0 ) );

        memory.writeByte(1,0x34 );
        assertEquals(0x34,memory.readByte(1 ) );
    }

    public void testWords() {

        memory.writeByte(0,0x34 );
        memory.writeByte(1,0x12 );

        assertEquals(0x1234,memory.readWord(0 ) );

        memory.writeWord(0, 0x1234 );
        assertEquals(0x1234,memory.readWord(0 ) );

        memory.writeByte(1,0x12 );
        memory.writeByte(2,0x34 );
        assertEquals(0x1234,memory.readWord(1 ) );

        memory.writeWord(1, 0x1234 );
        assertEquals(0x1234,memory.readWord(1 ) );
    }

    public void testLongs() {

        memory.writeByte(0,0x12 );
        memory.writeByte(1,0x34 );
        memory.writeByte(2,0x56 );
        memory.writeByte(3,0x78 );

        System.out.println("GOT: "+memory.hexdump(0,4 ) );

        final int actual = memory.readLong(0);
        assertHexEquals(0x12345678, actual);

        memory.writeLong(0, 0x12345678 );
        assertHexEquals(0x12345678,memory.readLong(0 ) );

        memory.writeByte(1,0x78 );
        memory.writeByte(2,0x56 );
        memory.writeByte(3,0x34 );
        memory.writeByte(4,0x12 );

        assertHexEquals(0x12345678, memory.readLong(1 ) );

        memory.writeLong(1, 0x12345678 );
        assertHexEquals(0x12345678,memory.readLong(1 ) );
    }

    private static void assertHexEquals(int expected,int actual) {
        assertEquals("Expected 0x"+Integer.toHexString(expected )+" but got 0x"+Integer.toHexString(actual),expected,actual);
    }

    private static void assertHexLong(long expected,long actual) {
        assertEquals("Expected 0x"+Long.toHexString(expected)+" but got 0x"+Long.toHexString(actual),expected,actual);
    }
}
