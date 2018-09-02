package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Breakpoint;
import de.codersourcery.m68k.emulator.Breakpoints;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.IBreakpointCondition;
import de.codersourcery.m68k.utils.Misc;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;

public class BreakpointsWindow extends AppWindow implements ITickListener
{
    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private Breakpoints breakpoints = new Breakpoints();

    private final MyAbstractTableModel tableModel = new MyAbstractTableModel();

    private JTable table = new JTable(tableModel);

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
                case 3:
                    return String.class;
                default:
                    throw new IllegalArgumentException("Invalid column " + columnIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return columnIndex == 1 || columnIndex == 3;
        }

        @Override
        public int getColumnCount()
        {
            return 4;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex)
        {
            if (columnIndex == 1)
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
                        System.out.println(existing+" is now "+bps.isEnabled(existing) );
                        emu.invokeTickCallback();
                    }
                });
                return;
            }
            else if (columnIndex == 3)
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
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            final Breakpoint bp = getBreakpoint(rowIndex);
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
                    return bp.condition.toString();
                case 3:
                    return bp.condition.getExpression();
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

        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e)
            {
                if ( e.getKeyCode() == KeyEvent.VK_DELETE )
                {
                    final int idx = table.getSelectedRow();
                    if ( idx != -1 )
                    {
                        final int modelRow = table.convertRowIndexToModel(idx);
                        synchronized (LOCK)
                        {
                            final Breakpoint existing = tableModel.getBreakpoint(modelRow);
                            if (existing != null)
                            {
                                runOnEmulator(emu ->
                                {
                                    final Breakpoint bp = emu.getBreakpoints().getBreakpoint(existing.address);
                                    if (bp != null)
                                    {
                                        emu.getBreakpoints().remove(bp);
                                        emu.invokeTickCallback();
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
        final JScrollPane pane = new JScrollPane(table);

        final GridBagConstraints cnstrs =
            cnstrs(0, 0);
        cnstrs.fill = GridBagConstraints.BOTH;
        getContentPane().setLayout(new GridBagLayout());
        getContentPane().add(pane,cnstrs);
    }

    @Override
    public String getWindowKey()
    {
        return "breakpoints";
    }

    @Override
    public void tick(Emulator emulator)
    {
        final boolean dataChanged;
        synchronized (LOCK)
        {
            dataChanged = emulator.getBreakpoints().isDifferent(breakpoints);
            if (dataChanged)
            {
                breakpoints = emulator.getBreakpoints().createCopy();
            }
        }
        if ( dataChanged )
        {
            System.out.println("tick(): BPWindow now has model with "+tableModel.getRowCount()+" rows");
            repaint();
            tableModel.fireTableDataChanged();
        } else {
            System.out.println("tick(): BPWindow model is still up-to-date");
        }
    }
}