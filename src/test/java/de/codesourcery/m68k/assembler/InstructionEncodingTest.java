package de.codesourcery.m68k.assembler;

import de.codesourcery.m68k.assembler.arch.Field;
import de.codesourcery.m68k.assembler.arch.InstructionEncoding;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;

public class InstructionEncodingTest extends TestCase
{

    public void testInstructionWordMasks() {

        InstructionEncoding encoding =
                InstructionEncoding.of("1111_1111_1111_1111");

        assertEquals(0xffff, encoding.getInstructionWordAndMask() );
        assertEquals(0xffff, encoding.getInstructionWordMask() );

        encoding =
                InstructionEncoding.of("0000_0000_0000_0000");

        assertEquals(0xffff, encoding.getInstructionWordAndMask() );
        assertEquals(0x0000, encoding.getInstructionWordMask() );

        encoding =
                InstructionEncoding.of("1001_x110_0ccc_0000");

        assertEquals(0b1111_0111_1000_1111, encoding.getInstructionWordAndMask() );
        assertEquals(0b1001_0110_0000_0000, encoding.getInstructionWordMask() );
    }

    public void testNullPatternFails()
    {
        try
        {
            InstructionEncoding.of(null);
            fail("Should've failed");
        } catch(NullPointerException e) {
            // ok
        }
        try
        {
            InstructionEncoding.of("");
            fail("Should've failed");
        } catch(IllegalArgumentException e) {
            // ok
        }
    }

    public void testNullPatternFails2()
    {
        try
        {
            InstructionEncoding.of("0000000000000000","0000000000000000",null);
            fail("Should've failed");
        } catch(NullPointerException e) {
            // ok
        }

        try
        {
            InstructionEncoding.of("0000000000000000","");
            fail("Should've failed");
        } catch(IllegalArgumentException e) {
            // ok
        }
    }

    public void testFixed16BitPattern()
    {
        final Function<Field,Integer> valueSource =
                field -> { throw new UnsupportedOperationException(); };

        final int expected = 0b1111000010010001;
        final String pattern = binaryWord(expected);
        final InstructionEncoding encoding = InstructionEncoding.of(pattern);
        assertEquals(1,encoding.getPatterns().length);
        assertEquals(pattern,encoding.getPatterns()[0]);
        assertEquals(1,encoding.getBitMappings().length);

        final byte[] data = encoding.apply(valueSource);
        assertEquals(2,data.length);

        final int value = toNumber(data);
        if ( expected != value ) {
            fail("\nExpected: "+binaryWord(expected)+"\nActual  : "+binaryWord(value) );
        }
    }

    private static int toNumber(byte[] data) {
        int result = 0;
        for ( byte b : data )
        {
            result <<=8;
            result |= (b&0xff);
        }
        return result;
    }

    public void testFixed32BitPattern()
    {
        final Function<Field,Integer> valueSource =
                field -> { throw new UnsupportedOperationException(); };

        final int expected = 0b1111000010010001_1111000010010101;
        final String pattern = binaryLong(expected);
        final InstructionEncoding encoding = InstructionEncoding.of(pattern);
        assertEquals(1,encoding.getPatterns().length);
        assertEquals(pattern,encoding.getPatterns()[0]);
        assertEquals(1,encoding.getBitMappings().length);

        final byte[] data = encoding.apply(valueSource);
        assertEquals(4,data.length);

        final int value = toNumber(data);
        if ( expected != value ) {
            fail("\nExpected: "+binaryWord(expected)+"\nActual  : "+binaryWord(value) );
        }
    }

    public void test16BitPattern()
    {
        final Function<Field,Integer> valueSource = field ->
                {
                    if ( field != Field.SRC_BASE_REGISTER) {
                        fail("Unexpected field: "+field);
                    }
                    return 0b101;
                };

        final String pattern = "11110sss10010001"; // 8-11
        final int expected = 0b1111010110010001;
        final InstructionEncoding encoding = InstructionEncoding.of(pattern);
        assertEquals(1,encoding.getPatterns().length);
        assertEquals(pattern,encoding.getPatterns()[0]);
        assertEquals(1,encoding.getBitMappings().length);

        final byte[] data = encoding.apply(valueSource);
        assertEquals(2,data.length);

        final int value = toNumber(data);
        if ( expected != value ) {
            fail("\nExpected: "+binaryWord(expected)+"\nActual  : "+binaryWord(value) );
        }
    }

    private static String binaryWord(int x) {
        String s = Integer.toBinaryString(x);
        return StringUtils.leftPad(s,16,'0');
    }

    private static String binaryLong(int x) {
        String s = Integer.toBinaryString(x);
        return StringUtils.leftPad(s,32,'0');
    }
}
