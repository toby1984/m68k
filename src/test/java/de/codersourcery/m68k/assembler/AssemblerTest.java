package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.emulator.memory.Memory;
import de.codersourcery.m68k.assembler.arch.CPUType;
import de.codersourcery.m68k.assembler.arch.ConditionalInstructionType;
import de.codersourcery.m68k.assembler.arch.Instruction;
import de.codersourcery.m68k.assembler.arch.Register;
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
        asm.getOptions().cpuType=CPUType.M68020;
    }

    public void testORIToSR()
    {
        assertArrayEquals(compile("ori.w #$1234,sr"),0x00,0x7c,0x12,0x34);
    }

    public void testORIToCCR() {
        assertArrayEquals(compile("ori.b #$ff,ccr"),0x00,0x3c,0x00,0xff);
    }

    public void testEORIToSR()
    {
        assertArrayEquals(compile("eori.w #$1234,sr"),0x0a,0x7c,0x12,0x34);
    }

    public void testEORIToCCR() {
        assertArrayEquals(compile("eori.b #$ff,ccr"),0x0a,0x3c,0x00,0xff);
    }

    public void testEORI() {
        assertArrayEquals(compile("eori.l #255,(a3)+     "),  0x0a,0x9b,0x00,0x00,0x00,0xff);
        assertArrayEquals(compile("eori.b #$12,(a3)+      "), 0x0a,0x1b,0x00,0x12);
        assertArrayEquals(compile("eori.b #$ff,(a3)+      "),0x0a,0x1b,0x00,0xff);
        assertArrayEquals(compile("eori.w #$1234,$1200   "), 0x0a,0x78,0x12,0x34,0x12,0x00);
        assertArrayEquals(compile("eori.l #$12345678,-(a3)"),  0x0a,0xa3,0x12,0x34,0x56,0x78);
    }

    public void testORI()
    {
        assertArrayEquals(compile("ori.w #$1234,$1200"),     0x00,0x78,0x12,0x34,0x12,0x00);
        assertArrayEquals(compile("ori.b #$12,(a3)+"),       0x00,0x1b,0x00,0x12);
        assertArrayEquals(compile("ori.l #$12345678,-(a3)"), 0x00,0xa3,0x12,0x34,0x56,0x78);
    }

    public void testMoveP() {
        assertArrayEquals(compile("movep.w d3,$80(a4)"),0x07,0x8c,0x00,0x80);
        assertArrayEquals(compile("movep.l d3,$80(a4)"),0x07,0xcc,0x00,0x80);
        assertArrayEquals(compile("movep.w $80(a4),d3"),0x07,0x0c,0x00,0x80);
        assertArrayEquals(compile("movep.l $80(a4),d3"),0x07,0x4c,0x00,0x80);
    }

    public void testMoveSR() {
       assertArrayEquals(compile("move.w $1200,sr"),0x46,0xf8,0x12,0x00);
       assertArrayEquals(compile("move.w sr,$1200"),0x40,0xf8,0x12,0x00);
    }

    public void testMoveToCCR() {
        assertArrayEquals(compile("move.w (a0)+,ccr")     ,0x44,0xd8);
        assertArrayEquals(compile("move.w $1200,ccr")   ,0x44,0xf8,0x12,0x00);
    }

    public void testANDI() {
        assertArrayEquals(compile("and.b #$12,d1")     ,0x02,0x01,0x00,0x12);
        assertArrayEquals(compile("and.w #$1234,d1")   ,0x02,0x41,0x12,0x34);
        assertArrayEquals(compile("and.l #$123456,d1"),0x02,0x81,0x00,0x12,0x34,0x56);

        assertFailsToCompile( "AND.b #$12,a0");
        assertFailsToCompile( "AND.w #$12,a0");
        assertFailsToCompile( "AND.L #$12,a0");
    }

    public void testMOVEMToMemory()
    {
        assertArrayEquals(compile("movem.w d3/a4-a5,-(a0)"),     0x48,0xa0,0x10,0x0c          );
        assertArrayEquals(compile("movem.l d3/a4-a5,-(a0)"),     0x48,0xe0,0x10,0x0c          );
        assertArrayEquals(compile("movem.w d3,$1200"),            0x48,0xb8,0x00,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.w d3-d4,$1200"),        0x48,0xb8,0x00,0x18,0x12,0x00);
        assertArrayEquals(compile("movem.w d3/a4-a5,$1200"),    0x48,0xb8,0x30,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.w d3/d5/a4/a6,$1200"),0x48,0xb8,0x50,0x28,0x12,0x00);
        assertArrayEquals(compile("movem.l d3,$1200"),            0x48,0xf8,0x00,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.l d3-d4,$1200"),        0x48,0xf8,0x00,0x18,0x12,0x00);
        assertArrayEquals(compile("movem.l d3/a4-a5,$1200"),    0x48,0xf8,0x30,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.l d3/d5/a4/a6,$1200"),0x48,0xf8,0x50,0x28,0x12,0x00);
    }

    public void testMOVEMFromMemory()
    {
        assertArrayEquals(compile("movem.w $1200,d3"),             0x4c,0xb8,0x00,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.w $1200,d3-d4"),         0x4c,0xb8,0x00,0x18,0x12,0x00);
        assertArrayEquals(compile("movem.w $1200,d3/a4-a5"),     0x4c,0xb8,0x30,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.w $1200,d3/d5/a4/a6"), 0x4c,0xb8,0x50,0x28,0x12,0x00);
        assertArrayEquals(compile("movem.l $1200,d3"),             0x4c,0xf8,0x00,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.l $1200,d3-d4"),         0x4c,0xf8,0x00,0x18,0x12,0x00);
        assertArrayEquals(compile("movem.l $1200,d3/a4-a5"),     0x4c,0xf8,0x30,0x08,0x12,0x00);
        assertArrayEquals(compile("movem.l $1200,d3/d5/a4/a6"), 0x4c,0xf8,0x50,0x28,0x12,0x00);
        assertArrayEquals(compile("movem.w (a0)+,d3/a4-a5"),      0x4c,0x98,0x30,0x08);
        assertArrayEquals(compile("movem.l (a0)+,d3/a4-a5"),      0x4c,0xd8,0x30,0x08);
    }

    public void testTST() {
        assertArrayEquals(compile("tst.b d3")     ,0x4a,0x03);
        assertArrayEquals(compile("tst.w (a4)")   ,0x4a,0x54);
        assertArrayEquals(compile("tst.l $12(a5)"),0x4a,0xad,0x00,0x12);
    }

    public void testEOR() {
        assertArrayEquals(compile("eor.l d2,d3"),   0xb5,0x83);
        assertArrayEquals(compile("eor.b d3,$1200"),0xb7,0x38,0x12,0x00);
        assertArrayEquals(compile("eor.w d3,$1200"),0xb7,0x78,0x12,0x00);
        assertArrayEquals(compile("eor.l d3,$1200"),0xb7,0xb8,0x12,0x00);
        assertArrayEquals(compile("eor.w d2,d3"),   0xb5,0x43);
        assertArrayEquals(compile("eor.b d2,d3"),   0xb5,0x03);
    }

    public void testOR() {
        assertArrayEquals(compile("or.b d3,$1200"),0x87,0x38,0x12,0x00);
        assertArrayEquals(compile("or.b $1200,d3"),0x86,0x38,0x12,0x00);
        assertArrayEquals(compile("or.b d2,d3   "),0x86,0x02);
        assertArrayEquals(compile("or.w d3,$1200"),0x87,0x78,0x12,0x00);
        assertArrayEquals(compile("or.w $1200,d3"),0x86,0x78,0x12,0x00);
        assertArrayEquals(compile("or.w d2,d3   "),0x86,0x42);
        assertArrayEquals(compile("or.l d3,$1200"),0x87,0xb8,0x12,0x00);
        assertArrayEquals(compile("or.l $1200,d3"),0x86,0xb8,0x12,0x00);
        assertArrayEquals(compile("or.l d2,d3   "),0x86,0x82);
    }

    public void testAND() {
        assertArrayEquals(compile("and.b d3,$1200"),0xc7,0x38,0x12,0x00);
        assertArrayEquals(compile("and.b $1200,d3"),0xc6,0x38,0x12,0x00);
        assertArrayEquals(compile("and.b d2,d3   "),0xc6,0x02);
        assertArrayEquals(compile("and.w d3,$1200"),0xc7,0x78,0x12,0x00);
        assertArrayEquals(compile("and.w $1200,d3"),0xc6,0x78,0x12,0x00);
        assertArrayEquals(compile("and.w d2,d3   "),0xc6,0x42);
        assertArrayEquals(compile("and.l d3,$1200"),0xc7,0xb8,0x12,0x00);
        assertArrayEquals(compile("and.l $1200,d3"),0xc6,0xb8,0x12,0x00);
        assertArrayEquals(compile("and.l d2,d3   "),0xc6,0x82);
    }

    public void testSCC() {
        assertArrayEquals(compile("st  $2000"),0x50,0xf8,0x20,0x00);
        assertArrayEquals(compile("sf  $2000"),0x51,0xf8,0x20,0x00);
        assertArrayEquals(compile("shi $2000"),0x52,0xf8,0x20,0x00);
        assertArrayEquals(compile("sls $2000"),0x53,0xf8,0x20,0x00);
        assertArrayEquals(compile("scc $2000"),0x54,0xf8,0x20,0x00);
        assertArrayEquals(compile("scs $2000"),0x55,0xf8,0x20,0x00);
        assertArrayEquals(compile("sne $2000"),0x56,0xf8,0x20,0x00);
        assertArrayEquals(compile("seq $2000"),0x57,0xf8,0x20,0x00);
        assertArrayEquals(compile("svc $2000"),0x58,0xf8,0x20,0x00);
        assertArrayEquals(compile("svs $2000"),0x59,0xf8,0x20,0x00);
        assertArrayEquals(compile("spl $2000"),0x5a,0xf8,0x20,0x00);
        assertArrayEquals(compile("smi $2000"),0x5b,0xf8,0x20,0x00);
        assertArrayEquals(compile("sge $2000"),0x5c,0xf8,0x20,0x00);
        assertArrayEquals(compile("slt $2000"),0x5d,0xf8,0x20,0x00);
        assertArrayEquals(compile("sgt $2000"),0x5e,0xf8,0x20,0x00);
        assertArrayEquals(compile("sle $2000"),0x5f,0xf8,0x20,0x00);
    }

    public void testBitInstructions()
    {
        assertArrayEquals(compile("btst #5,d6")    ,0x08,0x06,0x00,0x05);
        assertArrayEquals(compile("btst d4,d6")    ,0x09,0x06);
        assertArrayEquals(compile("btst #5,(a0)")    ,0x08,0x10,0x00,0x05);

        assertArrayEquals(compile("bclr #5,d6")   ,0x08,0x86,0x00,0x05);
        assertArrayEquals(compile("bclr d4,d6")   ,0x09,0x86);
        assertArrayEquals(compile("bclr #5,(a0)") ,0x08,0x90,0x00,0x05);

        assertArrayEquals(compile("bset #5,d6")   ,0x08,0xc6,0x00,0x05);
        assertArrayEquals(compile("bset d4,d6")   ,0x09,0xc6);
        assertArrayEquals(compile("bset #5,(a0)") ,0x08,0xd0,0x00,0x05);

        assertArrayEquals(compile("bchg #5,d6")  ,0x08,0x46,0x00,0x05);
        assertArrayEquals(compile("bchg d4,d6")  ,0x09,0x46);
        assertArrayEquals(compile("bchg #5,(a0)"),0x08,0x50,0x00,0x05);
    }

    public void testIllegal()
    {
        assertArrayEquals(compile("illegal")    ,0b01001010,0b11111100);
    }

    public void testMoveToUSP() {

        assertArrayEquals(compile("move a3,usp")    ,0x4e,0x63 );
    }

    public void testExt() {
        assertArrayEquals(compile("ext.w d3")    ,0x48,0x83 );
        assertArrayEquals(compile("ext.l d3")    ,0x48,0xc3 );
    }

    public void testBSR() {

        assertArrayEquals(compile("org $2000\n" +
                "bsr sub\n" +
                "move #2,d1\n" +
                "illegal\n" +
                "sub:\n" +
                "move #1,d0\n" +
                "rts")    ,         0x61,0x08,
            0x32,0x3c,0x00,0x02,
            0x4a,0xfc,
            0x30,0x3c,0x00,0x01,
            0x4e,0x75);
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

    public void testANDICCR() {
        assertArrayEquals(compile("AND #$12,ccr")    ,0x02,0x3c,0x00,0x12 );
        assertFailsToCompile( "AND.l #$12,ccr");
        assertFailsToCompile( "AND.w #$12,ccr");
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

    public void testCLR()
    {
        assertArrayEquals(compile("clr.b d3"), 0x42,0x03 );
        assertArrayEquals(compile("clr.w d3"), 0x42,0x43 );
        assertArrayEquals(compile("clr.l d3"), 0x42,0x83 );
    }

    public void testPEA() {
        assertArrayEquals(compile("pea (a3)")    ,0x48,0x53);
    }

    public void testJSR() {
        assertArrayEquals(compile("jsr next\nnext:")    ,0x4e,0xb8,0x00,0x04);
        assertArrayEquals(compile("jsr $2(pc)")    ,0x4e,0xba,0x00,0x02);
    }

    public void testNOT()
    {
        /*
   0:   4610            notb %a0@
   2:   4643            notw %d3
   4:   46b8 1200       notl 0x1200
         */
        assertArrayEquals(compile("not.l $1200"),0x46,0xb8, 0x12,0x00);
        assertArrayEquals(compile("not.b (a0)") ,0x46,0x10);
        assertArrayEquals(compile("not.w d3")   ,0x46,0x43);
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

    public void testStop()
    {
        assertArrayEquals(compile("stop #12"),0x4e,0x72,0x00,0x0c);
    }

    public void testAddx() {
        assertArrayEquals(compile("addx.b d3,d4    "),0xd9,0x03);
        assertArrayEquals(compile("addx.w d3,d4    "),0xd9,0x43);
        assertArrayEquals(compile("addx.l d3,d4    "),0xd9,0x83);
        assertArrayEquals(compile("addx.b -(a1),-(a2)"),0xd5,0x09);
        assertArrayEquals(compile("addx.w -(a1),-(a2)"),0xd5,0x49);
        assertArrayEquals(compile("addx.l -(a1),-(a2)"),0xd5,0x89);
    }

    public void testMulu()
    {
        assertArrayEquals(compile("mulu.w $1200,d3"),0xc6,0xf8,0x12,0x00);
        assertArrayEquals(compile("mulu.w d4,d3"),0xc6,0xc4);
    }

    public void testCmp()
    {
        assertArrayEquals(compile("cmp.b $1200,d2") ,0xb4,0x38,0x12,0x00);
        assertArrayEquals(compile("cmp.w (a3)+,d3") ,0xb6,0x5b);
        assertArrayEquals(compile("cmp.l -(a4),d4") ,0xb8,0xa4);
    }

    public void testSubx() {
        assertArrayEquals(compile("subx.b d1,d2"),0x95,0x01);
        assertArrayEquals(compile("subx.w d1,d2"),0x95,0x41);
        assertArrayEquals(compile("subx.l d1,d2"),0x95,0x81);
        assertArrayEquals(compile("subx.b -(a1),-(a2)"),0x95,0x09);
        assertArrayEquals(compile("subx.w -(a1),-(a2)"),0x95,0x49);
        assertArrayEquals(compile("subx.l -(a1),-(a2)"),0x95,0x89);
    }

    public void testNegx()
    {
        assertArrayEquals(compile("negx.b $12000"),0x40,0x39,0x00,0x01,0x20,0x00);
        assertArrayEquals(compile("negx.w d3")    ,0x40,0x43);
        assertArrayEquals(compile("negx.l (a4)+") ,0x40,0x9c);
    }

    public void testSub() {
        assertArrayEquals(compile("sub.b d3,$1200"),0x97,0x38,0x12,0x00);
        assertArrayEquals(compile("sub.b $1200,d3"),0x96,0x38,0x12,0x00);
        assertArrayEquals(compile("sub.b d2,d3   "),0x96,0x02          );
        assertArrayEquals(compile("sub.w d3,$1200"),0x97,0x78,0x12,0x00);
        assertArrayEquals(compile("sub.w $1200,d3"),0x96,0x78,0x12,0x00);
        assertArrayEquals(compile("sub.w d2,d3   "),0x96,0x42          );
        assertArrayEquals(compile("sub.l d3,$1200"),0x97,0xb8,0x12,0x00);
        assertArrayEquals(compile("sub.l $1200,d3"),0x96,0xb8,0x12,0x00);
        assertArrayEquals(compile("sub.l d2,d3   "),0x96,0x82          );
    }

    public void testAdd() {
        assertArrayEquals(compile("add.b d3,$1200"),0xd7,0x38,0x12,0x00);
        assertArrayEquals(compile("add.b $1200,d3"),0xd6,0x38,0x12,0x00);
        assertArrayEquals(compile("add.b d2,d3   "),0xd6,0x02);
        assertArrayEquals(compile("add.w d3,$1200"),0xd7,0x78,0x12,0x00);
        assertArrayEquals(compile("add.w $1200,d3"),0xd6,0x78,0x12,0x00);
        assertArrayEquals(compile("add.w d2,d3   "),0xd6,0x42);
        assertArrayEquals(compile("add.l d3,$1200"),0xd7,0xb8,0x12,0x00);
        assertArrayEquals(compile("add.l $1200,d3"),0xd6,0xb8,0x12,0x00);
        assertArrayEquals(compile("add.l d2,d3   "),0xd6,0x82);
    }

    public void testCmpa() {

        assertArrayEquals(compile("cmpa.w $1200,a4") ,0xb8,0xf8,0x12,0x00);
        assertArrayEquals(compile("cmpa.l $1200,a4") ,0xb9,0xf8,0x12,0x00);
        assertArrayEquals(compile("cmpa.l (a3)+,a3 "),0xb7,0xdb);
        assertArrayEquals(compile("cmpa.l (a3)+,a3 "),0xb7,0xdb);
        assertArrayEquals(compile("cmpa.w a4,a5 ")   ,0xba,0xcc);
    }

    public void testSuba() {

        assertArrayEquals(compile("suba.w $1200,a4"),0x98,0xf8,0x12,0x00);
        assertArrayEquals(compile("suba.l $1200,a4"),0x99,0xf8,0x12,0x00);
        assertArrayEquals(compile("suba.l (a3)+,a3 "),0x97,0xdb          );
        assertArrayEquals(compile("suba.l (a3)+,a3 "),0x97,0xdb          );
        assertArrayEquals(compile("suba.w a4,a5 "),0x9a,0xcc          );
    }

    public void testAdda() {
        assertArrayEquals(compile("adda.w $1200,a4"),0xd8,0xf8,0x12,0x00);
        assertArrayEquals(compile("adda.l $1200,a4"),0xd9,0xf8,0x12,0x00);
        assertArrayEquals(compile("adda.l (a3)+,a3 "),0xd7,0xdb);
    }

    public void testCmpm() {
        assertArrayEquals(compile("cmpm.b (a3)+,(a4)+"),0xb9,0x0b);
        assertArrayEquals(compile("cmpm.w (a3)+,(a4)+"),0xb9,0x4b);
        assertArrayEquals(compile("cmpm.l (a3)+,(a4)+"),0xb9,0x8b);
    }

    public void testCmpi()
    {
        assertArrayEquals(compile("cmpi.b #10,$1200"),0x0c,0x38,0x00,0x0a,0x12,0x00);
        assertArrayEquals(compile("cmpi.w #10,$1200"),0x0c,0x78,0x00,0x0a,0x12,0x00);
        assertArrayEquals(compile("cmpi.l #10,$1200"),0x0c,0xb8,0x00,0x00,0x00,0x0a,0x12,0x00);
    }

    public void testSubi() {
        assertArrayEquals(compile("subi.b #10,$1200"),0x04,0x38,0x00,0x0a,0x12,0x00);
        assertArrayEquals(compile("subi.w #10,$1200"),0x04,0x78,0x00,0x0a,0x12,0x00);
        assertArrayEquals(compile("subi.l #10,$1200"),0x04,0xb8,0x00,0x00,0x00,0x0a,0x12,0x00);
    }

    public void testAddi() {
        assertArrayEquals(compile("addi.b #10,$1200"),0x06,0x38,0x00,0x0a,0x12,0x00);
        assertArrayEquals(compile("addi.w #10,$1200"),0x06,0x78,0x00,0x0a,0x12,0x00);
        assertArrayEquals(compile("addi.l #10,$1200"),0x06,0xb8,0x00,0x00,0x00,0x0a,0x12,0x00);
    }

    public void testSubq()
    {
        assertArrayEquals(compile("subq.b #1,d3")     ,0x53,0x03           );
        assertArrayEquals(compile("subq.b #1,$1200")  ,0x53,0x38,0x12,0x00 );
        assertArrayEquals(compile("subq.w #8,d3")     ,0x51,0x43           );
        assertArrayEquals(compile("subq.w #8,a3")     ,0x51,0x4b           );
        assertArrayEquals(compile("subq.w #8,$1200")  ,0x51,0x78,0x12,0x00 );
        assertArrayEquals(compile("subq.l #8,d3")     ,0x51,0x83           );
        assertArrayEquals(compile("subq.l #8,a3")     ,0x51,0x8b           );
        assertArrayEquals(compile("subq.l #8,$1200")  ,0x51,0xb8,0x12,0x00 );
    }

    public void testAddq()
    {
        assertArrayEquals(compile("addq.b #1,d3"),0x52,0x03);
        assertArrayEquals(compile("addq.b #1,$1200"),0x52,0x38,0x12,0x00);
        assertArrayEquals(compile("addq.w #8,d3"),0x50,0x43);
        assertArrayEquals(compile("addq.w #8,a3"),0x50,0x4b);
        assertArrayEquals(compile("addq.w #8,$1200"),0x50,0x78,0x12,0x00);
        assertArrayEquals(compile("addq.l #8,d3"),0x50,0x83);
        assertArrayEquals(compile("addq.l #8,a3"),0x50,0x8b);
        assertArrayEquals(compile("addq.l #8,$1200"),0x50,0xb8,0x12,0x00);
    }

    public void testMuls()
    {
        assertArrayEquals(compile("muls.w $1200,d3"),0xc7,0xf8,0x12,0x00);
        assertArrayEquals(compile("muls.w d4,d3"),0xc7,0xc4);
    }

    public void testDivu()
    {
        assertArrayEquals(compile("divu.w $1200,d3"),0x86,0xf8,0x12,0x00);
        assertArrayEquals(compile("divu.w d4,d3"),   0x86,0xc4);
    }

    public void testDivs()
    {
        assertArrayEquals(compile("divs.w $1200,d3"),0x87,0xf8,0x12,0x00);
        assertArrayEquals(compile("divs.w d4,d3"),   0x87,0xc4);
    }

    public void testAsl()
    {
        assertArrayEquals(compile("asl $1234"  ),0xe1,0xf8,0x12,0x34);
        assertArrayEquals(compile("asl.w #3,d2"),0xe7,0x42);
        assertArrayEquals(compile("asl.w #8,d2"),0xe1,0x42);
        assertArrayEquals(compile("asl.w d1,d2"),0xe3,0x62);
        assertArrayEquals(compile("asl.b d1,d2"),0xe3,0x22);
        assertArrayEquals(compile("asl.l d1,d2"),0xe3,0xa2);
        assertArrayEquals(compile("asl.b #3,d2"),0xe7,0x02);
        assertArrayEquals(compile("asl.l #3,d2"),0xe7,0x82);
    }

    public void testLsl()
    {
        assertArrayEquals(compile("lsl $1234"  ),0xe3,0xf8,0x12,0x34);
        assertArrayEquals(compile("lsl.w #3,d2"),0xe7,0x4a);
        assertArrayEquals(compile("lsl.w #8,d2"),0xe1,0x4a);
        assertArrayEquals(compile("lsl.w d1,d2"),0xe3,0x6a);
        assertArrayEquals(compile("lsl.b d1,d2"),0xe3,0x2a);
        assertArrayEquals(compile("lsl.l d1,d2"),0xe3,0xaa);
        assertArrayEquals(compile("lsl.b #3,d2"),0xe7,0x0a);
        assertArrayEquals(compile("lsl.l #3,d2"),0xe7,0x8a);
    }

    public void testRoxl()
    {
        assertArrayEquals(compile("roxl.w d1,d2"),  0xe3,0x72);
        assertArrayEquals(compile("roxl.b d1,d2"),  0xe3,0x32);
        assertArrayEquals(compile("roxl.l d1,d2"),  0xe3,0xb2);
        assertArrayEquals(compile("roxl.w #1,d2"),  0xe3,0x52);
        assertArrayEquals(compile("roxl.b #2,d2"),  0xe5,0x12);
        assertArrayEquals(compile("roxl.l #8,d2"),  0xe1,0x92);
        assertArrayEquals(compile("roxl $1234"),    0xe5,0xf8,0x12,0x34 );
        assertArrayEquals(compile("roxl $12345678"),0xe5,0xf9,0x12,0x34,0x56,0x78);
    }

    public void testRoxr()
    {
        assertArrayEquals(compile("roxr.w d1,d2"),  0xe2,0x72                    );
        assertArrayEquals(compile("roxr.b d1,d2"),  0xe2,0x32                    );
        assertArrayEquals(compile("roxr.l d1,d2"),  0xe2,0xb2                    );
        assertArrayEquals(compile("roxr.w #1,d2"),  0xe2,0x52                    );
        assertArrayEquals(compile("roxr.b #2,d2"),  0xe4,0x12                    );
        assertArrayEquals(compile("roxr.l #8,d2"),  0xe0,0x92                    );
        assertArrayEquals(compile("roxr $1234"),    0xe4,0xf8,0x12,0x34          );
        assertArrayEquals(compile("roxr $12345678"),0xe4,0xf9,0x12,0x34,0x56,0x78);
    }

    public void testTAS() {
        assertArrayEquals(compile("tas $1234"  ),0x4a,0xf8,0x12,0x34);
        assertArrayEquals(compile("tas d3"  ),0x4a,0xc3);
    }

    public void testRol()
    {
        assertArrayEquals(compile("rol $1234"  ),0xe7,0xf8,0x12,0x34);

        assertArrayEquals(compile("rol.w #3,d2"),0xe7,0x5a);
        assertArrayEquals(compile("rol.w #8,d2"),0xe1,0x5a);

        assertArrayEquals(compile("rol.w d1,d2"),0xe3,0x7a);
        assertArrayEquals(compile("rol.b d1,d2"),0xe3,0x3a);
        assertArrayEquals(compile("rol.l d1,d2"),0xe3,0xba);
        assertArrayEquals(compile("rol.b #3,d2"),0xe7,0x1a);
        assertArrayEquals(compile("rol.l #3,d2"),0xe7,0x9a);
    }

    public void testChk()
    {
        assertArrayEquals(compile("chk $1200,d3"),  0x47,0xb8,0x12,0x00);
        assertArrayEquals(compile("chk.w $1200,d3"),0x47,0xb8,0x12,0x00);
        assertArrayEquals(compile("chk.l (a4),d7"),0x4f,0x14);
    }

    public void testAsr()
    {
        assertArrayEquals(compile("asr $1234"  ),0xe0,0xf8,0x12,0x34);
        assertArrayEquals(compile("asr.w #3,d2"),0xe6,0x42);
        assertArrayEquals(compile("asr.w #8,d2"),0xe0,0x42);
        assertArrayEquals(compile("asr.w d1,d2"),0xe2,0x62);
        assertArrayEquals(compile("asr.b d1,d2"),0xe2,0x22);
        assertArrayEquals(compile("asr.l d1,d2"),0xe2,0xa2);
        assertArrayEquals(compile("asr.b #3,d2"),0xe6,0x02);
        assertArrayEquals(compile("asr.l #3,d2"),0xe6,0x82);
    }

    public void testLsr()
    {
        assertArrayEquals(compile("lsr $1234"  ),0xe2,0xf8,0x12,0x34);
        assertArrayEquals(compile("lsr.w #3,d2"),0xe6,0x4a);
        assertArrayEquals(compile("lsr.w #8,d2"),0xe0,0x4a);
        assertArrayEquals(compile("lsr.w d1,d2"),0xe2,0x6a);
        assertArrayEquals(compile("lsr.b d1,d2"),0xe2,0x2a);
        assertArrayEquals(compile("lsr.l d1,d2"),0xe2,0xaa);
        assertArrayEquals(compile("lsr.b #3,d2"),0xe6,0x0a);
        assertArrayEquals(compile("lsr.l #3,d2"),0xe6,0x8a);
    }

    public void testRor() {
        assertArrayEquals(compile("ror.w d1,d2"),0xe2,0x7a);
        assertArrayEquals(compile("ror.b d1,d2"),0xe2,0x3a);
        assertArrayEquals(compile("ror.l d1,d2"),0xe2,0xba);
        assertArrayEquals(compile("ror.w #3,d2"),0xe6,0x5a);
        assertArrayEquals(compile("ror.w #8,d2"),0xe0,0x5a);
        assertArrayEquals(compile("ror.b #3,d2"),0xe6,0x1a);
        assertArrayEquals(compile("ror.l #3,d2"),0xe6,0x9a);
        assertArrayEquals(compile("ror $1234"  ),0xe6,0xf8,0x12,0x34);
    }

    public void testTrapV()
    {
        assertArrayEquals( compile( "trapv" ), 0x4e, 0x76 );
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

        asm.getOptions().debug=true;
        final CompilationMessages messages = asm.compile(root);
        if ( messages.hasErrors() )
        {
            messages.getMessages().stream().forEach(System.out::println );
            throw new RuntimeException("Compilation failed with errors");
        }
        //        System.out.println("RESULT: "+Memory.hexdump(0,data,0,data.length));
        return this.asm.getBytes(false);
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
