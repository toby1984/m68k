package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.Identifier;
import de.codersourcery.m68k.parser.ast.ASTNode;

public class Symbol
{
    public static enum SymbolType
    {
        /**
         * A label.
         */
        LABEL,
        /**
         * Used on symbols that only defined
         * but not declared yet.
         */
        UNKNOWN;
    }

    public final Identifier identifier;
    public ASTNode declaration;
    public SymbolType type;
    public Object value;

    public Symbol(Identifier identifier,SymbolType type) {
        if ( identifier == null ) {
            throw new IllegalArgumentException("Identifier must not be NULL");
        }
        if ( type == null ) {
            throw new IllegalArgumentException("type must not be NULL");
        }
        this.identifier = identifier;
        this.type = type;
    }

    public void setDeclaration(ASTNode declaration)
    {
        if ( declaration == null ) {
            throw new IllegalArgumentException("Declaration AST node must not be NULL");
        }
        this.declaration = declaration;
    }

    public ASTNode getDeclaration()
    {
        return declaration;
    }

    public void setType(SymbolType type)
    {
        if ( type == null ) {
            throw new IllegalArgumentException("type must not be NULL");
        }
        this.type = type;
    }

    public boolean hasType(SymbolType t)
    {
        if ( t == null ) {
            throw new IllegalArgumentException("type must not be NULL");
        }
        return this.type == t ;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public boolean hasNoValue() {
        return value == null;
    }

    @Override
    public boolean equals(Object o)
    {
        if ( o instanceof Symbol) {
            return this.identifier.equals( ((Symbol) o).identifier );
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return identifier.hashCode();
    }
}