package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;

import javax.swing.*;
import java.awt.*;

public class EmulatorStateWindow extends AppWindow implements Emulator.IEmulatorStateCallback
{
    private final JButton runButton;
    private final JButton stopButton;
    private final JButton stepButton;
    private final JButton resetButton;

    public EmulatorStateWindow(String title, UI ui)
    {
        super( title, ui );

        setLayout( new FlowLayout() );
        runButton = addButton("Run", () -> {
            ui.doWithEmulator( emu -> emu.start() );
        });
        stopButton = addButton("Stop", () -> {
            ui.doWithEmulator( emu -> emu.stop() );
        });
        stepButton = addButton("Step", () -> {
            ui.doWithEmulator( emu -> emu.singleStep() );
        });
        resetButton = addButton("Reset", () -> {
            ui.doWithEmulator( emu -> emu.reset() );
        });
    }

    @Override
    public String getWindowKey()
    {
        return "emulatorcontrol";
    }

    private JButton addButton(String label,Runnable action) {
        final JButton button = new JButton(label);
        button.addActionListener( ev -> action.run() );
        getContentPane().add( button );
        return button;
    }

    @Override
    public void stopped(Emulator emulator)
    {
        runOnEDT( () ->
        {
            runButton.setEnabled( true );
            stepButton.setEnabled( true );
            stopButton.setEnabled( false );
        });
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
        runOnEDT( () ->
        {
            runButton.setEnabled( false );
            stepButton.setEnabled( false );
            stopButton.setEnabled( true );
        });
    }
}