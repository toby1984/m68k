package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;

import javax.swing.*;
import java.awt.*;

public abstract class AppWindow extends JInternalFrame implements Emulator.IEmulatorStateCallback
{
    protected final UI ui;

    public AppWindow(String title,UI ui)
    {
        super(title);
        this.ui = ui;
        setPreferredSize(new Dimension(320,200));
        setResizable(true);
        setMaximizable(true);
        setFocusable(true);
    }

    public final void tick(Emulator emulator)
    {
        try
        {
            internalTick(emulator);
        }
        finally {
            repaint();
        }
    }

    protected final GridBagConstraints cnstrs(int x,int y)
    {
        final GridBagConstraints result = new GridBagConstraints();
        result.gridx = x; result.gridy = y;
        result.weightx = 1; result.weighty = 1;
        result.insets = new Insets(1,1,1,1);
        result.fill = GridBagConstraints.BOTH;
        return result;
    }

    protected final GridBagConstraints cnstrsNoResize(int x,int y)
    {
        final GridBagConstraints result = cnstrs(x,y);
        result.weightx = 0; result.weighty = 0;
        result.fill = GridBagConstraints.NONE;
        return result;
    }

    protected abstract void internalTick(Emulator emulator);

    protected final void error(Throwable cause) {
        ui.error(cause);
    }

    protected final void refresh()
    {
        ui.doWithEmulator(this::tick);
    }

    @Override
    public void stopped()
    {

    }

    @Override
    public void singleStepFinished()
    {

    }

    @Override
    public void enteredContinousMode()
    {

    }
}