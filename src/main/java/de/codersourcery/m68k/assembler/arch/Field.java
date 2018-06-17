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
    SRC_REGISTER_KIND('r'),
    SRC_INDEX_SIZE('q'),
    SRC_SCALE('e'),
    SRC_8_BIT_DISPLACEMENT('w'),
    // destination operand
    DST_VALUE('V'), // immediate/absolute mode value
    DST_BASE_REGISTER('D'),
    DST_INDEX_REGISTER('I'),
    DST_BASE_DISPLACEMENT('B'),
    DST_OUTER_DISPLACEMENT('Y'),
    DST_MODE('M'),
    DST_REGISTER_KIND('R'), // (bd,br,{Rx},od)
    DST_INDEX_SIZE('Q'), // (bd,br,Rx{.w|.l},od)
    DST_SCALE('E'), // (bd,br,Rx.w{*4},od)
    DST_8_BIT_DISPLACEMENT('W'), // (bd,br,Rx,{od}
    // misc
    SIZE('S'), // operation size: .b/.w/.l
    OP_CODE('o'), // bits 15-12
    EXG_DATA_REGISTER('k'), // data register if EXG used with registers of different types, otherwise either the src data or src address register
    EXG_ADDRESS_REGISTER('l'), // address register if EXG used with registers of different types, otherwise either the dst data or dst address register
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
            case 'r': return SRC_REGISTER_KIND;
            case 'q': return SRC_INDEX_SIZE;
            case 'e': return SRC_SCALE;
            case 'w': return SRC_8_BIT_DISPLACEMENT;
            // --
            case 'V': return DST_VALUE; // immediate/absolute mode value
            case 'D': return DST_BASE_REGISTER;
            case 'I': return DST_INDEX_REGISTER;
            case 'B': return DST_BASE_DISPLACEMENT;
            case 'Y': return DST_OUTER_DISPLACEMENT;
            case 'M': return DST_MODE;
            case 'R': return DST_REGISTER_KIND;
            case 'Q': return DST_INDEX_SIZE;
            case 'E': return DST_SCALE;
            case 'W': return DST_8_BIT_DISPLACEMENT;
            // --
            case 'k': return EXG_DATA_REGISTER;
            case 'l': return EXG_ADDRESS_REGISTER;
            case 'S': return SIZE;
            case 'o': return OP_CODE;

            default:
                return null;
        }
    }
}
