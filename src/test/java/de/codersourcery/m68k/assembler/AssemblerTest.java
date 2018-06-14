package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.Memory;
import junit.framework.TestCase;

public class AssemblerTest extends TestCase
{
    private Assembler asm;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        asm = new Assembler();
    }

    public void testMove()
    {
        // Pattern: ooooDDDMMMmmmsss
        //          0010001000111100 00010010 00110100 01010110 01111000
        //          0010000001111100 00010010 00110100 01010110 01111000

        assertArrayEquals(compile("move   $1234(pc),d1")    ,0x32,0x3a,0x12,0x34);
        assertArrayEquals(compile("move.l #$12345678,a0"),0x20,0x7c,0x12,0x34,0x56,0x78);

        /*
         *  An                           => ADDRESS_REGISTER_DIRECT
         *  d16(An)                      => ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT
         * (d8,An, Xn.SIZE*SCALE)        => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
         * (bd,An,Xn.SIZE*SCALE)         => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
         * ([bd,An],Xn.SIZE*SCALE,od)    => MEMORY_INDIRECT_POSTINDEXED
         * ([bd, An, Xn.SIZE*SCALE], od) => MEMORY_INDIRECT_PREINDEXED
         * (d16 ,PC)                     => PC_INDIRECT_WITH_DISPLACEMENT
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
    }

    private byte[] compile(String s)
    {
        final IResource source = IResource.stringResource(s);
        final CompilationUnit root = new CompilationUnit(source);

        final CompilationMessages messages = asm.compile(root);
        if ( messages.hasErrors() )
        {
            messages.getMessages().stream().forEach(System.out::println );
            fail("Compilation failed with errors");
        }
        //        System.out.println("RESULT: "+Memory.hexdump(0,data,0,data.length));
        return this.asm.getBytes();
    }

    private static void assertArrayEquals(byte[] actual,int...values) {

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
