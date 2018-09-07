package de.codersourcery.m68k.emulator.memory;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;

public class Video extends MemoryPage
{
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

    @Override
    public byte readByte(int offset)
    {
        return 0;
    }

    @Override
    public byte readByteNoSideEffects(int offset)
    {
        return 0;
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {

    }
    /*
 BPL1PTH   +  0E0  W   A       Bitplane 1 pointer (high 3 bits)
 BPL1PTL   +  0E2  W   A       Bitplane 1 pointer (low 15 bits)
 BPL2PTH   +  0E4  W   A       Bitplane 2 pointer (high 3 bits)
 BPL2PTL   +  0E6  W   A       Bitplane 2 pointer (low 15 bits)
 BPL3PTH   +  0E8  W   A       Bitplane 3 pointer (high 3 bits)
 BPL3PTL   +  0EA  W   A       Bitplane 3 pointer (low 15 bits)
 BPL4PTH   +  0EC  W   A       Bitplane 4 pointer (high 3 bits)
 BPL4PTL   +  0EE  W   A       Bitplane 4 pointer (low 15 bits)
 BPL5PTH   +  0F0  W   A       Bitplane 5 pointer (high 3 bits)
 BPL5PTL   +  0F2  W   A       Bitplane 5 pointer (low 15 bits)
 BPL6PTH   +  0F4  W   A       Bitplane 6 pointer (high 3 bits)
 BPL6PTL   +  0F6  W   A       Bitplane 6 pointer (low 15 bits)
              0F8
              0FA
              0FC
              0FE
 BPLCON0      100  W   AD( E ) Bitplane control register (misc. control bits)
 BPLCON1      102  W   D       Bitplane control reg. (scroll value PF1, PF2)
 BPLCON2      104  W   D( E )  Bitplane control reg. (priority control)
 BPLCON3      106  W   D( E )  Bitplane control (enhanced features)

 BPL1MOD      108  W   A       Bitplane modulo (odd planes)
 BPL2MOD      10A  W   A       Bitplane modulo (even planes)
              10C
              10E
 BPL1DAT   &  110  W   D       Bitplane 1 data (parallel-to-serial convert)
 BPL2DAT   &  112  W   D       Bitplane 2 data (parallel-to-serial convert)
 BPL3DAT   &  114  W   D       Bitplane 3 data (parallel-to-serial convert)
 BPL4DAT   &  116  W   D       Bitplane 4 data (parallel-to-serial convert)
 BPL5DAT   &  118  W   D       Bitplane 5 data (parallel-to-serial convert)
 BPL6DAT   &  11A  W   D       Bitplane 6 data (parallel-to-serial convert)
              11C
              11E
 SPR0PTH   +  120  W   A       Sprite 0 pointer (high 3 bits)
 SPR0PTL   +  122  W   A       Sprite 0 pointer (low 15 bits)
 SPR1PTH   +  124  W   A       Sprite 1 pointer (high 3 bits)
 SPR1PTL   +  126  W   A       Sprite 1 pointer (low 15 bits)
 SPR2PTH   +  128  W   A       Sprite 2 pointer (high 3 bits)
 SPR2PTL   +  12A  W   A       Sprite 2 pointer (low 15 bits)
 SPR3PTH   +  12C  W   A       Sprite 3 pointer (high 3 bits)
 SPR3PTL   +  12E  W   A       Sprite 3 pointer (low 15 bits)
 SPR4PTH   +  130  W   A       Sprite 4 pointer (high 3 bits)
 SPR4PTL   +  132  W   A       Sprite 4 pointer (low 15 bits)
 SPR5PTH   +  134  W   A       Sprite 5 pointer (high 3 bits)
 SPR5PTL   +  136  W   A       Sprite 5 pointer (low 15 bits)
 SPR6PTH   +  138  W   A       Sprite 6 pointer (high 3 bits)
 SPR6PTL   +  13A  W   A       Sprite 6 pointer (low 15 bits)
 SPR7PTH   +  13C  W   A       Sprite 7 pointer (high 3 bits)
 SPR7PTL   +  13E  W   A       Sprite 7 pointer (low 15 bits)
 SPR0POS   %  140  W   AD      Sprite 0 vert-horiz start position data
 SPR0CTL   %  142  W   AD( E ) Sprite 0 vert stop position and control data
 SPR0DATA  %  144  W   D       Sprite 0 image data register A
 SPR0DATB  %  146  W   D       Sprite 0 image data register B
 SPR1POS   %  148  W   AD      Sprite 1 vert-horiz start position data
 SPR1CTL   %  14A  W   AD      Sprite 1 vert stop position and control data
 SPR1DATA  %  14C  W   D       Sprite 1 image data register A
 SPR1DATB  %  14E  W   D       Sprite 1 image data register B
 SPR2POS   %  150  W   AD      Sprite 2 vert-horiz start position data
 SPR2CTL   %  152  W   AD      Sprite 2 vert stop position and control data
 SPR2DATA  %  154  W   D       Sprite 2 image data register A
 SPR2DATB  %  156  W   D       Sprite 2 image data register B
 SPR3POS   %  158  W   AD      Sprite 3 vert-horiz start position data
 SPR3CTL   %  15A  W   AD      Sprite 3 vert stop position and control data
 SPR3DATA  %  15C  W   D       Sprite 3 image data register A
 SPR3DATB  %  15E  W   D       Sprite 3 image data register B
 SPR4POS   %  160  W   AD      Sprite 4 vert-horiz start position data
 SPR4CTL   %  162  W   AD      Sprite 4 vert stop position and control data
 SPR4DATA  %  164  W   D       Sprite 4 image data register A
 SPR4DATB  %  166  W   D       Sprite 4 image data register B
 SPR5POS   %  168  W   AD      Sprite 5 vert-horiz start position data
 SPR5CTL   %  16A  W   AD      Sprite 5 vert stop position and control data
 SPR5DATA  %  16C  W   D       Sprite 5 image data register A
 SPR5DATB  %  16E  W   D       Sprite 5 image data register B
 SPR6POS   %  170  W   AD      Sprite 6 vert-horiz start position data
 SPR6CTL   %  172  W   AD      Sprite 6 vert stop position and control data
 SPR6DATA  %  174  W   D       Sprite 6 image data register A
 SPR6DATB  %  176  W   D       Sprite 6 image data register B
 SPR7POS   %  178  W   AD      Sprite 7 vert-horiz start position data
 SPR7CTL   %  17A  W   AD      Sprite 7 vert stop position and control data
 SPR7DATA  %  17C  W   D       Sprite 7 image data register A
 SPR7DATB  %  17E  W   D       Sprite 7 image data register B

 BIT# 15,14,13,12,11,10,09,08,07,06,05,04,03,02,01,00
      ----------- ----------- ----------- -----------
  RGB  X  X  X  X  R3 R2 R1 R0 G3 G2 G1 G0 B3 B2 B1 B0

 COLOR00      180  W   D       Color table 00
 COLOR01      182  W   D       Color table 01
 COLOR02      184  W   D       Color table 02
 COLOR03      186  W   D       Color table 03
 COLOR04      188  W   D       Color table 04
 COLOR05      18A  W   D       Color table 05
 COLOR06      18C  W   D       Color table 06
 COLOR07      18E  W   D       Color table 07
 COLOR08      190  W   D       Color table 08
 COLOR09      192  W   D       Color table 09
 COLOR10      194  W   D       Color table 10
 COLOR11      196  W   D       Color table 11
 COLOR12      198  W   D       Color table 12
 COLOR13      19A  W   D       Color table 13
 COLOR14      19C  W   D       Color table 14
 COLOR15      19E  W   D       Color table 15
 COLOR16      1A0  W   D       Color table 16
 COLOR17      1A2  W   D       Color table 17
 COLOR18      1A4  W   D       Color table 18
 COLOR19      1A6  W   D       Color table 19
 COLOR20      1A8  W   D       Color table 20
 COLOR21      1AA  W   D       Color table 21
 COLOR22      1AC  W   D       Color table 22
 COLOR23      1AE  W   D       Color table 23
 COLOR24      1B0  W   D       Color table 24
 COLOR25      1B2  W   D       Color table 25
 COLOR26      1B4  W   D       Color table 26
 COLOR27      1B6  W   D       Color table 27
 COLOR28      1B8  W   D       Color table 28
 COLOR29      1BA  W   D       Color table 29
 COLOR30      1BC  W   D       Color table 30
 COLOR31      1BE  W   D       Color table 31

 HTOTAL       1C0  W   A( E )  Highest number count, horiz line (VARBEAMEN=1)
 HSSTOP       1C2  W   A( E )  Horizontal line position for HSYNC stop
 HBSTRT       1C4  W   A( E )  Horizontal line position for HBLANK start
 HBSTOP       1C6  W   A( E )  Horizontal line position for HBLANK stop
 VTOTAL       1C8  W   A( E )  Highest numbered vertical line (VARBEAMEN=1)
 VSSTOP       1CA  W   A( E )  Vertical line position for VSYNC stop
 VBSTRT       1CC  W   A( E )  Vertical line for VBLANK start
 VBSTOP       1CE  W   A( E )  Vertical line for VBLANK stop

              1D0              Reserved
              1D2              Reserved
              1D4              Reserved
              1D6              Reserved
              1D8              Reserved
              1DA              Reserved

 BEAMCON0     1DC  W   A( E )  Beam counter control register (SHRES,PAL)
 HSSTRT       1DE  W   A( E )  Horizontal sync start (VARHSY)
 VSSTRT       1E0  W   A( E )  Vertical sync start   (VARVSY)
 HCENTER      1E2  W   A( E )  Horizontal position for Vsync on interlace
 DIWHIGH      1E4  W   AD( E ) Display window -  upper bits for start, stop
     */
}
