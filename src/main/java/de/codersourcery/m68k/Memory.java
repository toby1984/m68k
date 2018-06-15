package de.codersourcery.m68k;

import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;

public class Memory {

    private final byte[] data;

    public Memory(int sizeInBytes)
    {
        this.data = new byte[ sizeInBytes ];
    }
    public short readWord(int address) // return type NEEDS to be short, used for implicit sign extension 16 bits -> 32 bits
    {
        int hi = data[address];
        int lo = data[address+1];
        return (short) (hi<<8|(lo & 0xff));
    }

    public void writeWord(int address,int value)
    {
        data[address] = (byte) (value>>8);
        data[address+1] = (byte) value;
    }

    public byte readByte(int address) // return type NEEDS to be byte, used for implicit sign extension 8 bits -> 32 bits
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

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static final HexConverter HEX_CONVERTER = new HexConverter();
    private static final HexConverter BIN_CONVERTER = new BinConverter() {

    };

    public static class HexConverter
    {
        protected final char[] buffer;

        public HexConverter() {
            this(2);
        }

        protected HexConverter(int bufferSize)
        {
            this.buffer = new char[bufferSize];
        }

        public char[] convert(byte input)
        {
            buffer[0] = HEX_CHARS[ (input >> 4 ) & 0x0f ];
            buffer[1] = HEX_CHARS[ input & 0x0f ];
            return buffer;
        }
    }

    public static class BinConverter extends HexConverter
    {
        public BinConverter() {
            super(8);
        }

        public char[] convert(byte input)
        {
            buffer[0] = ( (input & ( 1<<7 )) != 0 ) ? '1' : '0';
            buffer[1] = ( (input & ( 1<<6 )) != 0 ) ? '1' : '0';
            buffer[2] = ( (input & ( 1<<5 )) != 0 ) ? '1' : '0';
            buffer[3] = ( (input & ( 1<<4 )) != 0 ) ? '1' : '0';
            buffer[4] = ( (input & ( 1<<3 )) != 0 ) ? '1' : '0';
            buffer[5] = ( (input & ( 1<<2 )) != 0 ) ? '1' : '0';
            buffer[6] = ( (input & ( 1<<1 )) != 0 ) ? '1' : '0';
            buffer[7] = ( (input & ( 1<<0 )) != 0 ) ? '1' : '0';
            return buffer;
        }
    }

    public static String hexdump(int startAddress, byte[] data, int offset, int count) {
        return dump(startAddress,data,offset,count,HEX_CONVERTER);
    }

    public static String bindump(int startAddress, byte[] data, int offset, int count) {
        return dump(startAddress,data,offset,count,BIN_CONVERTER);
    }

    private static String dump(int startAddress, byte[] data, int offset, int count, HexConverter converter)
    {

        final StringBuilder result = new StringBuilder();
        final StringBuilder ascii = new StringBuilder();
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
                final char[] value = converter.convert(v);
                result.append(value,0,value.length);
                if ( count-1 > 0 && (i+1) < 16) {
                  result.append(' ');
                }
            }
            result.append(" ").append( ascii );
        }
        return result.toString();
    }
}