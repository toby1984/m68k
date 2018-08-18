package de.codersourcery.m68k.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Misc
{
    public static String hex(int value) {
        return "$"+Integer.toHexString(value);
    }

    /**
     * Reverses the lower 16 bits of an int.
     *
     * @param bits bits to reverse (only lower 16 bits are considered)
     * @return lower 16 bits of input, reversed
     */
    public static int reverseWord(int bits) {

        int out = 0;

        int inMask = 0x1;
        int outMask = 0x00008000;
        for ( int i = 16 ; i > 0 ; i--, outMask >>>=1 , inMask <<= 1)
        {
            if ( (bits & inMask) != 0 ) {
                out |= outMask;
            }
        }
        return out;
    }

    public static String binary3Bit(int value) {
        return "%"+StringUtils.leftPad(Integer.toBinaryString( value & 0b111),3, "0");
    }

    public static String binary8Bit(int value) {
        return "%"+StringUtils.leftPad(Integer.toBinaryString( value & 0xff),8, "0");
    }

    public static String binary16Bit(int value) {
        return "%"+StringUtils.leftPad(Integer.toBinaryString( value & 0xffff),16, "0");
    }

    public static String read(InputStream in) throws IOException
    {
        final InputStreamReader r = new InputStreamReader(in);
        final char[] buffer = new char[1024];
        int len = 0;
        final StringBuilder string = new StringBuilder();
        while ( (len = r.read(buffer) ) > 0 )
        {
            string.append(buffer,0,len);
        }
        return string.toString();
    }
}
