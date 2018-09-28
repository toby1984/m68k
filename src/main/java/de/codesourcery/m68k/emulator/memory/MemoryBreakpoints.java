package de.codesourcery.m68k.emulator.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A collection of breakpoints.
 *
 * Breakpoints may ONLY be manipulated from inside the emulator thread.
 */
public class MemoryBreakpoints
{
    private static final Logger LOG = LogManager.getLogger( MemoryBreakpoints.class.getName() );

    private MemoryBreakpoint[] enabledBreakpoints = new MemoryBreakpoint[0];
    private MemoryBreakpoint[] disabledBreakpoints = new MemoryBreakpoint[0];

    public MemoryBreakpoint lastHit = null;

    private int hashcode;

    public void populateFrom(MemoryBreakpoints breakpoints)
    {
        enabledBreakpoints = Stream.of(breakpoints.enabledBreakpoints).toArray(MemoryBreakpoint[]::new);
        disabledBreakpoints = Stream.of(breakpoints.disabledBreakpoints).toArray(MemoryBreakpoint[]::new);
        updateHashCode();
    }

    public interface IBreakpointVisitor
    {
        boolean visit(MemoryBreakpoint bp);
    }

    public MemoryBreakpoints() {
    }

    @Override
    public String toString()
    {
        return "enabled={"+Arrays.toString(enabledBreakpoints)+"},disabled={"+Arrays.toString(disabledBreakpoints)+"}";
    }

    public void visitBreakpoints(IBreakpointVisitor c)
    {
        for (int i = 0, enabledBreakpointsLength = enabledBreakpoints.length; i < enabledBreakpointsLength; i++)
        {
            if (!c.visit(enabledBreakpoints[i]))
            {
                return;
            }
        }
        for (int i = 0, disabledBreakpointsLength = disabledBreakpoints.length; i < disabledBreakpointsLength; i++)
        {
            if (!c.visit(disabledBreakpoints[i]))
            {
                return;
            }
        }
    }

    public int getHashCode() {
        return hashcode;
    }

    public boolean isDifferent(MemoryBreakpoints other) {
        return this.hashcode != other.hashcode;
    }

    private void updateHashCode()
    {
        int result = 0;
        for (int i = 0, enabledBreakpointsLength = enabledBreakpoints.length; i < enabledBreakpointsLength; i++)
        {
            result = (result+1)*31 + enabledBreakpoints[i].hashCode();
        }
        for (int i = 0, disabledBreakpointsLength = disabledBreakpoints.length; i < disabledBreakpointsLength; i++)
        {
            result = (result+1)*37 + disabledBreakpoints[i].hashCode();
        }
        hashcode = result;
        LOG.info( "updateHashCode(): "+result );
    }

    public int size() {
        return enabledBreakpoints.length + disabledBreakpoints.length;
    }

    public boolean hasEnabledBreakpoints() {
        return enabledBreakpoints.length > 0;
    }

    public MemoryBreakpoints(MemoryBreakpoints other)
    {
        this.enabledBreakpoints = Arrays.copyOf(other.enabledBreakpoints,other.enabledBreakpoints.length);
        this.disabledBreakpoints = Arrays.copyOf(other.disabledBreakpoints,other.disabledBreakpoints.length);
        this.hashcode = other.hashcode;
    }

    public MemoryBreakpoints createCopy() {
        return new MemoryBreakpoints(this);
    }

    public void add(MemoryBreakpoint b)
    {
        internalRemove(b);
        enabledBreakpoints = addToArray(enabledBreakpoints,b);
        updateHashCode();
    }

    public void setEnabled(MemoryBreakpoint bp, boolean enabled)
    {
        if ( enabled ) {
            setEnabled(bp);
        } else {
            setDisabled(bp);
        }
    }

    public void setDisabled(MemoryBreakpoint bp)
    {
        for (int i = 0,len=disabledBreakpoints.length; i < len; i++)
        {
            MemoryBreakpoint existing = disabledBreakpoints[i];
            if (bp == existing)
            {
                return;
            }
        }

        for (int i = 0,len=enabledBreakpoints.length; i < len; i++)
        {
            MemoryBreakpoint existing = enabledBreakpoints[i];
            if (bp == existing)
            {
                enabledBreakpoints = removeFromArray(enabledBreakpoints,bp);
                disabledBreakpoints = addToArray( disabledBreakpoints,bp);
                updateHashCode();
                return;
            }
        }
        throw new RuntimeException("Unknown breakpoint "+bp);
    }

    public void setEnabled(MemoryBreakpoint bp)
    {
        for (int i = 0,len = enabledBreakpoints.length ; i < len; i++)
        {
            MemoryBreakpoint existing = enabledBreakpoints[i];
            if (bp == existing)
            {
                return;
            }
        }

        for (int i = 0,len = disabledBreakpoints.length ; i < len ; i++)
        {
            MemoryBreakpoint existing = disabledBreakpoints[i];
            if (bp == existing)
            {
                disabledBreakpoints = removeFromArray(disabledBreakpoints,bp);
                enabledBreakpoints = addToArray( enabledBreakpoints,bp);
                updateHashCode();
                return;
            }
        }
        throw new RuntimeException("Unknown breakpoint "+bp);
    }

