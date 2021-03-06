package de.codesourcery.m68k.assembler.arch;

import de.codesourcery.m68k.assembler.IntRange;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Instruction encoding bit pattern.
 *
 * This class takes a list of strings that describe the bit-encoding for a given instruction and provides a way of
 * applying this pattern to generate the binary representation of a CPU instruction.
 *
 * @author tobias.gierke@code-sourcery.de
 * @see Field
 */
public class InstructionEncoding
{
    private static final Logger LOG = LogManager.getLogger( InstructionEncoding.class.getName() );

    public static boolean DEBUG = false; // TODO: make this final and 'false' when done debugging

    private static final int NOT_MAPPED = 0xffff;

    public static final int MAX_BITNO = 31;

    public static final int PATTERN_MULTIPLE_OF = 16; // pattern length must be 16,32,48,... bits

    private final String[] patterns;
    private final Map<Field,List<IBitMapping>>[] bitMappings;
    private final int sizeInBytes;

    private final IValueDecorator valueDecorator;

    /*
     * Mask that has '1' bits wherever the first 16 bits
     * (=instruction word) of this encoding have a fixed
     * '0' or '1' value.
     */
    private int instructionWordAndMask;
    /*
     * Bits that had either a fixed '1' or '0' value
     * in the first 16 bits of this encoding. Non-fixed
     * bits will appear as '0'
     */
    private int instructionWordMask;

    private final String name;

    /**
     * Decorator used to change the bit encoding
     * of certain field values.
     * @author tobias.gierke@code-sourcery.de
     */
    public interface IValueDecorator
    {
        int getBits(Field field,int value);
    }

    private InstructionEncoding(String[] newPatterns, Map<Field,List<IBitMapping>>[] newMappings,
                                IValueDecorator valueDecorator,String name)
    {
        this.name = name;
        this.patterns = newPatterns;
        this.bitMappings = newMappings;
        int size = 0;
        for (String pattern : newPatterns)
        {
            size += pattern.length() / 8;
        }
        this.sizeInBytes = size;
        this.valueDecorator = valueDecorator;
        populateInstructionMask(newPatterns[0]);
    }

    public InstructionEncoding decorateWith(IValueDecorator decorator)
    {
        if ( decorator == null ) {
            throw new IllegalArgumentException("Decorator must not be NULL");
        }
        final IValueDecorator newDecorator = (field, value) -> decorator.getBits( field, this.valueDecorator.getBits( field,value ) );
        return new InstructionEncoding(this.patterns, this.bitMappings, newDecorator,this.name );
    }

    public InstructionEncoding withName(String name)
    {
        return new InstructionEncoding(this.patterns, this.bitMappings, this.valueDecorator,name );
    }

    private void populateInstructionMask(String pattern)
    {
        int andMask = 0;
        int mask = 0;
        for (int i = 0, len = pattern.length() ; i < len ; i++)
        {
            final char c = pattern.charAt(i);
            if (c != '_')
            {
                andMask <<= 1;
                mask <<= 1;
                if (c == '1' || c == '0')
                {
                    andMask |= 1;
                    if ( c == '1' ) {
                        mask |= 1;
                    }
                }
            }
        }
        this.instructionWordAndMask = andMask;
        this.instructionWordMask = mask;
    }

    public int getInstructionWordAndMask()
    {
        return instructionWordAndMask;
    }

    public int getInstructionWordMask()
    {
        return instructionWordMask;
    }

    public String toString()
    {
        StringBuffer buffer = new StringBuffer();
        for ( int i = 0,len = patterns.length;i<len;i++) {
            buffer.append('"').append(patterns[i]).append("'");
            if ( (i+1) < len ) {
                buffer.append(" , ");
            }
        }
        return name+" { "+buffer.toString()+" }";
    }

    public int getSizeInBytes()
    {
        return sizeInBytes;
    }

    public static abstract class IBitMapping
    {
        private final Field field;

        public IBitMapping(Field field) {
            Validate.notNull(field, "field must not be null");
            this.field = field;
        }

        /**
         * Returns the field this mapping applies to.
         * @return
         */
        public final Field getField() {
            return field;
        }

        /**
         * Apply bit mapping by copying bits from the input value into the output value.
         *
         * @param inputValue value to read bits from that should be copied into the output value
         * @param outputValue target value to update with bits from <code>value</code>.
         * @return Updated outputValue
         */
        public abstract int apply(int inputValue,int outputValue);
    }

    /**
     * Bit mapping that just sets a fixed value.
     */
    public static final class FixedBitValue extends IBitMapping
    {
        public final int clearMask;
        public final int setMask;

