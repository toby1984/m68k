package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.CPU;
import de.codesourcery.m68k.emulator.Emulator;
import de.codesourcery.m68k.utils.Misc;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class BacktraceWindow extends AppWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    private final JList<Integer> list = new JList<>();

    private final int[] backtrace = new int[CPU.MAX_BACKTRACE_SIZE];
    private int hashCode;

    private final MyListModel listModel = new MyListModel();

    private static final class MyListModel implements ListModel<Integer>
    {
        private final List<ListDataListener> listeners = new ArrayList<>();
        private final AtomicReference<List<Integer>> addresses = new AtomicReference<>( new ArrayList<>() );

        public void setData(List<Integer> addresses)
        {
            this.addresses.set( addresses );
            final ListDataEvent ev = new ListDataEvent(this,ListDataEvent.CONTENTS_CHANGED,0,
                this.addresses.get().size());
            listeners.forEach(l -> l.contentsChanged( ev ));
        }

        @Override
        public int getSize()
        {
            return addresses.get().size();
        }

        @Override
        public Integer getElementAt(int index)
        {
            return addresses.get().get(index);
        }

        @Override
        public void addListDataListener(ListDataListener l)
        {
            listeners.add(l);
        }

        @Override
        public void removeListDataListener(ListDataListener l)
        {
            listeners.remove(l);
        }
    }

    public BacktraceWindow(UI ui)
    {
        super("Backtrace",ui);

        attachKeyListeners(list);
        list.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                final Component  result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText( Misc.hex( (Integer) value ) );
                return result;
            }
        });
        list.setModel( listModel );

        list.addListSelectionListener(new ListSelectionListener()
        {
            @Override
            public void valueChanged(ListSelectionEvent e)
            {
                if ( ! e.getValueIsAdjusting() ) {
                    final int idx = e.getFirstIndex();
                    final Integer address = listModel.getElementAt(idx);
                    ui.getWindow(WindowKey.ROM_LISTING).ifPresent( window ->
                    {
                        ((ROMListingViewer) window).showAddress(address);
                    });
                    ui.getWindow(WindowKey.DISASSEMBLY).ifPresent( window ->
                    {
                        ((DisassemblyWindow) window).setAddressProvider( new FixedAdrProvider(address) );
                    });
                }
            }
        });
        getContentPane().add( new JScrollPane(list ) );
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
    }

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.BACKTRACE;
    }

    @Override
    public void tick(Emulator emulator)
    {
        final int backtraceLength = emulator.cpu.getBackTrace( backtrace );
        int newHash = 0;
        for ( int i = 0 ; i < backtraceLength ; i++ )
        {
            newHash += 31*newHash+(i+1)*31*backtrace[i];
        }
        if ( hashCode != newHash )
        {
            hashCode = newHash;

            final List<Integer> addresses = new ArrayList<>(backtraceLength);
            for ( int i = 0 ; i < backtraceLength ; i++ ) {
                addresses.add( backtrace[backtraceLength-i-1] );
            }
            runOnEDT(() -> listModel.setData(addresses) );
        }
    }
}
