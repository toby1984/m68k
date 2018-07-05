package de.codersourcery.m68k.tools;

import de.codersourcery.m68k.assembler.Assembler;
import de.codersourcery.m68k.assembler.CompilationMessages;
import de.codersourcery.m68k.assembler.CompilationUnit;
import de.codersourcery.m68k.assembler.ICompilationMessages;
import de.codersourcery.m68k.assembler.IResource;
import de.codersourcery.m68k.parser.Location;
import de.codersourcery.m68k.parser.ast.AST;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static javax.swing.SwingUtilities.invokeAndWait;

public class AssemblerUI extends JFrame
{
    public static final String DIRTY_SUFFIX = " (UNSAVED)";
    private JToolBar toolbar = new JToolBar(JToolBar.HORIZONTAL );
    private JEditorPane editor = new JEditorPane();
    private JTable messageTable = new JTable();
    private final MyTableModel tableModel = new MyTableModel();

    private boolean isDirty;

    private final CompilationUnit unit = new CompilationUnit( new IResource()
    {
        @Override
        public InputStream createInputStream() throws IOException
        {
            String s = editor.getText();
            if ( s == null ) {
                s = "";
            }
            return new ByteArrayInputStream(s.getBytes("UTF8" ) );
        }

        @Override
        public OutputStream createOutputStream() throws IOException
        {
            throw new UnsupportedOperationException("Method createOutputStream not implemented");
        }

        @Override public boolean exists() { return true; }
    });

    private ICompilationMessages compilationMessages = new CompilationMessages();

    private File sourceFile;

    private final class MyTableModel implements TableModel
    {
        private final List<TableModelListener> listeners = new ArrayList<>();

        @Override
        public void addTableModelListener(TableModelListener l)
        {
            listeners.add(l);
        }

        public void modelChanged()
        {
            final TableModelEvent tableModelEvent = new TableModelEvent(this );
            listeners.forEach(l -> l.tableChanged(tableModelEvent) );
        }

        @Override
        public void removeTableModelListener(TableModelListener l)
        {
            listeners.remove(l);
        }

        @Override
        public int getRowCount()
        {
            return compilationMessages.getMessages().size();
        }

        @Override
        public int getColumnCount()
        {
            return 3;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex)
        {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return false;
        }

        @Override
        public String getColumnName(int columnIndex)
        {
            switch (columnIndex)
            {
                case 0:
                    return "Location";
                case 1:
                    return "Severity";
                case 2:
                    return "Message";
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            final ICompilationMessages.Message message = compilationMessages.getMessages().get(rowIndex);
            switch (columnIndex)
            {
                case 0:
                    if ( unit == null || message.location == null )
                    {
                        if ( message.location == null ) {
                            return "<unknown>";
                        }
                        return "@"+message.location;
                    }
                    final Optional<Location> location =
                        unit.getLocation(message.location.getStartingOffset());
                    return location.isPresent() ? location.get().toString() : "<unknown>";
                case 1:
                    return message.level.toString();
                case 2:
                    return message.text;
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex)
        {
            throw new UnsupportedOperationException("Method setValueAt() not supported");
        }
    };

    public AssemblerUI()
    {
        super("AssemblerUI");
        messageTable.setModel(tableModel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        editor.setFont( new Font(Font.MONOSPACED,Font.PLAIN,18));

        editor.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                markDirty();
            }
            @Override public void changedUpdate(DocumentEvent e) { }
        });

        final JMenuBar menuBar = new JMenuBar();
        setJMenuBar( menuBar );

        final JMenu menu1 = new JMenu("File" );
        menuBar.add( menu1 );

        menu1.add( menuItem("Reload", this::reload ) );
        menu1.add( menuItem("Open...", this::load) );
        menu1.add( menuItem("Save", this::save ) );
        menu1.add( menuItem("Save as...", this::saveAs ) );
        menu1.add( menuItem("Quit", () -> System.exit(0 ) ) );

        final TableCellRenderer tableCellRenderer = new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
            {
                final Component result = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                final ICompilationMessages.Message message = compilationMessages.getMessages().get(row);
                if ( message.level == ICompilationMessages.Level.WARN ) {
                    setBackground(Color.ORANGE);
                } else if ( message.level == ICompilationMessages.Level.ERROR ) {
                    setBackground(Color.RED);
                } else {
                    setBackground(Color.WHITE);
                }
                return result;
            }
        };

