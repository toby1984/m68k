package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.AST;
import de.codersourcery.m68k.parser.ast.ASTNode;

import java.io.IOException;

public interface ICompilationContext extends ICompilationMessages
{
    public enum Segment
    {
        DATA,
        TEXT,
        BSS
    }

    // compilation

    public void setPhase(ICompilationPhase phase);

    public ICompilationPhase getPhase();

    // compilation unit
    public void setCompilationUnit(CompilationUnit unit);

    public CompilationUnit getCompilationUnit();

    public String getSource(CompilationUnit unit) throws IOException;

    // object code generation
    public IObjectCodeWriter getCodeWriter();

    public IObjectCodeWriter getCodeWriter(Segment segment);

    public Segment getSegment();

    public boolean isSegment(Segment s);

    public void setSegment(Segment segment);
}
