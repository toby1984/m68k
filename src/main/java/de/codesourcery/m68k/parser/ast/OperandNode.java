package de.codesourcery.m68k.parser.ast;

import de.codesourcery.m68k.assembler.arch.AddressingMode;
import de.codesourcery.m68k.parser.TextRegion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Operand node.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class OperandNode extends ASTNode
{
    public AddressingMode addressingMode;

    private List<IASTNode> children = new ArrayList<>();

    private int childCount = 0;
    private IValueNode baseDisplacement; // displacement relative to base register
    private IValueNode value; // either an address value/expression or the base register
    private RegisterNode indexRegister; // index register to use for (bd,BR,Xn.SIZE*SCALE,od) syntax
    private IValueNode outerDisplacement; // outer displacement to use for (bd,BR,Xn.SIZE*SCALE,od) syntax

    public OperandNode(AddressingMode mode, TextRegion region)
    {
        super(NodeType.OPERAND,region);
        if ( mode == null ) {
            throw new IllegalArgumentException("mode must not be NULL");
        }
        this.addressingMode = mode;
    }

    private static int count(ASTNode node) {
        return node == null ? 0 : 1;
    }

    public void setIndexRegister(RegisterNode indexRegister)
    {
        this.indexRegister = indexRegister;
        refreshList();;
    }

    public boolean hasAbsoluteAddressing() {
        return hasAddressingMode( AddressingMode.ABSOLUTE_LONG_ADDRESSING) ||
            hasAddressingMode( AddressingMode.ABSOLUTE_SHORT_ADDRESSING);
    }

    public boolean hasAddressingMode(AddressingMode mode) {
        if ( mode == null ) {
            throw new IllegalArgumentException("Mode must not be NULL");
        }
        return this.addressingMode == mode;
    }

    public RegisterNode getIndexRegister()
    {
        return indexRegister;
    }

    public IValueNode getValue() {
        return value;
    }

    public void setValue(IValueNode value)
    {
        this.value = value;
        refreshList();
    }

    public IValueNode getBaseDisplacement()
    {
        return baseDisplacement;
    }

    public void setBaseDisplacement(IValueNode value)
    {
        this.baseDisplacement = value;
        refreshList();
    }

    public IValueNode getOuterDisplacement()
    {
        return outerDisplacement;
    }

    public void setOuterDisplacement(IValueNode value)
    {
        this.outerDisplacement = value;
        refreshList();
    }

    @Override
    public List<IASTNode> children()
    {
        return children;
    }

    private void refreshList()
    {
        var result = new ArrayList(childCount);
        if ( value != null ) {
            result.add(value);
        }
        if ( indexRegister != null ) {
            result.add(indexRegister);
        }
        if ( baseDisplacement != null ) {
            result.add(baseDisplacement);
        }
        if ( outerDisplacement != null ) {
            result.add(outerDisplacement);
        }
        children = Collections.unmodifiableList(result);
    }

    @Override
    public void toString(StringBuilder buffer, int depth)
    {
        buffer.append(indent(depth)).append("Operand [ "+ addressingMode +" ]\n");
    }

    @Override
    public void add(IASTNode child)
    {
        throw new UnsupportedOperationException("Use setXXX(null) methods instead");
    }

    @Override
    public void remove(IASTNode child)
    {
        if ( child == null )
        {
            throw new IllegalArgumentException("child must not be null");
        }

        if ( child == indexRegister )
        {
            setIndexRegister(null);
        } else if ( child == baseDisplacement) {
            setBaseDisplacement(null);
        } else if ( child == outerDisplacement ) {
            setOuterDisplacement(null);
        } else if( child == value) {
            setValue(null);
        }
    }
}
