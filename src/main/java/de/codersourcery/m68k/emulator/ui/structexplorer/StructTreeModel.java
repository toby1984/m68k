package de.codersourcery.m68k.emulator.ui.structexplorer;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class StructTreeModel implements TreeModel
{
    public final AtomicReference<StructTreeNode> root =
            new AtomicReference<>( new StructTreeNode(0,"root") );

    @Override
    public Object getRoot()
    {
        return root.get();
    }

    @Override
    public Object getChild(Object parent, int index)
    {
        return ((StructTreeNode) parent).children.get(index);
    }

    @Override
    public int getChildCount(Object parent)
    {
        return ((StructTreeNode) parent).children.size();
    }

    @Override
    public boolean isLeaf(Object node)
    {
        return ((StructTreeNode) node).children.isEmpty();
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue)
    {

    }

    @Override
    public int getIndexOfChild(Object parent, Object child)
    {
        return ((StructTreeNode) parent).children.indexOf(child);
    }

    private final List<TreeModelListener> listeners =
            new ArrayList<>();

    @Override
    public void addTreeModelListener(TreeModelListener l)
    {
        listeners.add(l);
    }

    public void fireModelChanged() {
        TreeModelEvent ev = new TreeModelEvent(this,new Object[]{root});
        listeners.forEach( l -> l.treeStructureChanged( ev ) );
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l)
    {
        listeners.remove(l);
    }

    public void setRoot(StructTreeNode newModel)
    {
        root.set( newModel );
    }
}