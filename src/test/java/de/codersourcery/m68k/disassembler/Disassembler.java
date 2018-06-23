package de.codersourcery.m68k.disassembler;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.arch.Condition;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.assembler.arch.InstructionEncoding;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;

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
        while ( true )
        {
            pcAtStartOfInstruction = pc;
            final int insnWord = readWord();

            // TODO: Performance...maybe using a prefix tree
            // TODO: would be faster than linear search ?
            for ( var entry : Instruction.ALL_ENCODINGS.entrySet() )
            {
                var encoding = entry.getKey();
                if ( ( insnWord & encoding.getInstructionWordAndMask() ) ==
                        encoding.getInstructionWordMask() )
                {
                  var instruction = entry.getValue();
                  disassemble(instruction,encoding,insnWord);
                }
            }
        }
    }

    private void disassemble(Instruction insn,
                             InstructionEncoding encoding,
                             int insnWord)
    {
        switch( insn ) {

            case JMP:
                /*
    public static final InstructionEncoding JMP_INDIRECT_ENCODING = InstructionEncoding.of( "0100111011mmmsss");
    public static final InstructionEncoding JMP_SHORT_ENCODING = InstructionEncoding.of( "0100111011mmmsss","vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding JMP_LONG_ENCODING = InstructionEncoding.of( "0100111011mmmsss","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");
                 */
                if ( encoding == Instruction.JMP_INDIRECT_ENCODING ) {
                    // TODO: Implement me !
                }
                else if ( encoding == Instruction.JMP_LONG_ENCODING) {
                    // TODO: Implement me !
                }
                else if ( encoding == Instruction.JMP_SHORT_ENCODING) {
                    // TODO: Implement me !
                }
                break;
            case AND:
                // TODO: Implement me !
                break;
            case TRAP:
                // TODO: Implement me !
                break;
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
                /*
                 InstructionEncoding.of( "0101cccc11001sss","CCCCCCCC_CCCCCCCC");
                 */
                Condition cond = Condition.fromBits( (insnWord & 0b0000111100000000) >> 8 );
                int register = insnWord & 0b111;
                int offset = readWord();
                int destinationAdress = pcAtStartOfInstruction + offset;
                appendln("d").append(cond.suffix).append(" d").append( register ).append(",").append(
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
                cond = Condition.fromBits( (insnWord & 0b0000111100000000 ) >> 8 );
                appendln("B").append( cond.suffix ).append(" ");
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
                break;
            // Misc
            case NOP:
                appendln("nop");
                break;
            case EXG:
                // TODO: Implement me !
                break;
            case MOVEQ:
                // TODO: Implement me !
                break;
            case MOVE:
                // TODO: Implement me !
                break;
            case LEA:
                // TODO: Implement me !
                break;
        }
        appendln("<unknown ").append(Misc.binary16Bit(insnWord)).append(">");
    }

    private int readLong()
    {
        if ( (pc + 4 ) >= endAddress ) {
            throw new EndOfMemoryAccess();
        }
        int result = memory.readLong( pc );
        pc+=4;
        return result;
    }

    private int readWord() {
        if ( (pc + 2 ) >= endAddress ) {
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
}