package de.codersourcery.m68k;

import org.apache.commons.lang3.StringUtils;

public class Memory {

    private final byte[] data;

    public Memory(int sizeInBytes)
    {
        this.data = new byte[ sizeInBytes ];
    }
    public int readWord(int address)
    {
        int hi = data[address];
        int lo = data[address+1];
        return hi<<8|(lo & 0xff);
    }

    public void writeWord(int address,int value)
    {
        data[address] = (byte) (value>>8);
        data[address+1] = (byte) value;
    }

    public int readByte(int address)
    {
        return data[address];
    }

    public void writeByte(int address,int value)
    {
        data[address] = (byte) value;
    }

    public int readLong(int address)
    {
        int hi = readWord(address);
        int lo = readWord(address+2);
        return hi << 16 | (lo & 0xffff);
    }

    public void writeLong(int address,int value)
    {
        writeWord(address,value>>16);
        writeWord(address+2,value);
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