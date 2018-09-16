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
    public static final int MEM_READ = 1;
    public static final int MEM_WRITE = 2;

    public final int address;
    public final IBreakpointCondition condition;
    public final boolean isTemporary;

    public Breakpoint(int address,IBreakpointCondition condition) {
        this(address,false,condition);
    }

    public Breakpoint(int address,boolean isTemporary,IBreakpointCondition condition)
    {
        Validate.notNull( condition, "condition must not be null" );
        this.condition = condition;
        this.address = address;
        this.isTemporary = isTemporary;
    }

    public Breakpoint with(IBreakpointCondition newCondition)
    {
        return new Breakpoint(this.address,this.isTemporary,newCondition);
    }

    public boolean matches(Emulator emulator)
    {
        return condition.matches( emulator );
    }

    public boolean matches(int accessFlags)
    {
        return condition.matches( accessFlags );
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

    public boolean matchesAddress(int startAddress,int endAddressExclusive) {
        return startAddress <= this.address && this.address < endAddressExclusive;
    }

    public Breakpoint createCopy()
    {
        return new Breakpoint(this.address,this.isTemporary,this.condition);
    }

    @Override
    public String toString()
    {
        return "Breakpoint[ address="+ this.address+", condition = "+condition +" ]";
    }
}