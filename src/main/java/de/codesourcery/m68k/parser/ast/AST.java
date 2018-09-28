package de.codesourcery.m68k.parser.ast;

public class AST extends ASTNode
{
    public AST()
    {
        super(NodeType.AST);
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append("AST\n");
        for (IASTNode child : children() )
        {
            child.toString(buffer,depth+1);
        }
    }
}
