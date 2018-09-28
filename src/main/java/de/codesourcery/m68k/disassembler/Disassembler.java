package de.codesourcery.m68k.disassembler;

import de.codesourcery.m68k.emulator.memory.Memory;
import de.codesourcery.m68k.assembler.arch.AddressingMode;
import de.codesourcery.m68k.assembler.arch.Condition;
import de.codesourcery.m68k.assembler.arch.Instruction;
import de.codesourcery.m68k.assembler.arch.InstructionEncoding;
import de.codesourcery.m68k.emulator.CPU;
import de.codesourcery.m68k.utils.Misc;
import de.codesourcery.m68k.utils.OpcodeFileReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;

public class Disassembler
{
    private static final Logger LOG = LogManager.getLogger( Disassembler.class.getName() );

    private static final boolean DEBUG = false;

    public static final class FunctionDescription
    {
        public final String name;
        public final int offset;
        public final boolean isPublic;
        public final String signature;

        public FunctionDescription(String name, int offset, boolean isPublic, String signature)
        {
            this.name = name;
            this.offset = offset;
            this.isPublic = isPublic;
            this.signature = signature;
        }

        @Override
        public String toString()
        {
            return name+"("+offset+") , public = "+isPublic+", signature = "+signature;
        }
    }

    private final Memory memory;

    private LineConsumer lineConsumer;

    private final StringBuilder textBuffer = new StringBuilder();
    private final StringBuilder asciiBuffer = new StringBuilder();
    private final StringBuilder commentsBuffer = new StringBuilder();
    private final StringBuilder hexBuffer = new StringBuilder();

    private final Line currentLine = new Line();

    private final Instruction[] opcodeMap = new Instruction[65536];

    private boolean dumpHex;

    private boolean resolveRelativeOffsets;

    private IChipRegisterResolver chipRegisterResolver = null;
    private IIndirectCallResolver indirectCallResolver = null;

    private boolean verboseRegisterDescriptions;

    private int pc;

    /**
     * Used when attempting to resolve 'JSR $XXXXX(An)' library calls.
     */
    public interface IIndirectCallResolver
    {
        /**
         * Resolve library function.
         *
         * @param addressRegister
         * @param offset
         * @return Function name or <code>null</code> if resolving failed
         */
        FunctionDescription resolve(int addressRegister, int offset);
    }

    /**
     * Used to resolve absolute and address-register-indirect with displacement
     * operands.
     */
    public interface IChipRegisterResolver
    {
        /**
         * Try to resolve chip register name for an indirect memory address.
         *
         * @param addressRegister
         * @param offset
         * @return register name or <code>null</code> if the address did not belong to a valid chip register
         */
        RegisterDescription resolve(int addressRegister,int offset);

        /**
         * Try to resolve chip register name for a direct memory access.
         *
         * @param address
         * @return register name or <code>null</code> if the address did not belong to a valid chip register
         */
        RegisterDescription resolve(int address);
    }

    public static final class Line
    {
        public Object data; // field is not used by the Disassembler, just there for client code to store additional data
        public int pc;
        public Instruction instruction;
        public String text;
        public String hex;
        public String ascii;
        public String comments;

        public Line createCopy()
        {
            final Line result = new Line();
            result.pc = pc;
            result.instruction = instruction;
            result.text = text;
            result.hex = hex;
            result.ascii = ascii;
            result.comments = comments;
            result.data = data;
            return result;
        }

        public boolean isData() {
            return instruction == null;
        }

        @Override
        public String toString()
        {
            final boolean needsPadding = hex != null || comments != null;
            final String textPadded;
            if ( needsPadding )
            {
                textPadded = StringUtils.rightPad(text, 30, ' ')+" ; ";
            } else {
                textPadded = text;
            }
            return StringUtils.leftPad(Integer.toHexString(pc),8,'0')+
                    ": "+textPadded+
                    (hex == null ? "" : " "+hex ) +
                    (ascii == null ? "" : " "+ascii) +
                    (comments == null ? "" : " "+comments);
        }
    }

    private static final class EndOfMemoryAccess extends RuntimeException {
    }

