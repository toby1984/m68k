package de.codersourcery.m68k.emulator.ui;

import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class LibraryFunctionResolutionDialog
{
    private File baseDir;

    private JTextField baseDirTextField = new JTextField();

    private final List<UIConfig.LibraryMapping> mappings = new ArrayList<>();

    private MyTableModel tableModel = new MyTableModel();

    private final class MyTableModel implements TableModel
    {
        private final List<TableModelListener> listeners = new ArrayList<>();

        @Override
        public int getRowCount()
        {
            return mappings.size();
        }

        public boolean addNew()
        {
            if ( mappings.stream().anyMatch( x -> StringUtils.isBlank( x.libraryNameRegex ) ) )
            {
                return false;
            }
            final OptionalInt maxIdx = mappings.stream().mapToInt( x -> x.index ).max();
            int index = 0;
            if ( maxIdx.isPresent() ) {
                index = maxIdx.getAsInt()+1;
            }
            UIConfig.LibraryMapping newMapping = new UIConfig.LibraryMapping( "","",index );
            mappings.add( newMapping );
            final TableModelEvent ev = new TableModelEvent(this);
            listeners.forEach( l -> l.tableChanged( ev ) );
            return true;
        }

        @Override
        public int getColumnCount()
        {
            return 2;
        }

        @Override
        public String getColumnName(int columnIndex)
        {
            switch(columnIndex) {
                case 0:
                    return "Library Name RegEx";
                case 1:
                    return "File Name RegEx";
                default:
                    throw new IllegalArgumentException( "Invalid column "+columnIndex );
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex)
        {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            final UIConfig.LibraryMapping row = mappings.get( rowIndex );
            switch(columnIndex) {
                case 0:
                    return row.libraryNameRegex;
                case 1:
                    return row.descFileRegex;
                default:
                    throw new IllegalArgumentException( "Invalid column "+columnIndex );
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex)
        {
            final UIConfig.LibraryMapping row = mappings.get( rowIndex );
            switch(columnIndex)
            {
                case 0:
                    UIConfig.LibraryMapping newMapping = row.withLibraryNameRegex( (String) aValue );
                    mappings.set( rowIndex , newMapping );
                    return ;
                case 1:
                    newMapping = row.withDescFileRegex( (String) aValue );
                    mappings.set( rowIndex , newMapping );
                    return;
                default:
                    throw new IllegalArgumentException( "Invalid column "+columnIndex );
            }
        }

        @Override
        public void addTableModelListener(TableModelListener l)
        {
            listeners.add(l);
        }

        @Override
        public void removeTableModelListener(TableModelListener l)
        {
            listeners.remove(l);
        }
    };
    private final JTable table = new JTable(tableModel);

    public void showDialog(UIConfig appConfig)
    {
        mappings.clear();
        mappings.addAll( appConfig.getLibraryMappings() );

        mappings.sort( (a,b) -> a.libraryNameRegex.compareToIgnoreCase( b.libraryNameRegex ) );

        baseDir = appConfig.getLibraryFunctionDescBaseDir();
        if ( baseDir != null ) {
            baseDirTextField.setText( baseDir.getAbsolutePath() );
        } else {
            baseDirTextField.setText(null);
        }
        baseDirTextField.setEditable( false );

        final JDialog dialog = new JDialog( (JFrame) null );

        final Container pane = dialog.getContentPane();
        pane.setLayout(  new GridBagLayout() );

        // add row with base directory chooser
        GridBagConstraints cnstrs = AppWindow.cnstrsNoResize( 0, 0 );
        pane.add( new JLabel("Function descriptions basedir"));

        cnstrs = AppWindow.cnstrsNoResize( 1, 0 );
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        baseDirTextField.setColumns( 20 );
        baseDirTextField.setHorizontalAlignment( JTextField.RIGHT );
        pane.add( baseDirTextField , cnstrs );

        final JButton selectDirectoryButton = new JButton("Select...");
        selectDirectoryButton.addActionListener( ev ->
        {
            final JFileChooser chooser = new JFileChooser();
            if ( baseDir != null ) {
                chooser.setCurrentDirectory( baseDir );
            }
            chooser.setFileFilter( new FileFilter() {

                @Override
                public boolean accept(File f)
                {
                    return f.isDirectory() || ( f.isFile() && f.canRead() && f.getName().toLowerCase().endsWith(".fd" ) );
                }

                @Override
                public String getDescription()
                {
                    return "*.fd";
                }
            });
            chooser.setFileSelectionMode( JFileChooser.FILES_AND_DIRECTORIES );
            chooser.setDialogType( JFileChooser.SAVE_DIALOG );
            final int result = chooser.showDialog( null, "Select" );
            if ( result == JFileChooser.APPROVE_OPTION)
            {
                File file = chooser.getSelectedFile();
                if ( ! file.isDirectory() ) {
                    file = file.getParentFile();
                }
                baseDir = file;
                appConfig.setLibraryFunctionDescBaseDir( file );
                baseDirTextField.setText( file.getAbsolutePath() );
            }
        });

        cnstrs = AppWindow.cnstrsNoResize( 2, 0 );
        pane.add( selectDirectoryButton, cnstrs );

        // add table
        cnstrs = AppWindow.cnstrs( 0, 1 );
        cnstrs.gridwidth = 3;
        pane.add( new JScrollPane(table), cnstrs );

        // add button bar
        final JPanel buttons = new JPanel();
        buttons.setLayout(  new FlowLayout() );

        final JButton addRowButton = new JButton("Add");
        addRowButton.addActionListener( ev -> tableModel.addNew() );

        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener( ev -> dialog.dispose() );

        final JButton saveButton = new JButton("Save");
        saveButton.addActionListener( ev ->
        {
            appConfig.deleteLibraryMappings();
            for (UIConfig.LibraryMapping m : mappings)
            {
                if ( StringUtils.isNotBlank( m.libraryNameRegex ) )
                {
                    appConfig.addLibraryMapping( m.libraryNameRegex, m.descFileRegex );
                }
            }
            dialog.dispose();
        });

        buttons.add( addRowButton );
        buttons.add( cancelButton );
        buttons.add( saveButton );

        cnstrs = AppWindow.cnstrs( 0, 2 );
        cnstrs.gridwidth = 3;
        pane.add( buttons , cnstrs );

        // show dialog
        dialog.pack();
        dialog.setLocationRelativeTo( null );
        dialog.setVisible( true );
    }
}
