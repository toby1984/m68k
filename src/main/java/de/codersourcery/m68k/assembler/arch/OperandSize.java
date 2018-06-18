package de.codersourcery.m68k.assembler.arch;

/**
 * Operand size (byte,word or long).
 * @author tobias.gierke@code-sourcery.de
 */
public enum OperandSize
{
    BYTE(0b00),
    WORD(0b01),
    LONG(0b10);

    /**
     * The bit pattern to encode into a generated instruction when this size is used.
     */
    public final int bits;

    private OperandSize(int bits) {
        this.bits = bits;
    }

    /**
     * Returns the bit pattern to encode into a generated instruction when this size is used.
     * @return
     */
    public int bits() {
        return bits;
    }

    /**
     * Returns the size in bits for this operand.
     *
     * @return
     */
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
