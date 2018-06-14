package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TextRegion;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

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

    public CommentNode asComment();

    public IdentifierNode asIdentifier();

    public InstructionNode asInstruction();

    public LabelNode asLabel();

    public NumberNode asNumber();

    public StatementNode asStatement();

    public StringNode asString();

    public RegisterNode asRegister();

    public boolean isRegister();

    public boolean isAddressRegister();

    public boolean isPCRegister();

    public boolean isDataRegister();

    public  boolean is(NodeType t);

    public  boolean isNot(NodeType t);

    public  boolean hasChildren();

    public  TextRegion getRegion();

    public  void setRegion(TextRegion region);

    public  TextRegion getMergedRegion();

    public  Stream<ASTNode> stream();

    public  boolean hasNoChildren();

    public  int childCount();

    public List<ASTNode> children();

    public  ASTNode child(int idx);

    public  boolean hasParent();

    public  boolean hasNoParent();

    public void add(ASTNode child);

    public void remove(ASTNode child);

    public  ASTNode getParent();

    public  <T> T visitInOrder(BiConsumer<ASTNode,IterationCtx<T>> visitor);

    public void toString(StringBuilder buffer,int depth);

    public static int signExtend8To16(int input)
    {
         int result = input & 0xff;
        return (input & 0x80) == 0 ? result : 0xff00 | result;
    }

    public static int signExtend8To32(int input)
    {
         int result = input & 0xff;
        return (input & 0x80) == 0 ? result : 0xffffff00 | result;
    }

    public static int signExtend16To32(int input)
    {
         int result = input & 0xffff;
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