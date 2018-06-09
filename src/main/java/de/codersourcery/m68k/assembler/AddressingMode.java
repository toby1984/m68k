package de.codersourcery.m68k.assembler;

public enum AddressingMode
{
    NO_MEMORY_INDIRECT_ACTION0                       (0b000,false),
    INDIRECT_PREINDEXED_WITH_NULL_OUTER_DISPLACEMENT (0b001,false),
    INDIRECT_PREINDEXED_WITH_WORD_OUTER_DISPLACEMENT (0b010,false),
    INDIRECT_PREINDEXED_WITH_LONG_OUTER_DISPLACEMENT (0b011,false),
    RESERVED0                                        (0b100,false),
    INDIRECT_POSTINDEXED_WITH_NULL_OUTER_DISPLACEMENT(0b101,false),
    INDIRECT_POSTINDEXED_WITH_WORD_OUTER_DISPLACEMENT(0b110,false),
    INDIRECT_POSTINDEXED_WITH_LONG_OUTER_DISPLACEMENT(0b111,false),
    NO_MEMORY_INDIRECT_ACTION1                       (0b000,true),
    MEMORY_INDIRECT_WITH_NULL_OUTER_DISPLACEMENT     (0b001,true),
    MEMORY_INDIRECT_WITH_WORD_OUTER_DISPLACEMENT     (0b010,true),
    MEMORY_INDIRECT_WITH_LONG_OUTER_DISPLACEMENT     (0b011,true),
    RESERVED1                                        (0b100,true),
    RESERVED2                                        (0b101,true),
    RESERVED3                                        (0b110,true),
    RESERVED4                                        (0b111,true);

    public final boolean indexIndirectSelection;
    public final int bits;

    private AddressingMode(int bits,boolean indexIndirectSelection) {
        this.bits = bits;
        this.indexIndirectSelection = indexIndirectSelection;
    }
}
