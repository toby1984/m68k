package de.codersourcery.m68k.emulator.ui.structexplorer;

import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

public class StructTreeModelBuilder
{
    public enum StructType
    {
        NODE( "struct Node" ),
        MEM_HDR( "struct MemHeader" ),
        MEM_CHUNK( "struct MemChunk" ),
        LIST( "struct List" ),
        LIBRARY( "struct Library" ),
        TASK("struct Task");

        private final String uiLabel;

        StructType(String uiLabel)
        {
            this.uiLabel = uiLabel;
        }

        @Override
        public String toString()
        {
            return uiLabel;
        }
    }

    private enum FieldType
    {
        INT8( 1 ),
        UINT8( 1 ),
        INT16( 2 ),
        UINT16( 2 ),
        INT32( 4 ),
        UINT32( 4 ),
        CHAR_PTR( 4 ),
        PTR( 4 ),
        STRUCT_PTR( 4 ),
        INLINED_STRUCT(0xffff);

        public final int size;

        FieldType(int sizeInBytes)
        {
            this.size = sizeInBytes;
        }
    }

    private static final class StructDesc
    {
        public final StructType type;
        public int size;
        private final List<StructField> allFields = new ArrayList<>();

        public StructDesc(StructType type)
        {
            this.type = type;
        }

        public List<StructField> fields() {
            return allFields;
        }

        public StructDesc fields(String label,StructDesc desc)
        {
            final StructField newField = new StructField( label, FieldType.INLINED_STRUCT, desc.type );
            this.allFields.add( newField );
            size += newField.sizeInBytes();
            return this;
        }

        public StructDesc field(StructField field) {
            allFields.add(field);
            size += field.sizeInBytes();
            return this;
        }
    }

    protected static abstract class LookupTable<T extends LookupTable<T>>
    {
        protected int[] values = new int[0];
        protected String[] labels = new String[0];

        public T add(String label,int value)
        {
            values = Arrays.copyOf(values, values.length+1);
            values[values.length-1] = value;
            labels = Arrays.copyOf(labels,labels.length+1);
            labels[labels.length-1] = label;
            return (T) this;
        }

        public abstract String lookup(int value, BiFunction<Integer,String,String> labelGen, String defaultValue);
    }

    private static final class EnumValues extends LookupTable<EnumValues>
    {
        @Override
        public String lookup(int value, BiFunction<Integer,String,String> labelGen, String defaultValue)
        {
            for ( int i = 0 , len = values.length ; i < len ; i++ ) {
                if ( values[i] == value ) {
                    return labelGen.apply( Integer.valueOf(value) , labels[i] );
                }
            }
            return labelGen.apply( Integer.valueOf(value), defaultValue );
        }
    }

    private static final class BitMask extends LookupTable<BitMask>
    {
        @Override
        public String lookup(int value, BiFunction<Integer,String,String> labelGen, String defaultValue)
        {
            final StringBuilder result = new StringBuilder();
            for ( int i = 0 , len = values.length ; i < len ; i++ )
            {
                if ( (value & values[i]) == values[i] )
                {
                    if ( result.length() > 0 ) {
                        result.append("|");
                    }
                    result.append(labels[i]);
                }
            }
            return labelGen.apply( Integer.valueOf(value), result.length() == 0 ? defaultValue : result.toString() );
        }
    }

    private static final class StructField
    {
        public static final int DISPLAY_BITS = 1<<0;

        public String name;
        public FieldType type;
        public Object subType;
        public int displayFlags;

        public StructField(String name, FieldType type) {
            this(name,type,null);
        }

        public StructField(String name, FieldType type, Object subType)
        {
            this.name = name;
            this.type = type;
            this.subType = subType;
        }

        public boolean isDisplayBits()
        {
            return (displayFlags & DISPLAY_BITS) != 0;
        }

        public StructField showBits()
        {
            displayFlags |= DISPLAY_BITS;
            return this;
        }

        public int sizeInBytes()
        {
            if ( type == FieldType.INLINED_STRUCT )
            {
                return getStructDesc( (StructType) subType ).size;
            }
            return type.size;
        }
    }

