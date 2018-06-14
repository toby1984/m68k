package de.codersourcery.m68k.assembler.arch;

public enum Scaling
{
    IDENTITY(0b00),
    TWO(0b01),
    FOUR(0b10),
    EIGHT(0b11);

    public final int bits;

    private Scaling(int bits) {
        this.bits = bits;
    }
}