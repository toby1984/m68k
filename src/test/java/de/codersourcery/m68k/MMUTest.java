package de.codersourcery.m68k;

import de.codersourcery.m68k.emulator.Amiga;
import de.codersourcery.m68k.emulator.memory.Blitter;
import de.codersourcery.m68k.emulator.memory.DMAController;
import de.codersourcery.m68k.emulator.memory.MMU;
import de.codersourcery.m68k.emulator.memory.Memory;
import de.codersourcery.m68k.emulator.memory.Video;
import junit.framework.TestCase;

public class MMUTest extends TestCase
{
    private MMU mmu;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        final Amiga amiga = Amiga.AMIGA_500;
        final Blitter blitter = new Blitter(new DMAController());
        final Video video = new Video(amiga);
        this.mmu = new MMU(new MMU.PageFaultHandler(amiga, blitter, video ));
        final Memory memory = new Memory(this.mmu);
        blitter.setMemory( memory );
        video.setMemory( memory );
    }

    public void testGetOffsetInPage() {
        assertEquals( 4095, mmu.getOffsetInPage( 4095 ) );
        assertEquals( 128, mmu.getOffsetInPage( 128 ) );
        assertEquals( 1, mmu.getOffsetInPage( 2*MMU.PAGE_SIZE + 1 ) );
    }

    public void testGetPageNo() {
        assertEquals(0,mmu.getPageNo( 0 ));
        assertEquals(0,mmu.getPageNo( 4095 ));
        assertEquals(1,mmu.getPageNo( 4096 ));
        assertEquals(1,mmu.getPageNo( 4097 ));
    }

    public void testGetPageAddress() {
        assertEquals(0,mmu.getPageStartAddress( 0 ) );
        assertEquals(4096,mmu.getPageStartAddress( 1 ) );
    }
}
