package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;

public interface IValueNode extends IASTNode
{
    /**
     * Returns a bit representation of this node's value suitabe for inclusion in an instruction word.
     *
     * This method is called during code generation.
     *
     * @param context current compilation context
     *
     * @return value value to use in instruction encoding or <code>null</code> if this value could not be determined/calculated.
     */
    public Integer getBits(ICompilationContext context);
}
