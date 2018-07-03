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
        compile("AND #$1234,sr","00000000: andi #$1234,sr");
    }

    public void testDBRA()
    {
        testDBcc(Instruction.DBHI);
    }

    private void testDBcc(Instruction instruction)
    {
        final String expected = "00000000: "+instruction.getMnemonic()+" d1,$fffffffe";
        compile("loop: "+instruction.getMnemonic()+" d1,loop", expected );
    }

    public void testRelativeBranching()
    {
        compile("BRA loop\nloop:","00000000: bra $2");
        compile("BHI loop\nloop:","00000000: bhi $2");
        compile("BLS loop\nloop:","00000000: bls $2");
        compile("BCC loop\nloop:","00000000: bcc $2");
        compile("BCS loop\nloop:","00000000: bcs $2");
        compile("BNE loop\nloop:","00000000: bne $2");
        compile("BEQ loop\nloop:","00000000: beq $2");
        compile("BVC loop\nloop:","00000000: bvc $2");
        compile("BVS loop\nloop:","00000000: bvs $2");
        compile("BPL loop\nloop:","00000000: bpl $2");
        compile("BMI loop\nloop:","00000000: bmi $2");
        compile("BGE loop\nloop:","00000000: bge $2");
        compile("BLT loop\nloop:","00000000: blt $2");
        compile("BGT loop\nloop:","00000000: bgt $2");
        compile("BLE loop\nloop:","00000000: ble $2");
    }

    public void testReset()
    {
        compile("reset");
    }

    public void testTrap()
    {
        compile("trap #10");
    }

    public void testUnlink()
    {
        compile("unlk a3");
    }

    public void testPEA()
    {
        compile("pea (a3)");
    }

    public void testNeg()
    {
        compile("neg.b d3");
        compile("neg.w d3");
        compile("neg.l d3");
    }

    public void testLink()
    {
        compile("link a3,#$4");
    }

    public void testRTR()
    {
        compile("rtr");
    }

    public void testRTE()
    {
        compile("rte");
    }

    public void testJSR() {
        compile("jsr sub\nillegal\nsub:","00000000: jsr ($6)\n" +
                "00000004: illegal");
    }

    public void testRTS()
    {
        compile("rts");
    }

    public void testMovea() {
        compile("movea #$1234,a3","00000000: movea.w #$1234,a3");
        compile("movea.l #$123456,a3","00000000: movea.l #$123456,a3");
    }

    public void testLEA() {
        compile("lea $1234,a3","00000000: lea ($1234),a3");
        compile("lea $12345678,a3","00000000: lea ($12345678),a3");
    }

    public void testJMP() {
        compile("jmp $1234");
        compile("jmp $12345678");
    }

    public void testMove()
    {
        compile("move.b #$12,d1","00000000: move.b #$12,d1");
        compile("move   $123(pc),d1","00000000: move.w $123(pc),d1");
        compile("move.l #$12345678,a0","00000000: movea.l #$12345678,a0");
        compile("move   #$1234,d1","00000000: move.w #$1234,d1");
        compile("move.b d0,d1","00000000: move.b d0,d1");
        compile("move   d0,d1","00000000: move.w d0,d1");
        compile("move.w d0,d1","00000000: move.w d0,d1");
        compile("move.l d0,d1","00000000: move.l d0,d1");
        compile("move.b (a0),d1","00000000: move.b (a0),d1");
        compile("move   (a0),d1","00000000: move.w (a0),d1");
        compile("move.w (a0),d1","00000000: move.w (a0),d1");
        compile("move.l (a0),d1","00000000: move.l (a0),d1");
        compile("move.b (a0)+,d1","00000000: move.b (a0)+,d1");
        compile("move   (a0)+,d1","00000000: move.w (a0)+,d1");
        compile("move.w (a0)+,d1","00000000: move.w (a0)+,d1");
        compile("move.l (a0)+,d1","00000000: move.l (a0)+,d1");
        compile("move.b -(a0),d1","00000000: move.b -(a0),d1");
        compile("move   -(a0),d1","00000000: move.w -(a0),d1");
        compile("move.w -(a0),d1","00000000: move.w -(a0),d1");
        compile("move.l -(a0),d1","00000000: move.l -(a0),d1");

        compile("move.b ($1234),d1","00000000: move.b ($1234),d1");
        compile("move   ($1234),d1","00000000: move.w ($1234),d1");
        compile("move.w ($1234),d1","00000000: move.w ($1234),d1");
        compile("move.l ($1234),d1","00000000: move.l ($1234),d1");

        compile("move.b ($123456),d1","00000000: move.b ($123456),d1");
        compile("move   ($123456),d1","00000000: move.w ($123456),d1");
        compile("move.w ($123456),d1","00000000: move.w ($123456),d1");
        compile("move.l ($123456),d1","00000000: move.l ($123456),d1");

        compile("move   $10(a0),d1","00000000: move.w $10(a0),d1");
        compile("move.b $10(a0),d1","00000000: move.b $10(a0),d1");
        compile("move.w $10(a0),d1","00000000: move.w $10(a0),d1");
        compile("move.l $10(a0),d1","00000000: move.l $10(a0),d1");
        compile("move.w #$1234,d1","00000000: move.w #$1234,d1");
        compile("move.l #$12345678,d1","00000000: move.l #$12345678,d1");

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
        final byte[] executable = this.asm.getBytes();
        System.out.println("COMPILED: "+Memory.hexdump(0,executable,0,executable.length));

        final Memory memory = new Memory(2048);
        memory.writeBytes( 0,executable );
        Disassembler asm = new Disassembler( memory );
        final String disassembled = asm.disassemble( 0, executable.length );
        assertEquals(expectedSource,disassembled);
    }
}