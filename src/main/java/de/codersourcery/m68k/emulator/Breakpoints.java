package de.codersourcery.m68k.emulator;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A collection of breakpoints.
 *
 * Breakpoints may ONLY be manipulated from inside the emulator thread.
 */
public class Breakpoints
{
    private Breakpoint[] enabledBreakpoints = new Breakpoint[0];
    private Breakpoint[] disabledBreakpoints = new Breakpoint[0];

    public boolean hasChanged = true;

    public Breakpoints() {
    }

    @Override
    public String toString()
    {
        return "enabled={"+Arrays.toString(enabledBreakpoints)+"},disabled={"+Arrays.toString(disabledBreakpoints)+"}";
    }

    public boolean hasEnabledBreakpoints() {
        return enabledBreakpoints.length > 0;
    }

    public Breakpoints(Breakpoints other) {
        this.enabledBreakpoints = Arrays.copyOf(other.enabledBreakpoints,other.enabledBreakpoints.length);
        this.disabledBreakpoints = Arrays.copyOf(other.disabledBreakpoints,other.disabledBreakpoints.length);
    }

    public Breakpoints createCopy() {
        return new Breakpoints(this);
    }

    public void add(Breakpoint b)
    {
        internalRemove(b);
        enabledBreakpoints = addToArray(enabledBreakpoints,b);
        hasChanged = true;
    }

    public void setStatus(Breakpoint bp, boolean enabled)
    {
        if ( enabled ) {
            setEnabled(bp);
        } else {
            setDisabled(bp);
        }
    }

    public void setDisabled(Breakpoint bp)
    {
        for (int i = 0,len=disabledBreakpoints.length; i < len; i++)
        {
            Breakpoint existing = disabledBreakpoints[i];
            if (bp == existing)
            {
                return;
            }
        }

        for (int i = 0,len=enabledBreakpoints.length; i < len; i++)
        {
            Breakpoint existing = enabledBreakpoints[i];
            if (bp == existing)
            {
                enabledBreakpoints = removeFromArray(enabledBreakpoints,bp);
                disabledBreakpoints = addToArray( disabledBreakpoints,bp);
                hasChanged = true;
                return;
            }
        }
        throw new RuntimeException("Unknown breakpoint "+bp);
    }

    public void setEnabled(Breakpoint bp)
    {
        for (int i = 0,len = enabledBreakpoints.length ; i < len; i++)
        {
            Breakpoint existing = enabledBreakpoints[i];
            if (bp == existing)
            {
                return;
            }
        }

        for (int i = 0,len = disabledBreakpoints.length ; i < len ; i++)
        {
            Breakpoint existing = disabledBreakpoints[i];
            if (bp == existing)
            {
                disabledBreakpoints = removeFromArray(disabledBreakpoints,bp);
                enabledBreakpoints = addToArray( enabledBreakpoints,bp);
                hasChanged = true;
                return;
            }
        }
        throw new RuntimeException("Unknown breakpoint "+bp);
    }

    public Breakpoint getBreakpoint(int address)
    {
        for ( Breakpoint bp : enabledBreakpoints ) {
            if ( bp.matchesAddress( address ) ) {
                return bp;
            }
        }
        for ( Breakpoint bp : disabledBreakpoints ) {
            if ( bp.matchesAddress( address ) ) {
                return bp;
            }
        }
        return null;
    }

    public boolean isBreakpointHit(Emulator emulator)
    {
        final int adr = emulator.cpu.pc;
        for (int i = 0, len = enabledBreakpoints.length ; i < len ; i++)
        {
            final Breakpoint bp = enabledBreakpoints[i];
            if ( bp.matchesAddress( adr ) && bp.matches( emulator ) )
            {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(Breakpoint[] array,Breakpoint bp)
    {
        for ( int i = 0,len=array.length ; i < len ; i++) {
            if ( array[i] == bp ) {
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled(Breakpoint b)
    {
        if ( contains(enabledBreakpoints,b) ) {
            return true;
        }
        if ( contains(disabledBreakpoints,b) ) {
            return false;
        }
        throw new IllegalArgumentException("Unknown breakpoint: "+b);
    }

    private static Breakpoint[] removeFromArray(Breakpoint[] array, Breakpoint toRemove)
    {
        return Stream.of( array ).filter( x -> x != toRemove ).toArray( Breakpoint[]::new);
    }

    private static Breakpoint[] addToArray(Breakpoint[] array, Breakpoint toAdd)
    {
        final Breakpoint[] tmp = Arrays.copyOf(array,array.length+1);
        tmp[tmp.length-1] = toAdd;
        return tmp;
    }

    public void remove(Breakpoint b) {

        if ( ! internalRemove(b) )
        {
            throw new IllegalArgumentException("Unknown breakpoint " + b);
        }
    }

    private boolean internalRemove(Breakpoint b)
    {
        boolean removed = false;
        for ( Breakpoint existing : enabledBreakpoints )
        {
            if ( existing == b ) {
                enabledBreakpoints = removeFromArray(enabledBreakpoints,existing);
                removed = true;
                hasChanged = true;
            }
        }
        for ( Breakpoint existing : disabledBreakpoints )
        {
            if ( existing == b ) {
                enabledBreakpoints = removeFromArray(enabledBreakpoints,existing);
                removed = true;
                hasChanged = true;
            }
        }
        return removed;
    }
}