package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.emulator.exceptions.BadAlignmentException;
import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;
import de.codersourcery.m68k.emulator.exceptions.MemoryWriteProtectedException;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;

public class Memory {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static final HexConverter HEX_CONVERTER = new HexConverter();
    private static final HexConverter BIN_CONVERTER = new BinConverter();

    private static class HexConverter
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

    private static class BinConverter extends Memory.HexConverter
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

    public final MMU mmu;

    public Memory(MMU mmu)
    {
        this.mmu = mmu;
    }

    public void bulkWrite(int startAddress,byte[] data,int offset,int count)
    {
        final int firstPage = mmu.getPageNo( startAddress );
        final int startOffset = mmu.getOffsetInPage( startAddress );

        for ( int offsetInPage = startOffset, srcOffset = offset, currentPage = firstPage ; count > 0 ; )
        {
            final MemoryPage page = mmu.getPage( currentPage );
            if ( ! page.isWriteable() ) {
                final int pageStart = mmu.getPageStartAddress( currentPage );
                throw new MemoryWriteProtectedException( "Memory at "+Misc.hex(pageStart)+" is not writeable", MemoryAccessException.Operation.WRITE_BYTE,pageStart);
            }
            currentPage++;
            for ( ; count > 0 && offsetInPage <= 4095 ; count--) {
                page.writeByte( offsetInPage++, data[srcOffset++]);
            }
            offsetInPage = 0;
        }
    }

    public short readWord(int address) // return type NEEDS to be short, used for implicit sign extension 16 bits -> 32 bits when assigned to int later on
    {
        assertReadWordAligned(address);
        return readWordNoCheck(address);
    }

    public short readWordNoCheck(int address) // return type NEEDS to be short, used for implicit sign extension 16 bits -> 32 bits when assigned to int later on
    {
        final int p0 = mmu.getPageNo( address );
        final MemoryPage page = mmu.getPage( p0 );
        final int offset = mmu.getOffsetInPage( address );
        int hi = page.readByte(offset);
        int lo;
        if ( (offset+1) < 4096  ) {
            lo = page.readByte(offset+1);
        } else {
            lo = mmu.getPage( p0+1 ).readByte(0);
        }
        return (short) (hi<<8|(lo & 0xff));
    }

    public void writeWord(int address,int value)
    {
        assertWriteWordAligned(address);
        writeWordNoCheck(address,value);
    }

    private void checkPageWriteable(MemoryPage page, int pageNo)
    {
        if ( ! page.isWriteable() ) {
            final int pageStart = mmu.getPageStartAddress( pageNo );
            throw new MemoryWriteProtectedException( "Memory at "+Misc.hex(pageStart)+" is not writeable", MemoryAccessException.Operation.WRITE_BYTE,pageStart);
        }
    }

    private void writeWordNoCheck(int address,int value)
    {
        int p0 = mmu.getPageNo( address );
        MemoryPage page = mmu.getPage( p0 );
        checkPageWriteable(page,p0);
        final int offset = mmu.getOffsetInPage( address );
        page.writeByte(offset,value>>8); // hi
        if ( (offset+1) < 4096  ) {
            page.writeByte(offset+1,value); // lo
        } else {
            p0++;
            page = mmu.getPage( p0 );
            checkPageWriteable(page,p0);
            page.writeByte(0,value); // lo
        }
    }

    public byte readByte(int address) // return type NEEDS to be byte, used for implicit sign extension 8 bits -> 32 bits when assigned to int later on
    {
        final int pageNo = mmu.getPageNo( address );
        final int offset = mmu.getOffsetInPage( address );
        return mmu.getPage( pageNo ).readByte( offset );
    }

    public void writeByte(int address,int value)
    {
        final int offset = mmu.getOffsetInPage( address );
        final int pageNo = mmu.getPageNo( address );
        final MemoryPage page = mmu.getPage( pageNo );
        checkPageWriteable( page, pageNo );
        page.writeByte( offset, value);
    }

    public void writeBytes(int address,byte[] data)
    {
        int pageNo = mmu.getPageNo( address );
        int offset = mmu.getOffsetInPage( address );
        int count = data.length;
        int srcIdx = 0;
        while ( count > 0 ) {
            final MemoryPage page = mmu.getPage( pageNo );
            checkPageWriteable( page, pageNo );
            while ( count > 0 && offset < 4096 ) {
                page.writeByte( offset++, data[srcIdx++] );
                count--;
            }
            offset = 0;
            pageNo++;
        }
    }

    public int readLongNoCheck(int address)
    {
        int hi = readWordNoCheck(address);
        int lo = readWordNoCheck(address+2);
        return (hi << 16) | (lo & 0xffff);
    }

    public int readLong(int address)
    {
        assertReadLongAligned(address);
        return readLongNoCheck(address);
    }

    public void writeLong(int address,int value)
    {
        assertWriteLongAligned(address);
        writeWordNoCheck(address,value>>16);
        writeWordNoCheck(address+2,value);
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
            result.append(" ").append( ascii ).append("\n");
        }
        return result.toString();
    }

    private static void assertReadWordAligned(int address)
    {
        if ( (address & 1 ) != 0 ) {
            throw new BadAlignmentException(MemoryAccessException.Operation.READ_WORD,address);
        }
    }

    private static void assertWriteWordAligned(int address)
    {
        if ( (address & 1 ) != 0 ) {
            throw new BadAlignmentException(MemoryAccessException.Operation.WRITE_WORD,address);
        }
    }

    private static void assertReadLongAligned(int address)
    {
        if ( (address & 1 ) != 0 ) {
            throw new BadAlignmentException(MemoryAccessException.Operation.READ_LONG,address);
        }
    }

    private static void assertWriteLongAligned(int address)
    {
        if ( (address & 1 ) != 0 ) {
            throw new BadAlignmentException(MemoryAccessException.Operation.WRITE_LONG,address);
        }
    }
}