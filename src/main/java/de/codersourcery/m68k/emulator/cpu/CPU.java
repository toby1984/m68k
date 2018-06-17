package de.codersourcery.m68k.emulator.cpu;

import de.codersourcery.m68k.Memory;

public class CPU
{
    /*
    X N Z V C
     */
    // supervisor mode byte
    private static final int FLAG_T1        = 1<<15; // TRACE
    private static final int FLAG_T0        = 1<<14;
    private static final int FLAG_SUPERVISOR_MODE        = 1<<13;
    private static final int FLAG_MASTER_INTERRUPT       = 1<<12;

    private static final int FLAG_I2        = 1<<10; // IRQ priority mask bit 2
    private static final int FLAG_I1        = 1<<9; // IRQ priority mask bit 1
    private static final int FLAG_I0        = 1<<8; // IRQ priority mask bit 0

    // usermode byte (condition codes)
    private static final int FLAG_X        = 1<<4;
    private static final int FLAG_NEGATIVE = 1<<3;
    private static final int FLAG_ZERO     = 1<<2;
    private static final int FLAG_OVERFLOW = 1<<1;
    private static final int FLAG_CARRY    = 1<<0;

    public final Memory memory;

    public final int[] dataRegisters = new int[8];
    public final int[] addressRegisters = new int[8];

    public int sr;
    public int pc;

    private int ea;
    private int value;

    /*

TODO: Not all of them apply to m68k (for example FPU/MMU ones)

0 000 Reset Initial Interrupt Stack Pointer
1 004 Reset Initial Program Counter
2 008 Access Fault
3 00C Address Error
4 010 Illegal Instruction
5 014 Integer Divide by Zero
6 018 CHK, CHK2 Instruction
7 01C FTRAPcc, TRAPcc, TRAPV Instructions
8 020 Privilege Violation
9 024 Trace
10 028 Line 1010 Emulator (Unimplemented A- Line Opcode)
11 02C Line 1111 Emulator (Unimplemented F-Line Opcode)
12 030 (Unassigned, Reserved)
13 034 Coprocessor Protocol Violation
14 038 Format Error
15 03C Uninitialized Interrupt
16–23 040–05C (Unassigned, Reserved)
24 060 Spurious Interrupt
25 064 Level 1 Interrupt Autovector
26 068 Level 2 Interrupt Autovector
27 06C Level 3 Interrupt Autovector
28 070 Level 4 Interrupt Autovector
29 074 Level 5 Interrupt Autovector
30 078 Level 6 Interrupt Autovector
31 07C Level 7 Interrupt Autovector
32–47 080–0BC TRAP #0 D 15 Instruction Vectors
48 0C0 FP Branch or Set on Unordered Condition
49 0C4 FP Inexact Result
50 0C8 FP Divide by Zero
51 0CC FP Underflow
52 0D0 FP Operand Error
53 0D4 FP Overflow
54 0D8 FP Signaling NAN
55 0DC FP Unimplemented Data Type (Defined for MC68040)
56 0E0 MMU Configuration Error
57 0E4 MMU Illegal Operation Error
58 0E8 MMU Access Level Violation Error
59–63 0ECD0FC (Unassigned, Reserved)
64–255 100D3FC User Defined Vectors (192)
     */

    public CPU(Memory memory) {
        this.memory = memory;
    }

    /*
    Bits 15 – 12
 Operation
0000 Bit Manipulation/MOVEP/Immediate
0001 Move Byte
0010 Move Long
0011 Move Word
0100 Miscellaneous
0101 ADDQ/SUBQ/Scc/DBcc/TRAPc c
0110 Bcc/BSR/BRA
0111 MOVEQ
1000 OR/DIV/SBCD
1001 SUB/SUBX
1010 (Unassigned, Reserved)
1011 CMP/EOR
1100 AND/MUL/ABCD/EXG
1101 ADD/ADDX
1110 Shift/Rotate/Bit Field
1111 Coprocessor Interface/MC68040 and CPU32 Extensions
     */

    /**
     * Loads data from memory and sign-extends it to 32 bits.
     *
     * @param address
     * @param size
     * @return
     */
    private int memLoad(int address,int size)
    {
        int value;
        switch(size) {
            case 1: return memory.readByte(address);
            case 2: return memory.readWord(address);
            case 4: return memory.readLong(address);
            default:
                throw new RuntimeException("Unreachable code reached");
        }
    }

    private int memLoadWord(int address) {
        return memory.readWord(address);
    }

