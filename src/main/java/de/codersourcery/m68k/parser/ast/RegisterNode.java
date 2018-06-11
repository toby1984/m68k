package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.parser.TextRegion;

public class RegisterNode extends ASTNode
{
    public final Register register;

    /**
     * Always NULL unless "Rx.w" or "Rx.l" was parsed
     */
    public final OperandSize operandSize;

    public RegisterNode(Register register, OperandSize operandSize,TextRegion region)
    {
        super(NodeType.REGISTER, region);
        if ( register == null ) {
            throw new IllegalArgumentException("Register must not be NULL");
        }
        if ( ! register.supportsOperandSizeSpec && operandSize != null ) {
            throw new IllegalArgumentException("Register "+register+" does not support operand size specification");
        }
        this.register = register;
        this.operandSize = operandSize;
    }

    public boolean isAddressRegister() {
        return register.isAddress();
    }

    public boolean isDataRegister() {
        return register.isData();
    }

    /**
     * Returns whether Rx.w or Rx.l was parsed
     *
     * @return
     */
    public boolean hasOperandSize() {
        return operandSize != null;
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Register [").append( register ).append("]\n");
    }
}
