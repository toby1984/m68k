package de.codersourcery.m68k;

import org.apache.commons.lang3.StringUtils;

public class Memory {

    private final short[] data;

    public Memory(int sizeInBytes)
    {
        this.data = new short[ sizeInBytes/2 ];
    }
    public int readWord(int address)
    {
        return data[address>>1] & 0xffff;
    }

    public void writeWord(int address,int value)
    {
        data[address>>1] = (short) value;
    }

    public int readByte(int address)
    {
        int value = data[ address >> 1];
        if ( (address & 1) == 0 ) {
            return value & 0xff;
        }
        return (value>>8) & 0xff;
    }

    public void writeByte(int address,int value)
    {
        int trimmed = value & 0xff;
        int mask = 0x00ff;
        if ( (address & 1) == 0 )
        {
            trimmed <<= 8;
            mask = 0xff00;
        }
        data[address>>1] = (short) ( (data[address>>1] & ~mask) | trimmed );
    }

    public int readLong(int address)
    {
        final int shifted = address>>1;
        int low   = data[ shifted+1 ] & 0xffff;
        int hi  = data[ shifted ] & 0xffff;
        return hi << 16 | low;
    }

    public void writeLong(int address,int value)
    {
        final int shifted = address>>1;
        data[ shifted+1  ] = (short) value; // low
        data[ shifted ] = (short) ((value>>16) & 0xffff); // high
    }

    public String hexdump(int startAddress,int count)
    {
        final byte[] tmp = new byte[count];
        for ( int i = 0 ; i < count ; i++ ) {
            tmp[i] = (byte) readByte(startAddress+i);
        }
        return hexdump(startAddress, tmp, 0,count);
    }

    public static String hexdump(int startAddress, byte[] data, int offset, int count) {

        StringBuffer result = new StringBuffer();
        StringBuffer ascii = new StringBuffer();
        while ( count > 0 )
        {
            final String adr = Integer.toHexString(startAddress);
            startAddress += 16;
            result.append(StringUtils.leftPad(adr,4,'0')).append(": ");

            ascii.setLength(0);
            for ( int i = 0 ; i < 16 && count > 0 ; count-- )
            {
                final byte v = data[offset++];
                if ( v >=32 && v <127) {
                    ascii.append( (char) (int) v);
                } else {
                    ascii.append(".");
                }
                final String value = Integer.toHexString( v );
                result.append( StringUtils.leftPad(value,2,'0'));
                if ( count-1 > 0 && (i+1) < 16) {
                  result.append(' ');
                }
            }
            result.append(" ").append( ascii );
        }
        return result.toString();
    }
}