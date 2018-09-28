package de.codesourcery.m68k.emulator;

import de.codesourcery.m68k.assembler.arch.CPUType;

public enum Amiga
{
    AMIGA_500
    {
        @Override
        public int getChipRAMSize()
        {
            return 512*1024; // 512kB
        }

        @Override
        public int getKickRomStartAddress()
        {
            return 0xFC0000;
        }

        @Override
        public int getKickRomEndAddress()
        {
            return getKickRomStartAddress()+getKickRomSize();
        }

        @Override
        public int getKickRomSize()
        {
            return 256*1024; // 512kB
        }

        @Override
        public CPUType getCPUType()
        {
            return CPUType.M68000;
        }

        @Override
        public boolean isPAL()
        {
            return true;
        }

        @Override
        public boolean isNTSC()
        {
            return false;
        }

        @Override
        public float getCPUClock()
        {
            return isPAL() ? PAL_CPU_CLOCK_MHZ : NTSC_CPU_CLOCK_MHZ;
        }

        @Override
        public int getAgnusID()
        {
            /*

    8361 (Regular) or 8370 (Fat) (Agnus-NTSC) = 10
    8367 (Pal) or 8371 (Fat-Pal) (Agnus-PAL) = 00
    8372 (Fat-hr) (agnushr),thru rev4 = 20 PAL, 30 NTSC
    8372 (Fat-hr) (agnushr),rev 5 = 22 PAL, 31 NTSC
    8374 (Alice) thru rev 2 = 22 PAL, 32 NTSC
    8374 (Alice) rev 3 thru rev 4 = 23 PAL, 33 NTSC

             */
            return 0; // 8371 (Agnus-PAL)
        }
    };

    public abstract int getChipRAMSize();
    public abstract int getKickRomStartAddress();
    public abstract int getKickRomEndAddress();
    public abstract int getKickRomSize();
    public abstract CPUType getCPUType();
    public abstract boolean isPAL();
    public abstract boolean isNTSC();
    public abstract int getAgnusID();

    /**
     * Returns the CPU clock in MHz.
     *
     * @return
     */
    public abstract float getCPUClock();

    public static final float NTSC_CPU_CLOCK_MHZ = 7.15909f; // 7159090 => 139,68 ns
    public static final float PAL_CPU_CLOCK_MHZ  = 7.09379f; // 7093790 => 140,96 ns

    // 50 Hz = 0,02s
    // NTSC: 143184 cycles

    // 60 Hz = 0,0166s
    // PAL:  118237

    /**
     * Returns the number of CPU cycles per power-supply cycle.
     *
     * PAL runs at 60 Hz while NTSC runs at 50 Hz.
     * @return
     */
    public int getPowerSupplyCycles()
    {
        final double value;
        if ( isPAL() ) {
            value = (1/60.0) / (1.0 / getCPUClock()*1000000.0);
        } else
        {
            value = (1/50.0) / (1.0 / getCPUClock() * 1000000.0);
        }
        return (int) Math.round(value);
    }

    /**
     * Returns the number of CPU cycles per HSYNC.
     *
     * @return
     */
    public int getHSyncCycles()
    {
       return (int) Math.round( (1/15000.0) / ( 1.0 / getCPUClock()*1000000.0) );
    }
}