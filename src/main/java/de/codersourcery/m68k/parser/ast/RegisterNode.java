package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.assembler.arch.Scaling;
import de.codersourcery.m68k.parser.TextRegion;

public class RegisterNode extends ASTNode implements IValueNode
{
    public final Register register;

    /**
     * Always NULL unless "Rx.w" or "Rx.l" was parsed
     */
    public final OperandSize operandSize;
    public final Scaling scaling;

    public RegisterNode(Register register, OperandSize operandSize,Scaling scaling,TextRegion region)
    {
        super(NodeType.REGISTER, region);
        if ( register == null ) {
            throw new IllegalArgumentException("Register must not be NULL");
        }
        if ( operandSize != null && (operandSize != OperandSize.WORD && operandSize != OperandSize.LONG) ) {
            throw new IllegalArgumentException("Unsupported index register size "+operandSize);
        }
        this.scaling = scaling;
        this.register = register;
        this.operandSize = operandSize;
    }

    public boolean isAddressRegister() {
        return register.isAddress();
    }

    public boolean isPC() {
        return register == Register.PC;
    }

    public boolean isDataRegister() {
        return register.isData();
    }

    public boolean hasScaling() {
        return scaling != null;
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

    @Override
    public int getBits()
    {
        return register.bits;
    }
}