package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.assembler.arch.Field;
import de.codersourcery.m68k.parser.Location;
import de.codersourcery.m68k.parser.ast.AST;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CompilationUnit
{
    public CompilationUnit parent;
    public final List<CompilationUnit> children=new ArrayList<>();
    private final IResource resource;
    private AST ast;
    private List<IntRange> lines;

    public CompilationUnit(IResource resource) {
        Validate.notNull(resource, "resource must not be null");
        this.resource = resource;
    }

    public void addChild(CompilationUnit child)
    {
        Validate.notNull(child, "child must not be null");
        if ( child.parent != null ) {
            throw new IllegalStateException("Child "+child+" already has parent "+child.parent);
        }
        children.add(child);
        child.setParent(this);
    }

    private void setParent(CompilationUnit parent) {
        Validate.notNull(parent, "parent must not be null");
        this.parent = parent;
    }

    public IResource getResource()
    {
        return resource;
    }

    public AST getAST()
    {
        return ast;
    }

    public void setAST(AST ast)
    {
        this.ast = ast;
    }

    private List<IntRange> getLineMap()
    {
        var result = new ArrayList<IntRange>();
        final char[] buffer = new char[10*1024];
        int startOffset = 0;
        try ( final InputStreamReader reader = new InputStreamReader(resource.createInputStream(),"UTF8") )
        {
            final int len = reader.read(buffer);
            for ( int i = 0 ; i < len ; i++ )
            {
                if ( buffer[i] == '\n' )
                {
                    result.add( new IntRange(Field.NONE,startOffset,i) );
                    startOffset = i;
                }
            }
        }
        catch (IOException e)
        {
            return new ArrayList<>();
        }
        return result;
    }

    /**
     * Find location within source file for a given offset
     * @param offset
     * @return location
     */
    public Optional<Location> getLocation(int offset)
    {
        if ( lines == null )
        {
            lines = getLineMap();
        }

        for (int i = 0; i < lines.size(); i++)
        {
            final IntRange range = lines.get(i);
            if ( range.contains(offset) )
            {
                int lineNo = 1+i;
                int column = 1+offset-range.start;
                return Optional.of( Location.of(this,lineNo,column,offset) );
            }
        }
        return Optional.empty();
    }
}