    public Disassembler(Memory memory) {
        this.memory = memory;
        try
        {
            initializeOpcodeMap();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public interface LineConsumer
    {
        boolean stop(int pc);

        void consume(Line line);
    }

    public void disassemble(int startAddress, LineConsumer lineConsumer)
    {
        this.pc = startAddress;
        this.lineConsumer = lineConsumer;
        while ( true )
        {
            if ( lineConsumer.stop( pc ) ) {
                return;
            }

            textBuffer.setLength(0);
            hexBuffer.setLength(0);
            asciiBuffer.setLength(0);
            commentsBuffer.setLength(0);

            try {
                currentLine.pc = pc;
                currentLine.instruction = null;
                disassemble();
            }
            catch(EndOfMemoryAccess e)
            {
                return;
            }
            currentLine.text = textBuffer.toString();
            currentLine.comments = commentsBuffer.length() > 0 ? commentsBuffer.toString() : null;
            if ( dumpHex )
            {
                currentLine.ascii = asciiBuffer.length() > 0 ? asciiBuffer.toString() : null;
                currentLine.hex = hexBuffer.length() > 0 ? hexBuffer.toString() : null;
            }
            lineConsumer.consume(currentLine);
        }
    }

    public String disassemble(int startAddress,int bytes)
    {
        final StringBuilder buffer = new StringBuilder();

        disassemble( startAddress, new LineConsumer() {

            @Override
            public boolean stop( int pc)
            {
                return pc > startAddress+bytes;
            }

            @Override
            public void consume(Line line)
            {
                if ( buffer.length() > 0 ) {
                    buffer.append("\n");
                }
                buffer.append(line);
            }
        });
        return buffer.toString();
    }

    private void disassemble() throws EndOfMemoryAccess
    {
        if ( DEBUG )
        {
            LOG.info( "Disassembling at " + Misc.hex(pc) );
        }

        final int insnWord = readWord();

        final Instruction instruction = opcodeMap[insnWord&0xffff];
        try
        {
            currentLine.instruction = instruction;
            disassemble(instruction, insnWord);
        }
        catch(Exception e)
        {
            if ( e instanceof EndOfMemoryAccess == false )
            {
                e.printStackTrace();
            }
            textBuffer.append("dc.w ").append( Misc.hex(insnWord) );
            commentsBuffer.append( Misc.binary16Bit(insnWord) ).append(" , caught "+e.getMessage());
            pc = currentLine.pc+2;
        }
    }

    private static int getMaskLength(InstructionEncoding encoding) {

        int len = 0;
        final String word0 = encoding.getPatterns()[0];
        final int wlen = word0.length();
        for ( int i = 0 ; i < wlen ; i++) {
            final char c = word0.charAt(i);
            if ( c == '0' || c == '1' ) {
                len++;
            }
        }
        return len;
    }

    /**
     *
     * @param sizeBits size bits, already shifted so that b00 = .b / b01 = .w and b10 = .l
     */
    private void appendOperandSize(int sizeBits)
    {
        switch(sizeBits){
            case 0: append(".b "); break;
            case 1: append(".w "); break;
            case 2: append(".l "); break;
            default:
                // TODO: Print this as invalid instruction instead...
                throw new RuntimeException("Unreachable code reached, size bits: "+sizeBits);
        }
    }

    private void disassemble(Instruction insn,int insnWord)
    {
        switch( insn )
        {
            case STOP:
                appendln("stop ");
                int flags = readWord();
                append("#").appendHex16Bit(flags);
                return;
            case CHK:
                // 0100DDDSS0mmmsss
                int sizeBits = (insnWord & 0b110000000) >>> 7;
                int operandSize; // non-standard operand size encoding...
                switch( sizeBits )
                {
                    case 0b11:
                        operandSize = 2;
                        appendln("chk.w ");
                        break;
                    case 0b10:
                        operandSize = 4;
                        appendln("chk.l ");
                        break;
                    default:
                        illegalOperation(insnWord);
                        return;
                }
                int eaMode     = (insnWord & 0b111000) >>> 3;
                int eaRegister = (insnWord & 0b000111);
                decodeOperand(operandSize,eaMode,eaRegister);
                append(",");
                int regNum = (insnWord & 0b111000000000) >>> 9;
                appendDataRegister(regNum );
                return;
            case NOT:
                appendln("not");
                sizeBits = (insnWord & 0b11000000) >>> 6;
                appendOperandSize(sizeBits);
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);
                decodeOperand(1<<sizeBits,eaMode,eaRegister);
                return;
            case TAS:
                appendln("tas ");
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);
                decodeOperand(1,eaMode,eaRegister);
                return;
            case TRAPV:
                appendln("trapv");
                return;
            case TST:
                appendln("tst");
                sizeBits = (insnWord & 0b11000000) >>> 6;
                appendOperandSize(sizeBits);
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);
                decodeOperand(1<<sizeBits,eaMode,eaRegister);
                return;
            case CLR:
                appendln("clr");
                sizeBits = (insnWord & 0b11000000) >>> 6;
                appendOperandSize(sizeBits);
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);
                decodeOperand(1<<sizeBits,eaMode,eaRegister);
                return;
            case SWAP:
                appendln("swap.w ");
                appendDataRegister((insnWord&0b111) );
                return;
            case MOVEM:
                appendln("movem");
                if ( ( insnWord & 1<<6) == 0) {
                    append(".w ");
                    operandSize = 2;
                } else {
                    append(".l ");
                    operandSize = 4;
                }
                eaMode = (insnWord & 0b111000)>>>3;
                eaRegister = insnWord & 0b111;
                int registerMask = readWord();

                if ( (insnWord & 0b1111111110000000) == 0b0100110010000000) {
                    // MOVEM <ea>,<register list>
                    decodeOperand(operandSize,eaMode,eaRegister);
                    append(",");
                    printRegisterList(registerMask);
                    return;
                }
                // MOVEM <register list>,<ea>
                final boolean isPredecrement = eaMode == AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT.eaModeField;
                if ( isPredecrement ) {
                    // printRegisterList() always assumes bitmask bits in A7-A0|D7-D0 order
                    registerMask = Misc.reverseWord(registerMask);
                }
                printRegisterList(registerMask);
                append(",");
                decodeOperand(operandSize,eaMode,eaRegister);
                return;
            case BCHG:
                appendln("bchg ");
                if ( (insnWord & 0b1111000111000000) == 0b0000000101000000) {
                    // BCHG Dn,<ea>
                    printBitOp( insnWord,true );
                    return;
                }
                if ( (insnWord & 0b1111111111000000) == 0b0000100001000000) {
                    // BCHG #xx,<ea>
                    printBitOp( insnWord,false);
                    return;
                }
            case BSET:
                appendln("bset ");
                if ( (insnWord & 0b1111000111000000) == 0b0000000111000000) {
                    // BSET Dn,<ea>
                    printBitOp( insnWord,true );
                    return;
                }
                if ( (insnWord & 0b1111111111000000) == 0b0000100011000000) {
                    // BSET #xx,<ea>
                    printBitOp( insnWord,false );
                    return;
                }
                break;
            case BCLR:
                appendln("bclr ");
                if ( (insnWord & 0b1111000111000000) == 0b0000000110000000) {
                    // BCLR Dn,<ea>
                    printBitOp( insnWord,true );
                    return;
                }
                if ( (insnWord & 0b1111111111000000) == 0b0000100010000000) {
                    // BCLR #xx,<ea>
                    printBitOp( insnWord,false );
                    return;
                }
                break;
            case BTST:
                appendln("btst ");
                if ( (insnWord & 0b1111000111000000) == 0b0000000100000000) {
                    // BTST Dn,<ea>
                    printBitOp( insnWord,true );
                    return;
                }
                if ( (insnWord & 0b1111111111000000) == 0b0000100000000000) {
                    // BTST #xx,<ea>
                    printBitOp( insnWord,false);
                    return;
                }
                break;
            case EXT:
                appendln("ext");

