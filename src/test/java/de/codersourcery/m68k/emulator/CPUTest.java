package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.Assembler;
import de.codersourcery.m68k.assembler.CompilationMessages;
import de.codersourcery.m68k.assembler.CompilationUnit;
import de.codersourcery.m68k.assembler.IResource;
import de.codersourcery.m68k.emulator.cpu.CPU;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CPUTest extends TestCase
{
    private static final int MEM_SIZE = 512*1024;

    public static final int ALL_USR_FLAGS = CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW | CPU.FLAG_NEGATIVE | CPU.FLAG_ZERO | CPU.FLAG_EXTENDED;

    private Memory memory;
    private CPU cpu;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        memory = new Memory(MEM_SIZE);
        cpu = new CPU(memory);
    }

    public void testLEA()
    {
        execute("lea $12345678,a0", cpu -> cpu.setFlags(ALL_USR_FLAGS))
            .expectA0(0x12345678).carry().overflow().extended().negative().zero();
    }

    public void testLEA2()
    {
        execute("lea $1234,a0", cpu -> cpu.setFlags(ALL_USR_FLAGS))
            .expectA0(0x1234).carry().overflow().extended().negative().zero();
    }

    public void testMoveByteImmediate() {

        execute("move.b #$12,d0").expectD0(0x12).notCarry().noOverflow().notExtended().notNegative().notZero();
        execute("move.b #$00,d0").expectD0(0x00).notCarry().noOverflow().notExtended().notNegative().zero();
        execute("move.b #$ff,d0").expectD0(0xff).notCarry().noOverflow().notExtended().negative().notZero();
    }

    public void testMoveByteClearsFlags() {
        execute("move.b #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12).notCarry().noOverflow().notExtended().notNegative().notZero();
    }

    public void testMoveWordClearsFlags() {
        execute("move.w #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12).notCarry().noOverflow().notExtended().notNegative().notZero();
    }

    public void testMoveLongClearsFlags() {
        execute("move.l #$12,d0",cpu->cpu.setFlags(CPU.FLAG_CARRY|CPU.FLAG_OVERFLOW)).expectD0(0x12).notCarry().noOverflow().notExtended().notNegative().notZero();
    }

    public void testMoveWordImmediate() {

        execute("move.w #$1234,d0").expectD0(0x1234).notCarry().noOverflow().notExtended().notNegative().notZero();
        execute("move.w #$00,d0").expectD0(0x00).notCarry().noOverflow().notExtended().notNegative().zero();
        execute("move.w #$ffff,d0").expectD0(0xffff).notCarry().noOverflow().notExtended().negative().notZero();
    }

    public void testMoveLongImmediate() {

        execute("move.l #$fffffffe,d0").expectD0(0xfffffffe).notCarry().noOverflow().notExtended().negative().notZero();
        execute("move.l #$00,d0").expectD0(0x00).notCarry().noOverflow().notExtended().notNegative().zero();
        execute("move.l #$12345678,d0").expectD0(0x12345678).notCarry().noOverflow().notExtended().notNegative().notZero();
    }

    public void testMoveQClearsCarryAndOverflow()
    {
        execute("moveq #$70,d0", cpu -> cpu.setFlags( CPU.FLAG_CARRY | CPU.FLAG_OVERFLOW) ).expectD0(0x70).notCarry().noOverflow().notExtended().notNegative().notZero();
    }

    public void testMoveQ()
    {
        execute("moveq #$70,d0").expectD0(0x70).notCarry().noOverflow().notExtended().notNegative().notZero();
        execute("moveq #$0,d0").expectD0(0x0).notCarry().noOverflow().notExtended().notNegative().zero();
        execute("moveq #$ff,d0").expectD0(0xffffffff).notCarry().noOverflow().notExtended().negative().notZero();
    }

    public void testExgDataData() {

        execute(cpu -> cpu.setFlags(ALL_USR_FLAGS),
              "move.l #$12345678,d1",
            "move.l #$76543210,d3",
                       "exg d1,d3")
            .expectD1(0x76543210)
            .expectD3(0x12345678)
            .carry().overflow().extended().negative().zero();
    }

    public void testExgAdrAdr() {

        execute(cpu -> cpu.setFlags(ALL_USR_FLAGS),
            "lea $12345678,a1",
            "lea $76543210,a3",
            "exg a1,a3")
            .expectA1(0x76543210)
            .expectA3(0x12345678)
            .carry().overflow().extended().negative().zero();
    }

    public void testExgAdrData() {

        execute(cpu -> cpu.setFlags(ALL_USR_FLAGS),
            "lea $12345678,a1",
            "move.l $76543210,d3",
            "exg a1,d3")
            .expectA1(0x76543210)
            .expectD3(0x12345678)
            .carry().overflow().extended().negative().zero();
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
        final List<String> lines = new ArrayList<>();
        lines.add(program1);
        if ( additional != null ) {
            Arrays.stream(additional).forEach(lines::add );
        }

        int insCount = lines.size();

        memory.writeLong(0, MEM_SIZE ); // Supervisor mode stack pointer
        memory.writeLong(4, 1024 ); // PC starting value

        final String program = lines.stream().collect(Collectors.joining("\n"));
        final byte[] executable = compile(program);
        System.out.println( Memory.hexdump(1024,executable,0,executable.length) );
        memory.writeBytes(1024,executable );

        cpu.reset();
        for ( ; insCount > 0 ; insCount--)
        {
            // assumption is that we want to test the
            // very last instruction so we'll invoke the callback here
            if ( (insCount-1) == 0 ) {
                cpuSetup.accept(cpu);
            }
            cpu.executeOneInstruction();
        }
        return new ExpectionBuilder();
    }

    private byte[] compile(String program)
    {
        final Assembler asm = new Assembler();
        final CompilationUnit unit = new CompilationUnit(IResource.stringResource(program));
        final CompilationMessages messages = asm.compile(unit);
        assertFalse(messages.hasErrors());
        return asm.getBytes();
    }

    protected final class ExpectionBuilder
    {
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

        public ExpectionBuilder zero() { assertTrue( "Z flag not set ?" , cpu.isZero() ); return this; };
        public ExpectionBuilder notZero() { assertTrue( "Z flag set ?" , cpu.isNotZero() ); return this; };

        public ExpectionBuilder carry() { assertTrue( "C flag not set ?" , cpu.isCarry() ); return this; };
        public ExpectionBuilder notCarry() { assertTrue( "C flag set ?" , cpu.isNotCarry() ); return this; };

        public ExpectionBuilder negative() { assertTrue( "N flag not set ?" , cpu.isNegative() ); return this; };
        public ExpectionBuilder notNegative() { assertTrue( "N flag set ?" , cpu.isNotNegative() ); return this; };

        public ExpectionBuilder overflow() { assertTrue( "V flag not set ?" , cpu.isOverflow() ); return this; };
        public ExpectionBuilder noOverflow() { assertTrue( "V flag set ?" , cpu.isNotOverflow() ); return this; };

        public ExpectionBuilder extended() { assertTrue( "X flag not set ?" , cpu.isExtended() ); return this; };
        public ExpectionBuilder notExtended() { assertTrue( "X flag set ?" , cpu.isNotExtended() ); return this; };

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
