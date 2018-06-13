package de.codersourcery.m68k.assembler.arch;

public enum Field
{
    // src operand
    SRC_VALUE('s'),
    SRC_BASE_DISPLACEMENT('b'), // [bd,BR,Xn.SIZE*SCALE,od]
    SRC_MODE('m'),
    // destination operand
    DST_VALUE('D'),
    DST_BASE_DISPLACEMENT('B'), // [bd,BR,Xn.SIZE*SCALE,od]
    DST_MODE('M'),
    // misc
    SIZE('S'), // operation size: .b/.w/.l
    OP_CODE('o'), // bits 15-12
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
            case 'b': return SRC_BASE_DISPLACEMENT; // [bd,BR,Xn.SIZE*SCALE,od]
            case 'm': return SRC_MODE;
            // --
            case 'D': return DST_VALUE;
            case 'B': return DST_BASE_DISPLACEMENT;
            case 'M': return DST_MODE;
            // --
            case 'S': return SIZE;
            case 'o': return OP_CODE;
            default:
                return null;
        }
    }
}
