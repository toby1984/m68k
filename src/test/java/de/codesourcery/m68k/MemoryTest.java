package de.codesourcery.m68k;

import de.codesourcery.m68k.emulator.Amiga;
import de.codesourcery.m68k.emulator.memory.Blitter;
import de.codesourcery.m68k.emulator.memory.DMAController;
import de.codesourcery.m68k.emulator.memory.MMU;
import de.codesourcery.m68k.emulator.memory.Memory;
import de.codesourcery.m68k.emulator.exceptions.BadAlignmentException;
import de.codesourcery.m68k.emulator.exceptions.MemoryWriteProtectedException;
import de.codesourcery.m68k.emulator.memory.Video;
import junit.framework.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

public class MemoryTest extends TestCase
{
    private static final Logger LOG = LogManager.getLogger( MemoryTest.class.getName() );

    private MMU mmu;
    private Memory memory;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        final Amiga amiga = Amiga.AMIGA_500;
        final DMAController dmaCtrl = new DMAController();
        final Blitter blitter = new Blitter( dmaCtrl );
        final Video video = new Video(amiga,blitter,dmaCtrl);
        mmu = new MMU( new MMU.PageFaultHandler(amiga, blitter, video ) );
        memory = new Memory(mmu);
        blitter.setMemory( memory );
        video.setMemory( memory );
    }

    public void testBadAlignment() {

        assertBadAlignment( () -> memory.readWord(1) );
        assertBadAlignment( () -> memory.writeWord(1 ,1 ) );
        assertBadAlignment( () -> memory.readLong(1) );
        assertBadAlignment( () -> memory.writeLong(1 , 1) );

    }
    public void testWriteProtect()
    {
        mmu.setWriteProtection( MMU.PAGE_SIZE, MMU.PAGE_SIZE, true );

        for (int i = MMU.PAGE_SIZE, len = MMU.PAGE_SIZE; len > 0; len--, i++)
        {
            final int idx = i;
            assertWriteProtected( () -> memory.writeByte( idx, 0x12 ) );
            assertEquals(0,memory.readByte(i) );
        }

        memory.writeLong(MMU.PAGE_SIZE-4, 0x12345678);
        assertEquals(0x12345678, memory.readLong( MMU.PAGE_SIZE-4 ) );

        memory.writeByte(MMU.PAGE_SIZE-1, 0x12);
        assertEquals(0x12, memory.readByte( MMU.PAGE_SIZE-1 ) );

        assertWriteProtected( () -> memory.writeLong(MMU.PAGE_SIZE-2,0x12345678) );
    }

    private void assertBadAlignment( Runnable operation)
    {
        try
        {
            operation.run();
            fail( "Should've failed" );
        }
        catch (BadAlignmentException e)
        {
            /* ok */
        }
    }

    private void assertWriteProtected( Runnable operation)
    {
        try
        {
            operation.run();
            fail( "Should've failed" );
        }
        catch (MemoryWriteProtectedException e)
        {
            /* ok */
        }
    }

    public void testWriteBytes()
    {
        final Random rnd = new Random();
        final byte[] data = new byte[MMU.PAGE_SIZE*10 + 31];
        rnd.nextBytes( data );
        memory.writeBytes( 0,data );
        for ( int i = 0 ; i < data.length ; i++ ) {
            assertEquals(data[i],memory.readByte( i ) );
        }
    }

    public void testBytes() {

        memory.writeByte(0,0x12 );
        assertEquals(0x12,memory.readByte(0 ) );

        memory.writeByte(1,0x34 );
        assertEquals(0x34,memory.readByte(1 ) );
    }

    public void testWords() {

        memory.writeWord(0, 0x1234 );
        LOG.info(  memory.hexdump( 0,16 )  );
        assertEquals(0x1234,memory.readWord(0 ) );

        memory.writeByte(0,0x12 );
        memory.writeByte(1,0x34 );

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

        LOG.info( "GOT: "+memory.hexdump(0,4 )  );

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

    public void testWriteLongPageBoundary()
    {
        int adr = MMU.PAGE_SIZE-2;
        memory.writeLong(adr,0x12345678);
        assertEquals(0x12345678,memory.readLong(adr));
    }

    private static void assertHexEquals(int expected,int actual) {
        assertEquals("Expected 0x"+Integer.toHexString(expected )+" but got 0x"+Integer.toHexString(actual),expected,actual);
    }

    private static void assertHexLong(long expected,long actual) {
        assertEquals("Expected 0x"+Long.toHexString(expected)+" but got 0x"+Long.toHexString(actual),expected,actual);
    }
}
