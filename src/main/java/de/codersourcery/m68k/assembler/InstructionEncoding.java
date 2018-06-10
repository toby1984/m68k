package de.codersourcery.m68k.assembler;

import org.apache.commons.lang3.Validate;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Instruction encoding.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class InstructionEncoding
{
    private static final int NOT_MAPPED = 0xffff;

    public static final int MAX_BITNO = 31;

    public static final int PATTERN_MULTIPLE_OF = 16; // pattern length must be 16,32,48,... bits

    private final String[] patterns;
    private final Map<Field,List<IBitMapping>>[] bitMappings;

    public static enum Field
    {
        SRC_REGISTER,
        SRC_MODE,
        DST_REGISTER,
        DST_MODE,
        SIZE,
        /**
         * Dummy field that indicates
         * the {@link IBitMapping} requires no
         * input value at all.
         */
        NONE;


        public static Field getField(char c)
        {
            switch(c) {
                case 's': return SRC_REGISTER;
                case 'm': return SRC_MODE;
                case 'D': return DST_REGISTER;
                case 'M': return DST_MODE;
                case 'S': return SIZE;
                default:
                    return null;
            }
        }
    }


    public interface IBitMapping
    {
        public Field getField();

        /**
         * Apply bit mapping by copying bits from the input value into the output value.
         *
         * @param inputValue value to read bits from that should be copied into the output value
         * @param outputValue target value to update with bits from <code>value</code>.
         * @return Updated outputValue
         */
        public int apply(int inputValue,int outputValue);
    }

    private static boolean isBitNoOutOfRange(int bitNo) {
        return bitNo < 0 || bitNo > MAX_BITNO;
    }

    /**
     * Bit mapping that just sets a fixed value.
     */
    public static final class FixedBitValue implements IBitMapping
    {
        public final Field field;
        public final int clearMask;
        public final int setMask;

        public FixedBitValue(Field field,int setMask,int clearMask)
        {
            Validate.notNull(field, "field must not be null");
            this.field = field;
            this.clearMask = clearMask;
            this.setMask = setMask;
        }

        @Override
        public String toString()
        {
            return "FixedBitValue[ clear="+Integer.toBinaryString(clearMask)+",set="+Integer.toBinaryString(setMask)+"]";
        }

        @Override
        public Field getField()
        {
            return field;
        }

        @Override
        public int apply(int inputValue, int targetValue)
        {
            return ((targetValue & clearMask) | setMask );
        }
    }

    public static final class BitRangeMapping implements IBitMapping
    {
        private final Field field;
        private int srcMask;
        private int dstMask;
        private int shift;

        @Override
        public String toString()
        {
            return "BitRangeMapping{" +
                    "field=" + field +
                    ", srcMask=" + Integer.toBinaryString(srcMask) +
                    ", dstMask=" + Integer.toBinaryString(dstMask)+
                    ", shift=" + shift +
                    '}';
        }

        /**
         * Bit mapping that copies <code>bitCount</code> bits from the src value
         * starting at bit <code>srcBitNo</code> to bits <code>dstBitNo</code> up to
         * <code>dstBitNo+bitCount</code> in the destination value.
         * @param srcBitNo
         * @param dstBitNo
         * @param bitCount
         */
        public BitRangeMapping(Field field,int srcBitNo, int dstBitNo, int bitCount)
        {
            Validate.notNull(field, "field must not be null");
            this.field = field;
            if ( bitCount < 1 || bitCount > 32 ) {
                throw new IllegalArgumentException("Bitcount must be 1 <= x <= 32 but was "+bitCount);
            }
            if ( isBitNoOutOfRange(srcBitNo) ) {
                throw new IllegalArgumentException("srcBitNo must be 0 <= x <= 31 but was "+srcBitNo);
            }
            if ( isBitNoOutOfRange(srcBitNo+bitCount) ) {
                throw new IllegalArgumentException("srcBitNo+bitCount must be 0 <= x <= 31 but was "+(srcBitNo+bitCount));
            }
            if ( isBitNoOutOfRange( dstBitNo ) ) {
                throw new IllegalArgumentException("dstBitNo must be 0 <= x <= 31 but was "+dstBitNo);
            }
            if ( isBitNoOutOfRange(dstBitNo+bitCount) ) {
                throw new IllegalArgumentException("dstBitNo+bitCount must be 0 <= x <= 31 but was "+(dstBitNo+bitCount));
            }

            for ( int i = srcBitNo,len=bitCount ; len > 0 ; len--,i++ ) {
                srcMask |= (1<<i);
            }
            for ( int i = dstBitNo,len=bitCount ; len > 0 ; len--,i++ ) {
                dstMask |= (1<<i);
            }
            this.shift = dstBitNo - srcBitNo;
        }

        @Override
        public Field getField()
        {
            return field;
        }

        @Override
        public int apply(int inputValue, int targetValue)
        {
            int value = inputValue & srcMask;
            if ( shift > 0 ) {
                value = value << shift;
            } else {
                value = value >>> -shift;
            }
            return (targetValue & ~dstMask) | value;
        }
    }

    public static final class IndividualBitMapping implements IBitMapping
    {
        private final Field field;
        /**
         * Array index corresponds to bit number in source value.
         * Each array element holds a bitmask with 1 bits where the src value should go.
         *
         * Applying the bit mapping does the following:
         *
         * boolean isBitSet = (srcValue &  1<<arrayIndex) != 0;
         * if ( isBitSet ) {
         *   int valueToSet = 0xffffffff | destMasks[arrayIndex];
         *   srcValue |= valueToSet;
         * }
         */
        private final int[] destMasks = new int[32];
        private int clearMask = 0xffffffff;

        public IndividualBitMapping(Field field)
        {
            Validate.notNull(field, "field must not be null");
            this.field = field;
            Arrays.fill(destMasks, NOT_MAPPED);
        }

        @Override
        public Field getField()
        {
            return field;
        }

        public void add(int srcBitNo, int dstBitNo)
        {
            if ( srcBitNo <0 || srcBitNo > 31 ) {
                throw new IllegalArgumentException("srcBitNo must be 0 <= x <= 31 but was "+srcBitNo);
            }
            if ( dstBitNo <0 || dstBitNo > 31 ) {
                throw new IllegalArgumentException("dstBitNo must be 0 <= x <= 31 but was "+dstBitNo);
            }

            if ( destMasks[srcBitNo] != NOT_MAPPED) {
                throw new IllegalArgumentException("Duplicate mapping for bit "+srcBitNo);
            }

            destMasks[srcBitNo] = 1<<dstBitNo;
            clearMask &= ~(1<<dstBitNo);
        }

        /**
         * Apply bit mapping.
         *
         * @param inputValue value to read bits from
         * @param targetValue target value to update with bits from <code>value</code>.
         * @return Updated targetValue
         */
        public int apply(int inputValue,int targetValue)
        {
            int result = targetValue & clearMask;
            for ( int i = 31 ; i >= 0 ; i-- )
            {
                final int mask = destMasks[i];
                if ( mask != NOT_MAPPED )
                {
                    final boolean isBitSet = (inputValue & 1 << i) != 0;
                    if (isBitSet)
                    {
                        result |= mask;
                    }
                }
            }
            return result;
        }
    }

    private static final class IntRange implements Comparable<IntRange>
    {
        public final Field field;

        /** Start index (inclusive) */
        public int start;
        public int end;

        public IntRange(Field field,int start,int end)
        {
            Validate.notNull(field, "field must not be null");
            if ( end <= start ) {
                throw new IllegalArgumentException("End ("+end+") needs to be greater than start ("+start+")");
            }
            this.start = start;
            this.end = end;
            this.field = field;
        }

        @Override
        public String toString()
        {
            return "IntRange{" +
                    "field=" + field +
                    ", start=" + start +
                    ", end=" + end +
                    '}';
        }

        public boolean contains(int i) {
            return start <= i && i < end;
        }

        public int compareTo(IntRange other) {
            return this.start - other.start;
        }

        public int length()
        {
            return end-start;
        }
    }

    private InstructionEncoding(String pattern1,String...additional)
    {
        Validate.notBlank( pattern1, "pattern1 must not be null or blank");
        final int len = 1 + ( additional != null ? additional.length : 0 );
        this.bitMappings = new Map[len];
        this.patterns = new String[len];
        this.patterns[0] = pattern1;
        this.bitMappings[0] = getMappings(pattern1);
        if ( additional != null )
        {
            for (int i = 0; i < additional.length; i++)
            {
                Validate.notBlank( additional[i], "additional patterns must not be null or blank");
                this.patterns[i+1] = additional[i];
                this.bitMappings[i+1] = getMappings(additional[i]);
            }
        }
    }

    /**
     * Create an instruction encoding.
     *
     * Bit patterns need to be a multiple of {@link #PATTERN_MULTIPLE_OF} bits and must not
     * exceed {@link #MAX_BITNO MAX_BITNO+1} bits.
     *
     * Valid characters are '0','1' for zero/one bits or any of the {@link Field} IDs. Patterns are considered
     * to be big-endian (character at index 0 refers to MSB).
     *
     * @param pattern1 bit mapping pattern
     * @param additional additional bit mapping patterns
     * @return
     */
    public static InstructionEncoding of(String pattern1,String...additional) {
        return new InstructionEncoding(pattern1,additional);
    }

    /**
     * Returns all bit pattern of this instruction encoding.
     * @return
     * @see #getBitMappings()
     */
    public String[] getPatterns()
    {
        return this.patterns;
    }

    /**
     * Returns the bit mappings for each {@link #getPatterns() bit pattern} of this instruction encoding.
     * @return
     */
    public Map<Field,List<IBitMapping>>[] getBitMappings()
    {
        return bitMappings;
    }

    private Map<Field,List<IBitMapping>> getMappings(String pattern)
    {
        Validate.notBlank( pattern, "pattern must not be null or blank");
        if ( (pattern.length() % PATTERN_MULTIPLE_OF) != 0) {
            throw new IllegalArgumentException("Pattern length needs to be a multiple of "+PATTERN_MULTIPLE_OF+" bits but was "+pattern.length());
        }
        if ( pattern.length() > (MAX_BITNO+1) ) {
            throw new IllegalArgumentException("Pattern length must not exceed "+(MAX_BITNO+1)+" bits but was "+pattern.length());
        }
        int clearMask = 0xffffffff;
        int setMask = 0;
        final Map<Field,List<IntRange>> bitRanges = new HashMap<>();
        for ( int i = 0, len = pattern.length(),bitNo=len-1 ; bitNo >= 0 ; i++,bitNo--)
        {
            final char c = pattern.charAt(i);
            if ( c == '0' ) {
                clearMask &= ~(1<<bitNo);
            } else if ( c == '1' ) {
                clearMask &= ~(1<<bitNo);
                setMask |= (1<<bitNo);
            }
            else
            {
                final Field field = Field.getField(c );
                if ( field == null ) {
                    throw new IllegalArgumentException("Unknown field '"+c+"' at position "+i+" in pattern >"+pattern+"<");
                }
                List<IntRange> list = bitRanges.get(field);
                if (list == null)
                {
                    list = new ArrayList<>();
                    bitRanges.put(field,list);
                }
                boolean merged = false;
                for ( IntRange r : list )
                {
                    if ( r.contains(bitNo) ) {
                        merged = true;
                        break;
                    } else if ( r.start == bitNo+1) {
                        r.start = bitNo;
                        merged = true;
                        break;
                    }
                }
                if ( ! merged )
                {
                    list.add(new IntRange(field,bitNo,bitNo+1));
                }
            }
        }
        final List<IBitMapping> result = new ArrayList<>();
        if ( clearMask != 0xffffffff)
        {
            result.add( new FixedBitValue(Field.NONE,setMask,clearMask) );
        }
        bitRanges.forEach((field,ranges) ->
        {
            if ( ranges.size() == 1 )
            {
                final IntRange range = ranges.get(0);
                result.add( new BitRangeMapping(range.field,0,range.start,range.length()) );
            } else {
                final IntRange range = ranges.get(0);
                final IndividualBitMapping mapping = new IndividualBitMapping(range.field);

                // sort ascending by bit index
                // as we're going to increment srcBitNo from 0->x in
                // the loop below
                Collections.sort(ranges);

                int srcBitNo = 0;
                for ( IntRange r : ranges )
                {
                    for ( int dstBitNo = r.start ; dstBitNo < r.end ; dstBitNo++)
                    {
                        mapping.add(srcBitNo++,dstBitNo);
                    }
                }
                result.add(mapping);
            }
        });
        return result.stream().collect(Collectors.groupingBy(map -> map.getField() ) );
    }

    public byte[] apply(Function<Field,Integer> source)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < patterns.length; i++)
        {
            var pattern = patterns[i];
            System.out.println("Pattern: "+pattern);
            int outputValue = 0;
            for ( var entry : bitMappings[i].entrySet() ) {
                final Field field = entry.getKey();
                final List<IBitMapping> mappings = entry.getValue();

                final int inputValue = field == Field.NONE ? 0 : source.apply(field );
                for ( var mapping : mappings )
                {
                    System.out.println("Applying "+mapping);
                    outputValue = mapping.apply(inputValue,outputValue);
                }
            }
            switch(pattern.length())
            {
                case 32:
                    out.write((outputValue >> 24) & 0xff );
                case 24:
                    out.write((outputValue >> 16) & 0xff );
                case 16:
                    out.write((outputValue >> 8) & 0xff );
                case 8:
                    out.write( outputValue & 0xff );
                    break;
                default:
                    throw new RuntimeException("Internal error,invalid pattern length "+pattern.length());
            }
        }
        return out.toByteArray();
    }
}