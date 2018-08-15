package de.codersourcery.m68k.emulator.cpu;

import de.codersourcery.m68k.emulator.Memory;
import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.assembler.arch.AddressingModeKind;
import de.codersourcery.m68k.assembler.arch.CPUType;
import de.codersourcery.m68k.assembler.arch.Condition;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

/**
 * M68000 cpu emulation.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CPU
{
    private final CPUType cpuType;

    private enum BinaryLogicalOp
    {
        AND {
            @Override
            public int apply(int value, int mask)
            {
                return value & mask;
            }
        },
        OR {
            @Override
            public int apply(int value, int mask)
            {
                return value | mask;
            }
        },
        EOR {
            @Override
            public int apply(int value, int mask)
            {
                return value ^ mask;
            }
        };

        public abstract int apply(int value,int mask);
    }

    private enum BinaryLogicalOpMode
    {
        REGULAR,IMMEDIATE,SR,CCR;
    }

    private enum BitOp {
        FLIP,
        CLEAR,
        TEST,
        SET;
    }

    private enum BitOpMode {
        IMMEDIATE,REGISTER
    }

    public enum RotateOperandMode
    {
        // ASL/LSL/ROL #1,Dx
        IMMEDIATE,
        // ASL/LSL/ROL <ea>
        MEMORY,
        // ASL/LSL/ROL Dx,Dy
        REGISTER;
    }

    public enum RotateMode {
        ARITHMETIC_SHIFT,
        LOGICAL_SHIFT,
        ROTATE,
        ROTATE_WITH_EXTEND;
    }

    public enum ArithmeticOp {
        ADD,SUB
    }

    private enum ArithmeticOpMode
    {
        REGULAR,
        ADDRESS_REGISTER,
        IMMEDIATE,
        QUICK,
        EXTENDED
    }

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

    public static final int ALL_USERMODE_FLAGS  = FLAG_NEGATIVE|FLAG_ZERO|FLAG_OVERFLOW|FLAG_CARRY|FLAG_EXTENDED;
    public static final int USERMODE_FLAGS_NO_X = FLAG_NEGATIVE|FLAG_ZERO|FLAG_OVERFLOW|FLAG_CARRY;

    public final Memory memory;

    public final int[] dataRegisters = new int[8];
    public final int[] addressRegisters = new int[8];

    public int statusRegister;

    private final long[] irqData = new long[10];
    private final IRQ[] irqStack = new IRQ[10];

    private int irqStackPtr;
    public IRQ activeIrq; // currently active IRQ (if any)

    private boolean stopped;

    private int userModeStackPtr;
    private int supervisorModeStackPtr;

    private boolean addressRegisterDirectAllowed;

    public int pcAtStartOfLastInstruction;
    public int pc;

    public int cycles;

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

    public CPU(CPUType type,Memory memory)
    {
        Validate.notNull(type, "type must not be null");
        Validate.notNull(memory, "memory must not be null");
        this.memory = memory;
        this.cpuType = type;
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
                throw new RuntimeException("Unreachable code reached,size: "+size);
        }
    }

    private void memStore(int address,int value,int size)
    {
        switch(size) {
            case 1: memory.writeByte(address,value); break;
            case 2: memory.writeWord(address,value); break;
            case 4: memory.writeLong(address,value); break;
            default:
                throw new RuntimeException("Unreachable code reached,size: "+size);
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
     * Calculates the effective address based on EA mode and EA register bit patterns.
     *
     * CAREFUL: This method will increment/decrement address registers if post-increment/pre-decrement addressing is active.
     *
     * @param operandSize
     * @param eaMode
     * @param eaRegister
     * @param applyPostPre whether to increment/decrement address registers if post-increment/pre-decrement addressing is being used
     */
    private void calculateEffectiveAddress(int operandSize, int eaMode, int eaRegister,boolean applyPostPre) {
        calculateEffectiveAddress(operandSize,eaMode,eaRegister,true,applyPostPre);
    }

    /**
     * Calculates the effective address based on EA mode and EA register bit patterns.
     *
     * CAREFUL: This method will increment/decrement address registers if post-increment/pre-decrement addressing is active.
     *
     * @param operandSize
     * @param eaMode
     * @param eaRegister
     * @param advancePC
     * @param applyPostPre whether to increment/decrement address registers if post-increment/pre-decrement addressing is being used
     */
    private void calculateEffectiveAddress(int operandSize, int eaMode, int eaRegister,boolean advancePC,boolean applyPostPre)
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
                cycles += operandSize == 4 ? 8 : 4;
                return;
            case 0b011:
                // ADDRESS_REGISTER_INDIRECT_POST_INCREMENT;
                ea = addressRegisters[ eaRegister ];

                if ( applyPostPre )
                {
                    if (eaRegister == 7 && operandSize == 1)
                    {
                        // stack ptr always needs to be an even address
                        addressRegisters[eaRegister] += 2;
                    }
                    else
                    {
                        addressRegisters[eaRegister] += operandSize;
                    }
                }
                cycles += operandSize == 4 ? 8 : 4;
                return;
            case 0b100:
                // ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT;
                if (eaRegister == 7 && operandSize == 1)
                {
                    // stack ptr always needs to be an even address
                    ea = addressRegisters[eaRegister] - 2;
                }
                else
                {
                    ea = addressRegisters[eaRegister] - operandSize;
                }
                if ( applyPostPre )
                {
                    addressRegisters[ eaRegister ] = ea;
                }
                cycles += operandSize == 4 ? 10 : 6;
                return;
            case 0b101:
                // ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
                int offset = memLoadWord(pc);
                pc += 2; // skip displacement
                cycles += operandSize == 4 ? 12 : 8;
                ea = addressRegisters[ eaRegister ] + offset; // hint: memLoad() performs sign-extension to 32 bits
                return;
            case 0b110:
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
                // MEMORY_INDIRECT_POSTINDEXED
                // MEMORY_INDIRECT_PREINDEXED

                int extensionWord = memory.readWordNoCheck(pc);
                pc += 2; // skip extension word

                int baseRegisterValue = 0;
                int baseDisplacement;
                // bit 8 can be used to distinguish between brief extension words (bit = 0)
                // and full extension words (bit =  1)
                boolean isFullExtensionWord = (extensionWord & 0b0000_0001_0000_1000) == 0b0000_0001_0000_0000;
                if ( isFullExtensionWord )
                {
                    if ( cpuType.isNotCompatibleWith(CPUType.M68020 ) )
                    {
                        final int insn = memLoadWord(pcAtStartOfLastInstruction);
                        throw new IllegalInstructionException(pcAtStartOfLastInstruction,insn);
                    }
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
                            pc += 2;
                            int intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            ea = memLoadLongWithCheck(intermediateAddress)+outerDisplacement;
                            return;
                        case 0b0011: // Indirect Preindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement + decodeIndexRegisterValue(extensionWord );
                            ea = memLoadLongWithCheck(intermediateAddress) + outerDisplacement;
                            return;
                        case 0b0100: // Reserved
                            break;
                        case 0b0101: // Indirect Postindexed with Null Outer Displacement
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + decodeIndexRegisterValue(extensionWord );
                            return;
                        case 0b0110: // Indirect Postindexed with Word Outer Displacement
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return;
                        case 0b0111: // Indirect Postindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + decodeIndexRegisterValue(extensionWord ) + outerDisplacement;
                            return;
                        case 0b1000: // No Memory Indirect Action, Index suppressed
                            ea = baseRegisterValue + baseDisplacement;
                            return;
                        case 0b1001: // Memory Indirect with Null Outer Displacement, Index suppressed
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress);
                            return;
                        case 0b1010: // Memory Indirect with Word Outer Displacement, Index suppressed
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + outerDisplacement;
                            return;
                        case 0b1011: // Memory Indirect with Long Outer Displacement, Index suppressed
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            intermediateAddress = baseRegisterValue + baseDisplacement;
                            ea = memLoadLongWithCheck(intermediateAddress) + outerDisplacement;
                            return;
                        case 0b1100: // Reserved
                        case 0b1101: // Reserved
                        case 0b1110: // Reserved
                        case 0b1111: // Reserved
                            break;
                    }
                    final int insn = memLoadWord(pcAtStartOfLastInstruction);
                    throw new IllegalInstructionException(pcAtStartOfLastInstruction,insn);
                }

                // brief extension word with 8-bit displacement
                baseRegisterValue = addressRegisters[ eaRegister ];
                baseDisplacement = (byte) (extensionWord & 0xff);
                baseDisplacement = (baseDisplacement<<24)>>24;
                ea = baseRegisterValue+decodeIndexRegisterValue(extensionWord)+baseDisplacement;

                /* TODO: Cycle count not correct here as I don't know how to
                 * TODO: differentiate indirect with displacement from indirect with index ...
                 *
                 *                                                            BYTE/WORD   LONG
                 * d(An)	 address register indirect with displacement	 8(2/0)		12(3/0)
                 * d(An,ix)  address register indirect with index	        10(2/0)		14(3/0)
                 */
                cycles += operandSize == 4 ? 12 : 8;
                return;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b010:
                        // PC_INDIRECT_WITH_DISPLACEMENT(0b111,fixedValue(0b010),1),
                        baseDisplacement = memory.readWordNoCheck(pc);
                        ea = baseDisplacement + pc;
                        cycles += operandSize == 4 ? 12 : 8;
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
                        extensionWord = memory.readWordNoCheck(pc);
                        pc += 2;
                        if ( (extensionWord & 1<<8) == 0 ) { // 8-bit displacement
                            baseDisplacement = ((extensionWord & 0xff) << 24) >> 24;
                        } else {
                            baseDisplacement = loadBaseDisplacement(extensionWord);
                        }
                        ea = baseDisplacement + origPc + decodeIndexRegisterValue(extensionWord);
                        cycles += operandSize == 4 ? 14 : 10; // TODO: Most likely wrong ....
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
                        cycles += operandSize == 4 ? 12 : 8;
                        pc += 2;
                        return;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        ea = memLoadLong(pc);
                        cycles += operandSize == 4 ? 12 : 8;
                        pc += 4;
                        return;
                    case 0b100:
                        /*
                         * MOVE #xxxx,.... (1-6 extra words).
                         * // 1,2,4, OR 6, EXCEPT FOR PACKED DECIMAL REAL OPERANDS
                         * IMMEDIATE_VALUE(0b111,fixedValue(100), 6),   // move #XXXX
                         */
                        ea = pc;
                        cycles += operandSize == 4 ? 8 : 4;
                        pc += operandSize;
                        return;
                }
        }
        final int insn = memLoadWord(pcAtStartOfLastInstruction);
        throw new IllegalInstructionException(pcAtStartOfLastInstruction,insn);
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

    /**
     *
     * @deprecated UNIT-TESTING ONLY. Invoke {@link #executeOneCycle()} instead.
     */
    @Deprecated
    public void executeOneInstruction() {
        while ( cycles > 1 ) {
            executeOneCycle();
        }
        executeOneCycle();
    }

    public void executeOneCycle()
    {
        if ( --cycles > 0 ) {
            return;
        }

        if ( stopped ) { // TODO: Move above "--cycles" line so that cycles does not go below zero when CPU is stopped...
            return;
        }

        try
        {
            internalExecutionOneCycle();

            checkPendingIRQ();
        }
        catch(IllegalInstructionException e)
        {
            e.printStackTrace();
            illegalInstruction();
        }
        catch(MemoryAccessException e)
        {
            badAlignment(e.offendingAddress, e.operation );
        }
    }

    private void internalExecutionOneCycle()
    {
        addressRegisterDirectAllowed = true;


        if ( ( pc & 1 ) != 0 )
        {
            System.out.println(">>>> Executing instruction "+memory.readWordNoCheck(pc)+" at 0x"+Integer.toHexString(pc));
            badAlignment(pc,MemoryAccessException.Operation.READ_WORD);
            return;
        }

        pcAtStartOfLastInstruction = pc;

        int instruction= memory.readWordNoCheck(pc);
        System.out.println(">>>> Executing instruction "+Misc.hex(instruction)+" ("+Misc.binary16Bit(instruction)+") at 0x"+Integer.toHexString(pc));
        pc += 2;

        switch(instruction)
        {
            /* ================================
             * Bit Manipulation/MOVEP/Immediate
             * ================================
             */
            case 0b0000001000111100: // ANDI to CCR
                binaryLogicalOp(instruction,BinaryLogicalOp.AND,BinaryLogicalOpMode.CCR);
                return;
            case 0b0000001001111100: // ANDI to SR
                binaryLogicalOp(instruction,BinaryLogicalOp.AND,BinaryLogicalOpMode.SR);
                return;
            /* ================================
             * Miscellaneous instructions
             * ================================
             */
            case 0b0100111001110110: // TRAPV
                if ( isOverflow() )
                {
                    triggerIRQ( IRQ.FTRAP_TRAP_TRAPV, 0 );
                }
                else
                {
                    cycles += 4;
                }
                return;
            case 0b0100111001110111: // RTR
                int cr = popWord();
                pc = popLong();
                statusRegister = (statusRegister & 0xff00) | (cr & 0xff);
                cycles = 20;
                return;
            case 0b0100111001110011: // RTE
                returnFromException();
                cycles = 20;
                return;
            case 0b0100111001110001:  // NOP
                cycles = 4;
                return;
            case 0b0100111001110101:  // RTS
                pc = popLong();
                cycles = 16;
                return;
            case 0b0100101011111100: // ILLEGAL
                triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
                return;
            case 0b0100111001110000: // RESET
                cycles = 132;
                return;

            case 0b0100111001110010: // STOP
                if ( assertSupervisorMode() )
                {
                    statusRegister = memLoadWord( pc );
                    pc += 2;
                    cycles = 4;
                    stopped = true;
                }
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

                switch(instruction & 0b1111000111111000)
                {
                    case 0b0000000100001000:
                        // MOVEP_WORD_FROM_MEMORY_ENCODING
                        movepFromMemoryToRegister(instruction,2);
                        return;
                    case 0b0000000101001000:
                        // MOVEP_LONG_FROM_MEMORY_ENCODING
                        movepFromMemoryToRegister(instruction,4);
                        return;
                    case 0b0000000110001000:
                        // MOVEP_WORD_TO_MEMORY_ENCODING
                        movepFromRegisterToMemory(instruction,2);
                        return;
                    case 0b0000000111001000:
                        // MOVEP_LONG_TO_MEMORY_ENCODING
                        movepFromRegisterToMemory(instruction,4);
                        return;
                }

                if ( instruction == 0b0000000000111100 ) {
                    // ORI to CCR
                    binaryLogicalOp(instruction,BinaryLogicalOp.OR,BinaryLogicalOpMode.CCR);
                    return;
                }

                if ( instruction == 0b0000000001111100 ) {
                    // ORI to SR
                    binaryLogicalOp(instruction,BinaryLogicalOp.OR,BinaryLogicalOpMode.SR);
                    return;
                }

                if ( instruction == 0b0000101000111100 )
                {
                    // EORI #xx,CCR
                    binaryLogicalOp(instruction,BinaryLogicalOp.EOR,BinaryLogicalOpMode.CCR);
                    return;
                }

                if ( instruction == 0b0000101001111100)
                {
                    // EORI #xx,SR
                    if ( assertSupervisorMode() )
                    {
                        binaryLogicalOp(instruction,BinaryLogicalOp.EOR,BinaryLogicalOpMode.SR);
                    }
                    return;
                }

                if ( ( instruction & 0b1111111100000000 ) == 0b0000101000000000 ) {
                    // EORI #xx,<ea>
                    binaryLogicalOp(instruction,BinaryLogicalOp.EOR,BinaryLogicalOpMode.IMMEDIATE);
                    return;
                }

                if ( ( instruction & 0b1111111100000000 ) == 0b0000000000000000 )
                {
                    // ORI #xx,<ea>
                    binaryLogicalOp(instruction,BinaryLogicalOp.OR,BinaryLogicalOpMode.IMMEDIATE);
                    return;
                }

                if ( ( instruction & 0b1111111100000000 ) == 0b0000110000000000 ) {
                    // CMPI
                    subi(instruction,true);
                    return;
                }

                if ( ( instruction & 0b1111111100000000) == 0b0000010000000000 )
                {
                    // SUBI
                    subi(instruction);
                    return;
                }

                if ( (instruction & 0b1111111100000000) == 0b0000011000000000 )
                {
                    // ADDI
                    final int operandSizeInBytes = 1 << ( (instruction & 0b11000000) >> 6 );
                    int srcValue;
                    switch(operandSizeInBytes) {
                        case 1:
                            srcValue = (memLoadWord(pc)<<24)>>24; // sign-extend
                            pc += 2;
                            break;
                        case 2:
                            srcValue = memLoadWord(pc);
                            pc += 2;
                            break;
                        case 4:
                            srcValue = memLoadLong(pc);
                            pc += 4;
                            break;
                        default:
                            throw new RuntimeException("Unreachable code reached");
                    }
                    final int eaMode = (instruction & 0b111000) >> 3;
                    final int eaRegister = instruction & 0b111;

                    if ( decodeSourceOperand(instruction,operandSizeInBytes,false,false) ) {
                        // register operand
                        switch(operandSizeInBytes) {
                            case 1:
                            case 2:
                                cycles+=8;
                                break;
                            case 4:
                                cycles += 12;
                                break;
                        }
                    } else {
                        // memory operand
                        switch(operandSizeInBytes) {
                            case 1:
                            case 2:
                                cycles+=16;
                                break;
                            case 4:
                                cycles += 20;
                                break;
                        }
                    }

                    final int dstValue = value;

                    value += srcValue;

                    storeValue(eaMode, eaRegister, operandSizeInBytes);

                    updateFlags(srcValue, dstValue, value, operandSizeInBytes, CCOperation.ADDITION, CPU.ALL_USERMODE_FLAGS);
                    return;
                }

                if ( ( instruction & 0b1111111100000000) == 0b0000001000000000 ) {
                    // ANDI #xx,<ea>
                    binaryLogicalOp(instruction,BinaryLogicalOp.AND,BinaryLogicalOpMode.IMMEDIATE);
                    return;
                }
                if ( (instruction & 0b1111000111000000) == 0b0000000101000000) {
                    // BCHG Dn,<ea>
                    bitOp(instruction,BitOp.FLIP,BitOpMode.REGISTER);
                    return;
                }
                if ( (instruction & 0b1111111111000000) == 0b0000100001000000) {
                    // BCHG #xx,<ea>
                    bitOp(instruction,BitOp.FLIP,BitOpMode.IMMEDIATE);
                    return;
                }

                if ( (instruction & 0b1111000111000000) == 0b0000000111000000) {
                    // BSET Dn,<ea>
                    bitOp(instruction,BitOp.SET,BitOpMode.REGISTER);
                    return;
                }
                if ( (instruction & 0b1111111111000000) == 0b0000100011000000) {
                    // BSET #xx,<ea>
                    bitOp(instruction,BitOp.SET,BitOpMode.IMMEDIATE);
                    return;
                }
                if ( (instruction & 0b1111000111000000) == 0b0000000110000000) {
                    // BCLR Dn,<ea>
                    bitOp(instruction,BitOp.CLEAR,BitOpMode.REGISTER);
                    return;
                }
                if ( (instruction & 0b1111111111000000) == 0b0000100010000000) {
                    // BCLR #xx,<ea>
                    bitOp(instruction,BitOp.CLEAR,BitOpMode.IMMEDIATE);
                    return;
                }
                if ( (instruction & 0b1111000111000000) == 0b0000000100000000) {
                    // BTST Dn,<ea>
                    bitOp(instruction,BitOp.TEST,BitOpMode.REGISTER);
                    return;
                }
                if ( (instruction & 0b1111111111000000) == 0b0000100000000000) {
                    // BTST #xx,<ea>
                    bitOp(instruction,BitOp.TEST,BitOpMode.IMMEDIATE);
                    return;
                }
                break;
            /* ================================
             * Move Byte
             * ================================
             */
            case 0b0001_0000_0000_0000:
                decodeSourceOperand(instruction,2,false); // operandSize == 2 because PC must always be even so byte is actually stored as 16 bits
                value = (value<<24)>>24; // sign-extend so that updateFlagsAfterMove() works correctly
                updateFlagsAfterMove(1);
                storeValue(instruction,1 );
                return;
            /* ================================
             * Move Long
             * ================================
             */
            case 0b0010_0000_0000_0000:
                if ( (instruction & 0b0010000111000000) == 0b0010000001000000 ) {
                    // MOVEA
                    decodeSourceOperand(instruction,4,false);
                    // MOVEA does not change any flags
                    storeValue(instruction,4 );
                    // TODO: MOVEA instruction timing ???
                    return;
                }
                decodeSourceOperand(instruction,4,false);
                updateFlagsAfterMove(4); // hint: no sign-extension needed here
                storeValue(instruction,4 );
                return;
            /* ================================
             * Move Word
             * ================================
             */
            case 0b0011_0000_0000_0000:
                if ( (instruction & 0b0011000111000000) == 0b0011000001000000 ) {
                    // MOVEA
                    decodeSourceOperand(instruction,2,false);
                    // MOVEA does not change any flags
                    storeValue(instruction,4 );
                    return;
                }
                decodeSourceOperand(instruction,2,false);
                updateFlagsAfterMove(2);
                storeValue(instruction,2 );
                return;
            /* ================================
             * Miscellaneous instructions
             * ================================
             */
            case 0b0100_0000_0000_0000:

                if ( ( instruction & 0b1111111111000000 ) == 0b0100000011000000 )
                {
                    // MOVE_FROM_SR_ENCODING
                    if ( assertSupervisorMode() )
                    {
                        value = statusRegister;
                        storeValue( (instruction & 0b111000) >>> 3, instruction & 0b111, 2 );
                        cycles += 12;
                    }
                    return;
                }

                if ( ( instruction & 0b1111111111000000 ) == 0b0100011011000000 ) {
                    // MOVE_TO_SR_ENCODING
                    if ( assertSupervisorMode() )
                    {
                        decodeSourceOperand( instruction,2,false );
                        setStatusRegister( value & 0xffff );
                        cycles += 12;
                    }
                    return;
                }
                if ( (instruction & 0b1111111100000000) == 0b0100011000000000) {
                    // NOT
                    final int sizeBits = (instruction &0b11000000) >> 6;
                    final int eaMode = (instruction&0b111000) >> 3;
                    final int eaRegister = (instruction&0b111);
                    final int operandSize = 1 << sizeBits;
                    if ( decodeSourceOperand( instruction,operandSize,false ) )
                    {
                        cycles += (operandSize <= 2) ? 4 : 6; // register operation
                    } else {
                        cycles += (operandSize <= 2) ? 8 : 12; // memory operation
                    }
                    value = ~value;
                    storeValue( eaMode,eaRegister,operandSize);
                    updateFlagsAfterTST( operandSize );
                    return;
                }
                if ( ( instruction & 0b1111111111000000 ) == 0b0100101011000000 )
                {
                    // TAS
                    if ( decodeSourceOperand( instruction,1,false,false ) ) {
                        cycles += 4; // register operation
                    } else {
                        cycles += 10; // memory operation
                    }
                    int setMask = 0;
                    if ( (value & 1<<7) != 0 ) {
                        setMask |= FLAG_NEGATIVE;
                    } else if ( (value & 0xff) == 0 ) {
                        setMask |= FLAG_ZERO;
                    }
                    statusRegister = (statusRegister & ~(FLAG_ZERO|FLAG_NEGATIVE|FLAG_CARRY|FLAG_OVERFLOW))
                            | setMask;
                    value |= 1<<7;
                    final int eaMode     = (instruction & 0b111000) >> 3;
                    final int eaRegister = (instruction & 0b000111);
                    storeValue( eaMode, eaRegister, 1 );
                    return;
                }
                if ( (instruction & 0b1111111100000000) == 0b0100101000000000) {
                    // TST
                    final int operandSize = 1 << ((instruction & 0b11000000) >>> 6);
                    decodeSourceOperand( instruction,operandSize,false);

                    updateFlagsAfterTST( operandSize );
                    cycles += 4;
                    return;
                }
                if ( (instruction & 0b1111111100000000) == 0b0100001000000000)
                {
                    // CLR
                    int eaMode = (instruction & 0b111000)>>>3;
                    int eaRegister  = (instruction & 0b111);
                    final int operandSize =  1 << ((instruction & 0b11000000) >>> 6);

                    value = 0;
                    switch(operandSize) {
                        case 1:
                        case 2:
                            cycles += 2; // TODO: Not correct
                            break;
                        case 4:
                            cycles += 4; // TODO: Not correct
                            break;
                    }
                    statusRegister = ( statusRegister & ~(FLAG_NEGATIVE|FLAG_OVERFLOW|FLAG_CARRY) ) | FLAG_ZERO;
                    storeValue(eaMode,eaRegister,operandSize);
                    return;
                }

                if ( (instruction & 0b1111111111111000) == 0b0100100010000000)
                {
                    // EXT Byte -> Word
                    final int regNum = instruction & 0b111;
                    final int input = ( dataRegisters[regNum]  << 24) >> 24;
                    int setMask = 0;
                    if ( (input & 0xffff) == 0 ) {
                        setMask |= CPU.FLAG_ZERO;
                    } else if ( input < 0 ) {
                        setMask |= CPU.FLAG_NEGATIVE;
                    }
                    statusRegister = (statusRegister & ~(FLAG_CARRY|FLAG_OVERFLOW|FLAG_ZERO|FLAG_NEGATIVE)) | setMask;
                    dataRegisters[regNum] = (dataRegisters[regNum] & 0xffff0000) | (input & 0xffff);
                    cycles += 4;
                    return;
                }
                if ( (instruction & 0b1111111111111000) == 0b0100100011000000) {
                    // EXT Word -> Long
                    final int regNum = instruction & 0b111;
                    final int value = ( dataRegisters[regNum]  << 16) >> 16;
                    dataRegisters[regNum] = value;
                    int setMask = 0;
                    if ( value == 0 ) {
                        setMask |= CPU.FLAG_ZERO;
                    } else if ( value < 0 ) {
                        setMask |= CPU.FLAG_NEGATIVE;
                    }
                    statusRegister = (statusRegister & ~(FLAG_CARRY|FLAG_OVERFLOW|FLAG_ZERO|FLAG_NEGATIVE)) | setMask;
                    cycles += 4;
                    return;
                }
                if ( ( instruction & 0b1111111111000000 ) == 0b0100010011000000 )
                {
                    // MOVE_TO_CCR_ENCODING
                    decodeSourceOperand(instruction,2,false);
                    System.out.println("Move To CCR: "+value);
                    statusRegister = (statusRegister & ~0b11111) | (value & 0b11111);
                    return;
                }

                if ( ( instruction & 0b1111111110000000 ) == 0b0100100010000000 )
                {
                    // MOVEM_FROM_REGISTERS_ENCODING
                    moveMultipleRegisters(instruction,true);
                    return;
                }
                if ( ( instruction & 0b1111111110000000 ) == 0b0100110010000000 )
                {
                    // MOVEM_TO_REGISTERS_ENCODING
                    moveMultipleRegisters(instruction,false);
                    return;
                }
                if ( (instruction & 0b1111111111111000) == 0b0100111001011000) {
                    // UNLK
                    final int regNum = (instruction & 0b111);
                    addressRegisters[ 7 ] = addressRegisters[regNum];
                    addressRegisters[regNum] = popLong();
                    cycles = 12;
                    return;
                }

                if ( (instruction & 0b1111111111111000) == 0b0100111001010000) {
                    // LINK
                    final int regNum = (instruction & 0b111);
                    final int displacement = memLoadWord(pc);
                    pc += 2;
                    pushLong( addressRegisters[ regNum ] );
                    addressRegisters[ regNum ] = addressRegisters[ 7 ];
                    addressRegisters[7] += displacement; // TODO: Is the displacement in bytes or words ??? Stack pointer always needs to point to an even address....
                    cycles = 16;
                    return;
                }

                if ( (instruction & 0b1111111111000000) == 0b0100111010000000)
                {
                    // JSR
                    decodeSourceOperand(instruction,4,true);
                    pushLong(pc);
                    cycles += 4; // TODO: Timing correct ?
                    pc = ea;
                    return;
                }

                if ( (instruction & 0b1111111100000000) == 0b0100010000000000)
                {
                    // NEG
                    final int sizeBits = (instruction & 0b11000000) >>> 6;
                    final int operandSize = 1<<sizeBits;
                    if ( decodeSourceOperand(instruction,operandSize,false) )
                    {
                        // operand is register
                        cycles += (operandSize <= 2) ? 4 : 6;
                    } else {
                        // operand is memory
                        cycles += (operandSize <= 2) ? 8 : 12;
                    }
                    final int b = value;
                    value = 0 - value;
                    int eaMode     = (instruction & 0b111000) >> 3;
                    int eaRegister = (instruction & 0b000111);
                    storeValue(eaMode,eaRegister,operandSize);
                    int setMask=0;
                    switch(operandSize) {
                        case 1: setMask = isOverflow8Bit( 0,b,value ) ? FLAG_OVERFLOW : 0; break;
                        case 2: setMask = isOverflow16Bit( 0,b,value ) ? FLAG_OVERFLOW : 0; break;
                        case 4: setMask = isOverflow32Bit( 0,b,value ) ? FLAG_OVERFLOW : 0; break;
                    }
                    if ( value < 0 ) {
                        setMask |= FLAG_NEGATIVE | FLAG_CARRY | FLAG_EXTENDED;
                    } else if ( value == 0 ) {
                        setMask |= FLAG_ZERO;
                    } else {
                        setMask |= FLAG_CARRY | FLAG_EXTENDED;
                    }
                    statusRegister = (statusRegister & 0xff00) | setMask;
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
                    decodeSourceOperand(instruction,4,true);
                    pc = ea;
                    cycles += 4; // TODO: Timing correct?
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
                    cycles = 4;
                    return;
                }
                if ( (instruction & 0b1111111111110000) == 0b0100111001000000 ) {
                    // TRAP #xx
                    triggerIRQ(IRQ.userTrapToIRQ( instruction & 0b1111 ),0);
                    cycles = 38;
                    return;
                }

                if ( (instruction & 0b1111000111000000) == 0b0100000111000000 )
                {
                    // LEA
                    decodeSourceOperand(instruction,4,true);
                    final int dstAdrReg = (instruction & 0b1110_0000_0000) >> 9;

                    addressRegisters[dstAdrReg] = ea;
                    // TODO: Cycle timing correct ??
                    return;
                }

                if ( (instruction & 0b1111111111000000) == 0b0100100001000000) {
                    // PEA
                    decodeSourceOperand( instruction,4,true );
                    pushLong( value );
                    return;
                }

                if ( ( instruction & 0b1111111100000000 ) == 0b0100000000000000 )
                {
                    // NEGX
                    final int sizeBits = (instruction &0b11000000) >> 6;
                    final int eaMode = (instruction&0b111000) >> 3;
                    final int eaRegister = (instruction&0b111);
                    final int operandSize = 1 << sizeBits;
                    decodeSourceOperand(instruction,operandSize,false,false);
                    final int srcValue = value;
                    final int dstValue = 0;
                    final int result = dstValue - srcValue - (isExtended() ? 1 : 0);
                    value = result;
                    updateFlags(srcValue,dstValue,result,operandSize,CCOperation.SUBTRACTION,FLAG_EXTENDED|FLAG_NEGATIVE|FLAG_OVERFLOW|FLAG_CARRY);
                    if ( result != 0 ) {
                        statusRegister &= ~CPU.FLAG_ZERO;
                    }
                    storeValue(eaMode,eaRegister,operandSize);
                    return;
                }

                if ( ( instruction & 0b1111000001000000 ) == 0b0100000000000000 )
                {
                    // CHK_ENCODING
                    int sizeBits = (instruction & 0b110000000) >>> 7;
                    final int operandSize; // non-standard operand size encoding...
                    int regNum = (instruction & 0b111000000000) >>> 9;
                    int regValue = dataRegisters[regNum];
                    switch( sizeBits )
                    {
                        case 0b11:
                            operandSize = 2;
                            break;
                        case 0b10:
                            if ( cpuType.isCompatibleWith(CPUType.M68020) )
                            {
                                operandSize = 4;
                                break;
                            }
                            // $$FALL-THROUGH$$
                        default:
                            throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
                    }
                    decodeSourceOperand( instruction,operandSize,false );

                    // compare
                    final boolean lowerBoundViolated = regValue < 0;
                    final boolean upperBoundViolated = regValue > value;
                    final boolean outOfBounds = lowerBoundViolated | upperBoundViolated;
                    if ( outOfBounds ) {
                        if ( lowerBoundViolated ) {
                            statusRegister |= FLAG_NEGATIVE;
                        } else {
                            statusRegister &= ~FLAG_NEGATIVE;
                        }
                        triggerIRQ(IRQ.CHK_CHK2,0);
                    }
                    return;
                }

                break;
            /* ================================
             * ADDQ/SUBQ/Scc/DBcc/TRAPc c
             * ================================
             */
            case 0b0101_0000_0000_0000:

                if ( (instruction & 0b1111000011111000 ) == 0b0101000011001000 )
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
                             * - If the result is not equal to – 1, execution continues at the location indicated RAy the current value of the program
                             *   counter plus the sign-extended 16-bit displacement. The value in the program counter is
                             *   the address of the instruction word of the DBcc instruction plus two. The
                             */
                            pc = pc + memory.readWordNoCheck(pc);
                            cycles = 10;
                            return;
                        } else {
                            cycles = 14;
                        }
                    } else {
                        cycles = 12;
                    }
                    pc += 2; // skip branch offset
                    return;
                }
                if ( ( instruction & 0b1111000011000000 ) == 0b0101000011000000 )
                {
                    // SCC
                    final int cc = (instruction & 0b0000111100000000) >> 8;
                    final int eaMode = (instruction & 0b111000) >> 3;
                    final int eaRegister = (instruction & 0b111);
                    value = Condition.isTrue(this,cc ) ? 0xff : 0x00;
                    storeValue(eaMode,eaRegister,1);
                    return;
                }
                if ( ( instruction & 0b1111000100000000 ) == 0b0101000000000000 )
                {
                    // ADDQ_ENCODING
                    final int operandSizeInBytes = 1 << ( (instruction & 0b11000000) >> 6 );
                    switch(operandSizeInBytes) {
                        case 1:
                        case 2:
                            cycles += 4;
                            break;
                        case 4:
                            cycles += 6;
                            break;
                        default:
                            throw new RuntimeException("Unreachable code reached");
                    }
                    final int eaMode = (instruction & 0b111000) >> 3;
                    final int eaRegister = instruction & 0b111;
                    final boolean dstIsAddressRegister = eaMode == AddressingMode.ADDRESS_REGISTER_DIRECT.eaModeField;

                    int srcValue = (instruction& 0b111000000000) >> 9;
                    if ( srcValue == 0 ) {
                        srcValue = 8;
                    }

                    decodeSourceOperand(instruction,operandSizeInBytes,false,false);
                    final int dstValue = value;

                    value += srcValue;

                    storeValue(eaMode, eaRegister, operandSizeInBytes);

                    if ( ! dstIsAddressRegister )
                    {
                        updateFlags(srcValue, dstValue, value, operandSizeInBytes, CCOperation.ADDITION, CPU.ALL_USERMODE_FLAGS);
                    }
                    return;
                }

                if ( ( instruction & 0b1111000100000000 ) == 0b0101000100000000 ) {
                    // SUBQ_ENCODING
                    final int operandSizeInBytes = 1 << ( (instruction & 0b11000000) >> 6 );
                    switch(operandSizeInBytes) {
                        case 1:
                        case 2:
                            cycles += 4;
                            break;
                        case 4:
                            cycles += 6;
                            break;
                        default:
                            throw new IllegalInstructionException( pcAtStartOfLastInstruction,instruction);
                    }
                    final int eaMode = (instruction & 0b111000) >> 3;
                    final int eaRegister = instruction & 0b111;
                    final boolean dstIsAddressRegister = eaMode == AddressingMode.ADDRESS_REGISTER_DIRECT.eaModeField;

                    int srcValue = (instruction& 0b111000000000) >> 9;
                    if ( srcValue == 0 ) {
                        srcValue = 8;
                    }
                    decodeSourceOperand(instruction,operandSizeInBytes,false,false);
                    final int dstValue = value;

                    value -= srcValue;

                    storeValue(eaMode, eaRegister, operandSizeInBytes);

                    if ( ! dstIsAddressRegister )
                    {
                        updateFlags(srcValue, dstValue, value, operandSizeInBytes, CCOperation.SUBTRACTION, CPU.ALL_USERMODE_FLAGS);
                    }
                    return;
                }
                break;
            /* ================================
             * Bcc/BSR/BRA
             * ================================
             */
            case 0b0110_0000_0000_0000:

                // BRA/Bcc/BSR
                final int cc = (instruction & 0b0000111100000000) >> 8;
                if ( cc == Condition.BSR.bits )
                {
                    switch (instruction & 0xff)
                    {
                        case 0x00: // 16 bit offset
                            pushLong( pc+2 );
                            pc += memLoadWord(pc)-2; // -2 because we already advanced the PC after reading the instruction word
                            cycles = 18;
                            return;
                        case 0xff: // 32 bit offset
                            if ( cpuType.isNotCompatibleWith( CPUType.M68020 ) )
                            {
                                break;
                            }
                            pushLong( pc+4 );
                            pc += memLoadLong( pc )-2; // -2because we already advanced the PC after reading the instruction word
                            cycles = 24; // TODO: Wrong timing, find out 68020+ timings...
                            return;
                        default:
                            // 8-bit branch offset encoded in instruction itself
                            pushLong( pc );
                            final int offset = ((instruction & 0xff) << 24) >> 24;
                            pc += offset - 2; // -2 because we already advanced the PC after reading the instruction word
                            cycles = 18;
                            return;
                    }
                    throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
                }
                final boolean takeBranch = Condition.isTrue(this, cc);
                switch (instruction & 0xff)
                {
                    case 0x00: // 16 bit offset
                        if (takeBranch)
                        {
                            pc += memLoadWord(pc) - 2; // -2 because we already advanced the PC after reading the instruction word
                            cycles = 10;
                        } else {
                            cycles = 12;
                        }
                        pc += 2; // skip offset
                        break;
                    case 0xff: // 32 bit offset (NOT an M68000 addressing mode...)
                        if (takeBranch)
                        {
                            pc += memLoadLong(pc) - 2; // -2because we already advanced the PC after reading the instruction word
                            cycles = 12; // TODO: Wrong timing, find out 68020+ timings...
                        } else {
                            cycles = 10; // TODO: Wrong timing, find out 68020+ timings...
                        }
                        pc += 4;
                        break;
                    default:
                        // 8-bit branch offset encoded in instruction itself
                        if (takeBranch)
                        {
                            final int offset = ((instruction & 0xff) << 24) >> 24;
                            pc += offset - 2; // -2 because we already advanced the PC after reading the instruction word
                            cycles = 10;
                        } else {
                            cycles = 8;
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
                updateFlagsAfterMove(1);
                cycles = 4;
                return;
            /* ================================
             * OR/DIV/SBCD
             * ================================
             */
            case 0b1000_0000_0000_0000:

                if ( ( instruction & 0b1111000111000000 ) == 0b1000000011000000 )
                {
                    // DIVU_ENCODING
                    final int dataReg = (instruction&0b111000000000)>>9;
                    decodeSourceOperand(instruction,4,false);
                    long a = dataRegisters[dataReg];
                    a &= 0xffffffff;
                    long b = value;
                    b &= 0xffff;
                    if ( b == 0 )
                    {
                        triggerIRQ(IRQ.INTEGER_DIVIDE_BY_ZERO,0);
                        return;
                    }
                    long result = a/b;
                    long remainder = a - (result*b);
                    dataRegisters[dataReg] = (int) ( ((remainder & 0xffff)<<16) | (result & 0xffff) );
                    final int clearMask = ~(CPU.FLAG_NEGATIVE|CPU.FLAG_ZERO|CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW);
                    int setMask = 0;
                    if ( result == 0 ) {
                        setMask |= CPU.FLAG_ZERO;
                    }
                    if ( result < 0 || result > 0xffffffffL ) {
                        setMask |= CPU.FLAG_OVERFLOW;
                    }
                    if ( (result & 1<<15)!=0) {
                        setMask |= CPU.FLAG_NEGATIVE;
                    }
                    cycles += 140;
                    statusRegister = (statusRegister & clearMask) | setMask;
                    return;
                }

                if ( ( instruction & 0b1111000111000000 ) == 0b1000000111000000 ) {
                    // DIVS_ENCODING
                    final int dataReg = (instruction&0b111000000000)>>9;
                    decodeSourceOperand(instruction,4,false);
                    long a = dataRegisters[dataReg];
                    long b = value;
                    if ( b == 0 )
                    {
                        triggerIRQ(IRQ.INTEGER_DIVIDE_BY_ZERO,0);
                        return;
                    }
                    long result = a/b;
                    long remainder = a - (result*b);
                    dataRegisters[dataReg] = (int) ( ((remainder & 0xffff)<<16) | (result & 0xffff) );
                    final int clearMask = ~(CPU.FLAG_NEGATIVE|CPU.FLAG_ZERO|CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW);
                    int setMask = 0;
                    if ( result == 0 ) {
                        setMask |= CPU.FLAG_ZERO;
                    }
                    if ( (result & 1<<15)!=0) {
                        setMask |= CPU.FLAG_NEGATIVE;
                    }
                    final long masked = result & 0xffffffff_00000000L;
                    if ( masked != 0 && masked != 0xffffffff_00000000L) {
                        setMask |= CPU.FLAG_OVERFLOW;
                    }
                    cycles += 170;
                    statusRegister = (statusRegister & clearMask) | setMask;
                    return;
                }



                if ( ( instruction & 0b1111000100000000 ) == 0b1000000100000000 ) {
                    // OR Dn,<ea>
                    binaryLogicalOp(instruction,BinaryLogicalOp.OR,BinaryLogicalOpMode.REGULAR);
                    return;
                }

                if ( ( instruction& 0b1111000100000000 ) == 0b1000000000000000 ) {
                    // OR <ea>,Dn
                    binaryLogicalOp(instruction,BinaryLogicalOp.OR,BinaryLogicalOpMode.REGULAR);
                    return;
                }
                break;
            /* ================================
             * SUB/SUBX
             * ================================
             */
            case 0b1001_0000_0000_0000:

                if ( ( instruction & 0b1111000111000000 ) == 0b1001000111000000 ) {
                    // SUBA.L <ea>,An
                    decodeSourceOperand(instruction,4,false);
                    final int dstReg = (instruction & 0b111000000000) >> 9;
                    addressRegisters[dstReg] -= value;
                    cycles += 8;
                    return;
                }

                if ( ( instruction & 0b1111000111000000 ) == 0b1001000011000000 ) {
                    // SUBA.W <ea>,An
                    decodeSourceOperand(instruction,2,false);
                    final int dstReg = (instruction & 0b111000000000) >> 9;
                    addressRegisters[dstReg] -= value;
                    cycles += 8;
                    return;
                }

                if ((instruction & 0b1111000100111000) == 0b1001000100001000 ||
                    (instruction & 0b1111000100111000) == 0b1001000100000000)
                {
                    // SUBX
                    final int sizeBits = (instruction & 0b11000000) >> 6;
                    final int operandSizeInBytes = 1<<sizeBits;
                    final int srcReg = instruction & 0b111;
                    final int dstReg = (instruction & 0b111000000000) >> 9;
                    final boolean isDataRegister = (instruction & 1<<3) == 0;

                    // load
                    int srcValue;
                    int dstValue;
                    switch(operandSizeInBytes)
                    {
                        case 1:
                            if ( isDataRegister ) {
                                srcValue = (dataRegisters[ srcReg ] << 24) >> 24;
                                dstValue = (dataRegisters[ dstReg ] << 24) >> 24;
                            } else {
                                addressRegisters[srcReg] -= 1;
                                addressRegisters[dstReg] -= 1;
                                srcValue = memory.readByte(addressRegisters[srcReg]);
                                dstValue = memory.readByte(addressRegisters[dstReg]);
                            }
                            break;
                        case 2:
                            if ( isDataRegister ) {
                                srcValue = (dataRegisters[ srcReg ] << 16) >> 16;
                                dstValue = (dataRegisters[ dstReg ] << 16) >> 16;
                            } else {
                                addressRegisters[srcReg] -= 2;
                                addressRegisters[dstReg] -= 2;
                                srcValue = memory.readWord(addressRegisters[srcReg]);
                                dstValue = memory.readWord(addressRegisters[dstReg]);
                            }
                            break;
                        case 4:
                            if ( isDataRegister ) {
                                srcValue = dataRegisters[ srcReg ];
                                dstValue = dataRegisters[ dstReg ];
                            } else {
                                addressRegisters[srcReg] -= 4;
                                addressRegisters[dstReg] -= 4;
                                srcValue = memory.readLong(addressRegisters[srcReg]);
                                dstValue = memory.readLong(addressRegisters[dstReg]);
                            }
                            break;
                        default:
                            throw new IllegalInstructionException( pcAtStartOfLastInstruction,instruction );
                    }
                    final int carry = isExtended() ? 1 : 0;
                    final int result = dstValue - srcValue - carry;
                    // store
                    switch(operandSizeInBytes)
                    {
                        case 1:
                            if ( isDataRegister ) {
                                dataRegisters[dstReg] = (dataRegisters[dstReg] & 0xffffff00) | (result & 0xff);
                            } else {
                                memory.writeByte(addressRegisters[dstReg],result);
                            }
                            break;
                        case 2:
                            if ( isDataRegister ) {
                                dataRegisters[dstReg] = (dataRegisters[dstReg] & 0xffff0000) | (result & 0xffff);
                            } else {
                                memory.writeWord(addressRegisters[dstReg],result);
                            }
                            break;
                        case 4:
                            if ( isDataRegister ) {
                                dataRegisters[dstReg] = result;
                            } else {
                                memory.writeLong(addressRegisters[dstReg],result);
                            }
                            break;
                        default:
                            throw new IllegalInstructionException( pcAtStartOfLastInstruction,instruction );
                    }
                    /*
X — Set to the value of the carry bit.
N — Set if the result is negative; cleared otherwise.
Z — Cleared if the result is nonzero; unchanged otherwise.
V — Set if an overflow occurs; cleared otherwise.
C — Set if a borrow occurs; cleared otherwise.
                     */
                    updateFlags(srcValue,dstValue,result,operandSizeInBytes,CCOperation.SUBTRACTION,FLAG_EXTENDED|FLAG_NEGATIVE|FLAG_OVERFLOW|FLAG_CARRY);
                    if ( result != 0 ) {
                        statusRegister &= ~FLAG_ZERO;
                    }
                    return;
                }

                if ( (instruction & 0b1111000100000000) == 0b1001000000000000 ||
                     (instruction & 0b1111000100000000) == 0b1001000100000000)
                {
                    sub(instruction);
                    return;
                }
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

                if ( ( instruction & 0b1111000100111000 ) == 0b1011000100001000 ) {
                    // CMPM
                    final int srcRegNum = (instruction & 0b000000000111);
                    final int dstRegNum = (instruction & 0b111000000000)>>9;
                    final int sizeBits = (instruction&0b11000000)>>6;
                    final int sizeInBytes = 1<<sizeBits;
                    final int srcValue;
                    final int dstValue;
                    switch( sizeInBytes ) {
                        case 1:
                            srcValue = memory.readByte(addressRegisters[srcRegNum]);
                            dstValue = memory.readByte(addressRegisters[dstRegNum]);
                            break;
                        case 2:
                            srcValue = memory.readWord(addressRegisters[srcRegNum]);
                            dstValue = memory.readWord(addressRegisters[dstRegNum]);
                            break;
                        case 4:
                            srcValue = memory.readWord(addressRegisters[srcRegNum]);
                            dstValue = memory.readWord(addressRegisters[dstRegNum]);
                            break;
                        default:
                            throw new IllegalInstructionException( pcAtStartOfLastInstruction,instruction );
                    }
                    addressRegisters[srcRegNum] += sizeInBytes;
                    addressRegisters[dstRegNum] += sizeInBytes;
                    int result = dstValue - srcValue;
                    updateFlags( srcValue, dstValue,result,sizeInBytes,CCOperation.SUBTRACTION,CPU.USERMODE_FLAGS_NO_X );
                    return;
                }

                if ( (instruction & 0b1111000111000000) == 0b1011000111000000 ||
                     (instruction & 0b1111000111000000) == 0b1011000011000000)
                {
                    // CMPA_WORD_ENCODING / CMPA_LONG_ENCODING
                    final int dstReg = (instruction & 0b111000000000) >> 9;
                    final boolean isWordOp = (instruction & 0b100000000) == 0;
                    final int sizeInBytes = isWordOp ? 2 : 4;
                    decodeSourceOperand(instruction,sizeInBytes,false);
                    final int src = value;
                    final int dst = addressRegisters[dstReg];
                    final int result = dst - src;
                    updateFlags(src,dst,result,4,CCOperation.SUBTRACTION,USERMODE_FLAGS_NO_X);
                    cycles += 6;
                    return;
                }

                if ( ( instruction & 0b1111000100000000 ) == 0b1011000000000000 )
                {
                    // CMP
                    final int dstRegNum = (instruction & 0b111000000000)>>9;
                    final int sizeBits = (instruction&0b11000000)>>6;
                    final int sizeInBytes = 1<<sizeBits;
                    decodeSourceOperand(instruction,sizeInBytes,false);
                    final int srcValue = value;
                    final int dstValue;
                    switch(sizeInBytes) {
                        case 1:
                            dstValue = (dataRegisters[dstRegNum] << 24 )>>24;
                            break;
                        case 2:
                            dstValue = (dataRegisters[dstRegNum] << 16 )>>16;
                            break;
                        case 4:
                            dstValue = dataRegisters[dstRegNum];
                            break;
                        default:
                            throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
                    }
                    final int result = dstValue - srcValue;
                    updateFlags( srcValue, dstValue,result,sizeInBytes,CCOperation.SUBTRACTION,CPU.USERMODE_FLAGS_NO_X );
                    return;
                }

                if ( ( instruction & 0b1111000100000000 ) == 0b1011000100000000 ) {
                    // EOR_DST_EA_ENCODING
                    final int dataRegNum = (instruction&0b111000000000) >> 9;
                    final int sizeInBytes= 1 << ( (instruction&0b11000000) >> 6);
                    decodeSourceOperand(instruction, sizeInBytes,false,false);
                    value = dataRegisters[dataRegNum] ^ value;
                    storeValue((instruction&0b111000)>>3, instruction&0b111, sizeInBytes);
                    updateFlagsAfterMove(sizeInBytes);
                    return;
                }
                break;
            /* ================================
             * AND/MUL/ABCD/EXG
             * ================================
             */
            case 0b1100_0000_0000_0000:

                if ( ( instruction & 0b1111000111000000 ) == 0b1100000111000000 ) {
                    // MULS_ENCODING
                    final int dataReg = (instruction & 0b111_000000000) >> 9;
                    decodeSourceOperand(instruction,2,false);

                    int pattern = value & 0xffff;
                    pattern <<= 1;

                    int grpCount = 0;
                    grpCount += (((pattern & 0b110000000000000000) == 0b100000000000000000) ||
                            ((pattern & 0b110000000000000000) == 0b010000000000000000)) ? 1 : 0;
                    grpCount += (((pattern & 0b001100000000000000) == 0b001000000000000000) ||
                            ((pattern & 0b001100000000000000) == 0b000100000000000000)) ? 1 : 0;
                    grpCount += (((pattern & 0b000011000000000000) == 0b000010000000000000) ||
                            ((pattern & 0b000011000000000000) == 0b000001000000000000)) ? 1 : 0;
                    grpCount += (((pattern & 0b000000110000000000) == 0b000000100000000000) ||
                            ((pattern & 0b000000110000000000) == 0b000000010000000000)) ? 1 : 0;
                    grpCount += (((pattern & 0b000000001100000000) == 0b000000001000000000) ||
                            ((pattern & 0b000000001100000000) == 0b000000000100000000)) ? 1 : 0;
                    grpCount += (((pattern & 0b000000000011000000) == 0b000000000010000000) ||
                            ((pattern & 0b000000000011000000) == 0b000000000001000000)) ? 1 : 0;
                    grpCount += (((pattern & 0b000000000000110000) == 0b000000000000100000) ||
                            ((pattern & 0b000000000000110000) == 0b000000000000010000)) ? 1 : 0;
                    grpCount += (((pattern & 0b000000000000110000) == 0b000000000000100000) ||
                            ((pattern & 0b000000000000110000) == 0b000000000000010000)) ? 1 : 0;
                    grpCount += (((pattern & 0b000000000000001100) == 0b000000000000001000) ||
                            ((pattern & 0b000000000000001100) == 0b000000000000000100)) ? 1 : 0;
                    grpCount += (((pattern & 0b000000000000000011) == 0b000000000000000010) ||
                            ((pattern & 0b000000000000000011) == 0b000000000000000001)) ? 1 : 0;

                    value *= ((dataRegisters[dataReg] << 16 ) >> 16); // sign-extend data register
                    dataRegisters[dataReg] = value;
                    int setMask = 0;
                    if ( value == 0 ) {
                        setMask |= FLAG_ZERO;
                    } else if ( (value & 1<<31) != 0 ) {
                        setMask |= FLAG_NEGATIVE;
                    }
                    final int clearMask = ~(FLAG_OVERFLOW | FLAG_NEGATIVE | FLAG_ZERO | FLAG_CARRY);
                    statusRegister = (statusRegister & clearMask) | setMask;
                    cycles+=(38+2*grpCount);
                    return;
                }

                if ( ( instruction & 0b1111000111000000 ) == 0b1100000011000000 )
                {
                    // MULU_ENCODING
                    final int dataReg = (instruction & 0b111_000000000) >> 9;
                    decodeSourceOperand(instruction,2,false);
                    value &= 0xffff; // decodeSourceOperand() always does sign extension but we're doing unsigned multiplication here...
                    final int oneBitCnt = Integer.bitCount(value);
                    value *= (dataRegisters[dataReg] & 0xffff);
                    dataRegisters[dataReg] = value;
                    int setMask = 0;
                    if ( value == 0 ) {
                        setMask |= FLAG_ZERO;
                    } else if ( (value & 1<<31) != 0 ) {
                        setMask |= FLAG_NEGATIVE;
                    }
                    final int clearMask = ~(FLAG_OVERFLOW | FLAG_NEGATIVE | FLAG_ZERO | FLAG_CARRY);
                    statusRegister = (statusRegister & clearMask) | setMask;
                    cycles+=(38+2*oneBitCnt);
                    return;
                }

                // EXG and AND look almost the same so just applying the instruction encoding's AND
                // mask is not enough
                int masked = instruction & 0b1111000111111000;
                if ( masked ==  0b1100000101000000 || masked == 0b1100000101001000 || masked == 0b1100000110001000 ) // EXG
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
                            cycles = 6;
                            return;
                        case 0b01001000: // swap Address registers
                            tmp = addressRegisters[ addressReg ];
                            addressRegisters[ addressReg ] = addressRegisters[ dataReg ];
                            addressRegisters[ dataReg ] = tmp;
                            cycles = 6;
                            return;
                        case 0b10001000: // swap Data register and address register
                            tmp = addressRegisters[ addressReg ];
                            addressRegisters[ addressReg ] = dataRegisters[ dataReg ];
                            dataRegisters[ dataReg ] = tmp;
                            cycles = 6;
                            return;
                    }
                    triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
                    return;
                }

                masked = (instruction & 0b11110001_00000000);
                if ( masked == 0b1100000000000000 || masked == 0b1100000100000000) {
                    // AND <ea>,Dn
                    // AND Dn,<ea>
                    binaryLogicalOp(instruction,BinaryLogicalOp.AND,BinaryLogicalOpMode.REGULAR);
                    return;
                }
                break;
            /* ================================
             * ADD/ADDX
             * ================================
             */
            case 0b1101_0000_0000_0000:

                if ( ( instruction & 0b1111000100111000 ) == 0b1101000100001000 ) {
                    // ADDX -(Ax),-(Ay)
                    final int sizeBits = (instruction & 0b11000000) >> 6;
                    int srcReg = instruction&0b111;
                    int dstReg = (instruction&0b111000000000)>>9;
                    final int srcValue;
                    final int dstValue;
                    switch( sizeBits )
                    {
                        case 0b00:
                            addressRegisters[srcReg] -= 1;
                            srcValue = memory.readByte( addressRegisters[srcReg] );
                            addressRegisters[dstReg] -= 1;
                            dstValue = memory.readByte( addressRegisters[dstReg] );
                            cycles += 18;
                            break;
                        case 0b01:
                            addressRegisters[srcReg] -= 2;
                            srcValue = memory.readWord( addressRegisters[srcReg] );
                            addressRegisters[dstReg] -= 2;
                            dstValue = memory.readWord( addressRegisters[dstReg] );
                            cycles += 18;
                            break;
                        case 0b10:
                            addressRegisters[srcReg] -= 4;
                            srcValue = memory.readLong( addressRegisters[srcReg] );
                            addressRegisters[dstReg] -= 4;
                            dstValue = memory.readLong( addressRegisters[dstReg] );
                            cycles += 30;
                            break;
                        default:
                            throw new IllegalInstructionException( pcAtStartOfLastInstruction,instruction);
                    }
                    int result = srcValue + dstValue + (isExtended() ? 1 : 0);

                    updateFlags(srcValue,dstValue,result,1<<sizeBits,CCOperation.ADDITION,CPU.ALL_USERMODE_FLAGS);

                    switch( sizeBits )
                    {
                        case 0b00:
                            memory.writeByte( addressRegisters[dstReg], result );
                            break;
                        case 0b01:
                            memory.writeWord( addressRegisters[dstReg], result );
                            break;
                        case 0b10:
                            memory.writeLong( addressRegisters[dstReg], result );
                            break;
                        default:
                            throw new IllegalInstructionException( pcAtStartOfLastInstruction,instruction);
                    }
                    return;
                }

                if ( ( instruction & 0b1111000100111000 ) == 0b1101000100000000 ) {
                    // ADDX Dx,Dy
                    final int sizeBits = (instruction & 0b11000000) >> 6;
                    int srcReg = instruction&0b111;
                    int dstReg = (instruction&0b111000000000)>>9;
                    final int srcValue;
                    final int dstValue;
                    switch( sizeBits )
                    {
                        case 0b00:
                            srcValue = (dataRegisters[srcReg]<<24)>>24;
                            dstValue = (dataRegisters[dstReg]<<24)>>24;
                            cycles += 4;
                            break;
                        case 0b01:
                            srcValue = (dataRegisters[srcReg]<<16)>>16;
                            dstValue = (dataRegisters[dstReg]<<16)>>16;
                            cycles += 4;
                            break;
                        case 0b10:
                            srcValue = dataRegisters[srcReg];
                            dstValue = dataRegisters[dstReg];
                            cycles += 8;
                            break;
                        default:
                            throw new IllegalInstructionException( pcAtStartOfLastInstruction,instruction);
                    }
                    int result = srcValue + dstValue + (isExtended() ? 1 : 0);

                    updateFlags(srcValue,dstValue,result,1<<sizeBits,CCOperation.ADDITION,CPU.ALL_USERMODE_FLAGS);

                    switch( sizeBits )
                    {
                        case 0b00:
                            dataRegisters[dstReg] = (dataRegisters[dstReg] & ~0xff) | (result & 0xff);
                            break;
                        case 0b01:
                            dataRegisters[dstReg] = (dataRegisters[dstReg] & ~0xffff) | (result & 0xffff);
                            break;
                        case 0b10:
                            dataRegisters[dstReg] = result;
                            break;
                    }
                    return;
                }

                if ( ( instruction & 0b1111000111000000 ) == 0b1101000111000000 ) {
                    // ADDA_LONG_ENCODING
                    decodeSourceOperand(instruction,4,false);
                    final int dstReg = (instruction & 0b111000000000) >> 9;
                    addressRegisters[dstReg] += value;
                    cycles += 8;
                    return;
                }

                if ( ( instruction & 0b1111000111000000 ) == 0b1101000011000000 )
                {
                    // ADDA_WORD_ENCODING
                    decodeSourceOperand(instruction,2,false);
                    final int dstReg = (instruction & 0b111000000000) >> 9;
                    addressRegisters[dstReg] += value;
                    cycles += 8;
                    return;
                }

                if ( ( ( instruction & 0b1111000100000000 ) == 0b1101000000000000 ) ||
                    ( instruction & 0b1111000100000000 ) == 0b1101000100000000 )
                {
                    // ADD <ea>,Dx
                    // ADD Dx,<ea>
                    int sizeBits = (instruction & 0b11000000) >> 6;
                    int regNum = (instruction&0b111000000000)>>9;

                    final int srcValue;
                    final int dstValue;
                    final boolean dstIsEa = (instruction & 1<<8) != 0;
                    if (dstIsEa)
                    {
                        // Dn + <ea> -> <ea>
                        switch(sizeBits)
                        {
                            case 0b00:
                                srcValue = (dataRegisters[regNum]<<24)>>24;
                                cycles += 8;
                                break;
                            case 0b01:
                                srcValue = (dataRegisters[regNum]<<16)>>16;
                                cycles += 8;
                                break;
                            case 0b10:
                                srcValue = dataRegisters[regNum];
                                cycles += 12;
                                break;
                            default:
                                throw new RuntimeException("Unreachable code reached");
                        }
                        decodeSourceOperand(instruction,1<<sizeBits,false,false);
                        dstValue = value;
                    }
                    else
                    {
                        // <ea> + Dn -> Dn
                        decodeSourceOperand(instruction,1<<sizeBits,false);
                        srcValue = value;
                        switch(sizeBits)
                        {
                            case 0b00:
                                dstValue = (dataRegisters[regNum]<<24)>>24;
                                cycles += 4;
                                break;
                            case 0b01:
                                dstValue = (dataRegisters[regNum]<<16)>>16;
                                cycles += 4;
                                break;
                            case 0b10:
                                dstValue = dataRegisters[regNum];
                                cycles += 6;
                                break;
                            default:
                                throw new RuntimeException("Unreachable code reached");
                        }
                    }
                    final int result = srcValue + dstValue;
                    value = result;

                    updateFlags(srcValue,dstValue,result,1<<sizeBits,CCOperation.ADDITION,CPU.ALL_USERMODE_FLAGS);

                    if ( dstIsEa ) {
                        storeValue((instruction&0b111000)>>3,instruction&0b111,1<<sizeBits);
                    } else {
                        dataRegisters[regNum] = mergeValue(dataRegisters[regNum], result,1<<sizeBits);
                    }
                    return;
                }

                break;
            /* ================================
             * Shift/Rotate/Bit Field
             * ================================
             */
            case 0b1110_0000_0000_0000:

                /* ---------
                 * ROL/ ROR / LSL / LSR / ASL / ASR
                 * ---------
                 */
                if ( ( instruction & 0b1111000000111000 ) == 0b1110000000010000 )
                {
                    // ROXL/ROXR IMMEDIATE
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateImmediate(instruction,RotateMode.ROTATE_WITH_EXTEND,rotateLeft);
                    return;
                }

                if ( ( instruction & 0b1111000000111000 ) == 0b1110000000000000 )
                {
                    // ASL/ASR IMMEDIATE
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateImmediate(instruction,RotateMode.ARITHMETIC_SHIFT,rotateLeft);
                    return;
                }
                if ( ( instruction & 0b1111000000111000) ==  0b1110000000001000 ) // LSL/LSR
                {
                    // LSL/LSR IMMEDIATE
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateImmediate(instruction,RotateMode.LOGICAL_SHIFT,rotateLeft);
                    return;
                }
                if ( ( instruction & 0b1111000000111000 ) == 0b1110000000011000 )
                {
                    // ROL/ROR IMMEDIATE
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateImmediate(instruction,RotateMode.ROTATE,rotateLeft);
                    return;
                }

                if ( ( instruction & 0b1111111011000000 ) == 0b1110010011000000 )
                {
                    // ROXL/ROXR MEMORY
                    int sizeBits = (instruction & 0b11000000) >> 6;
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    int eaMode     = (instruction & 0b111000) >> 3;
                    int eaRegister = (instruction & 0b000111);
                    calculateEffectiveAddress(1<<sizeBits, eaMode, eaRegister,true);
                    int value = memLoadWord( ea );
                    value = rotate( value,2,RotateMode.ROTATE_WITH_EXTEND,rotateLeft,1 );
                    memory.writeWord(ea,value);
                    return;
                }

                if ( ( instruction & 0b1111111011000000 ) == 0b1110000011000000 ) {
                    // ASL/ASR MEMORY
                    int sizeBits = (instruction & 0b11000000) >> 6;
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    int eaMode     = (instruction & 0b111000) >> 3;
                    int eaRegister = (instruction & 0b000111);
                    calculateEffectiveAddress(1<<sizeBits, eaMode, eaRegister,true);
                    int value = memLoadWord( ea );
                    value = rotate( value,2,RotateMode.ARITHMETIC_SHIFT,rotateLeft,1 );
                    memory.writeWord(ea,value);
                    return;
                }

                if ( ( instruction & 0b1111111011000000) == 0b1110001011000000 ) { // LSL/LSR
                    // LSL/LSR MEMORY
                    int sizeBits = (instruction & 0b11000000) >> 6;
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    int eaMode     = (instruction & 0b111000) >> 3;
                    int eaRegister = (instruction & 0b000111);
                    calculateEffectiveAddress(1<<sizeBits, eaMode, eaRegister,true);
                    int value = memLoadWord( ea );
                    value = rotate( value,2,RotateMode.LOGICAL_SHIFT,rotateLeft,1 );
                    memory.writeWord(ea,value);
                    return;
                }
                if ( ( instruction & 0b1111111001000000) == 0b1110011001000000 ) {
                    // ROL/ROR MEMORY
                    int sizeBits = (instruction & 0b11000000) >> 6;
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    int eaMode     = (instruction & 0b111000) >> 3;
                    int eaRegister = (instruction & 0b000111);
                    calculateEffectiveAddress(1<<sizeBits, eaMode, eaRegister,true);
                    int value = memLoadWord( ea );
                    value = rotate( value,2,RotateMode.ROTATE,rotateLeft,1 );
                    memory.writeWord(ea,value);
                    return;
                }

                if ( ( instruction & 0b1111000000111000 ) == 0b1110000000110000 )
                {
                    // ROXL/ROXR REGISTER
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateRegister(instruction,RotateMode.ROTATE_WITH_EXTEND,rotateLeft);
                    return;
                }

                if ( ( instruction & 0b1111000000111000 ) == 0b1110000000100000 )
                {
                    // ASL/ASR REGISTER
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateRegister(instruction,RotateMode.ARITHMETIC_SHIFT,rotateLeft);
                    return;
                }
                if ( ( instruction & 0b1111000000111000) == 0b1110000000101000) { // LSL/LSR
                    // LSL/LSR REGISTER
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateRegister(instruction,RotateMode.LOGICAL_SHIFT,rotateLeft);
                    return;
                }

                if ( ( instruction & 0b1111000000111000) == 0b1110000000111000)
                {
                    // ROL/ROR REGISTER
                    final boolean rotateLeft = (instruction & 1<<8) != 0 ? true:false;
                    rotateRegister(instruction,RotateMode.ROTATE,rotateLeft);
                    return;
                }
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
        throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
    }

    private void illegalInstruction() {
        triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
    }

    private void rotateRegister(int instruction,RotateMode mode,boolean rotateLeft)
    {
        int sizeBits = (instruction & 0b11000000) >> 6;
        int srcRegNum = (instruction & 0b0000111000000000 ) >> 9;
        int dstRegNum = (instruction & 0b111);

        int cnt = dataRegisters[ srcRegNum ];
        int value = rotate( dataRegisters[ dstRegNum ],
                            1<<sizeBits,mode,rotateLeft,cnt);
        dataRegisters[ dstRegNum ] = mergeValue(dataRegisters[ dstRegNum ],value,1<<sizeBits);
    }

    private void rotateImmediate(int instruction,RotateMode mode,boolean rotateLeft)
    {
        int sizeBits = (instruction & 0b11000000) >> 6;
        final int cnt = (instruction & 0b0000111000000000 ) >> 9;
        final int regNum = (instruction & 0b111);

        int value = rotate( dataRegisters[ regNum ],1<<sizeBits,mode,rotateLeft,cnt);
        dataRegisters[ regNum ] = mergeValue(dataRegisters[ regNum ],value,1<<sizeBits);
    }

    private int mergeValue(int input,int toMerge,int operandSizeInBytes) {

        switch( operandSizeInBytes )
        {
            case 1: return (input & 0xffffff00) | (toMerge & 0xff);
            case 2: return (input & 0xffff0000) | (toMerge & 0xffff);
            case 4: return toMerge;
        }
        throw new IllegalInstructionException(pcAtStartOfLastInstruction, memLoadWord(pcAtStartOfLastInstruction) );
    }

    private void binaryLogicalOp(int instruction, BinaryLogicalOp operation, BinaryLogicalOpMode mode)
    {
        if ( mode == BinaryLogicalOpMode.IMMEDIATE )
        {
            final int sizeBits = (instruction&0b11000000)>>>6;
            final int immediateValue;
            switch(sizeBits)
            {
                case 0b00:
                    immediateValue = memLoadWord(pc) & 0xff;
                    pc += 2;
                    break;
                case 0b01:
                    immediateValue = memLoadWord(pc) & 0xffff;
                    pc += 2;
                    break;
                case 0b10:
                    immediateValue = memLoadLong(pc);
                    pc += 4;
                    break;
                default:
                    throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
            }
            decodeSourceOperand(instruction,1<<sizeBits,false,false);
            value = operation.apply(immediateValue,value);
            storeValue((instruction&0b111000)>>3, instruction&0b111,1<<sizeBits);
            updateFlagsAfterMove(1<<sizeBits);
            return;
        }

        if ( mode == BinaryLogicalOpMode.SR ) {
            // ANDI #xx,SR
            // ORI #xx,SR
            // EORI #xx,SR
            if ( assertSupervisorMode() )
            {
                int value = memLoadWord(pc) & 0xffff;
                pc += 2;
                final int result = operation.apply( value, statusRegister);
                setStatusRegister(result & 0xffff);
                cycles = 20;
            }
            return;
        }

        if ( mode == BinaryLogicalOpMode.CCR ) {
            // ANDI #xx,CCR
            // ORI #xx,CCR
            // EORI #xx,CCR
            int value = memLoadWord(pc) & 0b11111;
            pc += 2;
            final int result = operation.apply( value, statusRegister & ALL_USERMODE_FLAGS);
            statusRegister = (statusRegister & ~ALL_USERMODE_FLAGS) | result;
            cycles = 20;
            return;
        }

        if ( mode == BinaryLogicalOpMode.REGULAR )
        {
            // AND Dn,<ea> / AND Dn,<ea>
            // OR  Dn,<ea> / OR  Dn,<ea>
            // EOR Dn,<ea> / EOR Dn,<ea>
            final int sizeBits = (instruction & 0b11000000) >>> 6;
            final int operandSizeInBytes = 1<<sizeBits;
            final boolean destinationIsDataRegister = (instruction & 0b100000000) == 0;

            final int regNum = (instruction & 0b111000000000) >> 9;
            final int regValue = dataRegisters[regNum];

            // apply
            if ( destinationIsDataRegister )
            {
                // <ea> OP Dn -> DN
                decodeSourceOperand(instruction,operandSizeInBytes,false);

                value = operation.apply(value,regValue);
                dataRegisters[regNum] = mergeValue(regValue,value,operandSizeInBytes);
            } else {
                // Dn OP <ea> -> <ea>
                decodeSourceOperand(instruction,operandSizeInBytes,false,false);
                value = operation.apply(regValue,value);
                storeValue((instruction&0b111000)>>>3,instruction&0b111,operandSizeInBytes);
            }
            updateFlagsAfterMove(operandSizeInBytes);
            return;
        }

        if ( mode == BinaryLogicalOpMode.IMMEDIATE )
        {
            // ANDI #xx,<ea>
            // ORI #xx,<ea>
            // EORI #xx,<ea>
            final int sizeBits = (instruction >>> 6) & 0b11;
            final int operandSize = 1 << sizeBits;
            final int mask;
            switch (operandSize)
            {
                case 1:
                    mask = 0xffffff00 | (memLoadWord(pc) & 0xff);
                    pc += 2;
                    break;
                case 2:
                    mask = 0xffff0000 | (memLoadWord(pc) & 0xffff);
                    pc += 2;
                    break;
                case 4:
                    mask = memLoadLong(pc);
                    pc += 4;
                    break;
                default:
                    throw new IllegalInstructionException(pcAtStartOfLastInstruction, instruction);
            }
            if (decodeSourceOperand(instruction, operandSize, false, false))
            {
                // register operand
                switch (operandSize)
                {
                    case 1:
                    case 2:
                        cycles += 8;
                        break;
                    default:
                        cycles += 16;
                }
            }
            else
            {
                // memory operand
                switch (operandSize)
                {
                    case 1:
                    case 2:
                        cycles += 12;
                        break;
                    default:
                        cycles += 20;
                }
            }
            value = operation.apply(value, mask);
            storeValue((instruction >>> 3) & 0b111, instruction & 0b111, operandSize);
            updateFlagsAfterMove(operandSize);
            return;
        }
        throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
    }

    private int rotate(int value,int operandSizeInBytes,
                       RotateMode mode,
                       boolean rotateLeft,
                       final int rotateCount2)
    {
        int clearMask = FLAG_NEGATIVE|FLAG_ZERO|FLAG_CARRY|FLAG_OVERFLOW; // V flag is always cleared
        int setMask = 0;

        int rotateCount = rotateCount2 % 64;

        int lastBit=0; // do NOT change, value also serves as default (carry clear) when rotate count is 0
        final int msbBitNum = (operandSizeInBytes*8)-1;
        switch( mode )
        {
            case LOGICAL_SHIFT:
                if ( rotateCount > 0 )
                {
                    clearMask |= FLAG_EXTENDED;
                }
                if ( rotateLeft )
                {
                    final int mask = 1 << msbBitNum;
                    for ( ; rotateCount > 0 ; rotateCount-- )
                    {
                        lastBit = (value & mask);
                        value <<= 1;
                    }
                }
                else
                {
                    for ( ; rotateCount > 0 ; rotateCount-- )
                    {
                        lastBit = (value & 1);
                        value >>>= 1;
                    }
                }
                if ( lastBit != 0 ) {
                    setMask |= FLAG_EXTENDED;
                }
                break;
            case ROTATE:
                if ( rotateLeft )
                {
                    final int mask = 1 << msbBitNum;
                    for ( ; rotateCount > 0 ; rotateCount-- )
                    {
                        lastBit = (value & mask) >>> msbBitNum;
                        value = (value << 1 ) | lastBit;
                    }
                }
                else
                {
                    for ( ; rotateCount > 0 ; rotateCount-- )
                    {
                        lastBit = (value & 1) << msbBitNum;
                        value = (value >>> 1) | lastBit;
                    }
                }
                break;
            case ROTATE_WITH_EXTEND:
                /*
X — Set to the value of the last bit rotated out of the operand; unaffected when the
rotate count is zero.
N — Set if the most significant bit of the result is set; cleared otherwise.
Z — Set if the result is zero; cleared otherwise.
V — Always cleared.
C — Set according to the last bit rotated out of the operand; when the rotate count is zero, set to the value of the extend bit.
                 */
                if ( rotateCount == 0 )
                {
                    if ( isExtended() ) {
                        setMask |= FLAG_CARRY;
                    } // else: FLAG_CARRY cleared by default
                }
                else
                {
                    clearMask |= FLAG_EXTENDED;
                    if ( rotateLeft )
                    {
                        final int mask = 1 << msbBitNum;
                        for ( ; rotateCount > 0 ; rotateCount-- )
                        {
                            lastBit = (value & mask) >>> msbBitNum;
                            value = (value << 1 ) | lastBit;
                        }
                    }
                    else
                    {
                        for ( ; rotateCount > 0 ; rotateCount-- )
                        {
                            lastBit = (value & 1) << msbBitNum;
                            value = (value >>> 1) | lastBit;
                        }
                    }
                    if (lastBit != 0)
                    {
                        // C - carry contains last bit rotated out of the operand
                        setMask |= FLAG_CARRY;
                        setMask |= FLAG_EXTENDED;
                    }
                }
                break;
            case ARITHMETIC_SHIFT:
                /*
N — Set if the most significant bit of the result is set; cleared otherwise.
Z — Set if the result is zero; cleared otherwise.
V — Set if the most significant bit is changed at any time during the shift operation; cleared otherwise.

X — Set according to the last bit shifted out of the operand; unaffected for a shift count of zero.
C — Set according to the last bit shifted out of the operand; cleared for a shift count of zero.
                 */
                clearMask |= FLAG_OVERFLOW;
                if ( rotateCount > 0 )
                {
                    clearMask |= FLAG_EXTENDED;
                }
                final int mask = 1 << msbBitNum;
                boolean msbChanged = false;
                if ( rotateLeft )
                {
                    for ( ; rotateCount > 0 ; rotateCount-- )
                    {
                        lastBit = (value & mask);
                        int currentMsb = (value &mask) >>> msbBitNum;
                        value <<= 1;
                        int newMsb = (value &mask) >>> msbBitNum;
                        msbChanged |= (currentMsb != newMsb);
                    }
                    if ( msbChanged ) {
                        setMask |= FLAG_OVERFLOW;
                    }
                }
                else
                {
                    final int msb = value & mask;
                    for ( ; rotateCount > 0 ; rotateCount-- )
                    {
                        lastBit = (value & 1);
                        value = (value >>> 1) | msb;
                    }
                }
                if ( lastBit != 0 ) {
                    setMask |= FLAG_EXTENDED;
                }
                break;
            default:
                throw new RuntimeException("Unhandled switch/case: "+mode);
        }

        switch (operandSizeInBytes)
        {
            case 1:
                value &= value & 0xff;
                cycles += 6+2*rotateCount2;
                break;
            case 2:
                value &= value & 0xffff;
                cycles += 6+2*rotateCount2;
                break;
            case 4:
                cycles += 8+2*rotateCount2;
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }

        if ( (value & 1 << msbBitNum ) != 0 ) { // N — Set if the most significant bit of the result is set; cleared otherwise.
            setMask |= FLAG_NEGATIVE;
        }
        if ( value == 0 ) { // Z — Set if the result is zero; cleared otherwise.
            setMask |= FLAG_ZERO;
        }
        if ( mode != RotateMode.ROTATE_WITH_EXTEND ) // ROXL/ROXR handle the C flag in a special way
        {
            if (lastBit != 0)
            { // C - carry contains last bit rotated out of the operand
                setMask |= FLAG_CARRY;
            }
        }
        statusRegister = (statusRegister & ~clearMask) | setMask;
        return value;
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

    /**
     * Updates condition flags according to the current, <b>sign-extended to 32 bits</b> {@link #value}.
     */
    private void updateFlagsAfterMove(int operandSizeInBytes)
    {
        final int clearMask = ~(FLAG_ZERO|FLAG_NEGATIVE|FLAG_OVERFLOW|FLAG_CARRY);

        int setMask = 0;
        switch( operandSizeInBytes )
        {
            case 1:
                if ( ( value & 0xff) == 0 ) {
                    setMask = FLAG_ZERO;
                }
                else if ( (value & 1<<7) != 0 ) {
                    setMask = FLAG_NEGATIVE;
                }
                break;
            case 2:
                if ( ( value & 0xffff) == 0 ) {
                    setMask = FLAG_ZERO;
                }
                else if ( (value & 1<<15) != 0 ) {
                    setMask = FLAG_NEGATIVE;
                }
                break;
            case 4:
                if ( value == 0 ) {
                    setMask = FLAG_ZERO;
                }
                else if ( value < 0 ) {
                    setMask = FLAG_NEGATIVE;
                }
                break;
        }

        this.statusRegister = (this.statusRegister & clearMask ) | setMask;
    }

    /**
     * Decodes an 16-bit instruction word's source operand.
     *
     * @param instruction
     * @param operandSizeInBytes
     * @param calculateAddressOnly whether to only calculate the effective address but not actually load
     *                             the value from there
     * @return true if the operand is a register, otherwise false
     */
    private boolean decodeSourceOperand(int instruction, int operandSizeInBytes,boolean calculateAddressOnly) {
        return decodeSourceOperand(instruction,operandSizeInBytes,calculateAddressOnly,true);
    }

    /**
     * Decodes an 16-bit instruction word's source operand.
     *
     * @param instruction
     * @param operandSizeInBytes
     * @param calculateAddressOnly whether to only calculate the effective address but not actually load
     *                             the value from there
     * @param advancePC whether to advance the PC after reading additional instruction words
     * @return true if the operand is a register, otherwise false
     */
    private boolean decodeSourceOperand(int instruction, int operandSizeInBytes,boolean calculateAddressOnly,boolean advancePC)
    {
        // InstructionEncoding.of("ooooDDDMMMmmmsss");
        int eaMode     = (instruction & 0b111000) >> 3;
        int eaRegister = (instruction & 0b000111);

        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                int tmp;
                switch(operandSizeInBytes) {
                    case 1:
                        tmp = (dataRegisters[eaRegister]<<24)>>24;
                        break;
                    case 2:
                        tmp = (dataRegisters[eaRegister]<<16)>>16;
                        break;
                    case 4:
                        tmp = dataRegisters[eaRegister];
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }
                ea = value = tmp;
                cycles += 4;
                return true;
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                if ( ! addressRegisterDirectAllowed )
                {
                    throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
                }
                ea = addressRegisters[eaRegister];
                switch(operandSizeInBytes) {
                    case 2:
                        value = (addressRegisters[eaRegister]<<16)>>16;
                        break;
                    case 4:
                        value = addressRegisters[eaRegister];
                        break;
                    default:
                        throw new IllegalInstructionException(pcAtStartOfLastInstruction,instruction);
                }
                cycles += 4;
                return true;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b000:
                        /*
                         * MOVE (xxx).W,... (1 extra word).
                         * ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(000),1 ),
                         */
                        ea = memLoadWord(pc);
                        if ( ! calculateAddressOnly )
                        {
                            value = memLoad( ea, operandSizeInBytes );
                        }
                        if ( advancePC )
                        {
                            pc += 2;
                        }
                        cycles += 8;
                        return false;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        ea = memLoadLong(pc);
                        if ( ! calculateAddressOnly )
                        {
                            value = memLoad( ea, operandSizeInBytes );
                        }
                        if ( advancePC )
                        {
                            pc += 4;
                        }
                        cycles += 12;
                        return false;
                }
                // $$FALL-THROUGH$$
            default:
                calculateEffectiveAddress(operandSizeInBytes, eaMode, eaRegister,advancePC);
                if ( ! calculateAddressOnly )
                {
                    value = memLoad( ea, operandSizeInBytes );
                }
        }
        return false;
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
        int eaMode = (instruction & 0b0001_1100_0000) >> 6;
        int eaRegister = (instruction & 0b1110_0000_0000) >> 9;
        storeValue( eaMode,eaRegister,operandSize );
    }

    /**
     * Stores the current operation's value according to the
     * destination in the given instruction word.
     *
     * @param eaMode
     * @param eaRegister
     * @param operandSizeInBytes
     */
    private void storeValue(int eaMode,int eaRegister,int operandSizeInBytes)
    {
        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                switch( operandSizeInBytes )
                {
                    case 1:
                        dataRegisters[eaRegister] = (dataRegisters[eaRegister] & 0xffffff00) | (value & 0xff);
                        cycles += 2;
                        return;
                    case 2:
                        dataRegisters[eaRegister] = (dataRegisters[eaRegister] & 0xffff0000) | (value & 0xffff);
                        cycles += 2;
                        return;
                    case 4:
                        dataRegisters[eaRegister] = value;
                        cycles += 2;
                        return;
                }
                throw new RuntimeException("Unreachable code reached");
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                if ( operandSizeInBytes != 4 ) {
                    throw new IllegalArgumentException("Unexpected operand size "+operandSizeInBytes+" for address register");
                }
                addressRegisters[eaRegister] = value;
                cycles += 2;
                break;
            case 0b111:

                int address;
                switch(eaRegister)
                {
                    case 0b000:
                        /*
                         * MOVE (xxx).W,... (1 extra word).
                         * ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(000),1 ),
                         */
                        address = memory.readWordNoCheck(pc);
                        pc += 2;
                        cycles += 6;
                        break;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        address = memory.readLongNoCheck(pc);
                        pc += 4;
                        addressRegisters[eaRegister] = value;
                        cycles += 8;
                        break;
                    default:
                        triggerIRQ(IRQ.ILLEGAL_INSTRUCTION,0);
                        return;
                }
                switch(operandSizeInBytes)
                {
                    case 1:
                        memory.writeByte(address,value);
                        return;
                    case 2:
                        memory.writeWord(address,value);
                        return;
                    case 4:
                        memory.writeLong(address,value);
                        return;
                }
                throw new RuntimeException("Unreachable code reached");
            default:
                calculateEffectiveAddress(operandSizeInBytes, eaMode, eaRegister,true);
                switch (operandSizeInBytes)
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
                        final int insn = memLoadWord(pcAtStartOfLastInstruction);
                        throw new IllegalInstructionException(pcAtStartOfLastInstruction,insn);
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
        stopped = false;

        if ( irq == IRQ.RESET )
        {
            cycles = 0;

            // clear interrupt stack
            irqStackPtr = 0;
            activeIrq = null;

            supervisorModeStackPtr = memLoadLong(0 );
            addressRegisters[7] = supervisorModeStackPtr;
            pc = memLoadLong(4 );
            // enter supervisor mode, disable tracing, set interrupt level 7
            statusRegister = FLAG_I2|FLAG_I1|FLAG_I0|FLAG_SUPERVISOR_MODE;
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

            cycles += 50;

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
        } else {
            // TODO: Cycle count is NOT accurate !! Depends on IRQ type...
            cycles += 38;
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

    public boolean isStopped() { return stopped; }
    public boolean isNotStopped() { return ! stopped; }

    public void setStatusRegister(int newValue)
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

    private void moveMultipleRegisters(int instructionWord,boolean regsToMemory) {

        final int operandSizeInBytes = (instructionWord & 1<<6) == 0 ? 2 : 4;
        final int eaMode = (instructionWord & 0b111000)>>>3;
        final int eaRegister = (instructionWord & 0b111);

        int bitMask = memLoadWord(pc);
        pc += 2;

        final int oldCycles = cycles; // backup cycle count because calculateEffectiveAddress() will update it
        calculateEffectiveAddress(operandSizeInBytes,eaMode,eaRegister,false);
        cycles = oldCycles; // restore cycle count

        int address = ea;
        if ( regsToMemory )
        {
            // registers -> memory
            final boolean isPreDecrement = eaMode == 0b100;
            if ( isPreDecrement )
            {
                // note: register bitmask is reversed when -(An) is being used

                // increment address by operandSize as
                // calculateEffectiveAddress() already set EA to (actual-operandSize)
                // but we do this ourselves inside the loop
                address += operandSizeInBytes;

                // process address registers with predecrement
                for ( int bit = 0 , mask = 1 ; bit < 8 ; bit++,mask<<=1)
                {
                    if ( (bitMask & mask) != 0 )
                    {
                        address -= operandSizeInBytes;
                        memStore(address,addressRegisters[7-bit],operandSizeInBytes);
                    }
                }

                // process data registers
                for ( int bit = 0 , mask = 1<<8 ; bit < 8 ; bit++,mask<<=1)
                {
                    if ( (bitMask & mask) != 0 )
                    {
                        address -= operandSizeInBytes;
                        memStore(address,dataRegisters[7-bit],operandSizeInBytes);
                    }
                }
                addressRegisters[ eaRegister ] = address;
            }
            else
            {
                // process data registers (no predecrement)
                for ( int bit = 0 , mask = 1 ; bit < 8 ; bit++,mask<<=1)
                {
                    if ( (bitMask & mask) != 0 )
                    {
                        memStore(address,dataRegisters[bit],operandSizeInBytes);
                        address += operandSizeInBytes;
                    }
                }

                // process address registers
                for ( int bit = 0 , mask = 1<<8 ; bit < 8 ; bit++,mask<<=1)
                {
                    if ( (bitMask & mask) != 0 )
                    {
                        memStore(address,addressRegisters[bit],operandSizeInBytes);
                        address += operandSizeInBytes;
                    }
                }
            }
        }
        else
        {
            // memory -> registers

            // process data registers
            for ( int bit = 0 , mask = 1 ; bit < 8 ; bit++,mask<<=1)
            {
                if ( (bitMask & mask) != 0 )
                {
                    dataRegisters[bit] = memLoad(address,operandSizeInBytes);
                    address += operandSizeInBytes;
                }
            }

            // process address registers
            for ( int bit = 0 , mask = 1<<8 ; bit < 8 ; bit++,mask<<=1)
            {
                if ( (bitMask & mask) != 0 )
                {
                    addressRegisters[bit] = memLoad(address,operandSizeInBytes);
                    address += operandSizeInBytes;
                }
            }

            final boolean isPostIncrement = eaMode == 0b011;
            if ( isPostIncrement ) {
                addressRegisters[ eaRegister ] = address;
            }
        }

        /*
        Calculate cycles.

                    110       111/000    111/001      111/010     111/011    010      011      100      101
                 d(An,ix)     xxx.W      xxx.L      d(pc)      d(pc,ix)   (An)     (An)+    -(An)     d(An)
MOVEM	word	   14+4n      12+4n      16+4n	    -		-              8+4n	   -		  8+4n	  12+4n
R->M    long	   14+8n      12+8n      16+8n	    -		-              8+8n	   -		  8+8n	  12+8n

MOVEM	word	   18+4n      16+4n      20+4n	    16+4n      18+4n      12+4n	   12+4n	  -	      16+4n
M->R    long	   18+8n      16+8n      20+8n	    16+8n      18+8n      12+8n	   12+8n	  -	      16+8n
         */

        final int regsMovedCount = Integer.bitCount(bitMask & 0xffff); // need to do &0xffff because of memLoad() sign extension
        if ( operandSizeInBytes == 2 ) {
            cycles += 4*regsMovedCount;
        } else {
            cycles += 8*regsMovedCount;
        }

        if ( regsToMemory )
        {
            // REGS -> MEMORY
            switch( eaMode )
            {
                case 0b110: cycles += 14;break;case 0b111:
                switch(eaRegister)
                {
                    case 0b000: cycles += 12;break;
                    case 0b001: cycles += 16;break;
                    default: throw new RuntimeException("Unreachable code reached");
                }
                case 0b010:
                case 0b100: cycles += 8;break;
                case 0b101: cycles += 12;break;
                default: throw new RuntimeException("Unreachable code reached");
            }
        } else {
            // MEMORY -> REGS
            switch( eaMode )
            {
                case 0b110: cycles+=18;break;
                case 0b111:
                    switch(eaRegister)
                    {
                        case 0b000: cycles += 16;break;
                        case 0b001: cycles += 20;break;
                        case 0b011: cycles += 18;break;
                        default: throw new RuntimeException("Unreachable code reached");
                    }
                case 0b011: // (a0)+
                case 0b010: cycles+=12;break; // (a0)
                case 0b101: cycles+=16;break; // d(An)
                default: throw new RuntimeException("Unreachable code reached, eaMode = "+Misc.binary8Bit(eaMode ) );
            }
        }
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
        return "CPU[ pc = "+ Misc.hex(pc)+" , insn="+binaryInsn+", sr="+Misc.hex(statusRegister)+", sp="+Misc.hex(addressRegisters[7])+",IRQ="+activeIrq+"]\n"+flagHelp+"\n"+flags;
    }

    private void movepFromMemoryToRegister(int instruction, int operandSizeInBytes)
    {
        final int dstDataRegNum = (instruction & 0b111000000000) >>> 9;
        final int address = addressRegisters[(instruction & 0b111)] + memLoadWord(pc);
        pc+=2;
        if ( operandSizeInBytes == 2 )
        {
            final int value = ((memory.readByte(address) & 0xff) << 8) | (memory.readByte(address+2) & 0xff);
            dataRegisters[dstDataRegNum] = (dataRegisters[dstDataRegNum] & 0xffff0000) | value;
        } else {
            final int value =
                    ((memory.readByte(address)           & 0xff) << 24) |
                            ((memory.readByte(address+2) & 0xff) << 16) |
                            ((memory.readByte(address+4) & 0xff) <<  8) |
                            ( memory.readByte(address+6) & 0xff);
            dataRegisters[dstDataRegNum] = value;
        }
    }

    private void movepFromRegisterToMemory(int instruction, int operandSizeInBytes)
    {
        final int value = dataRegisters[(instruction & 0b111000000000) >>> 9];

        final int address = addressRegisters[(instruction & 0b111)] + memLoadWord(pc);
        pc+=2;

        if ( operandSizeInBytes == 2 )
        {
            memory.writeByte(address,(value & 0xff00) >>> 8);
            memory.writeByte(address+2,value & 0xff);
        } else {
            memory.writeByte(address,(value & 0xff000000) >>> 24);
            memory.writeByte(address+2,(value & 0x00ff0000) >>> 16);
            memory.writeByte(address+4,(value & 0x0000ff00) >>> 8);
            memory.writeByte(address+6,value  & 0x000000ff);
        }
    }

    private static boolean isOverflow8Bit(int a,int b,int result) {
        /*
       v <= (not add_A(7) and not add_B(7) and Y(7)) or (add_A(7) and add_B(7) and not Y(7))
         */
        final boolean msbA = (a & 1<<7) != 0;
        final boolean msbB = (b & 1<<7) != 0;
        final boolean msbResult = (result & 1<<7) != 0;
        return (! msbA & !msbB & msbResult) | (msbA & msbB & ! msbResult);
    }

    private static boolean isOverflow16Bit(int a,int b,int result) {
        final boolean msbA = (a & 1<<15) != 0;
        final boolean msbB = (b & 1<<15) != 0;
        final boolean msbResult = (result & 1<<15) != 0;
        return (! msbA & !msbB & msbResult) | (msbA & msbB & ! msbResult);
    }

    private static boolean isOverflow32Bit(int a,int b,int result) {
        final boolean msbA = (a & 1<<31) != 0;
        final boolean msbB = (b & 1<<31) != 0;
        final boolean msbResult = (result & 1<<31) != 0;
        return (! msbA & !msbB & msbResult) | (msbA & msbB & ! msbResult);
    }

    private void bitOp(int instruction,BitOp op,BitOpMode mode)
    {
        int bitNum;
        if ( mode == BitOpMode.IMMEDIATE )
        {
            // BTST #xx,<ea>
            bitNum = memLoadWord( pc ) & 0b11111;
            pc += 2;
        }
        else if ( mode == BitOpMode.REGISTER )
        {
            // BTST Dn,<ea>
            final int regNum = (instruction & 0b0000111000000000) >> 9;
            bitNum = dataRegisters[regNum] & 0b11111;
        } else {
            throw new RuntimeException("Unreachable code reached");
        }
        // hint: decodeSourceOperand only applies operandSize parameter when
        //       accessing memory locations
        final int eaMode = (instruction & 0b111000) >> 3;
        final int eaRegister = (instruction & 0b111);
        final int modeFlags = AddressingModeKind.bitsToFlags( eaMode,eaRegister );
        final boolean isMemory = (modeFlags & AddressingModeKind.MEMORY.bits) != 0;
        final int operandSize= isMemory ? 1 : 4;
        decodeSourceOperand( instruction,operandSize, false );
        if ( ( value & 1<<bitNum) == 0 ) {
            statusRegister |= FLAG_ZERO;
        } else {
            statusRegister &= ~FLAG_ZERO;
        }
        switch( op ) {

            case CLEAR:
                value &= ~(1<<bitNum);
                break;
            case TEST:
                // nothing to do after Z flag has been updated
                return;
            case SET:
                value |= (1<<bitNum);
                break;
            case FLIP:
                value ^= (1<<bitNum);
                break;
        }

        switch( mode )
        {
            case IMMEDIATE: // BTST #x,<ea>
                // STATIC
                if ( isMemory )
                {
                    switch( op ) {
                        case FLIP:  cycles += 12; break;
                        case CLEAR: cycles += 12; break;
                        case SET:   cycles += 12; break;
                        case TEST:  cycles +=  8; break;
                    }
                }
                else
                {
                    switch( op ) {
                        case FLIP:  cycles += 12; break;
                        case CLEAR: cycles += 14; break;
                        case SET:   cycles += 12; break;
                        case TEST:  cycles += 10; break;
                    }
                }
                break;
            case REGISTER:  // BTST Dx,<ea>
                // DYNAMIC
                if ( isMemory )
                {
                    switch( op )
                    {
                        case FLIP:  cycles += 8; break;
                        case CLEAR: cycles += 8; break;
                        case SET:   cycles += 8; break;
                        case TEST:  cycles += 4; break;
                    }
                }
                else
                {
                    switch( op ) {
                        case FLIP:  cycles +=  8; break;
                        case CLEAR: cycles += 10; break;
                        case SET:   cycles +=  8; break;
                        case TEST:  cycles +=  6; break;
                    }
                }
        }
        // FIXME: Cycle count for memory operations is probably wrong as storeValue()
        // FIXME: never adds to the 'cycles' variable
        storeValue( eaMode,eaRegister,operandSize);
    }

    private void updateFlagsAfterTST(int operandSize) {
        int setMask=0;
        switch(operandSize) {
            case 1:
                value &= 0xff;
                setMask |= (value&1<<7) != 0 ? FLAG_NEGATIVE : 0 ;
                break;
            case 2:
                value &= 0xffff;
                setMask |= (value&1<<15) != 0 ? FLAG_NEGATIVE : 0 ;
                break;
            case 4:
                setMask |= (value<0) ? FLAG_NEGATIVE : 0 ;
                break;
        }
        setMask |= (value == 0) ? FLAG_ZERO : 0 ;

        statusRegister = (statusRegister &
                ~(FLAG_NEGATIVE|FLAG_ZERO|FLAG_OVERFLOW|FLAG_CARRY)) | setMask;
    }

    public void setIRQLevel(int level) {
        if ( level < 0 || level > 7 ) {
            throw new IllegalArgumentException("Invalid IRQ level: "+level);
        }
        statusRegister = ( statusRegister & ~0b111_0000_0000) | (level<<8);
    }
    /**
     * Returns the CPUs current interrupt level (0...7).
     *
     * @return
     */
    public int getIRQLevel()
    {
        return (statusRegister >>>8 ) & 0b111;
    }

    private enum CCOperation {
        ADDITION, SUBTRACTION,OTHER
    }

    private void updateFlags(int src, int dst, int result, int sizeInBytes, CCOperation operation, int flagsToUpdate)
    {
        // this method taken from https://github.com/BSVC/bsvc and converted from C++ to Java by me.
        // The original implementation is (C) Dan Cross (https://github.com/dancrossnyc)
        boolean S, D, R;

        switch (sizeInBytes) {
            case 1:
                S = (src & 1<<7) != 0;
                D = (dst & 1<<7) != 0;
                R = (result & 1<<7) != 0;
                result = result & 0xff;
                break;
            case 2:
                S = (src & 1<<15) != 0;
                D = (dst & 1<<15) != 0;
                R = (result & 1<<15) != 0;
                result = result & 0xffff;
                break;
            case 4:
                S = (src & 1<<31) != 0;
                D = (dst & 1<<31) != 0;
                R = (result & 1<<31) != 0;
                result = result & 0xffffffff;
                break;
            default:
                S = D = R = false;
        }

        boolean needsCarry = (operation == CCOperation.ADDITION || operation == CCOperation.SUBTRACTION) && (flagsToUpdate & FLAG_CARRY) != 0 ||
                (flagsToUpdate & FLAG_EXTENDED) != 0;

        boolean setCarry = false;
        if ( needsCarry )
        {
            if (operation == CCOperation.ADDITION)
            {
                setCarry = ((S && D) || (!R && D) || (S && !R));
            }
            else if (operation == CCOperation.SUBTRACTION)
            {
                setCarry = ((S && !D) || (R && !D) || (S && R));
            }
        }

        int clearMask = 0xffffffff;
        int setMask = 0;

        if ( (flagsToUpdate & FLAG_CARRY) != 0)
        {
            switch (operation)
            {
                case ADDITION:
                    if (setCarry)
                    {
                        setMask |= FLAG_CARRY;
                    }
                    else
                    {
                        clearMask &= ~FLAG_CARRY;
                    }
                    break;
                case SUBTRACTION:
                    if (setCarry)
                    {
                        setMask |= FLAG_CARRY;
                    }
                    else
                    {
                        clearMask &= ~FLAG_CARRY;
                    }
                    break;
                default:
                    clearMask &= ~FLAG_CARRY;
                    break;
            }
        }

        if ( (flagsToUpdate & FLAG_OVERFLOW) != 0)
        {
            switch (operation)
            {
                case ADDITION:
                    if ((S && D && !R) || (!S && !D && R))
                    {
                        setMask |= FLAG_OVERFLOW;
                    }
                    else
                    {
                        clearMask &= ~FLAG_OVERFLOW;
                    }
                    break;
                case SUBTRACTION:
                    if ((!S && D && !R) || (S && !D && R))
                    {
                        setMask |= FLAG_OVERFLOW;
                    }
                    else
                    {
                        clearMask &= ~FLAG_OVERFLOW;
                    }
                    break;
                default:
                    clearMask &= ~FLAG_OVERFLOW;
                    break;
            }
        }

        if ( (flagsToUpdate & FLAG_ZERO) != 0) {
            if (result == 0)
            {
                setMask |= FLAG_ZERO;
            }
            else
            {
                clearMask &= ~FLAG_ZERO;
            }
        }

        if ( (flagsToUpdate & FLAG_NEGATIVE) != 0) {
            if (R)
            {
                setMask |= FLAG_NEGATIVE;
            }
            else
            {
                clearMask &= ~FLAG_NEGATIVE;
            }
        }

        if ( (flagsToUpdate & FLAG_EXTENDED) != 0)
        {
            switch (operation)
            {
                case ADDITION:
                    if (setCarry)
                    {
                        setMask |= FLAG_EXTENDED;
                    }
                    else
                    {
                        clearMask &= ~FLAG_EXTENDED;
                    }
                    break;
                case SUBTRACTION:
                    if (setCarry)
                    {
                        setMask |= FLAG_EXTENDED;
                    }
                    else
                    {
                        clearMask &= ~FLAG_EXTENDED;
                    }
                    break;
                default:
                    clearMask &= ~FLAG_EXTENDED;
                    break;
            }
        }

        statusRegister = (statusRegister & clearMask) | setMask;
    }

    private void sub(int instruction) {
        sub(instruction,false);
    }

    private void cmp(int instruction) {
        sub(instruction,true);
    }

    private void sub(int instruction,boolean isCompareInsn)
    {
        // SUB <ea>,Dx
        // SUB Dx,<ea>
        int sizeBits = (instruction & 0b11000000) >> 6;
        int regNum = (instruction&0b111000000000)>>9;

        final int srcValue;
        final int dstValue;
        final boolean dstIsEa = (instruction & 1<<8) != 0;
        if (dstIsEa)
        {
            // Dn + <ea> -> <ea>
            switch(sizeBits)
            {
                case 0b00:
                    srcValue = (dataRegisters[regNum]<<24)>>24;
                    cycles += 8;
                    break;
                case 0b01:
                    srcValue = (dataRegisters[regNum]<<16)>>16;
                    cycles += 8;
                    break;
                case 0b10:
                    srcValue = dataRegisters[regNum];
                    cycles += 12;
                    break;
                default:
                    throw new RuntimeException("Unreachable code reached");
            }
            decodeSourceOperand(instruction,1<<sizeBits,false,false);
            dstValue = value;
        }
        else
        {
            // <ea> + Dn -> Dn
            decodeSourceOperand(instruction,1<<sizeBits,false);
            srcValue = value;
            switch(sizeBits)
            {
                case 0b00:
                    dstValue = (dataRegisters[regNum]<<24)>>24;
                    cycles += 4;
                    break;
                case 0b01:
                    dstValue = (dataRegisters[regNum]<<16)>>16;
                    cycles += 4;
                    break;
                case 0b10:
                    dstValue = dataRegisters[regNum];
                    cycles += 6;
                    break;
                default:
                    throw new RuntimeException("Unreachable code reached");
            }
        }
        final int result = dstValue - srcValue;
        value = result;

        if ( isCompareInsn )
        {
            updateFlags(srcValue, dstValue, value, 1<<sizeBits, CCOperation.SUBTRACTION, CPU.USERMODE_FLAGS_NO_X);
        }
        else
        {
            updateFlags(srcValue, dstValue, value, 1<<sizeBits, CCOperation.SUBTRACTION, CPU.ALL_USERMODE_FLAGS);
            if (dstIsEa)
            {
                storeValue((instruction & 0b111000) >> 3, instruction & 0b111, 1 << sizeBits);
            }
            else
            {
                dataRegisters[regNum] = mergeValue(dataRegisters[regNum], result, 1 << sizeBits);
            }
        }
    }

    private void subi(int instruction) {
        subi(instruction,false);
    }

    private void cmpi(int instruction) {
        subi(instruction,true);
    }

    private void subi(int instruction,boolean isCompareInsn)
    {
        // SUBI
        final int operandSizeInBytes = 1 << ( (instruction & 0b11000000) >> 6 );
        int srcValue;
        switch(operandSizeInBytes) {
            case 1:
                srcValue = (memLoadWord(pc)<<24)>>24; // sign-extend
                pc += 2;
                break;
            case 2:
                srcValue = memLoadWord(pc);
                pc += 2;
                break;
            case 4:
                srcValue = memLoadLong(pc);
                pc += 4;
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }
        final int eaMode = (instruction & 0b111000) >> 3;
        final int eaRegister = instruction & 0b111;

        if ( decodeSourceOperand(instruction,operandSizeInBytes,false,false) ) {
            // register operand
            switch(operandSizeInBytes) {
                case 1:
                case 2:
                    cycles+=8;
                    break;
                case 4:
                    cycles += 12;
                    break;
            }
        } else {
            // memory operand
            switch(operandSizeInBytes) {
                case 1:
                case 2:
                    cycles+=16;
                    break;
                case 4:
                    cycles += 20;
                    break;
            }
        }

        final int dstValue = value;

        value -= srcValue;

        if ( isCompareInsn )
        {
            updateFlags(srcValue, dstValue, value, operandSizeInBytes, CCOperation.SUBTRACTION, CPU.USERMODE_FLAGS_NO_X);
        }
        else
        {
            storeValue(eaMode, eaRegister, operandSizeInBytes);
            updateFlags(srcValue, dstValue, value, operandSizeInBytes, CCOperation.SUBTRACTION, CPU.ALL_USERMODE_FLAGS);
        }

    }
}