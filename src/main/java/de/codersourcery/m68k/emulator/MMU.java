package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.emulator.exceptions.MemoryAccessException;
import de.codersourcery.m68k.emulator.exceptions.PageNotMappedException;
import de.codersourcery.m68k.utils.Misc;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Arrays;

public class MMU
{
    private static final boolean DEBUG = true;

    private static final int PAGE_SIZE_LEFT_SHIFT = 12;
    public static final int PAGE_SIZE = 0x1000;
    private static final int PAGE_OFFSET_MASK = 0xfff;

    private final TIntObjectHashMap<MemoryPage> pageMap = new TIntObjectHashMap<>();

    private int faultCount;
    private final PageFaultHandler faultHandler;

    public static class PageFaultHandler
    {
        public MemoryPage getPage(int pageNo) throws MemoryAccessException
        {
            return new RAMPage( MMU.PAGE_SIZE );
        }
    }

    public MMU(PageFaultHandler faultHandler) {
        this.faultHandler = faultHandler;
    }

    public int getPageStartAddress(int pageNo) {
        return pageNo * PAGE_SIZE;
    }

    public void setWriteProtection(int startaddress,int count,boolean onOff)
    {
        if ( onOff )
        {
            setPageFlags(startaddress, count, MemoryPage.FLAG_WRITE_PROTECTED );
        } else {
            clearPageFlags(startaddress, count, MemoryPage.FLAG_WRITE_PROTECTED );
        }
    }

    public MemoryPage getPage(int pageNo)
    {
        MemoryPage page = pageMap.get(pageNo);
        if ( page == null )
        {
            faultCount++;
            page = faultHandler.getPage( pageNo );
            if ( page == null )
            {
                final int adr = getPageStartAddress( pageNo );
                throw new PageNotMappedException("Page at "+
                        Misc.hex(adr)+" is not mapped?",
                        MemoryAccessException.Operation.UNSPECIFIED,adr,MemoryAccessException.ViolationType.PAGE_FAULT);
            }
            if ( DEBUG ) {
                System.out.println("Fault #"+faultCount+": Paged-in memory at "+Misc.hex( getPageStartAddress( pageNo )));
            }
            pageMap.put(pageNo,page);
        }
        return page;
    }

    public void setPageFlags(int startaddress,int count,byte flags)
    {
        int firstPage = getPageNo(startaddress );
        int lastPage = getPageNo(startaddress+count);
        for ( int pageNo = firstPage ; pageNo <= lastPage ; pageNo++) {
            getPage(pageNo).flags |= flags;
        }
    }

    public void clearPageFlags(int startaddress,int count,byte flags)
    {
        final byte negated = (byte) ~flags;
        int firstPage = getPageNo(startaddress );
        int lastPage = getPageNo(startaddress+count);
        for ( int pageNo = firstPage ; pageNo <= lastPage ; pageNo++) {
            getPage(pageNo).flags &= negated;
        }
    }

    public int getPageNo(int address)
    {
        return (address >>> PAGE_SIZE_LEFT_SHIFT);
    }

    public int getOffsetInPage(int address) {
        return (address & PAGE_OFFSET_MASK);
    }

    public void dumpPages() {

        final int[] keys = pageMap.keys();
        Arrays.sort(keys);
        final byte[] pageData = new byte[ MMU.PAGE_SIZE ];
        for ( int pageNo : keys )
        {
            MemoryPage page = getPage( pageNo );
            for ( int i = 0 ; i < MMU.PAGE_SIZE ; i++ ) {
                pageData[i] = page.readByte( i );
            }
            final int adr = getPageStartAddress( pageNo );
            System.out.println("===== Page "+Misc.hex(adr)+" =====");
            System.out.println( Memory.hexdump( adr,pageData,0, MMU.PAGE_SIZE ) );
        }
    }

    public int getPageSize() {
        return PAGE_SIZE;
    }

    public void reset() {
        pageMap.clear();
        faultCount = 0;
    }
}