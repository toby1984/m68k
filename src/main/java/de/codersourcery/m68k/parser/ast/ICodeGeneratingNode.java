package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.arch.Field;

public interface ICodeGeneratingNode
{
    public void generateCode(ICompilationContext ctx);

    public int getValueFor(Field field, ICompilationContext ctx);
}
