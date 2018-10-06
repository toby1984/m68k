package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.Emulator;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class CIAStateWindow extends AppWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    // @GuardedBy( buffer )
    private final StringBuffer buffer = new StringBuffer("n/a");

    private final JTextArea textArea = new JTextArea();

    public CIAStateWindow(UI ui)
    {
        super("CIA state",ui);
        textArea.setColumns(15);
        textArea.setRows(10);
        getContentPane().add( new JScrollPane( textArea ) );
    }

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.CIA_STATE;
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
    public void tick(Emulator emulator)
    {
        synchronized(buffer)
        {
            buffer.setLength(0);
            buffer.append("CIA A:\n").append( emulator.ciaa.getStateAsString() );
            buffer.append("\n\n");
            buffer.append("CIA B:\n").append( emulator.ciab.getStateAsString() );
        }
        runOnEDT(() ->
        {
            synchronized (buffer)
            {
                textArea.setText(buffer.toString());
            }
        });
    }
}
