package de.codersourcery.m68k.disassembler;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.Assembler;
import de.codersourcery.m68k.assembler.CompilationMessages;
import de.codersourcery.m68k.assembler.CompilationUnit;
import de.codersourcery.m68k.assembler.IResource;
import de.codersourcery.m68k.assembler.arch.Instruction;
import junit.framework.TestCase;

public class DisassemblerTest extends TestCase
{
    private Assembler asm;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        asm = new Assembler();
    }

    public void testIllegal()
    {
        compile("illegal");
    }

    public void testMoveToUSP() {
        compile("move a3,usp");
    }

    public void testMoveFromUSP() {

        compile("move usp,a3");
    }

    public void testANDISR() {
        compile("AND #$1234,sr");
    }

    public void testDBRA()
    {
        testDBcc(Instruction.DBHI);
    }

    private void testDBcc(Instruction instruction)
    {
        compile("loop: "+instruction.getMnemonic()+" d1,loop");
    }

    public void testRelativeBranching()
    {
        compile("BRA loop\nloop:","BRA $2");
        compile("BHI loop\nloop:","BHI $2");
        compile("BLS loop\nloop:","BLS $2");
        compile("BCC loop\nloop:","BCC $2");
        compile("BCS loop\nloop:","BCS $2");
        compile("BNE loop\nloop:","BNE $2");
        compile("BEQ loop\nloop:","BEQ $2");
        compile("BVC loop\nloop:","BVC $2");
        compile("BVS loop\nloop:","BVS $2");
        compile("BPL loop\nloop:","BPL $2");
        compile("BMI loop\nloop:","BMI $2");
        compile("BGE loop\nloop:","BGE $2");
        compile("BLT loop\nloop:","BLT $2");
        compile("BGT loop\nloop:","BGT $2");
        compile("BLE loop\nloop:","BLE $2");
    }

    public void testTrap()
    {
        compile("trap #10");
    }

    public void testRTE()
    {
        compile("rte");
    }

    public void testLEA() {
        compile("lea $1234,a3");
        compile("lea $12345678,a3");
    }

    public void testJMP() {
        compile("jmp $1234");
        compile("jmp $12345678");
    }

    public void testMove()
    {
        compile("move   $1234(pc),d1");
        compile("move.l #$12345678,a0");
        compile("move   #$1234,d1");
        compile("move.b d0,d1");
        compile("move   d0,d1");
        compile("move.w d0,d1");
        compile("move.l d0,d1");
        compile("move.b (a0),d1");
        compile("move   (a0),d1");
        compile("move.w (a0),d1");
        compile("move.l (a0),d1");
        compile("move.b (a0)+,d1");
        compile("move   (a0)+,d1");
        compile("move.w (a0)+,d1");
        compile("move.l (a0)+,d1");
        compile("move.b -(a0),d1");
        compile("move   -(a0),d1");
        compile("move.w -(a0),d1");
        compile("move.l -(a0),d1");
        compile("move.b ($1234),d1");
        compile("move.b ($1234),d1");
        compile("move   ($1234),d1");
        compile("move.w ($1234),d1");
        compile("move.l ($1234),d1");
        compile("move   $10(a0),d1");
        compile("move.b $10(a0),d1");
        compile("move.w $10(a0),d1");
        compile("move.l $10(a0),d1");
        compile("move.b #$12,d1");
        compile("move.w #$1234,d1");
        compile("move.l #$12345678,d1");

        // FIXME: Test PC-relative modes
    }

    private void compile(String s) {
        compile(s,"00000000: "+s);
    }

    private void compile(String s,String expectedSource)
    {
        final IResource source = IResource.stringResource(s);
        final CompilationUnit root = new CompilationUnit(source);

        final CompilationMessages messages = asm.compile(root);
        if ( messages.hasErrors() )
        {
            messages.getMessages().stream().forEach(System.out::println );
            throw new RuntimeException("Compilation failed with errors");
        }
        // System.out.println("RESULT: "+Memory.hexdump(0,data,0,data.length));
        final byte[] executable = this.asm.getBytes();

        final Memory memory = new Memory(2048);
        memory.writeBytes( 0,executable );
        Disassembler asm = new Disassembler( memory );
        final String disassembled = asm.disassemble( 0, executable.length );
        assertEquals(expectedSource,disassembled);
    }
}