package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.parser.TextRegion;

public class RegisterRangeNode extends ASTNode implements IValueNode
{
    public RegisterRangeNode()
    {
        super(NodeType.REGISTER_RANGE);
    }

    public RegisterRangeNode(TextRegion region)
    {
        super(NodeType.REGISTER_RANGE, region);
    }

    public void setRange(RegisterNode start,RegisterNode end)
    {
        removeAllChildren();
        add(start);
        add(end);
    }

    public RegisterNode rangeStart() {
        return (RegisterNode) child(0);
    }

    public RegisterNode rangeEnd() {
        return (RegisterNode) child(1);
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        rangeStart().toString(buffer,depth+1);
        buffer.append("-");
        rangeEnd().toString(buffer,depth+1);
    }

    @Override
    public Integer getBits(ICompilationContext context)
    {
        int start = rangeStart().getBits(context);
        int end = rangeEnd().getBits(context);
        if ( start > end ) {
            throw new IllegalStateException("start > end ?");
        }
        int mask = 0;
        for ( int i = start ; i <= end ; i++ ) {
            mask |= 1<<i;
        }
        final int leftShift = rangeStart().isAddressRegister() ? 8 : 0;
        return mask << leftShift;
    }
}