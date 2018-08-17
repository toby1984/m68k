package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.Validate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prints Java source code to standard out that resembles a jump table with 65536 entries to
 * handle all possible 16-bit opcodes the 68000 CPU supports.
 *
 * The code assumes there are static fields defined that match the names of the encoded instruction (all upper-case).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class EncodingTableGenerator
{
    /**
     * Iterators used when constructing all possible 16-bit opcodes for a given CPU instruction.
     */
    public static abstract class IValueIterator
    {
        protected final Set<Field> fields = new HashSet<>();

        public IValueIterator(Set<Field> fields)
        {
            Validate.notNull(fields, "fields must not be null");
            if ( fields.isEmpty() ) {
                throw new IllegalArgumentException("Fields cannot be empty");
            }
            this.fields.addAll(fields);
        }

        public IValueIterator(Field field1,Field...additional)
        {
            Validate.notNull(field1, "field must not be null");
            Stream.concat(Stream.of(new Field[]{field1}), Stream.of(additional)).forEach(fields::add);
        }

        /**
         * Returns whether this iterator handles generating values for a certain field.
         *
         * @param field
         * @return
         */
        public boolean handles(Field field) {
            return fields.contains(field);
        }

        public final Set<Field> getFields() {
            return fields;
        }

        @Override
        public String toString()
        {
            return fields.toString();
        }

        /**
         * Returns whether this iterator can produce yet another different value.
         *
         * @return
         * @see #next()
         */
        public abstract boolean hasNext();

        /**
         * Returns this iterator's value for a given field.
         *
         * @param field
         * @return
         * @throws IllegalArgumentException when passing a field for which {@link #handles(Field)} returns <code>false</code>
         */
        public final int getValue(Field field) {
            if ( ! handles(field) ) {
                throw new IllegalArgumentException("Unsupported field: "+field);
            }
            return internalGetValue(field);
        }

        protected abstract int internalGetValue(Field field);

        /**
         * Advance this iterator to the next value.
         * @throws IllegalStateException when called although {@link #hasNext()} produced <code>false</code>.
         */
        public final void next() {
            if ( ! hasNext() ) {
                throw new IllegalStateException("No next value for "+this);
            }
            doIncrement();;
        }

        protected abstract void doIncrement();

        /**
         * Resets this iterator to start over.
         */
        public abstract void reset();

        public final IValueIterator with(IValueIterator other) {
            return new CompoundValueIterator(this,other);
        }
    }

    public static final class CompoundValueIterator extends IValueIterator {

        private final IValueIterator[] iterators;

        private int maxPtr = 0;


        public CompoundValueIterator(IValueIterator it1,IValueIterator it2)
        {
            super( Stream.concat(it1.getFields().stream(),it2.getFields().stream()).collect(Collectors.toSet()));
            iterators = new IValueIterator[]{it1,it2};
        }

        @Override
        public boolean hasNext()
        {
            for ( int i = 0 ; i < iterators.length ; i++)
            {
                if (iterators[i].hasNext())
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected int internalGetValue(Field field)
        {
            for ( IValueIterator it : iterators ) {
                if ( it.handles(field ) ) {
                    return it.getValue(field);
                }
            }
            throw new IllegalArgumentException("No iterator handles field "+field);
        }

        @Override
        protected void doIncrement()
        {
            for ( int i = 0 ; i < iterators.length ; i++)
            {
                if (iterators[i].hasNext())
                {
                    iterators[i].doIncrement();
                    return;
                }
                if ( (i+1) == iterators.length ) {
                    break;
                }
                iterators[i].reset();
            }
            throw new IllegalStateException("Cannot increment any more");
        }

        @Override
        public void reset()
        {
            for ( IValueIterator it : iterators ) {
                it.reset();;
            }
        }
    }

    public static final class RangedValueIterator extends IValueIterator
    {
        private final int startValueInclusive;
        private int endValueExclusive;

        private int currentValue;

        public RangedValueIterator(Field field,int startValueInclusive,int endValueExclusive) {
            super(field);
            if ( startValueInclusive >= endValueExclusive ) {
                throw new IllegalArgumentException("start >= end ??");
            }
            this.startValueInclusive = startValueInclusive;
            this.endValueExclusive = endValueExclusive;
            reset();
        }

        @Override
        public boolean hasNext()
        {
            return (currentValue+1) < endValueExclusive;
        }

        @Override
        protected int internalGetValue(Field field)
        {
            return currentValue;
        }

        @Override
        public void doIncrement()
        {
            currentValue++;
        }

        @Override
        public void reset()
        {
            this.currentValue = startValueInclusive;
        }
    }

    public static class GenericValueIterator extends IValueIterator
    {
        private final List<IntSupplier> values = new ArrayList<>();

        private int ptr = 0;

        public GenericValueIterator(Field field,IntSupplier i1,IntSupplier...additional) {
            super(field);
            Validate.notNull(i1, "i1 must not be null");
            final List<IntSupplier> suppliers = Stream.concat(Arrays.stream(new IntSupplier[]{i1}), Arrays.stream(additional)).collect(Collectors.toList());
            populate(suppliers);
        }

        private void populate(List<IntSupplier> suppliers) {
            Validate.notNull(suppliers, "suppliers must not be null");
            if ( suppliers.stream().anyMatch( x -> x == null ) )
            {
                throw new IllegalArgumentException("Cannot add NULL values");
            }
            if ( suppliers.isEmpty() ) {
                throw new IllegalArgumentException("Need at least one int supplier");
            }
            this.values.addAll(suppliers);
        }

        public GenericValueIterator(Field field,List<IntSupplier> suppliers)
        {
            super(field);
            populate(suppliers);
        }

        @Override
        public boolean hasNext()
        {
            return (ptr+1) < values.size();
        }

        @Override
        protected int internalGetValue(Field field)
        {
            return values.get(ptr).getAsInt();
        }

        @Override
        public void doIncrement()
        {
            ptr++;
        }

        @Override
        public void reset()
        {
            ptr = 0;
        }
    }

    public static final class IntValueIterator extends GenericValueIterator {

        public IntValueIterator(Field field,int value1,int...additional)
        {
            super(field,()->value1,toIntSuppliers(additional));
        }

        private static IntSupplier[] toIntSuppliers(int[] additional)
        {
            if ( additional == null || additional.length == 0 ) {
                return new IntSupplier[0];
            }
            return Arrays.stream(additional).mapToObj( x -> (IntSupplier) () -> x ).collect(Collectors.toList()).toArray(new IntSupplier[0]);
        }
    }

    public static final class AddressingModeIterator extends EncodingTableGenerator.IValueIterator
    {
        private final List<AddressingMode> modes = new ArrayList<>();
        private int modePtr = 0;
        private int eaRegisterValue = 0;

        public AddressingModeIterator(Field field1,Field field2,Set<AddressingMode> modes)
        {
            super(field1,field2);
            this.modes.addAll(modes);
            this.modes.removeIf(x -> x == AddressingMode.IMPLIED ); // internal use only
            removeModesWithSameBitPattern();
            System.out.println("AddressingModeIterator(): eaMode is now "+currentMode());
            reset();
        }

        private void removeModesWithSameBitPattern()
        {
            boolean removed;
outer:
            do
            {
                removed = false;
                for ( int i = 0 ; i < this.modes.size() ; i++ )
                {
                    final AddressingMode m1 = this.modes.get(i);
                    for (Iterator<AddressingMode> it = this.modes.iterator() ; it.hasNext() ; )
                    {
                        final AddressingMode m2 = it.next();
                        if ( m1 != m2 )
                        {
                            if ( m1.eaModeField == m2.eaModeField )
                            {
                                if ( m1.hasFixedEaRegisterValue() && m2.hasFixedEaRegisterValue() )
                                {
                                    if ( m1.eaRegisterField == m2.eaRegisterField ) {
                                        System.out.println("WARN: Ignoring "+m2+" as it has the same bit pattern as "+m1);
                                        removed = true;
                                        it.remove();
                                    }
                                }
                                else if ( ! ( m1.hasFixedEaRegisterValue() | m1.hasFixedEaRegisterValue() ) )
                                {
                                    System.out.println("WARN: Ignoring "+m2+" as it has the same bit pattern as "+m1);
                                    removed = true;
                                    it.remove();
                                }
                            }
                        }
                    }
                }
            } while ( removed );
        }

        private AddressingMode currentMode() {
            return modes.get(modePtr);
        }

        @Override
        public boolean hasNext()
        {
            if ( currentMode().hasFixedEaRegisterValue() ) {
                return (modePtr+1) < modes.size();
            }
            return (eaRegisterValue+1)<8;
        }

        @Override
        protected void doIncrement()
        {
            if ( currentMode().hasFixedEaRegisterValue() ) {
                modePtr++;
                System.out.println("doIncrement(1): eaMode is now "+currentMode());
                resetEaRegisterValue();
                return;
            }
            eaRegisterValue++;
            if ( eaRegisterValue == 8 )
            {
                modePtr++;
                System.out.println("doIncrement(2): eaMode is now "+currentMode());
                resetEaRegisterValue();
            }
            else
            {
                System.out.println("doIncrement(): eaRegister is now " + Misc.binary3Bit(eaRegisterValue));
            }
        }

        @Override
        protected int internalGetValue(Field value)
        {
            if ( value == Field.SRC_MODE || value == Field.DST_MODE) {
                return currentMode().eaModeField;
            }
            if ( value == Field.SRC_BASE_REGISTER || value == Field.DST_BASE_REGISTER ) {
                return eaRegisterValue;
            }
            throw new RuntimeException("Unhandled field: "+value);
        }

        private void resetEaRegisterValue() {
            if ( currentMode().hasFixedEaRegisterValue() ) {
                eaRegisterValue = currentMode().eaRegisterField.value();
            } else {
                eaRegisterValue = 0;
            }
            System.out.println("reset(): eaRegister is now "+Misc.binary3Bit(eaRegisterValue));
        }

        @Override
        public void reset()
        {
            modePtr = 0;
            System.out.println("reset(): eaMode is now "+currentMode());
            resetEaRegisterValue();
        }
    }

    public void addMappings(InstructionEncoding encoding,Instruction instruction,CPUType cpuType,Map<Integer, Instruction> result)
    {
        final Map<Field, List<InstructionEncoding.IBitMapping>> mappings =
                InstructionEncoding.getMappings(encoding.getPatterns()[0]);// consider only the first 16 bit

        // Field.NONE is (ab-)used for the static '1' and '0' bits of the instruction pattern
        final List<InstructionEncoding.IBitMapping> initialMappings = mappings.get( Field.NONE );
        if ( initialMappings == null || initialMappings.isEmpty() )
        {
            throw new RuntimeException("Internal error, no initial mappings?");
        }
        mappings.remove(Field.NONE);

        final List<Field> fields = new ArrayList<>(mappings.keySet());

        final IValueIterator iterator;
        if ( fields.isEmpty() )
        {
            // bit pattern is static/fixed (NOP,RTE, etc.) so now need for a real value iterator
            iterator = new IValueIterator(Field.NONE)
            {
                @Override public boolean hasNext() { return false; }

                @Override
                protected int internalGetValue(Field field)
                {
                    throw new UnsupportedOperationException("Method internalGetValue not implemented");
                }

                @Override
                protected void doIncrement()
                {
                    throw new UnsupportedOperationException("Method doIncrement not implemented");
                }

                @Override
                public void reset()
                {
                    throw new UnsupportedOperationException("Method reset not implemented");
                }
            };
        }
        else
        {
            System.out.println("Getting iterator for "+fields);
            iterator = instruction.getValueIterator(encoding, cpuType);
        }

        int position = 1;
        while(true)
        {
            int value = 0;
            for (InstructionEncoding.IBitMapping initialMapping : initialMappings)
            {
                value = initialMapping.apply(0,value);
            }
            for ( Field f : fields)
            {
                final int fieldValue = iterator.internalGetValue(f);
                final List<InstructionEncoding.IBitMapping> mappers = mappings.get(f);

                for (InstructionEncoding.IBitMapping mapper : mappers)
                {
                    value = mapper.apply(fieldValue, value);
                }
            }

            System.out.println(position+": "+Misc.binary16Bit(value)+" => "+instruction.name());
            position++;
            if ( result.containsKey(value) ) {
                throw new RuntimeException("Internal error,duplicate mapping "+Misc.binary16Bit(value)+" for "+instruction);
            }
            result.put(value,instruction);

            if ( iterator.hasNext() )
            {
                iterator.doIncrement();
            } else {
                break;
            }
        }
    }

    public static void main(String[] args) throws IOException
    {
        final Map<Instruction,List<InstructionEncoding>> allEncodings =
            new HashMap<>();
        allEncodings.put(Instruction.NEGX,Arrays.asList(Instruction.NEGX_ENCODING));
        allEncodings.put(Instruction.CMPM,Arrays.asList(Instruction.CMPM_ENCODING));
        allEncodings.put(Instruction.CMP,Arrays.asList(Instruction.CMP_ENCODING));
        allEncodings.put(Instruction.SUBX,Arrays.asList(Instruction.SUBX_ADDR_REG_ENCODING,Instruction.SUBX_DATA_REG_ENCODING));
        allEncodings.put(Instruction.SUB,Arrays.asList(Instruction.SUB_DST_DATA_ENCODING,Instruction.SUB_DST_EA_ENCODING));
        allEncodings.put(Instruction.ADD,Arrays.asList(Instruction.ADD_DST_DATA_ENCODING,Instruction.ADD_DST_EA_ENCODING));
        allEncodings.put(Instruction.ADDX,Arrays.asList(Instruction.ADDX_ADDRREG_ENCODING,Instruction.ADDX_DATAREG_ENCODING));
        allEncodings.put(Instruction.SUBI,Arrays.asList(Instruction.SUBI_WORD_ENCODING)); // only one encoding needed (differ only in bits 16+)
        allEncodings.put(Instruction.CMPI,Arrays.asList(Instruction.CMPI_WORD_ENCODING)); // only one encoding needed (differ only in bits 16+)

        /*
         ***************************
         */
        final Map<Integer, Instruction> mappings = new HashMap<>();

        for ( var entry : allEncodings.entrySet() )
        {
            final Instruction instruction = entry.getKey();
            for (InstructionEncoding enc : entry.getValue() )
            {
                System.out.println("Processing encoding: " + enc);
                new EncodingTableGenerator().addMappings(enc, instruction, CPUType.M68000, mappings);
            }
        }

        final StringBuilder classBuffer = new StringBuilder();
        classBuffer.append("public static final InstructionImpl[] opcodes = new InstructionImpl[65536];\n");

        int initMethodNumber=0;
        final StringBuilder initMethodBuffer = new StringBuilder();
        initMethodBuffer.append("static {\n");
        initMethodBuffer.append("    java.util.Arrays.fill(opcodes,INVALID_OPCODE);");

        final StringBuilder dataMethodsBuffer = new StringBuilder();

        final StringBuilder currentMethodBuffer = new StringBuilder();

        int maxMethodSize = 5000;
        int currentMethodSize = 0;
        int insnCount = 0;
        for ( int index = 0 ; index < 65536 ; index++)
        {
            final Instruction insn = mappings.get( index );
            if ( insn != null )
            {
                final String insName = insn.name().toUpperCase();
                currentMethodBuffer.append("\n    opcodes[").append(index).append("] = ").append(insName).append("; // ").append(Misc.binary16Bit(index));
                currentMethodSize++;
                insnCount++;

                if ( currentMethodSize >= maxMethodSize )
                {
                    dataMethodsBuffer.append("public static void initMethod"+initMethodNumber+"() {");
                    dataMethodsBuffer.append( currentMethodBuffer );
                    dataMethodsBuffer.append("\n}\n\n");
                    initMethodBuffer.append("\n    initMethod"+initMethodNumber+"();");

                    initMethodNumber++;
                    currentMethodSize=0;
                    currentMethodBuffer.setLength(0);
                }
            }
        }
        if ( currentMethodBuffer.length() > 0 ) {
            dataMethodsBuffer.append("public static void initMethod"+initMethodNumber+"() {");
            dataMethodsBuffer.append( currentMethodBuffer );
            dataMethodsBuffer.append("\n}\n\n");
            initMethodBuffer.append("\n    initMethod"+initMethodNumber+"();");
        }
        initMethodBuffer.append("\n}\n");
        classBuffer.append("\n").append( initMethodBuffer ).append( "\n").append( dataMethodsBuffer );

        System.out.println( classBuffer );

        System.out.println("Instructions: "+insnCount);
        FileOutputStream out = new FileOutputStream("/tmp/Test.java");
        out.write(classBuffer.toString().getBytes());
        out.close();
    }
}
