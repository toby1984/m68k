package de.codesourcery.m68k.assembler;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class SRecordHelper
{
    private static final Logger LOG = LogManager.getLogger( SRecordHelper.class.getName() );

    public enum SRecordType
    {
        S0(2,true),
        S1(2,true),
        S2(3,true),
        S3(4,true),
        S5(2,false),
        S7(4,false),
        S8(3,false),
        S9(2,false);

        public final int addressByteCount;
        public final boolean hasData;

        SRecordType(int addressByteCount, boolean hasData)
        {
            this.addressByteCount = addressByteCount;
            this.hasData = hasData;
        }
    }

    public static final class SRecord
    {
        public final SRecordType type;
        public int address;
        private byte[] data = new byte[0];
        private int checksum;

        public SRecord(SRecordType type, int address, byte[] data) {
            this(type,address,data,0);
        }

        public SRecord(SRecordType type, int address, byte[] data, int checksum)
        {
            Validate.notNull(data, "data must not be null");
            Validate.notNull(type, "type must not be null");
            this.type = type;
            this.address = address;
            this.data = data;
            this.checksum = checksum;
        }

        public SRecord updateChecksum() {
            checksum = calcChecksum();
            return this;
        }

        public static SRecord parse(String line) {

            final char[] chars = line.toCharArray();
            int ptr = 0;

            // parse record type
            if ( chars[ptr++] != 'S' ) {
                throw new IllegalArgumentException("Expected line to start with S but was "+line);
            }
            final String sType = "S"+chars[ptr++];
            final SRecordType type =
                Stream.of( SRecordType.values() ).filter(t -> t.name().equals( sType ) ).findFirst().orElse(null);
            if ( type == null ) {
                throw new IllegalArgumentException("Unknown srecord type "+sType+": "+line);
            }

            // parse byte count
            final int byteCount = readByte(chars,ptr);
            ptr += 2;

            // parse address
            int address = 0;
            for ( int i = 0 ; i < type.addressByteCount ; i++)
            {
                address <<= 8;
                address |= readByte(chars,ptr);
                ptr += 2;
            }

            // parse data
            byte[] data = new byte[0];
            if ( type.hasData )
            {
                int dataLen = byteCount - type.addressByteCount - 1;
                data = new byte[dataLen];
                for ( int i = 0 ; i < dataLen ; i++)
                {
                    data[i] = (byte) readByte(chars,ptr);
                    ptr+= 2;
                }
            }

            // parse checksum
            final int chksum = readByte(chars,ptr);
            final SRecord result = new SRecord(type,address,data,chksum);
            if ( ! result.isChecksumOk() ) {
                throw new RuntimeException("Checksum error, actual: "+hex(chksum,2)+" <-> expected: "+hex(result.calcChecksum(),2));
            }
            return result;
        }

        private static int readByte(char[] chars,int offset) {
            final char hi = chars[offset];
            final char lo = chars[offset+1];
            return Integer.parseInt(""+hi+lo, 16 ) & 0xff;
        }

        public byte[] getData()
        {
            return data;
        }

        public String toString()
        {
            final int byteCount = type.addressByteCount+data.length+1;
            return type.name()+hex(byteCount,2)+hex(address,type.addressByteCount*2)+
                dataAsHex()+hex(checksum,2);
        }

        private String dataAsHex()
        {
            if ( type.hasData ) {
                final StringBuilder hex = new StringBuilder();
                for (byte aData : data)
                {
                    hex.append(hex(aData & 0xff, 2));
                }
                return hex.toString();
            }
            return "";
        }

        private static String hex(int value,int pad)
        {
            return StringUtils.leftPad(Integer.toHexString(value),pad,'0').toUpperCase();
        }

        public boolean isChecksumOk() {
            return this.checksum == calcChecksum();
        }

        public int calcChecksum()
        {
            // zwei hexadezimale Ziffern - das Einerkomplement des niederwertigen Bytes der Summe von byte count,
            // Adresse (byteweise) und der Daten (ebenfalls byteweise)
            int sum = 0;
            final int byteCount = type.addressByteCount+1+data.length;
            sum += byteCount;
            int adrMask = 0xff;
            int shift = ((type.addressByteCount-1)*8);
            adrMask <<= shift;
            for ( int i = 0 ; i < type.addressByteCount ; i++ )
            {
                final int value = (address & adrMask) >> shift;
                sum += value;
                adrMask >>>= 8;
                shift -= 8;
            }
            if ( type.hasData )
            {
                for (int i = 0; i < data.length; i++)
                {
                    int value = data[i] & 0xff;
                    sum += value;
                }
            }
            return (~sum & 0xff);
        }

        public void setData(byte[] data)
        {
            if ( ! this.type.hasData ) {
                throw new UnsupportedOperationException("Type "+type+" does not support data");
            }
            Validate.notNull(data, "data must not be null");
            this.data = data;
            this.checksum = calcChecksum();
        }
    }

    public static final class SRecordFile
    {
        private final List<SRecord> records = new ArrayList<>();

        public void add(SRecord record)
        {
            Validate.notNull(record, "record must not be null");
            if ( ! record.isChecksumOk() ) {
                throw new RuntimeException("Invalid checksum: "+record);
            }
            this.records.add(record);
        }
    }

    public void write(List<IObjectCodeWriter.Buffer> buffers, OutputStream out) throws IOException
    {
        try ( BufferedWriter w = new BufferedWriter( new OutputStreamWriter( out ) ) )
        {
            w.write(
                new SRecord(  SRecordType.S0,0,"test.h68".getBytes() ).updateChecksum().toString()
            );
            for (IObjectCodeWriter.Buffer buf : buffers )
            {
                if ( ! buf.isEmpty() )
                {
                    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    buf.appendTo( outStream );
                    final byte[] data = outStream.toByteArray();
                    int toWrite = data.length;
                    int ptr = 0;
                    while ( toWrite > 0 )
                    {
                        final int len = toWrite > 254 ? 254 : toWrite;
                        final byte[] subArray = Arrays.copyOfRange(data,ptr,ptr+len);
                        w.write("\n");
                        w.write(
                            // S3 => 4 address bytes
                            new SRecord(SRecordType.S3,buf.startOffset+ptr,subArray ).updateChecksum().toString()
                        );
                        ptr += len;
                        toWrite -= len;
                    }
                }
            }
        }
    }

    public SRecordFile load(InputStream in) throws IOException
    {
        final SRecordFile result = new SRecordFile();

        int lineNo = 1;
        try ( BufferedReader reader = new BufferedReader(new InputStreamReader(in) ) )
        {
            String line = null;
            while ( ( line = reader.readLine() ) != null )
            {
                line = line.trim();
                LOG.info(  line  );
                try
                {
                    result.add(SRecord.parse(line));
                }
                catch(Exception e)
                {
                    throw new IOException("Failed to parse line "+lineNo+": "+e.getMessage());
                }
                lineNo++;
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {

        final int[] data = {0x0B, 0x00, 0x00, 0x74, 0x65, 0x73, 0x74, 0x2E, 0x68, 0x36, 0x38};
        int sum = 0;
        for ( int i = 0 ; i < data.length ; i++) {
            sum += data[i];
        }
        sum = ~sum;
        LOG.info( "Got: "+Integer.toHexString(sum)  );

        final FileWriter writer = new FileWriter("/tmp/out.h68");
        final SRecordFile file = new SRecordHelper().load(new FileInputStream("/home/tobi/tmp/sim/test.h68"));
        for ( SRecord r : file.records )
        {
            LOG.info(  r.toString()  );
            writer.write( r.toString()+"\n");
        }
        writer.close();
    }
}