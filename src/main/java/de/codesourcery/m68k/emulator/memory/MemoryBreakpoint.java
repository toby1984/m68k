package de.codesourcery.m68k.emulator.memory;

import de.codesourcery.m68k.utils.Misc;

public final class MemoryBreakpoint
{
    public static final int ACCESS_READ = 1;
    public static final int ACCESS_WRITE = 2;

    public final int address;
    public final int accessFlags;

    public MemoryBreakpoint(int address, int accessFlags)
    {
        if ( accessFlags == 0 ) {
            throw new IllegalArgumentException( "Need at least one access flag" );
        }
        this.address = address;
        this.accessFlags = accessFlags;
    }

    public MemoryBreakpoint withFlags(int accessFlags) {
        return new MemoryBreakpoint( this.address, accessFlags );
    }

    public boolean matches(int start,int endExclusive,int accessFlags) {
        return start <= this.address && this.address < endExclusive &&
                (this.accessFlags & accessFlags) != 0;
    }

    @Override
    public String toString()
    {
        return "MemoryBreakpoint[ "+ Misc.hex(address)+", flags: "+accessFlags;
    }
}