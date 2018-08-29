package de.codersourcery.m68k.emulator.memory;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;

public class Blitter extends MemoryPage
{
    /*
Register	Name	description
$dff000	BLTDDAT	Blitter destination early read (unusable)
$dff002	DMACONR	DMA control (and blitter status) read
$dff01c	INTENAR	Interrupt enable bits read
$dff01e	INTREQR	Interrupt request bits read
----
$dff040	BLTCON0	Blitter control reg 0
$dff042	BLTCON1	Blitter control reg 1
$dff044	BLTAFWM	Blitter first word mask for source A
$dff046	BLTALWM	Blitter last word mask for source A
$dff048	BLTCPTH	Blitter pointer to source C (high 5 bits)
$dff04a	BLTCPTL	Blitter pointer to source C (low 15 bits)
$dff04c	BLTBPTH	Blitter pointer to source B (high 5 bits)
$dff04e	BLTBPTL	Blitter pointer to source B (low 15 bits)
$dff050	BLTAPTH	Blitter pointer to source A (high 5 bits)
$dff052	BLTAPTL	Blitter pointer to source A (low 15 bits)
$dff054	BLTDPTH	Blitter pointer to destination D (high 5 bits)
$dff056	BLTDPTL	Blitter pointer to destination D (low 15 bits)
$dff058	BLTSIZE	Blitter start and size (win/width, height)
$dff05a	BLTCON0L	Blitter control 0 lower 8 bits (minterms)
$dff05c	BLTSIZV	Blitter V size (for 15 bit vert size)
$dff05e	BLTSIZH	ECS: Blitter H size & start (for 11 bit H size)
$dff060	BLTCMOD	Blitter modulo for source C
$dff062	BLTBMOD	Blitter modulo for source B
$dff064	BLTAMOD	Blitter modulo for source A
$dff066	BLTDMOD	Blitter modulo for destination D
$dff070	BLTCDAT	Blitter source C data reg
$dff072	BLTBDAT	Blitter source B data reg
$dff074	BLTADAT	Blitter source A data reg
---
$dff096	DMACON	DMA control write (clear or set)
$dff09a	INTENA	Interrupt enable bits (clear or set bits)
$dff09c	INTREQ	Interrupt request bits (clear or set bits)
 */

    public static final int BLTCON0  = 0x00; //  0xdff040	Blitter control reg 0
    public static final int BLTCON1  = 0x02; //  0xdff042	Blitter control reg 1
    public static final int BLTAFWM  = 0x04; //  0xdff044	Blitter first word mask for source A
    public static final int BLTALWM  = 0x06; //  0xdff046	Blitter last word mask for source A
    public static final int BLTCPTH  = 0x08; //  0xdff048	Blitter pointer to source C (high 5 bits)
    public static final int BLTCPTL  = 0x0a; //  0xdff04a	Blitter pointer to source C (low 15 bits)
    public static final int BLTBPTH  = 0x0c; //  0xdff04c	Blitter pointer to source B (high 5 bits)
    public static final int BLTBPTL  = 0x0e; //  0xdff04e	Blitter pointer to source B (low 15 bits)
    public static final int BLTAPTH  = 0x10; //  0xdff050	Blitter pointer to source A (high 5 bits)
    public static final int BLTAPTL  = 0x12; //  0xdff052	Blitter pointer to source A (low 15 bits)
    public static final int BLTDPTH  = 0x14; //  0xdff054	Blitter pointer to destination D (high 5 bits)
    public static final int BLTDPTL  = 0x16; //  0xdff056	Blitter pointer to destination D (low 15 bits)
    public static final int BLTSIZE  = 0x18; //  0xdff058	Blitter start and size (win/width, height)
    public static final int BLTCON0L = 0x1a; //  0xdff05a	Blitter control 0 lower 8 bits (minterms)
    public static final int BLTSIZV  = 0x1c; //  0xdff05c	Blitter V size (for 15 bit vert size)
    public static final int BLTSIZH  = 0x1e; //  0xdff05e	ECS: Blitter H size & start (for 11 bit H size)
    public static final int BLTCMOD  = 0x20; //  0xdff060	Blitter modulo for source C
    public static final int BLTBMOD  = 0x22; //  0xdff062	Blitter modulo for source B
    public static final int BLTAMOD  = 0x24; //  0xdff064	Blitter modulo for source A
    public static final int BLTDMOD  = 0x26; //  0xdff066	Blitter modulo for destination D
    public static final int BLTCDAT  = 0x30; //  0xdff070	Blitter source C data reg
    public static final int BLTBDAT  = 0x32; //  0xdff072	Blitter source B data reg
    public static final int BLTADAT  = 0x34; //  0xdff074	Blitter source A data reg