                if ( (insnWord & 0b1111111111111000) == 0b0100100010000000) {
                    // EXT Byte -> Word
                    append(".w ").appendDataRegister( insnWord & 0b111 );
                    return;
                }
                if ( (insnWord & 0b1111111111111000) == 0b0100100011000000) {
                    // EXT Word -> Byte
                    append(".l ").appendDataRegister( insnWord & 0b111 );
                    return;
                }
                // TODO: Print as illegal instruction instead of throwing RE
                throw new RuntimeException("Unreachable code reached");
            case ROXL:
                appendln("roxl");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000100010000 ) {
                    // ROXL_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000 ) == 0b1110010111000000 ) {
                    // ROXL_MEMORY
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // ROXL register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case ROXR:
                appendln("roxr");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000000010000 ) {
                    // ROXR_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000 ) == 0b1110010011000000 ) {
                    // ROXR_MEMORY_ENCODING
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // ROXR register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case ASL:
                appendln("asl");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000100000000 )
                {
                    // ASL_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000 ) == 0b1110000111000000 ) {
                    // ASL_MEMORY
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // ASL register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case ASR:
                appendln("asr");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000000000000 ) {
                    // ASR_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000 ) == 0b1110000011000000 ) {
                    // ASR_MEMORY_ENCODING
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // ASR register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case LSL:
                appendln("lsl");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000100101000 )
                {
                    // LSL_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000) == 0b1110011111000000 ) {
                    // LSL_MEMORY
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // LSL register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case LSR:
                appendln("lsr");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000000011000 ) {
                    // LSR_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000) == 0b1110011011000000 ) {
                    // LSR_MEMORY
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // LSR register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case ROL:
                appendln("rol");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000100011000 )
                {
                    // ROL_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000) == 0b1110011111000000 ) {
                    // ROL_MEMORY
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // ROL register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case ROR:
                appendln("ror");
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000000011000 ) {
                    // ROR_IMMEDIATE
                    printRotateOperands(insnWord, CPU.RotateOperandMode.IMMEDIATE);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000) == 0b1110011011000000 ) {
                    // ROR_MEMORY
                    printRotateOperands(insnWord,CPU.RotateOperandMode.MEMORY);
                    return;
                }
                // ROR register
                printRotateOperands(insnWord,CPU.RotateOperandMode.REGISTER);
                return;
            case NEG:
                appendln("neg");
                sizeBits = (insnWord & 0b11000000) >>> 6;
                operandSize = 1<<sizeBits;
                appendOperandSize(sizeBits);
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);
                decodeOperand(operandSize,eaMode,eaRegister);
                return;
            case PEA:
                appendln("pea ");
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);
                decodeOperand(4,eaMode,eaRegister);
                return;
            case RTR:
                appendln("rtr");
                return;
            case RESET:
                appendln("reset");
                return;
            case UNLK:
                appendln("unlk ");
                eaRegister = (insnWord & 0b000111);
                append("a").append(eaRegister);
                return;
            case LINK:
                appendln("link ");
                eaRegister = (insnWord & 0b000111);
                int displacement = readWord();
                append("a").append(eaRegister).append(",#").appendHex16Bit(displacement);
                return;
            case RTS:
                appendln("rts");
                return;
            case JSR:
                appendln("jsr ");
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);

                decodeOperand(4,eaMode,eaRegister);
                return;
            case MOVEA:
                operandSize = 2;
                if ( (insnWord & 0b0011_0000_0000_0000) == 0b0010_0000_0000_0000) {
                    operandSize = 4;
                }
                appendln("movea").append( operandSize == 2 ? ".w" : ".l" ).append(" ");
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);

                decodeOperand(operandSize,eaMode,eaRegister);
                regNum = (insnWord & 0b0000111000000000) >> 9;
                append(",a").append( regNum );
                return;
            case JMP:
                eaMode     = (insnWord & 0b111000) >>> 3;
                eaRegister = (insnWord & 0b000111);
                appendln( "jmp " );
                // JMP encodings only differ in their eaRegister and eaMode values

                if ( eaMode == 0b111 && eaRegister == 0b000 )
                {
                    // JMP_SHORT_ENCODING
                    int adr = readWord();
                    append( Misc.hex(adr) );
                }
                else if ( eaMode == 0b111 && eaRegister == 0b001 )
                {
                    // JMP_LONG_ENCODING
                    int adr = readLong();
                    append( Misc.hex(adr) );
                } else {
                    // JMP_INDIRECT_ENCODING
                    decodeOperand(0,eaMode,eaRegister);
                }
                return;
            case EOR:
                appendln("eor");
                // 11000000
                sizeBits = (insnWord & 0b11000000) >> 6;
                switch( sizeBits ) {
                    case 0b00:
                        append(".b "); break;
                    case 0b01:
                        append(".w "); break;
                    case 0b10:
                        append(".l "); break;
                    default:
                        throw new RuntimeException("Invalid size");
                }
                appendDataRegister((insnWord & 0b0000111000000000) >> 9);
                append(",");
                decodeOperand(1<<sizeBits, (insnWord&0b111000)>>3, insnWord&0b111);
                return;
            case NEGX:
                appendln("negx");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                decodeOperand(1<<sizeBits,(insnWord&0b111000)>>3 , insnWord&0b111);
                return;
            case CMP:
                appendln("cmp");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                decodeOperand(1<<sizeBits,(insnWord&0b111000)>>3 , insnWord&0b111);
                append(",").appendDataRegister((insnWord&0b111000000000)>>9);
                return;
            case SUBX:
                appendln("subx");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                int srcReg = insnWord & 0b111;
                int dstReg = (insnWord & 0b111000000000) >> 9;
                final boolean isDataRegister = (insnWord & 1<<3) == 0;
                if ( isDataRegister ) {
                    appendDataRegister(srcReg).append(",").appendDataRegister(dstReg);
                } else {
                    append("-(").appendAddressRegister(srcReg).append("),-(").appendAddressRegister(dstReg).append(")");
                }
                return;
            case SUB:
                appendln("sub");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                if ( (insnWord & 1<<8) == 0 )
                {
                    // <ea> + Dn -> Dn
                    decodeOperand(1<<sizeBits,(insnWord&0b111000)>>3 , insnWord&0b111);
                    append(",").appendDataRegister((insnWord&0b111000000000)>>9);
                }
                else {
                    // Dn + <ea> -> <ea>
                    appendDataRegister((insnWord&0b111000000000)>>9);
                    append(",");
                    decodeOperand(1<<sizeBits,(insnWord&0b111000)>>3 , insnWord&0b111);
                }
                return;
            case ADD:
                appendln("add");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                if ( (insnWord & 1<<8) == 0 )
                {
                    // <ea> + Dn -> Dn
                    decodeOperand(1<<sizeBits,(insnWord&0b111000)>>3 , insnWord&0b111);
                    append(",").appendDataRegister((insnWord&0b111000000000)>>9);
                }
                else {
                    // Dn + <ea> -> <ea>
                    appendDataRegister((insnWord&0b111000000000)>>9);
                    append(",");
                    decodeOperand(1<<sizeBits,(insnWord&0b111000)>>3 , insnWord&0b111);
                }
                return;
            case ADDX:
                appendln("addx");
                appendOperandSize( (insnWord & 0b11000000) >> 6 );
                final boolean isDataReg = (insnWord & 1<<3) == 0;
                srcReg = (insnWord&0b111);
                dstReg = (insnWord&0b111000000000) >> 9;
                if ( isDataReg ) {
                    appendDataRegister( srcReg ).append(",").appendDataRegister( dstReg );
                } else {
                    append("-(").appendAddressRegister( srcReg ).append(")");
                    append(",");
                    append("-(").appendAddressRegister( dstReg ).append(")");
                }
                return;
            case CMPM:
                appendln("cmpm");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                append("(");
                appendAddressRegister( (insnWord&0b111) );
                append(")+,(");
                appendAddressRegister( (insnWord&0b111000000000)>>9 );
                append(")+");
                return;
            case CMPI:
                appendln("cmpi");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                int value;
                switch(sizeBits)
                {
                    case 0b00:
                        value = readWord()&0xff;
                        break;
                    case 0b01:
                        value = readWord()&0xffff;
                        break;
                    case 0b10:
                        value = readLong();
                        break;
                    default:
                        throw new RuntimeException("Invalid size");
                }
                append("#").appendHex(value).append(",");
                decodeOperand(1<<sizeBits, (insnWord&0b111000)>>3, insnWord&0b111);
                return;
            case SUBI:
                appendln("subi");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                switch(sizeBits)
                {
                    case 0b00:
                        value = readWord()&0xff;
                        break;
                    case 0b01:
                        value = readWord()&0xffff;
                        break;
                    case 0b10:
                        value = readLong();
                        break;
                    default:
                        throw new RuntimeException("Invalid size");
                }
                append("#").appendHex(value).append(",");
                decodeOperand(1<<sizeBits, (insnWord&0b111000)>>3, insnWord&0b111);
                return;
            case ADDI:
                appendln("addi");
                sizeBits = (insnWord & 0b11000000) >> 6;
                appendOperandSize(sizeBits);
                switch(sizeBits)
                {
                    case 0b00:
                        value = readWord()&0xff;
                        break;
                    case 0b01:
                        value = readWord()&0xffff;
                        break;
                    case 0b10:
                        value = readLong();
                        break;
                    default:
                        throw new RuntimeException("Invalid size");
                }
                append("#").appendHex(value).append(",");
                decodeOperand(1<<sizeBits, (insnWord&0b111000)>>3, insnWord&0b111);
                return;
            case CMPA:
                appendln("cmpa");
                if ((insnWord&1<<8) == 0 ) {
                    append(".w ");
                } else {
                    append(".l ");
                }
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                regNum = (insnWord&0b111000000000) >> 9;
                append(",").appendAddressRegister(regNum);
                return;
            case SUBA:
                appendln("suba");
                if ((insnWord&1<<8) == 0 ) {
                    append(".w ");
                } else {
                    append(".l ");
                }
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                regNum = (insnWord&0b111000000000) >> 9;
                append(",").appendAddressRegister(regNum);
                return;
            case ADDA:
                appendln("adda");
                final int opSize;
                if ((insnWord&1<<8) == 0 ) {
                    append(".w ");
                    opSize = 2;
                } else {
                    append(".l ");
                    opSize = 4;
                }
                decodeOperand(opSize, (insnWord&0b111000)>>3, insnWord&0b111);
                regNum = (insnWord&0b111000000000) >> 9;
                append(",").appendAddressRegister(regNum);
                return;
            case SUBQ:
                appendln("subq");
                appendOperandSize((insnWord&0b11000000) >> 6);
                value = (insnWord & 0b111000000000) >> 9;
                append("#").append(value == 0 ? 8 : value ).append(",");
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                return;
            case ADDQ:
                appendln("addq");
                appendOperandSize((insnWord&0b11000000) >> 6);
                value = (insnWord & 0b111000000000) >> 9;
                append("#").append(value == 0 ? 8 : value ).append(",");
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                return;
            case MULS:
                appendln("muls.w ");
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                dstReg = (insnWord&0b111000000000) >> 9;
                append(",").appendDataRegister(dstReg);
                return;
            case MULU:
                appendln("mulu.w ");
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                dstReg = (insnWord&0b111000000000) >> 9;
                append(",").appendDataRegister(dstReg);
                return;
            case DIVS:
                appendln("divs.w ");
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                dstReg = (insnWord&0b111000000000) >> 9;
                append(",").appendDataRegister(dstReg);
                return;
            case DIVU:
                appendln("divu.w ");
                decodeOperand(2, (insnWord&0b111000)>>3, insnWord&0b111);
                dstReg = (insnWord&0b111000000000) >> 9;
                append(",").appendDataRegister(dstReg);
                return;
            case EORI:
                if ( insnWord == 0b0000101000111100)
                {
                    appendln("eori.b #");
                    appendHex(readWord() & 0xff).append(",ccr");
                    return;
                }
                if ( insnWord == 0b0000101001111100)
                {
                    appendln("eori.w #");
                    appendHex(readWord() & 0xffff).append(",sr");
                    return;
                }

                appendln("eori");
                decodeImmediateBinaryLogicalOp(insnWord);
                return;
            case ORI:
                if ( matches(insnWord, Instruction.ORI_TO_SR_ENCODING) ) {
                    final int word = readWord() & 0xffff;
                    appendln("ori.w #").append( Misc.hex(word) ).append(",sr");
                    return;
                }
                if ( matches(insnWord, Instruction.ORI_TO_CCR_ENCODING) ) {
                    final int word = readWord() & 0xff;
                    appendln("ori.b #").append( Misc.hex(word) ).append(",ccr");
                    return;
                }
                appendln("ori");
                decodeImmediateBinaryLogicalOp(insnWord);
                return;
            case OR:
                if ( decodeRegularBinaryLogicalOp("or", insnWord,Instruction.OR_SRC_EA_ENCODING,Instruction.OR_DST_EA_ENCODING) )
                {
                    return;
                }
                break;
            case AND:
                if ( matches(insnWord, Instruction.ANDI_TO_SR_ENCODING) ) {
                    final int word = readWord() & 0xffff;
                    appendln("andi.w #").append( Misc.hex(word) ).append(",sr");
                    return;
                }
                if ( matches(insnWord, Instruction.ANDI_TO_CCR_ENCODING) ) {
                    final int word = readWord() & 0xff;
                    appendln("andi.b #").append( Misc.hex(word) ).append(",ccr");
                    return;
                }
                if ( matches(insnWord, Instruction.ANDI_BYTE_ENCODING) ) {
                    final int word = readWord() & 0xff;
                    appendln("andi.b #").append( Misc.hex(word) ).append(",");
                    decodeOperand(1,(insnWord&0b111)>>>3,insnWord&0b111);
                    return;
                }

                // TODO: Make ANDI encoding similar to ORI/EORI and use decodeImmediateBinaryLogicalOp(insnWord) here...
                if ( matches(insnWord, Instruction.ANDI_WORD_ENCODING) ) {
                    final int word = readWord() & 0xffff;
                    appendln("andi.w #").append( Misc.hex(word) ).append(",");
                    decodeOperand(1,(insnWord&0b111)>>>3,insnWord&0b111);
                    return;
                }
                if ( matches(insnWord, Instruction.ANDI_LONG_ENCODING) ) {
                    final int word = readLong();
                    appendln("andi.l #").append( Misc.hex(word) ).append(",");
                    decodeOperand(1,(insnWord&0b111)>>>3,insnWord&0b111);
                    return;
                }
                if ( decodeRegularBinaryLogicalOp("and", insnWord,Instruction.AND_SRC_EA_ENCODING,Instruction.AND_DST_EA_ENCODING) )
                {
                    return;
                }
                break;
            case TRAP:
                final int no = insnWord & 0b1111;
                appendln("trap #").append(no);
                return;
            case RTE:
                appendln("rte");
                return;
            case ILLEGAL:
                appendln("illegal");
                return;
            // Scc
            case SCC:
                Condition cond = Condition.fromBits( (insnWord & 0b0000111100000000) >>> 8 );
                if ( cond == Condition.BRT) {
                    appendln("st");
                }
                else if ( cond == Condition.BSR) {
                    appendln("sf");
                }
                else
                {
                    appendln("s" + cond.suffix.toLowerCase());
                }
                append(" ");
                eaMode = (insnWord & 0b111000)>>3;
                eaRegister = (insnWord & 0b111);
                decodeOperand(1,eaMode,eaRegister);
                return;
            // DBcc
            case DBT:
            case DBRA:
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
                cond = Condition.fromBits( (insnWord & 0b0000111100000000) >>> 8 );
                int register = insnWord & 0b111;
                int offset = readWord();
                int destinationAdress = currentLine.pc + 2 + offset;

                if ( cond == Condition.BSR ) {
                    appendln("dbra");
                } else {
                    appendln("db").append(cond.suffix.toLowerCase());
                }
                append(" d").append( register ).append(",").append(Misc.hex(destinationAdress));
                return;
            // Bcc
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
                cond = Condition.fromBits( (insnWord & 0b0000111100000000 ) >>> 8 );
                String suffix = cond.suffix.toLowerCase();
                if ( cond == Condition.BRT) {
                    suffix = "ra";
                }
                appendln("b").append( suffix ).append(" ");
                switch( insnWord & 0xff ) {
                    case 0x00:
                        offset = readWord();
                        break;
                    case 0xff:
                        offset = readLong();
                        break;
                    default:
                        offset = ((insnWord & 0xff) <<24) >> 24;
                }
                append( Misc.hex( currentLine.pc + 2 + offset ) );
                return;
            // Misc
            case NOP:
                appendln("nop");
                return;
            case EXG:
                final int opMode = (insnWord & 0b0000000011111000) >>> 3;
                final int rX =   (insnWord & 0b0000111000000000) >>> 9;
                final int rY =   (insnWord & 0b0000000000000111);
                appendln("exg ");
                switch( opMode ) {
                    case 0b01000: // Dx <-> Dx
                        appendDataRegister(rX).append(",").appendDataRegister(rY);
                        return;
                    case 0b01001: // Ax <-> Ax
                        appendAddressRegister(rX).append(",").appendAddressRegister(rY);
                        return;
                    case 0b10001: // Ax <-> Dx
                        appendDataRegister(rX).append(",").appendAddressRegister(rY);
                        return;
                }
                return;
            case MOVEQ:
                value = insnWord & 0xff;
                value = (value <<24) >> 24;
                regNum = (insnWord & 0b0000111000000000) >>> 9;
                appendln("moveq #").append( value ).append(",").appendDataRegister(regNum);
                return;
            case MOVEP:
                appendln("movep");
                offset = readWord();
                int adrReg = insnWord & 0b111;
                int dataReg = (insnWord & 0b111000000000)>>>9;
                if ( ( insnWord & 0b1111000111111000 ) == 0b0000000101001000 ) {
                    // MOVEP_LONG_FROM_MEMORY_ENCODING
                    append(".l ").appendHex16Bit(offset).append("(").appendAddressRegister(adrReg).append("),")
                            .appendDataRegister(dataReg );
                    return;
                }

                if ( ( insnWord & 0b1111000111111000 ) == 0b0000000111001000 ) {
                    // MOVEP_LONG_TO_MEMORY_ENCODING
                    append(".l ").appendDataRegister(dataReg ).append(",")
                            .appendHex16Bit(offset).append("(").appendAddressRegister(adrReg).append(")");
                    return;
                }

                if ( ( insnWord & 0b1111000111111000 ) == 0b0000000100001000 ) {
                    // MOVEP_LONG_FROM_MEMORY_ENCODING
                    append(".w ").appendHex16Bit(offset).append("(").appendAddressRegister(adrReg).append("),")
                            .appendDataRegister(dataReg );
                    return;
                }

                if ( ( insnWord & 0b1111000111111000 ) == 0b0000000110001000 ) {
                    // MOVEP_WORD_TO_MEMORY_ENCODING
                    append(".w ").appendDataRegister(dataReg ).append(",")
                            .appendHex16Bit(offset).append("(").appendAddressRegister(adrReg).append(")");
                    return;
                }
                break;
            case MOVE:
                if ( matches(insnWord,Instruction.MOVE_AX_TO_USP_ENCODING ) )
                {
                    regNum = insnWord & 0b111;
                    appendln("move a"+regNum+",usp");
                    return;
                }
                if ( matches(insnWord,Instruction.MOVE_USP_TO_AX_ENCODING) ) {
                    regNum = insnWord & 0b111;
                    appendln("move usp,a"+regNum);
                    return;
                }
                if ( matches(insnWord,Instruction.MOVE_TO_CCR_ENCODING ) ) {
                    appendln("move.w ");
                    decodeOperand(2,(insnWord&0b111000)>>>3,insnWord&0b111);
                    append(",ccr");
                    return;
                }
                if ( matches(insnWord,Instruction.MOVE_TO_SR_ENCODING ) ) {
                    appendln("move.w ");
                    decodeOperand(2,(insnWord&0b111000)>>>3,insnWord&0b111);
                    append(",sr");
                    return;
                }

                if ( matches(insnWord,Instruction.MOVE_FROM_SR_ENCODING ) ) {
                    appendln("move.w ");
                    append("sr,");
                    decodeOperand(2,(insnWord&0b111000)>>>3,insnWord&0b111);
                    return;
                }

                switch( (insnWord & 0b1111000000000000) >>> 12 ) {
                    case 0b0001:
                        appendln("move.b ");
                        operandSize = 1;
                        break;
                    case 0b0011:
                        appendln("move.w ");
                        operandSize = 2;
                        break;
                    case 0b0010:
                        appendln("move.l ");
                        operandSize = 4;
                        break;
                    default:
                        illegalOperation( insnWord );
                        return;
                }
                decodeOperand( operandSize,(insnWord & 0b111000)>>>3,(insnWord & 0b111) );
                append(",");
                decodeOperand( operandSize,(insnWord & 0b111000000)>>>6,(insnWord & 0b111000000000) >>> 9 );
                return;
            case LEA:
                appendln("lea ");
                decodeOperand( 4,(insnWord&0b111000)>>>3,insnWord&0b111 );
                register = (insnWord & 0b0000111000000000) >>> 9;
                append(",").appendAddressRegister(register);
                return;
        }
        illegalOperation(insnWord);
    }

    /**
     *
     * @param mnemonic
     * @param insnWord
     * @param srcEaEncoding
     * @param dstEaEncoding
     * @return <code>true</code> if decoding was successful, otherwise false
     */
    private boolean decodeRegularBinaryLogicalOp(String mnemonic,int insnWord,InstructionEncoding srcEaEncoding,InstructionEncoding dstEaEncoding)
    {
        if ( matches(insnWord, srcEaEncoding) || matches(insnWord, dstEaEncoding ) ) {

            appendln(mnemonic);

            int sizeBits = (insnWord & 0b11000000) >>> 6;
            appendOperandSize(sizeBits);

            if ( matches(insnWord, srcEaEncoding) )
            {
                decodeOperand(1<<sizeBits, (insnWord & 0b111000) >>> 3, insnWord & 0b111);
            } else {
                int regNum = (insnWord & 0b111000000000) >>> 9;
                appendDataRegister(regNum);
            }
            append(",");
            if ( matches(insnWord, srcEaEncoding) )
            {
                int regNum = (insnWord & 0b111000000000) >>> 9;
                appendDataRegister(regNum);
            } else {
                decodeOperand(1<<sizeBits, (insnWord & 0b111000) >>> 3, insnWord & 0b111);
            }
            return true;
        }
        return false;
    }

    private void decodeImmediateBinaryLogicalOp(int insnWord)
    {
        int sizeBits = (insnWord & 0b11000000) >>> 6;
        switch(sizeBits)
        {
            case 0b00:
                int value = readWord() & 0xff;
                append(".b #").appendHex(value).append(",");
                break;
            case 0b01:
                value = readWord() & 0xffff;
                append(".w #").appendHex(value).append(",");
                break;
            case 0b10:
                value = readLong();
                append(".l #").appendHex(value).append(",");
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }
        decodeOperand(1<<sizeBits,(insnWord&0b111000)>>>3,insnWord&0b111);
    }

    // print MOVEM register list
    private void printRegisterList(int registerMask)
    {
        // Always assumes bitmask bits in A7-A0|D7-D0 order
        // print data registers first
        String list1 = printRegisterList("d",0,registerMask);
        String list2 = printRegisterList("a",8,registerMask);

        if ( list1.isEmpty() )
        {
            append(list2);
        }
        else
        {
            append(list1);
            if ( ! list2.isEmpty() ) {
                append("/");
                append(list2);
            }
        }
    }

    public static String printRegisterList(String registerPrefix,int startBit,int registerMask)
    {
        // Always assumes bitmask bits in A7-A0|D7-D0 order
        // print data registers first
        final StringBuilder buffer = new StringBuilder();
        int firstSetBit = -1;

        final int end = startBit+8;
        for ( int bit = 0,mask = 1<<startBit ; bit <= end ; bit++, mask <<= 1)
        {
            final boolean bitSet = (registerMask & mask) != 0;
            if ( bitSet && bit < end )
            {
                if ( firstSetBit == -1 ) {
                    firstSetBit = bit;
                }
            }
            else
            {
                if ( firstSetBit != -1 )
                {
                    if ( buffer.length() > 0 ) {
                        buffer.append("/");
                    }
                    if ( firstSetBit == bit-1 ) {
                        buffer.append(registerPrefix).append(firstSetBit);
                    } else
                    {
                        buffer.append(registerPrefix).append(firstSetBit).append("-").append(registerPrefix).append(bit - 1);
                    }
                    firstSetBit = -1;
                }
            }
        }
        return buffer.toString();
    }

    private void illegalOperation(int insnWord ) {
        appendln("<unknown ").append(Misc.binary16Bit(insnWord)).append(">");
    }

    private int readLong()
    {
        if ( lineConsumer.stop(pc + 4 ) ) {
            throw new EndOfMemoryAccess();
        }
        int result = memory.readLongNoSideEffects( pc );
        if ( dumpHex )
        {
            appendHexByte(result >> 24);
            appendHexByte(result >> 16);
            appendHexByte(result >> 8);
            appendHexByte(result);
        }
        pc+=4;
        return result;
    }

    private int readWord()
    {
        if ( lineConsumer.stop((pc + 2 ) ) ) {
            throw new EndOfMemoryAccess();
        }
        final int result = memory.readWordNoSideEffects( pc );
        if ( dumpHex )
        {
            appendHexByte(result >> 8);
            appendHexByte( result);
        }
        pc+=2;
        return result;
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private void appendHexByte(int value) {

        final char low = HEX_CHARS[ value & 0b1111 ];
        final char hi = HEX_CHARS[ (value & 0b11110000)>>4 ];
        hexBuffer.append(hi).append(low);
        if ( value >= 32 && value < 127)
        {
            asciiBuffer.append(value);
        } else {
            asciiBuffer.append('.');
        }
    }

    private Disassembler appendln(String s)
    {
        return append( s );
    }

    private Disassembler append(String s) {
        textBuffer.append( s );
        return this;
    }

    private Disassembler append(int value) {
        textBuffer.append( Integer.toString( value ) );
        return this;
    }

    private Disassembler appendDataRegister(int regNum) {
        return append("d").append(regNum);
    }

    private Disassembler appendAddressRegister(int regNum) {
        return append("a").append(regNum);
    }

    private Disassembler appendHex(int value) {
        textBuffer.append( "$").append( Integer.toHexString( value ) );
        return this;
    }

    private Disassembler appendHex16Bit(int value)
    {
        textBuffer.append( "$").append( Integer.toHexString( value & 0xffff ) );
        return this;
    }

    private void decodeOperand(int operandSizeInBytes,int eaMode, int eaRegister)
    {
        switch( eaMode )
        {
            case 0b000:
                // DATA_REGISTER_DIRECT;
                append("d").append(eaRegister);
                return;
            case 0b001:
                // ADDRESS_REGISTER_DIRECT;
                append("a").append(eaRegister);
                return;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b000:
                        /*
                         * MOVE (xxx).W,... (1 extra word).
                         * ABSOLUTE_SHORT_ADDRESSING(0b111,fixedValue(000),1 ),
                         */
                        int adr = memLoadWord(pc);
                        pc += 2;
                        append( Misc.hex(adr) );
                        return;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        adr = memLoadLong(pc);
                        pc += 4;
                        if ( chipRegisterResolver != null )
                        {
                            final RegisterDescription desc = chipRegisterResolver.resolve(adr);
                            if ( desc != null ) {
                                append( desc.name );
                                commentsBuffer.append(Misc.hex(adr));
                                if ( verboseRegisterDescriptions ) {
                                    commentsBuffer.append(" ").append( desc.description );
                                }
                            } else {
                                append(Misc.hex(adr));
                            }
                        }
                        else
                        {
                            append(Misc.hex(adr));
                        }
                        return;
                }
                // $$FALL-THROUGH$$
            default:
                decodeOperand2(operandSizeInBytes, eaMode, eaRegister);
        }
    }

    private void decodeOperand2(int operandSizeInBytes, int eaMode, int eaRegister)
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
                append("(a").append(eaRegister).append(")");
                return;
            case 0b011:
                // ADDRESS_REGISTER_INDIRECT_POST_INCREMENT;
                append("(a").append( eaRegister ).append(")+");
                return;
            case 0b100:
                // ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT;
                append("-(a").append( eaRegister ).append(")");
                return;
            case 0b101:
                // ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
                int offset = memLoadWord(pc);
                pc += 2; // skip displacement

                if ( chipRegisterResolver != null ) {
                    final RegisterDescription resolved = chipRegisterResolver.resolve(eaRegister, offset);
                    if ( resolved != null ) {
                        append( resolved.name ).append("(a").append( eaRegister ).append(")");
                        commentsBuffer.append( Misc.hex(offset) ).append("(a").append( eaRegister ).append(")");
                        if ( verboseRegisterDescriptions ) {
                            commentsBuffer.append(" ").append( resolved.description );
                        }
                        return;
                    }
                }

                if ( indirectCallResolver != null )
                {
                    final FunctionDescription resolved = indirectCallResolver.resolve(eaRegister, offset);
                    if ( resolved != null ) {
                        append( resolved.name ).append("(a").append( eaRegister ).append(")");
                        commentsBuffer.append( Misc.hex(offset) ).append("(a").append( eaRegister ).append(")");
                        return;
                    }
                }

                append( Misc.hex(offset) ).append("(a").append( eaRegister ).append(")");
                return;
            case 0b110:
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
                // MEMORY_INDIRECT_POSTINDEXED
                // MEMORY_INDIRECT_PREINDEXED

                int extensionWord = memory.readWordNoCheckNoSideEffects(pc);
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

                    // load sign-extended base displacement
                    baseDisplacement = loadBaseDisplacement(extensionWord);

                    int outerDisplacement = 0;
                    switch ( ((extensionWord & 1<<6) >> 3) | (extensionWord & 0b111) )
                    {
                        case 0b0000: // No Memory Indirect Action
                            append( Misc.hex(baseDisplacement)).append("(");
                            if ( baseRegisterNotSuppressed ) {
                                append("a").append(eaRegister).append( decodeIndexRegisterValue(extensionWord) );
                            } else {
                                append( decodeIndexRegisterValue(extensionWord) );
                            }
                            return;
                        case 0b0001: // Memory Indirect Preindexed with Null Outer Displacement
                            append( Misc.hex(baseDisplacement)).append("(");
                            if ( baseRegisterNotSuppressed ) {
                                append("a").append(eaRegister).append( decodeIndexRegisterValue(extensionWord) );
                            } else {
                                append( decodeIndexRegisterValue(extensionWord) );
                            }
                            append(")");
                            return;
                        case 0b0010: // Indirect Preindexed with Word Outer Displacement
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            append( Misc.hex(baseDisplacement)).append("(");
                            if ( baseRegisterNotSuppressed ) {
                                append("a").append(eaRegister).append( decodeIndexRegisterValue(extensionWord) );
                            } else {
                                append( decodeIndexRegisterValue(extensionWord) );
                            }
                            append( Misc.hex(outerDisplacement) ).append(")");
                            return;
                        case 0b0011: // Indirect Preindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            append( Misc.hex(baseDisplacement)).append("(");
                            if ( baseRegisterNotSuppressed ) {
                                append("a").append(eaRegister).append( decodeIndexRegisterValue(extensionWord) );
                            } else {
                                append( decodeIndexRegisterValue(extensionWord) );
                            }
                            append( Misc.hex(outerDisplacement) ).append(")");
                            return;
                        case 0b0100: // Reserved
                            break;
                        case 0b0101: // Indirect Postindexed with Null Outer Displacement
                            append( Misc.hex(baseDisplacement)).append("(");
                            if ( baseRegisterNotSuppressed ) {
                                append("a").append(eaRegister).append( decodeIndexRegisterValue(extensionWord) );
                            } else {
                                append( decodeIndexRegisterValue(extensionWord) );
                            }
                            append(")");
                            return;
                        case 0b0110: // Indirect Postindexed with Word Outer Displacement
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            append( Misc.hex(baseDisplacement)).append("(");
                            if ( baseRegisterNotSuppressed ) {
                                append("a").append(eaRegister).append( decodeIndexRegisterValue(extensionWord) );
                            } else {
                                append( decodeIndexRegisterValue(extensionWord) );
                            }
                            append( Misc.hex(outerDisplacement) ).append(")");
                            return;
                        case 0b0111: // Indirect Postindexed with Long Outer Displacement
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            append( Misc.hex(baseDisplacement)).append("(");
                            if ( baseRegisterNotSuppressed ) {
                                append("a").append(eaRegister).append( decodeIndexRegisterValue(extensionWord) );
                            } else {
                                append( decodeIndexRegisterValue(extensionWord) );
                            }
                            append( Misc.hex(outerDisplacement) ).append(")");
                            return;
                        case 0b1000: // No Memory Indirect Action, Index suppressed
                            append("a").append(eaRegister).append("+").append(Misc.hex(baseRegisterValue));
                            return;
                        case 0b1001: // Memory Indirect with Null Outer Displacement, Index suppressed
                            append("(a").append(eaRegister).append("+").append(Misc.hex(baseRegisterValue));
                            append(")");
                            return;
                        case 0b1010: // Memory Indirect with Word Outer Displacement, Index suppressed
                            outerDisplacement = memLoadWord(pc);
                            pc += 2;
                            append("(a").append(eaRegister).append("+").append(Misc.hex(baseRegisterValue));
                            append(",").append(Misc.hex(outerDisplacement)).append(")");
                            return;
                        case 0b1011: // Memory Indirect with Long Outer Displacement, Index suppressed
                            outerDisplacement = memLoadLong(pc);
                            pc += 4;
                            append("(a").append(eaRegister).append("+").append(Misc.hex(baseRegisterValue));
                            append(",").append(Misc.hex(outerDisplacement)).append(")");
                            return;
                        case 0b1100: // Reserved
                        case 0b1101: // Reserved
                        case 0b1110: // Reserved
                        case 0b1111: // Reserved
                            break;
                    }
                    illegalOperand();
                    return;
                }

                // brief extension word with 8-bit displacement
                baseDisplacement = (byte) (extensionWord & 0xff);
                baseDisplacement = (baseDisplacement<<24)>>24;
                append( Misc.hex(baseDisplacement) );
                append("(a").append( eaRegister ).append(",").append( decodeIndexRegisterValue(extensionWord))
                        .append(")");
                return;
            case 0b111:
                switch(eaRegister)
                {
                    case 0b010:
                        // PC_INDIRECT_WITH_DISPLACEMENT(0b111,fixedValue(0b010),1),
                        int adr = pc;
                        baseDisplacement = memory.readWordNoCheckNoSideEffects(pc);
                        pc += 2;
                        if ( resolveRelativeOffsets )
                        {
                            commentsBuffer.append( "address =").append( Misc.hex(adr+baseDisplacement) );
                        }
                        append(Misc.hex(baseDisplacement)).append("(pc)");
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
                        extensionWord = memory.readWordNoCheckNoSideEffects(pc);
                        pc += 2;
                        if ( (extensionWord & 1<<8) == 0 ) { // 8-bit displacement
                            baseDisplacement = ((extensionWord & 0xff) << 24) >> 24;
                        } else {
                            baseDisplacement = loadBaseDisplacement(extensionWord);
                        }
                        append( Misc.hex(baseDisplacement)).append("(pc,").append(decodeIndexRegisterValue( extensionWord ));
                        append(")");
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
                        adr = memLoadWord(pc);
                        append( Misc.hex(adr) );
                        pc += 2;
                        return;
                    case 0b001:
                        /*
                         * MOVE (xxx).L,.... (2 extra words).
                        ABSOLUTE_LONG_ADDRESSING(0b111,fixedValue(001) ,2 ),
                         */
                        adr = memLoadLong(pc);
                        append( Misc.hex(adr) );
                        pc += 4;
                        return;
                    case 0b100:
                        /*
                         * MOVE #xxxx,.... (1-6 extra words).
                         * // 1,2,4, OR 6, EXCEPT FOR PACKED DECIMAL REAL OPERANDS
                         * IMMEDIATE_VALUE(0b111,fixedValue(100), 6),   // move #XXXX
                         */
                        append("#");
                        switch(operandSizeInBytes) {
                            case 1: // instruction words always need to be word aligned,
                                // byte values are still stored as words
                            case 2:
                                append(Misc.hex( memory.readWordNoSideEffects( pc ) ) );
                                pc += 2;
                                break;
                            case 4:
                                append(Misc.hex( memory.readLongNoSideEffects( pc ) ) );
                                pc += operandSizeInBytes;
                                break;
                            default:
                                throw new RuntimeException( "Unreachable code reached" );
                        }
                        return;
                }
        }
        illegalOperand();
    }

    private String decodeIndexRegisterValue(int extensionWord)
    {
        boolean indexIsAddressRegister = (extensionWord & 0b1000_0000_0000_0000) != 0;
        int idxRegisterBits = (extensionWord & 0b0111_0000_0000_0000) >> 12;

        String register = (indexIsAddressRegister ? "a" : "d")+idxRegisterBits;
        if ((extensionWord & 0b0000_1000_0000_0000) == 0)
        { // use only lower 16 bits from index register (IDX.w / IDX.l flag)
            register += ".w";
        }
        final int scale = (extensionWord & 0b0000_0110_0000_0000) >> 9;
        return scale == 1 ? register : register+"*"+scale;
    }

    private int memLoadWord(int address) {
        return memory.readWordNoSideEffects( address );
    }

    private int memLoadLong(int address ) {
        return memory.readLongNoSideEffects( address );
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

    private void printRotateOperands(int insnWord, CPU.RotateOperandMode mode)
    {
        int sizeBits = (insnWord & 0b11000000) >> 6;

        if ( mode == CPU.RotateOperandMode.IMMEDIATE )
        {
            // ROL/LSL/ASL IMMEDIATE
            appendOperandSize(sizeBits);
            int cnt = (insnWord & 0b0000111000000000 ) >>> 9;
            if ( cnt == 0 ) {
                cnt = 8;
            }
            append("#").append(cnt).append(",").appendDataRegister( (insnWord & 0b111) );
            return;
        }
        if ( mode == CPU.RotateOperandMode.MEMORY ) {
            // ROL/LSL/ASL MEMORY
            append(" ");
            int eaMode     = (insnWord & 0b111000) >>> 3;
            int eaRegister = (insnWord & 0b000111);
            decodeOperand(1<<sizeBits,eaMode,eaRegister);
            return;
        }
        // ROL/LSL/ASL register
        if ( mode == CPU.RotateOperandMode.REGISTER)
        {
            appendOperandSize(sizeBits);
            int srcRegNum = (insnWord & 0b0000111000000000) >>> 9;
            int dstRegNum = (insnWord & 0b111);
            append("d").append(srcRegNum).append(",").appendDataRegister(dstRegNum);
            return;
        }
        throw new RuntimeException("unhandled mode: "+mode);
    }

    private void illegalOperand() {
        append("???");
    }

    private static boolean matches(int instructionWord,InstructionEncoding insn)
    {
        return (instructionWord & insn.getInstructionWordAndMask()) == insn.getInstructionWordMask();
    }

    private void printBitOp(int insnWord, boolean bitNumInRegister)
    {
        if (bitNumInRegister) {
            // Bxxx Dn,<ea>
            final int regNum = (insnWord & 0b0000111000000000) >> 9;
            appendDataRegister( regNum ).append(",");
            decodeOperand( 2,(insnWord&0b111000)>>3,(insnWord&0b111) );
            return;
        }
        // Bxxx #xx,<ea>
        int bitToTest = memLoadWord(pc) & 0b11111;
        pc += 2;
        append("#").append(bitToTest).append(",");
        decodeOperand( 2,(insnWord&0b111000)>>3,(insnWord&0b111) );
    }

    public void setDumpHex(boolean dumpHex)
    {
        this.dumpHex = dumpHex;
    }

    private void initializeOpcodeMap() throws IOException, IllegalAccessException {
        Arrays.fill( opcodeMap, Instruction.ILLEGAL);
        OpcodeFileReader.parseFile( (opcode,insnName,insnEncName) -> {
            opcodeMap[ opcode ] = Instruction.valueOf( insnName );
        });
    }

    public void setResolveRelativeOffsets(boolean resolveRelativeOffsets)
    {
        this.resolveRelativeOffsets = resolveRelativeOffsets;
    }

    public void setIndirectCallResolver(IIndirectCallResolver indirectCallResolver)
    {
        this.indirectCallResolver = indirectCallResolver;
    }

    public void setChipRegisterResolver(IChipRegisterResolver chipRegisterResolver)
    {
        this.chipRegisterResolver = chipRegisterResolver;
    }

    public void setVerboseRegisterDescriptions(boolean verboseRegisterDescriptions)
    {
        this.verboseRegisterDescriptions = verboseRegisterDescriptions;
    }
}