        public FixedBitValue(Field field,int setMask,int clearMask)
        {
            super(field);
            this.clearMask = clearMask;
            this.setMask = setMask;
        }

        @Override
        public String toString()
        {
            return "FixedBitValue[ clear="+Integer.toBinaryString(clearMask)+",set="+Integer.toBinaryString(setMask)+"]";
        }

        @Override
        public int apply(int inputValue, int targetValue)
        {
            return ((targetValue & clearMask) | setMask );
        }
    }

    public static final class BitRangeMapping extends IBitMapping
    {
        private int srcMask;
        private int dstMask;
        private int shift;

        @Override
        public String toString()
        {
            return "BitRangeMapping{" +
                    "field=" + getField() +
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
            super(field);
            if ( bitCount < 1 || bitCount > 32 ) {
                throw new IllegalArgumentException("Bitcount must be 1 <= x <= 32 but was "+bitCount);
            }
            if (srcBitNo < 0 || srcBitNo > MAX_BITNO) {
                throw new IllegalArgumentException("srcBitNo must be 0 <= x <= 31 but was "+srcBitNo);
            }
            if (srcBitNo + bitCount < 0 || srcBitNo + bitCount > MAX_BITNO + 1) {
                throw new IllegalArgumentException("srcBitNo+bitCount must be 0 <= x <= 32 but was "+(srcBitNo+bitCount));
            }
            if (dstBitNo < 0 || dstBitNo > MAX_BITNO ) {
                throw new IllegalArgumentException("dstBitNo must be 0 <= x <= 31 but was "+dstBitNo);
            }
            if (dstBitNo + bitCount < 0 || dstBitNo + bitCount > MAX_BITNO + 1) {
                throw new IllegalArgumentException("dstBitNo+bitCount must be 0 <= x <= 32 but was "+(dstBitNo+bitCount));
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

    public static final class IndividualBitMapping extends IBitMapping
    {
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
            super(field);
            Arrays.fill(destMasks, NOT_MAPPED);
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

    private InstructionEncoding(String pattern1,String...additional)
    {
        pattern1 = stripUnderscores(pattern1);
        Validate.notBlank( pattern1, "pattern1 must not be null or blank");
        final int len = 1 + ( additional != null ? additional.length : 0 );
        this.name = null;
        this.bitMappings = new Map[len];
        this.patterns = new String[len];
        this.patterns[0] = pattern1;
        int sizeInBytes = pattern1.length()/8;
        this.bitMappings[0] = getMappings(pattern1);
        if ( additional != null )
        {
            for (int i = 0; i < additional.length; i++)
            {
                final String stripped = stripUnderscores( additional[i] );
                sizeInBytes += stripped.length()/8;
                Validate.notBlank(stripped, "additional patterns must not be null or blank");
                this.patterns[i+1] = stripped;
                this.bitMappings[i+1] = getMappings(stripped);
            }
        }
        this.valueDecorator = (field, value) -> value;
        this.sizeInBytes = sizeInBytes;
        populateInstructionMask(pattern1);
    }

    private static String stripUnderscores(String s) {
        return s.replace("_", "");
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

    /**
     * Returns all bit-field mappings for a given pattern.
     *
     * @param pattern
     * @return
     */
    public static Map<Field,List<IBitMapping>> getMappings(String pattern)
    {
        Validate.notBlank( pattern, "pattern must not be null or blank");
        if ( (pattern.length() % PATTERN_MULTIPLE_OF) != 0) {
            throw new IllegalArgumentException("Pattern length needs to be a multiple of "+PATTERN_MULTIPLE_OF+" bits " +
                    "but was "+pattern.length());
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
            switch(c) {
                case '0':
                    clearMask &= ~(1<<bitNo);
                    break;
                case '1':
                    clearMask &= ~(1<<bitNo);
                    setMask |= (1<<bitNo);
                    break;
                default:
                {
                    final Field field = Field.getField(c);
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
        }
        final List<IBitMapping> result = new ArrayList<>();
        if ( clearMask != 0xffffffff)
        {
            result.add( new FixedBitValue(Field.NONE,setMask,clearMask) );
        }
        for (Map.Entry<Field, List<IntRange>> entry : bitRanges.entrySet() ) {
            final List<IntRange> ranges = entry.getValue();
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
        }
        final Map<Field, List<IBitMapping>> resultMap = new HashMap<>();
        for (IBitMapping mapping : result)
        {
            List<IBitMapping> list = resultMap.get(mapping.getField());
            if ( list == null ) {
                list = new ArrayList<>();
                resultMap.put(mapping.getField(),list);
            }
            list.add(mapping);
        }
        return resultMap;
    }

    /**
     * Applies this instruction encoding using values from an input source.
     *
     * @param source
     * @return binary representation of this encoding using values from the input source
     */
    public byte[] apply(Function<Field,Integer> source)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < patterns.length; i++)
        {
            var pattern = patterns[i];
            if ( DEBUG )
            {
                LOG.info( "Pattern: " + pattern );
            }
            int outputValue = 0;
            for ( var entry : bitMappings[i].entrySet() ) {
                final Field field = entry.getKey();
                final List<IBitMapping> mappings = entry.getValue();

                final int inputValue;
                if ( field == Field.NONE )
                {
                    inputValue = 0;
                }
                else
                {
                    inputValue = valueDecorator.getBits( field, source.apply( field ));
                }
                for ( var mapping : mappings )
                {
                    if ( DEBUG )
                    {
                        LOG.info( "=== Applying " + mapping + " with " + StringUtils.leftPad(Integer.toBinaryString(inputValue), 16, '0') + " ===" );
                    }
                    final int oldValue = outputValue;
                    outputValue = mapping.apply(inputValue,outputValue);
                    if ( DEBUG && oldValue != outputValue )
                    {
                        LOG.info( "BEFORE: "+pattern );
                        LOG.info( "BEFORE: "+ StringUtils.leftPad(Integer.toBinaryString(oldValue),16,'0') );
                        LOG.info( "AFTER : " + pattern );
                        LOG.info( "AFTER : " + StringUtils.leftPad(Integer.toBinaryString(outputValue), 16, '0') );
                    }
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

    /**
     * Returns a new instruction encoding from this one with one more pattern appended.
     *
     * @param pattern pattern to append
     * @return new instruction encoding.
     */
    public InstructionEncoding append(String pattern)
    {
        pattern = stripUnderscores(pattern);
        final Map<Field,List<IBitMapping>>[] newMappings = new HashMap[ bitMappings.length +1 ];
        System.arraycopy(bitMappings,0,newMappings,0,bitMappings.length);
        newMappings[newMappings.length-1] = getMappings(pattern);

        final String[] newPatterns = new String[ patterns.length+1 ];
        System.arraycopy(patterns,0,newPatterns,0,patterns.length);
        newPatterns[newPatterns.length-1] = pattern;

        return new InstructionEncoding(newPatterns,newMappings,this.valueDecorator,this.name);
    }

    public InstructionEncoding append(String pattern1,String... morePatterns)
    {
        final int oldLen = patterns.length;

        final int additionalPatternCount = morePatterns == null ? 0 : morePatterns.length;
        final int newParamCount = 1 + additionalPatternCount;
        final int newLen = oldLen + newParamCount;

        final Map<Field,List<IBitMapping>>[] newMappings = new HashMap[ newLen ];
        final String[] newPatterns = new String[ newLen ];

        System.arraycopy(bitMappings,0,newMappings,0,oldLen);
        System.arraycopy(patterns,0,newPatterns,0,oldLen);

        pattern1 = stripUnderscores(pattern1);
        newPatterns[oldLen] = pattern1;
        newMappings[oldLen] = getMappings(pattern1);
        for ( int i = 0,newIdx = oldLen+1 ; i < additionalPatternCount ; i++,newIdx++ ) {
            final String pattern = stripUnderscores(morePatterns[i] );
            newMappings[newIdx] = getMappings(pattern);
            newPatterns[newIdx] = pattern;
        }
        return new InstructionEncoding(newPatterns,newMappings,this.valueDecorator,this.name);
    }

    /**
     * Returns a new instruction encoding created from this encoding and additional patterns.
     *
     * @param morePatterns
     * @return new instruction encoding, returns this instance if <code>morePatterns</code> was <code>null</code> or empty
     */
    public InstructionEncoding append(String[] morePatterns)
    {
        if ( morePatterns == null || morePatterns.length == 0 ) {
            return this;
        }

        final int oldLen = patterns.length;
        final int newLen = oldLen + morePatterns.length;

        final Map<Field,List<IBitMapping>>[] newMappings = new HashMap[ newLen ];
        final String[] newPatterns = new String[ newLen ];

        System.arraycopy(bitMappings,0,newMappings,0,oldLen);
        System.arraycopy(patterns,0,newPatterns,0,oldLen);

        for ( int i = 0,newIdx = oldLen,max = morePatterns.length ; i < max ; i++,newIdx++ )
        {
            final String pattern = stripUnderscores(morePatterns[i]);
            newMappings[newIdx] = getMappings(pattern);
            newPatterns[newIdx] = pattern;
        }
        return new InstructionEncoding(newPatterns,newMappings,this.valueDecorator,this.name);
    }

}