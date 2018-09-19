package de.codersourcery.m68k.emulator.ui.structexplorer;

import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;
import de.codersourcery.m68k.utils.Misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StructTreeModelBuilder
{
    public enum StructType
    {
        NODE( "struct Node" ),
        MEM_HDR( "struct MemHeader" ),
        MEM_CHUNK( "struct MemChunk" ),
        LIST( "struct List" );

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
        STRUCT_PTR( 4 );

        public final int sizeInBytes;

        FieldType(int sizeInBytes)
        {
            this.sizeInBytes = sizeInBytes;
        }
    }

    private static final class StructDesc
    {
        public final StructType type;
        public final List<StructField> fields = new ArrayList<>();

        public StructDesc(StructType type)
        {
            this.type = type;
        }

        public StructDesc fields(StructDesc desc) {
            this.fields.addAll( desc.fields );
            return this;
        }

        public StructDesc field(StructField field) {
            fields.add(field);
            return this;
        }
    }

    private static final class EnumValues
    {
        private int[] values = new int[0];
        private String[] labels = new String[0];

        public EnumValues add(String label,int value)
        {
            values = Arrays.copyOf(values, values.length+1);
            values[values.length-1] = value;
            labels = Arrays.copyOf(labels,labels.length+1);
            labels[labels.length-1] = label;
            return this;
        }

        public String lookup(int value) {
            for ( int i = 0 , len = values.length ; i < len ; i++ ) {
                if ( values[i] == value ) {
                    return labels[i];
                }
            }
            return null;
        }
    }

    private static final class StructField
    {
        public String name;
        public FieldType type;
        public Object subType;

        public StructField(String name, FieldType type) {
            this(name,type,null);
        }

        public StructField(String name, FieldType type, Object subType)
        {
            this.name = name;
            this.type = type;
            this.subType = subType;
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
            .fields( STRUCT_NODE )
            .field( uint16( "mh_Attributes" ) )
            .field( structPtr( "mh_First", StructType.MEM_CHUNK) )
            .field( ptr( "mh_Lower" ) )
            .field( ptr( "mh_Upper" ) )
            .field( uint32("mh_Free") );

    private final Emulator emulator;

    public StructTreeModelBuilder(Emulator emulator)
    {
        this.emulator = emulator;
    }

    public StructTreeNode build(int baseAddress, StructType type,int maxDepth)
    {
        return createTreeModel( "", baseAddress, type, 0 , maxDepth );
    }

    private StructTreeNode createTreeModel(String prefix,int baseAddress,StructType type,int depth,int maxDepth) {

        final StructDesc desc = getStructDesc( type );
        StructTreeNode result = new StructTreeNode(baseAddress,prefix + desc.type+" @ "+Misc.hex( baseAddress ) );
        int offset = 0;
        for ( StructField field : desc.fields )
        {
            final int adr = baseAddress + offset;
            int value=0;
            switch( field.type )
            {
                case INT8:
                case UINT8:
                    value = readByte( adr );
                    if ( field.type == FieldType.UINT8 ) {
                        value &= 0xff;
                    }
                    String sValue;
                    if ( field.subType instanceof EnumValues ) {
                        String label = ((EnumValues) field.subType).lookup( value );
                        if ( label == null ) {
                            sValue = "??? - "+Integer.toString(value)+" ("+Misc.hex(value)+")";
                        } else {
                            sValue = label+" - "+Integer.toString(value)+" ("+Misc.hex(value)+")";
                        }
                    } else {
                        sValue = Integer.toString(value)+" ("+Misc.hex(value)+")";
                    }
                    result.addChild( new StructTreeNode( adr, field.name+" - "+sValue ) );
                    break;
                case INT16:
                case UINT16:
                    if ( canReadWord( adr ) )
                    {
                        value = readWord( adr );
                        if ( field.type == FieldType.UINT16 ) {
                            value &= 0xffff;
                        }
                        result.addChild( new StructTreeNode( adr, field.name+" - "+Misc.hex(value) ) );
                    } else {
                        result.addChild( new StructTreeNode( adr, field.name+" - <bad alignment: "+Misc.hex(adr) ) );
                    }
                    break;
                case INT32:
                case UINT32:
                    if ( canReadLong( adr ) )
                    {
                        value = readLong( adr );
                        result.addChild( new StructTreeNode( adr, field.name+" - "+Misc.hex(value) ) );
                    } else {
                        result.addChild( new StructTreeNode( adr, field.name+" - <bad alignment: "+Misc.hex(adr) ) );
                    }
                    break;
                case CHAR_PTR:
                    if ( canReadLong( adr ) )
                    {
                        value = readLong( adr );
                        if ( value != 0 )
                        {
                            result.addChild( new StructTreeNode( value, field.name +" - "+parseString(value) ) );
                        } else {
                            result.addChild( new StructTreeNode( 0,field.name+" - <NULL>" ) );
                        }
                    } else {
                        result.addChild( new StructTreeNode( 0,field.name+"- bad alignment: "+Misc.hex(adr) ) );
                    }
                    break;
                case PTR:
                    if ( canReadLong( adr ) )
                    {
                        value = readLong( adr );
                        result.addChild( new StructTreeNode( 0, field.name+" - "+Misc.hex(value) ) );
                    } else {
                        result.addChild( new StructTreeNode( 0,field.name+" - bad alignment: "+Misc.hex(adr) ) );
                    }
                    break;
                case STRUCT_PTR:
                    if ( canReadLong( adr ) )
                    {
                        value = readLong( adr );
                        if ( depth+1 < maxDepth )
                        {
                            result.addChild( createTreeModel( field.name+" - ",value, (StructType) field.subType,depth+1, maxDepth ) );
                        }
                        else
                        {
                            result.addChild( new StructTreeNode( 0, field.name+" - "+Misc.hex( value ) ) );
                        }
                    } else {
                        result.addChild( new StructTreeNode( 0,field.name+" - <bad alignment: "+Misc.hex(adr)+" >" ) );
                    }
                    break;
            }
            offset += field.type.sizeInBytes;
        }
        return result;
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
        return chars.toString();
    }

    private StructDesc getStructDesc(StructType type)
    {
        switch(type)
        {
            case NODE:      return STRUCT_NODE;
            case MEM_HDR:   return STRUCT_MEM_HDR;
            case MEM_CHUNK: return STRUCT_MEM_CHUNK;
            case LIST:      return STRUCT_LIST;
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

    private static StructField uint8(String name,EnumValues enumValues) {
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