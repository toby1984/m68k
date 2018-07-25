package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.parser.TextRegion;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class ASTNode implements IASTNode
{
    public final NodeType type;
    private IASTNode parent;
    private final List<IASTNode> childs = new ArrayList<>(4);
    private TextRegion region;

    public ASTNode(NodeType type)
    {
        if ( type == null ) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
    }

    public ASTNode(NodeType type,TextRegion region) {
        this(type);
        this.region = region;
    }

    @Override
    public final CommentNode asComment() {
        return (CommentNode) this;
    }

    @Override
    public final IdentifierNode asIdentifier() {
        return (IdentifierNode) this;
    }

    @Override
    public final InstructionNode asInstruction() {
        return (InstructionNode) this;
    }

    @Override
    public final LabelNode asLabel() {
        return (LabelNode) this;
    }

    @Override
    public final NumberNode asNumber() {
        return (NumberNode) this;
    }

    @Override
    public final StatementNode asStatement() {
        return (StatementNode) this;
    }

    @Override
    public final StringNode asString() {
        return (StringNode) this;
    }

    @Override
    public final RegisterNode asRegister() {
        return (RegisterNode) this;
    }

    @Override
    public RegisterListNode asRegisterList()
    {
        return (RegisterListNode) this;
    }

    @Override
    public RegisterRangeNode asRegisterRange()
    {
        return (RegisterRangeNode) this;
    }

    @Override
    public final boolean isRegister() {
        return is(NodeType.REGISTER);
    }

    @Override
    public final boolean isRegister(Register r) {
        return isRegister() && asRegister().is(r);
    }

    @Override
    public boolean isAddressRegister() {
        return isRegister() && asRegister().isAddressRegister();
    }

    @Override
    public final boolean isPCRegister() {
        return isRegister() && asRegister().isPC();
    }

    @Override
    public boolean isDataRegister() {
        return isRegister() && asRegister().isDataRegister();
    }

    @Override
    public final boolean is(NodeType t) {
        if ( t == null ) {
            throw new IllegalArgumentException("type must not be null");
        }
        return type == t;
    }

    @Override
    public final boolean isNot(NodeType t) {
        if ( t == null ) {
            throw new IllegalArgumentException("type must not be null");
        }
        return type != t;
    }

    @Override
    public final boolean hasChildren()
    {
        return ! children().isEmpty();
    }

    @Override
    public final TextRegion getRegion()
    {
        return region;
    }

    @Override
    public final void setRegion(TextRegion region)
    {
        this.region = region;
    }

    @Override
    public final TextRegion getMergedRegion() {

        TextRegion result = null;
        if ( this.region != null ) {
            result = this.region.createCopy();
        }
        for ( IASTNode child : children() )
        {
            TextRegion merged = child.getMergedRegion();
            if ( merged != null ) {
                if ( result == null ) {
                    result = merged;
                } else {
                    result.merge(merged );
                }
            }
        }
        return result;
    }

    public final Stream<IASTNode> stream()
    {
        return children().stream();
    }

    @Override
    public final boolean hasNoChildren()
    {
        return children().isEmpty();
    }

    @Override
    public final int childCount()
    {
        return children().size();
    }

    @Override
    public List<IASTNode> children()
    {
        return childs;
    }

    @Override
    public final IASTNode child(int idx)
    {
        return children().get(idx);
    }

    @Override
    public final void setParent(IASTNode n)
    {
        this.parent = n;
    }

    @Override
    public final boolean hasParent()
    {
        return parent != null;
    }

    @Override
    public final boolean hasNoParent()
    {
        return parent == null;
    }

    @Override
    public void add(IASTNode child)
    {
        if (child == null)
        {
            throw new IllegalArgumentException("Child must not be NULL");
        }
        children().add(child);
        child.setParent( this );
    }

    @Override
    public final void removeAllChildren()
    {
        children().stream().forEach( this::remove );
    }

    @Override
    public void remove(IASTNode  child)
    {
        if (child == null)
        {
            throw new IllegalArgumentException("Child must not be NULL");
        }
        if ( children().remove(child) ) {
            child.setParent(null);
        }
    }

    @Override
    public final IASTNode  getParent()
    {
        return parent;
    }

    @Override
    public final <T> T visitInOrder(BiConsumer<IASTNode ,IterationCtx<T>> visitor)
    {
        final IterationCtx<T> ctx = new IterationCtx<T>();
        visitInOrder(this, ctx, visitor);
        return ctx.value;
    }

    private <T> void visitInOrder(IASTNode  node,IterationCtx<T> ctx, BiConsumer<IASTNode ,IterationCtx<T>> visitor)
    {
        visitor.accept(node,ctx );
        if ( ctx.isGoDeeper() )
        {
            for ( IASTNode  child : node.children() )
            {
                visitInOrder(child, ctx, visitor);
                if (ctx.stop)
                {
                    return;
                }
            }
        }
    }

    @Override
    public final String toString()
    {
        final StringBuilder buffer = new StringBuilder();
        this.toString(buffer,0 );
        return buffer.toString();
    }

    @Override
    public abstract void toString(StringBuilder buffer,int depth);

    protected static String indent(int depth) {
        return StringUtils.repeat(" ", depth*4);
    }

    protected final ASTNode findFirstChild(NodeType t)
    {
        for ( IASTNode  child : children() )
        {
            if ( child.is(t) ) {
                return child.asInstruction();
            }
        }
        return null;
    }
}