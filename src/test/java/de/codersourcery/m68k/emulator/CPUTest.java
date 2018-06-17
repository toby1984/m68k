package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.Assembler;
import de.codersourcery.m68k.assembler.CompilationMessages;
import de.codersourcery.m68k.assembler.CompilationUnit;
import de.codersourcery.m68k.assembler.IResource;
import de.codersourcery.m68k.emulator.cpu.CPU;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

public class CPUTest extends TestCase
{
    private static final int MEM_SIZE = 512*1024;
    private Memory memory;
    private CPU cpu;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        memory = new Memory(MEM_SIZE);
        cpu = new CPU(memory);
    }

    public void testMoveByteImmediate() {

        execute("move.b #$12,d0").expectD0(0x12).notCarry().noOverflow().notExtended().notNegative().notZero();
        execute("move.b #$00,d0").expectD0(0x00).notCarry().noOverflow().notExtended().notNegative().zero();
        execute("move.b #$ff,d0").expectD0(0xff).notCarry().noOverflow().notExtended().negative().notZero();
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

    public void testMoveQ()
    {
        execute("moveq #$70,d0").expectD0(0x70).notCarry().noOverflow().notExtended().notNegative().notZero();
        execute("moveq #$0,d0").expectD0(0x0).notCarry().noOverflow().notExtended().notNegative().zero();
        execute("moveq #$ff,d0").expectD0(0xffffffff).notCarry().noOverflow().notExtended().negative().notZero();
    }

    private ExpectionBuilder execute(String program) {

        memory.writeLong(0, MEM_SIZE ); // Supervisor mode stack pointer
        memory.writeLong(4, 1024 ); // PC starting value

        final byte[] executable = compile(program);
        System.out.println( Memory.hexdump(1024,executable,0,executable.length) );
        memory.writeBytes(1024,executable );

        cpu.reset();
        cpu.executeOneInstruction();
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
