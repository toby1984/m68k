package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.assembler.phases.CodeGenerationPhase;
import de.codersourcery.m68k.assembler.phases.FixAddressingModesPhase;
import de.codersourcery.m68k.assembler.phases.GatherSymbolsPhase;
import de.codersourcery.m68k.assembler.phases.ParsePhase;
import de.codersourcery.m68k.assembler.phases.ValidationPhase;
import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
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

    private final AssemblerOptions options = new AssemblerOptions();

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
        phases.add( new GatherSymbolsPhase() );
        // 1st pass, all instructions with as-of-now unknown operands will assume their maximum length
        phases.add( new CodeGenerationPhase(true) );
        // 2nd pass, by now all operands should have addresses assigned so the size estimate gets more accurate
        phases.add( new CodeGenerationPhase(true) );
        phases.add( new ValidationPhase() );
        // adjust addressing modes based on actual operand sizes
        // and recalculate label addresses again using the (potentially smaller) addressing modes
        phases.add( new FixAddressingModesPhase() );
        phases.add( new CodeGenerationPhase(true, FixAddressingModesPhase::isAddressingModesUpdated ) );
        // 4rd pass, actually generate code
        phases.add( new CodeGenerationPhase(false) );
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

        final boolean debug = options.debug;
        context = createContext(options);
        context.setCompilationUnit(unit);
        for ( ICompilationPhase phase : getPhases() )
        {
            if ( ! phase.shouldRun(context ) )
            {
                if ( debug )
                {
                    System.out.println("SKIPPING: " + phase);
                }
                continue;
            }
            context.setSegment(ICompilationContext.Segment.TEXT);
            context.setPhase(phase);
            try
            {
                if ( debug )
                {
                    System.out.println("RUNNING: " + phase);
                }
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
         if ( options.debug )
         {
            System.out.println( "==== Symbols =====\n\n"+unit.symbolTable );
         }
        return messages;
    }

    public AssemblerOptions getOptions()
    {
        return options;
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

    private MyCompilationContext createContext(AssemblerOptions options)
    {
        return new MyCompilationContext(options);
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
        private final AssemblerOptions options;
        private final Map<Class<? extends ICompilationPhase>,Map<String,Object>> blackboards = new HashMap<>();

        private MyCompilationContext(AssemblerOptions options)
        {
            Validate.notNull(options, "options must not be null");
            this.options = options;
        }

        @Override
        public void setPhase(ICompilationPhase phase)
        {
            Validate.notNull(phase, "phase must not be null");
            this.phase = phase;
        }

        @Override
        public Map<String, Object> getBlackboard(Class<? extends ICompilationPhase> phase)
        {
            if ( phase == null ) {
                throw new IllegalArgumentException("Phase must not be NULL");
            }
            Map<String, Object> result = blackboards.get(phase);
            if (result==null) {
                result = new HashMap<>();
                blackboards.put(phase,result);
            }
            return result;
        }

        @Override
        public AssemblerOptions options()
        {
            return options;
        }

        @Override
        public boolean isDebugModeEnabled()
        {
            return options.debug;
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
            try (var input = unit.getResource().createInputStream() )
            {
                return Misc.read(input);
            }
        }

        @Override
        public SymbolTable symbolTable()
        {
            return getCompilationUnit().symbolTable;
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
