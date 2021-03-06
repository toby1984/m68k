package de.codesourcery.m68k.assembler.arch;

import de.codesourcery.m68k.utils.Misc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * M68000 family addressing modes.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum AddressingMode
{
    /*
     *  Dn                           => DATA_REGISTER_DIRECT (000)
     *  An                           => ADDRESS_REGISTER_DIRECT (001)
     *  (An)                         => ADDRESS_REGISTER_INDIRECT (010)
     *  (An)+                        => ADDRESS_REGISTER_INDIRECT_POST_INCREMENT (011)
     *  -(An)                        => ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT (100)
     *  d16(An)                      => ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT (101)
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
    DATA_REGISTER_DIRECT              (0b000,registerNumber(),0, AddressingModeKind.DATA, AddressingModeKind.ALTERABLE),
    /**
     * MOVE A0,.. .
     * EA = An
     */
    ADDRESS_REGISTER_DIRECT           (0b001,registerNumber(),0, AddressingModeKind.ALTERABLE),
    /**
     * MOVE (A0),.. .
     *
     * EA = (An)
     */
    ADDRESS_REGISTER_INDIRECT         (0b010,registerNumber(),0, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),
    /**
     * MOVE.size (An)+,.. .
     *
     * EA = (An)
     * An = An + size
     */
    ADDRESS_REGISTER_INDIRECT_POST_INCREMENT   (0b011,registerNumber(),0, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.ALTERABLE),
    /**
     * MOVE.size -(An),.. .
     *
     * An = An - size
     * EA = (An)
     */
    ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT    (0b100,registerNumber(),0, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.ALTERABLE),
    /**
     * MOVE d16(An),... (1 extra word).
     *
     * EA = (An) + d16
     */
    ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT(0b101,registerNumber(),1, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),

    /*
     * ==============================================
     */

    /**
     * MOVE (d8,An, Xn.SIZE*SCALE),... (1 extra word).
     *
     * EA = (An) + (Xn) + d8
     */
    ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT(0b110,registerNumber(),1, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),
    /**
     * MOVE (bd,An,Xn.SIZE*SCALE),...(1-3 extra words).
     *
     * EA = (An) + (Xn) + bd
     */
    // 1,2 or 3 extra words
    ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT(0b110,registerNumber(),3, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),
    /**
     * MOVE ([bd,An],Xn.SIZE*SCALE,od),... (1-5 extra words).
     *
     * EA = (An + bd) + Xn.SIZE*SCALE + od
     */
    // 1,2,3,4 or 5 extra words
    MEMORY_INDIRECT_POSTINDEXED(0b110,registerNumber(),5, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),
    /**
     * MOVE ([bd, An, Xn.SIZE*SCALE], od),... (1-5 extra words).
     *
     * EA = (bd + An) + Xn.SIZE*SCALE + od
     */
    // 1,2,3,4 or 5 extra words
    MEMORY_INDIRECT_PREINDEXED(0b110,registerNumber(),5, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),

    /*
     * ==============================================
     */

    /**
     * MOVE (d16 ,PC),... (1 extra word).
     *
     * EA = (PC) + d16
     */
    PC_INDIRECT_WITH_DISPLACEMENT(0b111,fixedValue(0b010),1, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL ),
    /**
     * MOVE (d8,PC,Xn.SIZE*SCALE),... (1 extra word).
     * EA = (PC) + (Xn) + d8
     */
    PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT(0b111,fixedValue(0b011),1, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL),
    /**
     * MOVE (bd, PC, Xn. SIZE*SCALE),... (1-3 extra words).
     * EA = (PC) + (Xn) + bd
     */
    // 1,2 or 3 extra words
    PC_INDIRECT_WITH_INDEX_DISPLACEMENT(0b111,fixedValue(0b011),3, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL),
    /**
     * MOVE ([bd,PC],Xn.SIZE*SCALE,od),.... (1-5 extra words).
     * EA = (bd + PC) + Xn.SIZE*SCALE + od
     */
    // 1,2,3,4 or 5 extra words
    PC_MEMORY_INDIRECT_POSTINDEXED(0b111,fixedValue(0b011),5, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),
    /**
     * EA = (bd + PC) + Xn.SIZE*SCALE + od (1-5 extra words).
     * ([bd,PC,Xn.SIZE*SCALE],od)
     */
    // 1,2,3,4 or 5 extra words
    PC_MEMORY_INDIRECT_PREINDEXED(0b111,fixedValue(0b011),5, AddressingModeKind.DATA, AddressingModeKind.MEMORY, AddressingModeKind.CONTROL, AddressingModeKind.ALTERABLE),
    /**
     * MOVE (xxx).W,... (1 extra word).
     */
    ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(0b000),1,
            AddressingModeKind.DATA, AddressingModeKind.MEMORY,
            AddressingModeKind.CONTROL,
            AddressingModeKind.ALTERABLE),
    /**
     * MOVE (xxx).L,.... (2 extra words).
     */
    ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(0b001) ,2,
            AddressingModeKind.DATA, AddressingModeKind.MEMORY,
            AddressingModeKind.CONTROL,
            AddressingModeKind.ALTERABLE),
    /**
     * MOVE #xxxx,.... (1-6 extra words).
     */
    // 1,2,4, OR 6, EXCEPT FOR PACKED DECIMAL REAL OPERANDS
    IMMEDIATE_VALUE(0b111,fixedValue(0b100), 6, AddressingModeKind.DATA, AddressingModeKind.MEMORY),   // move #XXXX
    /**
     * Internal use only.
     */
    IMPLIED(0b000,fixedValue(0b000),0);

    public final FieldContent eaRegisterField;
    public final int eaModeField;
    public final int maxExtensionWords;
    private final int kinds;

    @Override
    public String toString()
    {
        if ( hasFixedEaRegisterValue() ) {
            return name()+" / eaMode: "+Misc.binary3Bit(eaModeField)+" / eaRegister: "+Misc.binary3Bit(eaRegisterField.value());
        }
        return name()+" / eaMode: "+Misc.binary3Bit(eaModeField)+" / eaRegister: 0-7";
    }

    public boolean isRegisterDirect()
    {
        return this == AddressingMode.DATA_REGISTER_DIRECT || this == AddressingMode.ADDRESS_REGISTER_DIRECT;
    }

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

    AddressingMode(int eaModeField,FieldContent eaRegisterField,int maxExtensionWords)
    {
        this(eaModeField,eaRegisterField,maxExtensionWords,null);
    }

    AddressingMode(int eaModeField,FieldContent eaRegisterField,int maxExtensionWords,AddressingModeKind kind1,AddressingModeKind... kinds)
    {
        this.eaModeField = eaModeField;
        this.eaRegisterField = eaRegisterField;
        this.maxExtensionWords = maxExtensionWords;
        int mask = kind1 != null ? kind1.bits : 0;
        if ( kinds != null )
        {
            for ( var kind : kinds ) {
                mask |= kind.bits;
            }
        }
        this.kinds = mask;
    }

    public boolean isControl() { return hasKind(AddressingModeKind.CONTROL); }
    public boolean isData() { return hasKind(AddressingModeKind.DATA); }
    public boolean isMemory() { return hasKind(AddressingModeKind.MEMORY); }
    public boolean isAlterable() { return hasKind(AddressingModeKind.ALTERABLE); }

    public boolean hasFixedEaRegisterValue()
    {
        return eaRegisterField.isFixedValue();
    }

    /**
     * Returns whether this addressing mode has all the given kinds.
     * @param kind1
     * @param additional
     * @return
     */
    public boolean hasKinds(AddressingModeKind kind1,AddressingModeKind...additional)
    {
        if ( ! hasKind(kind1) ) {
            return false;
        }
        if ( additional != null ) {
            for ( AddressingModeKind k : additional ) {
                if ( ! hasKind(k) ) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns whether this addressing mode has all the given kinds.
     * @param requiredKinds
     * @return
     */
    public boolean hasKinds(Set<AddressingModeKind> requiredKinds)
    {
        if ( requiredKinds == null || requiredKinds.isEmpty() ) {
            throw new IllegalArgumentException("Need at least one kind");
        }
        for ( AddressingModeKind k : requiredKinds )
        {
            if ( ! hasKind(k) ) {
                return false;
            }
        }
        return true;
    }

    public boolean hasKind(AddressingModeKind kind) {
        return (this.kinds & kind.bits) != 0;
    }

    public static Set<AddressingMode> getByKind(AddressingModeKind kind1,AddressingModeKind... additional)
    {
        final Set<AddressingModeKind> kinds = new HashSet<>();
        kinds.add(kind1);
        if ( additional != null ) {
            kinds.addAll(Arrays.asList(additional));
        }
        return Stream.of(AddressingMode.values()).filter( mode -> kinds.stream().anyMatch(mode::hasKind) ).collect(Collectors.toSet());
    }

    public int getHashKey()
    {
        if ( hasFixedEaRegisterValue() ) {
            return (eaModeField<<4| eaRegisterField.value());
        }
        return eaModeField<<4| 0b1111;
    }
}