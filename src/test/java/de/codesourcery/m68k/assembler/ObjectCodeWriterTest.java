package de.codesourcery.m68k.assembler;

import junit.framework.TestCase;

public class ObjectCodeWriterTest extends TestCase
{
    private ObjectCodeWriter writer = new ObjectCodeWriter(1);

    public void testEmpty()
    {
        assertEquals(0, writer.offset() );
        assertEquals(0,writer.getBytes(true).length);
    }

    public void testAllocate() {
        writer.allocateBytes( 10 );
        assertEquals( 10,writer.offset() );
        org.junit.Assert.assertArrayEquals(new byte[]{0,0,0,0,0,0,0,0,0,0},writer.getBytes(true));
    }

    public void testSetStartOnEmptyBuffer()
    {
        writer.setOffset( 10 );
        assertEquals(10, writer.offset() );
        assertEquals(0,writer.getBytes(true).length);
    }

    public void testWriteWord() {
        writer.writeWord( 0x1234 );
        assertEquals(2,writer.offset());
        org.junit.Assert.assertArrayEquals(new byte[]{0x12,0x34},writer.getBytes(true));
    }

    public void testWriteLong() {
        writer.writeLong( 0x12345678 );
        assertEquals(4,writer.offset());
        org.junit.Assert.assertArrayEquals(new byte[]{0x12,0x34,0x56,0x78},writer.getBytes(true));
    }

    public void testGoingBackwardsFails() {
        writer.setOffset( 10 );
        writer.writeByte( 1 );
        try {
            writer.setOffset( 10 );
            fail("Should've failed");
        } catch(IllegalStateException e ) {
            // ok
        }
    }

    public void testChangingOffsetOnEmptyBufferIsOk()
    {
        writer.setOffset( 10 );
        writer.setOffset( 5 );
        writer.setOffset( 0 );
        writer.writeByte( 1 );
        org.junit.Assert.assertArrayEquals(new byte[]{1},writer.getBytes(true));
    }

    public void testSetStartOnNonEmptyBuffer()
    {
        writer.setOffset( 10 );
        writer.writeByte( 1 );
        writer.setOffset( 11 );
        writer.writeByte(2);
        assertEquals(12, writer.offset() );
        org.junit.Assert.assertArrayEquals(new byte[]{0,0,0,0,0,0,0,0,0,0,1,2},writer.getBytes(true));
    }
}
