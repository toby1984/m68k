package de.codesourcery.m68k.disassembler;

import de.codesourcery.m68k.emulator.Emulator;
import de.codesourcery.m68k.utils.Misc;
import org.apache.commons.lang3.Validate;

import java.util.HashMap;
import java.util.Map;

public class ChipRegisterResolver implements Disassembler.IChipRegisterResolver
{
    /*
See http://amigadev.elowar.com/read/ADCD_2.1/Hardware_Manual_guide/node0060.html

The following codes and abbreviations are used in this appendix:

  &        Register used by DMA channel only.
  %        Register used by DMA channel usually, processors sometimes.
  +        Address register pair.  Must be an even address pointing to chip memory.
  *        Address not writable by the Copper.
  ~        Address not writable by the Copper unless the "copper danger bit",  COPCON  is set true.
  W,R      W=write-only; R=read-only,
  ER       Early read. This is a DMA data transfer to RAM, from either the
           disk or the blitter.  RAM timing requires data to be on the bus
           earlier than microprocessor read cycles. These transfers are
           therefore initiated by Agnus timing, rather than a read address
           on the destination address bus.
*/
    // TODO: Performance penalty due to boxing/unboxing of map key ... maybe use Trove etc. instead ?
    private static final Map<Integer, RegisterDescription> registerDescriptions = new HashMap<>();

    private final Emulator emulator;

    public ChipRegisterResolver(Emulator emulator)
    {
        this.emulator = emulator;
    }

    private static void registerCIA(int address, String registerName, String description)
    {
        final RegisterDescription desc = new RegisterDescription(address, registerName, 0, description);
        final RegisterDescription existing = registerDescriptions.put(address, desc);
        if (existing != null)
        {
            throw new RuntimeException("Duplicate description for register " + Misc.hex(address) + ", new: " + desc + " <-> old: " + existing);
        }
    }

    private static void registerCustomChip(String regName, String regFlags1, String copperAccess, int offset, String accessFlags, String description)
    {
        final String allFlags = regFlags1 + copperAccess + accessFlags;
        final int flagBits = parseFlagBits(allFlags);

        //
        final int address = 0xDFF000 + offset;
        final RegisterDescription desc = new RegisterDescription(address, regName, flagBits, description);
        final RegisterDescription existing = registerDescriptions.put(address, desc);
        if (existing != null)
        {
            throw new RuntimeException("Duplicate description for register " + Misc.hex(address) + ", new: " + desc + " <-> old: " + existing);
        }
    }

    private static int parseFlagBits(String allFlags)
    {
        int flagBits = 0;
        if (allFlags.contains("ER"))
        {
            flagBits |= RegisterDescription.FLAG_EARLY_READ;
            allFlags = allFlags.replace("ER", "");
        }
        for (int i = 0, len = allFlags.length(); i < len; i++)
        {
            switch (allFlags.charAt(i))
            {
                case '&':
                    flagBits |= RegisterDescription.FLAG_DMA_ONLY;
                    break;
                case '%':
                    flagBits |= RegisterDescription.FLAG_DMA_MOSTLY;
                    break;
                case '+':
                    flagBits |= RegisterDescription.FLAG_REGISTER_PAIR;
                    break;
                case '*':
                    flagBits |= RegisterDescription.FLAG_COPPER_NOT_WRITEABLE;
                    break;
                case '~':
                    flagBits |= RegisterDescription.FLAG_COPPER_WRITE_PROTECTED;
                    break;
                case 'R':
                    flagBits |= RegisterDescription.FLAG_READ_ONLY;
                    break;
                case 'W':
                    flagBits |= RegisterDescription.FLAG_WRITE_ONLY;
                    break;
                case 'S':
                    flagBits |= RegisterDescription.FLAG_STROBE;
                    break;
                default:
                    throw new IllegalArgumentException("Internal error, unknown flag '" + allFlags.charAt(i) + "'");
            }
        }
        return flagBits;
    }

