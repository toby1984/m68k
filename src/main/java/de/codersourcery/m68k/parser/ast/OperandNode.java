package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.AddressingMode;

public class OperandNode extends ASTNode
{
    public final AddressingMode mode;

    public OperandNode(AddressingMode mode)
    {
        super(NodeType.OPERAND);
        if ( mode == null ) {
            throw new IllegalArgumentException("mode ");
        }
        this.mode = mode;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Operand [ "+mode+" ]");
    }
}
