package de.codesourcery.m68k.assembler;

import de.codesourcery.m68k.assembler.arch.CPUType;

public class AssemblerOptions
{
    public boolean debug = false; // TODO: Change debug to 'false' when done debugging

    public CPUType cpuType = CPUType.M68000;
}