    private static final EnumValues NT_TYPES = new EnumValues()
            .add("NT_UNKNOWN",0)
            .add("NT_TASK",1)
            .add("NT_INTERRUPT",2)
            .add("NT_DEVICE",3)
            .add("NT_MSGPORT",4)
            .add("NT_MESSAGE",5)
            .add("NT_FREEMSG",6)
            .add("NT_REPLYMSG",7)
            .add("NT_RESOURCE",8)
            .add("NT_LIBRARY",9)
            .add("NT_MEMORY",10)
            .add("NT_SOFTINT",11)
            .add("NT_FONT",12)
            .add("NT_PROCESS",13)
            .add("NT_SEMAPHORE",14)
            .add("NT_SIGNALSEM",15)
            .add("NT_BOOTNODE",16)
            .add("NT_KICKMEM",17)
            .add("NT_GRAPHICS",18)
            .add("NT_DEATHMESSAGE",19)
            .add("NT_USER",254)
            .add("NT_EXTENDED",255);

    private static final BitMask TASK_FLAGS = new BitMask()
            .add("TF_PROCTIME",1<<0)
            .add("TF_ETASK",1<<3)
            .add("TF_STACKCHK",1<<4)
            .add("TF_EXCEPT",1<<5)
            .add("TF_SWITCH",1<<6)
            .add("TF_LAUNCH",1<<7);

    private static final BitMask TASK_STATES = new BitMask()
            .add("TS_INVALID",1<<0)
            .add("TS_ADDED",1<<1)
            .add("TS_RUN",1<<2)
            .add("TS_READY",1<<3)
            .add("TS_WAIT",1<<4)
            .add("TS_EXCEPT",1<<5)
            .add("TS_REMOVED",1<<6);

    private static final StructDesc STRUCT_NODE = new StructDesc(StructType.NODE)
            .field( structPtr( "ln_Succ", StructType.NODE ) ) // struct Node *ln_Succ;
            .field( structPtr( "ln_Pred", StructType.NODE ) ) // struct Node *ln_Pred;
            .field( uint8("ln_Type", NT_TYPES ) ) // uint8        ln_Type
            .field( int8("ln_Pri") ) // int8         ln_Pri;
            .field( stringPtr("ln_Name") ); // STRPTR       ln_Name;

    // struct List
    private static final StructDesc STRUCT_LIST = new StructDesc(StructType.LIST)
            .field( structPtr( "lh_Head", StructType.NODE ) ) // struct Node *lh_Head;
            .field( structPtr( "lh_Tail", StructType.NODE ) ) // struct Node *lh_Tail;
            .field( structPtr( "lh_TailPred", StructType.NODE ) ) // struct Node *lh_TailPred;
            .field( uint8("lh_Type", NT_TYPES) ) // uint8        lh_Type;
            .field( uint8("lh_Pad") ); // uint8        lh_Pad;

    /*
    struct	MemChunk {
    struct  MemChunk *mc_Next;	// pointer to next chunk
    ULONG   mc_Bytes;		// chunk byte size
};
     */
    private static final StructDesc STRUCT_MEM_CHUNK = new StructDesc(StructType.MEM_CHUNK)
            .field( structPtr("mc_Next",StructType.MEM_CHUNK ) )
            .field( uint32("mc_Bytes"));

    /*
struct MemHeader {
struct Node       mh_Node;
UWORD             mh_Attributes;  // characteristics of this region
struct  MemChunk *mh_First;       // first free region
APTR              mh_Lower;       // lower memory bound
APTR              mh_Upper;       // upper memory bound + 1
ULONG             mh_Free;        //total number of free bytes
 */
    private static final StructDesc STRUCT_MEM_HDR = new StructDesc(StructType.MEM_HDR)
            .fields( "mh_node", STRUCT_NODE )
            .field( uint16( "mh_Attributes" ) )
            .field( structPtr( "mh_First", StructType.MEM_CHUNK) )
            .field( ptr( "mh_Lower" ) )
            .field( ptr( "mh_Upper" ) )
            .field( uint32("mh_Free") );

