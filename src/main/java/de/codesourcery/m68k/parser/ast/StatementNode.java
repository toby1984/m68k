package de.codesourcery.m68k.parser.ast;

import de.codesourcery.m68k.parser.TextRegion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public List<LabelNode> getLabels()
    {
        List<LabelNode> result = null;
        for ( IASTNode child : children() ) {
            if ( child.is(NodeType.LABEL) ) {
                if ( result == null ) {
                    result = new ArrayList<>();
                }
                result.add( (LabelNode) child);
            }
        }
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Statement\n");
        for (IASTNode child : children() )
        {
            child.toString(buffer,depth+1);
        }
    }

    public InstructionNode getInstruction() {
        final ASTNode n = findFirstChild(NodeType.INSTRUCTION);
        return n == null ? null : n.asInstruction();
    }
}
