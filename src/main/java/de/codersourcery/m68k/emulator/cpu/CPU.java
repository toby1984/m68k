package de.codersourcery.m68k.emulator.cpu;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.arch.Condition;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;

/**
 * M68000 cpu emulation.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CPU
{
    /*
     * Interrupt vectors.
     */
    public enum IRQGroup
    {
        GROUP0(120),
        GROUP1(80),
        GROUP2(40);

        public final int priority;

        IRQGroup(int prio) {
            this.priority = prio;
        }
    }

    public enum IRQ
    {
        // entries in each group are sorted by descending priority (more important comes first)
        // group 0
        RESET(0,IRQGroup.GROUP0,999),
        BUS_ERROR(1,IRQGroup.GROUP0,122),
        ADDRESS_ERROR(2,IRQGroup.GROUP0,121),
        // group 1
        TRACE(8,IRQGroup.GROUP1,89),
        AUTOVECTOR_LVL7(30,IRQGroup.GROUP1,88),
        AUTOVECTOR_LVL6(29,IRQGroup.GROUP1,87),
        AUTOVECTOR_LVL5(28,IRQGroup.GROUP1,86),
        AUTOVECTOR_LVL4(27,IRQGroup.GROUP1,85),
        AUTOVECTOR_LVL3(26,IRQGroup.GROUP1,84),
        AUTOVECTOR_LVL2(25,IRQGroup.GROUP1,83),
        AUTOVECTOR_LVL1(24,IRQGroup.GROUP1,82),
        ILLEGAL_INSTRUCTION(3,IRQGroup.GROUP1,81),
        PRIVILEGE_VIOLATION(7,IRQGroup.GROUP1,80),

        // group 2
        INTEGER_DIVIDE_BY_ZERO(4,IRQGroup.GROUP2),
        CHK_CHK2(5,IRQGroup.GROUP2),
        FTRAP_TRAP_TRAPV(6,IRQGroup.GROUP2),
        LINE_1010_EMULATOR(9,IRQGroup.GROUP2),
        LINE_1111_EMULATOR(10,IRQGroup.GROUP2),
        COPROCESSOR_VIOLATION(12,IRQGroup.GROUP2),
        FORMAT_ERROR(13,IRQGroup.GROUP2),
        UNINITIALIZED_INTERRUPT(14,IRQGroup.GROUP2),
        SPURIOUS(23,IRQGroup.GROUP2),
        TRAP0_0(31,IRQGroup.GROUP2),
        TRAP0_1(32,IRQGroup.GROUP2),
        TRAP0_2(33,IRQGroup.GROUP2),
        TRAP0_3(34,IRQGroup.GROUP2),
        TRAP0_4(35,IRQGroup.GROUP2),
        TRAP0_5(36,IRQGroup.GROUP2),
        TRAP0_6(37,IRQGroup.GROUP2),
        TRAP0_7(38,IRQGroup.GROUP2),
        TRAP0_8(39,IRQGroup.GROUP2),
        TRAP0_9(40,IRQGroup.GROUP2),
        TRAP0_10(41,IRQGroup.GROUP2),
        TRAP0_11(42,IRQGroup.GROUP2),
        TRAP0_12(43,IRQGroup.GROUP2),
        TRAP0_13(44,IRQGroup.GROUP2),
        TRAP0_14(45,IRQGroup.GROUP2),
        TRAP0_15(46,IRQGroup.GROUP2),
        FP_BRANCH_UNORDERED(47,IRQGroup.GROUP2),
        FP_INEXACT_RESULT(48,IRQGroup.GROUP2),
        FP_DIVIDE_BY_ZERO(49,IRQGroup.GROUP2),
        FP_UNDERFLOW(50,IRQGroup.GROUP2),
        FP_OPERAND_ERROR(51,IRQGroup.GROUP2),
        FP_OVERFLOW(52,IRQGroup.GROUP2),
        FP_SIGNALING_NAN(53,IRQGroup.GROUP2),
        FP_UNIMPLEMENTED_DATA_TYPE(54,IRQGroup.GROUP2),
        MMU_CONFIGURATION_ERROR(55,IRQGroup.GROUP2),
        MMU_ILLEGAL_OPERATION_ERROR(56,IRQGroup.GROUP2),
        MMU_ACCESS_LEVEL_VIOLATION(57,IRQGroup.GROUP2);

        public final int pcVectorAddress; // address in memory where interrupt vector address can be found
        public final int irqNumber;
        public final IRQGroup group;
        public final int priority;

        IRQ(int irqNumber,IRQGroup group)
        {
            this(irqNumber, group, group.priority);
        }

        IRQ(int irqNumber,IRQGroup group,int priority)
        {
            this.irqNumber = irqNumber;
            this.group = group;
            this.priority = priority;
            // IRQ #0 is special as it occupies 8 bytes in the
            // vector table and memory address $0000 contains
            // the supervisor stack ptr value,not the jump address
            this.pcVectorAddress = irqNumber == 0 ? 4 : 8 + (irqNumber-1)*4;
        }

        /**
         * Turns a user trap number (used in TRAP #xx instruction)
         * into the corresponding IRQ.
         *
         * @param trapNo trap number (0-15)
         * @return IRQ
         */
        public static IRQ userTrapToIRQ(int trapNo)
        {
            switch(trapNo) {
                case 0: return IRQ.TRAP0_0;
                case 1: return IRQ.TRAP0_1;
                case 2: return IRQ.TRAP0_2;
                case 3: return IRQ.TRAP0_3;
                case 4: return IRQ.TRAP0_4;
                case 5: return IRQ.TRAP0_5;
                case 6: return IRQ.TRAP0_6;
                case 7: return IRQ.TRAP0_7;
                case 8: return IRQ.TRAP0_8;
                case 9: return IRQ.TRAP0_9;
                case 10: return IRQ.TRAP0_10;
                case 11: return IRQ.TRAP0_11;
                case 12: return IRQ.TRAP0_12;
                case 13: return IRQ.TRAP0_13;
                case 14: return IRQ.TRAP0_14;
                case 15: return IRQ.TRAP0_15;
                default: throw new RuntimeException("Unreachable code reached");
            }
        }
    }

    /*
     * Status register flags
     */

    // supervisor mode byte
    public static final int FLAG_T1        = 1<<15; // TRACE
    public static final int FLAG_T0        = 1<<14;
    public static final int FLAG_SUPERVISOR_MODE        = 1<<13;
    public static final int FLAG_MASTER_INTERRUPT       = 1<<12;

    public static final int FLAG_I2        = 1<<10; // IRQ priority mask bit 2
    public static final int FLAG_I1        = 1<<9; // IRQ priority mask bit 1
    public static final int FLAG_I0        = 1<<8; // IRQ priority mask bit 0

    // usermode byte (condition codes)
    public static final int FLAG_EXTENDED = 1<<4;
    public static final int FLAG_NEGATIVE = 1<<3;
    public static final int FLAG_ZERO     = 1<<2;
    public static final int FLAG_OVERFLOW = 1<<1;
    public static final int FLAG_CARRY    = 1<<0;

    public final Memory memory;

    public final int[] dataRegisters = new int[8];
    public final int[] addressRegisters = new int[8];

    public int statusRegister;

    private final long[] irqData = new long[10];
    private final IRQ[] irqStack = new IRQ[10];

    private int irqStackPtr;
    public IRQ activeIrq; // currently active IRQ (if any)

    private int userModeStackPtr;
    private int supervisorModeStackPtr;

    public int pcAtStartOfLastInstruction;
    public int pc;

    private int ea; // populated from address calculations
    private int value; // value the current instruction operates on

    /*

TODO: Not all of them apply to m68k (for example FPU/MMU ones)

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
        switch(size) {
            case 1: return memory.readByte(address);
            case 2: return memory.readWord(address);
            case 4: return memory.readLong(address);
            default:
                throw new RuntimeException("Unreachable code reached");
        }
    }

    private int memLoadWord(int address) {
        return memory.readWordNoCheck(address);
    }

    private int memLoadWordWithCheck(int address) {
        return memory.readWord(address);
    }

    private int memLoadLong(int address) {
        return memory.readLongNoCheck(address);
    }

    private int memLoadLongWithCheck(int address) {
        return memory.readLong(address);
    }

    /**
     *
     * @param operandSize
     * @param eaMode
     * @param eaRegister
     * @return true on success, false on failure (invalid instruction,misaligned memory access)
     */
    private boolean decodeOperand(int operandSize, int eaMode, int eaRegister)
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
                return true;
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
                return true;
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
                return true;
            case 0b101:
                // ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
                int offset = memLoadWord(pc);
                pc += 2; // skip displacement
                ea = addressRegisters[ eaRegister ] + offset; // hint: memLoad() performs sign-extension to 32 bits
                return true;
            case 0b110:
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
                // MEMORY_INDIRECT_POSTINDEXED
                // MEMORY_INDIRECT_PREINDEXED

                int extensionWord = memory.readWordNoCheck(pc);
                pc += 2; // skip extension word

                // bit 8 can be used to distinguish between brief extension words (bit = 0)
                // and full extension words (bit =  1)
                boolean isFullExtensionWord = (extensionWord & 0b0000_0001_0000_1000) == 0b0000_0001_0000_0000;

                int baseRegisterValue = 0;
                int baseDisplacement;
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
                            return true;
                        case 0b0001: // Memory Indirect Preindexed with Null Outer Displacement
                            ea = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            return true;
                        case 0b0010: // Indirect Preindexed with Word Outer Displacement
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            int intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            ea = memLoadLongWithCheck(intermediateAddress)+outerDisplacement;
                            return true;
                        case 0b0011: // Indirect Preindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            ea = memLoadLongWithCheck(intermediateAddress) + outerDisplacement;
                            return true;
                        case 0b0100: // Reserved
                            break;
                        case 0b0101: // Indirect Postindexed with Null Outer Displacement
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + decodeIndexRegisterValue(extensionWord );
                            return true;
                        case 0b0110: // Indirect Postindexed with Word Outer Displacement
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return true;
                        case 0b0111: // Indirect Postindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return true;
                        case 0b1000: // No Memory Indirect Action, Index suppressed
                            ea = baseRegisterValue + baseDisplacement;
                            return true;
                        case 0b1001: // Memory Indirect with Null Outer Displacement, Index suppressed
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress);
                            return true;
                        case 0b1010: // Memory Indirect with Word Outer Displacement, Index suppressed
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + outerDisplacement;
                            return true;
                        case 0b1011: // Memory Indirect with Long Outer Displacement, Index suppressed
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + outerDisplacement;
                            return true;
                        case 0b1100: // Reserved
                        case 0b1101: // Reserved
                        case 0b1110: // Reserved
                        case 0b1111: // Reserved
                            break;
                    }
                    triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
                    return false;
                }

                // brief extension word with 8-bit displacement
                baseRegisterValue = addressRegisters[ eaRegister ];
                baseDisplacement = (byte) (extensionWord & 0xff);
                baseDisplacement = (baseDisplacement<<24)>>24;
                ea = baseRegisterValue+decodeIndexRegisterValue(extensionWord)+baseDisplacement;
                return true;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b010:
                        // PC_INDIRECT_WITH_DISPLACEMENT(0b111,fixedValue(0b010),1),
                        baseDisplacement = memory.readWordNoCheck(pc);
                        ea = baseDisplacement + pc;
                        pc += 2;
                        return true;
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
                        extensionWord = memory.readWordNoCheck(pc);
                        pc += 2;
                        if ( (extensionWord & 1<<8) == 0 ) { // 8-bit displacement
                            baseDisplacement = ((extensionWord & 0xff) << 24) >> 24;
                        } else {
                            baseDisplacement = loadBaseDisplacement(extensionWord);
                        }
                        ea = baseDisplacement + origPc + decodeIndexRegisterValue(extensionWord);
                        return true;

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
                        return true;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        ea = memLoadLong(pc);
                        pc += 4;
                        return true;
                    case 0b100:
                        /*
                         * MOVE #xxxx,.... (1-6 extra words).
                         * // 1,2,4, OR 6, EXCEPT FOR PACKED DECIMAL REAL OPERANDS
                         * IMMEDIATE_VALUE(0b111,fixedValue(100), 6),   // move #XXXX
                         */
