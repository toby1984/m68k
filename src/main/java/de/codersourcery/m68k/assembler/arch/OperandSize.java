package de.codersourcery.m68k.assembler.arch;

public enum OperandSize
{
    BYTE(0b00),
    WORD(0b01),
    LONG(0b10);

    public final int bits;

    private OperandSize(int bits) {
        this.bits = bits;
    }

    public int bits() {
        return bits;
    }

    public int sizeInBits()
    {
        switch(this)
        {
            case BYTE:
                return 8;
            case WORD:
                return 16;
            case LONG:
                return 32;
        }
        throw new RuntimeException("Unreachable code reached");
    }
}
