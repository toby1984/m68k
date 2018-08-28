package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.utils.Misc;

public class Breakpoint
{
    public final int address;

    public Breakpoint(int address) {
        this.address = address;
    }

    public Breakpoint(Breakpoint other) {
        this.address = other.address;
    }

    public Breakpoint createCopy() {
        return new Breakpoint(this);
    }

    public boolean hasSameAddress(Breakpoint other) {
        return this.address == other.address;
    }

    public boolean hasSameAddress(Emulator emulator) {
        return emulator.cpu.pc == address;
    }

    @Override
    public String toString()
    {
        return "Breakpoint[ "+ Misc.hex(address)+" ]";
    }
}
