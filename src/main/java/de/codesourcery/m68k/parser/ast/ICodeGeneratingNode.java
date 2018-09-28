package de.codesourcery.m68k.parser.ast;

import de.codesourcery.m68k.assembler.ICompilationContext;

public interface ICodeGeneratingNode
{
    public void generateCode(ICompilationContext ctx,boolean estimateSizeForUnknownOperands);
}
