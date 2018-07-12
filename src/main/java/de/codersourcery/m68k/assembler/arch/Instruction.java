package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.IValueNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.NodeType;
import de.codersourcery.m68k.parser.ast.NumberNode;
import de.codersourcery.m68k.parser.ast.OperandNode;
import de.codersourcery.m68k.parser.ast.RegisterNode;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;

import static de.codersourcery.m68k.assembler.arch.AddressingMode.ABSOLUTE_SHORT_ADDRESSING;
import static de.codersourcery.m68k.assembler.arch.AddressingMode.IMMEDIATE_VALUE;

/**
 * Enumeration of all M68000 instructions.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum Instruction
{
    CHK("CHK",2) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingModeKind( node,AddressingModeKind.DATA );
            Instruction.checkDestinationAddressingMode( node,AddressingMode.DATA_REGISTER_DIRECT );
            if ( ! node.useImpliedOperandSize && node.getOperandSize() == OperandSize.BYTE ) {
                throw new RuntimeException("CHK only supports .w or .l operand sizes");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    NOT("NOT",1) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingModeKind( node,AddressingModeKind.ALTERABLE );
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    TRAPV("TRAPV",0) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
        }
    },
    TST("TST",1) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            final AddressingMode mode = node.source().addressingMode;
            if ( mode == AddressingMode.PC_INDIRECT_WITH_DISPLACEMENT ||
                    mode == AddressingMode.PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT ||
                    mode == AddressingMode.PC_INDIRECT_WITH_INDEX_DISPLACEMENT ||
                    mode == AddressingMode.IMMEDIATE_VALUE ||
                    mode == AddressingMode.ADDRESS_REGISTER_DIRECT ||
                    mode == AddressingMode.PC_MEMORY_INDIRECT_POSTINDEXED ||
                    mode == AddressingMode.PC_MEMORY_INDIRECT_PREINDEXED )
            {
                throw new RuntimeException("TST does not support addressing mode "+mode+" on 68000");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    CLR("CLR",1) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingModeKind(node,AddressingModeKind.ALTERABLE);
        }
        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    BCHG("BCHG",2) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkBitInstructionValid( node,ctx );
        }
    },
    BSET("BSET",2) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkBitInstructionValid( node,ctx );
        }
    },
    BCLR("BCLR",2) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkBitInstructionValid( node,ctx );
        }
    },
    BTST("BTST",2) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkDestinationAddressingModeKind( node,AddressingModeKind.DATA );
            if ( node.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                final Integer bitNum = node.source().getValue().getBits( ctx );
                if ( node.destination().addressingMode.hasKind( AddressingModeKind.MEMORY ) ) {
                    if ( bitNum != null && (bitNum < 0 || bitNum > 7) ) {
                        throw new RuntimeException( "BTST with memory locations can only operate on bits 0..7");
                    }
                } else {
                    if ( bitNum != null && (bitNum < 0 || bitNum > 31) ) {
                        throw new RuntimeException( "BTST can only operate on bits 0..31");
                    }
                }
            }
            else if ( ! node.source().getValue().isDataRegister() )
            {
                throw new RuntimeException( "Unsupported source addressing mode for BTST, only immediate or data register are allowed" );
            }
        }
    },
    EXT("EXT",1) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingMode( node,AddressingMode.DATA_REGISTER_DIRECT );
            if ( node.hasOperandSize( OperandSize.BYTE ) ) {
                throw new RuntimeException("Only operand sizes WORD or LONG are supported by EXT");
            }
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ASL("ASL",2) {
        @Override public int getMinOperandCount() { return 1; }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ASR("ASR",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    LSL("LSL",2) {
        @Override public int getMinOperandCount() { return 1; }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    LSR("LSR",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ROL("ROL",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ROR("ROR",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    NEG("NEG",1) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.ALTERABLE );
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    PEA("PEA",1) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL );
        }
    },
    RTR("RTR",0) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
        }
    },
    RESET("RESET",0)
    {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) {}
    },
    UNLK("UNLK",1)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            if ( ! node.source().getValue().isAddressRegister() ) {
                throw new RuntimeException("Expected an address register as source operand");
            }
        }
    },
    LINK("LINK",2)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            if ( ! node.source().getValue().isAddressRegister() ) {
                throw new RuntimeException("Expected an address register as source operand");
            }
            if ( ! node.destination().hasAddressingMode(AddressingMode.IMMEDIATE_VALUE ) ) {
                throw new RuntimeException("Expected an immediate mode value as destination operand");
            }
            Instruction.checkOperandSizeUnsigned( node.destination().getValue() ,16,ctx );
        }
    },
    RTS("RTS",0)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {

        }
    },
    JSR("JSR",1)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL);
        }
    },
    SWAP("SWAP",1)
    {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            if ( ! node.source().getValue().isDataRegister() ) {
                throw new RuntimeException("SWAP requires a data requires");
            }
            if ( ! node.useImpliedOperandSize && node.getOperandSize() != OperandSize.WORD ) {
                throw new RuntimeException("SWAP only supports .w");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    JMP("JMP",1)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL);
                }
            },
    AND("AND",2)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                }
            },
    TRAP("TRAP",1, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    if ( node.hasDestination() ) {
                        throw new RuntimeException("TRAP only supports one operand");
                    }
                    if ( ! node.source().hasAddressingMode(AddressingMode.IMMEDIATE_VALUE ) ) {
                        throw new RuntimeException("TRAP requires an immediate mode value as operand but was "+node.source().addressingMode);
                    }
                }
            },
    RTE("RTE",0)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                }
            },
    ILLEGAL("ILLEGAL",0) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
        }
    },
    /*
     * DBcc instructions
     */
    DBT("DBT",2, 0b0000,Condition.BRT,ConditionalInstructionType.DBCC) { // aka 'always branch'
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBRA("DBRA",2, 0b0001,Condition.BSR,ConditionalInstructionType.DBCC) { // ignores condition check
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBHI("DBHI",2, 0b0010,Condition.BHI,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBLS("DBLS",2, 0b0011,Condition.BLS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBCC("DBCC",2, 0b0100,Condition.BCC,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBCS("DBCS",2, 0b0101,Condition.BCS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBNE("DBNE",2, 0b0110,Condition.BNE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBEQ("DBEQ",2, 0b0111,Condition.BEQ,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBVC("DBVC",2, 0b1000,Condition.BVC,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBVS("DBVS",2, 0b1001,Condition.BVS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBPL("DBPL",2, 0b1010,Condition.BPL,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBMI("DBMI",2, 0b1011,Condition.BMI,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBGE("DBGE",2, 0b1100,Condition.BGE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBLT("DBLT",2, 0b1101,Condition.BLT,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBGT("DBGT",2, 0b1110,Condition.BGT,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    DBLE("DBLE",2, 0b1111,Condition.BLE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkDBccInstructionValid(node,ctx); }
    },
    /*
     * Bcc instructions.
     */
    BRA("BRA",1, 0b0000,Condition.BRT,ConditionalInstructionType.BCC) { // aka 'always branch'
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BSR("BSR",1, 0b0001,Condition.BSR,ConditionalInstructionType.BCC) { // TODO: this is essentially "never branch" .... not very useful as NOP exists as well...
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BHI("BHI",1, 0b0010,Condition.BHI,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BLS("BLS",1, 0b0011,Condition.BLS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BCC("BCC",1, 0b0100,Condition.BCC,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BCS("BCS",1, 0b0101,Condition.BCS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BNE("BNE",1, 0b0110,Condition.BNE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BEQ("BEQ",1, 0b0111,Condition.BEQ,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BVC("BVC",1, 0b1000,Condition.BVC,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BVS("BVS",1, 0b1001,Condition.BVS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BPL("BPL",1, 0b1010,Condition.BPL,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BMI("BMI",1, 0b1011,Condition.BMI,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BGE("BGE",1, 0b1100,Condition.BGE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BLT("BLT",1, 0b1101,Condition.BLT,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BGT("BGT",1, 0b1110,Condition.BGT,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    BLE("BLE",1, 0b1111,Condition.BLE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx) { checkBranchInstructionValid(node,ctx); }
    },
    // Misc
    NOP("nop",0, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    if ( node.hasChildren() ) {
                        throw new RuntimeException("NOP does not accept operands");
                    }
                }
            },
    EXG("exg",2, 0b1100)
            {
                @Override
                public int getOperationCode(InstructionNode insn)
                {
                /*
01000—Data registers
01001—Address registers
10001—Data register and address register
                 */
                    boolean isData1 = insn.source().getValue().isDataRegister();
                    boolean isData2 = insn.destination().getValue().asRegister().isDataRegister();
                    if ( isData1 != isData2 ) {
                        return 0b10001;
                    }
                    return isData1 ? 0b01000 : 0b01001;
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    final OperandNode source = node.source();

                    if ( ! source.getValue().isRegister() ) {
                        throw new RuntimeException("Bad operand type, EXG needs a address or data register");
                    }
                    if ( ! ( source.getValue().isDataRegister() || source.getValue().isAddressRegister() ) ) {
                        throw new RuntimeException("Unsupported register, EXG supports address or data registers");
                    }
                    final OperandNode destination = node.destination();
                    if ( ! destination.getValue().isRegister() ) {
                        throw new RuntimeException("Bad operand type, EXG needs a address or data register");
                    }
                    if ( ! ( destination.getValue().isDataRegister() || destination.getValue().isAddressRegister() ) ) {
                        throw new RuntimeException("Unsupported register, EXG supports address or data registers");
                    }
                }
            },
    MOVEA("movea", 2, 0b0000) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx)
        {
            if ( ! node.destination().getValue().isAddressRegister() ) {
                throw new RuntimeException("MOVEA requires an address register as destination");
            }
            if ( ! node.useImpliedOperandSize && node.getOperandSize() == OperandSize.BYTE) {
                throw new RuntimeException("MOVEA only supports .w or .l");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    MOVEQ("moveq", 2, 0b0111)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    final OperandNode source = node.source();
                    if ( ! source.hasAddressingMode(AddressingMode.IMMEDIATE_VALUE) )
                    {
                        throw new RuntimeException("MOVEQ requires an immediate value as source operand");
                    }
                    final OperandNode destination = node.destination();
                    if ( ! destination.hasAddressingMode(AddressingMode.DATA_REGISTER_DIRECT) )
                    {
                        throw new RuntimeException("MOVEQ requires a data register as destination operand");
                    }
                    Instruction.checkOperandSizeUnsigned(node.source().getValue(), 8,ctx);
                }

            },
    MOVE("move", 2, 0b0000)
            {
                @Override
                public int getOperationCode(InstructionNode insn)
                {
                    switch (insn.getOperandSize())
                    {
                        case BYTE:
                            return 0b0001;
                        case WORD:
                            return 0b0011;
                        case LONG:
                            return 0b0010;
                    }
                    throw new RuntimeException("Unhandled switch/case: " + insn.getOperandSize());
                }

                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                }
            },
    LEA("lea", 2, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx)
                {
                    Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL);

                    final OperandNode source = node.source();
                    final OperandNode destination = node.destination();
                    if ( destination.getValue().isNot(NodeType.REGISTER) || ! destination.getValue().asRegister().isAddressRegister() )
                    {
                        throw new RuntimeException("LEA needs an address register as destination");
                    }
                }
            };

    public final ConditionalInstructionType conditionalType;
    public final Condition condition;
    private final String mnemonic;
    private final int maxOperandCount;
    private final int operationMode; // bits 15-12 of first instruction word

    Instruction(String mnemonic, int maxOperandCount) {
        this(mnemonic,maxOperandCount, 0,null,ConditionalInstructionType.NONE);
    }

    Instruction(String mnemonic, int maxOperandCount, int operationMode) {
        this(mnemonic,maxOperandCount, operationMode,null,ConditionalInstructionType.NONE);
    }

    Instruction(String mnemonic, int maxOperandCount, int operationMode, Condition condition, ConditionalInstructionType conditionalType)
    {
        if ( maxOperandCount > 2 ) {
            throw new IllegalArgumentException("Parser only supports up to 2 operands");
        }
        this.mnemonic = mnemonic.toLowerCase();
        this.maxOperandCount = maxOperandCount;
        this.operationMode = operationMode;
        this.condition = condition;
        this.conditionalType = conditionalType;
    }

    public int getOperationMode()
    {
        return operationMode;
    }

    public abstract void checkSupports(InstructionNode node, ICompilationContext ctx);

    public int getOperationCode(InstructionNode insn)
    {
        return operationMode;
    }

    public int getMaxOperandCount()
    {
        return maxOperandCount;
    }

    public int getMinOperandCount()
    {
        return maxOperandCount;
    }

    public String getMnemonic()
    {
        return mnemonic;
    }

    public static Instruction getType(String value)
    {
        if (value != null)
        {
            final String lValue = value.toLowerCase();
            for (Instruction t : Instruction.values())
            {
                if (t.mnemonic.equals(lValue))
                {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Generates the code for a given instruction and writes it
     * using the context's current {@link de.codersourcery.m68k.assembler.IObjectCodeWriter}.
     *
     * @param insn
     * @param context
     * @param estimateSizeForUnknownOperands set to <code>true</code> to call {@link de.codersourcery.m68k.assembler.IObjectCodeWriter#allocateBytes(int)}
     *                         instead of writing the actual data bytes. Used while operands may still be unknown due to forward references
     */
    public void generateCode(InstructionNode insn, ICompilationContext context,boolean estimateSizeForUnknownOperands)
    {
        final InstructionEncoding encoding;
        final byte[] data;
        try
        {
            encoding = getEncoding(this, insn, context,estimateSizeForUnknownOperands);
            if ( estimateSizeForUnknownOperands ) {
                final int sizeInBytes = encoding.getSizeInBytes();
                context.getCodeWriter().allocateBytes(sizeInBytes);
                return;
            }

            final Function<Field, Integer> func;
            final Condition condition = insn.instruction.condition;
            if ( condition != null )
            {
                final int instructionAddress = context.getCodeWriter().offset();
                func = field ->
                {
                    switch( insn.instruction.conditionalType )
                    {
                        case DBCC:
                            if (field == Field.SRC_BASE_REGISTER)
                            { // DBcc Dx,...
                                return insn.source().getValue().getBits(context);
                            }
                            if ( field == Field.CONDITION_CODE)
                            {
                                return condition.bits;
                            }
                            if ( field == Field.RELATIVE_OFFSET )
                            {
                                final Integer branchTargetAddress = insn.destination().getValue().getBits(context);
                                if ( (branchTargetAddress & 1) != 0 ) {
                                    throw new RuntimeException("Relative branch needs an even target address but got "+branchTargetAddress);
                                }
                                return branchTargetAddress - instructionAddress - 2;
                            }
                            break;
                        case BCC:
                            if ( field == Field.CONDITION_CODE)
                            {
                                return condition.bits;
                            }
                            if ( field == Field.RELATIVE_OFFSET )
                            {
                                final Integer branchTargetAddress = insn.source().getValue().getBits(context);
                                if ( (branchTargetAddress & 1) != 0 ) {
                                    throw new RuntimeException("Relative branch needs an even target address but got "+branchTargetAddress);
                                }
                                return branchTargetAddress - instructionAddress;
                            }
                            break;
                        case NONE:
                            throw new RuntimeException("Internal error - Instruction "+insn.instruction+" has non-NULL CC value but conditional instruction type NONE ?");
                        default:
                            throw new RuntimeException("Internal error - Instruction "+insn.instruction+" has unhandled conditional instruction type "+insn.instruction.conditionalType);
                    }
                    throw new RuntimeException("Internal error,unhandled field "+field);
                };
            } else {
                func = field -> getValueFor(insn, field, context);
            }
            data = encoding.apply(func);
            context.getCodeWriter().writeBytes(data);
        }
        catch (Exception e)
        {
            context.error(e.getMessage(), insn, e);
        }
    }

    protected InstructionEncoding getEncoding(Instruction type,
                                              InstructionNode insn,
                                              ICompilationContext context,
                                              boolean estimateSizeOnly)
    {
        type.checkSupports(insn, context);

        switch (type)
        {
            case CHK:
                if ( insn.useImpliedOperandSize ) {
                    insn.setImplicitOperandSize(OperandSize.WORD);
                }
                String[] extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( extraSrcWords != null ) {
                    return CHK_ENCODING.append(extraSrcWords);
                }
                return CHK_ENCODING;
            case TRAPV:
                return TRAPV_ENCODING;
            case TST:
                extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( extraSrcWords != null ) {
                    return TST_ENCODING.append(extraSrcWords);
                }
                return TST_ENCODING;
            case NOT:
                System.out.println("NOT( "+insn.source().addressingMode+")");
                extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( extraSrcWords != null ) {
                    return NOT_ENCODING.append(extraSrcWords);
                }
                return NOT_ENCODING;
            case CLR:
                extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( extraSrcWords != null ) {
                    return CLR_ENCODING.append(extraSrcWords);
                }
                return CLR_ENCODING;
            case BCHG:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    return BCHG_STATIC_ENCODING;
                }
                return BCHG_DYNAMIC_ENCODING;
            case BSET:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    return BSET_STATIC_ENCODING;
                }
                return BSET_DYNAMIC_ENCODING;
            case BCLR:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    return BCLR_STATIC_ENCODING;
                }
                return BCLR_DYNAMIC_ENCODING;
            case BTST:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    return BTST_STATIC_ENCODING;
                }
                return BTST_DYNAMIC_ENCODING;
            case EXT:
                if ( insn.useImpliedOperandSize || insn.hasOperandSize( OperandSize.WORD ) ) {
                    return EXTW_ENCODING;
                }
                return EXTL_ENCODING;
            case ASL:
                return selectRotateEncoding( insn,
                         ASL_MEMORY_ENCODING,
                         ASL_IMMEDIATE_ENCODING,
                         ASL_REGISTER_ENCODING,context);
            case ASR:
                return selectRotateEncoding( insn,
                         ASR_MEMORY_ENCODING,
                         ASR_IMMEDIATE_ENCODING,
                         ASR_REGISTER_ENCODING,context);
            case LSL:
                return selectRotateEncoding( insn,
                        LSL_MEMORY_ENCODING,
                        LSL_IMMEDIATE_ENCODING,
                        LSL_REGISTER_ENCODING,context);
            case LSR:
                return selectRotateEncoding( insn,
                        LSR_MEMORY_ENCODING,
                        LSR_IMMEDIATE_ENCODING,
                        LSR_REGISTER_ENCODING,context);
            case ROL:
                return selectRotateEncoding( insn,
                        ROL_MEMORY_ENCODING,
                        ROL_IMMEDIATE_ENCODING,
                        ROL_REGISTER_ENCODING,context);
            case ROR:
                return selectRotateEncoding( insn,
                        ROR_MEMORY_ENCODING,
                        ROR_IMMEDIATE_ENCODING,
                        ROR_REGISTER_ENCODING,context);
            case NEG:
                return NEG_ENCODING;
            case PEA:
                return PEA_ENCODING;
            case RTR:
                return RTR_ENCODING;
            case RESET:
                return RESET_ENCODING;
            case UNLK:
                return UNLINK_ENCODING;
            case LINK:
                return LINK_ENCODING;
            case RTS:
                return RTS_ENCODING;
            case JSR:
                extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( extraSrcWords != null ) {
                    return JSR_ENCODING.append(extraSrcWords);
                }
                return JSR_ENCODING;
            case SWAP:
                return SWAP_ENCODING;
            case JMP:
                switch( insn.source().addressingMode )
                {
                    case ADDRESS_REGISTER_INDIRECT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    case MEMORY_INDIRECT_POSTINDEXED:
                    case MEMORY_INDIRECT_PREINDEXED:
                    case PC_INDIRECT_WITH_DISPLACEMENT:
                    case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                    case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    case PC_MEMORY_INDIRECT_POSTINDEXED:
                    case PC_MEMORY_INDIRECT_PREINDEXED:
                        return JMP_INDIRECT_ENCODING;
                    case ABSOLUTE_SHORT_ADDRESSING:
                        return JMP_SHORT_ENCODING;
                    case ABSOLUTE_LONG_ADDRESSING:
                        return JMP_LONG_ENCODING;
                    default:
                        throw new RuntimeException("Unsupported addressing mode for JMP: "+insn.source().addressingMode);
                }
            case AND:
                if ( insn.source().hasAddressingMode( IMMEDIATE_VALUE ) &&
                        insn.destination().getValue().isRegister(Register.SR) )
                {
                    // ANDI #xx,SR
                    if ( insn.useImpliedOperandSize )
                    {
                        insn.setImplicitOperandSize(OperandSize.WORD);
                    }
                    else if (insn.getOperandSize() != OperandSize.WORD)
                    {
                        throw new RuntimeException("ANDI to SR needs a 16-bit operand");
                    }
                    if ( ! estimateSizeOnly )
                    {
                        final int bits = insn.source().getValue().getBits(context);
                        if ( NumberNode.getSizeInBitsUnsigned(bits) > 16 ) {
                            throw new RuntimeException("ANDI to SR needs a 16-bit operand, was: "+Misc.hex(bits));
                        }
                    }
                    return ANDI_TO_SR_ENCODING;
                }
                // TODO: Implement the other variants of AND ...
                throw new RuntimeException("Sorry, AND operation not fully implemented");
            case TRAP:
                if ( ! estimateSizeOnly )
                {
                    final int value = insn.source().getValue().getBits(context);
                    if ( value < 0 || value > 15 ) {
                        throw new RuntimeException("TRAP # out-of-range (0-15), was "+value);
                    }
                }
                return TRAP_ENCODING;
            case RTE:
                return RTE_ENCODING;
            case ILLEGAL:
                return ILLEGAL_ENCODING;
            // DBcc instructions
            case DBRA:
            case DBT:
            case DBHI:
            case DBLS:
            case DBCC:
            case DBCS:
            case DBNE:
            case DBEQ:
            case DBVC:
            case DBVS:
            case DBPL:
            case DBMI:
            case DBGE:
            case DBLT:
            case DBGT:
            case DBLE:
                return DBCC_ENCODING;
            // Bcc branch instructions
            case BRA:
            case BSR:
            case BHI:
            case BLS:
            case BCC:
            case BCS:
            case BNE:
            case BEQ:
            case BVC:
            case BVS:
            case BPL:
            case BMI:
            case BGE:
            case BLT:
            case BGT:
            case BLE:
                Integer targetAddress = insn.source().getValue().getBits(context);
                if ( targetAddress == null )
                {
                    if ( estimateSizeOnly ) {
                        return BCC_32BIT_ENCODING; // worst-case scenario
                    }
                    throw new RuntimeException("Failed to get displacement from "+insn.source());
                }

                if ( (targetAddress & 1) != 0 ) {
                    throw new RuntimeException("Relative branch needs an even target address but got "+targetAddress);
                }

                final int currentAddress = context.getCodeWriter().offset();
                int relativeOffset = targetAddress - currentAddress;
                if ( relativeOffset == 0 ) {
                    /* A branch to the immediately following instruction automatically
                     * uses the 16-bit displacement format because the 8-bit
                     * displacement field contains $00 (zero offset) which indicates
                     * 16-bit displacement format.
                     */
                    return BCC_16BIT_ENCODING;
                }
                int size = NumberNode.getSizeInBitsSigned(relativeOffset);
                if ( size <= 8 )
                {
                    return BCC_8BIT_ENCODING;
                }
                if ( size <= 16 )
                {
                    return BCC_16BIT_ENCODING;
                }
                if ( size <= 32 )
                {
                    if ( context.options().cpuType.isNotCompatibleWith( CPUType.M68020 ) ) {
                        context.error("32-bit relative branch offset only supported on M68020+");
                    }
                    return BCC_32BIT_ENCODING;
                }
                throw new RuntimeException("Internal error - relative branch offset larger than 32 bits?");
            case NOP:
                return NOP_ENCODING;
            case EXG:
                return EXG_ENCODING;
            case MOVEQ:
                return MOVEQ_ENCODING;
            case LEA:
                if ( insn.source().hasAddressingMode( ABSOLUTE_SHORT_ADDRESSING ) ) {
                    return LEA_WORD_ENCODING;
                }
                return LEA_LONG_ENCODING;
            case MOVEA:
            case MOVE:
                // check for MOVE USP
                final int srcIsUSP = insn.source().getValue().isRegister(Register.USP)      ? 0b01 : 0b00;
                final int dstIsUSP = insn.destination().getValue().isRegister(Register.USP) ? 0b10 : 0b00;
                switch( srcIsUSP | dstIsUSP )
                {
                    case 0b00:
                        break;
                    case 0b01:
                        if (!insn.destination().getValue().isAddressRegister())
                        {
                            throw new RuntimeException("MOVE USP,Ax requires  an address register as destination");
                        }
                        if ( insn.useImpliedOperandSize ) {
                            insn.setImplicitOperandSize( OperandSize.LONG );
                        }
                        if (insn.getOperandSize() != OperandSize.LONG ) {
                            throw new RuntimeException("MOVE USP,Ax only works on long-sized operands");
                        }
                        return MOVE_USP_TO_AX_ENCODING;
                    case 0b10:
                        if ( !insn.source().getValue().isAddressRegister())
                        {
                            throw new RuntimeException("MOVE Ax,USP requires an address register as source");
                        }
                        if ( insn.useImpliedOperandSize ) {
                            insn.setImplicitOperandSize( OperandSize.LONG );
                        }
                        if (insn.getOperandSize() != OperandSize.LONG ) {
                            throw new RuntimeException("MOVE Ax,USP only works on long-sized operands");
                        }
                        return MOVE_AX_TO_USP_ENCODING;
                    case 0b11:
                        throw new RuntimeException("MOVE USP,USP does not exist");
                }

                if ( insn.useImpliedOperandSize ) {
                    insn.setImplicitOperandSize( OperandSize.WORD );
                }

                // regular move instruction
                extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( insn.instruction == MOVEA )
                {
                    final InstructionEncoding encoding =
                            insn.getOperandSize() == OperandSize.WORD ? MOVEA_WORD_ENCODING : MOVEA_LONG_ENCODING;
                    return extraSrcWords != null ? encoding.append( extraSrcWords ) : encoding;
                }
                final String[] extraDstWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);

                InstructionEncoding encoding;
                switch( insn.getOperandSize() ) {
                    case BYTE:
                        encoding = MOVE_BYTE_ENCODING;
                        break;
                    case WORD:
                        encoding = MOVE_WORD_ENCODING;
                        break;
                    case LONG:
                        encoding = MOVE_LONG_ENCODING;
                        break;
                    default:
                        throw new RuntimeException("MOVE without operand size?");
                }
                if (extraSrcWords != null && extraDstWords == null)
                {
                    return encoding.append(extraSrcWords);
                }
                if (extraSrcWords == null && extraDstWords != null)
                {
                    return encoding.append(extraDstWords);
                }
                if (extraSrcWords != null && extraDstWords != null)
                {
                    return encoding.append(extraSrcWords).append(extraDstWords);
                }
                return encoding;
            default:
                throw new RuntimeException("Internal error,unhandled instruction type " + type);
        }
    }

    // returns any InstructionEncoding patterns
    // needed to accomodate all operand values
    private static String[] getExtraWordPatterns(OperandNode op, Operand operandKind,InstructionNode insn,ICompilationContext ctx)
    {
        if ( op.addressingMode.maxExtensionWords == 0 ) {
            return null;
        }

        switch (op.addressingMode)
        {
            case DATA_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_POST_INCREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                Field field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
                return new String[] { StringUtils.repeat(field.c,16) };
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                // FIXME: 1 extra word
                break;
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                // FIXME: 1-3 extra words
                break;
            case MEMORY_INDIRECT_POSTINDEXED:
                // FIXME: 1-5 extra words
                break;
            case MEMORY_INDIRECT_PREINDEXED:
                // FIXME: 1-5 extra words
                break;
            case PC_INDIRECT_WITH_DISPLACEMENT:
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT: Field.DST_BASE_DISPLACEMENT;
                return new String[] { StringUtils.repeat(field.c,16) };
            case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:

                /*
                    DST_REGISTER_KIND('R'), // (bd,br,{Rx},od) D or A
    DST_INDEX_SIZE('Q'), // (bd,br,Rx{.w|.l},od)
    DST_SCALE('E'), // (bd,br,Rx.w{*4},od)
    DST_8_BIT_DISPLACEMENT('W'), // (bd,br,Rx,{od}

BRIEF EXTENSION WORD FORMAT
15 14 13 12 11 10 9 8 7 6 5 4 3 2 1 0
|   +-+--+  |  +--+ 0 +-------------+
D/A   |     |   |           |
    REGISTER|  SCALE      DISPLACEMENT
            W/L
                 */
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT: Field.DST_BASE_DISPLACEMENT;
                return new String[] { operandKind == Operand.SOURCE ? SRC_BRIEF_EXTENSION_WORD : DST_BRIEF_EXTENSION_WORD};
            case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                // FIXME: 1-3 extra words
                break;
            case PC_MEMORY_INDIRECT_POSTINDEXED:
                // FIXME: 1-5 extra words
                break;
            case PC_MEMORY_INDIRECT_PREINDEXED:
                // FIXME: 1-5 extra words
                break;
            case ABSOLUTE_SHORT_ADDRESSING:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,16) };
            case ABSOLUTE_LONG_ADDRESSING:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,32) };
            case IMMEDIATE_VALUE:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;

                // TODO: 8-bit immediate values could actually be stored in-line (MOVEQ) instead of wasting a byte here
                // TODO: Maybe add optimization pass that turns regular MOVE into MOVEQ when possible?
                final int words = insn.getOperandSize() == OperandSize.LONG ? 2 : 1;
                return new String[] { StringUtils.repeat(field.c,words*16) };
            case IMPLIED: return null; // handled
        }
        throw new RuntimeException("Unhandled addressing mode: "+op.addressingMode);
    }

    private static InstructionEncoding selectRotateEncoding(InstructionNode insn,
                                               InstructionEncoding memory,
                                               InstructionEncoding immediate,
                                               InstructionEncoding register,
                                               ICompilationContext context)
    {
        if ( insn.useImpliedOperandSize ) {
            insn.setImplicitOperandSize(OperandSize.WORD);
        }
        if ( insn.operandCount() == 1 ) {
            final String[] extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
            if ( extraSrcWords != null ) {
                return memory.append(extraSrcWords);
            }
            return memory;
        }
        if (insn.source().hasAddressingMode(AddressingMode.IMMEDIATE_VALUE))
        {
            return immediate;
        }
        return register;
    }

    private static void checkOperandSizeUnsigned(IValueNode value, int maxSizeInBits, ICompilationContext ctx)
    {
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return;
        }
        int actualSize = NumberNode.getSizeInBitsUnsigned(nodeValue);
        if ( actualSize > maxSizeInBits ) {
            throw new RuntimeException("Operand out of range, expected at most "+maxSizeInBits+" bits but was "+actualSize);
        }
    }

    private static void checkOperandSizeSigned(IValueNode value, int maxSizeInBits, ICompilationContext ctx)
    {
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return;
        }
        int actualSize = NumberNode.getSizeInBitsSigned(nodeValue);
        if ( actualSize > maxSizeInBits ) {
            throw new RuntimeException("Operand out of range, expected at most "+maxSizeInBits+" bits but was "+actualSize);
        }
    }

    public Condition getCondition()
    {
        return condition;
    }

    /**
     * Whether this instruction supports explicit .b/.w/.l suffixes or not.
     *
     * Defaults to <code>false</code>
     * @return
     */
    public boolean supportsExplicitOperandSize() {
        return false;
    }

    private static void checkDBccInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        if ( node.source().addressingMode != AddressingMode.DATA_REGISTER_DIRECT ) {
            throw new RuntimeException("Unsupported addressing mode: "+node.source().addressingMode );
        }
        switch( node.destination().addressingMode )
        {
            case ABSOLUTE_LONG_ADDRESSING:
            case ABSOLUTE_SHORT_ADDRESSING:
                break;
            default:
                throw new RuntimeException("Unsupported addressing mode: "+node.destination().addressingMode );
        }
        checkOperandSizeSigned(node.destination().getValue(), 16,ctx);
    }

    private static void checkRotateInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        if ( node.operandCount() == 1 ) {
            // 1 operand => memory location

            checkSourceAddressingMode(node,AddressingMode.ABSOLUTE_SHORT_ADDRESSING,AddressingMode.ABSOLUTE_LONG_ADDRESSING);

            if ( node.hasExplicitOperandSize() && ! node.hasOperandSize( OperandSize.WORD ) ) {
                throw new RuntimeException(node.instruction+" can only operate on WORDs in memory");
            }
            return;
        }
        // 2 operands, register/immediate
        if (node.source().getValue().isDataRegister())
        {
            // register,register
            Instruction.checkDestinationAddressingModeKind(node, AddressingModeKind.DATA);
            return;
        }
        if (node.source().hasAddressingMode( IMMEDIATE_VALUE ) )
        {
            final Integer value = node.source().getValue().getBits( ctx );
            if ( value != null && ( value < 1 || value > 8 ) ) {
                throw new RuntimeException( node.instruction+" only supports rotating 1..8 times");
            }
            checkDestinationAddressingMode(node, AddressingMode.DATA_REGISTER_DIRECT);
            return;
        }
        throw new RuntimeException("Operands have unsupported addressing modes for " + node.instruction);
    }

    private static void checkBitInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        Instruction.checkDestinationAddressingModeKind( node,AddressingModeKind.ALTERABLE );
        if ( node.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
            final Integer bitNum = node.source().getValue().getBits( ctx );
            if ( node.destination().addressingMode.hasKind( AddressingModeKind.MEMORY ) ) {
                if ( bitNum != null && (bitNum < 0 || bitNum > 7) ) {
                    throw new RuntimeException( node.instruction+" with memory locations can only operate on bits 0..7");
                }
            } else {
                if ( bitNum != null && (bitNum < 0 || bitNum > 31) ) {
                    throw new RuntimeException( node.instruction+" can only operate on bits 0..31");
                }
            }
        }
        else if ( ! node.source().getValue().isDataRegister() )
        {
            throw new RuntimeException( "Unsupported source addressing mode for "+node.instruction+", only immediate or data register are allowed" );
        }
    }

    private static void checkBranchInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        switch (node.source().addressingMode)
        {
            case ABSOLUTE_LONG_ADDRESSING:
            case ABSOLUTE_SHORT_ADDRESSING:
                break;
            default:
                throw new RuntimeException("Unsupported addressing mode: " + node.source().addressingMode);
        }
        checkOperandSizeSigned(node.source().getValue(), 32, ctx);
    }

    private int getValueFor(InstructionNode insn,Field field, ICompilationContext ctx)
    {
        switch(field)
        {
            case SRC_REGISTER_KIND:
                return insn.source().getIndexRegister().isDataRegister() ? 0 : 1;
            case SRC_INDEX_SIZE:
                return getIndexRegisterSizeBit( insn.source().getIndexRegister() );
            case SRC_SCALE:
                return insn.source().getIndexRegister().scaling.bits;
            case SRC_8_BIT_DISPLACEMENT:
                return insn.source().getBaseDisplacement().getBits(ctx);
            case DST_REGISTER_KIND:
                return insn.destination().getIndexRegister().isDataRegister() ? 0 : 1;
            case DST_INDEX_SIZE:
                return getIndexRegisterSizeBit( insn.destination().getIndexRegister() );
            case DST_SCALE:
                return insn.destination().getIndexRegister().scaling.bits;
            case DST_8_BIT_DISPLACEMENT:
                return insn.destination().getBaseDisplacement().getBits(ctx);
            case OP_CODE:
                return insn.getInstructionType().getOperationCode(insn );
            case SRC_VALUE:
                return insn.source().getValue().getBits(ctx);
            case SRC_BASE_REGISTER:
                if ( insn.source().addressingMode.eaRegisterField.isFixedValue() ) {
                    return insn.source().addressingMode.eaRegisterField.value();
                }
                return insn.source().getValue().asRegister().getBits(ctx);
            case SRC_INDEX_REGISTER:
                return insn.source().getIndexRegister().getBits(ctx);
            case SRC_BASE_DISPLACEMENT:
                return insn.source().getBaseDisplacement().getBits(ctx);
            case SRC_OUTER_DISPLACEMENT:
                return insn.source().getOuterDisplacement().getBits(ctx);
            case SRC_MODE:
                return insn.source().addressingMode.eaModeField;
            case DST_VALUE:
                return insn.destination().getValue().getBits(ctx);
            case DST_BASE_REGISTER:
                if ( insn.destination().addressingMode.eaRegisterField.isFixedValue() ) {
                    return insn.destination().addressingMode.eaRegisterField.value();
                }
                return insn.destination().getValue().asRegister().getBits(ctx);
            case DST_INDEX_REGISTER:
                return insn.destination().getIndexRegister().getBits(ctx);
            case DST_BASE_DISPLACEMENT:
                return insn.destination().getBaseDisplacement().getBits(ctx);
            case DST_OUTER_DISPLACEMENT:
                return insn.destination().getOuterDisplacement().getBits(ctx);
            case DST_MODE:
                return insn.destination().addressingMode.eaModeField;
            case SIZE:
                if ( insn.getOperandSize() == OperandSize.UNSPECIFIED ) {
                    throw new RuntimeException("Operand size not specified");
                }
                if ( insn.instruction == MOVEA ) {
                    // MOVEA uses non-standard encoding....
                    switch(insn.getOperandSize()) {
                        case WORD: return 0b11;
                        case LONG: return 0b10;
                    }
                    throw new RuntimeException("Unhandled operand size for MOVEA: "+insn.getOperandSize());
                }
                return insn.getOperandSize().bits;
            case EXG_DATA_REGISTER:
            case EXG_ADDRESS_REGISTER:
                final Register srcReg = insn.source().getValue().asRegister().register;
                final Register dstReg = insn.destination().getValue().asRegister().register;
                // data register if EXG used with registers of different types, otherwise either the src data or src address register
                if ( field == Field.EXG_DATA_REGISTER )
                {
                    if ( srcReg.isData() != dstReg.isData() )
                    {
                        return srcReg.isData() ? srcReg.bits : dstReg.bits;
                    }
                    return srcReg.bits;
                }
                // field == Field.EXG_ADDRESS_REGISTER;
                // address register if EXG used with registers of different types, otherwise either the dst data or dst address register
                if ( srcReg.isAddress() != dstReg.isAddress() )
                {
                    return dstReg.isAddress() ? dstReg.bits : srcReg.bits;
                }
                return dstReg.bits;
            case NONE:
                return 0;
            case CONDITION_CODE: // encoded branch condition,stored as operationMode on Instruction
                return insn.getInstructionType().getOperationMode();
            default:
                throw new RuntimeException("Internal error,unhandled field "+field);
        }
    }

    public static int getIndexRegisterSizeBit(RegisterNode register)
    {
        OperandSize size = register.operandSize;
        if ( size == null )  {
            size = OperandSize.WORD;
        }
        switch(size) {
            case WORD:
                return 0;
            case LONG:
                return 1;
        }
        throw new RuntimeException("Invalid index register operand size "+size);
    }

    private static void checkSourceAddressingMode(InstructionNode insn, AddressingMode mode1,AddressingMode...additional)
    {
        if ( insn.operandCount() < 1 ) {
            throw new RuntimeException( insn+" lacks source operand");
        }
        if (noMatchingAddressingMode(insn.source(), mode1, additional))
        {
            final List<AddressingMode> modes = new ArrayList<>();
            modes.add(mode1);
            if ( additional != null ) {
                modes.addAll(Arrays.asList(additional));
            }
            throw new RuntimeException("Unsupported addressing mode in source operand, instruction "+insn.instruction+" only supports "+modes);
        }
    }

    private static void checkDestinationAddressingMode(InstructionNode insn, AddressingMode mode1,AddressingMode...additional)
    {
        if ( insn.operandCount() < 2 ) {
            throw new RuntimeException( insn+" lacks destination operand");
        }
        if (noMatchingAddressingMode(insn.destination(), mode1, additional))
        {
            final List<AddressingMode> modes = new ArrayList<>();
            modes.add(mode1);
            if ( additional != null ) {
                modes.addAll(Arrays.asList(additional));
            }
            throw new RuntimeException("Unsupported addressing mode in destination operand, instruction "+insn.instruction+" only supports "+modes);
        }
    }

    private static boolean noMatchingAddressingMode(OperandNode operand, AddressingMode mode1, AddressingMode...additional)
    {
        if ( operand.hasAddressingMode(mode1) ) {
            return false;
        }
        if ( additional != null )
        {
            for ( AddressingMode expected : additional)
            {
                if ( operand.hasAddressingMode(expected ) ) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void checkSourceAddressingModeKind(InstructionNode insn, AddressingModeKind kind)
    {
        if( ! insn.source().addressingMode.hasKind( kind ) )
        {
            throw new RuntimeException("Instruction "+insn.instruction+" only supports addressing modes of kind "+kind+" but was "+insn.source().addressingMode);
        }
    }

    private static void checkDestinationAddressingModeKind(InstructionNode insn, AddressingModeKind kind)
    {
        if( ! insn.destination().addressingMode.hasKind( kind ) )
        {
            throw new RuntimeException("Instruction "+insn.instruction+" only supports addressing modes of kind "+kind+" but was "+insn.destination().addressingMode);
        }
    }

    private static InstructionEncoding.IValueDecorator fieldDecorator(Field f, Function<Integer, Integer> func)
    {
        return (field, inputValue) ->
        {
            if ( field == f ) {
                return func.apply( inputValue );
            }
            return inputValue;
        };
    }

    public static final String SRC_BRIEF_EXTENSION_WORD = "riiiqee0wwwwwwww";
    public static final String DST_BRIEF_EXTENSION_WORD = "RIIIQEE0WWWWWWWW";

    public static final InstructionEncoding ANDI_TO_SR_ENCODING =
            InstructionEncoding.of("0000001001111100","vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding TRAP_ENCODING = InstructionEncoding.of("010011100100vvvv");

    public static final InstructionEncoding RTE_ENCODING = InstructionEncoding.of( "0100111001110011");

    public static final InstructionEncoding JMP_INDIRECT_ENCODING = InstructionEncoding.of( "0100111011mmmsss");
    public static final InstructionEncoding JMP_SHORT_ENCODING = InstructionEncoding.of(    "0100111011mmmsss","vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding JMP_LONG_ENCODING = InstructionEncoding.of(     "0100111011mmmsss","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding MOVE_BYTE_ENCODING = InstructionEncoding.of("0001DDDMMMmmmsss");
    public static final InstructionEncoding MOVE_WORD_ENCODING = InstructionEncoding.of("0011DDDMMMmmmsss");
    public static final InstructionEncoding MOVE_LONG_ENCODING = InstructionEncoding.of("0010DDDMMMmmmsss");

    public static final InstructionEncoding MOVEQ_ENCODING = InstructionEncoding.of("0111DDD0vvvvvvvv");

    public static final InstructionEncoding LEA_LONG_ENCODING = InstructionEncoding.of("0100DDD111mmmsss", "vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding LEA_WORD_ENCODING = InstructionEncoding.of("0100DDD111mmmsss", "vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding MOVE_AX_TO_USP_ENCODING = InstructionEncoding.of("0100111001100sss");

    public static final InstructionEncoding MOVE_USP_TO_AX_ENCODING = InstructionEncoding.of("0100111001101DDD");

    public static final InstructionEncoding ILLEGAL_ENCODING = InstructionEncoding.of( "0100101011111100");

    public static final InstructionEncoding EXG_ENCODING = InstructionEncoding.of("1100kkk1ooooolll");

    public static final InstructionEncoding NOP_ENCODING = InstructionEncoding.of("0100111001110001");

    public static final InstructionEncoding DBCC_ENCODING = InstructionEncoding.of( "0101cccc11001sss","CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding BCC_8BIT_ENCODING =
            InstructionEncoding.of(  "0110ccccCCCCCCCC");

    public static final InstructionEncoding BCC_16BIT_ENCODING = InstructionEncoding.of( "0110cccc00000000","CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding BCC_32BIT_ENCODING = InstructionEncoding.of( "0110cccc11111111",
            "CCCCCCCC_CCCCCCCC_CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding MOVEA_WORD_ENCODING = InstructionEncoding.of(  "0011DDD001mmmsss");
    public static final InstructionEncoding MOVEA_LONG_ENCODING = InstructionEncoding.of(  "0010DDD001mmmsss");

    public static final InstructionEncoding SWAP_ENCODING = InstructionEncoding.of(  "0100100001000sss");

    public static final InstructionEncoding JSR_ENCODING = InstructionEncoding.of( "0100111010mmmsss");

    public static final InstructionEncoding RTS_ENCODING = InstructionEncoding.of( "0100111001110101");

    // TODO: 68020+ supports LONG displacement value as well
    public static final InstructionEncoding LINK_ENCODING = InstructionEncoding.of( "0100111001010sss",
                                                                                    "VVVVVVVV_VVVVVVVV");
    public static final InstructionEncoding UNLINK_ENCODING = InstructionEncoding.of( "0100111001011sss");

    public static final InstructionEncoding RESET_ENCODING = InstructionEncoding.of( "0100111001110000");

    public static final InstructionEncoding RTR_ENCODING = InstructionEncoding.of( "0100111001110111");

    public static final InstructionEncoding PEA_ENCODING = InstructionEncoding.of( "0100100001mmmsss");

    public static final InstructionEncoding ROL_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv1SS011DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ));
    public static final InstructionEncoding LSL_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv1SS001DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ));
    public static final InstructionEncoding ASL_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv1SS000DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ));

    public static final InstructionEncoding ROR_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv0SS011DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ) );
    public static final InstructionEncoding LSR_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv0SS001DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ) );
    public static final InstructionEncoding ASR_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv0SS000DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ) );

    public static final InstructionEncoding ROL_MEMORY_ENCODING = InstructionEncoding.of(    "1110011111mmmsss");
    public static final InstructionEncoding LSL_MEMORY_ENCODING = InstructionEncoding.of(    "1110001111mmmsss");
    public static final InstructionEncoding ASL_MEMORY_ENCODING = InstructionEncoding.of(    "1110000111mmmsss");

    public static final InstructionEncoding ROR_MEMORY_ENCODING = InstructionEncoding.of(    "1110011011mmmsss");
    public static final InstructionEncoding LSR_MEMORY_ENCODING = InstructionEncoding.of(    "1110001011mmmsss");
    public static final InstructionEncoding ASR_MEMORY_ENCODING = InstructionEncoding.of(    "1110000011mmmsss");

    public static final InstructionEncoding ROL_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss1SS111VVV");
    public static final InstructionEncoding LSL_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss1SS101VVV");
    public static final InstructionEncoding ASL_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss1SS100VVV");

    public static final InstructionEncoding ROR_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss0SS111VVV");
    public static final InstructionEncoding LSR_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss0SS101VVV");
    public static final InstructionEncoding ASR_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss0SS100VVV");

    public static final InstructionEncoding NEG_ENCODING = InstructionEncoding.of( "01000100SSmmmsss");

    public static final InstructionEncoding EXTW_ENCODING =
            InstructionEncoding.of( "0100100010000sss"); // Byte -> Word

    public static final InstructionEncoding EXTL_ENCODING =
            InstructionEncoding.of( "0100100011000sss"); // Word -> Long

    public static final InstructionEncoding BTST_DYNAMIC_ENCODING = // BTST Dn,<ea>
            InstructionEncoding.of( "0000sss100MMMDDD");

    public static final InstructionEncoding BTST_STATIC_ENCODING = // BTST #xx,<ea>
            InstructionEncoding.of( "0000100000MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding BCLR_DYNAMIC_ENCODING = // BCLR Dn,<ea>
            InstructionEncoding.of( "0000sss110MMMDDD");

    public static final InstructionEncoding BCLR_STATIC_ENCODING = // BCLR #xx,<ea>
            InstructionEncoding.of( "0000100010MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding BSET_DYNAMIC_ENCODING = // BSET Dn,<ea>
            InstructionEncoding.of( "0000sss111MMMDDD");

    public static final InstructionEncoding BSET_STATIC_ENCODING = // BSET #xx,<ea>
            InstructionEncoding.of( "0000100011MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding BCHG_DYNAMIC_ENCODING = // BCHG Dn,<ea>
            InstructionEncoding.of( "0000sss101MMMDDD");

    public static final InstructionEncoding BCHG_STATIC_ENCODING = // BCHG #xx,<ea>
            InstructionEncoding.of( "0000100001MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding CLR_ENCODING = // CLR <ea>
            InstructionEncoding.of( "01000010SSmmmsss");

    public static final InstructionEncoding TST_ENCODING = // TST.s <ea>
            InstructionEncoding.of( "01001010SSmmmsss");

    public static final InstructionEncoding TRAPV_ENCODING = // TRAPV
            InstructionEncoding.of( "0100111001110110");

    public static final InstructionEncoding NOT_ENCODING = // NOT
            InstructionEncoding.of( "01000110SSmmmsss");

    public static final InstructionEncoding CHK_ENCODING = // CHK <ea>,Dn
            InstructionEncoding.of( "0100DDDSS0mmmsss").decorateWith(fieldDecorator(Field.SIZE, originalValue ->
            {
                if ( originalValue == OperandSize.WORD.bits) {
                    return 0b11;
                }
                if ( originalValue == OperandSize.LONG.bits ) {
                    return 0b10;
                }
                throw new RuntimeException("Unhandled size bit-pattern: %"+Integer.toBinaryString(originalValue ) );
            }));

    public static final IdentityHashMap<InstructionEncoding,Instruction> ALL_ENCODINGS = new IdentityHashMap<>()
    {{
        put(ANDI_TO_SR_ENCODING,AND);
        put(JMP_INDIRECT_ENCODING,JMP);
        put(JMP_SHORT_ENCODING,JMP);
        put(JMP_LONG_ENCODING,JMP);
        put(TRAP_ENCODING,TRAP);
        put(RTE_ENCODING,RTE);
        put(MOVE_BYTE_ENCODING,MOVE);
        put(MOVE_WORD_ENCODING,MOVE);
        put(MOVE_LONG_ENCODING,MOVE);
        put(MOVEQ_ENCODING,MOVEQ);
        put(LEA_LONG_ENCODING,LEA);
        put(LEA_WORD_ENCODING,LEA);
        put(MOVE_AX_TO_USP_ENCODING,MOVE);
        put(MOVE_USP_TO_AX_ENCODING,MOVE);
        put(ILLEGAL_ENCODING,ILLEGAL);
        put(EXG_ENCODING,EXG);
        put(NOP_ENCODING,NOP);
        put(DBCC_ENCODING,DBCC);
        put(BCC_8BIT_ENCODING,BCC);
        put(BCC_16BIT_ENCODING,BCC);
        put(BCC_32BIT_ENCODING,BCC);
        put(MOVEA_LONG_ENCODING,MOVEA);
        put(MOVEA_WORD_ENCODING,MOVEA);
        put(JSR_ENCODING,JSR);
        put(RTS_ENCODING,RTS);
        put(LINK_ENCODING,LINK);
        put(UNLINK_ENCODING,UNLK);
        put(RESET_ENCODING,RESET);
        put(RTR_ENCODING,RTR);
        put(PEA_ENCODING,PEA);
        put(NEG_ENCODING,NEG);
        put(ROL_REGISTER_ENCODING,ROL);
        put(ROR_REGISTER_ENCODING,ROR);
        put(ROL_IMMEDIATE_ENCODING,ROL);
        put(ROR_IMMEDIATE_ENCODING,ROR);
        put(ROL_MEMORY_ENCODING,ROL);
        put(ROR_MEMORY_ENCODING,ROR);

        put(LSL_REGISTER_ENCODING,LSL);
        put(LSR_REGISTER_ENCODING,LSR);
        put(LSL_IMMEDIATE_ENCODING,LSL);
        put(LSR_IMMEDIATE_ENCODING,LSR);
        put(LSL_MEMORY_ENCODING,LSL);
        put(LSR_MEMORY_ENCODING,LSR);

        put(ASL_REGISTER_ENCODING,ASL);
        put(ASR_REGISTER_ENCODING,ASR);
        put(ASL_IMMEDIATE_ENCODING,ASL);
        put(ASR_IMMEDIATE_ENCODING,ASR);
        put(ASL_MEMORY_ENCODING,ASL);
        put(ASR_MEMORY_ENCODING,ASR);

        put(EXTW_ENCODING,EXT);
        put(EXTL_ENCODING,EXT);
        put(BTST_DYNAMIC_ENCODING,BTST);
        put(BTST_STATIC_ENCODING,BTST);
        put(BCLR_DYNAMIC_ENCODING,BCLR);
        put(BCLR_STATIC_ENCODING,BCLR);
        put(BSET_DYNAMIC_ENCODING,BSET);
        put(BSET_STATIC_ENCODING,BSET);
        put(BCHG_DYNAMIC_ENCODING,BCHG);
        put(BCHG_STATIC_ENCODING,BCHG);
        put(CLR_ENCODING,CLR);
        put(TST_ENCODING,TST);
        put(TRAPV_ENCODING,TRAPV);
        put(NOT_ENCODING,NOT);
        put(CHK_ENCODING,CHK);
    }};
}