    private int bltcon0;
    private int bltcon1;
    private int bltafwm;
    private int bltalwm;
    private int bltcpth;
    private int bltcptl;
    private int bltbpth;
    private int bltbptl;
    private int bltapth;
    private int bltaptl;
    private int bltdpth;
    private int bltdptl;
    private int bltsize;
    private int bltcon0l;
    private int bltsizv;
    private int bltsizh;
    private int bltcmod;
    private int bltbmod;
    private int bltamod;
    private int bltdmod;
    private int bltcdat;
    private int bltbdat;
    private int bltadat;

    @Override
    public byte readByte(int offset)
    {
        switch( offset )
        {
            case 0x00: return hi(bltcon0);
            case 0x01: return lo(bltcon0);
            case 0x02: return hi(bltcon1);
            case 0x03: return lo(bltcon1);
            case 0x04: return hi(bltafwm);
            case 0x05: return lo(bltafwm);
            case 0x06: return hi(bltalwm);
            case 0x07: return lo(bltalwm);
            case 0x08: return hi(bltcpth);
            case 0x09: return lo(bltcpth);
            case 0x0a: return hi(bltcptl);
            case 0x0b: return lo(bltcptl);
            case 0x0c: return hi(bltbpth);
            case 0x0d: return lo(bltbpth);
            case 0x0e: return hi(bltbptl);
            case 0x0f: return lo(bltbptl);
            case 0x10: return hi(bltapth);
            case 0x11: return lo(bltapth);
            case 0x12: return hi(bltaptl);
            case 0x13: return lo(bltaptl);
            case 0x14: return hi(bltdpth);
            case 0x15: return lo(bltdpth);
            case 0x16: return hi(bltdptl);
            case 0x17: return lo(bltdptl);
            case 0x18: return hi(bltsize);
            case 0x19: return lo(bltsize);
            case 0x1a: return hi(bltcon0l);
            case 0x1b: return lo(bltcon0l);
            case 0x1c: return hi(bltsizv);
            case 0x1d: return lo(bltsizv);
            case 0x1e: return hi(bltsizh);
            case 0x1f: return lo(bltsizh);
            case 0x20: return hi(bltcmod);
            case 0x21: return lo(bltcmod);
            case 0x22: return hi(bltbmod);
            case 0x23: return lo(bltbmod);
            case 0x24: return hi(bltamod);
            case 0x25: return lo(bltamod);
            case 0x26: return hi(bltdmod);
            case 0x27: return lo(bltdmod);
            case 0x30: return hi(bltcdat);
            case 0x31: return lo(bltcdat);
            case 0x32: return hi(bltbdat);
            case 0x33: return lo(bltbdat);
            case 0x34: return hi(bltadat);
            case 0x35: return lo(bltadat);
        }
        throw new RuntimeException( "Unhandled address: "+offset );
    }

