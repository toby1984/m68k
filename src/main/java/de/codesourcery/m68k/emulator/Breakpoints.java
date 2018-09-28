package de.codesourcery.m68k.emulator;

import de.codesourcery.m68k.emulator.ui.ConditionalBreakpointExpressionParser;

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
public class Breakpoints
{
    private Breakpoint[] enabledBreakpoints = new Breakpoint[0];
    private Breakpoint[] disabledBreakpoints = new Breakpoint[0];

    private int hashcode;

    public void populateFrom(Breakpoints breakpoints)
    {
        enabledBreakpoints = Stream.of(breakpoints.enabledBreakpoints).toArray(Breakpoint[]::new);
        disabledBreakpoints = Stream.of(breakpoints.disabledBreakpoints).toArray(Breakpoint[]::new);
        updateHashCode();
    }

    public interface IBreakpointVisitor
    {
        boolean visit(Breakpoint bp);
    }

    public Breakpoints() {
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

    public boolean isDifferent(Breakpoints other) {
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
    }

    public int size() {
        return enabledBreakpoints.length + disabledBreakpoints.length;
    }

    public boolean hasEnabledBreakpoints() {
        return enabledBreakpoints.length > 0;
    }

    public Breakpoints(Breakpoints other)
    {
        this.enabledBreakpoints = Arrays.copyOf(other.enabledBreakpoints,other.enabledBreakpoints.length);
        this.disabledBreakpoints = Arrays.copyOf(other.disabledBreakpoints,other.disabledBreakpoints.length);
        this.hashcode = other.hashcode;
    }

    public Breakpoints createCopy() {
        return new Breakpoints(this);
    }

    public void add(Breakpoint b)
    {
        internalRemove(b);
        enabledBreakpoints = addToArray(enabledBreakpoints,b);
        updateHashCode();
    }

    public void setEnabled(Breakpoint bp, boolean enabled)
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
                updateHashCode();
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
                updateHashCode();
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

    /**
     * Checks whether any breakpoint is hit by the current emulator state.
     *
     * If a temporary breakpoint got hit, it will be removed immediately.
     *
     * @param emulator
     * @return
     */
    public boolean checkBreakpointHit(Emulator emulator)
    {
        final int adr = emulator.cpu.pc;
        for (int i = 0, len = enabledBreakpoints.length ; i < len ; i++)
        {
            final Breakpoint bp = enabledBreakpoints[i];
            if ( bp.matchesAddress( adr ) && bp.matches( emulator ) )
            {
                if ( bp.isTemporary ) {
                    remove(bp);
                }
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

    public boolean hasEnabledBreakpoint(int address)
    {
        for ( var bp : enabledBreakpoints ) {
            if ( bp.address == address ) {
                return true;
            }
        }
        return false;
    }

    private static Breakpoint[] removeFromArray(Breakpoint[] array, Breakpoint toRemove)
    {
        return Stream.of( array ).filter( x -> x != toRemove ).toArray( Breakpoint[]::new);
    }

    private static Breakpoint[] addToArray(Breakpoint[] array, Breakpoint toAdd)
    {
        if ( array.length == 0 ) {
            return new Breakpoint[]{toAdd};
        }
        final List<Breakpoint> tmp = new ArrayList<>( Arrays.asList( array ) );
        for ( int i = 0 , len = tmp.size() ; i < len ; i++ ) {
            if ( toAdd.address <= tmp.get(i).address ) {
                tmp.add(i,toAdd);
                return tmp.toArray( new Breakpoint[0] );
            }
        }
        tmp.add(toAdd);
        return tmp.toArray( new Breakpoint[0] );
    }

    public void removeAllTemporaryBreakpoints() {

        final List<Breakpoint> toRemove = new ArrayList<>();
        for ( Breakpoint existing : enabledBreakpoints )
        {
            if ( existing.isTemporary ) {
                toRemove.add( existing );
            }
        }
        for ( Breakpoint existing : disabledBreakpoints )
        {
            if ( existing.isTemporary ) {
                toRemove.add( existing );
            }
        }
        for ( Breakpoint bp : toRemove ) {
            remove(bp);
        }
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
            }
        }
        for ( Breakpoint existing : disabledBreakpoints )
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

    public static Breakpoints load(Map<String,String> data)
    {
        final Breakpoints result = new Breakpoints();
        final Pattern p = Pattern.compile("breakpoint\\.(\\d+)\\.(.*)");
        for ( var entry : data.entrySet() )
        {
            final Matcher matcher =
                p.matcher(entry.getKey());
            if ( matcher.matches() )
            {
                final int adr = Integer.parseInt( matcher.group(1) );
                if ( result.getBreakpoint(adr) == null )
                {
                    final String prefix = "breakpoint."+adr+".";
                    final boolean enabled = Boolean.parseBoolean(data.get(prefix+"enabled") );
                    final Breakpoint bp = new Breakpoint(adr, ConditionalBreakpointExpressionParser.parse(data.get(prefix + "condition")));
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
            if ( ! bp.isTemporary ) // only remember persistent breakpoints
            {
                final boolean enabled = isEnabled( bp );
                final String prefix = "breakpoint." + bp.address + ".";
                data.put( prefix + "enabled", Boolean.toString( enabled ) );
                data.put( prefix + "condition", bp.condition.getExpression() );
            }
            return true;
        });
    }
}