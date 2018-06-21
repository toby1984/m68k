package de.codersourcery.m68k.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Misc
{
    public static String hex(int value) {
        return "$"+Integer.toHexString(value );
    }

    public static String binary8Bit(int value) {
        return "%"+StringUtils.leftPad(Integer.toBinaryString( value ),8, "0");
    }

    public static String binary16Bit(int value) {
        return "%"+StringUtils.leftPad(Integer.toBinaryString( value ),16, "0");
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
