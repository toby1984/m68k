package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;

import javax.swing.*;
import java.awt.*;

public class EmulatorStateWindow extends AppWindow
{
    private JButton runButton;
    private JButton stopButton;
    private JButton stepButton;
    private JButton resetButton;

    public EmulatorStateWindow(String title, UI ui)
    {
        super( title, ui );

        setLayout( new FlowLayout() );
        JButton runButton = addButton("Run", () -> {
            ui.doWithEmulator( emu -> emu.start() );
        });
        JButton stopButton = addButton("Stop", () -> {
            ui.doWithEmulator( emu -> emu.stop() );
        });
        JButton stepButton = addButton("Step", () -> {
            ui.doWithEmulator( emu -> emu.singleStep() );
        });
        JButton resetButton = addButton("Reset", () -> {
            ui.doWithEmulator( emu -> emu.reset() );
        });
    }

    private JButton addButton(String label,Runnable action) {
        final JButton button = new JButton(label);
        button.addActionListener( ev -> action.run() );
        getContentPane().add( button );
        return button;
    }

    @Override
    protected void internalTick(Emulator emulator)
    {

    }

    @Override
    public void stopped()
    {
        runButton.setEnabled( true );
        stepButton.setEnabled( true );
        stopButton.setEnabled( false );
    }

    @Override
    public void enteredContinousMode()
    {
        runButton.setEnabled( false );
        stepButton.setEnabled( false );
        stopButton.setEnabled( true );
    }
}
