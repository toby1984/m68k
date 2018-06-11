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
        //case 's': return SRC_REGISTER;
        //case 'm': return SRC_MODE;
        //case 'D': return DST_REGISTER;
        //case 'M': return DST_MODE;
        //case 'S': return SIZE;

        // 0001DDDMMMmmmsss
        // 0001001000010000
        assertArrayEquals(compile("move.b ($1234),d1"),0x12,0x38,0x12,0x34);

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
        assertArrayEquals(compile("move   ($1234),d1"),0x32,0x38,0x12,0x34);
        assertArrayEquals(compile("move.w ($1234),d1"),0x32,0x38,0x12,0x34);
        assertArrayEquals(compile("move.l ($1234),d1"),0x22,0x38,0x12,0x34);

        assertArrayEquals(compile("move.b $10(a0),d1"),0x12,0x28,0x00,0x10);
        assertArrayEquals(compile("move   $10(a0),d1"),0x32,0x28,0x00,0x10);
        assertArrayEquals(compile("move.w $10(a0),d1"),0x32,0x28,0x00,0x10);
        assertArrayEquals(compile("move.l $10(a0),d1"),0x22,0x28,0x00,0x10);

        /*
  40:   13fc 0012 0000  moveb #18,0x0
  46:   0000
  48:   33fc 1234 0000  movew #4660,0x0
  4e:   0000
  50:   23fc 1234 5678  movel #305419896,0x0
  56:   0000 0000
         */

        assertArrayEquals(compile("move.b #$12,d1")      ,0x13,0xfc,0x00,0x12,0x00,0x00);
        assertArrayEquals(compile("move   #$1234,d1")    ,0x33,0xfc,0x12,0x34,0x00,0x00);
        assertArrayEquals(compile("move.w #$1234,d1")    ,0x33,0xfc,0x12,0x34,0x00,0x00);
        assertArrayEquals(compile("move.l #$12345678,d1"),0x23,0xfc,0x12,0x34,0x56,0x78);
    }

    private byte[] compile(String s)
    {
        final IResource source = IResource.stringResource(s);
        final CompilationUnit root = new CompilationUnit(source);

        final CompilationMessages messages = asm.compile(root);
        assertFalse(messages.hasErrors());
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
        for ( int i = 0 ; i < len ; i++ )
        {
            if ( expected[i] != actual[i] ) {
                fail = true;
            }
        }
        if ( fail )
        {
            System.out.println("EXPECTED: "+Memory.hexdump(0,expected,0,expected.length));
            System.out.println("ACTUAL  : "+Memory.hexdump(0,actual,0,actual.length));
            fail("Arrays do not match");
        }
    }
}
