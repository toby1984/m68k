package de.codesourcery.m68k.emulator.memory;

import de.codesourcery.m68k.emulator.Amiga;
import de.codesourcery.m68k.emulator.chips.IRQController;
import de.codesourcery.m68k.emulator.exceptions.MemoryAccessException;
import de.codesourcery.m68k.utils.Misc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class Video extends MemoryPage
{
    private static final Logger LOG = LogManager.getLogger( Video.class.getName() );

    /* Agnus's timings are measured in "color clocks" of 280 ns.
     * This is equivalent to two low resolution (140 ns) pixels or
     * four high resolution (70 ns) pixels.
     * Like Denise, these timings were designed for display on household TVs, and can
     * be synchronized to an external clock source.
     */
    public static final int BPL1PTH = 0x0E0; //  W   A       Bitplane 1 pointer (high 3 bits)
    public static final int BPL1PTL = 0x0E2; //  W   A       Bitplane 1 pointer (low 15 bits)
    public static final int BPL2PTH = 0x0E4; //  W   A       Bitplane 2 pointer (high 3 bits)
    public static final int BPL2PTL = 0x0E6; //  W   A       Bitplane 2 pointer (low 15 bits)
    public static final int BPL3PTH = 0x0E8; //  W   A       Bitplane 3 pointer (high 3 bits)
    public static final int BPL3PTL = 0x0EA; //  W   A       Bitplane 3 pointer (low 15 bits)
    public static final int BPL4PTH = 0x0EC; //  W   A       Bitplane 4 pointer (high 3 bits)
    public static final int BPL4PTL = 0x0EE; //  W   A       Bitplane 4 pointer (low 15 bits)
    public static final int BPL5PTH = 0x0F0; //  W   A       Bitplane 5 pointer (high 3 bits)
    public static final int BPL5PTL = 0x0F2; //  W   A       Bitplane 5 pointer (low 15 bits)
    public static final int BPL6PTH = 0x0F4; //  W   A       Bitplane 6 pointer (high 3 bits)
    public static final int BPL6PTL = 0x0F6; //  W   A       Bitplane 6 pointer (low 15 bits)

    public static final int BPLCON0= 0x100; //  W   AD( E ) Bitplane control register (misc. control bits)
    public static final int BPLCON1= 0x102; //  W   D       Bitplane control reg. (scroll value PF1, PF2)
    public static final int BPLCON2= 0x104; //  W   D( E )  Bitplane control reg. (priority control)
    public static final int BPLCON3= 0x106; //  W   D( E )  Bitplane control (enhanced features)

    public static final int BPL1MOD = 0x108; // W   A       Bitplane modulo (odd planes)
    public static final int BPL2MOD = 0x10A; // W   A       Bitplane modulo (even planes)

    public static final int BPL1DAT = 0x110; //  W   D       Bitplane 1 data (parallel-to-serial convert)
    public static final int BPL2DAT = 0x112; //  W   D       Bitplane 2 data (parallel-to-serial convert)
    public static final int BPL3DAT = 0x114; //  W   D       Bitplane 3 data (parallel-to-serial convert)
    public static final int BPL4DAT = 0x116; //  W   D       Bitplane 4 data (parallel-to-serial convert)
    public static final int BPL5DAT = 0x118; //  W   D       Bitplane 5 data (parallel-to-serial convert)
    public static final int BPL6DAT = 0x11A; //  W   D       Bitplane 6 data (parallel-to-serial convert)

    public static final int SPR0PTH  = 0x120; //  W   A       Sprite 0 pointer (high 3 bits)
    public static final int SPR0PTL  = 0x122; //  W   A       Sprite 0 pointer (low 15 bits)
    public static final int SPR1PTH  = 0x124; //  W   A       Sprite 1 pointer (high 3 bits)
    public static final int SPR1PTL  = 0x126; //  W   A       Sprite 1 pointer (low 15 bits)
    public static final int SPR2PTH  = 0x128; //  W   A       Sprite 2 pointer (high 3 bits)
    public static final int SPR2PTL  = 0x12A; //  W   A       Sprite 2 pointer (low 15 bits)
    public static final int SPR3PTH  = 0x12C; //  W   A       Sprite 3 pointer (high 3 bits)
    public static final int SPR3PTL  = 0x12E; //  W   A       Sprite 3 pointer (low 15 bits)
    public static final int SPR4PTH  = 0x130; //  W   A       Sprite 4 pointer (high 3 bits)
    public static final int SPR4PTL  = 0x132; //  W   A       Sprite 4 pointer (low 15 bits)
    public static final int SPR5PTH  = 0x134; //  W   A       Sprite 5 pointer (high 3 bits)
    public static final int SPR5PTL  = 0x136; //  W   A       Sprite 5 pointer (low 15 bits)
    public static final int SPR6PTH  = 0x138; //  W   A       Sprite 6 pointer (high 3 bits)
    public static final int SPR6PTL  = 0x13A; //  W   A       Sprite 6 pointer (low 15 bits)
    public static final int SPR7PTH  = 0x13C; //  W   A       Sprite 7 pointer (high 3 bits)
    public static final int SPR7PTL  = 0x13E; //  W   A       Sprite 7 pointer (low 15 bits)
    public static final int SPR0POS  = 0x140; //  W   AD      Sprite 0 vert-horiz start position data
    public static final int SPR0CTL  = 0x142; //  W   AD( E ) Sprite 0 vert stop position and control data
    public static final int SPR0DATA = 0x144; //  W   D       Sprite 0 image data register A
    public static final int SPR0DATB = 0x146; //  W   D       Sprite 0 image data register B
    public static final int SPR1POS  = 0x148; //  W   AD      Sprite 1 vert-horiz start position data
    public static final int SPR1CTL  = 0x14A; //  W   AD      Sprite 1 vert stop position and control data
    public static final int SPR1DATA = 0x14C; //  W   D       Sprite 1 image data register A
    public static final int SPR1DATB = 0x14E; //  W   D       Sprite 1 image data register B
    public static final int SPR2POS  = 0x150; //  W   AD      Sprite 2 vert-horiz start position data
    public static final int SPR2CTL  = 0x152; //  W   AD      Sprite 2 vert stop position and control data
    public static final int SPR2DATA = 0x154; //  W   D       Sprite 2 image data register A
    public static final int SPR2DATB = 0x156; //  W   D       Sprite 2 image data register B
    public static final int SPR3POS  = 0x158; //  W   AD      Sprite 3 vert-horiz start position data
    public static final int SPR3CTL  = 0x15A; //  W   AD      Sprite 3 vert stop position and control data
    public static final int SPR3DATA = 0x15C; //  W   D       Sprite 3 image data register A
    public static final int SPR3DATB = 0x15E; //  W   D       Sprite 3 image data register B
    public static final int SPR4POS  = 0x160; //  W   AD      Sprite 4 vert-horiz start position data
    public static final int SPR4CTL  = 0x162; //  W   AD      Sprite 4 vert stop position and control data
    public static final int SPR4DATA = 0x164; //  W   D       Sprite 4 image data register A
    public static final int SPR4DATB = 0x166; //  W   D       Sprite 4 image data register B
    public static final int SPR5POS  = 0x168; //  W   AD      Sprite 5 vert-horiz start position data
    public static final int SPR5CTL  = 0x16A; //  W   AD      Sprite 5 vert stop position and control data
    public static final int SPR5DATA = 0x16C; //  W   D       Sprite 5 image data register A
    public static final int SPR5DATB = 0x16E; //  W   D       Sprite 5 image data register B
    public static final int SPR6POS  = 0x170; //  W   AD      Sprite 6 vert-horiz start position data
    public static final int SPR6CTL  = 0x172; //  W   AD      Sprite 6 vert stop position and control data
    public static final int SPR6DATA = 0x174; //  W   D       Sprite 6 image data register A
    public static final int SPR6DATB = 0x176; //  W   D       Sprite 6 image data register B
    public static final int SPR7POS  = 0x178; //  W   AD      Sprite 7 vert-horiz start position data
    public static final int SPR7CTL  = 0x17A; //  W   AD      Sprite 7 vert stop position and control data
    public static final int SPR7DATA = 0x17C; //  W   D       Sprite 7 image data register A
    public static final int SPR7DATB = 0x17E; //  W   D       Sprite 7 image data register B

    // BIT# 15,14,13,12,11,10,09,08,07,06,05,04,03,02,01,00
    //  RGB  X  X  X  X  R3 R2 R1 R0 G3 G2 G1 G0 B3 B2 B1 B0

    public static final int COLOR00 = 0x180; //  W   D       Color table 00
    public static final int COLOR01 = 0x182; //  W   D       Color table 01
    public static final int COLOR02 = 0x184; //  W   D       Color table 02
    public static final int COLOR03 = 0x186; //  W   D       Color table 03
    public static final int COLOR04 = 0x188; //  W   D       Color table 04
    public static final int COLOR05 = 0x18A; //  W   D       Color table 05
    public static final int COLOR06 = 0x18C; //  W   D       Color table 06
    public static final int COLOR07 = 0x18E; //  W   D       Color table 07
    public static final int COLOR08 = 0x190; //  W   D       Color table 08
    public static final int COLOR09 = 0x192; //  W   D       Color table 09
    public static final int COLOR10 = 0x194; //  W   D       Color table 10
    public static final int COLOR11 = 0x196; //  W   D       Color table 11
    public static final int COLOR12 = 0x198; //  W   D       Color table 12
    public static final int COLOR13 = 0x19A; //  W   D       Color table 13
    public static final int COLOR14 = 0x19C; //  W   D       Color table 14
    public static final int COLOR15 = 0x19E; //  W   D       Color table 15
    public static final int COLOR16 = 0x1A0; //  W   D       Color table 16
    public static final int COLOR17 = 0x1A2; //  W   D       Color table 17
    public static final int COLOR18 = 0x1A4; //  W   D       Color table 18
    public static final int COLOR19 = 0x1A6; //  W   D       Color table 19
    public static final int COLOR20 = 0x1A8; //  W   D       Color table 20
    public static final int COLOR21 = 0x1AA; //  W   D       Color table 21
    public static final int COLOR22 = 0x1AC; //  W   D       Color table 22
    public static final int COLOR23 = 0x1AE; //  W   D       Color table 23
    public static final int COLOR24 = 0x1B0; //  W   D       Color table 24
    public static final int COLOR25 = 0x1B2; //  W   D       Color table 25
    public static final int COLOR26 = 0x1B4; //  W   D       Color table 26
    public static final int COLOR27 = 0x1B6; //  W   D       Color table 27
    public static final int COLOR28 = 0x1B8; //  W   D       Color table 28
    public static final int COLOR29 = 0x1BA; //  W   D       Color table 29
    public static final int COLOR30 = 0x1BC; //  W   D       Color table 30
    public static final int COLOR31 = 0x1BE; //  W   D       Color table 31

    public static final int HTOTAL = 0x1C0; //  W   A( E )  Highest number count, horiz line (VARBEAMEN=1)
    public static final int HSSTOP = 0x1C2; //  W   A( E )  Horizontal line position for HSYNC stop
    public static final int HBSTRT = 0x1C4; //  W   A( E )  Horizontal line position for HBLANK start
    public static final int HBSTOP = 0x1C6; //  W   A( E )  Horizontal line position for HBLANK stop
    public static final int VTOTAL = 0x1C8; //  W   A( E )  Highest numbered vertical line (VARBEAMEN=1)
    public static final int VSSTOP = 0x1CA; //  W   A( E )  Vertical line position for VSYNC stop
    public static final int VBSTRT = 0x1CC; //  W   A( E )  Vertical line for VBLANK start
    public static final int VBSTOP = 0x1CE; //  W   A( E )  Vertical line for VBLANK stop

    public static final int BEAMCON0 = 0x1DC; //  W   A( E )  Beam counter control register (SHRES,PAL)
    public static final int HSSTRT   = 0x1DE; //  W   A( E )  Horizontal sync start (VARHSY)
    public static final int VSSTRT   = 0x1E0; //  W   A( E )  Vertical sync start   (VARVSY)
    public static final int HCENTER  = 0x1E2; //  W   A( E )  Horizontal position for Vsync on interlace
    public static final int DIWHIGH  = 0x1E4; //  W   AD( E ) Display window -  upper bits for start, stop

    private final DMAController dmaController;
    private final Blitter blitter; // needed to copper can wait on blitter finished bit

    /*
These registers control the operation of the
                 bitplanes and various aspects of the display.

                 BIT#     BPLCON0    BPLCON1    BPLCON2
                 ----     --------   --------   --------
                 15       HIRES       X           X
                 14       BPU2        X           X
                 13       BPU1        X           X
                 12       BPU0        X           X
                 11       HOMOD       X           X
                 10       DBLPF       X           X
                 09       COLOR       X           X
                 08       GAUD        X           X
                 07        X         PF2H3        X
                 06        X         PF2H2      PF2PRI
                 05        X         PF2H1      PF2P2
                 04        X         PF2H0      PF2P1
                 03       LPEN       PF1H3      PF2P0
                 02       LACE       PF1H2      PF1P2
                 01       ERSY       PF1H1      PF1P1
                 00        X         PF1H0      PF1P0

               HIRES=High-resolution (70 ns pixels)
               BPU  =Bitplane use code 000-110 (NONE through 6 inclusive)
               HOMOD=Hold-and-modify mode (1 = Hold-and-modify mode (HAM);
                       0 = Extra Half Brite (EHB) if HAM=0 and BPU=6 and DBLPF=0 then
                       bitplane 6 controls an intensity reduction in the other five bitplanes)
               DBLPF=Double playfield (PF1=odd PF2=even bitplanes)
               COLOR=Composite video COLOR enable
               GAUD=Genlock audio enable (muxed on BKGND pin during vertical blanking
               LPEN =Light pen enable (reset on power up)
               LACE =Interlace enable (reset on power up)
               ERSY =External resync (HSYNC, VSYNC pads become inputs) (reset on power up)
               PF2PRI=Playfield 2 (even planes) has priority over (appears in front of) playfield 1 (odd planes).
               PF2P=Playfield 2 priority code (with respect to sprites)
               PF1P=Playfield 1 priority code (with respect to sprites)
               PF2H=Playfield 2 horizontal scroll code
               PF1H=Playfield 1 horizontal scroll code
     */

    public Memory memory;
    public IRQController irqController;
    public final Amiga amiga;

    public int[] bpldat = new int[6];

    private int ticksUntilVSync;
    private int ticksUntilVSyncLatch;

    public int bplcon0;
    public int bplcon1;
    public int bplcon2;
    public int bplcon3;

    public final int[] bplPointers = new int[6];
    public final int[] colors = new int[32];

    public int bpl1mod; // modulo for odd bitplanes
    public int bpl2mod; // modulo for even bitplanes

    private int vpos;
    private int hpos;

    // interlace frame marker bit
    private int longFrame; // long frame bit, either 0x8000 (long frame) or 0
    /*
"All lines are not the same length in NTSC.
Every other line is long line (228 color clocks, 0-$E3), with the others being 227 color clocks long.
In PAL, they are all 227 long. The display sees all these lines as 227 1/2 color clocks long, while the copper sees alternating long and short lines."
See http://amigadev.elowar.com/read/ADCD.../node004C.html
     */
    private int longLine; // LOL = Long line bit. When low, it indicates short raster line.

    public Video(Amiga amiga,Blitter blitter,DMAController dmaController) {
        this.amiga = amiga;
        this.blitter = blitter;
        this.dmaController = dmaController;
        reset();
    }

    public void reset()
    {
        if ( amiga.isPAL() ) {
            ticksUntilVSyncLatch= (int) ((amiga.getCPUClock()*1000000)/60.0);
        } else {
            ticksUntilVSyncLatch = (int) ((amiga.getCPUClock()*1000000)/50.0);
        }
        ticksUntilVSync = ticksUntilVSyncLatch;
        bplcon0=0;
        bplcon1=0;
        bplcon2=0;
        bplcon3=0;
        Arrays.fill(bplPointers,0);
        Arrays.fill(colors,0);
        Arrays.fill(bpldat,0);
        bpl1mod = 0;
        bpl2mod = 0;
        vpos = 0;
        longFrame = 0;
        longLine = amiga.isPAL() ? 0b1000_0000 : 0; // LOL = Long line bit. When low, it indicates short raster line.

        copper.reset();
    }

    @Override
    public byte readByte(int offset)
    {
        return readByteNoSideEffects(offset);
    }

    @Override
    public byte readByteNoSideEffects(int offset)
    {
        switch( offset ) {
            case 0x100: return hi(bplcon0);
            case 0x101: return lo(bplcon0);
            case 0x102: return hi(bplcon1);
            case 0x103: return lo(bplcon1);
            case 0x104: return hi(bplcon2);
            case 0x105: return lo(bplcon2);
            case 0x106: return hi(bplcon3);
            case 0x107: return lo(bplcon3);
            case 0x108: return hi(bpl1mod); // Bitplane modulo (odd planes)
            case 0x109: return lo(bpl1mod);
            case 0x10a: return hi(bpl2mod); // Bitplane modulo (even planes)
            case 0x10b: return lo(bpl2mod);
        }
        // color registers
        if ( offset >= 0x180 && offset <= 0x1bf) {
            final int idx = (offset-0x180)>>>1;
            final int color = colors[idx];
            return (offset&1)== 0 ? hi(color) : lo(color);
        }
        // bitplane pointers
        if ( offset >= 0x0e2 && offset <= 0x0f7) {
            final int idx = (offset-0x0e2)>>>2;
            final int ptr = bplPointers[idx];
            switch((offset-0x0e2) & 0b11) {
                case 0b00: return hi(ptr>>16);
                case 0b01: return lo(ptr>>16);
                case 0b10: return hi(ptr);
                case 0b11: return lo(ptr);
                default:
                    throw new RuntimeException("Unreachable code reached");
            }
        }
        // bit plane data registers
        if ( offset >= 0x110 && offset <= 0x11b ) {
            final int idx = (offset-0x110)>>>1;
            int value = bpldat[idx];
            if ( (offset & 1 ) == 0 ) {
                return hi(value);
            }
            return lo(value);
        }
        LOG.info( "VIDEO: Unhandled read from offset "+offset );
        return 0;
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {
        switch( offset ) {
            case 0x100: bplcon0 = chghi(bplcon0,value); return;
            case 0x101: bplcon0 = chglo(bplcon0,value); return;
            case 0x102: bplcon1 = chghi(bplcon1,value); return;
            case 0x103: bplcon1 = chglo(bplcon1,value); return;
            case 0x104: bplcon2 = chghi(bplcon2,value); return;
            case 0x105: bplcon2 = chglo(bplcon2,value); return;
            case 0x106: bplcon3 = chghi(bplcon3,value); return;
            case 0x107: bplcon3 = chglo(bplcon3,value); return;
            case 0x108: bpl1mod = chglo(bpl1mod,value); return;
            case 0x109: bpl1mod = chglo(bpl1mod,value); return;
            case 0x10a: bpl2mod = chglo(bpl2mod,value); return;
            case 0x10b: bpl2mod = chglo(bpl2mod,value); return;
        }
        // color registers
        if ( offset >= 0x180 && offset <= 0x1bf) {
            final int idx = (offset-0x180)>>>1;
            colors[idx] = (offset&1) == 0 ? chghi( colors[idx],value) : chglo( colors[idx],value);
            return;
        }
        // bitplane pointers**
        if ( offset >= 0x0e2 && offset <= 0x0f7) {
            final int idx = (offset-0x0e2)>>>2;
            switch((offset-0x0e2) & 0b11) {
                case 0b00: bplPointers[idx] = (bplPointers[idx] & 0x00ffffff) | ((value<<24) & 0xff000000); break;
                case 0b01: bplPointers[idx] = (bplPointers[idx] & 0xff00ffff) | ((value<<16) & 0x00ff0000); break;
                case 0b10: bplPointers[idx] = (bplPointers[idx] & 0xffff00ff) | ((value<< 8) & 0x0000ff00); break;
                case 0b11: bplPointers[idx] = (bplPointers[idx] & 0xffffff00) | ((value    ) & 0x000000ff); break;
                default:
                    throw new RuntimeException("Unreachable code reached");
            }
            return;
        }
        // bit plane data registers
        if ( offset >= 0x110 && offset <= 0x11b ) {
            final int idx = (offset-0x110)>>>1;
            if ( (offset &1) == 0 ) {
                bpldat[idx] = chghi(bpldat[idx],value);
            } else {
                bpldat[idx] = chglo(bpldat[idx],value);
            }
            return;
        }
        LOG.info( "VIDEO: Unhandled write to offset "+offset );
    }

    @Override
    public void writeWord(int offset, int value) throws MemoryAccessException
    {
        /*
         * COPPER:
         *
         * COPCON      *02E  W   A( E )  Coprocessor control register (CDANG)
         * COP1LCH   +  080  W   A( E )  Coprocessor first location register (high 3 bits, high 5 bits if ECS)
         * COP1LCL   +  082  W   A       Coprocessor first location register (low 15 bits)
         * COP2LCH   +  084  W   A( E )  Coprocessor second location register (high 3 bits, high 5 bits if ECS)
         * COP2LCL   +  086  W   A       Coprocessor second location register (low 15 bits)
         * COPJMP1      088  S   A       Coprocessor restart at first location
         * COPJMP2      08A  S   A       Coprocessor restart at second location
         * COPINS       08C  W   A       Coprocessor instruction fetch identify
         */
        switch( offset )
        {
            case 0x2e: // COPCON
                copper.copperDanger = (value & 0b10) != 0; // bit 1 = Copper Danger bit
                return;
            case 0x80: // COP1LCH
                copper.list1Addr = (copper.list1Addr & 0x0000ffff) | ((value & 0xffff) << 16);
                return;
            case 0x82: // COP1LCL
                copper.list1Addr = (copper.list1Addr & 0xffff0000) | (value & 0xffff);
                return;
            case 0x84: // COP2LCH
                copper.list2Addr = (copper.list2Addr & 0x0000ffff) | ((value & 0xffff) << 16);
                return;
            case 0x86: // COP2LCL
                copper.list2Addr = (copper.list2Addr & 0xffff0000) | (value & 0xffff);
                return;
            case 0x88: // COPJMP1
                copper.pc = copper.list1Addr;
                return;
            case 0x8a: // COPJMP2
                copper.pc = copper.list2Addr;
                return;
            case 0x8c: // COPINS
                // TODO: Does writing here have any effect ?
                return;
            case 0x100: bplcon0 = value; return;
            case 0x102: bplcon1 = value; return;
            case 0x104: bplcon2 = value; return;
            case 0x106: bplcon3 = value; return;
            case 0x108: bpl1mod = value & 0xffff; return;
            case 0x10a: bpl2mod = value & 0xffff; return;
        }
        // color registers
        if ( offset >= 0x180 && offset <= 0x1bf) {
            final int idx = (offset-0x180)>>>1;
            colors[idx] = value;
            return;
        }
        // bitplane pointers**
        if ( offset >= 0x0e2 && offset <= 0x0f7) {
            final int idx = (offset-0x0e2)>>>2;
            switch((offset-0x0e2) & 0b10) {
                case 0b00: bplPointers[idx] = (bplPointers[idx] & 0x0000ffff) | ((value<<16) & 0xffff0000); break;
                case 0b10: bplPointers[idx] = (bplPointers[idx] & 0xffff0000) | ((value    ) & 0x0000ffff); break;
                default:
                    throw new RuntimeException("Unreachable code reached");
            }
            return;
        }
        // bit plane data registers
        if ( offset >= 0x110 && offset <= 0x11b ) {
            final int idx = (offset-0x110)>>>1;
            bpldat[idx] = value;
            return;
        }
        LOG.info( "VIDEO: Word write access to unhandled register "+ Misc.hex(offset) );
    }

    private static byte hi(int value) {
        return (byte)(value>>>8);
    }

    private static byte lo(int value) {
        return (byte)(value);
    }

    private static int chghi(int originalValue,int hiValue) {
        return (originalValue & 0x00ff) | ((hiValue<<8) & 0xff00);
    }

    private static int chglo(int originalValue,int hiValue) {
        return (originalValue & 0xff00) | (hiValue & 0xff);
    }

    /* In low resolution mode, the normal playfield has a width of 320 pixels.
     *
     * High resolution mode gives finer horizontal resolution -- 640 pixels
     *
     * In non-interlaced mode, the normal NTSC playfield has a height of 200
     * video lines.   The normal PAL screen has a height of 256 video lines.
     *
     * Interlaced mode gives finer vertical resolution -- lines in the same
     * physical display area in NTSC and 512 for PAL.
     */

    /**
     * Returns the configured display width in pixels.
     * @return
     */
    public int getDisplayWidth() {
        return isHiRes() ? 640 : 320;
    }

    /**
     * Writes the current screen content as ARGB into
     * an int array.
     *
     * The array size needs to match the current display resolution,otherwise
     * AIOOBE will come and get you.
     *
     * @param destination
     */
    public void convertDisplayData(int[] destination,boolean dmaEnabled)
    {
        // TODO: Add HAM support
        // TODO: Add support for horizontal scrolling
        // TODO: Add support for dual playfields / playfield priorities / transparency

        final int[] ptrs = Arrays.copyOf(bplPointers,6);
        final int[] colorIndex = new int[16];
        int bitplaneCount = (bplcon0 & 0b0111_0000_0000_0000) >>> 12;
        if ( ! dmaEnabled )
        {
            // TODO: Hack to "do the right thing"...need to find documentation about
            //       behaviour when DMA is off and/or bitplaneCount == 0
            bitplaneCount = 6;
        }

        // convert 4-bit RGB into 8-bit
        final boolean isEHB = isEHB();
        final int[] rgbColors = new int[isEHB ? 64: 32];
        for ( int i = 0 ; i < 32 ; i++)
        {
            final int color = colors[i];
            final int r = ((color >> 8) & 0b1111); // 0..15
            final int g = ((color >> 4) & 0b1111);
            final int b = ((color     ) & 0b1111);
            rgbColors[i]    = (r << 4) << 16 | (g << 4) << 8 | (b << 4);
            if ( isEHB ) {
                rgbColors[16+i] = (r << 2) << 16 | (g << 2) << 8 | (b << 2);
            }
        }

        // now convert the image data
        int dstPtr = 0;
        for ( int y = 0, maxY = getDisplayHeight() ; y < maxY ; y++)
        {
            for (int x = 0, maxX = getDisplayWidth(); x < maxX; x+=16) // operate on words
            {
                // reset colors as we're only going to OR '1'
                // bits (assume that all bits are 0 at the beginning)
                Arrays.fill(colorIndex,0);
                for ( int i = 0 ; i < bitplaneCount ; i++ )
                {
                    // read 16 bits (=pixels) from the current bit plane
                    final int data = dmaEnabled ? memory.readWord(ptrs[i]) : bpldat[i];

                    // advance to next word
                    ptrs[i] += 2;
                    // now shift-in color idx bits starting with the MSB (left-most) one
                    int mask = 1<<15;
                    for ( int col = 0 ; col < 16 ; col++, mask >>>=1 ) {
                        colorIndex[col] <<= 1;
                        if ( (data & mask) != 0 ) {
                            colorIndex[col] |= 1;
                        }
                    }
                }
                // now set 16 pixels in destination
                for ( int idx = 0 ; idx < 16 ; idx++ )
                {
                    destination[dstPtr++] = rgbColors[colorIndex[idx]];
                }
            }
            // add modulo to bitplane pointers
            // to advance to the next line
            final int evenMod = bpl2mod;
            final int oddMod = bpl1mod;
            if ( evenMod != 0 || oddMod != 0 )
            {
                ptrs[0] += evenMod; // even bitplane
                ptrs[1] += oddMod;  // odd bitplane
                ptrs[2] += evenMod; // even bitplane
                ptrs[3] += oddMod;  // odd bitplane
                ptrs[4] += evenMod; // even bitplane
                ptrs[5] += oddMod;  // odd bitplane
            }
        }
    }

    /**
     * Returns the configured display height in pixels.
     * @return
     */
    public int getDisplayHeight()
    {
        if ( amiga.isPAL() ) {
            return isInterlaced() ? 512 : 256;
        }
        return isInterlaced() ? 400 : 200;
    }

    /**
     * Ticked every 140ns (=2 hi-res pixel,1 lo-res pixel)
     */
    public void tick()
    {
        if ( --ticksUntilVSync == 0 )
        {
            ticksUntilVSync = ticksUntilVSyncLatch;
            copper.restart();
            irqController.triggerIRQ( IRQController.IRQSource.VBLANK );
        }
        if ( dmaController.isCopperDMAEnabled() )
        {
            copper.tick();
        }

        hpos++;
        if ( hpos == 0xd8 ) { // $d8 = 216 = 8 pixel hblank + 200 pixel + 8 pixel hblank
            hpos = 0;

            if ( amiga.isNTSC() )
            {
                // toggle long/short line flag, only applicable for NTSC amigas
                longLine ^= 0b1000_0000;
            }

            vpos++;
                /*
    | Normal  | Interlaced
PAL |  283    |     567
NTSC|  241    |     483
                 */

            if ( isInterlaced() )
            {
                final int maxY = amiga.isPAL() ? 283 : 241;
                if ( vpos >= maxY )
                {
                    longFrame = 0x8000;
                }
            }

            int maxY;
            if ( amiga.isPAL() )
            {
                maxY = 29 + (isInterlaced() ? 567 : 283);
            }
            else
            {
                maxY = 21 + (isInterlaced() ? 483 : 241);
            }
            if ( vpos == maxY ) {
                vpos = 0;
                longFrame = 0;
            }
        }
    }

    public boolean isHiRes() {
        return (bplcon0 & 1<<15) != 0;
    }

    public boolean isHAM() {
        return (bplcon0 & 1<<11) != 0;
    }

    public boolean isEHB() {
        return (bplcon0 & 0b0111_1100_0000_0000) == 0b0111_0000_0000_0000;
    }

    public boolean isInterlaced() {
        return (bplcon0 & 1<<2) != 0;
    }

    public void setMemory(Memory memory)
    {
        this.memory = memory;
    }

    public void setIRQController(IRQController irqController)
    {
        this.irqController = irqController;
    }

    /*
[ ] VPOSR       *004  R   A( E )  Read vert most signif. bit (and frame flop)
[ ] VHPOSR      *006  R   A       Read vert and horiz. position of beam
     */
    public int readVPOSR()
    {
        return longFrame | amiga.getAgnusID() << 8 | ( vpos & 0b111_0000_0000) >> 8;
    }

    public int readVHPOSR() {
        return (vpos<< 8 ) | ((hpos >> 1) & 0xff);
    }

    /*
The minimum time of vertical blanking is 20 horizontal scan lines for an
NTSC system and 25 horizontal scan lines for a PAL system. The range
starts at line 0 and ends at line 20 for NTSC or line 25 for PAL. After
the minimum vertical blanking range, you can control where the display
actually starts by using the  DIWSTRT  (display window start) register
to extend the effective vertical blanking time. See Chapter 3, "Playfield
Hardware," for more information on  DIWSTRT .

If you find that you still require additional time during vertical
blanking, you can use the Copper to create a  level 3 interrupt . This
Copper interrupt would be timed to occur just after the last line of
display on the screen (after the display window stop which you have
defined by using the  DIWSTOP  register).

0      $18                           $D8
+-------+-----------------------------+------+ 0
|       |   VBLANK (mandatory)        |      |
|       |                             |      | 21 (NTSC) or 29 (PAL) lines
+-------+-----------------------------+------+
|       |                             |      |
| HBLANK|     DISPLAY AREA            |HBLANK|
|       |                             |      |
+-------+-----------------------------+------+

Displayable lines of video:

    | Normal  | Interlaced
PAL |  283    |     567
NTSC|  241    |     483

Horizontally, the situation is similar. Strictly speaking, the hardware
sets a rightmost limit to  DDFSTOP  of ($D8) and a leftmost limit to
 DDFSTRT  of ($18). This gives a maximum of 25 words fetched in low
resolution mode. In high resolution mode the maximum here is 49 words,
because the rightmost limit remains ($D8) and only one word is fetched at
this limit. However, horizontal blanking actually limits the displayable
video to 368 low resolution pixels (23 words). These numbers are the same
both for NTSC and for PAL. In addition, it should be noted that using a
data-fetch start earlier than ($38) will disable some  sprites .


        Table 3-14: Maximum Allowable Horizontal Screen Video

                                   Lores            Hires
                                   -----            -----
         DDFSTRT  (standard)       $0038            $003C
         DDFSTOP  (standard)       $00D0            $00D4

         DDFSTRT  (hw limits)      $0018            $0018
         DDFSTOP  (hw limits)      $00D8            $00D8

         max words fetched         25               49
         max display pixels        368 (low res)

On the Amiga computer, the timing of a pixel was defined as 140 ns for Lores modes,
70 ns for Hires, and 35 ns for Super Hires (AGA).
Dividing the duration of the visible duration of a scanline (52 µs) by the duration of a single pixel
thus results in 742 Hires pixels that may be visible on a standard-compliant
NTSC or PAL TV.
While some Amiga components can in theory process 768 (Hires) pixels per line,
extensive practical tests (source: Toni Wilen) have led to the conclusion that
no more than 752 pixels can be rendered horizontally in Hires modes.
This also includes the maximum boundaries of sprites and copper effects.

When describing Amiga clipping offsets, these units are always relative to a
starting position which starts on the first line from the top after the
vertical blanking offset (42 interlaced lines for NTSC, 52 for PAL), and
considering a left offset of (768-752)/2=8 Hires (16 Superhires) "unused" pixels.
     */

    public final Copper copper = new Copper();

    public enum CopperInstruction
    {
        MOVE
                {
                    @Override
                    public boolean perform(Video video)
                    {
                        /*
                         * MOVE instruction.
                         *
                         * FIRST MOVE INSTRUCTION WORD (IR1)
                         * ---------------------------------
                         * Bit 0           Always set to 0.
                         * Bits 8 - 1      Register destination address (DA8-1).
                         * Bits 15 - 9     Not used, but should be set to 0.
                         *
                         * SECOND MOVE INSTRUCTION WORD (IR2)
                         * ----------------------------------
                         * Bits 15 - 0     16 bits of data to be transferred (moved) to the register destination.
                         */

                        /*
                         * The registers that the Copper can always affect are numbered
                         * $80 through $FF inclusive. Those it cannot affect at all are numbered
                         * $00 to $3E inclusive.
                         * The Copper control register is within this group ($00 to $3E).
                         * The rest of the registers, from $40 to $7E, are protected by the COPDANG bit.
                         */
                        final int offset = (video.copper.word1 & 0b111111110);
                        final int adr = 0xDFF000 + offset;
                        if ( offset > 0x3e && (offset >= 0x80 || video.copper.copperDanger) )
                        {
                            video.memory.writeWord( adr, video.copper.word2 );
                        } else {
                            LOG.warn("Invalid copper write to register "+Misc.hex(adr));
                        }
                        return true;
                    }
                },
        WAIT {
            @Override
            public boolean perform(Video video)
            {
                /*
                 * WAIT instruction.
                 *
                 * FIRST WAIT INSTRUCTION WORD (IR1)
                 * ---------------------------------
                 * Bit 0           Always set to 1.
                 * Bits 15 - 8      Vertical beam position  (called VP).
                 * Bits 7 - 1       Horizontal beam position  (called HP).
                 *
                 * SECOND WAIT INSTRUCTION WORD (IR2)
                 * ----------------------------------
                 * Bit 0           Always set to 0.
                 * Bit 15          The  blitter-finished-disable bit .  Normally, this bit is a 1. (See the "Advanced Topics" section below.)
                 * Bits 14 - 8     Vertical position compare enable bits (called VE).
                 * Bits 7 - 1      Horizontal position compare enable bits (called HE).
                 */
                int expectedHpos = (video.copper.word1 & 0b11111110)>>>1;
                int expectedVpos = (video.copper.word1 & 0b11111111_00000000)>>>8;
                expectedVpos &= ( (video.copper.word2 & 0b1111111_00000000) >>>8);
                expectedHpos &= ( (video.copper.word2 & 0b1111110) >>>1);
                if ( ( video.copper.word2 & 1<<15) == 0 && ! video.blitter.blitterDone )
                {
                    // wait for blitter done
                    return false;
                }
                return video.vpos >= expectedVpos && video.hpos >= expectedHpos;
            }
        },
        SKIP {
            @Override
            public boolean perform(Video video)
            {
                /*
                 * SKIP instruction.
                 *
                 * FIRST SKIP INSTRUCTION WORD (IR1)
                 * ---------------------------------
                 * Bit 0           Always set to 1.
                 * Bits 15 - 8     Vertical position  (called VP).
                 * Bits 7 - 1      Horizontal position  (called HP).
                 *
                 *                 Skip if the beam counter is equal to or
                 *                 greater than these combined bits
                 *                 (bits 15 through 1).
                 *
                 *
                 * SECOND SKIP INSTRUCTION WORD (IR2)
                 * ----------------------------------
                 * Bit 0           Always set to 1.
                 * Bit 15          The  blitter-finished-disable bit . (See "Using the Copper with the Blitter" below.)
                 * Bits 14 - 8     Vertical position compare enable bits (called VE).
                 * Bits 7 - 1      Horizontal position compare enable bits (called HE).
                 */
                int expectedHpos = (video.copper.word1 & 0b11111110)>>>1;
                int expectedVpos = (video.copper.word1 & 0b11111111_00000000)>>>8;
                expectedVpos &= ( (video.copper.word2 & 0b1111111_00000000) >>>8);
                expectedHpos &= ( (video.copper.word2 & 0b1111110) >>>1);
                if ( ( video.copper.word2 & 1<<15) == 0 && ! video.blitter.blitterDone)
                {
                    // wait for blitter done
                    return false;
                }
                if ( video.vpos >= expectedVpos && video.hpos >= expectedHpos ) {
                    video.copper.pc += 4;
                    return true;
                }
                return false;
            }
        };

        /**
         * Execute copper instruction.
         *
         * @param video
         * @return true if instruction has completed
         */
        public abstract boolean perform(Video video);
    }

    public final class Copper
    {
        public int pc;
        public int cycles;
        public boolean list1Active;

        public int list1Addr;
        public int list2Addr;

        public int word1;
        public int word2;

        public boolean copperDanger;

        public CopperInstruction currentInstruction;

        public void reset()
        {
            pc = 0;
            cycles = 1;
            list1Active = true;
            list1Addr = list2Addr;
            word1 = word2 = 0;
            copperDanger = false;
        }

        public void restart()
        {
            pc = list1Active ? list1Addr : list2Addr;
        }

        public void tick()
        {
            if ( --cycles == 0 )
            {
                if ( currentInstruction != null )
                {
                    if ( ! currentInstruction.perform(Video.this) ) {
                        // copper only gets every 2nd cycle
                        cycles = 2;
                        return;
                    }
                }
                // fetch next instruction
                final int words = memory.readLong( pc & ~1 );
                pc += 4;
                word1 = words >>> 16;
                word2 = words & 0xffff;

                if ( (word1 & 1 ) == 0 )
                {
                    // MOVE instruction.
                    // Bit 0 in IR1 => Always set to 0.
                    currentInstruction = CopperInstruction.MOVE;
                    cycles = 6;
                    return;
                }
                // bit 0 of IR1 is 1, test bit 0 of IR2
                if ( (word2 & 1) == 0 )
                {
                    /*
                     * WAIT instruction.
                     * Bit 0 in IR1 => Always set to 1.
                     * Bit 0 in IR2 => Always set to 0.
                     */
                    currentInstruction = CopperInstruction.WAIT;
                    cycles = 6;
                }
                else
                {
                    /*
                     * SKIP instruction.
                     * Bit 0           Always set to 1.
                     * Bit 0           Always set to 1.
                     */
                    currentInstruction = CopperInstruction.SKIP;
                    cycles = 6;
                }
            }
        }
    }
}