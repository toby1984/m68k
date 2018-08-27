package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.assembler.Assembler;
import de.codersourcery.m68k.assembler.CompilationMessages;
import de.codersourcery.m68k.assembler.CompilationUnit;
import de.codersourcery.m68k.assembler.IObjectCodeWriter;
import de.codersourcery.m68k.assembler.IResource;
import de.codersourcery.m68k.assembler.SRecordHelper;
import de.codersourcery.m68k.assembler.Symbol;
import de.codersourcery.m68k.assembler.arch.CPUType;
import de.codersourcery.m68k.assembler.arch.Condition;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.emulator.memory.MMU;
import de.codersourcery.m68k.emulator.memory.Memory;
import de.codersourcery.m68k.parser.Identifier;
import de.codersourcery.m68k.utils.Misc;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CPUTest extends TestCase
{

    private static final boolean DEBUG_DUMP_BINARY = true;
    private static final File DEBUG_BINARY = new File("/tmp/test.h68");

    private static final int MEM_SIZE = 10*1024;

    private static final int SUPERVISOR_STACK_PTR = MEM_SIZE; // stack grows downwards on M68k
    private static final int USERMODE_STACK_PTR = SUPERVISOR_STACK_PTR-256; // stack grows downwards on M68k

    public static final int ALL_USR_FLAGS = CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW | CPU.FLAG_NEGATIVE | CPU.FLAG_ZERO | CPU.FLAG_EXTENDED;

    // program start address in memory (in bytes)
    public static final int PROGRAM_START_ADDRESS = 4096;

    private MMU mmu;
    private Memory memory;
    private CPU cpu;
    private CompilationUnit compilationUnit;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        mmu = new MMU( new MMU.PageFaultHandler(Amiga.AMIGA_500) );
        memory = new Memory(mmu);
        cpu = new CPU(CPUType.BEST,memory);
    }

    public void testSubx()
    {
        final int adr = PROGRAM_START_ADDRESS + 0x100;

        execute(cpu->{},
                "lea "+adr+",a0",
                "move.l #$ffffff02,(a0)+",
                "move.l #$ffffff03,(a0)+",
                "lea "+(adr+4)+",a1",
                "lea "+(adr+8)+",a2",
                "move.w #0,ccr",
                "subx.b -(a1),-(a2)") // 3 - 2
                .expectA1( adr+3 )
                .expectA2( adr+7 )
                .expectMemoryLong(adr+4,0xffffff01)
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->{},
                "move.l #$ffffff02,d0",
                "move.l #$ffffff03,d1",
                "move.w #0,ccr",
                "subx.b d0,d1") // 3 - 2
                .expectD1( 0xffffff01 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->{},
                "move.l #$ffffff02,d0",
                "move.l #$ffffff03,d1",
                "move.w #"+CPU.FLAG_EXTENDED+",ccr",
                "subx.b d0,d1") // 3 - 2 -1
                .expectD1( 0xffffff00 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->{},
                "move.l #$ffffff02,d0",
                "move.l #$ffffff03,d1",
                "move.w #"+(CPU.FLAG_EXTENDED|CPU.FLAG_ZERO)+",ccr",
                "subx.b d0,d1") // 3 - 2 -1
                .expectD1( 0xffffff00 )
                .zero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();
    }

    public void testSub() {

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                 "move.l #$ffffff01,d0",
                "move.l #$ffffff01,d1",
                "sub.b d0,d1")
                .expectD1( 0xffffff00 )
                .zero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$ffff0001,d0",
                "move.l #$ffff0001,d1",
                "sub.w d0,d1")
                .expectD1( 0xffff0000 )
                .zero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l  #$0001,d0",
                "move.l #$0001,d1",
                "sub.l d0,d1")
                .expectD1( 0x0 )
                .zero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l  #$0001,d0",
                "move.l #$0000,d1",
                "sub.l d0,d1")
                .expectD1( 0xffffffff )
                .notZero()
                .carry()
                .extended()
                .noOverflow()
                .negative()
                .notSupervisor().noIrqActive();
    }

    public void testAdd() {

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$ffffff01,d0",
                "move.l #$ffffff01,d1",
                "add.b d0,d1")
                .expectD1( 0xffffff02 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();
    }

    public void testNegx()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaaaa00,d1",
                "move.w #"+(CPU.FLAG_ZERO|CPU.FLAG_EXTENDED)+",ccr", // clear X flag
                "negx.b d1")
                .expectD1( 0xaaaaaaff )
                .negative()
                .notZero()
                .carry()
                .extended()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaaaa00,d1",
                "move.w #"+CPU.FLAG_ZERO+",ccr", // Set zero flag, clear everything else
                "negx.b d1")
                .expectD1( 0xaaaaaaff )
                .negative()
                .notZero()
                .carry()
                .extended()
                .notSupervisor().noIrqActive();
    }

    public void testCmp()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaaaa01,d0",
                "move.l #$bbbbbb01,d1",
                "cmp.b d0,d1")
                .expectD1( 0xbbbbbb01)
                .equals()
                .extended()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaa0001,d0",
                "move.l #$bbbb0001,d1",
                "cmp.w d0,d1")
                .expectD1( 0xbbbb0001)
                .equals()
                .extended()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #1,d0",
                "move.l #1,d1",
                "cmp.w d0,d1")
                .expectD1( 1 )
                .equals()
                .extended()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaa0002,d0",
                "move.l #$bbbb0003,d1",
                "cmp.w d0,d1") // d1 - d0 = 1
                .expectD1( 0xbbbb0003)
                .greaterThan()
                .greaterThanEquals()
                .extended()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaa0003,d0",
                "move.l #$bbbb0002,d1",
                "cmp.w d0,d1") // d1 - d0 = 1
                .expectD1( 0xbbbb0002)
                .lessThan()
                .lessThanEquals()
                .extended()
                .notSupervisor().noIrqActive();
    }

    public void testCmpi()
    {
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$0,d0",
                "cmpi.b #$1,d0")
                .expectD0( 0x0)
                .notZero()
                .carry()
                .extended()
                .noOverflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$0,d0",
                "cmpi.b #$0,d0")
                .expectD0( 0x0)
                .zero()
                .noCarry()
                .extended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$1,d0",
                "cmpi.b #$0,d0")
                .expectD0( 0x01)
                .notZero()
                .noCarry()
                .extended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();
    }

    public void testCMPM()
    {
        final int adr = PROGRAM_START_ADDRESS+256;

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.b #2,"+adr,
                "move.b #1,"+(adr+1),
                "lea "+adr+",a0",
                "lea "+(adr+1)+",a1",
                "cmpm.b (a0)+,(a1)+") // 1 cmp 2
                .expectA0( adr+1 )
                .expectA1( adr+2 )
                .notZero() // not equals
                .notGreaterThan()
                .notGreaterOrEquals()
                .lessThanEquals()
                .lessThan()
                .extended()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.b #1,"+adr,
                "move.b #1,"+(adr+1),
                "lea "+adr+",a0",
                "lea "+(adr+1)+",a1",
                "cmpm.b (a0)+,(a1)+") // 1 cmp 1
                .expectA0( adr+1 )
                .expectA1( adr+2 )
                .equals()
                .greaterThanEquals()
                .lessThanEquals()
                .notLessThan()
                .notGreaterThan()
                .extended()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.b #1,"+adr,
                "move.b #2,"+(adr+1),
                "lea "+adr+",a0",
                "lea "+(adr+1)+",a1",
                "cmpm.b (a0)+,(a1)+") // 2 cmp 1
                .expectA0( adr+1 )
                .expectA1( adr+2 )
                .notZero() // not equals
                .greaterThan()
                .greaterThanEquals()
                .notLessThan()
                .notLessThanEquals()
                .extended()
                .notSupervisor().noIrqActive();

    }

    public void testSUBI()
    {
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaaaa03,d0",
                "subi.b #$4,d0")
                .expectD0( 0xaaaaaaff )
                .notZero()
                .carry()
                .extended()
                .noOverflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$7f,d0",
                "subi.b #$1,d0")
                .expectD0( 0x7e )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$aaaaffff,d0",
                "subi.w #$1,d0")
                .expectD0( 0xaaaafffe )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #0,d0",
                "subi.l #$12345678,d0")
                .expectD0( 0-0x12345678 )
                .notZero()
                .carry()
                .extended()
                .noOverflow()
                .negative()
                .notSupervisor().noIrqActive();
    }

    public void testADDI()
    {
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$3,d0",
                "addi.b #$4,d0")
                .expectD0( 0x7 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$7f,d0",
                "addi.b #$1,d0")
                .expectD0( 0x80 )
                .notZero()
                .noCarry()
                .noExtended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$ffff,d0",
                "addi.w #$1,d0")
                .expectD0( 0x0 )
                .zero()
                .carry()
                .extended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #0,d0",
                "addi.l #$12345678,d0")
                .expectD0( 0x12345678 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();
    }

    public void testAddx_Byte()
    {
        execute(cpu-> {},
                "move.l #$ffffff00,d0",
                "move.l #$ffffff00,d1",
                "move.w #"+CPU.FLAG_EXTENDED+",ccr",
                "addx.b d0,d1")
                .expectD1( 0xffffff01 )
                .notNegative()
                .notZero()
                .noOverflow()
                .noCarry()
                .noExtended();

        execute(cpu-> {},
                "move.l #$ffffff00,d0",
                "move.l #$ffffff00,d1",
                "move.w #0,ccr",
                "addx.b d0,d1")
                .expectD1( 0xffffff00 )
                .notNegative()
                .zero()
                .noOverflow()
                .noCarry()
                .noExtended();

        execute(cpu-> {},
                "move.l #$ffffffff,d0",
                "move.l #$ffffff01,d1",
                "move.w #0,ccr",
                "addx.b d0,d1")
                .expectD1( 0xffffff00 )
                .notNegative()
                .zero()
                .noOverflow()
                .carry()
                .extended();
    }

    public void testAddx_Word()
    {
        execute(cpu-> {},
                "move.l #$ffff0000,d0",
                "move.l #$ffff0000,d1",
                "move.w #"+CPU.FLAG_EXTENDED+",ccr",
                "addx.w d0,d1")
                .expectD1( 0xffff0001 )
                .notNegative()
                .notZero()
                .noOverflow()
                .noCarry()
                .noExtended();

        execute(cpu-> {},
                "move.l #$ffff0000,d0",
                "move.l #$ffff0000,d1",
                "move.w #0,ccr",
                "addx.w d0,d1")
                .expectD1( 0xffff0000 )
                .notNegative()
                .zero()
                .noOverflow()
                .noCarry()
                .noExtended();

        execute(cpu-> {},
                "move.l #$ffffffff,d0",
                "move.l #$ffff0001,d1",
                "move.w #0,ccr",
                "addx.w d0,d1")
                .expectD1( 0xffff0000 )
                .notNegative()
                .zero()
                .noOverflow()
                .carry()
                .extended();
    }

    public void testAddx_Long()
    {
        execute(cpu-> {},
                "move.l #$00000000,d0",
                "move.l #$00000000,d1",
                "move.w #"+CPU.FLAG_EXTENDED+",ccr",
                "addx.l d0,d1")
                .expectD1( 0x00000001 )
                .notNegative()
                .notZero()
                .noOverflow()
                .noCarry()
                .noExtended();

        execute(cpu-> {},
                "move.l #$00000000,d0",
                "move.l #$00000000,d1",
                "move.w #0,ccr",
                "addx.l d0,d1")
                .expectD1( 0x00000000 )
                .notNegative()
                .zero()
                .noOverflow()
                .noCarry()
                .noExtended();

        execute(cpu-> {},
                "move.l #$ffffffff,d0",
                "move.l #$00000001,d1",
                "move.w #0,ccr",
                "addx.w d0,d1")
                .expectD1( 0x00000000 )
                .notNegative()
                .zero()
                .noOverflow()
                .carry()
                .extended();
    }

    public void testCMPA()
    {
        final int addr = PROGRAM_START_ADDRESS + 256;

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
            "move.w #$ffff," + addr,
            "move.l #" + addr + ",A0",
            "lea 1,A1",
            "cmpa.w (a0),a1")
            .expectA1(0x00000001)
            .notZero()
            .carry()
            .extended()
            .noOverflow()
            .notNegative()
            .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
            "move.l #$ffff0002,a0",
            "move.l #$ffff0001,a1",
            "cmpa.w a1,a0")
            .expectA0(0xffff0002)
            .expectA1(0xffff0001)
            .notZero()
            .noCarry()
            .extended()
            .noOverflow()
            .negative()
            .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
            "move.w #$0001," + addr,
            "move.l #" + addr + ",A0",
            "lea 2,A1",
            "cmpa.w (a0),a1")
            .expectA0(addr)
            .expectA1(0x2)
            .notZero()
            .noCarry()
            .extended()
            .noOverflow()
            .notNegative()
            .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
            "move.l #$00000002," + addr,
            "move.l #" + addr + ",A0",
            "lea 1,A1",
            "cmpa.l (a0),a1")
            .expectA0(addr)
            .expectA1(1)
            .notZero()
            .carry()
            .extended()
            .noOverflow()
            .negative();
    }

    public void testSUBA()
    {
        final int addr = PROGRAM_START_ADDRESS + 256;

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$ffff0002,a0",
                "move.l #$ffff0001,a1",
                "suba.w a1,a0")
                .expectA0(0xffff0001)
                .expectA1(0xffff0001)
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.w #$0001," + addr,
                "move.l #" + addr + ",A0",
                "lea 2,A1",
                "suba.w (a0),a1")
                .expectA1(0x1)
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.w #$ffff," + addr,
                "move.l #" + addr + ",A0",
                "lea 1,A1",
                "suba.w (a0),a1")
                .expectA1(0x02)
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$00000002," + addr,
                "move.l #" + addr + ",A0",
                "lea 1,A1",
                "suba.l (a0),a1")
                .expectA1(0xffffffff)
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative();
    }

    public void testADDA()
    {
        final int addr = PROGRAM_START_ADDRESS+256;

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$ffff0000,a0",
                "adda.w #1,a0")
                .expectA0( 0xffff0001 )
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.w #$0001,"+addr,
                "move.l #"+addr+",A0",
                "lea 0,A1",
                "adda.w (a0),a1")
                .expectA1( 0x1 )
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.w #$ffff,"+addr,
                "move.l #"+addr+",A0",
                "lea 1,A1",
                "adda.w (a0),a1")
                .expectA1( 0x0 )
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$00000001,"+addr,
                "move.l #"+addr+",A0",
                "lea 1,A1",
                "adda.l (a0),a1")
                .expectA1( 0x02 )
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();
    }

    public void testAddq()
    {
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$7fff,d3",
                "addq.w #1,d3")
                .expectD3( 0x8000 )
                .notZero()
                .noCarry()
                .noExtended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "addq.w #1,d3")
                .expectD3( 0xffff0000 )
                .zero()
                .carry()
                .extended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffff00,d3",
                "addq.b #1,d3")
                .expectD3( 0xffffff01 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffff0000,d3",
                "addq.w #1,d3")
                .expectD3( 0xffff0001 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #0,a0",
                "addq.l #1,a0")
                .expectA0(0x1)
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();
    }

    public void testSubq()
    {
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$7fff,d3",
                "subq.w #1,d3")
                .expectD3( 0x7ffe )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffff0000,d3",
                "subq.w #1,d3")
                .expectD3( 0xffffffff )
                .notZero()
                .carry()
                .extended()
                .noOverflow()
                .negative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffff02,d3",
                "subq.b #1,d3")
                .expectD3( 0xffffff01 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffff0002,d3",
                "subq.w #1,d3")
                .expectD3( 0xffff0001 )
                .notZero()
                .noCarry()
                .noExtended()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #2,a0",
                "subq.l #1,a0")
                .expectA0(0x1)
                .zero()
                .carry()
                .extended()
                .overflow()
                .negative()
                .notSupervisor().noIrqActive();
    }

    public void testORI() {

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffff00,d3",
                "ori.b #$12,d3")
                .expectD3(0xffffff12 ).notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffff1200,d3",
                "ori.w #$12,d3")
                .expectD3(0xffff1212 ).notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #0,d3",
                "ori.l #$12345678,d3")
                .expectD3(0x12345678 )
                .notZero().notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #0,d3",
                "ori.w #$0,d3")
                .expectD3(0x0)
                .zero()
                .notNegative()
                .noCarry()
                .noOverflow()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$8000,d3",
                "ori.w #$0,d3")
                .expectD3(0x8000)
                .notZero()
                .negative()
                .noCarry()
                .noOverflow()
                .notSupervisor().noIrqActive();
    }

    public void testDivu()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #0,d2",
                "move.l #3,d3",
                "divu.w d2,d3") // d3 / d2
                .expectD2(0x00)
                .expectD3(0x03)
                .zero()
                .overflow()
                .negative()
                .extended() // not affected
                .carry() // always cleared
                .supervisor().irqActive(CPU.IRQ.INTEGER_DIVIDE_BY_ZERO);

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #3,d2",
                "move.l #6,d3",
                "divu.w d2,d3") // d3 / d2
                .expectD2(0x03)
                .expectD3(0x02)
                .notZero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #3,d2",
                "move.l #7,d3",
                "divu.w d2,d3") // d3 / d2
                .expectD2(0x03)
                .expectD3((0x01<<16) | 0x02)
                .notZero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #2,d2",
                "move.l #1,d3",
                "divu.w d2,d3") // d3 / d2
                .expectD2(0x02)
                .expectD3((0x01<<16) | 0x00)
                .zero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #1,d2",
                "move.l #$ffffffff,d3",
                "divu.w d2,d3") // d3 / d2
                .expectD2(0x01)
                .expectD3(0xffff)
                .notZero()
                .overflow()
                .negative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();
    }

    public void testDivs()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #0,d2",
                "move.l #3,d3",
                "divs.w d2,d3") // d3 / d2
                .expectD2(0x00)
                .expectD3(0x03)
                .zero()
                .overflow()
                .negative()
                .extended() // not affected
                .carry() // always cleared
                .supervisor().irqActive(CPU.IRQ.INTEGER_DIVIDE_BY_ZERO);

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #3,d2",
                "move.l #6,d3",
                "divs.w d2,d3") // d3 / d2
                .expectD2(0x03)
                .expectD3(0x02)
                .notZero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #3,d2",
                "move.l #7,d3",
                "divs.w d2,d3") // d3 / d2
                .expectD2(0x03)
                .expectD3((0x01<<16) | 0x02)
                .notZero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #2,d2",
                "move.l #1,d3",
                "divs.w d2,d3") // d3 / d2
                .expectD2(0x02)
                .expectD3((0x01<<16) | 0x00)
                .zero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #1,d2",
                "move.l #$ffffffff,d3",
                "divs.w d2,d3") // d3 / d2
                .expectD2(0x01)
                .expectD3(0xffff)
                .notZero()
                .noOverflow()
                .negative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();
    }

    public void testMulu()
    {
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #3,d2",
                "move.l #4,d3",
                "mulu.w d2,d3")
                .expectD2(0x03)
                .expectD3(12)
                .notZero()
                .noOverflow() // cannot happen on 68000 with 16x16 operands
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #2,d2",
                "move.l #$ffff,d3",
                "mulu.w d2,d3")
                .expectD2(2)
                .expectD3(0x1fffe)
                .notZero()
                .noOverflow() // cannot happen on 68000 with 16x16 operands
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();
    }

    public void testMuls()
    {
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #2,d2",
                "move.l #$ffff,d3",
                "muls.w d2,d3")
                .expectD2(2)
                .expectD3(0xfffffffe)
                .notZero()
                .noOverflow()
                .negative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #3,d2",
                "move.l #4,d3",
                "muls.w d2,d3")
                .expectD2(0x03)
                .expectD3(12)
                .notZero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$8fff,d2",
                "move.l #$ffff,d3",
                "muls.w d2,d3")
                .expectD2(0x8fff)
                .expectD3(0x00007001)
                .notZero()
                .noOverflow()
                .notNegative()
                .extended() // not affected
                .noCarry() // always cleared
                .notSupervisor().noIrqActive();
    }

    public void testEOR() {

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #0,d2",
                "move.l #0,d3",
                "eor.b d2,d3")
                .expectD2(0x00)
                .expectD3(0x00)
                .zero()
                .noCarry()
                .noOverflow()
                .notNegative()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$ffff,d2",
                "move.l #$12340000,d3",
                "eor.w d2,d3")
                .expectD2(0xffff)
                .expectD3(0x1234ffff)
                .notZero()
                .negative()
                .noCarry()
                .noOverflow()
                .notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$0,d2",
                "move.l #$ffffffff,d3",
                "eor.l d2,d3")
                .expectD2(0)
                .expectD3(0xffffffff)
                .notZero()
                .negative()
                .noCarry()
                .noOverflow()
                .notSupervisor().noIrqActive();
    }

    public void testEORI() {

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffff55,d3",
                "eori.b #$00,d3")
                .expectD3(0xffffff55 ).notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffff0055,d3",
                "eori.w #$55,d3")
                .expectD3(0xffff0000 ).notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #0,d3",
                "eori.l #$55555555,d3")
                .expectD3(0x55555555 ).notSupervisor().noIrqActive();
    }

    public void testMovePWordToMemory()
    {
        final int adr = PROGRAM_START_ADDRESS+256;
        final int adrPlusOffset = adr + 10;
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$12345678,d4",
                "lea "+adr+",a3",
                "movep.w d4,10(a3)")
                .zero()
                .extended()
                .negative()
                .overflow()
                .carry()
                .expectMemoryByte(adrPlusOffset, 0x56)
                .expectMemoryByte(adrPlusOffset+2, 0x78)
                .notSupervisor().noIrqActive();
    }

    public void testMovePLongToMemory()
    {
        final int adr = PROGRAM_START_ADDRESS+256;
        final int adrPlusOffset = adr + 10;
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.l #$12345678,d4",
                "lea "+adr+",a3",
                "movep.l d4,10(a3)")
                .zero()
                .extended()
                .negative()
                .overflow()
                .carry()
                .expectMemoryByte(adrPlusOffset, 0x12)
                .expectMemoryByte(adrPlusOffset+2, 0x34)
                .expectMemoryByte(adrPlusOffset+4, 0x56)
                .expectMemoryByte(adrPlusOffset+6, 0x78)
                .notSupervisor().noIrqActive();
    }

    public void testMovePWordFromMemory()
    {
        final int adr = PROGRAM_START_ADDRESS+256;
        final int adrPlusOffset = adr + 10;
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.b #$12,"+(adrPlusOffset),
                "move.b #$34,"+(adrPlusOffset+2),
                "move.b #$56,"+(adrPlusOffset+4),
                "move.b #$78,"+(adrPlusOffset+6),
                "move.l #$ffffffff,d4",
                "lea "+adr+",a3",
                "movep.w 10(a3),d4")
                .expectD4(0xffff1234)
                .zero()
                .extended()
                .negative()
                .overflow()
                .carry()
                .notSupervisor().noIrqActive();
    }

    public void testMovePLongFromMemory()
    {
        final int adr = PROGRAM_START_ADDRESS+256;
        final int adrPlusOffset = adr + 10;
        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.b #$12,"+(adrPlusOffset),
                "move.b #$34,"+(adrPlusOffset+2),
                "move.b #$56,"+(adrPlusOffset+4),
                "move.b #$78,"+(adrPlusOffset+6),
                "move.l #$ffffffff,d4",
                "lea "+adr+",a3",
                "movep.l 10(a3),d4")
                .expectD4(0x12345678)
                .zero()
                .extended()
                .negative()
                .overflow()
                .carry()
                .notSupervisor().noIrqActive();
    }

    public void testMoveToCCR() {

        final int adr = PROGRAM_START_ADDRESS+256;
        execute(cpu->cpu.clearFlags(CPU.ALL_USERMODE_FLAGS),
                "move.w #$ffff,"+adr,
                "lea "+adr+",a0",
                "move.w (a0)+,ccr")
                .zero().extended().negative().overflow().carry().notSupervisor().noIrqActive();

        execute(cpu->cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "move.w #$0000,"+adr,
                "move.w "+adr+",ccr")
                .notZero().noExtended().notNegative().noOverflow().noCarry().notSupervisor().noIrqActive();
    }

    public void testMOVEMToMemoryLong()
    {
        final int adr = PROGRAM_START_ADDRESS+256;
        final int adrExpected = adr - 6*4;

        System.out.println("Address: "+Misc.hex(adr) );
        System.out.println("Expected address: "+Misc.hex(adrExpected) );
        execute(cpu->{},
                "lea "+adr+",a3",
                "move.l #1,d0",
                "move.l #2,d1",
                "move.l #3,d2",
                "move.l #4,a0",
                "move.l #5,a1",
                "move.l #6,a2",
                "movem.l d0-d2/a0-a2,-(a3)"
        ).notSupervisor().noIrqActive()
                .expectA3( adrExpected )
                .expectMemoryLongs(adrExpected,1,2,3,4,5,6);
    }

    public void testMOVEMToMemoryWord()
    {
        final int adr = PROGRAM_START_ADDRESS+256;
        final int expectedAdr = adr - 6*2;

        execute(cpu->{},
                "lea "+adr+",a3",
                "move.l #1,d0",
                "move.l #2,d1",
                "move.l #3,d2",
                "move.l #4,a0",
                "move.l #5,a1",
                "move.l #6,a2",
                "movem.w d0-d2/a0-a2,-(a3)"
        ).notSupervisor().noIrqActive()
                .expectMemoryWords(expectedAdr,1,2,3,4,5,6)
                .expectA3( expectedAdr );
    }

    public void testMOVEMToMemoryWord2()
    {
        final int adr = PROGRAM_START_ADDRESS+128;

        execute(cpu->{},
                "lea "+adr+",a3",
                "move.l #1,d0",
                "move.l #2,d1",
                "move.l #3,d2",
                "move.l #4,a0",
                "move.l #5,a1",
                "move.l #6,a2",
                "movem.w d0-d2/a0-a2,"+adr
        ).notSupervisor().noIrqActive()
                .expectMemoryWords(adr,1,2,3,4,5,6)
                .expectA3( adr );
    }

    public void testMOVEMFromMemoryWords()
    {
        final int adr = PROGRAM_START_ADDRESS+128;

        execute(cpu-> writeWords(adr,1,2,3,4,5,6),
                "movem.w "+adr+",d0-d2/a0-a2"
        ).notSupervisor().noIrqActive()
                .expectD0(1)
                .expectD1(2)
                .expectD2(3)
                .expectA0(4)
                .expectA1(5)
                .expectA2(6);
    }

    public void testMOVEMFromMemoryLong()
    {
        final int adr = PROGRAM_START_ADDRESS+128;

        execute(cpu-> writeLongs(adr,1,2,3,4,5,6),
                "movem.l "+adr+",d0-d2/a0-a2"
        ).notSupervisor().noIrqActive()
                .expectD0(1)
                .expectD1(2)
                .expectD2(3)
                .expectA0(4)
                .expectA1(5)
                .expectA2(6);
    }

    public void testMoveMRoundTrip()
    {
        final int adr = PROGRAM_START_ADDRESS+256;

        execute(cpu->{},
                "lea "+adr+",a3",
                "move.l #1,d0",
                "move.l #2,d1",
                "move.l #3,d2",
                "move.l #4,a0",
                "move.l #5,a1",
                "move.l #6,a2",
                "movem.l d0-d2/a0-a2,-(a3)",
                "clr.l d0",
                "clr.l d1",
                "clr.l d2",
                "movea #0,a0",
                "movea #0,a1",
                "movea #0,a2",
                "movem.l (a3)+,d0-d2/a0-a2"
        ).notSupervisor().noIrqActive()
                .expectD0(1)
                .expectD1(2)
                .expectD2(3)
                .expectA0(4)
                .expectA1(5)
                .expectA2(6)
                .expectA3(adr);
    }

    public void testLEA()
    {
        execute("lea $12345678,a0", cpu -> cpu.setFlags(ALL_USR_FLAGS))
                .expectA0(0x12345678).carry().overflow().extended().negative().zero().notSupervisor();
    }

    public void testLEA3()
    {
        execute("lea $40000,a7", cpu -> cpu.setFlags(ALL_USR_FLAGS))
                .expectA7(0x40000).carry().overflow().extended().negative().zero().notSupervisor();
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
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "lsl.b #1,d3")
                .expectD3(  0x12345602 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "lsl.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().noExtended();
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
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "asr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "asr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "asr.b #1,d3")
                .expectD3(  0x123456C0 ).notZero().noCarry().negative().noOverflow().noExtended();
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
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "asl.b #1,d3")
                .expectD3(  0x12345602 ).notZero().notNegative().noCarry().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "asl.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "asl.b #1,d3")
                .expectD3(  0x12345600 ).zero().carry().notNegative().overflow().extended();
    }

    public void testRoxlByte()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_EXTENDED),
                "move.l #0,d2",
                "move.l #0,d3",
                "roxl.b d2,d3")
                .expectD3( 0 ).zero().notNegative().carry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY ),
                "move.l #0,d2",
                "move.l #0,d3",
                "roxl.b d2,d3")
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "roxl.b #1,d3")
                .expectD3(  0x12345602 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "roxl.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "roxl.b #1,d3")
                .expectD3(  0x12345601 ).notZero().carry().notNegative().noOverflow().extended();
    }

    public void testRoxlWord()
    {
        final int adr = PROGRAM_START_ADDRESS+ 128;
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,"+adr,
                "roxl "+(adr+2))
                .expectMemoryLong( adr,0x12340001 )
                .carry().extended().notZero().notNegative();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                "roxl.w #1,d3")
                .expectD3(  0x12340002 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "roxl.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                "roxl.w #1,d3")
                .expectD3(  0x12340001 ).notZero().carry().notNegative().noOverflow().extended();
    }
    public void testRoxlLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                "move.l #1,d1", // hack to clear N flag set by move #$80000000
                "roxl.l #1,d3")
                .expectD3(  0x00000001 ).notZero().carry().notNegative().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                "roxl.l #1,d3")
                .expectD3(  0x00000002 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "roxl.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().noExtended();
    }

    public void testRoxrByte() {
        execute( cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_EXTENDED),
                "move.l #0,d2",
                "move.l #0,d3",
                "roxr.b d2,d3")
                .expectD3( 0 ).zero().notNegative().carry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "roxr.b #1,d3")
                .expectD3(  0x12345680 ).notZero().negative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "roxr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "roxr.b #1,d3")
                .expectD3(  0x12345640 ).notZero().noCarry().notNegative().noOverflow().noExtended();
    }
    public void testRoxrWord() {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                "roxr.w #1,d3")
                .expectD3(  0x12348000 ).notZero().negative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "roxr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                "roxr.w #1,d3")
                .expectD3(  0x12344000 ).notZero().noCarry().notNegative().noOverflow().noExtended();
    }

    public void testRoxrLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                "roxr.l #1,d3")
                .expectD3(  0x40000000 ).notZero().noCarry().notNegative().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                "roxr.l #1,d3")
                .expectD3(  0x80000000 ).notZero().negative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "roxr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().noExtended();
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
                .expectD3(  0x12340002 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "asl.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                "asl.w #1,d3")
                .expectD3(  0x12340000 ).zero().carry().notNegative().overflow().extended();
    }

    public void testLslWord() {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                "lsl.w #1,d3")
                .expectD3(  0x12340002 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "lsl.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().noExtended();
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
                .expectD3(  0x00000002 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "asl.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().noExtended();
    }

    public void testLslLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                "move.l #1,d1", // hack to clear N flag set by move #$80000000
                "lsl.l #1,d3")
                .expectD3(  0x00000000 ).zero().carry().notNegative().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                "lsl.l #1,d3")
                .expectD3(  0x00000002 ).notZero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "lsl.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().noExtended();
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
                .expectD3( 0 ).zero().notNegative().noCarry().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345601,d3",
                "lsr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345600,d3",
                "lsr.b #1,d3")
                .expectD3(  0x12345600 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12345680,d3",
                "lsr.b #1,d3")
                .expectD3(  0x12345640 ).notZero().noCarry().notNegative().noOverflow().noExtended();
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
                .noCarry().noExtended().notZero().notNegative();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                "asr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "asr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                "asr.w #1,d3")
                .expectD3(  0x1234c000 ).notZero().noCarry().negative().noOverflow().noExtended();
    }

    public void testLsrWord()
    {
        final int adr = PROGRAM_START_ADDRESS+ 128;
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340002,"+adr,
                "lsr "+(adr+2))
                .expectMemoryLong( adr,0x12340001 )
                .noCarry().noExtended().notZero().notNegative();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340001,d3",
                "lsr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12340000,d3",
                "lsr.w #1,d3")
                .expectD3(  0x12340000 ).zero().notNegative().noCarry().noOverflow().noExtended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$12348000,d3",
                "lsr.w #1,d3")
                .expectD3(  0x12344000 ).notZero().noCarry().notNegative().noOverflow().noExtended();
    }

    public void testSTOP()
    {
        execute( cpu -> {},1,true,
                "stop #1234")
                .waitingForInterrupt();
    }

    public void testBasicAND() {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "and.b d2,d3")
                .expectD2(0x0)
                .expectD3(0x0)
                .zero().notNegative().noCarry().noOverflow().extended();
    }

    public void testOR_Byte()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffff00,d3",
                "or.b d2,d3")
                .expectD2(0x00)
                .expectD3(0xffffff00)
                .zero().notNegative().noCarry().noOverflow().extended();


        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffff00,d2",
                "move.l #$12,d3",
                "or.b d2,d3")
                .expectD2(0xffffff00)
                .expectD3(0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        // ($1200) = d3 & ($1200)
        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "or.b d3,"+adr)
                .expectD3(0xffffffff)
                .dumpMemory(adr,12)
                .expectMemoryByte(adr, 0xff)
                .notZero().negative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffffff,d3",
                "or.b d2,d3")
                .expectD2(0x0)
                .expectD3(0xffffffff)
                .notZero().negative().noCarry().noOverflow().extended();

        // d3 = ($1200) & d3
        // d3 = $12 & 0xff
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "or.b "+adr+",d3")
                .expectD3(0xffffffff)
                .expectMemoryByte(adr, 0x12)
                .notZero().negative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "or.b d3,d2")
                .expectD2(0xffffffff)
                .expectD3(0x12)
                .notZero().negative().noCarry().noOverflow().extended();
    }

    public void testOR_Word()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffff00,d3",
                "or.w d2,d3")
                .expectD2(0x00)
                .expectD3(0xffffff00)
                .notZero().negative().noCarry().noOverflow().extended();


        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffff00,d2",
                "move.l #$12,d3",
                "or.w d2,d3")
                .expectD2(0xffffff00)
                .expectD3(0xff12)
                .notZero().negative().noCarry().noOverflow().extended();

        // ($1200) = d3 & ($1200)
        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "or.w d3,"+adr)
                .expectD3(0xffffffff)
                .dumpMemory(adr,12)
                .expectMemoryByte(adr, 0xff)
                .notZero().negative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffffff,d3",
                "or.w d2,d3")
                .expectD2(0x0)
                .expectD3(0xffffffff)
                .notZero().negative().noCarry().noOverflow().extended();

        // d3 = ($1200) & d3
        // d3 = $12 & 0xff
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "or.w "+adr+",d3")
                .expectD3(0xffffffff)
                .expectMemoryByte(adr, 0x12)
                .notZero().negative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "or.w d3,d2")
                .expectD2(0xffffffff)
                .expectD3(0x12)
                .notZero().negative().noCarry().noOverflow().extended();
    }

    public void testOR_Long()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffff00,d3",
                "or.l d2,d3")
                .expectD2(0x00)
                .expectD3(0xffffff00)
                .notZero().negative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffff00,d2",
                "move.l #$12,d3",
                "or.l d2,d3")
                .expectD2(0xffffff00)
                .expectD3(0xffffff12)
                .notZero().negative().noCarry().noOverflow().extended();

        // ($1200) = d3 & ($1200)
        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "or.l d3,"+adr)
                .expectD3(0xffffffff)
                .dumpMemory(adr,12)
                .expectMemoryByte(adr, 0xff)
                .notZero().negative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffffff,d3",
                "or.l d2,d3")
                .expectD2(0x0)
                .expectD3(0xffffffff)
                .notZero().negative().noCarry().noOverflow().extended();

        // d3 = ($1200) & d3
        // d3 = $12 & 0xff
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "or.l "+adr+",d3")
                .expectD3(0xffffffff)
                .expectMemoryByte(adr, 0x12)
                .notZero().negative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "or.l d3,d2")
                .expectD2(0xffffffff)
                .expectD3(0x12)
                .notZero().negative().noCarry().noOverflow().extended();
    }

    public void testAND_Byte()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffffff,d3",
                "and.b d2,d3")
                .expectD2(0x0)
                .expectD3(0xffffff00)
                .zero().notNegative().noCarry().noOverflow().extended();

        // ($1200) = d3 & ($1200)
        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "and.b d3,"+adr)
                .expectD3(0xffffffff)
                .expectMemoryByte(adr, 0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        // d3 = ($1200) & d3
        // d3 = $12 & 0xff
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.b #$12,"+adr,
                "and.b "+adr+",d3")
                .expectD3(0xffffff12)
                .expectMemoryByte(adr, 0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "and.b d2,d3")
                .expectD2(0xffffffff)
                .expectD3(0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "and.b d3,d2")
                .expectD2(0xffffff12)
                .expectD3(0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();
    }

    public void testAND_Word()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffffff,d3",
                "and.w d2,d3")
                .expectD2(0x0)
                .expectD3(0xffff0000)
                .zero().notNegative().noCarry().noOverflow().extended();

        // ($1200) = d3 & ($1200)
        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.w #$12,"+adr,
                "and.w d3,"+adr)
                .expectD3(0xffffffff)
                .expectMemoryWord(adr, 0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        // d3 = ($1200) & d3
        // d3 = $12 & 0xff
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.w #$12,"+adr,
                "and.w "+adr+",d3")
                .expectD3(0xffff0012)
                .expectMemoryWord(adr, 0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "and.w d2,d3")
                .expectD2(0xffffffff)
                .expectD3(0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "and.w d3,d2")
                .expectD2(0xffff0012)
                .expectD3(0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();
    }

    public void testAND_Long()
    {
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$0,d2",
                "move.l #$ffffffff,d3",
                "and.l d2,d3")
                .expectD2(0x0)
                .expectD3(0x0)
                .zero().notNegative().noCarry().noOverflow().extended();

        // ($1200) = d3 & ($1200)
        final int adr = PROGRAM_START_ADDRESS + 128;
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.l #$12,"+adr,
                "and.l d3,"+adr)
                .expectD3(0xffffffff)
                .expectMemoryLong(adr, 0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        // d3 = ($1200) & d3
        // d3 = $12 & 0xff
        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d3",
                "move.l #$12,"+adr,
                "and.l "+adr+",d3")
                .expectD3(0x12)
                .expectMemoryLong(adr, 0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "and.l d2,d3")
                .expectD2(0xffffffff)
                .expectD3(0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute(cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),"move.l #$ffffffff,d2",
                "move.l #$12,d3",
                "and.l d3,d2")
                .expectD2(0x12)
                .expectD3(0x12)
                .notZero().notNegative().noCarry().noOverflow().extended();
    }

    public void testEORI_SR()
    {
        execute( cpu -> cpu.setIRQLevel(0b111),1,true,
                "eori.w #"+(CPU.FLAG_I1)+",sr")
                .noIrqActive().expectIRQLevel(5);

        execute( cpu -> cpu.setIRQLevel(0b111),
                "eori.w #$ffff,sr")
                .irqActive(CPU.IRQ.PRIVILEGE_VIOLATION);
    }

    public void testANDISR()
    {
        execute( cpu -> cpu.setIRQLevel(0b111),1,true,
                "and #"+(0xffff & ~CPU.FLAG_I0)+",sr")
                .noIrqActive().expectIRQLevel(6);

        execute( cpu -> cpu.setIRQLevel(0b111),
                "and #"+(0xffff & ~CPU.FLAG_I0)+",sr")
                .irqActive(CPU.IRQ.PRIVILEGE_VIOLATION);
    }

    public void testANDI_Byte()
    {
        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffffffff,d3",
                "and.b #%01010101,d3")
                .expectD3(  0xffffff55 )
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffffff00,d3",
                "and.b #%01010101,d3")
                .expectD3(  0xffffff00 )
                .zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffffffff,d3",
                "and.b #%10000000,d3")
                .expectD3(  0xffffff80 )
                .notZero().negative().noCarry().noOverflow().extended();
    }

    public void testANDI_Word()
    {
        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffffffff,d3",
                "and.w #%0101010101010101,d3")
                .expectD3(  0xffff5555 )
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffff0000,d3",
                "and.w #%0101010101010101,d3")
                .expectD3(  0xffff0000 )
                .zero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffffffff,d3",
                "and.w #%1000000000000000,d3")
                .expectD3(  0xffff8000 )
                .notZero().negative().noCarry().noOverflow().extended();
    }

    public void testANDI_Long()
    {
        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffffffff,d3",
                "and.l #$00800000,d3")
                .expectD3(  0x00800000 )
                .notZero().notNegative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$ffffffff,d3",
                "and.l #$ffffffff,d3")
                .expectD3(  0xffffffff )
                .notZero().negative().noCarry().noOverflow().extended();

        execute( cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ), "move.l #$0,d3",
                "and.l #$ffffffff,d3")
                .expectD3(  0x0 )
                .zero().notNegative().noCarry().noOverflow().extended();
    }

    public void testEORIToCCR()
    {
        execute( cpu -> cpu.setFlags(CPU.ALL_USERMODE_FLAGS),
                "eori.b #$ff,ccr")
                .notZero().noExtended().notNegative().noCarry().noOverflow()
                .noIrqActive();
    }

    public void testANDICCR()
    {
        execute( cpu -> cpu.setFlags(CPU.FLAG_ZERO),
                "and #"+(0xff & ~CPU.FLAG_ZERO)+",ccr")
                .notZero().noIrqActive();
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

//    public void testChkLong() { // MC68020+
//
//        // $ffff < 0 || $ffff > $0a
//        execute( cpu -> cpu.setFlags(CPU.FLAG_NEGATIVE),
//                "move.l #$1234ffff,d5", // value to check
//                "move.l #$2234000a,d4", // upper bound
//                "chk.l d4,d5")
//                .negative().noIrqActive();
//
//        // $b < 0 || $b > $0a
//        execute( cpu -> {},
//                "move.l #$1234000b,d5", // value to check
//                "move.l #$2234000a,d4", // upper bound
//                "chk.l d4,d5").notNegative().noIrqActive();
//
//        // $9 < 0 || $9 > $0a
//        execute( cpu -> cpu.setFlags(CPU.FLAG_NEGATIVE),
//                "move.l #$12340009,d5", // value to check
//                "move.l #$2234000a,d4", // upper bound
//                "chk.l d4,d5")
//                .negative().noIrqActive();
//    }

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
                .expectD3(  0xc0000000 ).notZero().noCarry().negative().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                "asr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "asr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().noExtended();
    }

    public void testLsrLong()
    {
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$80000000,d3",
                "lsr.l #1,d3")
                .expectD3(  0x40000000 ).notZero().noCarry().notNegative().noOverflow().noExtended();

        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000001,d3",
                "lsr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().carry().noOverflow().extended();
        execute( cpu -> cpu.setFlags( CPU.FLAG_EXTENDED ), "move.l #$00000000,d3",
                "lsr.l #1,d3")
                .expectD3(  0x00000000 ).zero().notNegative().noCarry().noOverflow().noExtended();
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

        execute("move.b #$12,d0").expectD0(0x12).noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
        execute("move.b #$00,d0").expectD0(0x00).noCarry().noOverflow().noExtended().notNegative().zero().notSupervisor();
        execute("move.b #$ff,d0").expectD0(0xff).noCarry().noOverflow().noExtended().negative().notZero().notSupervisor();
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
                .expectD3(0x56781234).noOverflow().notNegative().notZero().noExtended().notSupervisor();
    }

    public void testUnlink()
    {
        execute( cpu -> {},3,
                "move.l #$56781234,(2020)",
                "move.l #2020,a3",
                "unlk a3" )
                .expectA3(0x12345678)
                .expectA7(2024).noOverflow().notNegative().notZero().noExtended().notSupervisor();
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
                .expectD0(0).zero().notNegative().noCarry().noExtended();

        execute(cpu->{},2,"move.l #$12345601,d0", "neg.b d0")
                .expectD0(0x123456ff).notZero().negative().carry().extended();

        execute(cpu->{},2,"move.l #$12340001,d0", "neg.w d0")
                .expectD0(0x1234ffff).notZero().negative().carry().extended();

        execute(cpu->{},2,"move.l #$00000001,d0", "neg.l d0")
                .expectD0(0xffffffff).notZero().negative().carry().extended();
    }

    public void testLink()
    {
        System.out.println("USP = "+Misc.hex(USERMODE_STACK_PTR));
        execute( "link a4,#$fffe" )
                .expectA4(USERMODE_STACK_PTR-4)
                .expectA7(USERMODE_STACK_PTR-6).noOverflow().notNegative().notZero().noExtended().notSupervisor();
    }

    public void testSWAP2() {
        execute(cpu-> cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW), 2, "move.l #0,D3",
                "swap d3")
                .expectD3(0).noOverflow().notNegative().zero().noExtended().notSupervisor();
    }

    public void testSWAP3() {
        execute(cpu-> cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW), 2, "move.l #$ffffffff,D3",
                "swap d3")
                .expectD3(0xffffffff).noOverflow().negative().notZero().noExtended().notSupervisor();
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
                .noExtended()
                .notSupervisor();

        execute(cpu-> {}, 3, "move.l #1,D0",
                "loop: dbra d0,loop").expectD0(0xffff).noOverflow().notNegative().notZero().noExtended().notSupervisor();
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
                .noExtended()
                .notSupervisor();

        execute(cpu-> {}, 3, "move.l #1,D0",
                "loop: dbra d0,loop").expectD0(0xffff).noOverflow().notNegative().notZero().noExtended().notSupervisor();
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
                "loop: dbeq d0,loop").expectD0(1).noOverflow().notNegative().zero().noExtended().notSupervisor();

        execute(cpu-> {}, 3, "move.l #1,D0",
                "loop: dbra d0,loop").expectD0(0xffff).noOverflow().notNegative().notZero().noExtended().notSupervisor();
    }

    public void testMoveByteClearsFlags() {
        execute("move.b #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12)
                .noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveWordClearsFlags() {
        execute("move.w #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12).noCarry()
                .noOverflow().noExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveLongClearsFlags() {
        execute("move.l #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12).noCarry().noOverflow().noExtended().notNegative().notZero();
    }

    public void testMoveWordImmediate() {

        execute("move.w #$1234,d0").expectD0(0x1234).noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
        execute("move.w #$00,d0").expectD0(0x00).noCarry().noOverflow().noExtended().notNegative().zero().notSupervisor();
        execute("move.w #$ffff,d0").expectD0(0xffff).noCarry().noOverflow().noExtended().negative().notZero().notSupervisor();
    }

    public void testMoveLongImmediate() {

        execute("move.l #$fffffffe,d0").expectD0(0xfffffffe).noCarry().noOverflow().noExtended().negative().notZero().notSupervisor();
        execute("move.l #$00,d0").expectD0(0x00).noCarry().noOverflow().noExtended().notNegative().zero().notSupervisor();
        execute("move.l #$12345678,d0").expectD0(0x12345678).noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveQClearsCarryAndOverflow()
    {
        execute("moveq #$70,d0", cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW) ).expectD0(0x70)
                .noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveQ()
    {
        execute("moveq #$70,d0").expectD0(0x70).noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
        execute("moveq #$0,d0").expectD0(0x0).noCarry().noOverflow().noExtended().notNegative().zero().notSupervisor();
        execute("moveq #$ff,d0").expectD0(0xffffffff).noCarry().noOverflow().noExtended().negative().notZero().notSupervisor();
    }

    public void testBRA() {
        System.out.println("Program start is at "+Misc.hex(PROGRAM_START_ADDRESS));
        execute(cpu -> {},"BRA next\nILLEGAL\nnext:").expectPC( PROGRAM_START_ADDRESS + 4).notSupervisor();
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
        System.out.println("Program start is at "+Misc.hex(PROGRAM_START_ADDRESS));
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
                .noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
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
                .noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
    }

    public void testMoveByte()
    {
        execute(cpu -> {} ,
                "move.l #$12345678,d1",
                "move.b #$00,d1")
                .expectD1(0x12345600)
                .noCarry().noOverflow().noExtended().notNegative().zero().notSupervisor();
        execute(cpu -> {} ,
                "move.l #$12345678,d1",
                "move.b #$ff,d1")
                .expectD1(0x123456ff)
                .noCarry().noOverflow().noExtended().negative().notZero().notSupervisor();
    }

    public void testMoveWord()
    {
        execute(cpu -> {} ,
                "move.l #$12345678,d1",
                "move.w #$00,d1")
                .expectD1(0x12340000)
                .noCarry().noOverflow().noExtended().notNegative().zero().notSupervisor();
        execute(cpu -> {} ,
                "move.l #$12345678,d1",
                "move.w #$ffff,d1")
                .expectD1(0x1234ffff)
                .noCarry().noOverflow().noExtended().negative().notZero().notSupervisor();
    }

    public void testMoveLong()
    {
        execute(cpu -> {} ,
                "move.l #$12345678,d1",
                "move.l #$00,d1")
                .expectD1(0)
                .noCarry().noOverflow().noExtended().notNegative().zero().notSupervisor();
        execute(cpu -> {} ,
                "move.l #$12345678,d1",
                "move.l #$ffffffff,d1")
                .expectD1(0xffffffff)
                .noCarry().noOverflow().noExtended().negative().notZero().notSupervisor();
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

    public void testMoveFromSR()
    {
        final int adr = PROGRAM_START_ADDRESS+128;
        execute( cpu->
                {
                    cpu.setFlags( CPU.ALL_USERMODE_FLAGS );
                },1,true,
                "move.w sr,"+adr)
                .expectMemoryWord( adr,CPU.ALL_USERMODE_FLAGS | CPU.FLAG_SUPERVISOR_MODE|CPU.FLAG_I0|CPU.FLAG_I1|CPU.FLAG_I2)
                .supervisor();
    }

    public void testMoveToSR()
    {
        final int adr = PROGRAM_START_ADDRESS+128;
        execute( cpu-> {},2,true,
                "move.w #"+(CPU.FLAG_SUPERVISOR_MODE|CPU.ALL_USERMODE_FLAGS|CPU.FLAG_I1)+","+adr,
                "move.w "+adr+",sr"
        )
                .expectIRQLevel( 2 )
                .zero()
                .overflow()
                .carry()
                .extended()
                .negative()
                .supervisor();
    }

    public void testTAS() {

        final int adr = PROGRAM_START_ADDRESS+128;

        execute(cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ),
                "move.b #0,"+adr,
                "tas "+adr)
                .extended()
                .expectMemoryByte( adr,0xffffff80 )
                .zero().notNegative().notSupervisor();

        execute(cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ),
                "move.b #128,"+adr,
                "tas "+adr)
                .extended()
                .expectMemoryByte( adr,0xffffff80 )
                .notZero().negative().notSupervisor();

        execute(cpu -> cpu.setFlags( CPU.ALL_USERMODE_FLAGS ),
                "move.b #1,"+adr,
                "tas "+adr)
                .extended()
                .expectMemoryByte( adr,0xffffff81 )
                .notZero().notNegative().notSupervisor();
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
                .noCarry().noOverflow().noExtended().notNegative().notZero().notSupervisor();
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
        return execute(cpuSetup,insToExecute,false,program1,additional);
    }

    private ExpectionBuilder execute(Consumer<CPU> cpuSetup,int insToExecute,
                                     boolean runInSuperVisorMode,
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

        insToExecute += ( runInSuperVisorMode ? 3 : 4 ); // + number of instructions in reset handler

        final int mask = ~CPU.FLAG_SUPERVISOR_MODE;
        System.out.println("USERMODE_STACK_PTR = "+Misc.hex(USERMODE_STACK_PTR));
        String resetHandler = "MOVE.L #"+Misc.hex(USERMODE_STACK_PTR)+",A0\n" +
                "MOVE.L A0,USP\n";
        if ( ! runInSuperVisorMode )
        {
            resetHandler += "AND #" + Misc.binary16Bit(mask) + ",SR\n";
        }
        resetHandler += "JMP "+Misc.hex(PROGRAM_START_ADDRESS); // clear super visor bit

        final byte[] exceptionHandler = compile(resetHandler);
        memory.writeBytes(exceptionHandlerAddress, exceptionHandler );

        // write the actual program
        final String program = lines.stream().collect(Collectors.joining("\n"));
        final byte[] executable = compile("ORG "+PROGRAM_START_ADDRESS+"\n"+program);
        System.out.println( Memory.hexdump(PROGRAM_START_ADDRESS,executable,0,executable.length) );

        memory.writeBytes(PROGRAM_START_ADDRESS,executable );

        if ( DEBUG_DUMP_BINARY)
        {
            System.out.println("***");
            System.out.println("* Binary dumped to "+DEBUG_BINARY.getAbsolutePath());
            System.out.println("***");
            SRecordHelper helper = new SRecordHelper();
            try (FileOutputStream out = new FileOutputStream( DEBUG_BINARY ))
            {
                final IObjectCodeWriter.Buffer buffer = new IObjectCodeWriter.Buffer(PROGRAM_START_ADDRESS,1024);
                for ( byte b : executable )
                {
                    buffer.writeByte( b );
                }
                helper.write( Arrays.asList( buffer ), out );
            }
            catch (Exception e)
            {
                throw new RuntimeException( e );
            }
        }

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

    private void assertEquals(byte[] expected,int startAddress)
    {
        for ( int len = expected.length,idx =0 ; len > 0 ; len--,idx++) {
            byte actual = memory.readByte(  startAddress+idx );
            if ( actual != expected[idx] ) {
                fail("Byte mismatch at "+Misc.hex(startAddress+idx));
            }
        }
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

        public ExpectionBuilder expectMemoryWords(int startAddress,int expect1,int...additional)
        {
            int adr = startAddress;
            expectMemoryWord(adr,expect1);
            if ( additional != null )
            {
                adr+=2;
                for ( int exp : additional ) {
                    expectMemoryWord(adr,exp);
                    adr+=2;
                }
            }
            return this;
        }

        public ExpectionBuilder expectMemoryLongs(int startAddress,int expect1,int...additional)
        {
            final int len = 1 + ( (additional==null) ? 0 : additional.length);
            int adr = startAddress;
            expectMemoryLong(adr,expect1);
            if ( additional != null )
            {
                adr+=4;
                for ( int exp : additional ) {
                    expectMemoryLong(adr,exp);
                    adr+=4;
                }
            }
            return this;
        }

        public ExpectionBuilder dumpMemory(int startAddress,int byteCount)
        {
            System.out.println("=== Dump at "+Misc.hex(startAddress)+" ===");
            System.out.println( cpu.memory.hexdump(startAddress, byteCount) );
            return this;
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
        public ExpectionBuilder expectIRQLevel(int level) { return assertHexEquals( "IRQ level mismatch" , level, cpu.getIRQLevel() ); }

        public ExpectionBuilder cycles(int number) {
            assertEquals("Expected CPU cycle count "+number+" but was "+cpu.cycles,number,cpu.cycles);
            return this;
        }

        public ExpectionBuilder expectPC(int value) { return assertHexEquals( "PC mismatch" , value, cpu.pc); }

        public ExpectionBuilder zero() { assertTrue( "Z flag not set ?" , cpu.isZero() ); return this; };
        public ExpectionBuilder notZero() { assertTrue( "Z flag set ?" , cpu.isNotZero() ); return this; };

        public ExpectionBuilder carry() { assertTrue( "C flag not set ?" , cpu.isCarry() ); return this; };
        public ExpectionBuilder noCarry() { assertTrue( "C flag set ?" , cpu.isNotCarry() ); return this; };

        public ExpectionBuilder waitingForInterrupt() { assertTrue( "CPU is not stopped?" , cpu.isStopped()); return this; };

        public ExpectionBuilder negative() { assertTrue( "N flag not set ?" , cpu.isNegative() ); return this; };
        public ExpectionBuilder notNegative() { assertTrue( "N flag set ?" , cpu.isNotNegative() ); return this; };

        public ExpectionBuilder overflow() { assertTrue( "V flag not set ?" , cpu.isOverflow() ); return this; };
        public ExpectionBuilder noOverflow() { assertTrue( "V flag set ?" , cpu.isNotOverflow() ); return this; };

        public ExpectionBuilder extended() { assertTrue( "X flag not set ?" , cpu.isExtended() ); return this; };
        public ExpectionBuilder noExtended() { assertTrue("X flag set ?" , cpu.isNotExtended() ); return this; };


        public ExpectionBuilder lessThan() {

            assertTrue( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BLT.bits));
            return this;
        }

        public ExpectionBuilder notLessThan() {

            assertFalse( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BLT.bits));
            return this;
        }

        public ExpectionBuilder lessThanEquals()
        {
            assertTrue( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BLE.bits));
            return this;
        }

        public ExpectionBuilder notLessThanEquals()
        {
            assertFalse( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BLE.bits));
            return this;
        }

        public ExpectionBuilder greaterThan()
        {
            assertTrue( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BGT.bits));
            return this;
        }

        public ExpectionBuilder notGreaterThan()
        {
            assertFalse( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BGT.bits));
            return this;
        }
        
        public ExpectionBuilder greaterThanEquals()
        {
            assertTrue( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BGE.bits));
            return this;
        }

        public ExpectionBuilder notGreaterOrEquals()
        {
            assertFalse( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BGE.bits));
            return this;
        }

        public ExpectionBuilder equals()
        {
            assertTrue( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BEQ.bits));
            return this;
        }

        public ExpectionBuilder notEquals()
        {
            assertTrue( "CC mismatch: "+cpu,Condition.isTrue(cpu,Condition.BNE.bits));
            return this;
        }

        public ExpectionBuilder supervisor() { assertTrue( "S flag not set ?" , cpu.isSupervisorMode() ); return this; };
        public ExpectionBuilder notSupervisor() { assertTrue( "S flag set ?" , ! cpu.isSupervisorMode()); return this; };

        public ExpectionBuilder expectMemoryByte(int address,int value)
        {
            final int actual = memory.readByte(address);
            if ( (value & 0xff) != (actual & 0xff) )
            {
                fail("Expected "+Misc.hex(value)+" @ "+Misc.hex(address)+" but got "+Misc.hex(actual & 0xff));
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

    private void writeWords(int startAddress,int word1,int...additional)
    {
        memory.writeWord(startAddress,word1);
        if ( additional != null )
        {
            for (int anAdditional : additional)
            {
                startAddress += 2;
                memory.writeWord(startAddress, anAdditional);
            }
        }
    }

    private void writeLongs(int startAddress,int word1,int...additional)
    {
        memory.writeLong(startAddress,word1);
        if ( additional != null )
        {
            for (int anAdditional : additional)
            {
                startAddress += 4;
                memory.writeLong(startAddress, anAdditional);
            }
        }
    }
}