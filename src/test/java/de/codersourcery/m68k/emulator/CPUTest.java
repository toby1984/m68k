package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.Assembler;
import de.codersourcery.m68k.assembler.CompilationMessages;
import de.codersourcery.m68k.assembler.CompilationUnit;
import de.codersourcery.m68k.assembler.IResource;
import de.codersourcery.m68k.assembler.Symbol;
import de.codersourcery.m68k.assembler.arch.CPUType;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.emulator.cpu.CPU;
import de.codersourcery.m68k.parser.Identifier;
import de.codersourcery.m68k.utils.Misc;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CPUTest extends TestCase
{
    private static final int MEM_SIZE = 10*1024;

    private static final int SUPERVISOR_STACK_PTR = MEM_SIZE; // stack grows downwards on M68k
    private static final int USERMODE_STACK_PTR = SUPERVISOR_STACK_PTR-256; // stack grows downwards on M68k

    public static final int ALL_USR_FLAGS = CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW | CPU.FLAG_NEGATIVE | CPU.FLAG_ZERO | CPU.FLAG_EXTENDED;

    // program start address in memory (in bytes)
    public static final int PROGRAM_START_ADDRESS = 4096;

    private Memory memory;
    private CPU cpu;
    private CompilationUnit compilationUnit;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        memory = new Memory(MEM_SIZE);
        cpu = new CPU(CPUType.BEST,memory);
    }

    public void testLEA()
    {
        execute("lea $12345678,a0", cpu -> cpu.setFlags(ALL_USR_FLAGS))
            .expectA0(0x12345678).carry().overflow().extended().negative().zero().notSupervisor();
    }

    public void testIllegal()
    {
        execute(cpu->{},"illegal").supervisor().irqActive(CPU.IRQ.ILLEGAL_INSTRUCTION);
    }

    public void testExtByteToWord()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED | CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW ),
                "move.l #$12345680,d3",
                "ext.w d3")
                .expectD3( 0x1234ff80 ).extended().negative().notZero().noCarry().noOverflow();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED | CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW ),
                "move.l #$12345600,d3",
                "ext.w d3")
                .expectD3( 0x12340000 ).extended().notNegative().zero().noCarry().noOverflow();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED | CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW ),
                "move.l #$12345601,d3",
                "ext.w d3")
                .expectD3( 0x12340001 ).extended().notNegative().notZero().noCarry().noOverflow();
    }

    public void testCLR()
    {
        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ),
                 "move.l #$12345678,d3",
                 "clr.b d3")
                .expectD3( 0x12345600 ).extended().notNegative().zero().noOverflow().noCarry();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ),
                 "move.l #$12345678,d3",
                 "clr.w d3")
                .expectD3( 0x12340000 ).extended().notNegative().zero().noOverflow().noCarry();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ),
                 "move.l #$12345678,d3",
                 "clr.l d3")
                .expectD3( 0 ).extended().notNegative().zero().noOverflow().noCarry();
    }

    public void testExtWordToLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED | CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW ),
                "move.l #$12348000,d3",
                "ext.l d3")
                .expectD3( 0xffff8000 ).extended().negative().notZero().noCarry().noOverflow();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED | CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW ),
                "move.l #$12340000,d3",
                "ext.l d3")
                .expectD3( 0x00000000 ).extended().notNegative().zero().noCarry().noOverflow();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED | CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW ),
                "move.l #$12340001,d3",
                "ext.l d3")
                .expectD3( 0x00000001 ).extended().notNegative().notZero().noCarry().noOverflow();
    }

    public void testLslByte()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_EXTENDED),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "lsl.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "lsl.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "lsl.b #1,d3")
                .expectD3(  0x12345602 ).notZero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "lsl.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "lsl.b #1,d3")
                .expectD3(  0x12345600 ).zero().carry().notNegative().noOverflow().extended();
    }

    public void testAsrByte()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED|CPU.FLAG_CARRY ),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "asr.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY ),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "asr.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                 "asr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                 "asr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                 "asr.b #1,d3")
                .expectD3(  0x123456C0 ).notZero().noCarry().negative().noOverflow().notExtended();
    }

    public void testAslByte()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED|CPU.FLAG_CARRY ),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "asl.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY ),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "asl.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                 "asl.b #1,d3")
                .expectD3(  0x12345602 ).notZero().notNegative().noCarry().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                 "asl.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                 "asl.b #1,d3")
                .expectD3(  0x12345600 ).zero().carry().notNegative().overflow().extended();
    }

    public void testRolByte()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_EXTENDED),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "rol.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "rol.b #1,d3")
                .expectD3(  0x12345602 ).notZero().notNegative().noCarry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "rol.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "rol.b #1,d3")
                .expectD3(  0x12345601 ).notZero().carry().notNegative().noOverflow().extended();
    }

    public void testAslWord() {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                 "asl.w #1,d3")
                .expectD3(  0x12340002 ).notZero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                 "asl.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                 "asl.w #1,d3")
                .expectD3(  0x12340000 ).zero().carry().notNegative().overflow().extended();
    }

    public void testLslWord() {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                "lsl.w #1,d3")
                .expectD3(  0x12340002 ).notZero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "lsl.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                "lsl.w #1,d3")
                .expectD3(  0x12340000 ).zero().carry().notNegative().noOverflow().extended();
    }

    public void testRolWord() {

        final int adr = PROGRAM_START_ADDRESS+ 128;
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,"+adr,
                "rol "+(adr+2))
                .expectMemoryLong( adr,0x12340001 )
                .carry().extended().notZero().notNegative();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
            "rol.w #1,d3")
            .expectD3(  0x12340002 ).notZero().notNegative().noCarry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
            "rol.w #1,d3")
            .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
            "rol.w #1,d3")
            .expectD3(  0x12340001 ).notZero().carry().notNegative().noOverflow().extended();
    }

    public void testAslLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                 "move.l #1,d1", // hack to clear N flag set by move #$80000000
                 "asl.l #1,d3")
                .expectD3(  0x00000000 ).zero().carry().notNegative().overflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                 "asl.l #1,d3")
                .expectD3(  0x00000002 ).notZero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                 "asl.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().notExtended();
    }

    public void testLslLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                "move.l #1,d1", // hack to clear N flag set by move #$80000000
                "lsl.l #1,d3")
                .expectD3(  0x00000000 ).zero().carry().notNegative().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                "lsl.l #1,d3")
                .expectD3(  0x00000002 ).notZero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "lsl.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().notExtended();
    }

    public void testRolLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
            "move.l #1,d1", // hack to clear N flag set by move #$80000000
            "rol.l #1,d3")
            .expectD3(  0x00000001 ).notZero().carry().notNegative().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
            "rol.l #1,d3")
            .expectD3(  0x00000002 ).notZero().notNegative().noCarry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
            "rol.l #1,d3")
            .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().extended();
    }

    public void testLsrByte()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_EXTENDED),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "lsr.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "lsr.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "lsr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "lsr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "lsr.b #1,d3")
                .expectD3(  0x12345640 ).notZero().noCarry().notNegative().noOverflow().notExtended();
    }

    public void testBSR() {

            execute(cpu -> {}, 4, "org $2000",
                            "bsr sub",
                            "move #2,d1",
                            "illegal",
                            "sub:",
                            "move #1,d0",
                            "rts").noIrqActive().expectD0(1).expectD1(2);
    }

    public void testRorByte()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_EXTENDED),
                 "move.l #0,d2",
                 "move.l #0,d3",
                 "ror.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
            "ror.b #1,d3")
            .expectD3(  0x12345680 ).notZero().negative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
            "ror.b #1,d3")
            .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
            "ror.b #1,d3")
            .expectD3(  0x12345640 ).notZero().noCarry().notNegative().noOverflow().extended();
    }

    public void testAsrWord()
    {
        final int adr = PROGRAM_START_ADDRESS+ 128;
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340002,"+adr,
                 "asr "+(adr+2))
                .expectMemoryLong( adr,0x12340001 )
                .noCarry().notExtended().notZero().notNegative();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                 "asr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                 "asr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                 "asr.w #1,d3")
                .expectD3(  0x1234c000 ).notZero().noCarry().negative().noOverflow().notExtended();
    }

    public void testLsrWord()
    {
        final int adr = PROGRAM_START_ADDRESS+ 128;
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340002,"+adr,
                "lsr "+(adr+2))
                .expectMemoryLong( adr,0x12340001 )
                .noCarry().notExtended().notZero().notNegative();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                "lsr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "lsr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().notExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                "lsr.w #1,d3")
                .expectD3(  0x12344000 ).notZero().noCarry().notNegative().noOverflow().notExtended();
    }

    public void testChkWord() {

        // $ffff < 0 || $ffff > $0a
        execute( cpu -> {},
                  "move.l #$ffffffff,d5", // value to check
                 "move.l #$0000000a,d4", // upper bound
                 "chk d4,d5")
                .negative().irqActive(CPU.IRQ.CHK_CHK2);

        // $b < 0 || $b > $0a
        execute( cpu -> {},
                 "move.l #$0000000b,d5", // value to check
                 "move.l #$0000000a,d4", // upper bound
                 "chk d4,d5").notNegative().irqActive(CPU.IRQ.CHK_CHK2);

        // $9 < 0 || $9 > $0a
        execute( cpu -> cpu.setFlags(CPU.FLAG_NEGATIVE),
                 "move.l #$00000009,d5", // value to check
                 "move.l #$0000000a,d4", // upper bound
                 "chk d4,d5")
                .negative().noIrqActive();
    }

    public void testChkLong() { // MC68020+

        // $ffff < 0 || $ffff > $0a
        execute( cpu -> cpu.setFlags(CPU.FLAG_NEGATIVE),
                 "move.l #$1234ffff,d5", // value to check
                 "move.l #$2234000a,d4", // upper bound
                 "chk.l d4,d5")
                .negative().noIrqActive();

        // $b < 0 || $b > $0a
        execute( cpu -> {},
                 "move.l #$1234000b,d5", // value to check
                 "move.l #$2234000a,d4", // upper bound
                 "chk.l d4,d5").notNegative().noIrqActive();

        // $9 < 0 || $9 > $0a
        execute( cpu -> cpu.setFlags(CPU.FLAG_NEGATIVE),
                 "move.l #$12340009,d5", // value to check
                 "move.l #$2234000a,d4", // upper bound
                 "chk.l d4,d5")
                .negative().noIrqActive();
    }

    public void testRorWord()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
            "ror.w #1,d3")
            .expectD3(  0x12348000 ).notZero().negative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
            "ror.w #1,d3")
            .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
            "ror.w #1,d3")
            .expectD3(  0x12344000 ).notZero().noCarry().notNegative().noOverflow().extended();
    }

    public void testAsrLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                 "asr.l #1,d3")
                .expectD3(  0xc0000000 ).notZero().noCarry().negative().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                 "asr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                 "asr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().notExtended();
    }

    public void testLsrLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                "lsr.l #1,d3")
                .expectD3(  0x40000000 ).notZero().noCarry().notNegative().noOverflow().notExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                "lsr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "lsr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().notExtended();
    }

    public void testRorLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
            "ror.l #1,d3")
            .expectD3(  0x40000000 ).notZero().noCarry().notNegative().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
            "ror.l #1,d3")
            .expectD3(  0x80000000 ).notZero().negative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
            "ror.l #1,d3")
            .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().extended();
    }

    public void testReset()
    {
        execute("reset").cycles(132);
    }

    public void testLEA2()
    {
        execute("lea $1234,a0", cpu -> cpu.setFlags(ALL_USR_FLAGS))
            .expectA0(0x1234).carry().overflow().extended().negative().zero().notSupervisor();
    }

    public void testMoveByteImmediate() {

        execute("move.b #$12,d0").expectD0(0x12).noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
        execute("move.b #$00,d0").expectD0(0x00).noCarry().noOverflow().notExtended().notNegative().zero().notSupervisor();
        execute("move.b #$ff,d0").expectD0(0xff).noCarry().noOverflow().notExtended().negative().notZero().notSupervisor();
    }

    public void testTrapVTriggered()
    {
        execute(cpu -> cpu.setFlags( CPU.FLAG_OVERFLOW ), "TRAPV")
                .supervisor().irqActive(CPU.IRQ.FTRAP_TRAP_TRAPV);
    }

    public void testTrapVNoTriggered()
    {
        execute(cpu -> cpu.clearFlags( CPU.FLAG_OVERFLOW ), "TRAPV")
                .notSupervisor().noIrqActive();
    }

    public void testTrap()
    {
        execute("TRAP #0").supervisor().irqActive(CPU.IRQ.TRAP0_0);
        execute("TRAP #1").supervisor().irqActive(CPU.IRQ.TRAP0_1);
        execute("TRAP #2").supervisor().irqActive(CPU.IRQ.TRAP0_2);
        execute("TRAP #3").supervisor().irqActive(CPU.IRQ.TRAP0_3);
        execute("TRAP #4").supervisor().irqActive(CPU.IRQ.TRAP0_4);
        execute("TRAP #5").supervisor().irqActive(CPU.IRQ.TRAP0_5);
        execute("TRAP #6").supervisor().irqActive(CPU.IRQ.TRAP0_6);
        execute("TRAP #7").supervisor().irqActive(CPU.IRQ.TRAP0_7);
        execute("TRAP #8").supervisor().irqActive(CPU.IRQ.TRAP0_8);
        execute("TRAP #9").supervisor().irqActive(CPU.IRQ.TRAP0_9);
        execute("TRAP #10").supervisor().irqActive(CPU.IRQ.TRAP0_10);
        execute("TRAP #11").supervisor().irqActive(CPU.IRQ.TRAP0_11);
        execute("TRAP #12").supervisor().irqActive(CPU.IRQ.TRAP0_12);
        execute("TRAP #13").supervisor().irqActive(CPU.IRQ.TRAP0_13);
        execute("TRAP #14").supervisor().irqActive(CPU.IRQ.TRAP0_14);
        execute("TRAP #15").supervisor().irqActive(CPU.IRQ.TRAP0_15);
    }

    public void testSWAP1() {
        execute(cpu-> cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW), 2, "move.l #$12345678,D3",
                "swap d3")
                .expectD3(0x56781234).noOverflow().notNegative().notZero().notExtended().notSupervisor();
    }

    public void testUnlink()
    {
        execute( cpu -> {},3,
                 "move.l #$56781234,(2020)",
                 "move.l #2020,a3",
                 "unlk a3" )
                .expectA3(0x12345678)
                .expectA7(2024).noOverflow().notNegative().notZero().notExtended().notSupervisor();
    }

    public void testNOT()
    {
        /*
        X — Not affected.
 N — Set if the result is negative; cleared otherwise.
Z — Set if the result is zero; cleared otherwise.
V — Always cleared.
C — Always cleared.
         */
        execute(cpu-> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),2,"move.l #1,d0", "not.b d0")
                .expectD0(0b11111110).notZero()
                .negative().noCarry().noOverflow().extended();

        execute(cpu-> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),2,"move.l #0,d0", "not.b d0")
                .expectD0(0xff).notZero().negative().noCarry().noOverflow().extended();

        execute(cpu-> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),2,"move.l #$12345601,d0", "not.b d0")
                .expectD0(0x123456fe).notZero().negative().noCarry().noOverflow().extended();

        execute(cpu-> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),2,"move.l #$12340001,d0", "not.w d0")
                .expectD0(0x1234fffe).notZero().negative().noCarry().noOverflow().extended();

        execute(cpu-> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),2,"move.l #$00000001,d0", "not.l d0")
                .expectD0(0xfffffffe).notZero().negative().noCarry().noOverflow().extended();

        execute(cpu-> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),2,"move.l #$ffffffff,d0", "not.l d0")
                .expectD0(0).zero().notNegative().noCarry().noOverflow().extended();
    }

    public void testNeg()
    {
        execute(cpu->{},2,"move.l #1,d0", "neg.b d0")
                .expectD0(0xff).notZero().negative().carry().extended();

        execute(cpu->{},2,"move.l #0,d0", "neg.b d0")
                .expectD0(0).zero().notNegative().noCarry().notExtended();

        execute(cpu->{},2,"move.l #$12345601,d0", "neg.b d0")
                .expectD0(0x123456ff).notZero().negative().carry().extended();

        execute(cpu->{},2,"move.l #$12340001,d0", "neg.w d0")
                .expectD0(0x1234ffff).notZero().negative().carry().extended();

        execute(cpu->{},2,"move.l #$00000001,d0", "neg.l d0")
                .expectD0(0xffffffff).notZero().negative().carry().extended();
    }

    public void testLink()
    {
        execute( "link a3,#$fffe" )
                .expectA3(USERMODE_STACK_PTR-4)
                .expectA7(USERMODE_STACK_PTR-6).noOverflow().notNegative().notZero().notExtended().notSupervisor();
    }

    public void testSWAP2() {
        execute(cpu-> cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW), 2, "move.l #0,D3",
                "swap d3")
                .expectD3(0).noOverflow().notNegative().zero().notExtended().notSupervisor();
    }

    public void testSWAP3() {
        execute(cpu-> cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW), 2, "move.l #$ffffffff,D3",
                "swap d3")
                .expectD3(0xffffffff).noOverflow().negative().notZero().notExtended().notSupervisor();
    }

    public void testJSR() {

        // expectTopOfStack
        execute(cpu-> {}, 2, "start: jsr sub",
                "illegal",
                "sub: move.w #$1234,d3")
                .expectTopOfStack("start", adr -> adr+4)
                .expectD3(0x1234)
                .noOverflow()
                .notNegative()
                .notZero()
                .notExtended()
                .notSupervisor();

        execute(cpu-> {}, 3, "move.l #1,D0",
                "loop: dbra d0,loop").expectD0(0xffff).noOverflow().notNegative().notZero().notExtended().notSupervisor();
    }

    public void testRTS() {

        // expectTopOfStack
        execute(cpu-> {}, 4, "start: jsr sub",
                "move.w #$5678,d1",
                "illegal",
                "sub: move.w #$1234,d3",
                "rts")
                .expectD3(0x1234)
                .expectD1(0x5678)
                .noOverflow()
                .notNegative()
                .notZero()
                .notExtended()
                .notSupervisor();

        execute(cpu-> {}, 3, "move.l #1,D0",
                "loop: dbra d0,loop").expectD0(0xffff).noOverflow().notNegative().notZero().notExtended().notSupervisor();
    }

    public void testScc()
    {
        testScc(Instruction.ST);
        testScc(Instruction.SF);
        testScc(Instruction.SHI);
        testScc(Instruction.SLS);
        testScc(Instruction.SCC);
        testScc(Instruction.SCS);
        testScc(Instruction.SNE);
        testScc(Instruction.SEQ);
        testScc(Instruction.SVC);
        testScc(Instruction.SVS);
        testScc(Instruction.SPL);
        testScc(Instruction.SMI);
        testScc(Instruction.SGE);
        testScc(Instruction.SLT);
        testScc(Instruction.SGT);
        testScc(Instruction.SLE);
    }

    private void testScc(Instruction insn) {

        final int adr = PROGRAM_START_ADDRESS+128;
        int expected = -1;
        int flags = 0;
        switch(insn.condition) {
/*
BRA* True            0000 = 1
F*   False           0001 = 0
BHI High             0010 = !C & !Z
BLS Low or Same      0011 = C | Z
BCC/BHI Carry Clear  0100 = !C
BCS/BLO Carry Set    0101 = C
BNE Not Equal        0110 = !Z
BEQ Equal            0111 = Z
BVC Overflow Clear   1000 = !V
BVS Overflow Set     1001 = V
BPL Plus             1010 = !N
BMI Minus            1011 = N
BGE Greater or Equal 1100 = (N &  V) | (!N & !V)
BLT Less Than        1101 = (N & !V) | (!N & V)
BGT Greater Than     1110 = ((N & V) | (!N & !V)) & !Z;
BLE Less or Equal    1111 = Z | (N & !V) | (!N & V)
 */
            case BRT: break; // always true
            case BSR: expected = 0x00; break; // always false
            case BHI: break;
            case BLS: flags |= CPU.FLAG_CARRY; break;
            case BCC: break;
            case BCS: flags |= CPU.FLAG_CARRY; break;
            case BNE: break;
            case BEQ: flags |= CPU.FLAG_ZERO; break;
            case BVC: break;
            case BVS: flags |= CPU.FLAG_OVERFLOW; break;
            case BPL: break;
            case BMI: flags |= CPU.FLAG_NEGATIVE ;break;
            case BGE: flags |= CPU.FLAG_NEGATIVE | CPU.FLAG_OVERFLOW; break;
            case BLT: flags |= CPU.FLAG_NEGATIVE ; break;
            case BGT: flags |= CPU.FLAG_NEGATIVE | CPU.FLAG_OVERFLOW; break;
            case BLE: flags |= CPU.FLAG_ZERO; break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }
        final String source= insn.getMnemonic()+" "+adr;
        final int finalFlags=flags;
        execute(cpu -> cpu.setFlags(finalFlags), source )
            .expectMemoryByte(adr,expected ).notSupervisor().noIrqActive();
    }

    public void testDBRA() {

        execute(cpu-> {}, 3, "move.l #1,D0",
                "move.l #0,d1",
                "loop: dbeq d0,loop").expectD0(1).noOverflow().notNegative().zero().notExtended().notSupervisor();

        execute(cpu-> {}, 3, "move.l #1,D0",
                "loop: dbra d0,loop").expectD0(0xffff).noOverflow().notNegative().notZero().notExtended().notSupervisor();
    }

    public void testMoveByteClearsFlags() {
        execute("move.b #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12)
                .noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveWordClearsFlags() {
        execute("move.w #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12).noCarry()
                .noOverflow().notExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveLongClearsFlags() {
        execute("move.l #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12).noCarry().noOverflow().notExtended().notNegative().notZero();
    }

    public void testMoveWordImmediate() {

        execute("move.w #$1234,d0").expectD0(0x1234).noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
        execute("move.w #$00,d0").expectD0(0x00).noCarry().noOverflow().notExtended().notNegative().zero().notSupervisor();
        execute("move.w #$ffff,d0").expectD0(0xffff).noCarry().noOverflow().notExtended().negative().notZero().notSupervisor();
    }

    public void testMoveLongImmediate() {

        execute("move.l #$fffffffe,d0").expectD0(0xfffffffe).noCarry().noOverflow().notExtended().negative().notZero().notSupervisor();
        execute("move.l #$00,d0").expectD0(0x00).noCarry().noOverflow().notExtended().notNegative().zero().notSupervisor();
        execute("move.l #$12345678,d0").expectD0(0x12345678).noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveQClearsCarryAndOverflow()
    {
        execute("moveq #$70,d0", cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW) ).expectD0(0x70)
                .noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveQ()
    {
        execute("moveq #$70,d0").expectD0(0x70).noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
        execute("moveq #$0,d0").expectD0(0x0).noCarry().noOverflow().notExtended().notNegative().zero().notSupervisor();
        execute("moveq #$ff,d0").expectD0(0xffffffff).noCarry().noOverflow().notExtended().negative().notZero().notSupervisor();
    }

    public void testBranchTaken()
    {
        /*
Mnemonic Condition Encoding Test
BRA* True            0000 = 1 (ok)
F*   False           0001 = 0
BHI High             0010 = !C & !Z (ok)
BLS Low or Same      0011 = C | Z (ok)
BCC/BHI Carry Clear  0100 = !C (ok)
BCS/BLO Carry Set    0101 = C (ok)
BNE Not Equal        0110 = !Z (ok)
BEQ Equal            0111 = Z (ok)
BVC Overflow Clear   1000 = !V (ok)
BVS Overflow Set     1001 = V (ok)
BPL Plus             1010 = !N (ok)
BMI Minus            1011 = N (ok)
BGE Greater or Equal 1100 = (N &  V) | (!N & !V) (ok)
BLT Less Than        1101 = (N & !V) | (!N & V) (ok)
BGT Greater Than     1110 = ((N & V) | (!N & !V)) & !Z; // (ok)
BLE Less or Equal    1111 = Z | (N & !V) | (!N & V) (ok)
         */
        execute(cpu -> {},"BRA next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        execute(cpu -> {},"BHI next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        execute(cpu -> cpu.carry(),"BLS next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> cpu.zero(),"BLS next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        execute(cpu -> {},"BCC next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> {},"BNE next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> {},"BVC next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> {},"BPL next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        execute(cpu -> cpu.carry(),"BCS next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> cpu.zero(),"BEQ next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> cpu.overflow(),"BVS next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> cpu.negative(),"BMI next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        // >=
        execute(cpu -> cpu.negative().overflow(),"BGE next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> {},"BGE next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        // <
        execute(cpu -> cpu.negative(),"BLT next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> cpu.overflow(),"BLT next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        // >
        execute(cpu -> cpu.negative().overflow(),"BGT next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> {} ,"BGT next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();

        // <=
        execute(cpu -> cpu.zero() ,"BLE next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> cpu.negative() ,"BLE next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
        execute(cpu -> cpu.overflow() ,"BLE next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
    }

    public void testNOP() {
        execute("nop", cpu -> cpu.setFlags( ALL_USR_FLAGS ) )
                .expectPC(PROGRAM_START_ADDRESS+2)
                .carry().overflow().extended().negative().zero();
    }

    public void testBranchNotTaken()
    {
        /*
Mnemonic Condition Encoding Test
BLE Less or Equal    1111 = Z | (N & !V) | (!N & V) (ok)
         */
        execute(cpu -> cpu.carry(),"BHI next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.zero(),"BHI next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();

        execute(cpu -> {},"BLS next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.carry(),"BCC next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> {},"BCS next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.zero(),"BNE next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> {},"BEQ next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.overflow(),"BVC next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> {},"BVS next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.negative(),"BPL next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> {},"BMI next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();

        execute(cpu -> cpu.negative(),"BGE next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.overflow(),"BGE next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();

        execute(cpu -> cpu.negative().overflow(),"BLT next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();

        execute(cpu -> cpu.negative() ,"BGT next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.overflow(),"BGT next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.zero(),"BGT next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();

        execute(cpu -> {},"BLE next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
        execute(cpu -> cpu.negative().overflow(),"BLE next\nNOP\nnext: ILLEGAL").expectPC( PROGRAM_START_ADDRESS + 2).notSupervisor();
    }

    public void testExgDataData() {

        execute(cpu -> cpu.setFlags(ALL_USR_FLAGS),
              "move.l #$12345678,d1",
            "move.l #$76543210,d3",
                       "exg d1,d3")
            .expectD1(0x76543210)
            .expectD3(0x12345678)
            .carry().overflow().extended().negative().zero().notSupervisor();
    }

    public void testPEA()
    {
        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> {} ,
                "lea "+adr+",a3",
                "move.l #$12345678,(a3)",
                "pea (a3)",
                "move.l (a7)+,d0")
                .expectD0(0x56781234) // swapped
                .noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveToMemory()
    {
        final int adr = PROGRAM_START_ADDRESS+256;
        execute(cpu -> cpu.memory.writeLong(adr,0x12345678) ,
                "move.w #$1234,"+Misc.hex( adr+2 ))
                .expectMemoryLong(adr,0x12341234);

        execute(cpu -> cpu.memory.writeLong(adr,0x12345678) ,
                "move.l #$87654321,"+Misc.hex( adr ))
                .expectMemoryLong(adr,0x87654321);
    }

    public void testMoveFromMemory()
    {
        final int adr = PROGRAM_START_ADDRESS+128;
        execute(cpu -> cpu.memory.writeLong(adr,0x12345678) ,
                "move.l #$ffffffff,d0",
                "move.b "+Misc.hex( adr )+",d0")
                .expectD0(0xffffff34);

        execute(cpu -> cpu.memory.writeLong(adr,0x12345678) ,
                "move.l #$ffffffff,d0",
                "move.w "+Misc.hex( adr )+",d0")
                .expectD0(0xffff1234);

        execute(cpu -> cpu.memory.writeLong(adr,0x12345678) ,
                "move.l #$ffffffff,d0",
                "move.l "+Misc.hex( adr )+",d0")
                .expectD0(0x12345678);
    }

    public void testMoveaWord()
    {
        execute(cpu -> {} ,
                "movea #$ffff,a3")
                .expectA3(0xffffffff)
                .noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveByte()
    {
        execute(cpu -> {} ,
            "move.l #$12345678,d1",
            "move.b #$00,d1")
            .expectD1(0x12345600)
            .noCarry().noOverflow().notExtended().notNegative().zero().notSupervisor();
        execute(cpu -> {} ,
            "move.l #$12345678,d1",
            "move.b #$ff,d1")
            .expectD1(0x123456ff)
            .noCarry().noOverflow().notExtended().negative().notZero().notSupervisor();
    }

    public void testMoveWord()
    {
        execute(cpu -> {} ,
            "move.l #$12345678,d1",
            "move.w #$00,d1")
            .expectD1(0x12340000)
            .noCarry().noOverflow().notExtended().notNegative().zero().notSupervisor();
        execute(cpu -> {} ,
            "move.l #$12345678,d1",
            "move.w #$ffff,d1")
            .expectD1(0x1234ffff)
            .noCarry().noOverflow().notExtended().negative().notZero().notSupervisor();
    }

    public void testMoveLong()
    {
        execute(cpu -> {} ,
            "move.l #$12345678,d1",
            "move.l #$00,d1")
            .expectD1(0)
            .noCarry().noOverflow().notExtended().notNegative().zero().notSupervisor();
        execute(cpu -> {} ,
            "move.l #$12345678,d1",
            "move.l #$ffffffff,d1")
            .expectD1(0xffffffff)
            .noCarry().noOverflow().notExtended().negative().notZero().notSupervisor();
    }

    public void testMemoryStoreByte()
    {
        execute("move.b #$12,(1024)")
                .expectMemoryByte(1024,0x12 ).notZero().notNegative();

        execute("move.b #$00,(1024)")
                .expectMemoryByte(1024,0x0).zero().notNegative();

        execute("move.b #$ff,(1024)")
                .expectMemoryByte(1024,-1 ).notZero().negative();
    }

    public void testMemoryStoreWord()
    {
        execute("move.w #$12,(1024)")
                .expectMemoryWord(1024,0x12 ).notZero().notNegative();

        execute("move.w #$00,(1024)")
                .expectMemoryWord(1024,0x0).zero().notNegative();

        execute("move.w #$ff,(1024)")
                .expectMemoryWord(1024,0xff ).notZero().notNegative();

        execute("move.w #$ffff,(1024)")
                .expectMemoryWord(1024,-1 ).notZero().negative();
    }

    public void testMemoryStoreLong()
    {
        execute("move.l #$12,(1024)")
                .expectMemoryLong(1024,0x12 ).notZero().notNegative();

        execute("move.l #$00,(1024)")
                .expectMemoryLong(1024,0x0).zero().notNegative();

        execute("move.l #$ff,(1024)")
                .expectMemoryLong(1024,0xff ).notZero().notNegative();

        execute("move.l #$ffffffff,(1024)")
                .expectMemoryLong(1024,-1 ).notZero().negative();
    }

    public void testRTR()
    {
        final int swapped = (PROGRAM_START_ADDRESS << 16) | (PROGRAM_START_ADDRESS >>> 16);
        execute(cpu -> {} ,
                "move.l #"+swapped+",-(a7)", // swapped because stack grows towards address 0
                "move.w #$ff,-(a7)",
                "rtr")
                .expectPC( PROGRAM_START_ADDRESS )
                .carry().overflow().extended().negative().zero().notSupervisor();
    }

    public void testBCHG()
    {
        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #31,d1",
                "bchg d1,d0")
                .expectD0(0)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                "bchg #3,d0")
                .expectD0(0b10000001)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                "bchg #2,d0")
                .expectD0(0b10001101)
                .zero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "bchg #31,d0")
                .expectD0(0)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #30,d1",
                "bchg d1,d0")
                .expectD0( 0xc0000000 )
                .zero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128+1)+",a0",
                "bchg #0,(a0)")
                .expectMemoryByte( PROGRAM_START_ADDRESS+128+1, 0x00 )
                .notZero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128)+",a0",
                "bchg #0,(a0)")
                .expectMemoryByte( PROGRAM_START_ADDRESS+128, 0x03 )
                .zero().notSupervisor();
    }

    public void testTST() {

        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS) ,
                "move.l #$00801200,"+adr,
                "tst.b "+adr)
                .zero().notNegative().noOverflow().extended().noCarry().notSupervisor();
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS) ,
                "move.l #$00801200,"+adr,
                "tst.b "+(adr+1))
                .notZero().negative().noOverflow().extended().noCarry().notSupervisor();
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS) ,
                "move.l #$00801200,"+adr,
                "tst.b "+(adr+2))
                .notZero().notNegative().noOverflow().extended().noCarry().notSupervisor();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS) ,
                "move.l #$00801200,"+adr,
                "tst.w "+(adr+2))
                .notZero().notNegative().noOverflow().extended().noCarry().notSupervisor();
    }

    public void testBTST()
    {
        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #31,d1",
                "btst d1,d0")
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                 "btst #3,d0")
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                "btst #2,d0")
                .zero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "btst #31,d0")
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #30,d1",
                "btst d1,d0")
                .zero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128+1)+",a0",
                "btst #0,(a0)")
                .notZero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128+1)+",a0",
                "btst #1,(a0)")
                .zero().notSupervisor();
    }

    public void testBSET()
    {
        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #30,d1",
                "bset d1,d0")
                .expectD0(0b11000000000000000000000000000000)
                .zero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                "bset #3,d0")
                .expectD0(0b10001001)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                "bset #2,d0")
                .expectD0(0b10001101)
                .zero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "bset #31,d0")
                .expectD0(0b10000000000000000000000000000000)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #30,d1",
                "bset d1,d0")
                .expectD0(0b11000000000000000000000000000000)
                .zero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128+1)+",a0",
                "bset #0,(a0)")
                .expectMemoryByte( PROGRAM_START_ADDRESS+128+1,0x01 )
                .notZero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128+1)+",a0",
                "bset #1,(a0)")
                .expectMemoryByte( PROGRAM_START_ADDRESS+128+1,0x03 )
                .zero().notSupervisor();
    }

    public void testBCLR()
    {
        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #31,d1",
                "bclr d1,d0")
                .expectD0(0)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                "bclr #3,d0")
                .expectD0(0b10000001)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10001001,d0",
                "bclr #2,d0")
                .expectD0(0b10001001)
                .zero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "bclr #31,d0")
                .expectD0(0)
                .notZero().notSupervisor();

        execute(cpu -> {} ,
                "move.l #%10000000000000000000000000000000,d0",
                "move.l #30,d1",
                "bclr d1,d0")
                .expectD0(0b10000000000000000000000000000000)
                .zero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128+1)+",a0",
                "bclr #0,(a0)")
                .expectMemoryByte( PROGRAM_START_ADDRESS+128,0x02 )
                .notZero().notSupervisor();

        execute(cpu -> cpu.memory.writeWord( PROGRAM_START_ADDRESS+128, 0x0201 ) ,
                "lea "+(PROGRAM_START_ADDRESS+128+1)+",a0",
                "bclr #1,(a0)")
                .expectMemoryByte( PROGRAM_START_ADDRESS+128+1,0x01 )
                .zero().notSupervisor();
    }

    public void testMoveaLong()
    {
        execute(cpu -> {} ,
                "move.l #$123456,a3")
                .expectA3(0x123456)
                .noCarry().noOverflow().notExtended().notNegative().notZero().notSupervisor();
    }

    public void testExgAdrAdr() {

        execute(cpu -> cpu.setFlags(ALL_USR_FLAGS),
            "lea $12345678,a1",
            "lea $76543210,a3",
            "exg a1,a3")
            .expectA1(0x76543210)
            .expectA3(0x12345678)
            .carry().overflow().extended().negative().zero().notSupervisor();
    }

    public void testExgAdrData() {

        execute(cpu -> cpu.setFlags(ALL_USR_FLAGS),
            "lea $12345678,a1",
            "move.l #$76543210,d3",
            "exg a1,d3")
            .expectA1(0x76543210)
            .expectD3(0x12345678)
            .carry().overflow().extended().negative().zero().notSupervisor();
    }

    private ExpectionBuilder execute(String program)
    {
        return execute(cpu->{},program);
    }

    private ExpectionBuilder execute(String program,Consumer<CPU> cpuSetup)
    {
        return execute(cpuSetup,program);
    }

    private ExpectionBuilder execute(Consumer<CPU> cpuSetup,String program1,String... additional)
    {
        int insCount = 1 + ( ( additional == null ) ? 0 : additional.length);
        return execute(cpuSetup,insCount,program1,additional);
    }

    private ExpectionBuilder execute(Consumer<CPU> cpuSetup,int insToExecute,
                                     String program1,
                                     String... additional)
    {
        final List<String> lines = new ArrayList<>();
        lines.add(program1);
        if ( additional != null ) {
            Arrays.stream(additional).forEach(lines::add );
        }

        memory.writeLong(0, SUPERVISOR_STACK_PTR); // Supervisor mode stack pointer

        // write reset handler
        // that sets the usermode stack ptr
        // and exits supervisor mode
        final int exceptionHandlerAddress = PROGRAM_START_ADDRESS-128;
        memory.writeLong(4, exceptionHandlerAddress); // PC starting value

        insToExecute += 4; // +4 instructions in reset handler

        final int mask = ~CPU.FLAG_SUPERVISOR_MODE;
        final String resetHandler = "MOVE.L #"+Misc.hex(USERMODE_STACK_PTR)+",A0\n" +
                "MOVE.L A0,USP\n" +
                "AND #"+ Misc.binary16Bit(mask)+",SR\n" +
                "JMP "+Misc.hex(PROGRAM_START_ADDRESS); // clear super visor bit

        final byte[] exceptionHandler = compile(resetHandler);
        memory.writeBytes(exceptionHandlerAddress, exceptionHandler );

        // write the actual program
        final String program = lines.stream().collect(Collectors.joining("\n"));
        final byte[] executable = compile("ORG "+PROGRAM_START_ADDRESS+"\n"+program);
        System.out.println( Memory.hexdump(PROGRAM_START_ADDRESS,executable,0,executable.length) );
        memory.writeBytes(PROGRAM_START_ADDRESS,executable );

        cpu.reset();

        for ( ; insToExecute > 0 ; insToExecute--)
        {
            // assumption is that we want to test the
            // very last instruction so we'll invoke the callback here
            if ( (insToExecute-1) == 0 ) {
                cpuSetup.accept(cpu);
            }
            cpu.executeOneInstruction();
        }
        return new ExpectionBuilder();
    }

    private byte[] compile(String program)
    {
        final Assembler asm = new Assembler();
        compilationUnit = new CompilationUnit(IResource.stringResource(program));
        final CompilationMessages messages = asm.compile(compilationUnit);
        assertFalse(messages.hasErrors());
        return asm.getBytes(false);
    }

    protected final class ExpectionBuilder
    {
        public ExpectionBuilder expectTopOfStack(String label, Function<Integer,Integer> valueTransform)
        {
            final Symbol symbol = compilationUnit.symbolTable.lookup(Identifier.of(label));
            if ( symbol == null ) {
                fail("Undefined symbol: "+label);
            }
            if ( ! symbol.hasValue() ) {
                fail("Symbol "+label+" has no value ?");
            }
            final int expected = valueTransform.apply( symbol.getBits() );
            return expectTopOfStack(expected );
        }

        public ExpectionBuilder expectTopOfStack(int expected)
        {
            int actual = cpu.memory.readLong(cpu.addressRegisters[7] );
            // swap because CPU#push(int) pushes HIGH word first
            actual = (actual >>> 16) | (actual << 16);
            return assertHexEquals( "value on top of stack mismatch" , expected, actual);
        }

        public ExpectionBuilder expectD0(int value) { return assertHexEquals( "D0 mismatch" , value, cpu.dataRegisters[0]); }
        public ExpectionBuilder expectD1(int value) { return assertHexEquals( "D1 mismatch" , value, cpu.dataRegisters[1]); }
        public ExpectionBuilder expectD2(int value) { return assertHexEquals( "D2 mismatch" , value, cpu.dataRegisters[2]); }
        public ExpectionBuilder expectD3(int value) { return assertHexEquals( "D3 mismatch" , value, cpu.dataRegisters[3]); }
        public ExpectionBuilder expectD4(int value) { return assertHexEquals( "D4 mismatch" , value, cpu.dataRegisters[4]); }
        public ExpectionBuilder expectD5(int value) { return assertHexEquals( "D5 mismatch" , value, cpu.dataRegisters[5]); }
        public ExpectionBuilder expectD6(int value) { return assertHexEquals( "D6 mismatch" , value, cpu.dataRegisters[6]); }
        public ExpectionBuilder expectD7(int value) { return assertHexEquals( "D7 mismatch" , value, cpu.dataRegisters[7]); }

        public ExpectionBuilder expectA0(int value) { return assertHexEquals( "A0 mismatch" , value, cpu.addressRegisters[0]); }
        public ExpectionBuilder expectA1(int value) { return assertHexEquals( "A1 mismatch" , value, cpu.addressRegisters[1]); }
        public ExpectionBuilder expectA2(int value) { return assertHexEquals( "A2 mismatch" , value, cpu.addressRegisters[2]); }
        public ExpectionBuilder expectA3(int value) { return assertHexEquals( "A3 mismatch" , value, cpu.addressRegisters[3]); }
        public ExpectionBuilder expectA4(int value) { return assertHexEquals( "A4 mismatch" , value, cpu.addressRegisters[4]); }
        public ExpectionBuilder expectA5(int value) { return assertHexEquals( "A5 mismatch" , value, cpu.addressRegisters[5]); }
        public ExpectionBuilder expectA6(int value) { return assertHexEquals( "A6 mismatch" , value, cpu.addressRegisters[6]); }
        public ExpectionBuilder expectA7(int value) { return assertHexEquals( "A7 mismatch" , value, cpu.addressRegisters[7]); }

        public ExpectionBuilder cycles(int number) {
            assertEquals("Expected CPU cycle count "+number+" but was "+cpu.cycles,number,cpu.cycles);
            return this;
        }

        public ExpectionBuilder expectPC(int value) { return assertHexEquals( "PC mismatch" , value, cpu.pc); }

        public ExpectionBuilder zero() { assertTrue( "Z flag not set ?" , cpu.isZero() ); return this; };
        public ExpectionBuilder notZero() { assertTrue( "Z flag set ?" , cpu.isNotZero() ); return this; };

        public ExpectionBuilder carry() { assertTrue( "C flag not set ?" , cpu.isCarry() ); return this; };
        public ExpectionBuilder noCarry() { assertTrue( "C flag set ?" , cpu.isNotCarry() ); return this; };

        public ExpectionBuilder negative() { assertTrue( "N flag not set ?" , cpu.isNegative() ); return this; };
        public ExpectionBuilder notNegative() { assertTrue( "N flag set ?" , cpu.isNotNegative() ); return this; };

        public ExpectionBuilder overflow() { assertTrue( "V flag not set ?" , cpu.isOverflow() ); return this; };
        public ExpectionBuilder noOverflow() { assertTrue( "V flag set ?" , cpu.isNotOverflow() ); return this; };

        public ExpectionBuilder extended() { assertTrue( "X flag not set ?" , cpu.isExtended() ); return this; };
        public ExpectionBuilder notExtended() { assertTrue( "X flag set ?" , cpu.isNotExtended() ); return this; };

        public ExpectionBuilder supervisor() { assertTrue( "S flag not set ?" , cpu.isSupervisorMode() ); return this; };
        public ExpectionBuilder notSupervisor() { assertTrue( "S flag set ?" , ! cpu.isSupervisorMode()); return this; };

        public ExpectionBuilder expectMemoryByte(int address,int value)
        {
            final int actual = memory.readByte(address);
            if ( value != actual ) {
                fail("Expected "+Misc.hex(value)+" @ "+Misc.hex(address)+" but got "+Misc.hex(actual));
            }
            return this;
        }

        public ExpectionBuilder expectMemoryWord(int address,int value)
        {
            final int actual = memory.readWord(address);
            if ( value != actual ) {
                fail("Expected "+Misc.hex(value)+" @ "+Misc.hex(address)+" but got "+Misc.hex(actual));
            }
            return this;
        }

        public ExpectionBuilder expectMemoryLong(int address,int value)
        {
            final int actual = memory.readLong(address);
            if ( value != actual ) {
                fail("Expected "+Misc.hex(value)+" @ "+Misc.hex(address)+" but got "+Misc.hex(actual));
            }
            return this;
        }





        public ExpectionBuilder noIrqActive()
        {
            assertNull("Expected no CPU active but got "+cpu.activeIrq,cpu.activeIrq);
            return this;
        }

        public ExpectionBuilder irqActive(CPU.IRQ irq) {
            assertEquals("Expected "+irq+" but active IRQ was "+cpu.activeIrq,irq,cpu.activeIrq);
            return this;
        }

        private ExpectionBuilder assertHexEquals(String msg,int expected,int actual)
        {
            if ( expected != actual )
            {
                System.out.println("EXPECTED: "+hex32(expected));
                System.out.println("ACTUAL  : "+hex32(actual));
                fail(msg);
            }
            return this;
        }

        private String hex32(int value) {
            return "$"+StringUtils.leftPad(Integer.toHexString(value), 8 , '0' );
        }
    }
}
