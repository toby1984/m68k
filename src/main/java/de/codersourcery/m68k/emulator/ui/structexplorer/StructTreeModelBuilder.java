package de.codersourcery.m68k.emulator.ui.structexplorer;

import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class StructTreeModelBuilder
{
    public enum StructType
    {
        NODE( "struct Node" ),
        MEM_HDR( "struct MemHeader" ),
        MEM_CHUNK( "struct MemChunk" ),
        LIST( "struct List" ),
        LIBRARY( "struct Library" ),
        INTVEC( "struct IntVec" ),
        SOFTINTLIST( "struct SoftIntList" ),
        EXEC_BASE( "struct ExecBase" ),
        MEM_LIST( "struct MemList" ),
        MEM_ENTRY( "struct MemEntry" ),
        MINLIST( "struct MinList" ),
        MINNODE( "struct MinNode" ),
        TASK("struct Task"),
        RESIDENT("struct Resident");

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

        public StructDesc(StructDesc other) {
            this.type = other.type;
            this.size = other.size;
            other.allFields.stream().map( f -> f.createCopy() ).forEach( allFields::add );
        }

        public StructDesc[] times(int count)
        {
            final StructDesc[] result = new StructDesc[count];
            for (int i = 0; i < count; i++)
            {
                result[i] = createCopy();
            }
            return result;
        }

        public StructDesc createCopy()
        {
            return new StructDesc(this);
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

        public StructDesc fields(String label,StructDesc[] descs)
        {
            for ( StructDesc desc : descs )
            {
                final StructField newField = new StructField( label, FieldType.INLINED_STRUCT, desc.type );
                this.allFields.add( newField );
                size += newField.sizeInBytes();
            }
            return this;
        }

        public StructDesc field(StructField field) {
            allFields.add(field);
            size += field.sizeInBytes();
            return this;
        }

        public StructDesc field(StructField[] field)
        {
            final List<StructField> list = Arrays.asList( field );
            allFields.addAll( list );
            size += list.stream().mapToInt( l -> l.sizeInBytes() ).sum();
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

        public StructField(StructField structField)
        {
            this.subType = structField.subType;
            this.name = structField.name;
            this.type = structField.type;
            this.displayFlags = structField.displayFlags;
        }

        public StructField createCopy() {
            return new StructField(this);
        }

        public StructField[] times(int count) {
            StructField[] result = new StructField[count];
            for (int i = 0; i < count; i++)
            {
                result[i] = withName( name+"_"+i);
            }
            return result;
        }

        public StructField withName(String name) {
            StructField result = new StructField(this);
            result.name = name;
            return result;
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

    private static final BitMask LIB_FLAGS = new BitMask()
            .add("LIBF_SUMMING",1<<0)
            .add("LIBF_CHANGED",1<<1)
            .add("LIBF_SUMUSED",1<<2)
            .add("LIBF_DELEXP",1<<3);

    private static final StructDesc STRUCT_LIBRARY = new StructDesc(StructType.LIBRARY)
            .fields( "lib_Node" , STRUCT_NODE)
            .field( uint8("lib_Flags", LIB_FLAGS) )
            .field( uint8("lib_pad") )
            .field( uint16("lib_NegSize") )            // number of bytes before library
            .field( uint16("lib_PosSize") )          // number of bytes after library
            .field( uint16("lib_Version") )
            .field( uint16("lib_Revision") )
            .field( stringPtr("lib_IdString") )
            .field( uint32("lib_ChkSum") )                // the checksum itself
            .field( uint16("lib_OpenCnt") );            // number of current opens

    /*
struct IntVector {
APTR    iv_Data;
VOID    (*iv_Code)();
struct  Node *iv_Node;
};*/

    private static final StructDesc STRUCT_INTVECTOR = new StructDesc(StructType.INTVEC)
            .field( ptr("iv_Data" ) )
            .field( ptr("iv_Code" ) )
            .field( structPtr("iv_Node", StructType.NODE ) );

    /*
struct SoftIntList {
    struct List sh_List;
    UWORD  sh_Pad;
    };
 */

    private static final StructDesc STRUCT_SOFTINTLIST = new StructDesc(StructType.SOFTINTLIST)
            .fields( "sh_List", STRUCT_LIST )
            .field( uint16("sh_Pad") );

    /*
struct MinNode {
struct MinNode *mln_Succ;
struct MinNode *mln_Pred;
};
     */
    private static final StructDesc STRUCT_MINNODE = new StructDesc(StructType.MINNODE)
            .field( structPtr("mln_Succ", StructType.MINNODE ))
            .field( structPtr("mln_Pred", StructType.MINNODE ));
    /*
    struct MinList {
   struct  MinNode *mlh_Head;
   struct  MinNode *mlh_Tail;
   struct  MinNode *mlh_TailPred;
}; // longword aligned
     */
    private static final StructDesc STRUCT_MINLIST = new StructDesc(StructType.MINLIST)
            .field( structPtr("mlh_Head", StructType.MINNODE) )
            .field( structPtr("mlh_Tail", StructType.MINNODE) )
            .field( structPtr("mlh_TailPred", StructType.MINNODE) );

    private static final BitMask ATTN_FLAGS = new BitMask()
            .add("AFB_68010",1<<0)	// also set for 68020
            .add("AFB_68020",1<<1)	// also set for 68030
            .add("AFB_68030",1<<2)	// also set for 68040
            .add("AFB_68040",1<<3)	// also set for 68060
            .add("AFB_68881",1<<4)	// also set for 68882
            .add("AFB_68882",1<<5)   //
            .add("AFB_FPU40",1<<6)	// Set if 68040 FPU
            .add("AFB_68060",1<<7);

    private static final StructDesc STRUCT_EXECBASE = new StructDesc(StructType.EXEC_BASE)
            .fields( "LibNode", STRUCT_LIBRARY) // struct Library LibNode; // Standard library node
            .field( uint16("SoftVer"))	//  kickstart release number (obs.)
            .field( int16("LowMemChkSum"))		// checksum of 68000 trap vectors
            .field( uint32("ChkBase"))		// system base pointer complement
            .field( ptr("ColdCapture"))	// coldstart soft capture vector
            .field( ptr("CoolCapture"))	//  coolstart soft capture vector
            .field( ptr("WarmCapture"))	//  warmstart soft capture vector
            .field( ptr("SysStkUpper"))	//  system stack base   (upper bound)
            .field( ptr("SysStkLower"))	//  top of system stack (lower bound)
            .field( uint32("MaxLocMem"))	//  top of chip memory
            .field( ptr("DebugEntry"))	//  global debugger entry point
            .field( ptr("DebugData"))	//  global debugger data segment
            .field( ptr("AlertData"))	//  alert data segment
            .field( ptr("MaxExtMem"))	//  top of extended mem, or null if none
            .field( uint16("ChkSum"))	//  for all of the above (minus 2)
            .fields( "IntVects", STRUCT_INTVECTOR.times(16) ) // struct	IntVector IntVects[16];
            .field( structPtr("ThisTask", StructType.TASK) ) //  pointer to current task (readable)
            .field( uint32("IdleCount"))	//  idle counter
            .field( uint32("DispCount"))	//  dispatch counter
            .field( uint16("Quantum"))	//  time slice quantum
            .field( uint16("Elapsed"))	//  current quantum ticks
            .field( uint16("SysFlags"))	//  misc internal system flags
            .field( int8("IDNestCnt"))	//  interrupt disable nesting count
            .field( int8("TDNestCnt"))	//  task disable nesting count
            .field( uint16("AttnFlags", ATTN_FLAGS))	//  special attention flags (readable)
            .field( uint16("AttnResched"))	//  rescheduling attention
            .field( ptr("ResModules"))	//  resident module array pointer
            .field( ptr("TaskTrapCode"))
            .field( ptr("TaskExceptCode"))
            .field( ptr("TaskExitCode"))
            .field( uint32("TaskSigAlloc"))
            .field( uint16("TaskTrapAlloc"))
            .fields( "MemList", STRUCT_LIST)
            .fields( "ResourceList", STRUCT_LIST)
            .fields( "DeviceList", STRUCT_LIST)
            .fields( "IntrList", STRUCT_LIST)
            .fields( "LibList", STRUCT_LIST)
            .fields( "PortList", STRUCT_LIST)
            .fields( "TaskReady", STRUCT_LIST)
            .fields( "TaskWait", STRUCT_LIST)
            .fields( "SoftInts", STRUCT_SOFTINTLIST.times(5) )
            .field( int32("LastAlert").times(4) ) // LONG	LastAlert[4];
            .field( uint8("VBlankFrequency") )	//  (readable)
            .field( uint8("PowerSupplyFrequency") )	//  (readable)
            .fields( "SemaphoreList", STRUCT_LIST)
            .field( ptr("KickMemPtr"))	//  ptr to queue of mem lists
            .field( ptr("KickTagPtr"))	//  ptr to rom tag queue
            .field( ptr("KickCheckSum"))	//  checksum for mem and tags
            .field( uint16("ex_Pad0"))		//  Private internal use
            .field( uint32("ex_LaunchPoint"))		//  Private to Launch/Switch
            .field( ptr("ex_RamLibPrivate"))
            .field( uint32("ex_EClockFrequency"))	//  (readable)
            .field( uint32("ex_CacheControl"))	//  Private to CacheControl calls
            .field( uint32("ex_TaskID"))		//  Next available task ID
            .field( uint32("ex_Reserved1").times(5) )
            .field( ptr("ex_MMULock"))		//  private
            .field( uint32("ex_Reserved2").times(3))
            .fields( "ex_MemHandlers", STRUCT_MINLIST) // struct	MinList	ex_MemHandlers;	//  The handler list
            .field( ptr("ex_MemHandler"));		//  Private! handler pointer

    /*
struct	MemEntry
{
    union {
      ULONG   meu_Reqs;		// the AllocMem requirements
      APTR    meu_Addr;		// the address of this memory region
    } me_Un;
    ULONG   me_Length;		// the length of this memory region
    };
     */
    private static final StructDesc STRUCT_MEMENTRY= new StructDesc(StructType.MEM_ENTRY)
        .field( uint32("meu_Reqs/meu_Addr (union)") )
        .field( uint32("me_Length"));

    /*
 Note: sizeof(struct MemList) includes the size of the first MemEntry!
    struct	MemList {
    struct  Node ml_Node;
    UWORD   ml_NumEntries;	/* number of entries in this struct
    struct  MemEntry ml_ME[1];	/* the first entry
};
     */
    private static final StructDesc STRUCT_MEMLIST = new StructDesc(StructType.MEM_LIST)
        .fields( "ml_Node", STRUCT_NODE)
        .field( uint16("ml_NumEntries") )
        .fields( "ml_ME", STRUCT_MEMENTRY );

    /*
struct Resident {
    field( uint16("rt_MatchWord") )	          //  word to match on (ILLEGAL)
    field( structPtr( "rt_MatchTag", StructType.RESIDENT)) //  pointer to the above
    field( ptr("rt_EndSkip"))		     //  address to continue scan
    field( uint8("rt_Flags"))		         //  various tag flags
    field( uint8("rt_Version"))		     //  release version number
    field( uint8("rt_Type"))		         //  type of module (NT_XXXXXX)
    field( int8("rt_Pri"))		         //  initialization priority
    field( stringPtr("rt_Name"))		         //  pointer to node name
    field( stringPtr("rt_IdString"))	         //  pointer to identification string
    field( ptr("rt_Init"));		         //  pointer to init code
};

#define RTC_MATCHWORD	0x4AFC	//  The 68000 "ILLEGAL" instruction

        #define RTF_AUTOINIT	(1<<7)	//  rt_Init points to data structure
        #define RTF_AFTERDOS	(1<<2)
        #define RTF_SINGLETASK	(1<<1)
        #define RTF_COLDSTART	(1<<0)

        //  Compatibility: (obsolete)
        //  #define RTM_WHEN	   3
        #define RTW_NEVER	0
        #define RTW_COLDSTART	1
     */

    private static final BitMask RESIDENT_FLAGS = new BitMask()
            .add("RTF_AUTOINIT",1<<7)
            .add("RTF_AFTERDOS",1<<2)
            .add("RTF_SINGLETASK",1<<1)
            .add("RTF_COLDSTART",1<<0);

    private static final StructDesc STRUCT_RESIDENT = new StructDesc(StructType.RESIDENT)
            .field( uint16("rt_MatchWord") )	          //  word to match on (ILLEGAL)
            .field( structPtr( "rt_MatchTag", StructType.RESIDENT)) //  pointer to the above
            .field( ptr("rt_EndSkip"))		     //  address to continue scan
            .field( uint8("rt_Flags",RESIDENT_FLAGS))		         //  various tag flags
            .field( uint8("rt_Version"))		     //  release version number
            .field( uint8("rt_Type"))		         //  type of module (NT_XXXXXX)
            .field( int8("rt_Pri"))		         //  initialization priority
            .field( stringPtr("rt_Name"))		         //  pointer to node name
            .field( stringPtr("rt_IdString"))	         //  pointer to identification string
            .field( ptr("rt_Init"));		         //  pointer to init code

    private final Emulator emulator;

    public StructTreeModelBuilder(Emulator emulator)
    {
        this.emulator = emulator;
    }

    public StructTreeNode build(int baseAddress, StructType type,int maxDepth)
    {
        StructTreeNode result = new StructTreeNode(baseAddress, "");
        int ptr = baseAddress;
        for ( int i = 0 ; i < 1 ; i++ )
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
                result.add( createTreeModel( "[ "+Misc.hex(adr)+" - "+Misc.hex(offset)+" ] " + field.name+" ",adr,(StructType) field.subType,depth,maxDepth ) );
            }
            else
            {
                result.add( valueOf( field, adr, depth, maxDepth, offset ) );
            }
            offset += field.sizeInBytes();
        }
        return result;
    }

    private StructTreeNode valueOf(StructField field,int adr,int depth,int maxDepth,int offset)
    {
        final String sAddr = "[ "+Misc.hex(adr)+" - "+Misc.hex(offset)+" ] ";
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
                return new StructTreeNode( adr, sAddr+field.name + " - " + translate( field, value ) );
            case INT16:
            case UINT16:
                if ( canReadWord( adr ) )
                {
                    value = readWord( adr );
                    if ( field.type == FieldType.UINT16 )
                    {
                        value &= 0xffff;
                    }
                    return new StructTreeNode( adr, sAddr+field.name + " - " + translate( field, value ) );
                }
                return new StructTreeNode( adr, sAddr+field.name + " - <bad alignment: " + Misc.hex( adr ) );
            case INT32:
            case UINT32:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    return new StructTreeNode( adr, sAddr+field.name + " - " + translate( field, (int) value ) );
                }
                return new StructTreeNode( adr, sAddr+field.name + " - <bad alignment: " + Misc.hex( adr ) );
            case CHAR_PTR:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    if ( value != 0 )
                    {
                        return new StructTreeNode( adr, sAddr+field.name + " - '" + parseString( value )+"'" );
                    }
                    return new StructTreeNode( adr, sAddr+field.name + " - <NULL>" );
                }
                return new StructTreeNode( adr, sAddr+field.name + "- bad alignment: " + Misc.hex( adr ) );
            case PTR:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    if ( value != 0 )
                    {
                        return new StructTreeNode( adr, sAddr+field.name + " - " + Misc.hex( value ) );
                    }
                    return new StructTreeNode( adr, sAddr+field.name + " - <NULL>");
                }
                return new StructTreeNode( adr, sAddr+field.name + " - bad alignment: " + Misc.hex( adr ) );
            case STRUCT_PTR:
                if ( canReadLong( adr ) )
                {
                    value = readLong( adr );
                    if ( value != 0 )
                    {
                        if ( depth + 1 < maxDepth )
                        {
                            return createTreeModel( sAddr+field.name + " - ", value, (StructType) field.subType, depth + 1, maxDepth );
                        }
                        return new StructTreeNode( adr, sAddr+field.name + " - " + Misc.hex( value ) );
                    }
                    return new StructTreeNode( adr, sAddr+field.name + " - <NULL>");
                }
                return new StructTreeNode( adr, sAddr+field.name + " - <bad alignment: " + Misc.hex( adr ) + " >" );
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
            case INTVEC:    return STRUCT_INTVECTOR;
            case SOFTINTLIST: return STRUCT_SOFTINTLIST;
            case EXEC_BASE: return STRUCT_EXECBASE;
            case MEM_LIST:  return STRUCT_MEMLIST;
            case MEM_ENTRY: return STRUCT_MEMENTRY;
            case MINLIST:   return STRUCT_MINLIST;
            case MINNODE:   return STRUCT_MINNODE;
            case TASK:      return STRUCT_TASK;
            case LIBRARY:   return STRUCT_LIBRARY;
            case RESIDENT:  return STRUCT_RESIDENT;
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

    private static StructField uint16(String name,LookupTable table) {
        return new StructField(name,FieldType.UINT16,table);
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