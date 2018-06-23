package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.parser.TextRegion;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * AST node.
 */
public interface IASTNode
{
    public static  class IterationCtx<T>
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

    public  boolean is(NodeType t);

    public  boolean isNot(NodeType t);

    public  boolean hasChildren();

    public  boolean hasNoChildren();

    public  int childCount();

    public List<ASTNode> children();

    public  ASTNode child(int idx);

    public  boolean hasParent();

    public  boolean hasNoParent();

    public  ASTNode getParent();

    // tree mutation
    public void add(ASTNode child);

    public void remove(ASTNode child);

    // tree traversal

    public  <T> T visitInOrder(BiConsumer<ASTNode,IterationCtx<T>> visitor);

    // source text locations
    public  TextRegion getRegion();

    public  void setRegion(TextRegion region);

    public  TextRegion getMergedRegion();

    // convenience methods for commonly required checks
    public boolean isRegister(Register r);

    public boolean isRegister();

    public boolean isAddressRegister();

    public boolean isPCRegister();

    public boolean isDataRegister();

    // Convenience methods to make casting less annoying.

    public CommentNode asComment();

    public IdentifierNode asIdentifier();

    public InstructionNode asInstruction();

    public LabelNode asLabel();

    public NumberNode asNumber();

    public StatementNode asStatement();

    public StringNode asString();

    public RegisterNode asRegister();

    // utility
    public void toString(StringBuilder buffer,int depth);
}