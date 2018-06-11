package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.assembler.arch.Scaling;
import de.codersourcery.m68k.parser.TextRegion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Operand node.
 *
 * FIRST child is operand value.
 * SECOND child (if any) is offset in case this operand uses indirect addressing with an offset
 */
public class OperandNode extends ASTNode
{
    public final AddressingMode addressingMode;
    public final Scaling scaling;

    private List<ASTNode> children = new ArrayList<>();

    private int childCount = 0;
    private ASTNode innerDisplacement;
    private ASTNode value;
    private RegisterNode indexRegister;
    private ASTNode outerDisplacement;

    public OperandNode(AddressingMode mode, Scaling scaling,TextRegion region)
    {
        super(NodeType.OPERAND,region);
        if ( mode == null ) {
            throw new IllegalArgumentException("mode must not be NULL");
        }
        if ( scaling == null ) {
            throw new IllegalArgumentException("scaling must not be NULL");
        }
        this.scaling = scaling;
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

    public RegisterNode getIndexRegister()
    {
        return indexRegister;
    }

    public ASTNode getValue() {
        return value;
    }

    public void setValue(ASTNode value)
    {
        this.value = value;
        refreshList();
    }

    public ASTNode getInnerDisplacement()
    {
        return innerDisplacement;
    }

    public void setInnerDisplacement(ASTNode value)
    {
        this.innerDisplacement = value;
        refreshList();
    }

    public ASTNode getOuterDisplacement()
    {
        return outerDisplacement;
    }

    public void setOuterDisplacement(ASTNode value)
    {
        this.outerDisplacement = value;
        refreshList();
    }

    @Override
    public List<ASTNode> children()
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
        if ( innerDisplacement != null ) {
            result.add(innerDisplacement);
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
    public void add(ASTNode child)
    {
        throw new UnsupportedOperationException("Use setXXX(null) methods instead");
    }

    @Override
    public void remove(ASTNode child)
    {
        if ( child == null )
        {
            throw new IllegalArgumentException("child must not be null");
        }

        if ( child == indexRegister )
        {
            setIndexRegister(null);
        } else if ( child == innerDisplacement ) {
            setInnerDisplacement(null);
        } else if ( child == outerDisplacement ) {
            setOuterDisplacement(null);
        } else if( child == value) {
            setValue(null);
        }
    }
}
