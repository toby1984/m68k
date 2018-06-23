package de.codersourcery.m68k;

import de.codersourcery.m68k.emulator.cpu.BadAlignmentException;
import de.codersourcery.m68k.emulator.cpu.MemoryAccessException;
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

        memory.writeByte(0,0x12 );
        memory.writeByte(1,0x34 );

        assertEquals(0x1234,memory.readWord(0 ) );

        memory.writeWord(0, 0x1234 );
        assertEquals(0x1234,memory.readWord(0 ) );

        memory.writeByte(1,0x12 );
        memory.writeByte(2,0x34 );
        assertEquals(0x1234,memory.readWordNoCheck(1 ) );

        try
        {
            memory.writeWord(1, 0x1234);
            fail("Should've failed");
        } catch(BadAlignmentException e) {
            // ok
        }
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

        memory.writeByte(1,0x12 );
        memory.writeByte(2,0x34 );
        memory.writeByte(3,0x56 );
        memory.writeByte(4,0x78 );
        assertHexEquals(0x12345678, memory.readLongNoCheck(1 ) );

        try
        {
            memory.writeLong(1, 0x12345678);
            fail("Should've failed");
        } catch(BadAlignmentException e) {
            // ok
        }
    }

    private static void assertHexEquals(int expected,int actual) {
        assertEquals("Expected 0x"+Integer.toHexString(expected )+" but got 0x"+Integer.toHexString(actual),expected,actual);
    }

    private static void assertHexLong(long expected,long actual) {
        assertEquals("Expected 0x"+Long.toHexString(expected)+" but got 0x"+Long.toHexString(actual),expected,actual);
    }
}
