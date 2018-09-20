package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.ui.structexplorer.StructTreeModel;
import de.codersourcery.m68k.emulator.ui.structexplorer.StructTreeModelBuilder;
import de.codersourcery.m68k.emulator.ui.structexplorer.StructTreeNode;

import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Iterator;

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
    public String getWindowKey()
    {
        return "struct-explorer";
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
                if ( selected != null )
                {
                    StructTreeNode lastNode = (StructTreeNode) selected.getLastPathComponent();
                    for ( Iterator<StructTreeNode> iterator = finalModel.iterator(); iterator.hasNext() ; ) {
                        StructTreeNode node = iterator.next();
                        if ( node.equals( lastNode ) )
                        {
                            tree.setLeadSelectionPath( new TreePath( node.pathToRoot() ) );
                            break;
                        }
                    }
                }
            });
        }
    }
}