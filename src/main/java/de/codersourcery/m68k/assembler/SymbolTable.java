package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.Identifier;
import de.codersourcery.m68k.parser.ast.ASTNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A symbol table.
 *
 * Symbol tables may have a parent symbol table.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class SymbolTable
{
    public SymbolTable parent;

    public final Map<Identifier,Symbol> symbols =
            new HashMap<>();

    public final List<SymbolTable> children = new ArrayList<>();

    /**
     * Set's this symbol table's parent.
     *
     * @param parent parent , never <code>null</code>
     */
    public void setParent(SymbolTable parent)
    {
        if ( parent == null ) {
            throw new IllegalArgumentException("parent must not be NULL");
        }
        if ( this.parent != null ) {
            throw new IllegalStateException("Parent already set?");
        }
        this.parent = parent;
        parent.addChild(this);
    }

    private void addChild(SymbolTable table)
    {
        if ( table == null ) {
            throw new IllegalArgumentException("table must not be NULL");
        }
        children.add(table);
    }

    /**
     * Look-up a symbol by identifier.
     *
     * This method will recursively search parent symbol tables (if available)
     * when this symbol table contains no matching entry.
     *
     * @param identifier
     * @return symbol or <code>null</code>
     */
    public Symbol lookup(Identifier identifier)
    {
        if ( identifier == null ) {
            throw new IllegalArgumentException("identifier must not be NULL");
        }
        final Symbol result = symbols.get( identifier );
        if ( result == null && parent != null ) {
            return parent.lookup(identifier);
        }
        return result;
    }

    /**
     * Check whether a given symbol is declared in
     * this symbol table or any of it's parent tables (if any).
     *
     * A symbol is declared as soon as it's name is known.
     *
     * @param identifier
     * @return
     */
    public boolean isDeclared(Identifier identifier) {
        if ( identifier == null ) {
            throw new IllegalArgumentException("identifier must not be NULL");
        }
        if (symbols.containsKey(identifier) ) {
            return true;
        }
        return parent == null ? false : parent.isDeclared(identifier);
    }

    /**
     * Check whether a given symbol is defined in
     * this symbol table or any of it's parent tables (if any).
     *
     * A symbol is defined if it is declared and has a non-NULL value.
     *
     * @param identifier
     * @return
     */
    public boolean isDefined(Identifier identifier)
    {
        if ( identifier == null ) {
            throw new IllegalArgumentException("identifier must not be NULL");
        }
        final Symbol s = lookup(identifier);
        if ( s == null ) {
            return parent == null ? false : parent.isDefined(identifier);
        }
        return s.hasValue();
    }

    public void define(Identifier identifier)
    {
        Symbol existing = lookup(identifier);
        if ( existing == null ) {
            symbols.put(identifier,new Symbol(identifier,Symbol.SymbolType.UNKNOWN));
            return;
        }
        if ( existing.hasType(Symbol.SymbolType.UNKNOWN) ) {
            return; // ok
        }
        throw new IllegalArgumentException("Symbol already declared: "+existing);
    }

    public void declare(Symbol newSymbol)
    {
        if ( newSymbol.hasType(Symbol.SymbolType.UNKNOWN) ) {
            throw new IllegalArgumentException("Cannot declare symbol "+newSymbol+" with unknown type");
        }
        Symbol existing = lookup(newSymbol.identifier);
        if ( existing != null )
        {
            if ( ! existing.hasType(Symbol.SymbolType.UNKNOWN) )
            {
                throw new IllegalArgumentException("Symbol already defined: " + existing);
            }
        }
        this.symbols.put(newSymbol.identifier,newSymbol);
    }
}