    static
    {
        // register pheriphal chip registers

        // CIAA Address Map

        registerCIA(0xBFE001, "ciaa_pra", "/FIR1 /FIR0  /RDY /TK0  /WPRO /CHNG /LED  OVL");
        registerCIA(0xBFE101, "ciaa_prb", "Parallel port");
        registerCIA(0xBFE201, "ciaa_ddra", "Direction for port A (BFE001);1=output (set to 0x03)");
        registerCIA(0xBFE301, "ciaa_ddrb", "Direction for port B (BFE101);1=output (can be in or out)");
        registerCIA(0xBFE401, "ciaa_talo", "CIAA timer A low byte (.715909 Mhz NTSC; .709379 Mhz PAL)");
        registerCIA(0xBFE501, "ciaa_tahi", "CIAA timer A high byte");
        registerCIA(0xBFE601, "ciaa_tblo", "CIAA timer B low byte (.715909 Mhz NTSC; .709379 Mhz PAL)");
        registerCIA(0xBFE701, "ciaa_tbhi", "CIAA timer B high byte");
        registerCIA(0xBFE801, "ciaa_todlo", "50/60 Hz event counter bits 7-0 (VSync or line tick)");
        registerCIA(0xBFE901, "ciaa_todmid", "50/60 Hz event counter bits 15-8");
        registerCIA(0xBFEA01, "ciaa_todhi", "50/60 Hz event counter bits 23-16");
        registerCIA(0xBFEC01, "ciaa_sdr", "CIAA serial data register (connected to keyboard)");
        registerCIA(0xBFED01, "ciaa_icr", "CIAA interrupt control register");
        registerCIA(0xBFEE01, "ciaa_cra", "CIAA control register A");
        registerCIA(0xBFEF01, "ciaa_crb", "CIAA control register B");

        // CIAB Address Map

        registerCIA(0xBFD000, "ciab_pra", "/DTR  /RTS  /CD   /CTS  /DSR   SEL   POUT  BUSY");
        registerCIA(0xBFD100, "ciab_prb", "/MTR  /SEL3 /SEL2 /SEL1 /SEL0 /SIDE  DIR  /STEP");
        registerCIA(0xBFD200, "ciab_ddra", "Direction for Port A (BFD000);1 = output (set to 0xFF)");
        registerCIA(0xBFD300, "ciab_ddrb", "Direction for Port B (BFD100);1 = output (set to 0xFF)");
        registerCIA(0xBFD400, "ciab_talo", "CIAB timer A low byte (.715909 Mhz NTSC; .709379 Mhz PAL)");
        registerCIA(0xBFD500, "ciab_tahi", "CIAB timer A high byte");
        registerCIA(0xBFD600, "ciab_tblo", "CIAB timer B low byte (.715909 Mhz NTSC; .709379 Mhz PAL)");
        registerCIA(0xBFD700, "ciab_tbhi", "CIAB timer B high byte");
        registerCIA(0xBFD800, "ciab_todlo", "Horizontal sync event counter bits 7-0");
        registerCIA(0xBFD900, "ciab_todmid", "Horizontal sync event counter bits 15-8");
        registerCIA(0xBFDA00, "ciab_todhi", "Horizontal sync event counter bits 23-16");
        registerCIA(0xBFDC00, "ciab_sdr", "CIAB serial data register (unused)");
        registerCIA(0xBFDD00, "ciab_icr", "CIAB interrupt control register");
        registerCIA(0xBFDE00, "ciab_cra", "CIAB Control register A");
        registerCIA(0xBFDF00, "ciab_crb", "CIAB Control register B");

        // register custom chip registers
        registerCustomChip("BLTDDAT", "&", "*", 0x000, "ER", "Blitter destination early read (dummy address)");
        registerCustomChip("DMACONR", "", "*", 0x002, "R", "DMA control (and blitter status) read");
        registerCustomChip("VPOSR", "", "*", 0x004, "R", "Read vert most signif. bit (and frame flop)");
        registerCustomChip("VHPOSR", "", "*", 0x006, "R", "Read vert and horiz. position of beam");
        registerCustomChip("DSKDATR", "&", "*", 0x008, "ER", "Disk data early read (dummy address)");
        registerCustomChip("JOY0DAT", "", "*", 0x00A, "R", "Joystick-mouse 0 data (vert,horiz)");
        registerCustomChip("JOY1DAT", "", "*", 0x00C, "R", "Joystick-mouse 1 data (vert,horiz)");
        registerCustomChip("CLXDAT", "", "*", 0x00E, "R", "Collision data register (read and clear)");
        registerCustomChip("ADKCONR", "", "*", 0x010, "R", "Audio, disk control register read");
        registerCustomChip("POT0DAT", "", "*", 0x012, "R", "Pot counter pair 0 data (vert,horiz)");
        registerCustomChip("POT1DAT", "", "*", 0x014, "R", "Pot counter pair 1 data (vert,horiz)");
        registerCustomChip("POTGOR", "", "*", 0x016, "R", "Pot port data read (formerly POTINP)");
        registerCustomChip("SERDATR", "", "*", 0x018, "R", "Serial port data and status read");
        registerCustomChip("DSKBYTR", "", "*", 0x01A, "R", "Disk data byte and status read");
        registerCustomChip("INTENAR", "", "*", 0x01C, "R", "Interrupt enable bits read");
        registerCustomChip("INTREQR", "", "*", 0x01E, "R", "Interrupt request bits read");
        registerCustomChip("DSKPTH", "+", "*", 0x020, "W", "Disk pointer (high 3 bits, 5 bits if ECS)");
        registerCustomChip("DSKPTL", "+", "*", 0x022, "W", "Disk pointer (low 15 bits)");
        registerCustomChip("DSKLEN", "", "*", 0x024, "W", "Disk length");
        registerCustomChip("DSKDAT", "&", "*", 0x026, "W", "Disk DMA data write");
        registerCustomChip("REFPTR", "&", "*", 0x028, "W", "Refresh pointer");
        registerCustomChip("VPOSW", "", "*", 0x02A, "W", "Write vert most signif. bit (and frame flop)");
        registerCustomChip("VHPOSW", "", "*", 0x02C, "W", "Write vert and horiz position of beam");
        registerCustomChip("COPCON", "", "*", 0x02E, "W", "Coprocessor control register (CDANG)");
        registerCustomChip("SERDAT", "", "*", 0x030, "W", "Serial port data and stop bits write");
        registerCustomChip("SERPER", "", "*", 0x032, "W", "Serial port period and control");
        registerCustomChip("POTGO", "", "*", 0x034, "W", "Pot port data write and start");
        registerCustomChip("JOYTEST", "", "*", 0x036, "W", "Write to all four joystick-mouse counters at once");
        registerCustomChip("STREQU", "&", "*", 0x038, "S", "Strobe for horiz sync with VB and EQU");
        registerCustomChip("STRVBL", "&", "*", 0x03A, "S", "Strobe for horiz sync with VB (vert. blank)");
        registerCustomChip("STRHOR", "&", "*", 0x03C, "S", "Strobe for horiz sync");
        registerCustomChip("STRLONG", "&", "*", 0x03E, "S", "Strobe for identification of long horiz. line.");
        registerCustomChip("BLTCON0", "", "~", 0x040, "W", "Blitter control register 0");
        registerCustomChip("BLTCON1", "", "~", 0x042, "W", "Blitter control register 1");
        registerCustomChip("BLTAFWM", "", "~", 0x044, "W", "Blitter first word mask for source A");
        registerCustomChip("BLTALWM", "", "~", 0x046, "W", "Blitter last word mask for source A");
        registerCustomChip("BLTCPTH", "+", "~", 0x048, "W", "Blitter pointer to source C (high 3 bits)");
        registerCustomChip("BLTCPTL", "+", "~", 0x04A, "W", "Blitter pointer to source C (low 15 bits)");
        registerCustomChip("BLTBPTH", "+", "~", 0x04C, "W", "Blitter pointer to source B (high 3 bits)");
        registerCustomChip("BLTBPTL", "+", "~", 0x04E, "W", "Blitter pointer to source B (low 15 bits)");
        registerCustomChip("BLTAPTH", "+", "~", 0x050, "W", "Blitter pointer to source A (high 3 bits)");
        registerCustomChip("BLTAPTL", "+", "~", 0x052, "W", "Blitter pointer to source A (low 15 bits)");
        registerCustomChip("BLTDPTH", "+", "~", 0x054, "W", "Blitter pointer to destination D (high 3 bits)");
        registerCustomChip("BLTDPTL", "+", "~", 0x056, "W", "Blitter pointer to destination D (low 15 bits)");
        registerCustomChip("BLTSIZE", "", "~", 0x058, "W", "Blitter start and size (window width,height)");
        registerCustomChip("BLTCON0L", "", "~", 0x05A, "W", "Blitter control 0, lower 8 bits (minterms)");
        registerCustomChip("BLTSIZV", "", "~", 0x05C, "W", "Blitter V size (for 15 bit vertical size)");
        registerCustomChip("BLTSIZH", "", "~", 0x05E, "W", "Blitter H size and start (for 11 bit H size)");
        registerCustomChip("BLTCMOD", "", "~", 0x060, "W", "Blitter modulo for source C");
        registerCustomChip("BLTBMOD", "", "~", 0x062, "W", "Blitter modulo for source B");
        registerCustomChip("BLTAMOD", "", "~", 0x064, "W", "Blitter modulo for source A");
        registerCustomChip("BLTDMOD", "", "~", 0x066, "W", "Blitter modulo for destination D");
        registerCustomChip("BLTCDAT", "%", "~", 0x070, "W", "Blitter source C data register");
        registerCustomChip("BLTBDAT", "%", "~", 0x072, "W", "Blitter source B data register");
        registerCustomChip("BLTADAT", "%", "~", 0x074, "W", "Blitter source A data register");
        registerCustomChip("SPRHDAT", "", "~", 0x078, "W", "Ext. logic UHRES sprite pointer and data id");
        registerCustomChip("DENISEID", "", "~", 0x07C, "R", "Chip revision level for Denise (video out chip)");
        registerCustomChip("DSKSYNC", "", "~", 0x07E, "W", "Disk sync pattern register for disk read");
        registerCustomChip("COP1LCH", "+", "", 0x080, "W", "Coprocessor first location register (high 3 bits, high 5 bits if ECS)");
        registerCustomChip("COP1LCL", "+", "", 0x082, "W", "Coprocessor first location register (low 15 bits)");
        registerCustomChip("COP2LCH", "+", "", 0x084, "W", "Coprocessor second location register (high 3 bits, high 5 bits if ECS)");
        registerCustomChip("COP2LCL", "+", "", 0x086, "W", "Coprocessor second location register (low 15 bits)");
        registerCustomChip("COPJMP1", "", "", 0x088, "S", "Coprocessor restart at first location");
        registerCustomChip("COPJMP2", "", "", 0x08A, "S", "Coprocessor restart at second location");
        registerCustomChip("COPINS", "", "", 0x08C, "W", "Coprocessor instruction fetch identify");
        registerCustomChip("DIWSTRT", "", "", 0x08E, "W", "Display window start (upper left vert-horiz position)");
        registerCustomChip("DIWSTOP", "", "", 0x090, "W", "Display window stop (lower right vert.-horiz. position)");
        registerCustomChip("DDFSTRT", "", "", 0x092, "W", "Display bitplane data fetch start (horiz. position)");
        registerCustomChip("DDFSTOP", "", "", 0x094, "W", "Display bitplane data fetch stop (horiz. position)");
        registerCustomChip("DMACON", "", "", 0x096, "W", "DMA control write (clear or set)");
        registerCustomChip("CLXCON", "", "", 0x098, "W", "Collision control");
        registerCustomChip("INTENA", "", "", 0x09A, "W", "Interrupt enable bits (clear or set bits)");
        registerCustomChip("INTREQ", "", "", 0x09C, "W", "Interrupt request bits (clear or set bits)");
        registerCustomChip("ADKCON", "", "", 0x09E, "W", "Audio, disk, UART control");
        registerCustomChip("AUD0LCH", "+", "", 0x0A0, "W", "Audio channel 0 location (high 3 bits, 5 if ECS)");
        registerCustomChip("AUD0LCL", "+", "", 0x0A2, "W", "Audio channel 0 location (low 15 bits)");
        registerCustomChip("AUD0LEN", "", "", 0x0A4, "W", "Audio channel 0 length");
        registerCustomChip("AUD0PER", "", "", 0x0A6, "W", "Audio channel 0 period");
        registerCustomChip("AUD0VOL", "", "", 0x0A8, "W", "Audio channel 0 volume");
        registerCustomChip("AUD0DAT", "&", "", 0x0AA, "W", "Audio channel 0 data");
        registerCustomChip("AUD1LCH", "+", "", 0x0B0, "W", "Audio channel 1 location (high 3 bits)");
        registerCustomChip("AUD1LCL", "+", "", 0x0B2, "W", "Audio channel 1 location (low 15 bits)");
        registerCustomChip("AUD1LEN", "", "", 0x0B4, "W", "Audio channel 1 length");
        registerCustomChip("AUD1PER", "", "", 0x0B6, "W", "Audio channel 1 period");
        registerCustomChip("AUD1VOL", "", "", 0x0B8, "W", "Audio channel 1 volume");
        registerCustomChip("AUD1DAT", "&", "", 0x0BA, "W", "Audio channel 1 data");
        registerCustomChip("AUD2LCH", "+", "", 0x0C0, "W", "Audio channel 2 location (high 3 bits)");
        registerCustomChip("AUD2LCL", "+", "", 0x0C2, "W", "Audio channel 2 location (low 15 bits)");
        registerCustomChip("AUD2LEN", "", "", 0x0C4, "W", "Audio channel 2 length");
        registerCustomChip("AUD2PER", "", "", 0x0C6, "W", "Audio channel 2 period");
        registerCustomChip("AUD2VOL", "", "", 0x0C8, "W", "Audio channel 2 volume");
        registerCustomChip("AUD2DAT", "&", "", 0x0CA, "W", "Audio channel 2 data");
        registerCustomChip("AUD3LCH", "+", "", 0x0D0, "W", "Audio channel 3 location (high 3 bits)");
        registerCustomChip("AUD3LCL", "+", "", 0x0D2, "W", "Audio channel 3 location (low 15 bits)");
        registerCustomChip("AUD3LEN", "", "", 0x0D4, "W", "Audio channel 3 length");
        registerCustomChip("AUD3PER", "", "", 0x0D6, "W", "Audio channel 3 period");
        registerCustomChip("AUD3VOL", "", "", 0x0D8, "W", "Audio channel 3 volume");
        registerCustomChip("AUD3DAT", "&", "", 0x0DA, "W", "Audio channel 3 data");
        registerCustomChip("BPL1PTH", "+", "", 0x0E0, "W", "Bitplane 1 pointer (high 3 bits)");
        registerCustomChip("BPL1PTL", "+", "", 0x0E2, "W", "Bitplane 1 pointer (low 15 bits)");
        registerCustomChip("BPL2PTH", "+", "", 0x0E4, "W", "Bitplane 2 pointer (high 3 bits)");
        registerCustomChip("BPL2PTL", "+", "", 0x0E6, "W", "Bitplane 2 pointer (low 15 bits)");
        registerCustomChip("BPL3PTH", "+", "", 0x0E8, "W", "Bitplane 3 pointer (high 3 bits)");
        registerCustomChip("BPL3PTL", "+", "", 0x0EA, "W", "Bitplane 3 pointer (low 15 bits)");
        registerCustomChip("BPL4PTH", "+", "", 0x0EC, "W", "Bitplane 4 pointer (high 3 bits)");
        registerCustomChip("BPL4PTL", "+", "", 0x0EE, "W", "Bitplane 4 pointer (low 15 bits)");
        registerCustomChip("BPL5PTH", "+", "", 0x0F0, "W", "Bitplane 5 pointer (high 3 bits)");
        registerCustomChip("BPL5PTL", "+", "", 0x0F2, "W", "Bitplane 5 pointer (low 15 bits)");
        registerCustomChip("BPL6PTH", "+", "", 0x0F4, "W", "Bitplane 6 pointer (high 3 bits)");
        registerCustomChip("BPL6PTL", "+", "", 0x0F6, "W", "Bitplane 6 pointer (low 15 bits)");
        registerCustomChip("BPLCON0", "", "", 0x100, "W", "Bitplane control register (misc. control bits)");
        registerCustomChip("BPLCON1", "", "", 0x102, "W", "Bitplane control reg. (scroll value PF1, PF2)");
        registerCustomChip("BPLCON2", "", "", 0x104, "W", "Bitplane control reg. (priority control)");
        registerCustomChip("BPLCON3", "", "", 0x106, "W", "Bitplane control (enhanced features)");
        registerCustomChip("BPL1MOD", "", "", 0x108, "W", "Bitplane modulo (odd planes)");
        registerCustomChip("BPL2MOD", "", "", 0x10A, "W", "Bitplane modulo (even planes)");
        registerCustomChip("BPL1DAT", "&", "", 0x110, "W", "Bitplane 1 data (parallel-to-serial convert)");
        registerCustomChip("BPL2DAT", "&", "", 0x112, "W", "Bitplane 2 data (parallel-to-serial convert)");
        registerCustomChip("BPL3DAT", "&", "", 0x114, "W", "Bitplane 3 data (parallel-to-serial convert)");
        registerCustomChip("BPL4DAT", "&", "", 0x116, "W", "Bitplane 4 data (parallel-to-serial convert)");
        registerCustomChip("BPL5DAT", "&", "", 0x118, "W", "Bitplane 5 data (parallel-to-serial convert)");
        registerCustomChip("BPL6DAT", "&", "", 0x11A, "W", "Bitplane 6 data (parallel-to-serial convert)");
        registerCustomChip("SPR0PTH", "+", "", 0x120, "W", "Sprite 0 pointer (high 3 bits)");
        registerCustomChip("SPR0PTL", "+", "", 0x122, "W", "Sprite 0 pointer (low 15 bits)");
        registerCustomChip("SPR1PTH", "+", "", 0x124, "W", "Sprite 1 pointer (high 3 bits)");
        registerCustomChip("SPR1PTL", "+", "", 0x126, "W", "Sprite 1 pointer (low 15 bits)");
        registerCustomChip("SPR2PTH", "+", "", 0x128, "W", "Sprite 2 pointer (high 3 bits)");
        registerCustomChip("SPR2PTL", "+", "", 0x12A, "W", "Sprite 2 pointer (low 15 bits)");
        registerCustomChip("SPR3PTH", "+", "", 0x12C, "W", "Sprite 3 pointer (high 3 bits)");
        registerCustomChip("SPR3PTL", "+", "", 0x12E, "W", "Sprite 3 pointer (low 15 bits)");
        registerCustomChip("SPR4PTH", "+", "", 0x130, "W", "Sprite 4 pointer (high 3 bits)");
        registerCustomChip("SPR4PTL", "+", "", 0x132, "W", "Sprite 4 pointer (low 15 bits)");
        registerCustomChip("SPR5PTH", "+", "", 0x134, "W", "Sprite 5 pointer (high 3 bits)");
        registerCustomChip("SPR5PTL", "+", "", 0x136, "W", "Sprite 5 pointer (low 15 bits)");
        registerCustomChip("SPR6PTH", "+", "", 0x138, "W", "Sprite 6 pointer (high 3 bits)");
        registerCustomChip("SPR6PTL", "+", "", 0x13A, "W", "Sprite 6 pointer (low 15 bits)");
        registerCustomChip("SPR7PTH", "+", "", 0x13C, "W", "Sprite 7 pointer (high 3 bits)");
        registerCustomChip("SPR7PTL", "+", "", 0x13E, "W", "Sprite 7 pointer (low 15 bits)");
        registerCustomChip("SPR0POS", "%", "", 0x140, "W", "Sprite 0 vert-horiz start position data");
        registerCustomChip("SPR0CTL", "%", "", 0x142, "W", "Sprite 0 vert stop position and control data");
        registerCustomChip("SPR0DATA", "%", "", 0x144, "W", "Sprite 0 image data register A");
        registerCustomChip("SPR0DATB", "%", "", 0x146, "W", "Sprite 0 image data register B");
        registerCustomChip("SPR1POS", "%", "", 0x148, "W", "Sprite 1 vert-horiz start position data");
        registerCustomChip("SPR1CTL", "%", "", 0x14A, "W", "Sprite 1 vert stop position and control data");
        registerCustomChip("SPR1DATA", "%", "", 0x14C, "W", "Sprite 1 image data register A");
        registerCustomChip("SPR1DATB", "%", "", 0x14E, "W", "Sprite 1 image data register B");
        registerCustomChip("SPR2POS", "%", "", 0x150, "W", "Sprite 2 vert-horiz start position data");
        registerCustomChip("SPR2CTL", "%", "", 0x152, "W", "Sprite 2 vert stop position and control data");
        registerCustomChip("SPR2DATA", "%", "", 0x154, "W", "Sprite 2 image data register A");
        registerCustomChip("SPR2DATB", "%", "", 0x156, "W", "Sprite 2 image data register B");
        registerCustomChip("SPR3POS", "%", "", 0x158, "W", "Sprite 3 vert-horiz start position data");
        registerCustomChip("SPR3CTL", "%", "", 0x15A, "W", "Sprite 3 vert stop position and control data");
        registerCustomChip("SPR3DATA", "%", "", 0x15C, "W", "Sprite 3 image data register A");
        registerCustomChip("SPR3DATB", "%", "", 0x15E, "W", "Sprite 3 image data register B");
        registerCustomChip("SPR4POS", "%", "", 0x160, "W", "Sprite 4 vert-horiz start position data");
        registerCustomChip("SPR4CTL", "%", "", 0x162, "W", "Sprite 4 vert stop position and control data");
        registerCustomChip("SPR4DATA", "%", "", 0x164, "W", "Sprite 4 image data register A");
        registerCustomChip("SPR4DATB", "%", "", 0x166, "W", "Sprite 4 image data register B");
        registerCustomChip("SPR5POS", "%", "", 0x168, "W", "Sprite 5 vert-horiz start position data");
        registerCustomChip("SPR5CTL", "%", "", 0x16A, "W", "Sprite 5 vert stop position and control data");
        registerCustomChip("SPR5DATA", "%", "", 0x16C, "W", "Sprite 5 image data register A");
        registerCustomChip("SPR5DATB", "%", "", 0x16E, "W", "Sprite 5 image data register B");
        registerCustomChip("SPR6POS", "%", "", 0x170, "W", "Sprite 6 vert-horiz start position data");
        registerCustomChip("SPR6CTL", "%", "", 0x172, "W", "Sprite 6 vert stop position and control data");
        registerCustomChip("SPR6DATA", "%", "", 0x174, "W", "Sprite 6 image data register A");
        registerCustomChip("SPR6DATB", "%", "", 0x176, "W", "Sprite 6 image data register B");
        registerCustomChip("SPR7POS", "%", "", 0x178, "W", "Sprite 7 vert-horiz start position data");
        registerCustomChip("SPR7CTL", "%", "", 0x17A, "W", "Sprite 7 vert stop position and control data");
        registerCustomChip("SPR7DATA", "%", "", 0x17C, "W", "Sprite 7 image data register A");
        registerCustomChip("SPR7DATB", "%", "", 0x17E, "W", "Sprite 7 image data register B");
        registerCustomChip("COLOR00", "", "", 0x180, "W", "Color table 00");
        registerCustomChip("COLOR01", "", "", 0x182, "W", "Color table 01");
        registerCustomChip("COLOR02", "", "", 0x184, "W", "Color table 02");
        registerCustomChip("COLOR03", "", "", 0x186, "W", "Color table 03");
        registerCustomChip("COLOR04", "", "", 0x188, "W", "Color table 04");
        registerCustomChip("COLOR05", "", "", 0x18A, "W", "Color table 05");
        registerCustomChip("COLOR06", "", "", 0x18C, "W", "Color table 06");
        registerCustomChip("COLOR07", "", "", 0x18E, "W", "Color table 07");
        registerCustomChip("COLOR08", "", "", 0x190, "W", "Color table 08");
        registerCustomChip("COLOR09", "", "", 0x192, "W", "Color table 09");
        registerCustomChip("COLOR10", "", "", 0x194, "W", "Color table 10");
        registerCustomChip("COLOR11", "", "", 0x196, "W", "Color table 11");
        registerCustomChip("COLOR12", "", "", 0x198, "W", "Color table 12");
        registerCustomChip("COLOR13", "", "", 0x19A, "W", "Color table 13");
        registerCustomChip("COLOR14", "", "", 0x19C, "W", "Color table 14");
        registerCustomChip("COLOR15", "", "", 0x19E, "W", "Color table 15");
        registerCustomChip("COLOR16", "", "", 0x1A0, "W", "Color table 16");
        registerCustomChip("COLOR17", "", "", 0x1A2, "W", "Color table 17");
        registerCustomChip("COLOR18", "", "", 0x1A4, "W", "Color table 18");
        registerCustomChip("COLOR19", "", "", 0x1A6, "W", "Color table 19");
        registerCustomChip("COLOR20", "", "", 0x1A8, "W", "Color table 20");
        registerCustomChip("COLOR21", "", "", 0x1AA, "W", "Color table 21");
        registerCustomChip("COLOR22", "", "", 0x1AC, "W", "Color table 22");
        registerCustomChip("COLOR23", "", "", 0x1AE, "W", "Color table 23");
        registerCustomChip("COLOR24", "", "", 0x1B0, "W", "Color table 24");
        registerCustomChip("COLOR25", "", "", 0x1B2, "W", "Color table 25");
        registerCustomChip("COLOR26", "", "", 0x1B4, "W", "Color table 26");
        registerCustomChip("COLOR27", "", "", 0x1B6, "W", "Color table 27");
        registerCustomChip("COLOR28", "", "", 0x1B8, "W", "Color table 28");
        registerCustomChip("COLOR29", "", "", 0x1BA, "W", "Color table 29");
        registerCustomChip("COLOR30", "", "", 0x1BC, "W", "Color table 30");
        registerCustomChip("COLOR31", "", "", 0x1BE, "W", "Color table 31");
        registerCustomChip("HTOTAL", "", "", 0x1C0, "W", "Highest number count, horiz line (VARBEAMEN=1)");
        registerCustomChip("HSSTOP", "", "", 0x1C2, "W", "Horizontal line position for HSYNC stop");
        registerCustomChip("HBSTRT", "", "", 0x1C4, "W", "Horizontal line position for HBLANK start");
        registerCustomChip("HBSTOP", "", "", 0x1C6, "W", "Horizontal line position for HBLANK stop");
        registerCustomChip("VTOTAL", "", "", 0x1C8, "W", "Highest numbered vertical line (VARBEAMEN=1)");
        registerCustomChip("VSSTOP", "", "", 0x1CA, "W", "Vertical line position for VSYNC stop");
        registerCustomChip("VBSTRT", "", "", 0x1CC, "W", "Vertical line for VBLANK start");
        registerCustomChip("VBSTOP", "", "", 0x1CE, "W", "Vertical line for VBLANK stop");
        registerCustomChip("BEAMCON0", "", "", 0x1DC, "W", "Beam counter control register (SHRES,PAL)");
        registerCustomChip("HSSTRT", "", "", 0x1DE, "W", "Horizontal sync start (VARHSY)");
        registerCustomChip("VSSTRT", "", "", 0x1E0, "W", "Vertical sync start   (VARVSY)");
        registerCustomChip("HCENTER", "", "", 0x1E2, "W", "Horizontal position for Vsync on interlace");
        registerCustomChip("DIWHIGH", "", "", 0x1E4, "W", "Display window -  upper bits for start, stop");
    }

    @Override
    public RegisterDescription resolve(int address)
    {
        return registerDescriptions.get(address);
    }

    @Override
    public RegisterDescription resolve(int addressRegister, int offset)
    {
        return resolve(emulator.cpu.addressRegisters[addressRegister] + offset);
    }
}