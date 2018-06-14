package de.codersourcery.m68k.assembler.arch;

public enum Field
{
    // src operand
    SRC_VALUE('v'), // immediate/absolute mode value
    SRC_BASE_REGISTER('s'),
    SRC_INDEX_REGISTER('i'),
    SRC_BASE_DISPLACEMENT('b'),
    SRC_OUTER_DISPLACEMENT('x'),
    SRC_MODE('m'),
    // destination operand
    DST_VALUE('V'), // immediate/absolute mode value
    DST_BASE_REGISTER('D'),
    DST_INDEX_REGISTER('I'),
    DST_BASE_DISPLACEMENT('B'),
    DST_OUTER_DISPLACEMENT('y'),
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
            case 'v': return SRC_VALUE; // immediate/absolute mode value
            case 's': return SRC_BASE_REGISTER;
            case 'i': return SRC_INDEX_REGISTER;
            case 'b': return SRC_BASE_DISPLACEMENT; // [bd,BR,Xn.SIZE*SCALE,od]
            case 'x': return SRC_OUTER_DISPLACEMENT;
            case 'm': return SRC_MODE;
            // --
            case 'V': return DST_VALUE; // immediate/absolute mode value
            case 'D': return DST_BASE_REGISTER;
            case 'I': return DST_INDEX_REGISTER;
            case 'B': return DST_BASE_DISPLACEMENT;
            case 'y': return DST_OUTER_DISPLACEMENT;
            case 'M': return DST_MODE;
            // --
            case 'S': return SIZE;
            case 'o': return OP_CODE;
            default:
                return null;
        }
    }
}
