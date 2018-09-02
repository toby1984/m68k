package de.codersourcery.m68k.emulator;

import org.apache.commons.lang3.Validate;

/**
 * Condition for conditional breakpoints.
 *
 * @see Breakpoint#Breakpoint(int, IBreakpointCondition)
 * @see Breakpoint#matches(Emulator)
 */
public abstract class IBreakpointCondition
{
    public static final IBreakpointCondition TRUE = new IBreakpointCondition() {

        @Override
        public boolean matches(Emulator emulator)
        {
            return true;
        }

        @Override
        public String getExpression()
        {
            return "";
        }
    };


    /**
     * Returns whether the emulator's state satisfies this condition.
     *
     * @param emulator
     * @return <code>true</code> if the emulator's state satisfies this condition.
     */
    public abstract boolean matches(Emulator emulator);

    public abstract String getExpression();

    public final boolean equals(Object o)
    {
        if ( o != null && o.getClass() == getClass() )
        {
            return ((IBreakpointCondition) o).getExpression().equals(this.getExpression());
        }
        return false;
    }

    @Override
    public final int hashCode()
    {
        return getExpression().hashCode();
    }
}