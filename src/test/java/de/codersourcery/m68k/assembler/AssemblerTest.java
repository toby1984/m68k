package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.Memory;
import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.assembler.arch.ConditionalInstructionType;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.parser.ast.AST;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.StatementNode;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.function.Consumer;

public class AssemblerTest extends TestCase
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
        assertArrayEquals(compile("illegal")    ,0b01001010,0b11111100);
    }

    public void testMoveToUSP() {

        assertArrayEquals(compile("move a3,usp")    ,0x4e,0x63 );
    }

    public void testMovea() {
        assertArrayEquals(compile("movea #$1234,a3")    ,0x36,0x7c,0x12,0x34 );
        assertArrayEquals(compile("movea.l #$123456,a3")    ,0x26,0x7c,0x00,0x12,0x34,0x56 );
    }

    public void testMoveFromUSP() {

        assertArrayEquals(compile("move usp,a3")    ,0x4e,0x6b );
        assertFailsToCompile("move usp,usp" );
        assertFailsToCompile("move.b a3,usp" );
        assertFailsToCompile("move.w a3,usp" );
        assertFailsToCompile("move.b usp,a3" );
        assertFailsToCompile("move.w usp,a3" );
    }

    public void testANDISR() {
        assertArrayEquals(compile("AND #$1234,sr")    ,0x02,0x7c,0x12,0x34 );
        assertFailsToCompile( "AND.l #$1234,sr");
        assertFailsToCompile( "AND.b #$12,sr");
    }

    public void testDBRA()
    {
        testDBcc(Instruction.DBHI);

        Arrays.stream( Instruction.values() )
                .filter( insn -> insn.conditionalType == ConditionalInstructionType.DBCC )
                .peek(x->System.out.println("Testing "+x))
                .forEach(this::testDBcc );
    }

    private void testDBcc(Instruction instruction)
    {
        // 0101cccc11001sss
        final int insWord = 0b0101000011001000 | instruction.condition.bits<< 8 | Register.D1.bits;
        assertArrayEquals(compile("loop: "+instruction.getMnemonic()+" d1,loop")    ,(insWord & 0xff00)>>8,insWord&0xff,0xff,0xfe);
    }

    public void testRelativeBranching()
    {
        branchInstructionTest("RA");
        branchInstructionTest("HI");
        branchInstructionTest("LS");
        branchInstructionTest("CC");
        branchInstructionTest("CS");
        branchInstructionTest("NE");
        branchInstructionTest("EQ");
        branchInstructionTest("VC");
        branchInstructionTest("VS");
        branchInstructionTest("PL");
        branchInstructionTest("MI");
        branchInstructionTest("GE");
        branchInstructionTest("LT");
        branchInstructionTest("GT");
        branchInstructionTest("LE");
    }

    private void branchInstructionTest(String conditionCode)
    {
        branchInstructionTest(conditionCode,8);
        branchInstructionTest(conditionCode,16);
        branchInstructionTest(conditionCode,32);
    }

    private void branchInstructionTest(String conditionCode,int bits)
    {
        final Instruction it = Instruction.getType("B"+conditionCode);
        int firstByte;
        int secondByte;
        int thirdByte;
        int fourthByte;
        int fifthByte;
        int sixthByte;

        final Consumer<byte[]> check;
        int offset;
        switch(bits)
        {
            case 8:
                offset = 100;
                firstByte = (0b0110_0000 | it.condition.bits);
                secondByte = offset;
                check = actualBytes -> assertArrayEquals(actualBytes,firstByte,secondByte);
                break;
            case 16:
                offset = 16384;
                firstByte = (0b0110_0000 | it.condition.bits);
                secondByte = 0;
                thirdByte = (offset & 0xff00) >> 8;
                fourthByte = offset & 0x00ff;
                check = actualBytes -> assertArrayEquals(actualBytes,firstByte,secondByte,thirdByte,fourthByte);
                break;
            case 32:
                offset = 65536;
                firstByte = (0b0110_0000 | it.condition.bits);
                secondByte = 0xff;

                thirdByte =  (offset & 0xff000000) >> 24;
                fourthByte = (offset & 0x00ff0000) >> 16;
                fifthByte =  (offset & 0x0000ff00) >>  8;
                sixthByte =  (offset & 0x000000ff);
                check = actualBytes -> assertArrayEquals(actualBytes,firstByte,secondByte,thirdByte,fourthByte,fifthByte,sixthByte);
                break;
            default:
                throw new RuntimeException("Unreachable code reached: "+bits);
        }
        final String source = "B"+conditionCode+" next\n" +
                "ORG "+offset+"\n"+
                "next:";
        final byte[] bytes = compile(source);
        check.accept(bytes );
    }

    public void testPEA() {
        assertArrayEquals(compile("pea (a3)")    ,0x48,0x53);
    }

    public void testJSR() {
        assertArrayEquals(compile("jsr next\nnext:")    ,0x4e,0xb8,0x00,0x04);
        assertArrayEquals(compile("jsr $2(pc)")    ,0x4e,0xba,0x00,0x02);
    }

    public void testNeg()
    {
        assertArrayEquals(compile("neg.b d3")    ,0x44,0x03);
        assertArrayEquals(compile("neg.w d3")    ,0x44,0x43);
        assertArrayEquals(compile("neg.l d3")    ,0x44,0x83);
    }

    public void testSwap()
    {
        assertArrayEquals(compile("swap d3")    ,0x48,0x43);
        assertArrayEquals(compile("swap.w d3")    ,0x48,0x43);
        assertFailsToCompile("swap a3");
        assertFailsToCompile("swap.b d3");
        assertFailsToCompile("swap.l d3");
    }

    public void testTrap()
    {
        assertArrayEquals(compile("trap #10")    ,0x4e,0x4a);

        try {
            compile("trap #16"); // only 0-15 are valid on 68000
            fail("Should've failed");
        } catch(Exception e) {
            // ok
        }
    }

    public void testReset()
    {
        assertArrayEquals(compile("reset")    ,0x4e,0x70);
    }

    public void testRTE()
    {
        assertArrayEquals(compile("rte")    ,0x4e,0x73);
    }

    public void testLINK()
    {
        assertArrayEquals(compile("LINK a3,#$04")    ,0x4e,0x53,0x00,0x04);
    }

    public void testUNLK()
    {
        assertArrayEquals(compile("unlk a3")    ,0x4e,0x5b);
    }

    public void testRTR()
    {
        assertArrayEquals(compile("rtr")    ,0x4e,0x77);
    }

    public void testRTS()
    {
        assertArrayEquals(compile("rts")    ,0x4e,0x75);
    }

    public void testJMP() {
        assertArrayEquals(compile("jmp $1234")    ,0x4e,0xf8,0x12,0x34);
        assertArrayEquals(compile("jmp $12345678")    ,0x4e,0xf9,0x12,0x34,0x56,0x78);
    }

    public void testMove()
    {
        // Pattern: ooooDDDMMMmmmsss
        //          0010001000111100 00010010 00110100 01010110 01111000
        //          0010000001111100 00010010 00110100 01010110 01111000

//        assertArrayEquals(compile("move   $12(pc,a0.w*4),d1")    ,0x32,0x3b,0x84,0x12);
        assertArrayEquals(compile("move   $1234(pc),d1")    ,0x32,0x3a,0x12,0x34);

        assertArrayEquals(compile("move.l #$12345678,a0"),0x20,0x7c,0x12,0x34,0x56,0x78);

        /*
         *  An                           => ADDRESS_REGISTER_DIRECT
         *  d16(An)                      => ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT
         * (d8,An, Xn.SIZE*SCALE)        => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
         * (bd,An,Xn.SIZE*SCALE)         => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
         * ([bd,An],Xn.SIZE*SCALE,od)    => MEMORY_INDIRECT_POSTINDEXED
         * ([bd, An, Xn.SIZE*SCALE], od) => MEMORY_INDIRECT_PREINDEXED
         * (d8,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
         * (bd, PC, Xn. SIZE*SCALE)      => PC_INDIRECT_WITH_INDEX_DISPLACEMENT
         * ([bd,PC],Xn.SIZE*SCALE,od)    => PC_MEMORY_INDIRECT_POSTINDEXED
         * ([bd,PC,Xn.SIZE*SCALE],od)    => PC_MEMORY_INDIRECT_PREINDEXED
         * ($1234).w                     => ABSOLUTE_SHORT_ADDRESSING
         * ($1234).L                     => ABSOLUTE_LONG_ADDRESSING
         * #$1234                        => IMMEDIATE_VALUE
         */

        assertArrayEquals(compile("move   #$1234,d1")    ,0x32,0x3c,0x12,0x34);

        assertArrayEquals(compile("move.b d0,d1"),0x12,0x00);
        assertArrayEquals(compile("move   d0,d1"),  0x32,0x00);
        assertArrayEquals(compile("move.w d0,d1"),0x32,0x00);
        assertArrayEquals(compile("move.l d0,d1"),0x22,0x00);

        assertArrayEquals(compile("move.b (a0),d1"),0x12,0x10);
        assertArrayEquals(compile("move   (a0),d1"),  0x32,0x10);
        assertArrayEquals(compile("move.w (a0),d1"),0x32,0x10);
        assertArrayEquals(compile("move.l (a0),d1"),0x22,0x10);

        assertArrayEquals(compile("move.b (a0)+,d1"),0x12,0x18);
        assertArrayEquals(compile("move   (a0)+,d1"),  0x32,0x18);
        assertArrayEquals(compile("move.w (a0)+,d1"),0x32,0x18);
        assertArrayEquals(compile("move.l (a0)+,d1"),0x22,0x18);

        assertArrayEquals(compile("move.b -(a0),d1"),0x12,0x20);
        assertArrayEquals(compile("move   -(a0),d1"),  0x32,0x20);
        assertArrayEquals(compile("move.w -(a0),d1"),0x32,0x20);
        assertArrayEquals(compile("move.l -(a0),d1"),0x22,0x20);

        assertArrayEquals(compile("move.b ($1234),d1"),0x12,0x38,0x12,0x34);
        assertArrayEquals(compile("move.b ($1234),d1"),0x12,0x38,0x12,0x34);
        assertArrayEquals(compile("move   ($1234),d1"),0x32,0x38,0x12,0x34);
        assertArrayEquals(compile("move.w ($1234),d1"),0x32,0x38,0x12,0x34);
        assertArrayEquals(compile("move.l ($1234),d1"),0x22,0x38,0x12,0x34);

        assertArrayEquals(compile("move   $10(a0),d1"),0x32,0x28,0x00,0x10);
        assertArrayEquals(compile("move.b $10(a0),d1"),0x12,0x28,0x00,0x10);
        assertArrayEquals(compile("move.w $10(a0),d1"),0x32,0x28,0x00,0x10);
        assertArrayEquals(compile("move.l $10(a0),d1"),0x22,0x28,0x00,0x10);

        assertArrayEquals(compile("move.b #$12,d1")      ,0x12,0x3c,0x00,0x12);

        assertArrayEquals(compile("move.w #$1234,d1")    ,0x32,0x3c,0x12,0x34);
        assertArrayEquals(compile("move.l #$12345678,d1"),0x22,0x3c,0x12,0x34,0x56,0x78);

        // FIXME: Test PC-relative modes
    }

    private byte[] compile(String s)
    {
        final IResource source = IResource.stringResource(s);
        final CompilationUnit root = new CompilationUnit(source);

        final CompilationMessages messages = asm.compile(root);
        if ( messages.hasErrors() )
        {
            messages.getMessages().stream().forEach(System.out::println );
            throw new RuntimeException("Compilation failed with errors");
        }
        //        System.out.println("RESULT: "+Memory.hexdump(0,data,0,data.length));
        return this.asm.getBytes();
    }

    private void assertFailsToCompile(String program)
    {
        try
        {
            compile(program);
            fail("Should have failed to compile");
        }
        catch(Exception e) {
            // ok
        }
    }
    private static void assertArrayEquals(byte[] actual,int...values)
    {
        int len = values == null ? 0 : values.length;
        final byte[] expected= new byte[len];
        for ( int i = 0 ; i < len ; i++ ) {
            expected[i]=(byte) values[i];
        }
        boolean fail = false;
        if ( actual.length != len ) {
            fail = true;
        }
        final int minLen = Math.min(actual.length,expected.length);
        for ( int i = 0 ; i < minLen ; i++ )
        {
            if ( expected[i] != actual[i] ) {
                fail = true;
            }
        }
        if ( fail )
        {
            System.out.println("EXPECTED: "+Memory.hexdump(0,expected,0,expected.length));
            System.out.println("ACTUAL  : "+Memory.hexdump(0,actual,0,actual.length));
            System.out.println();
            System.out.println("EXPECTED: "+Memory.bindump(0,expected,0,expected.length));
            System.out.println("ACTUAL  : "+Memory.bindump(0,actual,0,actual.length));
            fail("Arrays do not match");
        }
    }
}
