package de.codersourcery.m68k.assembler;

/**
 * A compiler phase.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationPhase
{
    /**
     * Executes this compiler phase.
     *
     * @param ctx
     * @throws Exception
     */
    public void run(ICompilationContext ctx) throws Exception;

    public default boolean shouldRun(ICompilationContext ctx)
    {
        return true;
    }
}
