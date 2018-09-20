package de.codersourcery.m68k.emulator.ui.structexplorer;

import java.sql.Struct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

public class StructTreeNode
{
    public final List<StructTreeNode> children = new ArrayList<>();
    public final int address;
    public StructTreeNode parent;
    public String lable;

    public StructTreeNode(int address,String lable)
    {
        this.address = address;
        this.lable = lable;
    }

    public Iterator<StructTreeNode> iterator() {

        final Stack<StructTreeNode> stack = new Stack<>();
        stack.push(this);
        return new Iterator<>()
        {
            @Override
            public boolean hasNext()
            {
                return !stack.isEmpty();
            }

            @Override
            public StructTreeNode next()
            {
                final StructTreeNode result = stack.pop();
                stack.addAll( result.children );
                return result;
            }
        };
    }

    public Object[] pathToRoot() {

        final LinkedList<StructTreeNode> path = new LinkedList<>();
        StructTreeNode current = this;
        do {
            path.addFirst( current );
            current = current.parent;
        } while ( current != null );
        return path.toArray();
    }

    public static boolean compare(StructTreeNode tree1, StructTreeNode tree2) {

        final Iterator<StructTreeNode> it1 = tree1.iterator();
        final Iterator<StructTreeNode> it2 = tree2.iterator();
        final Set<StructTreeNode> visited = new HashSet<>();
        while ( it1.hasNext() && it2.hasNext() )
        {
            final StructTreeNode n1 = it1.next();
            final StructTreeNode n2 = it2.next();
            if ( ! n1.equals( n2 ) ) {
                return false;
            }
            if ( visited.contains( n1 ) ) {
                return true;
            }
            visited.add( n1 );
        }
        return it1.hasNext() == it2.hasNext();
    }

    @Override
    public boolean equals(Object o)
    {
        if ( o instanceof StructTreeNode) {
            final StructTreeNode that = (StructTreeNode) o;
            return this.address == that.address && this.lable.equals( that.lable );
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.address,this.lable);
    }

    public void add(StructTreeNode node) {
        node.parent = this;
        children.add(node);
    }

    @Override
    public String toString()
    {
        return lable;
    }
}
