package de.codersourcery.m68k.disassembler;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.arch.Condition;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.assembler.arch.InstructionEncoding;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Disassembler
{
    private final Memory memory;

    private final StringBuilder output = new StringBuilder();

    private int pcAtStartOfInstruction;
    private int pc;
    private int endAddress;

    private static final class EndOfMemoryAccess extends RuntimeException {
    }
    public Disassembler(Memory memory) {
        this.memory = memory;
    }

    public String disassemble(int startAddress,int bytes)
    {
        pc = startAddress;
        endAddress = startAddress + bytes;
        output.setLength(0);

        try {
            disassemble();
        }
        catch(EndOfMemoryAccess e) {
            append("<...>");
        }
        return output.toString();
    }

    private void disassemble() throws EndOfMemoryAccess
    {
        Map<Integer,InstructionEncoding> existing = new HashMap<>();
        for ( var entry : Instruction.ALL_ENCODINGS.entrySet() )
        {
            Integer andMask = entry.getKey().getInstructionWordAndMask();
            final InstructionEncoding otherEncoding = existing.get(andMask);
            if ( otherEncoding != null ) {
                if ( otherEncoding.getInstructionWordMask() == entry.getKey().getInstructionWordMask() ) {
                    System.err.println("Cannot distinguish "+otherEncoding+" from "+entry.getKey()+" - "+entry.getValue());
                }
            } else {
                existing.put( andMask, entry.getKey() );
            }
        }

        while ( pc < endAddress )
        {
            pcAtStartOfInstruction = pc;
            final int insnWord = readWord();

            // TODO: Performance...maybe using a prefix tree
            // TODO: would be faster than linear search ?
            final Map<InstructionEncoding,Instruction> candidates = new HashMap<>();
            for ( var entry : Instruction.ALL_ENCODINGS.entrySet() )
            {
                var encoding = entry.getKey();
                boolean matches = matches( insnWord, encoding );
                System.out.println( entry.getValue()+" => "+matches+" (mask: "+Misc.hex(encoding.getInstructionWordAndMask() )+", expected: "+encoding.getInstructionWordMask() );
                if ( matches )
                {
                    candidates.put(encoding, entry.getValue());
                }
            }
            if ( candidates.isEmpty() ) {
                illegalOperation(insnWord);
                continue;

            }

            InstructionEncoding encoding;
            Instruction instruction;
            if ( candidates.size() > 1 )
            {
                List<InstructionEncoding> sortedByMaskLength = new ArrayList<>( candidates.keySet() );
                sortedByMaskLength.sort( (b,a) -> Integer.compare( getMaskLength(a), getMaskLength(b) ) );

                final InstructionEncoding shortest = sortedByMaskLength.get(0);
                final Set<Instruction> instructions = new HashSet<>();
                final List<InstructionEncoding> clashes = new ArrayList<>();
                for ( var entry : sortedByMaskLength ) {
                    if ( getMaskLength(entry ) == getMaskLength(shortest ) ) {
                        instructions.add( candidates.get( entry ) );
                        clashes.add( entry );
                    }
                }
                if ( clashes.size() > 1 )
                {
                    if ( instructions.size() > 1 )
                    {
                        throw new RuntimeException("Same mask len: " + shortest + " <-> " + sortedByMaskLength.get(1));
                    }
                    System.err.println("WARNING: Found multiple matching encodings for "+instructions);
                }
                // same instruction
                encoding = shortest;
                instruction = candidates.get(shortest);
            } else {
                encoding = candidates.keySet().iterator().next();
                instruction = candidates.values().iterator().next();
            }

            System.out.println("Using: "+encoding+" => "+instruction);
            disassemble(instruction,encoding,insnWord);
        }
    }

    private static int getMaskLength(InstructionEncoding encoding) {

        int i = 0;
        final int mask = encoding.getInstructionWordAndMask();
        int bit = 1;
        for ( ; i < 32 ; i++ )
        {
            if ( (mask & bit) !=  0) {
                return 32-i;
            }
            bit <<= 1;
        }
        return 0;
    }

    /**
     *
     * @param sizeBits size bits, already shifted so that b00 = .b / b01 = .w and b10 = .l
     */
    private void appendOperandSize(int sizeBits)
    {
        int operandSize = 1<<sizeBits;
        switch(operandSize){
            case 1: append(".b "); break;
            case 2: append(".w "); break;
            case 4: append(".l "); break;
            default:
                throw new RuntimeException("Unreachable code reached, size bits: "+sizeBits);
        }
    }

    private void disassemble(Instruction insn,
                             InstructionEncoding encoding,
                             int insnWord)
    {
        switch( insn )
        {
            case NOT:
                appendln("not");
                int sizeBits = (insnWord & 0b11000000) >>> 6;
                appendOperandSize(sizeBits);
                int eaMode     = (insnWord & 0b111000) >>> 3;
                int eaRegister = (insnWord & 0b000111);
                decodeOperand(1<<sizeBits,eaMode,eaRegister);
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
                throw new RuntimeException("Unreachable code reached");
            case ROL:
                appendln("rol");
                sizeBits = (insnWord & 0b11000000) >> 6;
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000100011000 )
                {
                    // ROL_IMMEDIATE
                    appendOperandSize(sizeBits);
                    int cnt = (insnWord & 0b0000111000000000 ) >>> 9;
                    if ( cnt == 0 ) {
                        cnt = 8;
                    }
                    append("#").append(cnt).append(",").appendDataRegister( (insnWord & 0b111) );
                    return;
                }
                if ( ( insnWord & 0b1111111111000000) == 0b1110011111000000 ) {
                    // ROL_MEMORY
                    append(" ");
                    eaMode     = (insnWord & 0b111000) >>> 3;
                    eaRegister = (insnWord & 0b000111);
                    decodeOperand(1<<sizeBits,eaMode,eaRegister);
                    return;
                }
                // ROL register
                appendOperandSize(sizeBits);
                int srcRegNum = (insnWord & 0b0000111000000000 ) >>> 9;
                int dstRegNum = (insnWord & 0b111);
                append("d").append(srcRegNum).append(",").appendDataRegister(dstRegNum);
                return;
            case ROR:
                appendln("ror");
                sizeBits = (insnWord & 0b11000000) >>> 6;
                if ( ( insnWord & 0b1111000100111000 ) == 0b1110000000011000 ) {
                    // ROR_IMMEDIATE
                    appendOperandSize(sizeBits);
                    int cnt = (insnWord & 0b0000111000000000 ) >>> 9;
                    if ( cnt == 0 ) {
                        cnt = 8;
                    }
                    final int regNum = (insnWord & 0b111);
                    append("#").append(cnt).append(",").appendDataRegister(regNum);
                    return;
                }
                if ( ( insnWord & 0b1111111111000000) == 0b1110011011000000 ) {
                    // ROR_MEMORY
                    append(" ");
                    eaMode     = (insnWord & 0b111000) >>> 3;
                    eaRegister = (insnWord & 0b000111);
                    decodeOperand(1<<sizeBits,eaMode,eaRegister);
                    return;
                }
                // ROR register
                appendOperandSize(sizeBits);
                srcRegNum = (insnWord & 0b0000111000000000 ) >>> 9;
                dstRegNum = (insnWord & 0b111);
                append("d").append(srcRegNum).append(",").appendDataRegister(dstRegNum);
                return;
            case NEG:
                appendln("neg");
                sizeBits = (insnWord & 0b11000000) >>> 6;
                int operandSize = 1<<sizeBits;
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
                final int displacement = readWord();
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
                int regNum = (insnWord & 0b0000111000000000) >> 9;
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
            case AND:
                if ( encoding == Instruction.ANDI_TO_SR_ENCODING) {
                    //     public static final InstructionEncoding ANDI_TO_SR_ENCODING =
                    //            InstructionEncoding.of("0000001001111100","vvvvvvvv_vvvvvvvv");
                    final int word = readWord();
                    appendln("andi #").append( Misc.hex(word) ).append(",sr");
                    return;
                }
                // TODO: Implement other AND variants as well
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
                Condition cond = Condition.fromBits( (insnWord & 0b0000111100000000) >>> 8 );
                int register = insnWord & 0b111;
                int offset = readWord();
                int destinationAdress = pcAtStartOfInstruction + offset;
                appendln("db").append(cond.suffix.toLowerCase()).append(" d").append( register ).append(",").append(
                        Misc.hex(destinationAdress));
                return;
            // Bcc
            case BRA:
            case BRF:
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
                if ( encoding == Instruction.BCC_8BIT_ENCODING ) {
                    offset = insnWord & 0xff;
                    offset = (offset<<24) >> 24;
                } else if ( encoding == Instruction.BCC_16BIT_ENCODING ) {
                    offset = readWord();
                } else if ( encoding == Instruction.BCC_32BIT_ENCODING ) {
                    offset = readLong();
                } else {
                    break;
                }
                append( Misc.hex( pcAtStartOfInstruction+offset ) );
                return;
            // Misc
            case NOP:
                appendln("nop");
                break;
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
                break;
            case MOVEQ:
                int value = insnWord & 0xff;
                value = (value <<24) >> 24;
                regNum = (insnWord & 0b0000111000000000) >>> 9;
                appendln("moveq #").append( value ).append(",").appendDataRegister(regNum);
                return;
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

    private void illegalOperation(int insnWord ) {
        appendln("<unknown ").append(Misc.binary16Bit(insnWord)).append(">");
    }

    private int readLong()
    {
        if ( (pc + 4 ) > endAddress ) {
            throw new EndOfMemoryAccess();
        }
        int result = memory.readLong( pc );
        pc+=4;
        return result;
    }

    private int readWord() {
        if ( (pc + 2 ) > endAddress ) {
            throw new EndOfMemoryAccess();
        }
        int result = memory.readWord( pc );
        pc+=2;
        return result;
    }

    private Disassembler appendln(String s) {
        if ( output.length() > 0 )
        {
            append("\n");
        }
        final String address = StringUtils.leftPad( Integer.toHexString( pcAtStartOfInstruction ),8,"0");
        return append( address).append(": ").append( s );
    }

    private Disassembler append(String s) {
        output.append( s );
        return this;
    }

    private Disassembler append(int value) {
        output.append( Integer.toString( value ) );
        return this;
    }

    private Disassembler appendDataRegister(int regNum) {
        return append("d").append(regNum);
    }

    private Disassembler appendAddressRegister(int regNum) {
        return append("a").append(regNum);
    }

    private Disassembler appendHex(int value) {
        output.append( "$").append( Integer.toHexString( value ) );
        return this;
    }

    private Disassembler appendHex16Bit(int value)
    {
        output.append( "$").append( Integer.toHexString( value & 0xffff ) );
        return this;
    }

    private void decodeOperand(int operandSize,int eaMode, int eaRegister)
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
                        append( Misc.hex(adr) );
                        return;
                }
                // $$FALL-THROUGH$$
            default:
                decodeOperand2(operandSize, eaMode, eaRegister);
        }
    }

    private void decodeOperand2(int operandSize, int eaMode, int eaRegister)
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
                append( Misc.hex(offset) ).append("(a").append( eaRegister ).append(")");
                return;
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
                        baseDisplacement = memory.readWordNoCheck(pc);
                        pc += 2;
                        append( Misc.hex(baseDisplacement ) ).append("(pc)");
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
                        extensionWord = memory.readWordNoCheck(pc);
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
                        int adr = memLoadWord(pc);
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
                        switch(operandSize) {
                            case 1: // instruction words always need to be word aligned,
                                // byte values are still stored as words
                            case 2:
                                append(Misc.hex( memory.readWord( pc ) ) );
                                pc += 2;
                                break;
                            case 4:
                                append(Misc.hex( memory.readLong( pc ) ) );
                                pc += operandSize;
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
        return memory.readWord( address );
    }

    private int memLoadLong(int address ) {
        return memory.readLong( address );
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
}