    public void checkRead(int startInclusive,int endExclusive)
    {
        final int len = enabledBreakpoints.length;
        if ( len > 0 )
        {
            int i = 0;
            do
            {
                final MemoryBreakpoint bp = enabledBreakpoints[i++];
                if ( bp.matches( startInclusive, endExclusive, MemoryBreakpoint.ACCESS_READ ) )
                {
                    lastHit = bp;
                    return;
                }
            } while ( i < len );
        }
    }

    public void checkWrite(int startInclusive,int endExclusive)
    {
        final int len = enabledBreakpoints.length;
        if ( len > 0 )
        {
            int i = 0;
            do
            {
                final MemoryBreakpoint bp = enabledBreakpoints[i++];
                if ( bp.matches( startInclusive, endExclusive, MemoryBreakpoint.ACCESS_WRITE ) )
                {
                    lastHit = bp;
                    return;
                }
            } while ( i < len );
        }
    }

    private static boolean contains(MemoryBreakpoint[] array,MemoryBreakpoint bp)
    {
        for ( int i = 0,len=array.length ; i < len ; i++) {
            if ( array[i] == bp ) {
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled(MemoryBreakpoint b)
    {
        if ( contains(enabledBreakpoints,b) ) {
            return true;
        }
        if ( contains(disabledBreakpoints,b) ) {
            return false;
        }
        throw new IllegalArgumentException("Unknown breakpoint: "+b);
    }

    public boolean hasEnabledBreakpoint(int address)
    {
        for ( var bp : enabledBreakpoints ) {
            if ( bp.address == address ) {
                return true;
            }
        }
        return false;
    }

    private static MemoryBreakpoint[] removeFromArray(MemoryBreakpoint[] array, MemoryBreakpoint toRemove)
    {
        return Stream.of( array ).filter( x -> x != toRemove ).toArray( MemoryBreakpoint[]::new);
    }

    private static MemoryBreakpoint[] addToArray(MemoryBreakpoint[] array, MemoryBreakpoint toAdd)
    {
        if ( array.length == 0 ) {
            return new MemoryBreakpoint[]{toAdd};
        }
        final List<MemoryBreakpoint> tmp = new ArrayList<>( Arrays.asList( array ) );
        for ( int i = 0 , len = tmp.size() ; i < len ; i++ ) {
            if ( toAdd.address <= tmp.get(i).address ) {
                tmp.add(i,toAdd);
                return tmp.toArray( new MemoryBreakpoint[0] );
            }
        }
        tmp.add(toAdd);
        return tmp.toArray( new MemoryBreakpoint[0] );
    }

    public void remove(MemoryBreakpoint b) {

        if ( ! internalRemove(b) )
        {
            throw new IllegalArgumentException("Unknown breakpoint " + b);
        }
    }

    private boolean internalRemove(MemoryBreakpoint b)
    {
        boolean removed = false;
        for ( MemoryBreakpoint existing : enabledBreakpoints )
        {
            if ( existing == b ) {
                enabledBreakpoints = removeFromArray(enabledBreakpoints,existing);
                removed = true;
            }
        }
        for ( MemoryBreakpoint existing : disabledBreakpoints )
        {
            if ( existing == b ) {
                disabledBreakpoints = removeFromArray(disabledBreakpoints,existing);
                removed = true;
            }
        }
        if ( removed ) {
            updateHashCode();
        }
        return removed;
    }

    public MemoryBreakpoint getBreakpoint(int address)
    {
        for (int i = 0, enabledBreakpointsLength = enabledBreakpoints.length; i < enabledBreakpointsLength; i++)
        {
            MemoryBreakpoint bp = enabledBreakpoints[i];
            if ( bp.address == address )
            {
                return bp;
            }
        }
        for (int i = 0, disabledBreakpointsLength = disabledBreakpoints.length; i < disabledBreakpointsLength; i++)
        {
            MemoryBreakpoint bp = disabledBreakpoints[i];
            if ( bp.address == address )
            {
                return bp;
            }
        }
        return null;
    }

    public static MemoryBreakpoints load(Map<String,String> data)
    {
        final MemoryBreakpoints result = new MemoryBreakpoints();
        final Pattern p = Pattern.compile("membreakpoint\\.(\\d+)\\.(.*)");
        for ( var entry : data.entrySet() )
        {
            final Matcher matcher = p.matcher(entry.getKey());
            if ( matcher.matches() )
            {
                final int adr = Integer.parseInt( matcher.group(1) );
                if ( result.getBreakpoint(adr) == null )
                {
                    final String prefix = "membreakpoint."+adr+".";
                    final boolean enabled = Boolean.parseBoolean(data.get(prefix+"enabled") );
                    final int flags = Integer.parseInt(data.get(prefix+"flags") );
                    final MemoryBreakpoint bp =
                            new MemoryBreakpoint(adr, flags);
                    result.add(bp);
                    if ( ! enabled )
                    {
                        result.setDisabled(bp);
                    }
                }
            }
        }
        return result;
    }

    public void save(Map<String,String> data) {

        visitBreakpoints(bp ->
        {
            final boolean enabled = isEnabled( bp );
            final String prefix = "membreakpoint." + bp.address + ".";
            data.put( prefix + "enabled", Boolean.toString( enabled ) );
            data.put( prefix + "flags", Integer.toString(bp.accessFlags));
            return true;
        });
    }
}