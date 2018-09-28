package de.codesourcery.m68k.assembler;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SRecordHelperTest extends TestCase
{
    public void testWriteSRecordFile() throws IOException
    {
        final List<IObjectCodeWriter.Buffer> buffers = new ArrayList<>();
        final IObjectCodeWriter.Buffer buffer1 = new IObjectCodeWriter.Buffer( 0,1024 );
        buffer1.writeByte( (byte) 0x01 );
        buffer1.writeByte( (byte) 0x02 );
        buffer1.writeByte( (byte) 0x03 );
        buffers.add( buffer1 );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new SRecordHelper().write( buffers,out );
        final byte[] data = out.toByteArray();
        final String expected = "S00B0000746573742E68363830\n" +
                "S30800000000010203F1";
        assertEquals(expected, new String(data) );
    }
}
