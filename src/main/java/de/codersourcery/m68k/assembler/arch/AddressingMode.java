package de.codersourcery.m68k.assembler.arch;

/**
 * M68000 family addressing modes.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum AddressingMode
{
    /*
     *  Dn                           => DATA_REGISTER_DIRECT
     *  An                           => ADDRESS_REGISTER_DIRECT
     *  (An)                         => ADDRESS_REGISTER_INDIRECT
     *  (An)+                        => ADDRESS_REGISTER_INDIRECT_POST_INCREMENT
     *  -(An)                        => ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT
     *  d16(An)                      => ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT
     * (d8,An, Xn.SIZE*SCALE)        => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
     * (bd,An,Xn.SIZE*SCALE)         => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
     * ([bd,An],Xn.SIZE*SCALE,od)    => MEMORY_INDIRECT_POSTINDEXED
     * ([bd, An, Xn.SIZE*SCALE], od) => MEMORY_INDIRECT_PREINDEXED
     * (d16 ,PC)                     => PC_INDIRECT_WITH_DISPLACEMENT
     * (d8,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
     * (bd, PC, Xn. SIZE*SCALE)      => PC_INDIRECT_WITH_INDEX_DISPLACEMENT
     * ([bd,PC],Xn.SIZE*SCALE,od)    => PC_MEMORY_INDIRECT_POSTINDEXED
     * ([bd,PC,Xn.SIZE*SCALE],od)    => PC_MEMORY_INDIRECT_PREINDEXED
     * ($1234).w                     => ABSOLUTE_SHORT_ADDRESSING
     * ($1234).L                     => ABSOLUTE_LONG_ADDRESSING
     * #$1234                        => IMMEDIATE_VALUE
     */
    /**
     * MOVE D0,.. .
     *
     * EA = Dn
     */
    DATA_REGISTER_DIRECT              (0b000,registerNumber(),0),
    /**
     * MOVE A0,.. .
     * EA = An
     */
    ADDRESS_REGISTER_DIRECT           (0b001,registerNumber(),0),
    /**
     * MOVE (A0),.. .
     *
     * EA = (An)
     */
    ADDRESS_REGISTER_INDIRECT         (0b010,registerNumber(),0),
    /**
     * MOVE.size (An)+,.. .
     *
     * EA = (An)
     * An = An + size
     */
    ADDRESS_REGISTER_INDIRECT_POST_INCREMENT   (0b011,registerNumber(),0),
    /**
     * MOVE.size -(An),.. .
     *
     * An = An - size
     * EA = (An)
     */
    ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT    (0b100,registerNumber(),0),
    /**
     * MOVE d16(An),... (1 extra word).
     *
     * EA = (An) + d16
     */
    ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT(0b101,registerNumber(),1),

    /*
     * ==============================================
     */

    /**
     * MOVE (d8,An, Xn.SIZE*SCALE),... (1 extra word).
     *
     * EA = (An) + (Xn) + d8
     */
    ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT(0b110,registerNumber(),1),
    /**
     * MOVE (bd,An,Xn.SIZE*SCALE),...(1-3 extra words).
     *
     * EA = (An) + (Xn) + bd
     */
    // 1,2 or 3 extra words
    ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT(0b110,registerNumber(),3),
    /**
     * MOVE ([bd,An],Xn.SIZE*SCALE,od),... (1-5 extra words).
     *
     * EA = (An + bd) + Xn.SIZE*SCALE + od
     */
    // 1,2,3,4 or 5 extra words
    MEMORY_INDIRECT_POSTINDEXED(0b110,registerNumber(),5),
    /**
     * MOVE ([bd, An, Xn.SIZE*SCALE], od),... (1-5 extra words).
     *
     * EA = (bd + An) + Xn.SIZE*SCALE + od
     */
    // 1,2,3,4 or 5 extra words
    MEMORY_INDIRECT_PREINDEXED(0b110,registerNumber(),5),

    /*
     * ==============================================
     */

    /**
     * MOVE (d16 ,PC),... (1 extra word).
     *
     * EA = (PC) + d16
     */
    PC_INDIRECT_WITH_DISPLACEMENT(0b111,fixedValue(0b010),1),
    /**
     * MOVE (d8,PC,Xn.SIZE*SCALE),... (1 extra word).
     * EA = (PC) + (Xn) + d8
     */
    PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT(0b111,fixedValue(0b011),1),
    /**
     * MOVE (bd, PC, Xn. SIZE*SCALE),... (1-3 extra words).
     * EA = (PC) + (Xn) + bd
     */
    // 1,2 or 3 extra words
    PC_INDIRECT_WITH_INDEX_DISPLACEMENT(0b111,fixedValue(0b011),3),
    /**
     * MOVE ([bd,PC],Xn.SIZE*SCALE,od),.... (1-5 extra words).
     * EA = (bd + PC) + Xn.SIZE*SCALE + od
     */
    // 1,2,3,4 or 5 extra words
    PC_MEMORY_INDIRECT_POSTINDEXED(0b111,fixedValue(0b011),5),
    /**
     * EA = (bd + PC) + Xn.SIZE*SCALE + od (1-5 extra words).
     * ([bd,PC,Xn.SIZE*SCALE],od)
     */
    // 1,2,3,4 or 5 extra words
    PC_MEMORY_INDIRECT_PREINDEXED(0b111,fixedValue(0b011),5),
    /**
     * MOVE (xxx).W,... (1 extra word).
     */
    ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(000),1 ),
    /**
     * MOVE (xxx).L,.... (2 extra words).
     */
    ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
    /**
     * MOVE #xxxx,.... (1-6 extra words).
     */
    // 1,2,4, OR 6, EXCEPT FOR PACKED DECIMAL REAL OPERANDS
    IMMEDIATE_VALUE(0b111,fixedValue(100), 6),   // move #XXXX
    /**
     * NO OPERAND.
     */
    NO_OPERAND(0b000,fixedValue(0b000),0);

    public final FieldContent eaRegisterField;
    public final int eaModeField;
    public final int maxExtensionWords;

    public interface FieldContent
    {
        public boolean isFixedValue();

        public int value();

        public boolean isRegisterNumber();
    }

    private static final FieldContent fixedValue(int bits) {
        return new FixedValue(bits);
    }

    private static final FieldContent registerNumber() {
        return new RegisterNumber();
    }

    public static final class FixedValue implements FieldContent
    {
        private final int bits;

        public FixedValue(int bits) {
            this.bits = bits;
        }


        @Override
        public int value()
        {
            return bits;
        }

        @Override
        public boolean isRegisterNumber()
        {
            return false;
        }

        public boolean isFixedValue() {
            return true;
        }
    }

    public static final class RegisterNumber implements FieldContent {

        @Override
        public boolean isFixedValue()
        {
            return false;
        }

        @Override
        public int value()
        {
            throw new UnsupportedOperationException("You need to get the register number from the operand");
        }

        @Override
        public boolean isRegisterNumber()
        {
            return true;
        }
    };

    private AddressingMode(int eaModeField,FieldContent eaRegisterField,int maxExtensionWords)
    {
        this.eaModeField = eaModeField;
        this.eaRegisterField = eaRegisterField;
        this.maxExtensionWords = maxExtensionWords;
    }
}
