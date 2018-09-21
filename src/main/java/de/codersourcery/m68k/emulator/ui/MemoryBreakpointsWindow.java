package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.memory.MemoryBreakpoint;
import de.codersourcery.m68k.emulator.memory.MemoryBreakpoints;
import de.codersourcery.m68k.utils.Misc;

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

public class MemoryBreakpointsWindow extends AppWindow implements Emulator.IEmulatorStateCallback,
        ITickListener
{
    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private MemoryBreakpoints breakpoints = new MemoryBreakpoints();

    private MemoryBreakpoint activeBreakpoint;

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

        @Override
        public Class<?> getColumnClass(int columnIndex)
        {
            switch (columnIndex)
            {
                case 0:
                    return String.class;
                case 1:
                    return Boolean.class;
                case 2:
                    return String.class;
                default:
                    throw new IllegalArgumentException("Invalid column " + columnIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return columnIndex == 1 || columnIndex == 2;
        }

        @Override
        public int getColumnCount()
        {
            return 3;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex)
        {
            if (columnIndex == 1)
            {
                // enable/disable
                final MemoryBreakpoint bp = getBreakpoint(rowIndex);
                final boolean newState;
                synchronized (LOCK)
                {
                    newState = ! breakpoints.isEnabled(bp);
                }
                runOnEmulator(emu ->
                {
                    final MemoryBreakpoints bps = emu.memory.breakpoints;
                    final MemoryBreakpoint existing = bps.getBreakpoint(bp.address);
                    if (existing != null)
                    {
                        bps.setEnabled(existing,newState);
                        System.out.println(existing+" is now "+bps.isEnabled(existing) );
                        emu.invokeTickCallback();
                    }
                });
                return;
            }
            else if (columnIndex == 2)
            {
                // expression
                final String newExpr = ((String) aValue).trim().toLowerCase();
                try
                {
                    final MemoryBreakpoint bp = getBreakpoint(rowIndex);
                    if ( bp != null )
                    {
                        int newFlags = 0;
                        if ( newExpr.contains("r") ) {
                            newFlags |= MemoryBreakpoint.ACCESS_READ;
                        }
                        if ( newExpr.contains("w") ) {
                            newFlags |= MemoryBreakpoint.ACCESS_WRITE;
                        }
                        final int finalFlags = newFlags;

                        runOnEmulator(emu ->
                        {
                            final MemoryBreakpoints bps = emu.memory.breakpoints;
                            final MemoryBreakpoint existing = bps.getBreakpoint(bp.address);
                            if ( existing != null ) {
                                boolean isEnabled = bps.isEnabled(existing);
                                final MemoryBreakpoint newBP = existing.withFlags(finalFlags);
                                bps.remove(existing);
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
                case 0:
                    return "Address";
                case 1:
                    return "Enabled?";
                case 2:
                    return "Expression";
                default:
                    throw new IllegalArgumentException("Invalid column "+column);
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            final MemoryBreakpoint bp = getBreakpoint(rowIndex);
            switch (columnIndex)
            {
                case 0:
                    return Misc.hex(bp.address);
                case 1:
                    synchronized (LOCK)
                    {
                        return breakpoints.isEnabled(bp);
                    }
                case 2:

                    if ( bp.accessFlags == MemoryBreakpoint.ACCESS_READ ) {
                        return "READ";
                    }
                    if ( bp.accessFlags == MemoryBreakpoint.ACCESS_WRITE) {
                        return "WRITE";
                    }
                    return "READ|WRITE";
                default:
                    throw new IllegalArgumentException("Invalid column " + columnIndex);
            }
        }

        public MemoryBreakpoint getBreakpoint(int rowIndex)
        {
            final AtomicReference<MemoryBreakpoint> result = new AtomicReference<>();
            final MemoryBreakpoints.IBreakpointVisitor consumer = new MemoryBreakpoints.IBreakpointVisitor()
            {
                private int i = 0;

                @Override
                public boolean visit(MemoryBreakpoint breakpoint)
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

    public MemoryBreakpointsWindow(UI ui)
    {
        super("Memory Breakpoints", ui);

        final TableCellRenderer tableCellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
            {
                Component result =
                        super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );

                final boolean highlight;
                synchronized(LOCK)
                {
                    final MemoryBreakpoint current = tableModel.getBreakpoint( row );
                    highlight = activeBreakpoint != null &&
                            current.address == activeBreakpoint.address;
                }
                if ( highlight ) {
                    setBackground( Color.RED );
                } else {
                    setBackground( MemoryBreakpointsWindow.this.getBackground() );
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
                        final List<MemoryBreakpoint> toDelete =
                                Arrays.stream(rows)
                                        .map( table::convertRowIndexToModel )
                                        .mapToObj( tableModel::getBreakpoint )
                                        .collect( Collectors.toList() );
                        runOnEmulator(emu ->
                        {
                            boolean deleted = false;
                            for ( MemoryBreakpoint copy: toDelete )
                            {
                                final MemoryBreakpoint bp =
                                        emu.memory.breakpoints.getBreakpoint( copy.address );
                                if ( bp != null )
                                {
                                    emu.memory.breakpoints.remove( bp );
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
                existsAlready.set( emu.memory.breakpoints.getBreakpoint( address ) != null );
            });
            if ( ! existsAlready.get() )
            {
                runOnEmulator( emu ->
                {
                    final MemoryBreakpoint breakpoint =
                            new MemoryBreakpoint( address, MemoryBreakpoint.ACCESS_WRITE );
                    emu.memory.breakpoints.add( breakpoint );
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
        return WindowKey.MEMORY_BREAKPOINTS;
    }

    @Override
    public void tick(Emulator emulator)
    {
        final boolean dataChanged;
        final boolean bpChanged;
        synchronized (LOCK)
        {
            final MemoryBreakpoint previouslyActive = activeBreakpoint;
            activeBreakpoint = emulator.memory.breakpoints.lastHit;
            bpChanged = previouslyActive == null ^ activeBreakpoint == null ||
                    previouslyActive != null && previouslyActive.address != activeBreakpoint.address;

            dataChanged = emulator.memory.breakpoints.isDifferent(breakpoints);
            if (dataChanged)
            {
                breakpoints = emulator.memory.breakpoints.createCopy();
            }
        }
        if ( dataChanged || bpChanged )
        {
            repaint();
            tableModel.fireTableDataChanged();
        } else {
            System.out.println("tick(): BPWindow model is still up-to-date");
        }
    }
}