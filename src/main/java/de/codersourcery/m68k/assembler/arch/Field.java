package de.codersourcery.m68k.assembler.arch;

public enum Field
{
    SRC_VALUE,
    SRC_MODE,
    DST_VALUE,
    DST_MODE,
    SIZE,
    OP_CODE,
    /**
     * Dummy field that indicates
     * the {@link InstructionEncoding.IBitMapping} requires no
     * input value at all.
     */
    NONE;

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
