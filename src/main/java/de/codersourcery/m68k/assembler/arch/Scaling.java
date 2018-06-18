package de.codersourcery.m68k.assembler.arch;

/**
 * Index register scaling factor.
 * @author tobias.gierke@code-sourcery.de
 */
public enum Scaling
{
    /** Scaling x1 */
    IDENTITY(0b00),
    /** Scaling x2 */
    TWO(0b01),
    /** Scaling x4 */
    FOUR(0b10),
    /** Scaling x8 */
    EIGHT(0b11);

    public final int bits;

    private Scaling(int bits) {
        this.bits = bits;
    }
}