    /*
struct Task {
    struct  Node tc_Node;
    UBYTE   tc_Flags;
    UBYTE   tc_State;
    BYTE    tc_IDNestCnt;	    // intr disabled nesting
    BYTE    tc_TDNestCnt;	    // task disabled nesting
    ULONG   tc_SigAlloc;	    // sigs allocated
    ULONG   tc_SigWait;	        // sigs we are waiting for
    ULONG   tc_SigRecvd;	    // sigs we have received
    ULONG   tc_SigExcept;	    // sigs we will take excepts for
    UWORD   tc_TrapAlloc;	    // traps allocated
    UWORD   tc_TrapAble;	    // traps enabled
    APTR    tc_ExceptData;	    // points to except data
    APTR    tc_ExceptCode;	    // points to except code
    APTR    tc_TrapData;	    // points to trap data
    APTR    tc_TrapCode;	    // points to trap code
    APTR    tc_SPReg;		    // stack pointer
    APTR    tc_SPLower;	        // stack lower bound
    APTR    tc_SPUpper;	        // stack upper bound + 2
    VOID    (*tc_Switch)();	    // task losing CPU
    VOID    (*tc_Launch)();	    // task getting CPU
    struct  List tc_MemEntry;	//  /* Allocated memory. Freed by RemTask()
    APTR    tc_UserData;	    // For use by the task; no restrictions!
};


     */
    private static final StructDesc STRUCT_TASK = new StructDesc(StructType.TASK)
            .fields( "tc_Node", STRUCT_NODE )
            .field( uint8("tc_Flags", TASK_FLAGS) )
            .field( uint8("tc_State",TASK_STATES) )
            .field( int8("tc_IDNestCnt") )	    // intr disabled nesting
            .field( int8("tc_TDNestCnt") )	    // task disabled nesting
            .field( uint32("tc_SigAlloc").showBits() )	    // sigs allocated
            .field( uint32("tc_SigWait").showBits() )	        // sigs we are waiting for
            .field( uint32("tc_SigRecvd").showBits() )	    // sigs we have received
            .field( uint32("tc_SigExcept").showBits() )	    // sigs we will take excepts for
            .field( uint16("tc_TrapAlloc").showBits() )	    // traps allocated
            .field( uint16("tc_TrapAble").showBits() )	    // traps enabled
            .field( ptr("tc_ExceptData") )	    // points to except data
            .field( ptr("tc_ExceptCode") )	    // points to except code
            .field( ptr("tc_TrapData") )	    // points to trap data
            .field( ptr("tc_TrapCode") )	    // points to trap code
            .field( ptr("tc_SPReg") )		    // stack pointer
            .field( ptr("tc_SPLower") )	        // stack lower bound
            .field( ptr("tc_SPUpper") )	        // stack upper bound + 2
            .field( ptr("(*tc_Switch)()") )	    // task losing CPU
            .field( ptr("(*tc_Launch)()") )	    // task getting CPU
            .fields( "tc_MemEntry", STRUCT_LIST )	//  /* Allocated memory. Freed by RemTask()
            .field( ptr("tc_UserData") );	    // For use by the task; no restrictions!

    /*
struct Library
{
    .fields( "lib_Node" , StructType.NODE )
    .field( uint8("lib_Flags") )
    .field( uint8("lib_pad") )
    .field( uint16("lib_NegSize") )            // number of bytes before library
    .field( uint16("lib_PosSize") )          // number of bytes after library
    .field( uint16("lib_Version") )
    .field( uint16("lib_Revision") )
    .field( stringPtr("lib_IdString") )
    .field( uint32("lib_Sum") )                // the checksum itself
    .field( uint16("lib_OpenCnt") )            // number of current opens
};

// Meaning of the flag bits:
// A task is currently running a checksum on
#define LIBF_SUMMING (1 << 0)  // this library (system maintains this flag)
        #define LIBF_CHANGED (1 << 1)  // One or more entries have been changed in the library
                                  code vectors used by SumLibrary (system maintains
                                  this flag)

        #define LIBF_SUMUSED (1 << 2)  // A checksum fault should cause a system panic
                                  (library flag)
        #define LIBF_DELEXP (1 << 3)   // A user has requested expunge but another user still
                                  has the library open (this is maintained by library)
     */

    private static final StructDesc STRUCT_LIBRARY = new StructDesc(StructType.LIBRARY)
            .fields( "lib_Node" , StructType.NODE )
            .field( uint8("lib_Flags") )
            .field( uint8("lib_pad") )
            .field( uint16("lib_NegSize") )            // number of bytes before library
            .field( uint16("lib_PosSize") )          // number of bytes after library
            .field( uint16("lib_Version") )
            .field( uint16("lib_Revision") )
            .field( stringPtr("lib_IdString") )
            .field( uint32("lib_Sum") )                // the checksum itself
            .field( uint16("lib_OpenCnt") );            // number of current opens

