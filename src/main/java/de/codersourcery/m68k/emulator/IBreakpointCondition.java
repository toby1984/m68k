package de.codersourcery.m68k.emulator;

/**
 * Condition for conditional breakpoints.
 *
 * @see Breakpoint#Breakpoint(int, IBreakpointCondition)
 * @see Breakpoint#matches(Emulator)
 */
public interface IBreakpointCondition
{
    IBreakpointCondition TRUE = new AlwaysTrueBreakpoint();

    /**
     * Returns whether the emulator's state satisfies this condition.
     *
     * @param emulator
     * @return <code>true</code> if the emulator's state satisfies this condition.
     */
    boolean matches(Emulator emulator);

    static IBreakpointCondition unconditional(int adr) {
        return new UnconditionalBreakpoint(adr);
    }

    final class AlwaysTrueBreakpoint implements IBreakpointCondition
    {
        @Override
        public boolean matches(Emulator emulator)
        {
            return true;
        }
    }

    final class UnconditionalBreakpoint implements IBreakpointCondition
    {
        private final int adr;

        public UnconditionalBreakpoint(int adr)
        {
            this.adr = adr;
        }

        @Override
        public boolean matches(Emulator emulator)
        {
            return emulator.cpu.cycles == 1 && emulator.cpu.pc == adr;
        }
    }
}