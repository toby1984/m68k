package de.codersourcery.m68k.emulator.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Abstract base class for application windows.
 *
 * Windows that need access to emulator state or
 * want to be notified about emulator state changes
 * can implement {@link ITickListener} and/or
 * {@link de.codersourcery.m68k.emulator.Emulator.IEmulatorStateCallback}
 * and the {@link UI} will take care of forwarding those calls
 * to the interested windows.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AppWindow extends JInternalFrame
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

    protected final void error(Throwable cause) {
        ui.error(cause);
    }

    protected final void runOnEDT(Runnable r) {
        if ( SwingUtilities.isEventDispatchThread() ) {
            r.run();
        } else {
            SwingUtilities.invokeLater( r );
        }
    }

    public abstract String getWindowKey();

    public void applyWindowState(WindowState state)
    {
        if ( ! getWindowKey().equals(state.getWindowKey()) ) {
            throw new IllegalArgumentException("Window state belongs to "+state.getWindowKey()+" but this is "+getWindowKey());
        }
        // TODO: setEnabled() not honored here
        setVisible(state.isVisible());
        setBounds( state.getLocationAndSize() );
    }

    public WindowState getWindowState()
    {
        final WindowState state = new WindowState();
        state.setLocationAndSize( getBounds() );
        state.setEnabled( true );
        state.setVisible( isVisible() );
        state.setWindowKey( getWindowKey() );
        return state;
    }
}