    private final Emulator emulator;

    public StructTreeModelBuilder(Emulator emulator)
    {
        this.emulator = emulator;
    }

    public StructTreeNode build(int baseAddress, StructType type,int maxDepth)
    {
        StructTreeNode result = new StructTreeNode(baseAddress, "");
        int ptr = baseAddress;
        for ( int i = 0 ; i < 4 ; i++ )
        {
            result.add( createTreeModel( "", ptr , type, 0, maxDepth ) );
            ptr += getStructDesc( type ).size;
        }
        result.assignNodeIds();
        return result;
    }

    private StructTreeNode createTreeModel(String prefix,int baseAddress,StructType type,int depth,int maxDepth) {

        final StructDesc desc = getStructDesc( type );
        final StructTreeNode result = new StructTreeNode( baseAddress, prefix + desc.type+" @ "+Misc.hex( baseAddress ) );

        int offset = 0;
        final List<StructField> fields = desc.fields();
        for (int i = 0, fieldsSize = fields.size(); i < fieldsSize; i++)
        {
            final StructField field = fields.get( i );
            final int adr = baseAddress + offset;
            if ( field.type == FieldType.INLINED_STRUCT ) {
                result.add( createTreeModel( field.name,adr,(StructType) field.subType,depth,maxDepth ) );
            }
            else
            {
                result.add( valueOf( field, adr, depth, maxDepth ) );
            }
            offset += field.sizeInBytes();
        }
        return result;
    }

