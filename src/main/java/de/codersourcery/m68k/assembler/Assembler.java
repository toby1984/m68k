package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.assembler.phases.CodeGenerationPhase;
import de.codersourcery.m68k.assembler.phases.ParsePhase;
import de.codersourcery.m68k.assembler.phases.ValidationPhase;
import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.ASTNode;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M68000 assembler.
 * @author tobias.gierke@code-sourcery.de
 */
public class Assembler
{
    private final CompilationMessages messages = new CompilationMessages();
    private MyCompilationContext context;

    /**
     * Returns the assembler's phases that will be run
     * when {@link #compile(CompilationUnit)} is invoked.
     *
     * @return
     */
    public List<ICompilationPhase> getPhases()
    {
        var phases = new ArrayList<ICompilationPhase>();
        phases.add( new ParsePhase() );
        phases.add( new ValidationPhase() );
        phases.add( new CodeGenerationPhase() );
        return phases;
    }

    /**
     * Compiles source.
     *
     * Call {@link #getBytes()} to get the generated binary.
     *
     * @param unit
     * @return compilation messages
     * @see #getBytes()
     */
    public CompilationMessages compile(CompilationUnit unit)
    {
        messages.clearMessages();

        context = createContext();
        context.setCompilationUnit(unit);
        for ( ICompilationPhase phase : getPhases() )
        {
            context.setSegment(ICompilationContext.Segment.TEXT);
            context.setPhase(phase);
            try
            {
                phase.run(context);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                messages.error(unit,"Phase "+phase+" failed unexpectedly ("+e.getMessage()+")");
            }
            if ( context.hasErrors() )
            {
                break;
            }
         }
        return messages;
    }

    /**
     * Returns the generated binary data from the last call to {@link #compile(CompilationUnit)}.
     *
     * @return
     * @throws IllegalStateException if the last compilation failed with an error.
     */
    public byte[] getBytes()
    {
        if ( context.hasErrors() ) {
            throw new IllegalStateException("Compilation failed");
        }
        final ObjectCodeWriter writer = (ObjectCodeWriter)
                context.getCodeWriter(ICompilationContext.Segment.TEXT);
        return writer.getBytes();
    }

    private MyCompilationContext createContext()
    {
        return new MyCompilationContext();
    }

    private IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
    {
        return new ObjectCodeWriter();
    }

    private final class MyCompilationContext implements ICompilationContext
    {
        private ICompilationPhase phase;
        private CompilationUnit unit;
        private Map<Segment,IObjectCodeWriter> writers = new HashMap<>();
        private IObjectCodeWriter currentWriter;
        private ICompilationContext.Segment segment = Segment.TEXT;

        @Override
        public void setPhase(ICompilationPhase phase)
        {
            Validate.notNull(phase, "phase must not be null");
            this.phase = phase;
        }

        @Override
        public ICompilationPhase getPhase()
        {
            return phase;
        }

        @Override
        public void setCompilationUnit(CompilationUnit unit)
        {
            Validate.notNull(unit, "unit must not be null");
            this.unit = unit;
        }

        @Override
        public CompilationUnit getCompilationUnit()
        {
            return unit;
        }

        @Override
        public String getSource(CompilationUnit unit) throws IOException
        {
            Validate.notNull(unit, "unit must not be null");
            final var buffer = new StringBuilder();
            final var tmp = new char[1024*10];
            try (var reader = new InputStreamReader( unit.getResource().createInputStream() ) ) {
                final var len = reader.read(tmp);
                buffer.append(tmp,0,len);
            }
            return buffer.toString();
        }

        @Override
        public IObjectCodeWriter getCodeWriter()
        {
            return getCodeWriter( getSegment() );
        }

        @Override
        public IObjectCodeWriter getCodeWriter(Segment segment)
        {
            IObjectCodeWriter tmp = currentWriter;
            if ( tmp == null ) {
                tmp = writers.get( segment );
                if ( tmp == null ) {
                    tmp = createObjectCodeWriter(this );
                    writers.put(segment,tmp);
                }
                currentWriter = tmp;
            }
            return tmp;
        }

        @Override
        public ICompilationContext.Segment getSegment()
        {
            return segment;
        }

        @Override
        public boolean isSegment(ICompilationContext.Segment s)
        {
            if ( s == null ) {
                throw new IllegalArgumentException("segment == NULL ??");
            }
            return s == segment;
        }

        @Override
        public void setSegment(ICompilationContext.Segment segment)
        {
            this.segment = segment;
        }

        @Override
        public boolean hasErrors()
        {
            return messages.hasErrors();
        }

        @Override
        public CompilationMessages getMessages()
        {
            return messages;
        }

        @Override
        public void error(String message)
        {
            messages.error(getCompilationUnit(),message);
        }

        @Override
        public void error(String message, ASTNode node)
        {
            messages.error(getCompilationUnit(),message,node);
        }

        @Override
        public void error(String message, ASTNode node, Throwable t)
        {
            if ( t != null ) {
                t.printStackTrace();
            }
            error(message,node);
        }

        @Override
        public void error(String message, Token token)
        {
            messages.error(getCompilationUnit(),message,token);
        }

        @Override
        public void error(String message, TextRegion region)
        {
            messages.error(getCompilationUnit(),message,region);
        }

        @Override
        public void warn(String message)
        {
            messages.warn(getCompilationUnit(),message);
        }

        @Override
        public void warn(String message, ASTNode node)
        {
            messages.warn(getCompilationUnit(),message,node);
        }

        @Override
        public void warn(String message, Token token)
        {
            messages.warn(getCompilationUnit(),message,token);
        }

        @Override
        public void warn(String message, TextRegion region)
        {
            messages.warn(getCompilationUnit(),message,region);
        }

        @Override
        public void info(String message)
        {
            messages.info(getCompilationUnit(),message);
        }

        @Override
        public void info(String message, ASTNode node)
        {
            messages.info(getCompilationUnit(),message,node);
        }

        @Override
        public void info(String message, Token token)
        {
            messages.info(getCompilationUnit(),message,token);
        }

        @Override
        public void info(String message, TextRegion region)
        {
            messages.info(getCompilationUnit(),message,region);
        }
    }
}