    private int memLoadLong(int address) {
        return memory.readLong(address);
    }

    private void decodeOperand(int instruction, int operandSize,int eaMode,int eaRegister)
    {
        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                // value = dataRegisters[ eaRegister ];
                // return;
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                // value = addressRegisters[ eaRegister ];
                // return;
                throw new RuntimeException("Internal error, should've been handled by caller");
            case 0b010:
                // ADDRESS_REGISTER_INDIRECT;
                ea = addressRegisters[ eaRegister ];
                return;
            case 0b011:
                // ADDRESS_REGISTER_INDIRECT_POST_INCREMENT;
                ea = addressRegisters[ eaRegister ];
                if ( eaRegister == 7 && operandSize == 1 ) {
                    // stack ptr always needs to be an even address
                    addressRegisters[eaRegister] += 2;
                }
                else
                {
                    addressRegisters[eaRegister] += operandSize;
                }
                return;
            case 0b100:
                // ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT;
                if ( eaRegister == 7 && operandSize == 1 ) {
                    // stack ptr always needs to be an even address
                    ea = addressRegisters[eaRegister] - 2;
                }
                else
                {
                    ea = addressRegisters[eaRegister] - operandSize;
                }
                addressRegisters[ eaRegister ] = ea;
                return;
            case 0b101:
                // ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
                int offset = memLoadWord(pc);
                pc += 2; // skip displacement
                offset = (offset<<16)>>16;
                ea = addressRegisters[ eaRegister ] + offset; // hint: memLoad() performs sign-extension to 32 bits
                return;
            case 0b110:
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
                // MEMORY_INDIRECT_POSTINDEXED
                // MEMORY_INDIRECT_PREINDEXED

                int extensionWord = memory.readWord(pc);
                pc += 2; // skip extension word

                // bit 8 can be used to distinguish between brief extension words (bit = 0)
                // and full extension words (bit =  1)
                boolean isFullExtensionWord = (extensionWord & 0b0000_0001_0000_1000) == 0b0000_0001_0000_0000;

                int baseRegisterValue = 0;
                int baseDisplacement = 0;
                if ( isFullExtensionWord )
                {
                    /*
                     * Load base register value.
                     */
                    final boolean baseRegisterNotSuppressed = (extensionWord & 1<<7) == 0;
                    if ( baseRegisterNotSuppressed )
                    {
                        baseRegisterValue = addressRegisters[ eaRegister ];
                    }

                    // load sign-extended base displacement
                    baseDisplacement = loadBaseDisplacement(extensionWord);

                    int outerDisplacement = 0;
                    switch ( ((extensionWord & 1<<6) >> 3) | (extensionWord & 0b111) )
                    {
                        case 0b0000: // No Memory Indirect Action
                            ea = baseRegisterValue+decodeIndexRegisterValue(extensionWord )+ baseDisplacement;
                            return;
                        case 0b0001: // Memory Indirect Preindexed with Null Outer Displacement
                            ea = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            return;
                        case 0b0010: // Indirect Preindexed with Word Outer Displacement
                            outerDisplacement = memLoadWord(pc);
                            outerDisplacement = (outerDisplacement<<16)>>16;
                            pc += 2;
                            int intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            ea = memLoadLong(intermediateAddress)+outerDisplacement;
                            return;
                        case 0b0011: // Indirect Preindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            ea = memLoadLong(intermediateAddress) + outerDisplacement;
                            return;
                        case 0b0100: // Reserved
                            break;
                        case 0b0101: // Indirect Postindexed with Null Outer Displacement
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLong(intermediateAddress) + decodeIndexRegisterValue(extensionWord );
                            return;
                        case 0b0110: // Indirect Postindexed with Word Outer Displacement
                            outerDisplacement = memLoadWord(pc);
                            outerDisplacement = (outerDisplacement<<16)>>16;
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLong(intermediateAddress) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return;
                        case 0b0111: // Indirect Postindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLong(intermediateAddress) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return;
                        case 0b1000: // No Memory Indirect Action, Index suppressed
                            ea = baseRegisterValue + baseDisplacement;
                            return;
                        case 0b1001: // Memory Indirect with Null Outer Displacement, Index suppressed
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLong(intermediateAddress);
                            return;
                        case 0b1010: // Memory Indirect with Word Outer Displacement, Index suppressed
                            outerDisplacement = memLoadWord(pc);
                            outerDisplacement = (outerDisplacement<<16)>>16;
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLong(intermediateAddress) + outerDisplacement;
                            return;
                        case 0b1011: // Memory Indirect with Long Outer Displacement, Index suppressed
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLong(intermediateAddress) + outerDisplacement;
                            return;
                        case 0b1100: // Reserved
                        case 0b1101: // Reserved
                        case 0b1110: // Reserved
                        case 0b1111: // Reserved
                            break;
                    }
                    throw new RuntimeException("Illegal extension word: %"+Integer.toBinaryString(extensionWord ) );
                }

                // brief extension word with 8-bit displacement
                baseRegisterValue = addressRegisters[ eaRegister ];
                baseDisplacement = (byte) (extensionWord & 0xff);
                baseDisplacement = (baseDisplacement<<24)>>24;
                ea = baseRegisterValue+decodeIndexRegisterValue(extensionWord)+baseDisplacement;
                return;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b010:
                        // PC_INDIRECT_WITH_DISPLACEMENT(0b111,fixedValue(0b010),1),
                        baseDisplacement = memory.readWord(pc);
                        baseDisplacement = (baseDisplacement<<16) >> 16; // sign-extend
                        ea = baseDisplacement + pc;
                        pc += 2;
                        return;
                    case 0b011:
                     /*
                      * MOVE (d8,PC,Xn.SIZE*SCALE),... (1 extra word).
                      * EA = (PC) + (Xn) + d8
                      PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT(0b111,fixedValue(0b011),1),
                      * MOVE (bd, PC, Xn. SIZE*SCALE),... (1-3 extra words).
                      * EA = (PC) + (Xn) + bd
                      // 1,2 or 3 extra words
                      PC_INDIRECT_WITH_INDEX_DISPLACEMENT(0b111,fixedValue(0b011),3),
                      */
                        int origPc = pc;
                        extensionWord = memory.readWord(pc);
                        pc += 2;
                        if ( (extensionWord & 1<<8) == 0 ) { // 8-bit displacement
                            baseDisplacement = ((extensionWord & 0xff) << 24) >> 24;
                        } else {
                            baseDisplacement = loadBaseDisplacement(extensionWord);
                        }
                        ea = baseDisplacement + origPc + decodeIndexRegisterValue(extensionWord);
                        return;

                /*
                         * MOVE ([bd,PC],Xn.SIZE*SCALE,od),.... (1-5 extra words).
                         * EA = (bd + PC) + Xn.SIZE*SCALE + od
                        // 1,2,3,4 or 5 extra words
                        PC_MEMORY_INDIRECT_POSTINDEXED(0b111,fixedValue(0b011),5),

                         * EA = (bd + PC) + Xn.SIZE*SCALE + od (1-5 extra words).
                         * ([bd,PC,Xn.SIZE*SCALE],od)
                        // 1,2,3,4 or 5 extra words
                        PC_MEMORY_INDIRECT_PREINDEXED(0b111,fixedValue(0b011),5),
                        */
                    case 0b000:
                        /*
                         * MOVE (xxx).W,... (1 extra word).
                         * ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(000),1 ),
                         */
                        ea = memLoadWord(pc);
                        pc += 2;
                        ea = (ea << 16 ) >> 16; // sign-extend
                        return;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        ea = memLoad(pc,4);
                        pc += 4;
                        return;
                    case 0b100:
                        /*
                         * MOVE #xxxx,.... (1-6 extra words).
                         * // 1,2,4, OR 6, EXCEPT FOR PACKED DECIMAL REAL OPERANDS
                         * IMMEDIATE_VALUE(0b111,fixedValue(100), 6),   // move #XXXX
                         */
                        ea = pc;
                        pc += operandSize;
                        return;
                    default:
                        throw new RuntimeException("Unhandled eaRegister value: %"+
                                Integer.toBinaryString(eaRegister));
                }
        }
    }

    /**
     * Loads the base displacement and advances the PC
     * @param extensionWord
     * @return
     */
    private int loadBaseDisplacement(int extensionWord)
    {
        /*
         * Load base displacement (if any).
         *
         * Base displacement size
         * bdSize = 0b00 => Reserved
         * bdSize = 0b01 => NO base displacement
         * bdSize = 0b10 => Word
         * bdSize = 0b11 => Long
         */
        int baseDisplacement = 0;
        final int bdSize = (extensionWord & 0b11_0000) >> 4;
        switch(bdSize)
        {
            case 0b00: // reserved
            case 0b01: // NO base displacement
                break;
            case 0b10: // word
                baseDisplacement = memLoadWord(pc);
                pc += 2;
                baseDisplacement = (baseDisplacement << 16 ) >> 16;
                break;
            case 0b11: // long
                baseDisplacement = memLoadLong(pc);
                pc += 4;
                break;
        }
        return baseDisplacement;
    }

    private int decodeIndexRegisterValue(int extensionWord)
    {
        boolean indexIsAddressRegister = (extensionWord & 0b1000_0000_0000_0000) != 0;
        int idxRegisterBits = (extensionWord & 0b0111_0000_0000_0000) >> 12;

        int idxRegisterValue = indexIsAddressRegister ? addressRegisters[idxRegisterBits] : dataRegisters[idxRegisterBits];
        if ((extensionWord & 0b0000_1000_0000_0000) == 0)
        { // use only lower 16 bits from index register (IDX.w / IDX.l flag)
            idxRegisterValue = (idxRegisterValue & 0xffff);
            idxRegisterValue = (idxRegisterValue<<16)>>16; // sign extend
        }
        int scale = (extensionWord & 0b0000_0110_0000_0000) >> 9;
        return idxRegisterValue * scale;
    }

    public void executeOneInstruction()
    {
        int instruction= memory.readWord(pc);
        pc += 2;

        int operandSize = 2; // operation operandSize in bytes, defaults to 1 word
        final int insBits = (instruction & 0b1111_0000_0000_0000);
        switch( insBits  )
        {
            case 0b0000_0000_0000_0000: // Bit Manipulation/MOVEP/Immediate
                operandSize = 1;
                break;
            case 0b0001_0000_0000_0000: // Move Byte
                loadValue(instruction,1);
                storeValue(instruction,1 );
                return;
            case 0b0010_0000_0000_0000: // Move Long
                loadValue(instruction,4);
                storeValue(instruction,4 );
                return;
            case 0b0011_0000_0000_0000: // Move Word
                loadValue(instruction,2);
                storeValue(instruction,2 );
                return;
            case 0b0100_0000_0000_0000: // Miscellaneous
            case 0b0101_0000_0000_0000: // ADDQ/SUBQ/Scc/DBcc/TRAPc c
            case 0b0110_0000_0000_0000: // Bcc/BSR/BRA
            case 0b0111_0000_0000_0000: // MOVEQ
            case 0b1000_0000_0000_0000: // OR/DIV/SBCD
            case 0b1001_0000_0000_0000: // SUB/SUBX
            case 0b1010_0000_0000_0000: // (Unassigned, Reserved)
            case 0b1011_0000_0000_0000: // CMP/EOR
            case 0b1100_0000_0000_0000: // AND/MUL/ABCD/EXG
            case 0b1101_0000_0000_0000: // ADD/ADDX
            case 0b1110_0000_0000_0000: // Shift/Rotate/Bit Field
            case 0b1111_0000_0000_0000: // Coprocessor Interface/MC68040 and CPU32 Extensions
        }

        // loadValue(instruction,operandSize);

        // perform operation

        // storeValue(instruction,operandSize);
    }

    private void loadValue(int instruction,int operandSize)
    {
        // InstructionEncoding.of("ooooDDDMMMmmmsss");
        int eaMode     = (instruction & 0b111000) >> 3;
        int eaRegister = (instruction & 0b000111);

        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                value = dataRegisters[eaRegister];
                break;
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                value = addressRegisters[eaRegister];
                break;
            default:
                decodeOperand(instruction, operandSize, eaMode, eaRegister);
                value = memLoad(ea, operandSize);
        }
    }

    private void storeValue(int instruction,int operandSize)
    {
        // InstructionEncoding.of("ooooDDDMMMmmmsss");
        int eaMode     = (instruction & 0b0001_1100_0000) >> 6;
        int eaRegister = (instruction & 0b1110_0000_0000) >> 9;

        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                dataRegisters[eaRegister] = value;
                break;
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                addressRegisters[eaRegister] = value;
                break;
            default:
                decodeOperand(instruction, operandSize, eaMode, eaRegister);
                switch(operandSize)
                {
                    case 1:
                        memory.writeByte(ea,value );
                        break;
                    case 2:
                        memory.writeWord(ea,value );
                        break;
                    case 4:
                        memory.writeLong(ea,value );
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }
        }

    }

    public void reset()
    {
        addressRegisters[7] = memLoadLong(0 );
        pc = memLoadLong(4 );
    }
}