    private StructTreeNode valueOf(StructField field,int adr,int depth,int maxDepth)
    {
        int value = 0;
        switch (field.type)
        {
            case INT8:
            case UINT8:
                value = readByte( adr );
                if ( field.type == FieldType.UINT8 )
                {
                    value &= 0xff;
                }
                return new StructTreeNode( adr, field.name + " - " + translate( field, value ) );
            case INT16:
            case UINT16:
                if ( canReadWord( adr ) )
                {
                    value = readWord( adr );
                    if ( field.type == FieldType.UINT16 )
                    {
                        value &= 0xffff;
                    }
                    return new StructTreeNode( adr, field.name + " - " + translate( field, value ) );
                }
                return new StructTreeNode( adr, field.name + " - <bad alignment: " + Misc.hex( adr ) );
            case INT32:
            case UINT32:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    return new StructTreeNode( adr, field.name + " - " + translate( field, (int) value ) );
                }
                return new StructTreeNode( adr, field.name + " - <bad alignment: " + Misc.hex( adr ) );
            case CHAR_PTR:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    if ( value != 0 )
                    {
                        return new StructTreeNode( adr, field.name + " - '" + parseString( value )+"'" );
                    }
                    return new StructTreeNode( adr, field.name + " - <NULL>" );
                }
                return new StructTreeNode( adr, field.name + "- bad alignment: " + Misc.hex( adr ) );
            case PTR:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    if ( value != 0 )
                    {
                        return new StructTreeNode( adr, field.name + " - " + Misc.hex( value ) );
                    }
                    return new StructTreeNode( adr, field.name + " - <NULL>");
                }
                return new StructTreeNode( adr, field.name + " - bad alignment: " + Misc.hex( adr ) );
            case STRUCT_PTR:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    if ( value != 0 )
                    {
                        if ( depth + 1 < maxDepth )
                        {
                            return createTreeModel( field.name + " - ", value, (StructType) field.subType, depth + 1, maxDepth );
                        }
                        return new StructTreeNode( adr, field.name + " - " + Misc.hex( value ) );
                    }
                    return new StructTreeNode( adr, field.name + " - <NULL>");
                }
                return new StructTreeNode( adr, field.name + " - <bad alignment: " + Misc.hex( adr ) + " >" );
        }
        throw new RuntimeException("Unhandled field type: "+field);
    }

    private String translate(StructField field, int value)
    {
        String sValue;
        if ( field.subType instanceof LookupTable )
        {
            //         public abstract String lookup(int value, BiFunction<Integer,String,String> labelGen, String defaultValue);
            final BiFunction<Integer,String,String> func = (rawValue,text) -> text+ " - " + rawValue + " (" + Misc.hex( rawValue ) + ")";
            sValue = ((LookupTable) field.subType).lookup( value , func , "???" );
        }
        else
        {
            if ( field.isDisplayBits() )
            {
                switch( field.sizeInBytes() ) {
                    case 1:
                    case 2:
                    case 4:
                        sValue = toPrettyBinary(value,field.sizeInBytes()) + " (" + Misc.hex( value ) + ")";
                        break;
                    default:
                        throw new RuntimeException("Don't know how to print "+field.sizeInBytes()+" bytes as bit mask,field: "+field);
                }
            }
            else
            {
                sValue = Integer.toString( value ) + " (" + Misc.hex( value ) + ")";
            }
        }
        return sValue;
    }

    private static String toPrettyBinary(int value,int sizeInBytes)
    {
        int v = value;
        String result;
        switch( sizeInBytes ) {
            case 1:
                v &= 0xff;
                return "%"+StringUtils.leftPad( Integer.toBinaryString( v ) , 8, '0' );
            case 2:
                v &= 0xffff;
                return "%"+
                        StringUtils.leftPad( Integer.toBinaryString( (v>>>8) & 0xff ) , 8, '0' )+"_"+
                        StringUtils.leftPad( Integer.toBinaryString( v & 0xff) , 8, '0' );
            default:
                return "%"+
                        StringUtils.leftPad( Integer.toBinaryString( (v>>>24) & 0xff ) , 8, '0' )+"_"+
                        StringUtils.leftPad( Integer.toBinaryString( (v>>>16) & 0xff ) , 8, '0' )+"_"+
                        StringUtils.leftPad( Integer.toBinaryString( (v>>> 8) & 0xff ) , 8, '0' )+"_"+
                        StringUtils.leftPad( Integer.toBinaryString(  v  & 0xff ) , 8, '0' );
        }
    }

    private String parseString(int adr) {
        int ptr = adr;
        final StringBuilder chars = new StringBuilder();
        do {
            final int value = readByte( ptr++ ) & 0xff;
            if ( value == 0 ) {
                break;
            }
            if ( value < 32 || value >= 127) {
                chars.append( '.' );
            }
            else
            {
                chars.append( (char) value );
            }
        } while ( chars.length() < 25 );
        String result = chars.toString();
        return result;
    }

    private static StructDesc getStructDesc(StructType type)
    {
        switch(type)
        {
            case NODE:      return STRUCT_NODE;
            case MEM_HDR:   return STRUCT_MEM_HDR;
            case MEM_CHUNK: return STRUCT_MEM_CHUNK;
            case LIST:      return STRUCT_LIST;
            case TASK:      return STRUCT_TASK;
            case LIBRARY:   return STRUCT_LIBRARY;
            default:
                throw new RuntimeException("Unhandled struct type "+type);
        }
    }

    private static StructField structPtr(String name,StructType type)
    {
        return new StructField(name,FieldType.STRUCT_PTR,type);
    }

    private static StructField stringPtr(String name)
    {
        return new StructField(name,FieldType.CHAR_PTR);
    }

    private static StructField ptr(String name)
    {
        return new StructField(name,FieldType.PTR);
    }

    private static StructField int32(String name) {
        return new StructField(name,FieldType.INT32);
    }

    private static StructField uint32(String name) {
        return new StructField(name,FieldType.UINT32);
    }

    private static StructField int16(String name) {
        return new StructField(name,FieldType.INT16);
    }

    private static StructField uint16(String name) {
        return new StructField(name,FieldType.UINT16);
    }

    private static StructField int8(String name) {
        return new StructField(name,FieldType.INT8);
    }

    private static StructField uint8(String name) {
        return new StructField(name,FieldType.UINT8);
    }

    private static StructField uint8(String name,LookupTable<?> enumValues) {
        return new StructField(name,FieldType.UINT8,enumValues);
    }

    private boolean canReadWord(int address) {
        try {
            readWord(address);
            return true;
        } catch(MemoryAccessException e) {
            return false;
        }
    }

    private int readLong(int address) {
        return emulator.memory.readLong( address );
    }

    private short readWord(int address) {
        return emulator.memory.readWord( address );
    }

    private byte readByte(int address) {
        return emulator.memory.readByte( address );
    }

    private boolean canReadLong(int address) {
        try {
            readLong(address);
            return true;
        } catch(MemoryAccessException e) {
            return false;
        }
    }
}