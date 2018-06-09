package de.codersourcery.m68k.assembler;

public interface ICompilationPhase
{
    public void run(ICompilationContext ctx) throws Exception;
}
