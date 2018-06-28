package de.codersourcery.m68k.assembler.arch;

public enum AddressingModeKind
{
    DATA(1<<0),
    MEMORY(1<<1),
    CONTROL(1<<2),
    ALTERABLE(1<<3);

    public final int bits;

    AddressingModeKind(int bits) {
        this.bits = bits;
    }
}
