package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TextRegion;

public class StatementNode extends ASTNode
{
    public StatementNode()
    {
        super(NodeType.STATEMENT);
    }

    public StatementNode(TextRegion region)
    {
        super(NodeType.STATEMENT, region);
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Statement\n");
        for (ASTNode child : children() )
        {
            child.toString(buffer,depth+1);
        }
    }

    public InstructionNode getInstruction() {
        final ASTNode n = findFirstChild(NodeType.MNEMONIC);
        return n == null ? null : n.asInstruction();
    }
}
