package de.codersourcery.m68k.assembler.arch;

public enum Scaling
{
    IDENTITY(1),
    TWO(2),
    FOUR(4),
    EIGHT(8);

    public final int value;

    private Scaling(int value) {
        this.value = value;
    }
}