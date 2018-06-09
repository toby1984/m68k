package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;

public interface ICodeGeneratingNode
{
    public void generateCode(ICompilationContext ctx);
}
