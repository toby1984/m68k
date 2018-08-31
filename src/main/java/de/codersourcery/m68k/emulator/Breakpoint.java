package de.codersourcery.m68k.emulator;

import org.apache.commons.lang3.Validate;

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

    public boolean matches(Emulator emulator)
    {
        return condition.matches( emulator );
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