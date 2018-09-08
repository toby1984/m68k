package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.assembler.arch.CPUType;

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
        public float getCPUClock()
        {
            return isPAL() ? PAL_CPU_CLOCK_MHZ : NTSC_CPU_CLOCK_MHZ;
        }
    };

    public abstract int getChipRAMSize();
    public abstract int getKickRomStartAddress();
    public abstract int getKickRomEndAddress();
    public abstract int getKickRomSize();
    public abstract CPUType getCPUType();
    public abstract boolean isPAL();

    /**
     * Returns the CPU clock in MHz.
     *
     * @return
     */
    public abstract float getCPUClock();

    public static final float NTSC_CPU_CLOCK_MHZ = 7.15909f;
    public static final float PAL_CPU_CLOCK_MHZ  = 7.09379f;
}