package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.assembler.phases.ParsePhase;
import de.codersourcery.m68k.parser.Lexer;
import de.codersourcery.m68k.parser.Parser;
import de.codersourcery.m68k.parser.StringScanner;
import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.AST;
import de.codersourcery.m68k.parser.ast.ASTNode;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Assembler
{
    private final CompilationMessages messages = new CompilationMessages();
    private MyCompilationContext context;

    public List<ICompilationPhase> getPhases()
    {
        var phases = new ArrayList<ICompilationPhase>();
        phases.add( new ParsePhase() );
        return phases;
    }

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
                messages.error("Phase "+phase+" failed unexpectedly ("+e.getMessage()+")");
            }
            if ( context.hasErrors() )
            {
                break;
            }
         }
        return messages;
    }

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
            return null;
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
        public void clearMessages()
        {
            messages.clearMessages();
        }

        @Override
        public List<ICompilationMessages.Message> getMessages()
        {
            return messages.getMessages();
        }

        @Override
        public void error(String message)
        {
            messages.error(message);
        }

        @Override
        public void error(String message, ASTNode node)
        {
            messages.error(message,node);
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
            messages.error(message,token);
        }

        @Override
        public void error(String message, TextRegion region)
        {
            messages.error(message,region);
        }

        @Override
        public void warn(String message)
        {
            messages.warn(message);
        }

        @Override
        public void warn(String message, ASTNode node)
        {
            messages.warn(message,node);
        }

        @Override
        public void warn(String message, Token token)
        {
            messages.warn(message,token);
        }

        @Override
        public void warn(String message, TextRegion region)
        {
            messages.warn(message,region);
        }

        @Override
        public void info(String message)
        {
            messages.info(message);
        }

        @Override
        public void info(String message, ASTNode node)
        {
            messages.info(message,node);
        }

        @Override
        public void info(String message, Token token)
        {
            messages.info(message,token);
        }

        @Override
        public void info(String message, TextRegion region)
        {
            messages.info(message,region);
        }
    }
}
