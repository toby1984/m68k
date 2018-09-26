package de.codersourcery.m68k.emulator.ui;

import org.apache.commons.lang3.StringUtils;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            return 3;
        }

        @Override
        public String getColumnName(int columnIndex)
        {
            switch(columnIndex) {
                case 0:
                    return "Library Name RegEx";
                case 1:
                    return "File Name RegEx";
                case 2:
                    return "Matched file";
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
            return columnIndex != 2;
        }

        public List<String> getMatches(UIConfig.LibraryMapping row) {
            if ( baseDir != null && baseDir.exists() && StringUtils.isNotBlank(row.descFileRegex ) )
            {
                final Pattern pat = Pattern.compile(row.descFileRegex,Pattern.CASE_INSENSITIVE);
                final AtomicInteger matchCount = new AtomicInteger(0);
                try
                {
                    final Stream<Path> stream = Files.list( baseDir.toPath() );
                    return stream.takeWhile( p -> matchCount.get() < 2 )
                            .map( path -> path.getFileName().toString() )
                            .filter(  path ->
                            {
                                final boolean matched = pat.matcher( path ).matches();
                                if ( matched ) {
                                    matchCount.incrementAndGet();
                                }
                                return matched;
                            }).collect(Collectors.toList());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return new ArrayList<>();
        }

        public UIConfig.LibraryMapping getRow(int row) {
            return mappings.get(row);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            final UIConfig.LibraryMapping row = getRow( rowIndex );
            switch(columnIndex) {
                case 0:
                    return row.libraryNameRegex;
                case 1:
                    return row.descFileRegex;
                case 2:
                    final List<String> matches = getMatches( row );
                    return matches.stream().collect( Collectors.joining(",") );
                default:
                    throw new IllegalArgumentException( "Invalid column "+columnIndex );
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex)
        {
            final UIConfig.LibraryMapping row = getRow( rowIndex );
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
        table.setDefaultRenderer( String.class , new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
            {
                final Component result =
                        super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );
                final UIConfig.LibraryMapping mapping = tableModel.getRow(row);
                final int matchCount = tableModel.getMatches( mapping ).size();
                if ( matchCount == 0 || matchCount > 1 ) {
                    setBackground( Color.RED );
                } else {
                    setBackground( table.getBackground() );
                }
                return result;
            }
        } );
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
