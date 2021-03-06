package de.codesourcery.m68k.assembler;

import de.codesourcery.m68k.parser.TextRegion;
import de.codesourcery.m68k.parser.Token;
import de.codesourcery.m68k.parser.ast.IASTNode;

import java.io.IOException;
import java.util.Map;

/**
 * Compilation context.
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationContext
{
    /**
     * Memory/output segment.
     */
    public enum Segment
    {
        /**
         * Initialized data.
         */
        DATA,
        /**
         * Program code.
         */
        TEXT,
        /**
         * Uninitialized data.
         */
        BSS
    }

    // compilation
    public void setPhase(ICompilationPhase phase);

    public Map<String,Object> getBlackboard(Class<? extends ICompilationPhase> phaseClass);

    public ICompilationPhase getPhase();

    public AssemblerOptions options();

    public boolean isDebugModeEnabled();

    // compilation unit
    public void setCompilationUnit(CompilationUnit unit);

    public CompilationUnit getCompilationUnit();

    public String getSource(CompilationUnit unit) throws IOException;

    // symbol handling
    public SymbolTable symbolTable();

    // object code generation
    public IObjectCodeWriter getCodeWriter();

    public IObjectCodeWriter getCodeWriter(Segment segment);

    public Segment getSegment();

    public boolean isSegment(Segment s);

    public void setSegment(Segment segment);

    // message handling

    public boolean hasErrors();
    public CompilationMessages getMessages();

    // errors
    public void error(String message);
    public void error(String message, IASTNode node);
    public void error(String message, IASTNode node,Throwable t);
    public void error(String message, Token token);
    public void error(String message, TextRegion region);

    // warnings
    public void warn(String message);
    public void warn(String message, IASTNode node);
    public void warn(String message, Token token);
    public void warn(String message, TextRegion region);

    // info
    public void info(String message);
    public void info(String message, IASTNode node);
    public void info(String message, Token token);
    public void info(String message, TextRegion region);
}
