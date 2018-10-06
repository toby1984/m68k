package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.Breakpoint;
import de.codesourcery.m68k.emulator.Breakpoints;
import de.codesourcery.m68k.emulator.Emulator;
import de.codesourcery.m68k.emulator.IBreakpointCondition;
import de.codesourcery.m68k.utils.Misc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class BreakpointsWindow extends AppWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    private static final Logger LOG = LogManager.getLogger( BreakpointsWindow.class.getName() );

    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private Breakpoints breakpoints = new Breakpoints();

    private Breakpoint activeBreakpoint;

    private final MyAbstractTableModel tableModel = new MyAbstractTableModel();

    private final JTextField newBpAddress = new JTextField();

    private final JTable table = new JTable(tableModel);

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
    }

    private class MyAbstractTableModel extends AbstractTableModel
    {
        @Override
        public int getRowCount()
        {
            synchronized (LOCK)
            {
                return breakpoints.size();
            }
        }

        private static final int COL_ADDRESS = 0;
        private static final int COL_COMMENT = 1;
        private static final int COL_ENABLED = 2;
        private static final int COL_CONDITION = 3;
        private static final int COL_COUNT = 4;

        @Override
        public Class<?> getColumnClass(int columnIndex)
        {
            switch (columnIndex)
            {
                case COL_ENABLED:
                    return Boolean.class;
                case COL_ADDRESS:
                case COL_COMMENT:
                case COL_CONDITION:
                    return String.class;
                default:
                    throw new IllegalArgumentException("Invalid column " + columnIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            switch(columnIndex)
            {
                case COL_ENABLED:
                case COL_COMMENT:
                case COL_CONDITION:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public int getColumnCount()
        {
            return COL_COUNT;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex)
        {
            if ( columnIndex == COL_COMMENT ) {
                // comment
                final Breakpoint bp = getBreakpoint(rowIndex);
                runOnEmulator(emu ->
                {
                    final Breakpoints bps = emu.getBreakpoints();
                    final Breakpoint existing = bps.getBreakpoint(bp.address);
                    if (existing != null)
                    {
                        bps.setComment(existing,(String) aValue);
                        emu.invokeTickCallback();
                    }
                });
                return;
            }
            if (columnIndex == COL_ENABLED )
            {
                // enable/disable
                final Breakpoint bp = getBreakpoint(rowIndex);
                final boolean newState;
                synchronized (LOCK)
                {
                    newState = ! breakpoints.isEnabled(bp);
                }
                runOnEmulator(emu ->
                {
                    final Breakpoints bps = emu.getBreakpoints();
                    final Breakpoint existing = bps.getBreakpoint(bp.address);
                    if (existing != null)
                    {
                        bps.setEnabled(existing,newState);
                        emu.invokeTickCallback();
                    }
                });
                return;
            }
            else if (columnIndex == COL_CONDITION )
            {
                // expression
                final String newExpr = (String) aValue;
                try
                {
                    final Breakpoint bp = getBreakpoint(rowIndex);
                    if ( bp != null )
                    {
                        final IBreakpointCondition newExpression =
                            ConditionalBreakpointExpressionParser.parse(newExpr);

                        runOnEmulator(emu ->
                        {
                            final Breakpoints bps = emu.getBreakpoints();
                            final Breakpoint existing = bps.getBreakpoint(bp.address);
                            if ( existing != null ) {
                                boolean isEnabled = bps.isEnabled(existing);
                                bps.remove(existing);
                                final Breakpoint newBP = existing.with(newExpression);
                                bps.add(newBP);
                                if ( ! isEnabled ) {
                                    bps.setDisabled(newBP);
                                }
                                emu.invokeTickCallback();
                            }
                        });
                    }
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
                return;
            }
            throw new IllegalArgumentException("cell not editable");
        }

        @Override
        public String getColumnName(int column)
        {
            switch(column) {
                case COL_ADDRESS:
                    return "Address";
                case COL_ENABLED:
                    return "Enabled?";
                case COL_CONDITION:
                    return "Expression";
                case COL_COMMENT:
                    return "Comment";
                default:
                    throw new IllegalArgumentException("Invalid column "+column);
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            final Breakpoint bp = getBreakpoint(rowIndex);
            switch (columnIndex)
            {
                case COL_ADDRESS:
                    return Misc.hex(bp.address);
                case COL_ENABLED:
                    synchronized (LOCK)
                    {
                        return breakpoints.isEnabled(bp);
                    }
                case COL_CONDITION:
                    return bp.condition.getExpression();
                case COL_COMMENT:
                    return bp.comment;
                default:
                    throw new IllegalArgumentException("Invalid column " + columnIndex);
            }
        }

        public Breakpoint getBreakpoint(int rowIndex)
        {
            final AtomicReference<Breakpoint> result = new AtomicReference<>();
            final Breakpoints.IBreakpointVisitor consumer = new Breakpoints.IBreakpointVisitor()
            {
                private int i = 0;

                @Override
                public boolean visit(Breakpoint breakpoint)
                {
                    if (i == rowIndex)
                    {
                        result.set(breakpoint);
                        return false;
                    }
                    i++;
                    return true;
                }
            };
            synchronized (LOCK)
            {
                breakpoints.visitBreakpoints(consumer);
            }
            if (result.get()== null)
            {
                throw new IllegalArgumentException("Invalid row " + rowIndex);
            }
            return result.get();
        }
    }

    public BreakpointsWindow(UI ui)
    {
        super("Breakpoints", ui);

        final TableCellRenderer tableCellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
            {
                Component result =
                        super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );

                final boolean highlight;
                synchronized(LOCK)
                {
                    final Breakpoint current = tableModel.getBreakpoint( row );
                    highlight = activeBreakpoint != null &&
                            current.address == activeBreakpoint.address;
                }
                if ( highlight ) {
                    setBackground( Color.RED );
                } else {
                    setBackground( BreakpointsWindow.this.getBackground() );
                }
                return result;
            }
        };
        table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultRenderer( String.class, tableCellRenderer );
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e)
            {
                if ( e.getKeyCode() == KeyEvent.VK_DELETE )
                {
                    final int[] rows = table.getSelectedRows();
                    if ( rows.length == 0 )
                    {
                        return;
                    }
                    synchronized (LOCK)
                    {
                        final List<Breakpoint> toDelete =
                                Arrays.stream(rows)
                                        .map( table::convertRowIndexToModel )
                                        .mapToObj( tableModel::getBreakpoint )
                                        .collect( Collectors.toList() );
                        runOnEmulator(emu ->
                        {
                            boolean deleted = false;
                            for ( Breakpoint copy: toDelete )
                            {
                                final Breakpoint bp = emu.getBreakpoints().getBreakpoint( copy.address );
                                if ( bp != null )
                                {
                                    emu.getBreakpoints().remove( bp );
                                    deleted = true;
                                }
                            }
                            if ( deleted )
                            {
                                emu.invokeTickCallback();
                            }
                        });
                    }
                }
            }
        });
        final JScrollPane scrollPane = new JScrollPane(table);

        newBpAddress.addActionListener(  ev ->
        {
            final int address;
            try
            {
                address = parseNumber( newBpAddress.getText().trim() );
            }
            catch(Exception e) {
                error( e );
                return;
            }
            final AtomicBoolean existsAlready = new AtomicBoolean();
            runOnEmulator( emu -> {
                existsAlready.set( emu.getBreakpoints().getBreakpoint( address ) != null );
            });
            if ( ! existsAlready.get() )
            {
                runOnEmulator( emu ->
                {
                    final Breakpoint breakpoint =
                            new Breakpoint( address, "",IBreakpointCondition.TRUE );
                    emu.getBreakpoints().add( breakpoint );
                    emu.invokeTickCallback();
                });
            }
        });

        newBpAddress.setColumns( 15 );

        getContentPane().setLayout(new GridBagLayout());

        GridBagConstraints cnstrs = cnstrs(0, 0);
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.weightx=1;cnstrs.weighty = 0;
        getContentPane().add(newBpAddress,cnstrs);

        cnstrs = cnstrs(0, 1);
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx=1;cnstrs.weighty = 1;
        getContentPane().add(scrollPane,cnstrs);
    }

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.BREAKPOINTS;
    }

    @Override
    public void tick(Emulator emulator)
    {
        final boolean dataChanged;
        final boolean bpChanged;
        synchronized (LOCK)
        {
            final Breakpoint previouslyActive = activeBreakpoint;
            activeBreakpoint = emulator.getBreakpoints().getBreakpoint( emulator.cpu.pc );
            bpChanged = previouslyActive == null ^ activeBreakpoint == null ||
                    previouslyActive != null && previouslyActive.address != activeBreakpoint.address;

            dataChanged = emulator.getBreakpoints().isDifferent(breakpoints);
            if (dataChanged)
            {
                breakpoints = emulator.getBreakpoints().createCopy();
            }
        }
        if ( dataChanged || bpChanged )
        {
            repaint();
            tableModel.fireTableDataChanged();
        }
    }
}