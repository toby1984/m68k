package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.assembler.arch.CPUType;

public enum Amiga
{
    AMIGA_500_PLUS
    {
        @Override
        public int getChipRAMSize()
        {
            return 512*1024; // 512kB
        }

        @Override
        public int getKickRomStartAddress()
        {
            return 0xF80000;
        }

        @Override
        public int getKickRomSize()
        {
            return 256*1024; // 256kB
        }

        @Override
        public CPUType getCPUType()
        {
            return CPUType.M68000;
        }
    };

    public abstract int getChipRAMSize();
    public abstract int getKickRomStartAddress();
    public abstract int getKickRomSize();
    public abstract CPUType getCPUType();

}
