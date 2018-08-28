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
        for (int i = 0; i < disabledBreakpoints.length; i++)
        {
            Breakpoint existing = disabledBreakpoints[i];
            if (bp.address == existing.address)
            {
                return;
            }
        }

        for (int i = 0; i < enabledBreakpoints.length; i++)
        {
            Breakpoint existing = enabledBreakpoints[i];
            if (bp.address == existing.address)
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
        for (int i = 0; i < enabledBreakpoints.length; i++)
        {
            Breakpoint existing = enabledBreakpoints[i];
            if (bp.address == existing.address)
            {
                return;
            }
        }
        for (int i = 0; i < disabledBreakpoints.length; i++)
        {
            Breakpoint existing = disabledBreakpoints[i];
            if (bp.address == existing.address)
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
            if ( bp.address == address ) {
                return bp;
            }
        }
        for ( Breakpoint bp : disabledBreakpoints ) {
            if ( bp.address == address ) {
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
            if (bp.address == adr )
            {
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled(Breakpoint b)
    {
        if ( Stream.of(enabledBreakpoints).anyMatch(x -> x.hasSameAddress(b) ) ) {
            return true;
        }
        if ( Stream.of(disabledBreakpoints).anyMatch(x -> x.hasSameAddress(b) ) ) {
            return false;
        }
        throw new IllegalArgumentException("Unknown breakpoint: "+b);
    }

    private static Breakpoint[] removeFromArray(Breakpoint[] array,Breakpoint toRemove)
    {
        return Stream.of( array ).filter( x -> x != toRemove ).toArray(Breakpoint[]::new);
    }

    private static Breakpoint[] addToArray(Breakpoint[] array,Breakpoint toAdd)
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
            if ( existing.hasSameAddress(b) ) {
                enabledBreakpoints = removeFromArray(enabledBreakpoints,existing);
                removed = true;
                hasChanged = true;
            }
        }
        for ( Breakpoint existing : disabledBreakpoints )
        {
            if ( existing.hasSameAddress(b) ) {
                enabledBreakpoints = removeFromArray(enabledBreakpoints,existing);
                removed = true;
                hasChanged = true;
            }
        }
        return removed;
    }
}
