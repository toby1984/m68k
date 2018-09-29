package de.codesourcery.m68k.emulator;

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
    public final boolean isTemporary;
    public final String comment;

    public Breakpoint(int address,String comment,IBreakpointCondition condition) {
        this(address,false,comment,condition);
    }

    public Breakpoint(int address,boolean isTemporary,String comment,IBreakpointCondition condition)
    {
        Validate.notNull( condition, "condition must not be null" );
        this.condition = condition;
        this.address = address;
        this.isTemporary = isTemporary;
        this.comment = comment;
    }

    public Breakpoint with(IBreakpointCondition newCondition)
    {
        return new Breakpoint(this.address,this.isTemporary,this.comment,newCondition);
    }

    public Breakpoint withComment(String newComment)
    {
        return new Breakpoint(this.address,this.isTemporary,newComment,this.condition);
    }

    public boolean matches(Emulator emulator)
    {
        return condition.matches( emulator );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(address, condition,comment);
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
        return new Breakpoint(this.address,this.isTemporary,this.comment,this.condition);
    }

    @Override
    public String toString()
    {
        return "Breakpoint[ address="+ this.address+", condition = "+condition +" ]";
    }
}