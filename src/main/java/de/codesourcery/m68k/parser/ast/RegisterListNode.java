package de.codesourcery.m68k.parser.ast;

import de.codesourcery.m68k.assembler.ICompilationContext;
import de.codesourcery.m68k.parser.TextRegion;

public class RegisterListNode extends ASTNode implements IValueNode
{
    public RegisterListNode()
    {
        super(NodeType.REGISTER_LIST);
    }

    public RegisterListNode(TextRegion region)
    {
        super(NodeType.REGISTER_LIST, region);
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("RegisterList[ ");
        for (IASTNode child : children() )
        {
            child.toString(buffer,depth+1);
        }
        buffer.append("]");
    }

    @Override
    public Integer getBits(ICompilationContext context)
    {
        /*
         * Register mask
         *
         * A7|A6|A5|A4|A3|A2|A1|A0|D7|D6|D5|D4|D3|D2|D1|D0
         */
        int mask = 0;
        for ( IASTNode child : children())
        {
            int bits = ((IValueNode) child).getBits(context);
            if ( child.is(NodeType.REGISTER) ) {
                bits = 1<<bits;
                if ( child.isAddressRegister() ) {
                    bits <<= 8;
                }
            }
            mask |= bits;
        }
        return mask;
    }
}
