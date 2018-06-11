package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TextRegion;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class ASTNode
{
    public final NodeType type;
    private ASTNode parent;
    private final List<ASTNode> childs = new ArrayList<>(4);
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

    public final CommentNode asComment() {
        return (CommentNode) this;
    }

    public final IdentifierNode asIdentifier() {
        return (IdentifierNode) this;
    }

    public final InstructionNode asInstruction() {
        return (InstructionNode) this;
    }

    public final LabelNode asLabel() {
        return (LabelNode) this;
    }

    public final NumberNode asNumber() {
        return (NumberNode) this;
    }

    public final StatementNode asStatement() {
        return (StatementNode) this;
    }

    public final StringNode asString() {
        return (StringNode) this;
    }

    public final RegisterNode asRegister() {
        return (RegisterNode) this;
    }

    public final boolean is(NodeType t) {
        if ( t == null ) {
            throw new IllegalArgumentException("type must not be null");
        }
        return type == t;
    }

    public final boolean hasChildren()
    {
        return ! children().isEmpty();
    }

    public final TextRegion getRegion()
    {
        return region;
    }

    public final void setRegion(TextRegion region)
    {
        this.region = region;
    }

    public final TextRegion getMergedRegion() {

        TextRegion result = null;
        if ( this.region != null ) {
            result = this.region.createCopy();
        }
        for ( ASTNode child : children() )
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

    public final Stream<ASTNode> stream()
    {
        return children().stream();
    }

    public final boolean hasNoChildren()
    {
        return children().isEmpty();
    }

    public final int childCount()
    {
        return children().size();
    }

    public List<ASTNode> children()
    {
        return childs;
    }

    public final ASTNode child(int idx)
    {
        return children().get(idx);
    }

    public final boolean hasParent()
    {
        return parent != null;
    }

    public final boolean hasNoParent()
    {
        return parent == null;
    }

    public void add(ASTNode child)
    {
        if (child == null)
        {
            throw new IllegalArgumentException("Child must not be NULL");
        }
        children().add(child);
        child.parent = this;
    }

    public void remove(ASTNode child)
    {
        if (child == null)
        {
            throw new IllegalArgumentException("Child must not be NULL");
        }
        if ( children().remove(child) ) {
            child.parent = null;
        }
    }

    public final ASTNode getParent()
    {
        return parent;
    }

    public static final class IterationCtx<T>
    {
        public T value = null;
        public boolean stop = false;
        public boolean dontGoDeeper = false;

        public void stop(T value) {
            this.value = value;
            stop = true;
        }
        public void stop() {
            stop = true;
        }
        public void dontGoDeeper() {
            dontGoDeeper = true;
        }

        public boolean isGoDeeper()
        {
            if ( stop ) {
                return false;
            }
            if ( dontGoDeeper ) {
                dontGoDeeper = false;
                return false;
            }
            return true;
        }
    }

    public final <T> T visitInOrder(BiConsumer<ASTNode,IterationCtx<T>> visitor)
    {
        final IterationCtx<T> ctx = new IterationCtx<T>();
        visitInOrder(this, ctx, visitor);
        return ctx.value;
    }

    private <T> void visitInOrder(ASTNode node,IterationCtx<T> ctx, BiConsumer<ASTNode,IterationCtx<T>> visitor)
    {
        visitor.accept(node,ctx );
        if ( ctx.isGoDeeper() )
        {
            for ( ASTNode child : node.children() )
            {
                visitInOrder(child, ctx, visitor);
                if (ctx.stop)
                {
                    return;
                }
            }
        }
    }

    public final String toString()
    {
        final StringBuilder buffer = new StringBuilder();
        this.toString(buffer,0 );
        return buffer.toString();
    }

    public abstract void toString(StringBuilder buffer,int depth);

    protected static String indent(int depth) {
        return StringUtils.repeat(" ", depth*4);
    }

    public static int signExtend8To16(int input)
    {
        final int result = input & 0xff;
        return (input & 0x80) == 0 ? result : 0xff00 | result;
    }

    public static int signExtend8To32(int input)
    {
        final int result = input & 0xff;
        return (input & 0x80) == 0 ? result : 0xffffff00 | result;
    }

    public static int signExtend16To32(int input)
    {
        final int result = input & 0xffff;
        return (input & 1<<15) == 0 ? result : 0xffff0000 | result;
    }

    public static int signExtend(int input,int inputBits,int outputBits)
    {
        if ( outputBits < inputBits ) {
            throw new IllegalArgumentException("outputBits < inputBits: "+outputBits+" vs. "+inputBits);
        }
        switch(inputBits) {
            case 8:
                switch (outputBits) {
                    case 8:  return input;
                    case 16: return signExtend8To16(input);
                    case 32: return signExtend8To32(input);
                }
                break;
            case 16:
                switch (outputBits) {
                    case 16: return input;
                    case 32: return signExtend16To32(input);
                }
                break;
            case 32:
                if ( outputBits == 32 ) {
                    return input;
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported input bit count "+inputBits);
        }
        throw new IllegalArgumentException("Unsupported output bit count "+outputBits);
    }
}