package de.codersourcery.m68k.assembler.arch;

public enum AddressingModeKind
{
    DATA(1<<0),
    MEMORY(1<<1),
    CONTROL(1<<2),
    ALTERABLE(1<<3);

    public final int bits;

    AddressingModeKind(int bits) {
        this.bits = bits;
    }

    public static int bitsToFlags(int eaMode,int eaRegister)
    {
        switch(eaMode)
        {
            case 0b000:
                return DATA.bits | ALTERABLE.bits;
            case 0b001:
                return ALTERABLE.bits;
            case 0b010:
                return DATA.bits | MEMORY.bits | CONTROL.bits | ALTERABLE.bits;
            case 0b011:
                return DATA.bits | MEMORY.bits | ALTERABLE.bits;
            case 0b100:
                return DATA.bits | MEMORY.bits | ALTERABLE.bits;
            case 0b101:
                return DATA.bits | MEMORY.bits | CONTROL.bits | ALTERABLE.bits;
            case 0b110:
                return DATA.bits | MEMORY.bits | CONTROL.bits | ALTERABLE.bits;
            case 0b111:
                switch( eaRegister ) {
                    case 0b000:
                    case 0b001:
                    case 0b010:
                        return DATA.bits | MEMORY.bits | CONTROL.bits;
                    case 0b011:
                        return DATA.bits | MEMORY.bits | CONTROL.bits | ALTERABLE.bits;
                    case 0b100:
                        return DATA.bits | MEMORY.bits;
                }
        }
        throw new RuntimeException("Unreachable code. eaMode=%"+Integer.toBinaryString( eaMode)+
                ",eaRegister=%"+Integer.toBinaryString(eaRegister));
    }
}