//                        ea = memLoad( pc,operandSize );
                        ea = pc;
                        pc += operandSize;
                        return true;
                }
        }
        triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
        return false;
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
        try
        {
            internalExecutionOneInstruction();

            checkPendingIRQ();
        }
        catch(MemoryAccessException e)
        {
            badAlignment(e.offendingAddress, e.operation );
        }
    }

    private void internalExecutionOneInstruction()
    {
        System.out.println(">>>> Executing instruction at 0x"+Integer.toHexString(pc));

        if ( ( pc & 1 ) != 0 )
        {
            badAlignment(pc,MemoryAccessException.Operation.READ_WORD);
            return;
        }

        pcAtStartOfLastInstruction = pc;

        int instruction= memory.readWordNoCheck(pc);
        pc += 2;

        switch(instruction)
        {
            /* ================================
             * Bit Manipulation/MOVEP/Immediate
             * ================================
             */
            case 0b0000001001111100: // ANDI to SR
                if ( assertSupervisorMode() )
                {
                    decodeSourceOperand(instruction, 2);
                    setStatusRegister( statusRegister & value );
                }
                return;
            /* ================================
             * Miscellaneous instructions
             * ================================
             */
            case 0b0100111001110011: // RET
                returnFromException();
                return;
            case 0b0100111001110001:  // NOP
                return;
            case 0b0100111001110101:  // RTS
                pc = popLong();
                return;
            case 0b0100101011111100: // ILLEGAL
                triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
                return;
        }
        final int insBits = (instruction & 0b1111_0000_0000_0000);
        switch( insBits  )
        {
            /* ================================
             * Bit Manipulation/MOVEP/Immediate
             * ================================
             */
            case 0b0000_0000_0000_0000:
                break;
            /* ================================
             * Move Byte
             * ================================
             */
            case 0b0001_0000_0000_0000:
                decodeSourceOperand(instruction,2); // operandSize == 2 because PC must always be even so byte is actually stored as 16 bits
                value = (value<<24)>>24; // sign-extend
                updateFlags();
                clearFlags(FLAG_CARRY | FLAG_OVERFLOW );
                storeValue(instruction,1 );
                return;
            /* ================================
             * Move Long
             * ================================
             */
            case 0b0010_0000_0000_0000:
                // 00SSDDD001mmmsss => MOVEA
                // 1111000111000000 => MOVEA
                if ( (instruction & 0b0010000111000000) == 0b0010000001000000 ) {
                    decodeSourceOperand(instruction,4);
                    // MOVEA does not change any flags
                    storeValue(instruction,4 );
                    return;
                }
                decodeSourceOperand(instruction,4);
                updateFlags();
                clearFlags(FLAG_CARRY | FLAG_OVERFLOW );
                storeValue(instruction,4 );
                return;
            /* ================================
             * Move Word
             * ================================
             */
            case 0b0011_0000_0000_0000:
                // 00SSDDD001mmmsss => MOVEA
                if ( (instruction & 0b0011000111000000) == 0b0011000001000000 ) {
                    decodeSourceOperand(instruction,2);
                    // MOVEA does not change any flags
                    storeValue(instruction,4 );
                    return;
                }
                decodeSourceOperand(instruction,2);
                updateFlags();
                clearFlags(FLAG_CARRY | FLAG_OVERFLOW );
                storeValue(instruction,2 );
                return;
            /* ================================
             * Miscellaneous instructions
             * ================================
             */
            case 0b0100_0000_0000_0000:

                if ( (instruction & 0b1111111111000000) == 0b0100111010000000)
                {
                    // JSR
                    decodeSourceOperand(instruction,4);
                    pushLong(pc);
                    pc = value;
                    return;
                }

                if ( (instruction & 0b1111111111111000) == 0b0100100001000000) {
                    // SWAP
                    final int regNum = (instruction & 0b111);
                    int result = (dataRegisters[regNum] << 16) | (dataRegisters[regNum] >>> 16);
                    dataRegisters[regNum] = result;
                    /* N — Set if the most significant bit of the 32-bit result is set; cleared otherwise.
                     * Z — Set if the 32-bit result is zero; cleared otherwise.
                     * V — Always cleared.
                     * C — Always cleared. */
                    int flagsToSet = 0;
                    if ( result == 0 ) {
                        flagsToSet |= FLAG_ZERO;
                    }
                    if ( (result & 1<<31) != 0 ) {
                        flagsToSet |= FLAG_NEGATIVE;
                    }
                    this.statusRegister = (statusRegister & ~(FLAG_OVERFLOW | FLAG_CARRY | FLAG_ZERO | FLAG_NEGATIVE) ) | flagsToSet;
                    return;
                }

                if ( (instruction & 0b0100111011000000) == 0b0100111011000000)
                {
                    // JMP
                    decodeSourceOperand(instruction,4);
                    pc = value;
                    return;
                }
                if ( (instruction & 0b11111111_11110000) == 0b0100111001100000)
                {
                    // MOVE Ax,USP / MOVE USP,Ax
                    if ( assertSupervisorMode() )
                    {
                        final int regNum = instruction & 0b111;
                        if ((instruction & 1 << 3) == 0) // check transfer direction
                        {
                            userModeStackPtr = addressRegisters[ regNum ]; // address register -> USP
                        }
                        else
                        {
                            addressRegisters[ regNum ] = userModeStackPtr; // USP -> address register
                        }
                    }
                    return;
                }
                if ( (instruction & 0b1111111111110000) == 0b0100111001000000 ) {
                    // TRAP #xx
                    triggerIRQ(IRQ.userTrapToIRQ( instruction & 0b1111 ),0);
                    return;
                }

                if ( (instruction & 0b1111000111000000) == 0b0100000111000000 )
                {
                    // LEA
                    decodeSourceOperand(instruction,4);
                    final int dstAdrReg = (instruction & 0b1110_0000_0000) >> 9;

                    addressRegisters[dstAdrReg] = value;
                    return;
                }
                break;
            /* ================================
             * ADDQ/SUBQ/Scc/DBcc/TRAPc c
             * ================================
             */
            case 0b0101_0000_0000_0000:
                if ( (instruction & 0b11110000_11111000 ) == 0b01010000_11001000 )
                {
                     // DBcc
                    final int cc = (instruction & 0b0000111100000000) >> 8;
                    if ( ! Condition.isTrue(this,cc ) )
                    {
                        // condition is false, decrement data register lower 16 bits
                        final int regNum = instruction & 0b111;
                        final int regVal = dataRegisters[regNum];
                        int newValue = ( (regVal & 0xffff) - 1 ) & 0xffff;
                        dataRegisters[ regNum ] = (regVal & 0xffff0000) | newValue;
                        if ( newValue != 0xffff ) {
                            /*
                             * - If the result is – 1, execution continues with the next instruction.
                             * - If the result is not equal to – 1, execution continues at the location indicated by the current value of the program
                             *   counter plus the sign-extended 16-bit displacement. The value in the program counter is
                             *   the address of the instruction word of the DBcc instruction plus two. The
                             */
                            pc = pc + memory.readWordNoCheck(pc);
                            return;
                        }
                    }
                    pc += 2; // skip branch offset
                    return;
                }
                break;
            /* ================================
             * Bcc/BSR/BRA
             * ================================
             */
            case 0b0110_0000_0000_0000:

                if ( (instruction & 0b11111111_00000000) == 0b01100001_00000000 ) {
                    throw new RuntimeException("BSR not implemented yet");
                }

                // BRA/Bcc
                final int cc = (instruction & 0b0000111100000000) >> 8;
                final boolean takeBranch = Condition.isTrue(this, cc);
                switch (instruction & 0xff)
                {
                    case 0x00: // 16 bit offset
                        if (takeBranch)
                        {
                            pc += memLoadWord(pc) - 2; // -2 because we already advanced the PC after reading the instruction word
                        }
                        pc += 2; // skip offset
                        break;
                    case 0xff: // 32 bit offset
                        if (takeBranch)
                        {
                            pc += memLoadLong(pc) - 2; // -2because we already advanced the PC after reading the instruction word
                        }
                        pc += 4;
                        break;
                    default:
                        // 8-bit branch offset encoded in instruction itself
                        if (takeBranch)
                        {
                            final int offset = ((instruction & 0xff) << 24) >> 24;
                            pc += offset - 2; // -2 because we already advanced the PC after reading the instruction word
                        }
                }
                return;
            /* ================================
             * MOVEQ
             * ================================
             */
            case 0b0111_0000_0000_0000:
                value = instruction & 0xff;
                value = (value<<24)>>24; // sign-extend
                int register = (instruction & 0b0111_0000_0000) >> 8;
                dataRegisters[register] = value;
                updateFlags();
                clearFlags(FLAG_CARRY | FLAG_OVERFLOW );
                return;
            /* ================================
             * OR/DIV/SBCD
             * ================================
             */
            case 0b1000_0000_0000_0000:
                break;
            /* ================================
             * SUB/SUBX
             * ================================
             */
            case 0b1001_0000_0000_0000:
                break;
            /* ================================
             * (Unassigned, Reserved)
             * ================================
             */
            case 0b1010_0000_0000_0000:
                break;
            /* ================================
             * CMP/EOR
             * ================================
             */
            case 0b1011_0000_0000_0000:
                break;
            /* ================================
             * AND/MUL/ABCD/EXG
             * ================================
             */
            case 0b1100_0000_0000_0000:
                if ( (instruction & 0b11110001_00000000) == 0b11000001_00000000 ) // EXG
                {
                    // hint: variable names are a bit misleading, only apply if EXG between different register types
                    final int dataReg = (instruction & 0b111_000000000) >> 9;
                    final int addressReg = instruction & 0b111;
                    switch( instruction & 0b1111_1000 )
                    {
                        case 0b01000000: // swap Data registers
                            int tmp = dataRegisters[ addressReg ];
                            dataRegisters[ addressReg ] = dataRegisters[ dataReg ];
                            dataRegisters[ dataReg ] = tmp;
                            return;
                        case 0b01001000: // swap Address registers
                            tmp = addressRegisters[ addressReg ];
                            addressRegisters[ addressReg ] = addressRegisters[ dataReg ];
                            addressRegisters[ dataReg ] = tmp;
                            return;
                        case 0b10001000: // swap Data register and address register
                            tmp = addressRegisters[ addressReg ];
                            addressRegisters[ addressReg ] = dataRegisters[ dataReg ];
                            dataRegisters[ dataReg ] = tmp;
                            return;
                    }
                    triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
                    return;
                }
                break;
            /* ================================
             * ADD/ADDX
             * ================================
             */
            case 0b1101_0000_0000_0000:
                break;
            /* ================================
             * Shift/Rotate/Bit Field
             * ================================
             */
            case 0b1110_0000_0000_0000:
                break;
            /* ================================
             * Coprocessor Interface/MC68040 and CPU32 Extensions
             * ================================
             */
            case 0b1111_0000_0000_0000:
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }
        triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
    }

    /**
     * Sets all bits in the status register where the bit bitMask has a '1' bit.
     *
     * @param bitMask
     * @return
     */
    // unit-testing helper method
    public CPU setFlags(int bitMask) {
        this.statusRegister |= bitMask;
        return this;
    }

    // unit-testing helper method
    public CPU overflow() { return setFlags(CPU.FLAG_OVERFLOW); }
    // unit-testing helper method
    public CPU carry() { return setFlags(CPU.FLAG_CARRY); }
    // unit-testing helper method
    public CPU negative() { return setFlags(CPU.FLAG_NEGATIVE); }
    // unit-testing helper method
    public CPU zero() { return setFlags(CPU.FLAG_ZERO); }

    /**
     * Clears the all bits in the status register where the bit mask has a '1' bit.
     *
     * @param bitMask
     */
    public void clearFlags(int bitMask)
    {
        this.statusRegister &= ~bitMask;
    }

    private void updateFlags()
    {
        int clearMask = 0xffffffff;
        int setMask = 0;
        if ( value == 0 ) {
            setMask |= FLAG_ZERO;
        } else {
            clearMask &= ~FLAG_ZERO;
        }

        if ( ( value & 1<<31 ) != 0 ) {
            setMask |= FLAG_NEGATIVE;
        } else {
            clearMask &= ~FLAG_NEGATIVE;
        }
        this.statusRegister = (this.statusRegister & clearMask ) | setMask;
    }

    private void decodeSourceOperand(int instruction, int operandSize)
    {
        // InstructionEncoding.of("ooooDDDMMMmmmsss");
        int eaMode     = (instruction & 0b111000) >> 3;
        int eaRegister = (instruction & 0b000111);

        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                value = dataRegisters[eaRegister];
                return;
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                value = addressRegisters[eaRegister];
                return;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b000:
                        /*
                         * MOVE (xxx).W,... (1 extra word).
                         * ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(000),1 ),
                         */
                        value = memLoadWord(pc);
                        pc += 2;
                        return;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        value = memLoadLong(pc);
                        pc += 4;
                        return;
                }
                // $$FALL-THROUGH$$
            default:
                if ( decodeOperand(operandSize, eaMode, eaRegister) )
                {
                    value = memLoad(ea, operandSize);
                }
        }
    }

    /**
     * Stores the current operation's value according to the
     * destination in the given instruction word.
     *
     * @param instruction
     * @param operandSize
     */
    private void storeValue(int instruction,int operandSize)
    {
        // instruction word: ooooDDDMMMmmmsss
        int eaMode     = (instruction & 0b0001_1100_0000) >> 6;
        int eaRegister = (instruction & 0b1110_0000_0000) >> 9;

        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                switch( operandSize )
                {
                    case 1:
                        dataRegisters[eaRegister] = value & 0x00ff;
                        return;
                    case 2:
                        dataRegisters[eaRegister] = value & 0xffff;
                        return;
                    case 4:
                        dataRegisters[eaRegister] = value;
                        return;
                }
                throw new RuntimeException("Unreachable code reached");
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                if ( operandSize != 4 ) {
                    throw new IllegalArgumentException("Unexpected operand size "+operandSize+" for address register");
                }
                addressRegisters[eaRegister] = value;
                break;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b000:
                        /*
                         * MOVE (xxx).W,... (1 extra word).
                         * ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(000),1 ),
                         */
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        addressRegisters[eaRegister] = value;
                        break;
                }
                triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
                return;
            default:
                if (  decodeOperand(operandSize, eaMode, eaRegister) )
                {
                    switch (operandSize)
                    {
                        case 1:
                            memory.writeByte(ea, value);
                            break;
                        case 2:
                            memory.writeWord(ea, value);
                            break;
                        case 4:
                            memory.writeLong(ea, value);
                            break;
                        default:
                            throw new RuntimeException("Unreachable code reached");
                    }
                }
        }
    }

    private void badAlignment(int address,MemoryAccessException.Operation operation)
    {
        /*
         * 1. Word
         *
         * Bit 15-5: unused
         * Bit 4:   R/W
         * Bit 3:   Instruction/NOT
         * Bit 2-0: Function code
         *
         * 2. Word: Access address high
         * 3. Word: Access address low
         *
         * 4. Word: Instruction word
         */
        final boolean isInstruction = address == pc;
        final int functionCode = 0; // TODO: What is the function code?
        int formatWord = (operation.isRead ? 1<<4 : 0 ) | ( isInstruction ? 1<<3:0 ) | functionCode;
        final int instructionWord = memory.readWordNoCheck(pcAtStartOfLastInstruction );

        final long irqData = ( (long) formatWord << (64-16) | address << (64-48) | (instructionWord & 0xffff) );

        triggerIRQ(IRQ.ADDRESS_ERROR,irqData );
    }

    private void checkPendingIRQ() {

        if ( irqStackPtr > 0 )
        {
            if ( activeIrq == null || irqStack[irqStackPtr-1].priority > activeIrq.priority )
            {
                irqStackPtr--;
                final IRQ irq = irqStack[irqStackPtr];
                final long irqData = this.irqData[irqStackPtr];
                irqStack[irqStackPtr] = null;
                this.irqData[irqStackPtr] = 0;
                triggerIRQ(irq,irqData);
            }
        }
    }

    private void pushIRQ(IRQ irq,long irqData)
    {
        this.irqStack[irqStackPtr] = irq;
        this.irqData[irqStackPtr] = irqData;
        this.irqStackPtr++;
    }

    public void reset() {
        triggerIRQ(IRQ.RESET,0);
    }

    private boolean assertSupervisorMode()
    {
        if ( isSupervisorMode() ) {
            return true;
        }
        triggerIRQ(IRQ.PRIVILEGE_VIOLATION,0);
        return false;
    }

    private void triggerIRQ(IRQ irq, long irqData)
    {
        if ( irq == IRQ.RESET )
        {
            // clear interrupt stack
            irqStackPtr = 0;
            activeIrq = null;

            supervisorModeStackPtr = memLoadLong(0 );
            addressRegisters[7] = supervisorModeStackPtr;
            pc = memLoadLong(4 );
            // enter supervisor mode, disable tracing, set interrupt level 7
            statusRegister = ( (FLAG_I2|FLAG_I1|FLAG_I0) | FLAG_SUPERVISOR_MODE ) & ~(FLAG_T0|FLAG_T1);
            return;
        }

        // TODO: Implement support for emulating hardware interrupts, needs
        // TODO: to honor FLAG_I2|FLAG_I1|FLAG_I0 priorities (IRQs with less than/equal priority get ignored)

        if ( this.activeIrq != null && this.activeIrq.priority > irq.priority )
        {
            // higher prio IRQ already active, queue this IRQ
            pushIRQ(irq,irqData);
            return;
        }
        enterIRQ(irq,irqData);
    }

    private void enterIRQ(IRQ irq,long irqData)
    {
        // copy current SR value
        int oldSr = statusRegister;

        // remember user mode stack pointer
        if ( ! isSupervisorMode() )
        {
            userModeStackPtr = addressRegisters[7];
            addressRegisters[7] = supervisorModeStackPtr;
        }

        activeIrq = irq;

        // assert supervisor mode
        statusRegister = ( statusRegister | FLAG_SUPERVISOR_MODE ) & ~(FLAG_T0|FLAG_T1);

        if ( irq.group == IRQGroup.GROUP0 )
        {
            // GROUP0 IRQs push additional data on the stack

            /*
             * 1. Word
             *
             * Bit 15-5: unused
             * Bit 4:   R/W
             * Bit 3:   Instruction/NOT
             * Bit 2-0: Function code
             *
             * 2. Word: Access address high
             * 3. Word: Access address low
             *
             * 4. Word: Instruction word
             */
            pushWord( (int) (irqData >> (64-16) ));
            pushWord( (int) (irqData >> (64-32) ) );
            pushWord( (int) (irqData >> (64-48) ) );
            pushWord( (int) irqData);
        }

        // push old status register
        pushWord(oldSr);

        // push old program counter
        if ( irq == IRQ.ADDRESS_ERROR )
        {
            // FIXME: Currently decoding operand addresses and
            // FIXME: actually loading/storing values from memory are interleaved
            // FIXME: so PC might point to the middle of an instruction when
            // FIXME: the address error gets raised....
            // FIXME: This in turn might cause us to push a PC value that will
            // FIXME: immediately cause another crash when returning from SV mode...
            if ( (pc & 1) != 0) {
                pushLong( pc+1 );
            } else {
                pushLong( pc );
            }
        } else {
            pushLong( pc );
        }
        final int newAddress = memory.readLongNoCheck(irq.pcVectorAddress);
        if ( newAddress == 0 ) {
            System.err.println("***********************************");
            System.err.println("* Using UNINITIALIZED (=$00000000) interrupt vector for "+irq);
            System.err.println("***********************************");
            // TODO: exception commented out because so that some exception tests in CPUTest work...fix tests and re-enable this exception again
            // throw new CPUHaltedException("Uninitialized (=$00000000) interrupt vector for "+irq);
        }
        this.pc = newAddress;
    }

    private void returnFromException()
    {
        if ( ! isSupervisorMode() )
        {
            // ERROR: Not in supervisor mode
            triggerIRQ(IRQ.FTRAP_TRAP_TRAPV,0);
            return;
        }

        if( activeIrq.group == IRQGroup.GROUP0 ) {
            throw new RuntimeException("Cannot return from GROUP0 irq "+activeIrq+" using RTE");
        }

        activeIrq = null;

        pc = popLong();
        statusRegister = popWord();

        // switch back to user-mode stack
        supervisorModeStackPtr = addressRegisters[7];
        addressRegisters[7] = userModeStackPtr;
    }

    private void pushWord(int value) {

        int sp = addressRegisters[7];
        // MOVE Dx,-(A7)
        sp -= 2;
        memory.writeWord(sp,value);

        addressRegisters[7]= sp;
    }

    private int popWord() {

        int sp = addressRegisters[7];

        // MOVE.W (A7)+,Dx
        final int value = memory.readWord(sp);
        sp += 2;

        addressRegisters[7]= sp;

        return value;
    }

    private void pushLong(int value) {

        int sp = addressRegisters[7];

        // MOVE Dx,-(A7)
        sp -= 2;
        memory.writeWord(sp, value >> 16 ); // push high

        sp -= 2;
        memory.writeWord(sp,value); // push low

        addressRegisters[7]= sp;
    }

    private int popLong() {

        int sp = addressRegisters[7];

        int lo = memory.readWord(sp);
        sp += 2;

        int hi = memory.readWord(sp);
        sp += 2;

        addressRegisters[7]= sp;

        return (hi << 16) | ( lo & 0xffff);
    }

    public boolean isExtended() { return (statusRegister & FLAG_EXTENDED) != 0; }
    public boolean isNotExtended() { return (statusRegister & FLAG_EXTENDED) == 0; }

    public boolean isNegative() { return (statusRegister & FLAG_NEGATIVE) != 0; }
    public boolean isNotNegative() { return (statusRegister & FLAG_NEGATIVE) == 0; }

    public boolean isZero() { return (statusRegister & FLAG_ZERO) != 0; }
    public boolean isNotZero() { return (statusRegister & FLAG_ZERO) == 0; }

    public boolean isOverflow() { return (statusRegister & FLAG_OVERFLOW) != 0; }
    public boolean isNotOverflow() { return (statusRegister & FLAG_OVERFLOW) == 0; }

    public boolean isCarry() { return (statusRegister & FLAG_CARRY) != 0; }
    public boolean isNotCarry() { return (statusRegister & FLAG_CARRY) == 0; }

    public boolean isSupervisorMode() { return ( statusRegister & FLAG_SUPERVISOR_MODE) != 0; }
    public boolean isUserMode() { return ( statusRegister & FLAG_SUPERVISOR_MODE) == 0; }

    private void setStatusRegister(int newValue)
    {
        if ( isSupervisorMode() )
        {
            // we're currently in supervisor mode so
            // code being executed may switch back to user-mode
            if ( ( newValue & FLAG_SUPERVISOR_MODE) == 0 )
            {
                // switch back to user-mode stack
                supervisorModeStackPtr = addressRegisters[7];
                addressRegisters[7] = userModeStackPtr;

                activeIrq = null;
            }
        }
        this.statusRegister = newValue;
    }

    @Override
    public String toString()
    {
        final int insn = memory.readWordNoCheck(pc);
        final String binaryInsn =
                StringUtils.leftPad(Integer.toBinaryString((insn & 0xff00) >>8 ),8,"0")+"_"+
                StringUtils.leftPad(Integer.toBinaryString((insn & 0xff) ),8,"0");

        final String flagHelp = "|T1|T0|S|M|I2|I1|I0|X|N|Z|O|C|";
        String flags = "|"+
                ( ( statusRegister & FLAG_T1 ) != 0 ? "XX" : "--" )+"|"+
                        ( ( statusRegister & FLAG_T0 ) != 0 ? "XX" : "--" )+"|"+
                        ( ( statusRegister & FLAG_SUPERVISOR_MODE ) != 0 ? "X" : "-" )+"|"+
                        ( ( statusRegister & FLAG_MASTER_INTERRUPT ) != 0 ? "X" : "-" )+"|"+
                        ( ( statusRegister & FLAG_I2 ) != 0 ? "XX" : "--" )+"|"+
                        ( ( statusRegister & FLAG_I1 ) != 0 ? "XX" : "--" )+"|"+
                        ( ( statusRegister & FLAG_I0 ) != 0 ? "XX" : "--" )+"|"+
                        ( ( statusRegister & FLAG_EXTENDED ) != 0 ? "X" : "-" )+"|"+
                        ( ( statusRegister & FLAG_NEGATIVE ) != 0 ? "X" : "-" )+"|"+
                        ( ( statusRegister & FLAG_ZERO     ) != 0 ? "X" : "-" )+"|"+
                        ( ( statusRegister & FLAG_OVERFLOW ) != 0 ? "X" : "-" )+"|"+
                        ( ( statusRegister & FLAG_CARRY    ) != 0 ? "X" : "-" )+"|";
        return "CPU[ pc = "+ Misc.hex(pc)+" , insn="+binaryInsn+", sp="+Misc.hex(addressRegisters[7])+",IRQ="+activeIrq+"]\n"+flagHelp+"\n"+flags;
    }
}