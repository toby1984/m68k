package de.codersourcery.m68k.assembler.arch;

public enum Field
{
    SRC_VALUE('s'),
    SRC_MODE('m'),
    DST_VALUE('D'),
    DST_MODE('M'),
    SIZE('S'),
    OP_CODE('o'),
    /**
     * Dummy field that indicates
     * the {@link InstructionEncoding.IBitMapping} requires no
     * input value at all.
     */
    NONE('x');

    public final char c;

    private Field(char c) {
        this.c = c;
    }

    public static Field getField(char c)
    {
        switch(c) {
            case 's': return SRC_VALUE;
            case 'm': return SRC_MODE;
            case 'D': return DST_VALUE;
            case 'M': return DST_MODE;
            case 'S': return SIZE;
            case 'o': return OP_CODE;
            default:
                return null;
        }
    }
}
