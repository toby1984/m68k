package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.ast.AST;
import org.apache.commons.lang3.Validate;

public class CompilationUnit
{
    private final IResource resource;
    private AST ast;

    public CompilationUnit(IResource resource) {
        Validate.notNull(resource, "resource must not be null");
        this.resource = resource;
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
}
