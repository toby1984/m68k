package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.Register;
import de.codersourcery.m68k.parser.TextRegion;

public class RegisterNode extends ASTNode
{
    public final Register register;

    public RegisterNode(Register register, TextRegion region)
    {
        super(NodeType.REGISTER, region);
        if ( register == null ) {
            throw new IllegalArgumentException("Register must not be NULL");
        }
        this.register = register;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Register [").append( register ).append("]\n");
    }
}
