package de.codersourcery.m68k.emulator.cpu;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.assembler.arch.Scaling;

public class CPU
{
    public final Memory memory;

    public final int[] dataRegisters = new int[8];
    public final int[] addressRegisters = new int[8];

    public int sr;
    public int pc;

    private int value;

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
            case 1: return memory.readByte(address );
            case 2: return memory.readWord(address );
            case 4: return memory.readLong(address );
            default:
                throw new RuntimeException("Unreachable code reached");
        }
    }

    private void decodeSource(int instruction)
    {
        // InstructionEncoding.of("ooooDDDMMMmmmsss");
        final int mode = (instruction & 0b111000) >> 3;
        final int registerBits = (instruction & 0b111);

        int operandSize = 2; // operation operandSize in bytes
        final int insBits = (instruction & 0b1111_0000_0000_0000) >> 12;
        switch( insBits  )
        {
            case 0b0000: // Bit Manipulation/MOVEP/Immediate
                operandSize = 1;
                break;
            case 0b0001: // Move Byte
                operandSize = 1;
                break;
            case 0b0010: // Move Long
                operandSize = 4;
                break;
            case 0b0011: // Move Word
                operandSize = 2;
                break;
            case 0b0100: // Miscellaneous
            case 0b0101: // ADDQ/SUBQ/Scc/DBcc/TRAPc c
            case 0b0110: // Bcc/BSR/BRA
            case 0b0111: // MOVEQ
            case 0b1000: // OR/DIV/SBCD
            case 0b1001: // SUB/SUBX
            case 0b1010: // (Unassigned, Reserved)
            case 0b1011: // CMP/EOR
            case 0b1100: // AND/MUL/ABCD/EXG
            case 0b1101: // ADD/ADDX
            case 0b1110: // Shift/Rotate/Bit Field
            case 0b1111: // Coprocessor Interface/MC68040 and CPU32 Extensions
        }

        switch( mode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
//                srcOperand.mode = AddressingMode.AddressingMode.DATA_REGISTER_DIRECT;
                value = dataRegisters[ registerBits ];
                pc += 2; // skip instruction word
                break;
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
//                srcOperand.mode = AddressingMode.AddressingMode.ADDRESS_REGISTER_DIRECT;
                value = addressRegisters[ registerBits ];
                pc += 2; // skip instruction word
                break;
            case 0b010:
                // ADDRESS_REGISTER_INDIRECT;
//                srcOperand.mode = AddressingMode.ADDRESS_REGISTER_INDIRECT;
                value = memLoad( addressRegisters[ registerBits ] , operandSize );
                pc += 2; // skip instruction word
                break;
            case 0b011:
                // ADDRESS_REGISTER_INDIRECT_POST_INCREMENT;
//                srcOperand.mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT;
                value = memLoad( addressRegisters[ registerBits ] , operandSize );
                pc += 2; // skip instruction word
                break;
            case 0b100:
                // ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT;
//                srcOperand.mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT;
                int address = addressRegisters[ registerBits ] - operandSize;
                addressRegisters[ registerBits ] = address;
                value = memLoad( address , operandSize );
                pc += 2; // skip instruction word
                break;
            case 0b101:
                // ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
//                srcOperand.mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
                address = addressRegisters[ registerBits ];
                address += memLoad(pc+2, 2 ); // hint: memLoad() performs sign-extension to 32 bits
                pc += 4; // skip instruction word + src displacement
                break;
            case 0b110:
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
                // MEMORY_INDIRECT_POSTINDEXED
                // MEMORY_INDIRECT_PREINDEXED

                int extensionWord = memory.readWord(pc );
                pc += 4; // skip instruction + extension word

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
                        baseRegisterValue = addressRegisters[ registerBits ];
                    }
                    /*
                     * Load base displacement (if any).
                     *
                     * Base displacement size
                     * bdSize = 0b00 => Reserved
                     * bdSize = 0b01 => NO base displacement
                     * bdSize = 0b10 => Word
                     * bdSize = 0b11 => Long
                     */
                    final int bdSize = (extensionWord & 0b11_0000) >> 4;
                    switch(bdSize)
                    {
                        case 0b00: // reserved
                        case 0b01: // NO base displacement
                            break;
                        case 0b10: // word
                            baseDisplacement = memLoad(pc, 2 );
                            pc += 2;
                            break;
                        case 0b11: // long
                            baseDisplacement = memLoad(pc, 4 );
                            pc += 4;
                            break;
                    }

                    /*
                     * Load outer displacement (if any)
                     */
                    int outerDisplacement = 0;
                    boolean indexRegisterSuppressed = true;
                    switch ( ((extensionWord & 1<<6) >> 3) | (extensionWord & 0b111) )
                    {
                        case 0b0000: // No Memory Indirect Action
                            value = memLoad(baseRegisterValue+decodeIndexRegisterValue(extensionWord )+ baseDisplacement, operandSize ) + outerDisplacement;
                            return;
                        case 0b0001: // Indirect Preindexed with Null Outer Displacement
                            int intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            value = memLoad(intermediateAddress,4);
                            return;
                        case 0b0010: // Indirect Preindexed with Word Outer Displacement
                            outerDisplacement = memLoad(pc,2);
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            value = memLoad(intermediateAddress,4)+outerDisplacement;
                            return;
                        case 0b0011: // Indirect Preindexed with Long Outer Displacement
                            outerDisplacement = memLoad(pc,4);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            value = memLoad(intermediateAddress,4) + outerDisplacement;
                            return;
                        case 0b0100: // Reserved
                            break;
                        case 0b0101: // Indirect Postindexed with Null Outer Displacement
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            value = memLoad(intermediateAddress,4) + decodeIndexRegisterValue(extensionWord );
                            return;
                        case 0b0110: // Indirect Postindexed with Word Outer Displacement
                            outerDisplacement = memLoad(pc,2);
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            value = memLoad(intermediateAddress,4) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return;
                        case 0b0111: // Indirect Postindexed with Long Outer Displacement
                            outerDisplacement = memLoad(pc,4);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            value = memLoad(intermediateAddress,4) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return;
                        case 0b1000: // No Memory Indirect Action, Index suppressed
                            // indexRegisterSuppressed = true;
                            value = memLoad(baseRegisterValue+ baseDisplacement, operandSize ) + outerDisplacement;
                            return;
                        case 0b1001: // Memory Indirect with Null Outer Displacement, Index suppressed
                            // indexRegisterSuppressed = true;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            value = memLoad(intermediateAddress,4);
                            return;
                        case 0b1010: // Memory Indirect with Word Outer Displacement, Index suppressed
                            outerDisplacement = memLoad(pc,2);
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            value = memLoad(intermediateAddress,4) + outerDisplacement;
                            return;
                        case 0b1011: // Memory Indirect with Long Outer Displacement, Index suppressed
                            outerDisplacement = memLoad(pc,4);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            value = memLoad(intermediateAddress,4) + outerDisplacement;
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
                baseRegisterValue = addressRegisters[ registerBits ];
                baseDisplacement = (byte) (extensionWord & 0xff);
                value = memLoad(baseRegisterValue+decodeIndexRegisterValue(extensionWord)+baseDisplacement,operandSize );
                return;
            case 0b111:
                // PC_INDIRECT_WITH_DISPLACEMENT;
                // PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                // PC_INDIRECT_WITH_INDEX_DISPLACEMENT;
                // PC_MEMORY_INDIRECT_POSTINDEXED;
                // PC_MEMORY_INDIRECT_PREINDEXED;
                // ABSOLUTE_SHORT_ADDRESSING;
                // ABSOLUTE_LONG_ADDRESSING;
                // IMMEDIATE_VALUE;
                break;
        }
    }

    private int decodeIndexRegisterValue(int extensionWord)
    {
        boolean indexIsAddressRegister = (extensionWord & 0b1000_0000_0000_0000) != 0;
        int idxRegisterBits = (extensionWord & 0b0111_0000_0000_0000) >> 12;

        int idxRegisterValue = indexIsAddressRegister ? addressRegisters[idxRegisterBits] : dataRegisters[idxRegisterBits];
        if ((extensionWord & 0b0000_1000_0000_0000) == 0)
        { // use only lower 16 bits from index register (IDX.w / IDX.l flag)
            idxRegisterValue = (idxRegisterValue & 0xffff);
            idxRegisterValue <<= 16;
            idxRegisterValue >>= 16;
        }
        int scale = (extensionWord & 0b0000_0110_0000_0000) >> 9;
        return idxRegisterValue * scale;
    }

    private void decodeDestination(int instruction) {

    }

    public void executeOneInstruction()
    {
        int instruction= memory.readWord(pc );
        decodeSource(instruction);
        performOperation(instruction);
    }

    private void performOperation(int instruction)
    {
        switch( (instruction & 0b1111_0000_0000_0000) >> 12 )
        {
            case 0b0000: // Bit Manipulation/MOVEP/Immediate
            case 0b0001: // Move Byte
            case 0b0010: // Move Long
            case 0b0011: // Move Word

            case 0b0100: // Miscellaneous
            case 0b0101: // ADDQ/SUBQ/Scc/DBcc/TRAPc c
            case 0b0110: // Bcc/BSR/BRA
            case 0b0111: // MOVEQ
            case 0b1000: // OR/DIV/SBCD
            case 0b1001: // SUB/SUBX
            case 0b1010: // (Unassigned, Reserved)
            case 0b1011: // CMP/EOR
            case 0b1100: // AND/MUL/ABCD/EXG
            case 0b1101: // ADD/ADDX
            case 0b1110: // Shift/Rotate/Bit Field
            case 0b1111: // Coprocessor Interface/MC68040 and CPU32 Extensions
        }
    }
}