    @Override
    public byte readByteNoSideEffects(int offset)
    {
        return readByte( offset );
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {
        switch( offset )
        {
            case 0x00: bltcon0 =sethi(bltcon0, value); break;
            case 0x01: bltcon0 =setlo(bltcon0, value); break;
            case 0x02: bltcon1 =sethi(bltcon1, value); break;
            case 0x03: bltcon1 =setlo(bltcon1, value); break;
            case 0x04: bltafwm =sethi(bltafwm, value); break;
            case 0x05: bltafwm =setlo(bltafwm, value); break;
            case 0x06: bltalwm =sethi(bltalwm, value); break;
            case 0x07: bltalwm =setlo(bltalwm, value); break;
            case 0x08: bltcpth =sethi(bltcpth, value); break;
            case 0x09: bltcpth =setlo(bltcpth, value); break;
            case 0x0a: bltcptl =sethi(bltcptl, value); break;
            case 0x0b: bltcptl =setlo(bltcptl, value); break;
            case 0x0c: bltbpth =sethi(bltbpth, value); break;
            case 0x0d: bltbpth =setlo(bltbpth, value); break;
            case 0x0e: bltbptl =sethi(bltbptl, value); break;
            case 0x0f: bltbptl =setlo(bltbptl, value); break;
            case 0x10: bltapth =sethi(bltapth, value); break;
            case 0x11: bltapth =setlo(bltapth, value); break;
            case 0x12: bltaptl =sethi(bltaptl, value); break;
            case 0x13: bltaptl =setlo(bltaptl, value); break;
            case 0x14: bltdpth =sethi(bltdpth, value); break;
            case 0x15: bltdpth =setlo(bltdpth, value); break;
            case 0x16: bltdptl =sethi(bltdptl, value); break;
            case 0x17: bltdptl =setlo(bltdptl, value); break;
            case 0x18: bltsize =sethi(bltsize, value); break;
            case 0x19: bltsize =setlo(bltsize, value); break;
            case 0x1a: bltcon0l=sethi(bltcon0l, value); break;
            case 0x1b: bltcon0l=setlo(bltcon0l, value); break;
            case 0x1c: bltsizv =sethi(bltsizv, value); break;
            case 0x1d: bltsizv =setlo(bltsizv, value); break;
            case 0x1e: bltsizh =sethi(bltsizh, value); break;
            case 0x1f: bltsizh =setlo(bltsizh, value); break;
            case 0x20: bltcmod =sethi(bltcmod, value); break;
            case 0x21: bltcmod =setlo(bltcmod, value); break;
            case 0x22: bltbmod =sethi(bltbmod, value); break;
            case 0x23: bltbmod =setlo(bltbmod, value); break;
            case 0x24: bltamod =sethi(bltamod, value); break;
            case 0x25: bltamod =setlo(bltamod, value); break;
            case 0x26: bltdmod =sethi(bltdmod, value); break;
            case 0x27: bltdmod =setlo(bltdmod, value); break;
            case 0x30: bltcdat =sethi(bltcdat, value); break;
            case 0x31: bltcdat =setlo(bltcdat, value); break;
            case 0x32: bltbdat =sethi(bltbdat, value); break;
            case 0x33: bltbdat =setlo(bltbdat, value); break;
            case 0x34: bltadat =sethi(bltadat, value); break;
            case 0x35: bltadat =setlo(bltadat, value); break;
            default:
                throw new RuntimeException( "Unhandled address: "+offset );
        }
    }

    private static int setlo(int register,int value) {
        return (register & 0xff00) | (value & 0xff);
    }

    private static int sethi(int register,int value) {
        return (register & 0x00ff) | ((value & 0xff) << 8);
    }

    private static byte lo(int register) {
        return (byte) ((register & 0xff00) >>> 8);
    }

    private static byte hi(int register) {
        return (byte) register;
    }

    private boolean isLineMode() {
        return (bltcon1 & 1) != 0 ;
    }

    private boolean isAreaMode() {
        return (bltcon1 & 1) == 0 ;
    }

    public void tick()
    {

    }

    /*


         These two control registers are used together to control blitter
         operations. There are 2 basic modes, are and line, which are
         selected by bit 0 of BLTCON1, as show below.


         +--------------------------+
         | AREA MODE ("normal")     |
         +------+---------+---------+
         | BIT# | BLTCON0 | BLTCON1 |
         +------+---------+---------+
         | 15   | ASH3    | BSH3    | ASH = Shift value of A source
         | 14   | ASH2    | BSH2    | BSH = Shift value of B source
         | 13   | ASH1    | BSH1    |
         | 12   | ASA0    | BSH0    |
         | 11   | USEA    | 0       | USEA = Mode control bit to use source A
         | 10   | USEB    | 0       | USEB = Mode control bit to use source B
         | 09   | USEC    | 0       | USEC = Mode control bit to use source C
         | 08   | USED    | 0       | USED = Mode control bit to use destination D
         | 07   | LF7(ABC)| DOFF    | DOFF = Disables the D output- for external ALUs (Hi-res chips only)
         | 06   | LF6(ABc)| 0       |
         | 05   | LF5(AbC)| 0       |
         | 04   | LF4(Abc)| EFE     | EFE  = Exclusive fill enable
         | 03   | LF3(aBC)| IFE     | IFE  = Inclusive fill enable
         | 02   | LF2(aBc)| FCI     | FCI  = Fill carry input
         | 01   | LF1(abC)| DESC    | DESC = Descending address mode
         | 00   | LF0(abc)| LINE(=0)|
         +------+---------+---------+

         +---------------------------+
         | LINE MODE (line draw)     |
         +------+---------+----------+
         | BIT# | BLTCON0 | BLTCON1  |
         +------+---------+----------+
         | 15   | ASH3    | BSH3     | ASH = Shift value of A source
         | 14   | ASH2    | BSH2     | BSH = Shift value of B source
         | 13   | ASH1    | BSH1     |
         | 12   | ASH0    | BSH0     |
         | 11   | 1       | 0        |
         | 10   | 0       | 0        |
         | 09   | 1       | 0        |
         | 08   | 1       | 0        |
         | 07   | LF7(ABC)| DOFF     | DOFF = Disables the D output- for external ALUs (Hi-res chips only)
         | 06   | LF6(ABc)| SIGN     | Sign flag (0 Reserved for new mode)
         | 05   | LF5(AbC)| OVF      |
         | 04   | LF4(Abc)| SUD      | SUD  = Sometimes up or down (=AUD*)
         | 03   | LF3(aBC)| SUL      | SUL  = Sometimes up or left
         | 02   | LF2(aBc)| AUL      | AUL  = Always up or left
         | 01   | LF1(abC)| SING     | SING = Single bit per horizontal line for use with subsequent area fill
         | 00   | LF0(abc)| LINE(=1) |
+------+---------+----------+


         ASH 3-0 Shift value of A source
         BSH 3-0 Shift value of B source
         USEA Mode control bit to use source A
         USEB Mode control bit to use source B
         USEC Mode control bit to use source C
         USED Mode control bit to use destination D
         LF 7-0 Logic function minterm select lines
         EFE Exclusive fill enable
         IFE Inclusive fill enable
         FCI Fill carry input
         DESC Descending (decreasing address) control bit
         LINE Line mode control bit (set to 0 on normal mode)
         DOFF      Disables the D output- for external ALUs
                   The cycle occurs normally, but the data
                   bus is tristate (hires chips only)


         LINE DRAW     LINE MODE (line draw)
         LINE DRAW
         LINE DRAW    BIT# BLTCON0  BLTCON1
         LINE DRAW
         LINE DRAW     15   START3  TEXTURE3
         LINE DRAW     14   START2  TEXTURE2
         LINE DRAW     13   STARTl  TEXTURE1
         LINE DRAW     12   START0  TEXTURE0
         LINE DRAW     11   1       0
         LINE DRAW     10   0       0
         LINE DRAW     09   1       0
         LINE DRAW     08   1       0
         LINE DRAW     07   LF7     0
         LINE DRAW     06   LF6     SIGN
         LINE DRAW     05   LF5     0 (Reserved)
         LINE DRAW     04   LF4     SUD
         LINE DRAW     03   LF3     SUL
         LINE DRAW     02   LF2     AUL
         LINE DRAW     01   LF1     SING
         LINE DRAW     00   LF0     LINE(=1)
         LINE DRAW
         LINE DRAW     START 3-0 Starting point of line
         LINE DRAW               (0 thru 15 hex)


         LINE DRAW     LF7-0 Logic function minterm
         LINE DRAW     select lines should be preloaded
         LINE DRAW     with 4A to select the equation
         LINE DRAW     D=(AC+ABC). Since A contains a
         LINE DRAW     single bit true (8000), most bits
         LINE DRAW     will pass the C field unchanged
         LINE DRAW     (not A and C), hut one bit will
         LINE DRAW     invert the C field and combine it
         LINE DRAW     with texture (A and B and not C).
         LINE DRAW     The A bit is automatically moved
         LINE DRAW     across the word by the hardware.
         LINE DRAW
         LINE DRAW     LINE Line mode control bit (set to 1)
         LINE DRAW     SIGN Sign flag
         LINE DRAW     0 Reserved for new mode
         LINE DRAW     SING Single bit per horizontal line for use with subsequent area fill
         LINE DRAW     SUD Sometimes up or down (=AUD*)
         LINE DRAW     SUL Sometimes up or left
         LINE DRAW     AUL Always up or left


         LINE DRAW     The 3 bits above select the octant
         LINE DRAW     for line drawing:


         LINE DRAW     OCT     SUD SUL AUL
         LINE DRAW
         LINE DRAW      0       1   1   0
         LINE DRAW      1       0   0   1
         LINE DRAW      2       0   1   1
         LINE DRAW      3       1   1   1
         LINE DRAW      4       1   0   1
         LINE DRAW      5       0   1   0
         LINE DRAW      6       0   0   0
         LINE DRAW      7       1   0   0

         LINE DRAW The "B" source is used for
         LINE DRAW texturing the drawn lines.
     */
}