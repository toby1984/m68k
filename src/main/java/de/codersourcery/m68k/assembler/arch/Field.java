package de.codersourcery.m68k.assembler.arch;

public enum Field
{
    SRC_REGISTER,
    SRC_MODE,
    DST_REGISTER,
    DST_MODE,
    SIZE,
    /**
     * Dummy field that indicates
     * the {@link InstructionEncoding.IBitMapping} requires no
     * input value at all.
     */
    NONE;


    public static Field getField(char c)
    {
        switch(c) {
            case 's': return SRC_REGISTER;
            case 'm': return SRC_MODE;
            case 'D': return DST_REGISTER;
            case 'M': return DST_MODE;
            case 'S': return SIZE;
            default:
                return null;
        }
    }
}
