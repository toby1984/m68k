package de.codesourcery.m68k.tools;

import de.codesourcery.m68k.assembler.Assembler;
import de.codesourcery.m68k.assembler.CompilationMessages;
import de.codesourcery.m68k.assembler.CompilationUnit;
import de.codesourcery.m68k.assembler.ICompilationContext;
import de.codesourcery.m68k.assembler.ICompilationMessages;
import de.codesourcery.m68k.assembler.IObjectCodeWriter;
import de.codesourcery.m68k.assembler.IResource;
import de.codesourcery.m68k.assembler.SRecordHelper;
import de.codesourcery.m68k.parser.Location;

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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static javax.swing.SwingUtilities.invokeAndWait;

public class AssemblerUI extends JFrame
{
    private static final String CONFIG_FILE = ".m68kconfig";

    private static final String CONFIG_KEY_LAST_SOURCE_FILE = "lastSourceFile";

    public static final String DIRTY_SUFFIX = " (UNSAVED)";
    private JToolBar toolbar = new JToolBar(JToolBar.HORIZONTAL );
    private JEditorPane editor = new JEditorPane();
    private JTable messageTable = new JTable();
    private final MyTableModel tableModel = new MyTableModel();

    private boolean configLoaded;
    private final Properties properties = new Properties();
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
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener( new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                quit();
            }
        } );
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
        menu1.add( menuItem("Quit", this::quit ) );

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
        toolbar.add( button("Quit", this::quit ) );

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
            error("Failed to save source to "+sourceFile.getAbsolutePath()+": "+e.getMessage(),e);
        }
    }

    private void saveAs()
    {
        File newFile = selectFile(sourceFile,true);
        if ( newFile != null ) {
            setSourceFile( newFile );
            save();
        }
    }

    private File getObjectFile()
    {
        return sourceFile == null ? null : createObjectFile( sourceFile );
    }

    private void setSourceFile(File file)
    {
        this.sourceFile = file;
        getConfiguration().put( CONFIG_KEY_LAST_SOURCE_FILE, file.getAbsolutePath() );
        saveConfiguration();
    }

    public void quit()
    {
        if ( isDirty && ! proceed( "Unsaved changes", "You have unsaved changes. Do you really want to QUIT ?" ) )
        {
            return;
        }
        saveConfiguration();
        System.exit(0);
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
                error("Failed to load "+sourceFile.getAbsolutePath()+": "+e.getMessage(),e);
            }
        }
        else
        {
            load();
        }
    }

    private void load()
    {
        final String lastFile = getConfiguration().getProperty( CONFIG_KEY_LAST_SOURCE_FILE );
        final File file;
        if ( lastFile != null ) {
            file = selectFile( new File(lastFile) , false );
        }
        else
        {
            file = selectFile( sourceFile, false );
        }
        if ( file != null && file.exists() ) {
            setSourceFile( file );
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


        final File objectFile = getObjectFile();
        if ( objectFile != null && objectFile.exists() ) {
            objectFile.delete();
        }
        final long start = System.currentTimeMillis();
        try
        {
            compilationMessages = asm.compile(unit);
            final byte[] executable = asm.getBytes(false);
            compilationMessages.info(unit,"Compilation produced "+executable.length+" bytes");
            if ( ! compilationMessages.hasErrors() && objectFile != null )
            {
                final IObjectCodeWriter writer = asm.getContext().getCodeWriter( ICompilationContext.Segment.TEXT );
                try ( FileOutputStream out = new FileOutputStream( objectFile ) )
                {
                    new SRecordHelper().write( writer.getBuffers(), out );
                }
                compilationMessages.info(unit,"Wrote "+executable.length+" bytes to "+objectFile.getAbsolutePath());
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            error("Internal error: ",e);
        }
        final long elapsedMillis = System.currentTimeMillis() - start;
        if ( compilationMessages.hasErrors() ) {
            error("Compilation FAILED ("+elapsedMillis+" ms)");
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

    private void saveConfiguration()
    {
        final File file = getConfigFile();
        try ( FileOutputStream inputStream = new FileOutputStream( file ) )
        {
            properties.save( inputStream, "# auto-generated, do not edit" );
        }
        catch (Exception e)
        {
            e.printStackTrace();
            error("Failed to save configuration to "+file.getAbsolutePath(),e);
        }
    }

    private Properties getConfiguration()
    {
        if ( ! configLoaded )
        {
            final File file = getConfigFile();
            if ( file.exists() )
            {
                try ( FileInputStream inputStream = new FileInputStream( file ) )
                {
                    properties.load( inputStream );
                    configLoaded = true;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    error( "Failed to load configuration from "+file.getAbsolutePath(), e);
                }
                if ( ! configLoaded )
                {
                    configLoaded = true;
                    saveConfiguration();
                }
            }
        }
        return properties;
    }

    private File getConfigFile()
    {
        final String userDir = System.getProperty( "user.home" );
        if ( userDir == null || userDir.trim().isEmpty() ) {
            throw new RuntimeException("Unable to determine user home directory");
        }
        return new File(userDir,CONFIG_FILE);
    }

    private static File createObjectFile(File sourceFile) {

        String file = sourceFile.getAbsolutePath();
        if ( file.length() > 2 && file.toLowerCase().endsWith(".s") ) {
            file = file.substring( 0,file.length()-2 );
        }
        return new File( file+".h68" );
    }

    private void clearDirty()
    {
        if ( isDirty )
        {
            setTitle(createWindowTitle());
            isDirty = false;
        }
    }

    private void error(String message)
    {
        error(message,null);
    }

    private void error(String message,Throwable cause)
    {
        compilationMessages.error( unit, message );
        tableModel.modelChanged();
    }

    private boolean proceed(String title,String message)
    {
        final int result = JOptionPane.showConfirmDialog( null, message, title, JOptionPane.WARNING_MESSAGE );
        return result == JOptionPane.OK_OPTION;
    }
}