package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.ui.structexplorer.StructTreeModel;
import de.codersourcery.m68k.emulator.ui.structexplorer.StructTreeModelBuilder;
import de.codersourcery.m68k.emulator.ui.structexplorer.StructTreeNode;
import de.codersourcery.m68k.utils.Misc;

import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StructExplorer extends AppWindow implements
        ITickListener, Emulator.IEmulatorStateCallback
{
    private final StructTreeModel treeModel = new StructTreeModel();

    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private StructTreeModelBuilder.StructType structType = StructTreeModelBuilder.StructType.NODE;

    // @GuardedBy( LOCK )
    private IAdrProvider adrProvider = new FixedAdrProvider( 0 );

    private final JTree tree = new JTree(treeModel);

    private JComboBox<StructTreeModelBuilder.StructType> comboBox =
            new JComboBox<>( StructTreeModelBuilder.StructType.values() );

    private JTextField expression = new JTextField();

    public StructExplorer(UI ui)
    {
        super( "Struct Explorer", ui );

        expression.setText( "$00000000" );
        expression.addActionListener( ev ->
        {
            final IAdrProvider provider = parseExpression( expression.getText() );
            if ( provider != null ) {
                synchronized (LOCK)
                {
                    adrProvider = provider;
                }
                runOnEmulator( this::tick );
            }
        });

        comboBox.addActionListener( ev -> {
            synchronized (LOCK)
            {
                structType = (StructTreeModelBuilder.StructType) comboBox.getSelectedItem();
            }
            runOnEmulator( this::tick );
        });

        attachKeyListeners(tree);
        tree.setExpandsSelectedPaths( true );
        tree.setRootVisible( false );

        setFocusable( true );
        getContentPane().setLayout( new GridBagLayout() );
        GridBagConstraints cnstrs = cnstrs( 0, 0 );
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.weightx=0.7;cnstrs.weighty=0;
        expression.setColumns( 10 );
        getContentPane().add( expression,cnstrs );

        cnstrs = cnstrs( 1, 0 );
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.weightx=0.3;cnstrs.weighty=0;
        getContentPane().add( comboBox ,cnstrs );

        cnstrs = cnstrs( 0, 1 );
        cnstrs.weightx=1;cnstrs.weighty=1;
        cnstrs.gridwidth = 2;
        cnstrs.fill=GridBagConstraints.BOTH;
        final JScrollPane scrollPane = new JScrollPane( tree );
        attachKeyListeners(scrollPane);
        getContentPane().add( scrollPane,cnstrs );
    }

    @Override
    public void stopped(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.STRUCT_EXPLORER;
    }

    private String debugString(StructTreeNode node) {
        return "Node #"+node.nodeId+" @ "+ Misc.hex(node.address);
    }

    private String debugString(TreePath path) {
        if ( path == null ) {
            return "<NULL>";
        }
        return Arrays.stream( path.getPath() ).map( x -> debugString( (StructTreeNode) x) )
                .collect( Collectors.joining(",") );
    }

    @Override
    public void tick(Emulator emulator)
    {
        StructTreeNode newModel = null;
        synchronized( LOCK )
        {
            final StructTreeModelBuilder builder = new StructTreeModelBuilder( emulator );
            newModel = builder.build( adrProvider.getAddress( emulator ), structType, 4 );
            if ( StructTreeNode.compare( newModel, this.treeModel.root.get() ) )
            {
                newModel = null;
            }
        }
        System.out.println("Tree model changed: "+(newModel!=null));
        if ( newModel != null)
        {
            final StructTreeNode finalModel = newModel;
            runOnEDT( () ->
            {
                final TreePath selected = tree.getSelectionPath();
                // whole tree model changed
                this.treeModel.setRoot( finalModel );
                this.treeModel.fireModelChanged();

                // try to restore expanded path
                if ( selected != null )
                {
                    System.out.println("Selected: "+debugString(selected));
outer:
                    for ( int i = selected.getPathCount()-1 ; i >= 0 ; i--)
                    {
                        StructTreeNode node = (StructTreeNode) selected.getPathComponent( i );
                        for ( Iterator<StructTreeNode> iterator = finalModel.iterator(); iterator.hasNext() ; ) {
                            StructTreeNode actual = iterator.next();
                            if ( node.nodeId == actual.nodeId )
                            {
                                final TreePath newPath = new TreePath( actual.pathToRoot() );
                                System.out.println("new selection : "+ debugString(newPath) );
                                tree.setSelectionPath( newPath );
                                break outer;
                            }
                        }
                    }
                }
            });
        }
    }
}