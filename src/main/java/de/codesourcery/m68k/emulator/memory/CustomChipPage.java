package de.codesourcery.m68k.emulator.memory;

import de.codesourcery.m68k.disassembler.ChipRegisterResolver;
import de.codesourcery.m68k.disassembler.RegisterDescription;
import de.codesourcery.m68k.emulator.chips.IRQController;
import de.codesourcery.m68k.emulator.exceptions.MemoryAccessException;
import de.codesourcery.m68k.utils.Misc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Address range containing all custom-chip registers.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CustomChipPage extends MemoryPage
{
    private static final Logger LOG = LogManager.getLogger( CustomChipPage.class.getName() );

    private static final int CHIPRAM_START = 0xDF0000;

    private static final ChipRegisterResolver regResolver =
            new ChipRegisterResolver( null ); // TODO: Debug only

    private final Blitter blitter;
    private final int startAddress;

    /*
See http://amigadev.elowar.com/read/ADCD_2.1/Hardware_Manual_guide/node0060.html

The following codes and abbreviations are used in this appendix:

  &        Register used by DMA channel only.

  %        Register used by DMA channel usually, processors sometimes.

  +        Address register pair.  Must be an even address pointing to chip
           memory.

  *        Address not writable by the Copper.

  ~        Address not writable by the Copper unless the "copper danger bit",  COPCON  is set true.

  W,R      W=write-only; R=read-only,

  ER       Early read. This is a DMA data transfer to RAM, from either the
           disk or the blitter.  RAM timing requires data to be on the bus
           earlier than microprocessor read cycles. These transfers are
           therefore initiated by Agnus timing, rather than a read address
           on the destination address bus.

----------------------------------------------------------------------
NAME        ADD  R/W  CHIP    FUNCTION
----------------------------------------------------------------------
[ ] BLTDDAT   & *000  ER  A       Blitter destination early read (dummy address)
[ ] DMACONR     *002  R   AP      DMA control (and blitter status) read
[ ] VPOSR       *004  R   A( E )  Read vert most signif. bit (and frame flop)
[ ] VHPOSR      *006  R   A       Read vert and horiz. position of beam
[ ] DSKDATR   & *008  ER  P       Disk data early read (dummy address)
[ ] JOY0DAT     *00A  R   D       Joystick-mouse 0 data (vert,horiz)
[ ] JOY1DAT     *00C  R   D       Joystick-mouse 1 data (vert,horiz)
[ ] CLXDAT      *00E  R   D       Collision data register (read and clear)
[ ] ADKCONR     *010  R   P       Audio, disk control register read
[ ] POT0DAT     *012  R   P( E )  Pot counter pair 0 data (vert,horiz)
[ ] POT1DAT     *014  R   P( E )  Pot counter pair 1 data (vert,horiz)
[ ] POTGOR      *016  R   P       Pot port data read (formerly POTINP)
[ ] SERDATR     *018  R   P       Serial port data and status read
[ ] DSKBYTR     *01A  R   P       Disk data byte and status read
[ ] INTENAR     *01C  R   P       Interrupt enable bits read
[ ] INTREQR     *01E  R   P       Interrupt request bits read
[ ] DSKPTH    + *020  W   A( E )  Disk pointer (high 3 bits, 5 bits if ECS)
[ ] DSKPTL    + *022  W   A       Disk pointer (low 15 bits)
[ ] DSKLEN      *024  W   P       Disk length
[ ] DSKDAT    & *026  W   P       Disk DMA data write
[ ] REFPTR    & *028  W   A       Refresh pointer
[ ] VPOSW       *02A  W   A       Write vert most signif. bit (and frame flop)
[ ] VHPOSW      *02C  W   A       Write vert and horiz position of beam
[ ] COPCON      *02E  W   A( E )  Coprocessor control register (CDANG)
[ ] SERDAT      *030  W   P       Serial port data and stop bits write
[ ] SERPER      *032  W   P       Serial port period and control
[ ] POTGO       *034  W   P       Pot port data write and start
[ ] JOYTEST     *036  W   D       Write to all four joystick-mouse counters
[ ]                               at once
[ ] STREQU    & *038  S   D       Strobe for horiz sync with VB and EQU
[ ] STRVBL    & *03A  S   D       Strobe for horiz sync with VB (vert. blank)
[ ] STRHOR    & *03C  S   DP      Strobe for horiz sync
[ ] STRLONG   & *03E  S   D( E )  Strobe for identification of long
[ ]                                   horiz. line.
[X] BLTCON0     ~040  W   A       Blitter control register 0
[X] BLTCON1     ~042  W   A( E )  Blitter control register 1
[X] BLTAFWM     ~044  W   A       Blitter first word mask for source A
[X] BLTALWM     ~046  W   A       Blitter last word mask for source A
[X] BLTCPTH   + ~048  W   A       Blitter pointer to source C (high 3 bits)
[X] BLTCPTL   + ~04A  W   A       Blitter pointer to source C (low 15 bits)
[X] BLTBPTH   + ~04C  W   A       Blitter pointer to source B (high 3 bits)
[X] BLTBPTL   + ~04E  W   A       Blitter pointer to source B (low 15 bits)
[X] BLTAPTH   + ~050  W   A( E )  Blitter pointer to source A (high 3 bits)
[X] BLTAPTL   + ~052  W   A       Blitter pointer to source A (low 15 bits)
[X] BLTDPTH   + ~054  W   A       Blitter pointer to destination D (high 3 bits)
[X] BLTDPTL   + ~056  W   A       Blitter pointer to destination D (low 15 bits)
[X] BLTSIZE     ~058  W   A       Blitter start and size (window width,height)
[X] BLTCON0L    ~05A  W   A( E )  Blitter control 0, lower 8 bits (minterms)
[X] BLTSIZV     ~05C  W   A( E )  Blitter V size (for 15 bit vertical size)
[X] BLTSIZH     ~05E  W   A( E )  Blitter H size and start (for 11 bit H size)
[X] BLTCMOD     ~060  W   A       Blitter modulo for source C
[X] BLTBMOD     ~062  W   A       Blitter modulo for source B
[X] BLTAMOD     ~064  W   A       Blitter modulo for source A
[X] BLTDMOD     ~066  W   A       Blitter modulo for destination D
                ~068
                ~06A
                ~06C
                ~06E
[X] BLTCDAT   % ~070  W   A       Blitter source C data register
[X] BLTBDAT   % ~072  W   A       Blitter source B data register
[X] BLTADAT   % ~074  W   A       Blitter source A data register
                ~076
[ ] SPRHDAT     ~078  W   A( E )  Ext. logic UHRES sprite pointer and data id
                ~07A
[ ] DENISEID    ~07C  R   D( E )  Chip revision level for Denise (video out chip)
[ ] DSKSYNC     ~07E  W   P       Disk sync pattern register for disk read
[ ] COP1LCH   +  080  W   A( E )  Coprocessor first location register (high 3 bits, high 5 bits if ECS)
[ ] COP1LCL   +  082  W   A       Coprocessor first location register (low 15 bits)
[ ] COP2LCH   +  084  W   A( E )  Coprocessor second location register (high 3 bits, high 5 bits if ECS)
[ ] COP2LCL   +  086  W   A       Coprocessor second location register (low 15 bits)
[ ] COPJMP1      088  S   A       Coprocessor restart at first location
[ ] COPJMP2      08A  S   A       Coprocessor restart at second location
[ ] COPINS       08C  W   A       Coprocessor instruction fetch identify
[ ] DIWSTRT      08E  W   A       Display window start (upper left vert-horiz position)
[ ] DIWSTOP      090  W   A       Display window stop (lower right vert.-horiz. position)
[ ] DDFSTRT      092  W   A       Display bitplane data fetch start
[ ]                                  (horiz. position)
[ ] DDFSTOP      094  W   A       Display bitplane data fetch stop
[ ]                                  (horiz. position)
[ ] DMACON       096  W   ADP     DMA control write (clear or set)
[ ] CLXCON       098  W   D       Collision control
[ ] INTENA       09A  W   P       Interrupt enable bits (clear or
[ ]                                  set bits)
[ ] INTREQ       09C  W   P       Interrupt request bits (clear or
[ ]                                  set bits)
[ ] ADKCON       09E  W   P       Audio, disk, UART control
[ ] AUD0LCH   +  0A0  W   A( E )  Audio channel 0 location (high 3 bits,
[ ]                                   5 if ECS)
[ ] AUD0LCL   +  0A2  W   A       Audio channel 0 location (low 15 bits)
[ ] AUD0LEN      0A4  W   P       Audio channel 0 length
[ ] AUD0PER      0A6  W   P( E )  Audio channel 0 period
[ ] AUD0VOL      0A8  W   P       Audio channel 0 volume
[ ] AUD0DAT   &  0AA  W   P       Audio channel 0 data
[ ]              0AC
[ ]              0AE
[ ] AUD1LCH   +  0B0  W   A       Audio channel 1 location (high 3 bits)
[ ] AUD1LCL   +  0B2  W   A       Audio channel 1 location (low 15 bits)
[ ] AUD1LEN      0B4  W   P       Audio channel 1 length
[ ] AUD1PER      0B6  W   P       Audio channel 1 period
[ ] AUD1VOL      0B8  W   P       Audio channel 1 volume
[ ] AUD1DAT   &  0BA  W   P       Audio channel 1 data
[ ]              0BC
[ ]              0BE
[ ] AUD2LCH   +  0C0  W   A       Audio channel 2 location (high 3 bits)
[ ] AUD2LCL   +  0C2  W   A       Audio channel 2 location (low 15 bits)
[ ] AUD2LEN      0C4  W   P       Audio channel 2 length
[ ] AUD2PER      0C6  W   P       Audio channel 2 period
[ ] AUD2VOL      0C8  W   P       Audio channel 2 volume
[ ] AUD2DAT   &  0CA  W   P       Audio channel 2 data
[ ]              0CC
[ ]              0CE
[ ] AUD3LCH   +  0D0  W   A       Audio channel 3 location (high 3 bits)
[ ] AUD3LCL   +  0D2  W   A       Audio channel 3 location (low 15 bits)
[ ] AUD3LEN      0D4  W   P       Audio channel 3 length
[ ] AUD3PER      0D6  W   P       Audio channel 3 period
[ ] AUD3VOL      0D8  W   P       Audio channel 3 volume
[ ] AUD3DAT   &  0DA  W   P       Audio channel 3 data
[ ]              0DC
[ ]              0DE
[ ] BPL1PTH   +  0E0  W   A       Bitplane 1 pointer (high 3 bits)
[ ] BPL1PTL   +  0E2  W   A       Bitplane 1 pointer (low 15 bits)
[ ] BPL2PTH   +  0E4  W   A       Bitplane 2 pointer (high 3 bits)
[ ] BPL2PTL   +  0E6  W   A       Bitplane 2 pointer (low 15 bits)
[ ] BPL3PTH   +  0E8  W   A       Bitplane 3 pointer (high 3 bits)
[ ] BPL3PTL   +  0EA  W   A       Bitplane 3 pointer (low 15 bits)
[ ] BPL4PTH   +  0EC  W   A       Bitplane 4 pointer (high 3 bits)
[ ] BPL4PTL   +  0EE  W   A       Bitplane 4 pointer (low 15 bits)
[ ] BPL5PTH   +  0F0  W   A       Bitplane 5 pointer (high 3 bits)
[ ] BPL5PTL   +  0F2  W   A       Bitplane 5 pointer (low 15 bits)
[ ] BPL6PTH   +  0F4  W   A       Bitplane 6 pointer (high 3 bits)
[ ] BPL6PTL   +  0F6  W   A       Bitplane 6 pointer (low 15 bits)
[ ]              0F8
[ ]              0FA
[ ]              0FC
[ ]              0FE
[ ] BPLCON0      100  W   AD( E ) Bitplane control register
[ ]                                   (misc. control bits)
[ ] BPLCON1      102  W   D       Bitplane control reg.
[ ]                                   (scroll value PF1, PF2)
[ ] BPLCON2      104  W   D( E )  Bitplane control reg. (priority control)
[ ] BPLCON3      106  W   D( E )  Bitplane control (enhanced features)
[ ]
[ ] BPL1MOD      108  W   A       Bitplane modulo (odd planes)
[ ] BPL2MOD      10A  W   A       Bitplane modulo (even planes)
[ ]              10C
[ ]              10E
[ ] BPL1DAT   &  110  W   D       Bitplane 1 data (parallel-to-serial convert)
[ ] BPL2DAT   &  112  W   D       Bitplane 2 data (parallel-to-serial convert)
[ ] BPL3DAT   &  114  W   D       Bitplane 3 data (parallel-to-serial convert)
[ ] BPL4DAT   &  116  W   D       Bitplane 4 data (parallel-to-serial convert)
[ ] BPL5DAT   &  118  W   D       Bitplane 5 data (parallel-to-serial convert)
[ ] BPL6DAT   &  11A  W   D       Bitplane 6 data (parallel-to-serial convert)
[ ]              11C
[ ]              11E
[ ] SPR0PTH   +  120  W   A       Sprite 0 pointer (high 3 bits)
[ ] SPR0PTL   +  122  W   A       Sprite 0 pointer (low 15 bits)
[ ] SPR1PTH   +  124  W   A       Sprite 1 pointer (high 3 bits)
[ ] SPR1PTL   +  126  W   A       Sprite 1 pointer (low 15 bits)
[ ] SPR2PTH   +  128  W   A       Sprite 2 pointer (high 3 bits)
[ ] SPR2PTL   +  12A  W   A       Sprite 2 pointer (low 15 bits)
[ ] SPR3PTH   +  12C  W   A       Sprite 3 pointer (high 3 bits)
[ ] SPR3PTL   +  12E  W   A       Sprite 3 pointer (low 15 bits)
[ ] SPR4PTH   +  130  W   A       Sprite 4 pointer (high 3 bits)
[ ] SPR4PTL   +  132  W   A       Sprite 4 pointer (low 15 bits)
[ ] SPR5PTH   +  134  W   A       Sprite 5 pointer (high 3 bits)
[ ] SPR5PTL   +  136  W   A       Sprite 5 pointer (low 15 bits)
[ ] SPR6PTH   +  138  W   A       Sprite 6 pointer (high 3 bits)
[ ] SPR6PTL   +  13A  W   A       Sprite 6 pointer (low 15 bits)
[ ] SPR7PTH   +  13C  W   A       Sprite 7 pointer (high 3 bits)
[ ] SPR7PTL   +  13E  W   A       Sprite 7 pointer (low 15 bits)
[ ] SPR0POS   %  140  W   AD      Sprite 0 vert-horiz start position
[ ]                                  data
[ ] SPR0CTL   %  142  W   AD( E ) Sprite 0 vert stop position and
[ ]                                  control data
[ ] SPR0DATA  %  144  W   D       Sprite 0 image data register A
[ ] SPR0DATB  %  146  W   D       Sprite 0 image data register B
[ ] SPR1POS   %  148  W   AD      Sprite 1 vert-horiz start position
[ ]                                  data
[ ] SPR1CTL   %  14A  W   AD      Sprite 1 vert stop position and
[ ]                                  control data
[ ] SPR1DATA  %  14C  W   D       Sprite 1 image data register A
[ ] SPR1DATB  %  14E  W   D       Sprite 1 image data register B
[ ] SPR2POS   %  150  W   AD      Sprite 2 vert-horiz start position
[ ]                                  data
[ ] SPR2CTL   %  152  W   AD      Sprite 2 vert stop position and
[ ]                                  control data
[ ] SPR2DATA  %  154  W   D       Sprite 2 image data register A
[ ] SPR2DATB  %  156  W   D       Sprite 2 image data register B
[ ] SPR3POS   %  158  W   AD      Sprite 3 vert-horiz start position
[ ]                                  data
[ ] SPR3CTL   %  15A  W   AD      Sprite 3 vert stop position and
[ ]                                  control data
[ ] SPR3DATA  %  15C  W   D       Sprite 3 image data register A
[ ] SPR3DATB  %  15E  W   D       Sprite 3 image data register B
[ ] SPR4POS   %  160  W   AD      Sprite 4 vert-horiz start position
[ ]                                  data
[ ] SPR4CTL   %  162  W   AD      Sprite 4 vert stop position and
[ ]                                  control data
[ ] SPR4DATA  %  164  W   D       Sprite 4 image data register A
[ ] SPR4DATB  %  166  W   D       Sprite 4 image data register B
[ ] SPR5POS   %  168  W   AD      Sprite 5 vert-horiz start position
[ ]                                  data
[ ] SPR5CTL   %  16A  W   AD      Sprite 5 vert stop position and
[ ]                                  control data
[ ] SPR5DATA  %  16C  W   D       Sprite 5 image data register A
[ ] SPR5DATB  %  16E  W   D       Sprite 5 image data register B
[ ] SPR6POS   %  170  W   AD      Sprite 6 vert-horiz start position
[ ]                                  data
[ ] SPR6CTL   %  172  W   AD      Sprite 6 vert stop position and
[ ]                                  control data
[ ] SPR6DATA  %  174  W   D       Sprite 6 image data register A
[ ] SPR6DATB  %  176  W   D       Sprite 6 image data register B
[ ] SPR7POS   %  178  W   AD      Sprite 7 vert-horiz start position
[ ]                                  data
[ ] SPR7CTL   %  17A  W   AD      Sprite 7 vert stop position and
[ ]                                  control data
[ ] SPR7DATA  %  17C  W   D       Sprite 7 image data register A
[ ] SPR7DATB  %  17E  W   D       Sprite 7 image data register B
[ ] COLOR00      180  W   D       Color table 00
[ ] COLOR01      182  W   D       Color table 01
[ ] COLOR02      184  W   D       Color table 02
[ ] COLOR03      186  W   D       Color table 03
[ ] COLOR04      188  W   D       Color table 04
[ ] COLOR05      18A  W   D       Color table 05
[ ] COLOR06      18C  W   D       Color table 06
[ ] COLOR07      18E  W   D       Color table 07
[ ] COLOR08      190  W   D       Color table 08
[ ] COLOR09      192  W   D       Color table 09
[ ] COLOR10      194  W   D       Color table 10
[ ] COLOR11      196  W   D       Color table 11
[ ] COLOR12      198  W   D       Color table 12
[ ] COLOR13      19A  W   D       Color table 13
[ ] COLOR14      19C  W   D       Color table 14
[ ] COLOR15      19E  W   D       Color table 15
[ ] COLOR16      1A0  W   D       Color table 16
[ ] COLOR17      1A2  W   D       Color table 17
[ ] COLOR18      1A4  W   D       Color table 18
[ ] COLOR19      1A6  W   D       Color table 19
[ ] COLOR20      1A8  W   D       Color table 20
[ ] COLOR21      1AA  W   D       Color table 21
[ ] COLOR22      1AC  W   D       Color table 22
[ ] COLOR23      1AE  W   D       Color table 23
[ ] COLOR24      1B0  W   D       Color table 24
[ ] COLOR25      1B2  W   D       Color table 25
[ ] COLOR26      1B4  W   D       Color table 26
[ ] COLOR27      1B6  W   D       Color table 27
[ ] COLOR28      1B8  W   D       Color table 28
[ ] COLOR29      1BA  W   D       Color table 29
[ ] COLOR30      1BC  W   D       Color table 30
[ ] COLOR31      1BE  W   D       Color table 31
[ ]
[ ] HTOTAL       1C0  W   A( E )  Highest number count, horiz line
[ ]                                   (VARBEAMEN=1)
[ ] HSSTOP       1C2  W   A( E )  Horizontal line position for HSYNC stop
[ ] HBSTRT       1C4  W   A( E )  Horizontal line position for HBLANK start
[ ] HBSTOP       1C6  W   A( E )  Horizontal line position for HBLANK stop
[ ] VTOTAL       1C8  W   A( E )  Highest numbered vertical line
[ ]                                   (VARBEAMEN=1)
[ ] VSSTOP       1CA  W   A( E )  Vertical line position for VSYNC stop
[ ] VBSTRT       1CC  W   A( E )  Vertical line for VBLANK start
[ ] VBSTOP       1CE  W   A( E )  Vertical line for VBLANK stop
[ ]
[ ]              1D0              Reserved
[ ]              1D2              Reserved
[ ]              1D4              Reserved
[ ]              1D6              Reserved
[ ]              1D8              Reserved
[ ]              1DA              Reserved
[ ]
[ ] BEAMCON0     1DC  W   A( E )  Beam counter control register (SHRES,PAL)
[ ] HSSTRT       1DE  W   A( E )  Horizontal sync start (VARHSY)
[ ] VSSTRT       1E0  W   A( E )  Vertical sync start   (VARVSY)
[ ] HCENTER      1E2  W   A( E )  Horizontal position for Vsync on interlace
[ ] DIWHIGH      1E4  W   AD( E ) Display window -  upper bits for start, stop
     */
    public final Video video;
    private IRQController irqController;

    public CustomChipPage(int startAddress,
                          Blitter blitter,
                          Video video,
                          IRQController irqController)
    {
        this.startAddress = startAddress;
        this.blitter = blitter;
        this.video = video;
        this.irqController = irqController;
    }

    @Override
    public byte readByte(int offset)
    {
        return readByteNoSideEffects( offset );
    }

    /*
    INTREQ     09C      W       P   Interrupt request bits (clear or set)
    INTREQR    01E      R       P   Interrupt request bits (read)
    INTENA     09A      W       P    Interrupt enable bits (clear or set bits)
    INTENAR    01C      R       P    Interrupt enable bits (read)
     */
    @Override
    public byte readByteNoSideEffects(int offset)
    {
        final int adr = (startAddress+offset) & 0x1ff;
        if ( adr >= 0x40 && adr <= 0x74 )
        {
            final int regOffset = adr - 0x040;
            return blitter.readByte( regOffset );
        }
        if ( adr == 0x02 ) {
            return (byte) (readDMACONR() >> 8);
        }
        if ( adr == 0x03 ) {
            return (byte) readDMACONR();
        }

        if ( adr == 0x04 ) {
            // VPOSR hi-byte
            return (byte) (video.readVPOSR() >>> 8);
        }
        if ( adr == 0x05 ) {
            // VPOSR lo-byte
            return (byte) video.readVPOSR();
        }
        if ( adr == 0x06 ) {
            // VHPOSR hi-byte
            return (byte) (video.readVHPOSR() >>> 8);
        }
        if ( adr == 0x07 ) {
            // VHPOSR lo-byte
            return (byte) (video.readVHPOSR() >>> 8);
        }

        // 0x0E0...0x1e4 => video
        if ( adr >= 0x0e0 && adr <= 0x1E5) {
            return video.readByte( adr );
        }
        if ( adr == 0x01c) { // INTENAR
            return (byte) (irqController.irqEnabled >>> 8);
        }
        if ( adr == 0x01d) { // INTENAR
            return (byte) irqController.irqEnabled;
        }
        if ( adr == 0x01e) { // INTREQR
            return (byte) (irqController.irqRequests >>> 8);
        }
        if ( adr == 0x01f) { // INTREQR
            return (byte) irqController.irqRequests;
        }
        LOG.info( "CUSTOM CHIP AREA: Unhandled read at offset "+Misc.hex(offset) );
        return 0;
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {
        final int adr = (startAddress+offset) & 0x1ff;
        if ( adr >= 0x40 && adr <= 0x74 )
        {
            final int regOffset = adr - 0x040;
            blitter.writeByte( regOffset, value);
            return;
        }
        // 0x0E0...0x1e4 => video
        if ( adr >= 0x0e0 && adr <= 0x1E5) {
            video.writeByte( adr, value );
            return;
        }
        LOG.info( "CUSTOM CHIP: Unhandled byte write @ "+ Misc.hex(offset) );
    }

    @Override
    public void writeWord(int offset, int value) throws MemoryAccessException
    {
        final int adr = (startAddress+offset) & 0x1ff;
        switch( adr ) {
            case 0x34: // TODO: POTGO, currently silently dropped
                return;
        }
        if ( adr >= 0x40 && adr <= 0x74 )
        {
            final int regOffset = adr - 0x040;
            blitter.writeWord( regOffset, value );
        } else if ( adr == 0x2e || adr >= 0x80 && adr <= 0x8c ) { // COPCON
            video.writeWord( adr, value );
        } else if ( adr == 0x96 ) {
            writeDMACON( value );
        } else if ( adr >= 0x0e0 && adr <= 0x1E5) {
            // 0x0E0...0x1e4 => video
            video.writeWord( adr, value );
        } else if ( adr == 0x09a) { // INTENA
            irqController.writeIRQEnable( value );
        } else if ( adr == 0x09c) { // INTREQR
            irqController.writeIRQReq( value );
        } else {
            LOG.info( "CHIPSET: Unhandled word write "+Misc.hex(value)+" ("+Misc.binary16Bit( value )+" @ "+registerName(startAddress+offset) );
        }
    }

    private void writeDMACON(int value)
    {
        int current = readDMACONR();
        if ( ( value & 1<<15) != 0 )
        {
            // set bits
            current |= (value & ~(1<<15) );
        } else {
            // clear bits
            current &= ~value;
        }
        blitter.dmaController.flags=current;
        LOG.info( blitter.dmaController );
    }

    private int readDMACONR() {
        int value=0;

        if ( blitter.blitterActive ) {
            value |= 1<<14;
        }
        if ( blitter.totalResult == 0 ) {
            value |= 1<<13;
        }
        if ( blitter.blitterNasty ) {
            value |= 1<<10;
        }
        value |= blitter.dmaController.flags;
        return value;
    }

    /*
Register Address  Write   Paula         Function
-------- -------  -----   -------       --------
DMACON     096      W     A D P   DMA control write (clear or set)
DMACONR    002      R     A   P   DMA control (and blitter status) read

                 This register controls all of the DMA channels and
                 contains blitter DMA status bits.

                 BIT#  FUNCTION    DESCRIPTION
                 ----  ---------   -----------------------------------
                 15    SET/CLR     Set/clear control bit. Determines
                                   if bits written with a 1 get set or
                                   cleared.  Bits written with a zero
                                   are unchanged.
                 14    BBUSY       Blitter busy status bit (read only)
                 13    BZERO       Blitter logic  zero status bit
                                   (read only).
                 12    X
                 11    X
                 10    BLTPRI      Blitter DMA priority
                                   (over CPU micro) (also called
                                   "blitter nasty") (disables /BLS
                                   pin, preventing micro from
                                   stealing any bus cycles while
                                   blitter DMA is running).
                 09    DMAEN       Enable all DMA below
                 08    BPLEN       Bitplane DMA enable
                 07    COPEN       Copper DMA enable
                 06    BLTEN       Blitter DMA enable
                 05    SPREN       Sprite DMA enable
                 04    DSKEN       Disk DMA enable
                 03    AUD3EN      Audio channel 3 DMA enable
                 02    AUD2EN      Audio channel 2 DMA enable
                 01    AUD1EN      Audio channel 1 DMA enable
                 00    AUD0EN      Audio channel 0 DMA enable
     */
    private static String registerName(int address) {
        final RegisterDescription register = regResolver.resolve( address );
        return register == null ? Misc.hex(address) : register.name+" ("+Misc.hex(address)+")";
    }
}