package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.Assembler;
import de.codersourcery.m68k.assembler.CompilationMessages;
import de.codersourcery.m68k.assembler.CompilationUnit;
import de.codersourcery.m68k.assembler.IResource;
import de.codersourcery.m68k.emulator.cpu.CPU;
import junit.framework.TestCase;

public class CPUTest extends TestCase
{
    private static final int MEM_SIZE = 512*1024;
    private Memory memory;
    private CPU cpu;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        memory = new Memory(MEM_SIZE );
        cpu = new CPU(memory);
    }

    public void testMoveByte() {

    }

    private byte[] execute(String program) {

        memory.writeLong(0, MEM_SIZE ); // Supervisor mode stack pointer
        memory.writeLong(4, 1024 ); // PC starting value

        cpu.reset();
        final byte[] executable = compile(program);
        memory.writeBytes(1024,executable );

    }

    private byte[] compile(String program)
    {
        final Assembler asm = new Assembler();
        final CompilationUnit unit = new CompilationUnit(IResource.stringResource(program));
        final CompilationMessages messages = asm.compile(unit);
        assertFalse(messages.hasErrors());
        return asm.getBytes();
    }
}
