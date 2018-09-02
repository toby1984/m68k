package de.codersourcery.m68k.emulator;

import org.apache.commons.lang3.Validate;

import java.util.Objects;

/**
 * A conditional breakpoint.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class Breakpoint
{
    public final int address;
    public final IBreakpointCondition condition;

    public Breakpoint(int address,IBreakpointCondition condition)
    {
        Validate.notNull( condition, "condition must not be null" );
        this.condition = condition;
        this.address = address;
    }

    public Breakpoint with(IBreakpointCondition newCondition)
    {
        return new Breakpoint(this.address,newCondition);
    }

    public boolean matches(Emulator emulator)
    {
        return condition.matches( emulator );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(address, condition);
    }

    @Override
    public boolean equals(Object obj)
    {
        if ( obj instanceof Breakpoint) {
            final Breakpoint bp = (Breakpoint) obj;
            return this.address == bp.address && this.condition.equals( bp.condition );
        }
        return false;
    }

    public boolean matchesAddress(int address) {
        return this.address == address;
    }

    public Breakpoint createCopy()
    {
        return new Breakpoint(this.address,this.condition);
    }

    @Override
    public String toString()
    {
        return "Breakpoint[ address="+ this.address+", condition = "+condition +" ]";
    }
}