        messageTable.setDefaultRenderer(String.class, tableCellRenderer);
    }

    private JMenuItem menuItem(String label,Runnable action)
    {
        final JMenuItem item = new JMenuItem(label);
        item.addActionListener(ev -> action.run() );
        return item;
    }

    public void run()
    {
        getContentPane().setLayout( new GridBagLayout() );

        int y = 0;
        // add toolbar
        toolbar.add( button("Compile", this::compile) );
        toolbar.addSeparator();
        toolbar.add( button("Quit", () -> System.exit(0) ) );

        GridBagConstraints cnstrs = cnstrs(0, y);
        cnstrs.fill = GridBagConstraints.REMAINDER;
        cnstrs.weightx = 0;
        cnstrs.weighty = 0;
        cnstrs.anchor=GridBagConstraints.WEST;
        getContentPane().add( toolbar, cnstrs );

        final JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentShown(ComponentEvent e)
            {
                pane.setDividerLocation(0.8d);
                removeComponentListener(this);
            }
        });
        pane.setTopComponent( new JScrollPane(editor) );
        pane.setBottomComponent( new JScrollPane(messageTable) );

        // add message table
        y++;
        cnstrs = cnstrs(0, y);
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx = 1;
        cnstrs.weighty = 1;
        getContentPane().add( pane, cnstrs );

        // add message panel
        // setup window
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JButton button(String label,Runnable action)
    {
        final JButton button = new JButton(label );
        button.addActionListener( ev -> action.run() );
        return button;
    }

    private GridBagConstraints cnstrs(int x,int y)
    {
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.gridx = x;
        cnstrs.gridy = y;
        cnstrs.gridwidth = 1;
        cnstrs.gridheight = 1;
        cnstrs.weightx = 1;
        cnstrs.weighty = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        return cnstrs;
    }

    public static void main(String[] args) throws InvocationTargetException, InterruptedException
    {
        invokeAndWait(() -> {
            new AssemblerUI().run();
        });
    }

    private void save()
    {
        if ( sourceFile == null ) {
            saveAs();
            return;
        }

        try ( FileWriter writer = new FileWriter(sourceFile) )
        {
            writer.write(editor.getText());
            compilationMessages.info(unit,"Source saved to "+sourceFile.getAbsolutePath());
            clearDirty();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            compilationMessages.error(unit,"Failed to save source to "+sourceFile.getAbsolutePath()+": "+e.getMessage());
        }
    }

    private void saveAs()
    {
        File newFile = selectFile(sourceFile,true);
        if ( newFile != null ) {
            sourceFile = newFile;
            save();
        }
    }

    private void reload()
    {
        if ( sourceFile != null )
        {
            try ( BufferedReader reader = new BufferedReader(new FileReader( sourceFile ) ) ) {
                final StringBuilder buffer = new StringBuilder();
                String line = null;
                while ( (line=reader.readLine()) != null ) {
                    buffer.append(line).append("\n");
                }
                editor.setText(buffer.toString());
                compilationMessages.info(unit,"Loaded file "+sourceFile.getAbsolutePath());
                clearDirty();
            }
            catch (IOException e)
            {
                e.printStackTrace();
                compilationMessages.error(unit,"Failed to load "+sourceFile.getAbsolutePath()+": "+e.getMessage());
            }
        }
        else
        {
            load();
        }
    }

    private void load()
    {
        final File file = selectFile( sourceFile, false );
        if ( file != null && file.exists() ) {
            sourceFile = file;
            reload();
        }
    }

    private File selectFile(File previous,boolean forSave)
    {
        JFileChooser chooser = new JFileChooser();

        if ( previous != null )
        {
            chooser.setCurrentDirectory(previous.getParentFile());
            chooser.setSelectedFile( previous );
        }

        if ( forSave )
        {
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
            {
                return chooser.getSelectedFile();
            }
            return null;
        }
        if (chooser.showOpenDialog(this ) == JFileChooser.APPROVE_OPTION )
        {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private void compile()
    {
        final String source = editor.getText();
        if ( source == null || source.trim().isEmpty() ) {
            return;
        }

        Assembler asm = new Assembler();

        final long start = System.currentTimeMillis();
        try
        {
            compilationMessages = asm.compile(unit);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            compilationMessages.error(unit,"Internal error: "+e.getMessage());
        }
        final long elapsedMillis = System.currentTimeMillis() - start;
        if ( compilationMessages.hasErrors() ) {
            compilationMessages.info(unit,"Compilation FAILED ("+elapsedMillis+" ms)");
        } else {
            compilationMessages.info(unit,"Compilation successful ("+elapsedMillis+" ms)");
        }

        // TODO: syntax highlighting

        // repaint stuff
        tableModel.modelChanged();
    }

    private void markDirty()
    {
        if ( ! isDirty )
        {
            setTitle( createWindowTitle() + DIRTY_SUFFIX);
        }
        isDirty = true;
    }

    private String createWindowTitle()
    {
        return sourceFile == null ? "" : sourceFile.getAbsolutePath();
    }

    private void clearDirty()
    {
        if ( isDirty )
        {
            setTitle(createWindowTitle());
            isDirty = false;
        }
    }
}