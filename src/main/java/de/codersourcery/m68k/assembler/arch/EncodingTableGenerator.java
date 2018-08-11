package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.Validate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

        public IValueIterator(Field field1,Field...additional)
        {
            Validate.notNull(field1, "field must not be null");
            Stream.concat(Stream.of(new Field[]{field1}), Stream.of(additional)).forEach(fields::add);
            reset();
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

        public AddressingModeIterator(Field field1,Field field2,Set<AddressingMode> modes) {
            super(field1,field2);
            if ( modes.size() < 1 ) {
                throw new IllegalArgumentException("Need at least 1 addressing mode");
            }
            for ( AddressingMode m : modes ) {
                if ( m == null ) {
                    throw new IllegalArgumentException("Mode cannot be NULL");
                }
            }
            this.modes.addAll(modes);
        }

        public AddressingModeIterator(Field field1,Field field2,AddressingMode m1,AddressingMode...additional)
        {
            super(field1,field2);
            this.modes.add(m1);
            if ( additional != null ) {
                this.modes.addAll(Arrays.asList(additional));
            }
        }

        private AddressingMode currentMode() {
            return modes.get(modePtr);
        }

        @Override
        public boolean hasNext()
        {
            if ( ! currentMode().eaRegisterField.isFixedValue() )
            {
                if ( (eaRegisterValue+1)<8 )
                {
                    return true;
                }
            }
            return (modePtr+1) < modes.size();
        }

        @Override
        protected int internalGetValue(Field value)
        {
            if ( value == Field.SRC_MODE ) {
                return currentMode().eaModeField;
            }
            if ( value == Field.SRC_VALUE ) {
                return eaRegisterValue;
            }
            throw new RuntimeException("Unhandled field: "+value);
        }

        @Override
        protected void doIncrement()
        {
            if ( currentMode().hasFixedEaRegisterValue() ) {
                modePtr++;
                resetEaRegisterValue();
                return;
            }
            eaRegisterValue++;
            if ( eaRegisterValue == 8 )
            {
                modePtr++;
                resetEaRegisterValue();;
            }
        }

        private void resetEaRegisterValue() {
            if ( currentMode().hasFixedEaRegisterValue() ) {
                eaRegisterValue = currentMode().eaModeField;
            } else {
                eaRegisterValue = 0;
            }
        }

        @Override
        public void reset()
        {
            modePtr = 0;
            resetEaRegisterValue();
        }
    }

    public void addMappings(InstructionEncoding encoding,Instruction instruction,Map<Integer, Instruction> result)
    {
        final Map<Field, List<InstructionEncoding.IBitMapping>> mappings =
                InstructionEncoding.getMappings(encoding.getPatterns()[0]);// consider only the first 16 bit

        final Map<Field,IValueIterator> iteratorMap = new HashMap<>();

        // Field.NONE is (ab-)used for the static '1' and '0' bits of the instruction pattern
        final List<InstructionEncoding.IBitMapping> initialMappings = mappings.get( Field.NONE );
        if ( initialMappings == null || initialMappings.isEmpty() )
        {
            throw new RuntimeException("Internal error, no initial mappings?");
        }
        mappings.remove(Field.NONE);

        final List<Field> fields = new ArrayList<>(mappings.keySet());

        final List<IValueIterator> vars = instruction.getValueIterators(mappings.keySet());

        int highestIteratorIdx = 0;
        while ( highestIteratorIdx < vars.size() ) {

            int value = 0;
            for (InstructionEncoding.IBitMapping initialMapping : initialMappings)
            {
                value = initialMapping.apply(0,value);
            }
            for ( Field f : fields)
            {
                for ( IValueIterator it : vars )
                {
                    if ( it.handles(f) )
                    {
                        final int fieldValue = it.internalGetValue(f);
                        final List<InstructionEncoding.IBitMapping> mappers = mappings.get(f);

                        for (InstructionEncoding.IBitMapping mapper : mappers)
                        {
                            value = mapper.apply(fieldValue, value);
                        }
                    }
                }
            }

            // System.out.println(Misc.binary16Bit(value)+" => "+instruction.name());
            if ( result.containsKey(value) ) {
                throw new RuntimeException("Internal error,duplicate mapping "+value+" for "+instruction);
            }
            result.put(value,instruction);

            // increment
            while (highestIteratorIdx < vars.size() )
            {
                boolean atLeastOneIncrement = false;
                for (int i = 0; i <= highestIteratorIdx; i++)
                {
                    final IValueIterator it = vars.get(i);
                    if (it.hasNext())
                    {
//                        System.out.println("Incrementing field "+it+" by one");
                        it.next();
                        atLeastOneIncrement = true;
                        break;
                    }
                    it.reset();
                }

                if (atLeastOneIncrement)
                {
                    break;
                }
                highestIteratorIdx++;
                if ( highestIteratorIdx < vars.size() )
                {
                    final IValueIterator inc = vars.get(highestIteratorIdx);
                    if (inc.hasNext())
                    {
                        inc.next();
                    }
                }
                break;
            }
        }
    }

    public static void main(String[] args) throws IOException
    {
        InstructionEncoding encoding = InstructionEncoding.of("00000000_0000vvvv");
        Instruction instruction = Instruction.TRAP;
        final Map<Integer, Instruction> mappings = new HashMap<>();
        new EncodingTableGenerator().addMappings(encoding, instruction,mappings);

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
        for ( int index = 0 ; index < 65536 ; index++)
        {
            final Instruction insn = mappings.get( index );
            if ( insn != null )
            {
                final String insName = insn.name().toUpperCase();
                currentMethodBuffer.append("\n    opcodes[").append(index).append("] = ").append(insName).append("; // ").append(Misc.binary16Bit(index));
                currentMethodSize++;

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
        FileOutputStream out = new FileOutputStream("/home/tgierke/tmp/Test.java");
        out.write(classBuffer.toString().getBytes());
        out.close();
    }
}
