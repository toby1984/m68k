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
            return 512*1024; // 512kB
        }

        @Override
        public CPUType getCPUType()
        {
            return CPUType.M68000;
        }
    };

    public abstract int getChipRAMSize();
    public abstract int getKickRomStartAddress();
    public abstract int getKickRomEndAddress();
    public abstract int getKickRomSize();
    public abstract CPUType getCPUType();

}
