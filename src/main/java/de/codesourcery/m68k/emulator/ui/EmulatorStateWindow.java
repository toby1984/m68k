package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.Breakpoint;
import de.codesourcery.m68k.emulator.Emulator;
import de.codesourcery.m68k.emulator.IBreakpointCondition;
import de.codesourcery.m68k.utils.Misc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class EmulatorStateWindow extends AppWindow implements Emulator.IEmulatorStateCallback
{
    private static final Logger LOG = LogManager.getLogger( EmulatorStateWindow.class.getName() );

    private final JButton runButton;
    private final JButton stopButton;
    private final JButton stepButton;
    private final JButton resetButton;
    private final JButton stepOverButton;

    private final Consumer<KeyEvent> keyAdapter = event ->
    {
        if (event.getKeyCode() == KeyEvent.VK_F7)
        {
            runOnEmulator(Emulator::singleStep);
        }
        else if (event.getKeyCode() == KeyEvent.VK_F9)
        {
            runOnEmulator(Emulator::start);
        }
    };

    public EmulatorStateWindow(UI ui)
    {
        super( "Emulator", ui );

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
        stepOverButton = addButton("Step over", () ->
        {
            ui.doWithEmulator( emu ->
            {
                emu.getBreakpoints().removeAllTemporaryBreakpoints();
                final int size = emu.cpu.getBranchInstructionSizeInBytes();
                final int bpAdr = emu.cpu.pc + size;
                LOG.info( "*** Installing temporary breakpoint at "+ Misc.hex(bpAdr) );
                emu.getBreakpoints().add( new Breakpoint( bpAdr, true, "(temporary)", IBreakpointCondition.TRUE ) );
                emu.start();
            });
        });
        stepOverButton.setEnabled(false);
        resetButton = addButton("Reset", () -> {
            ui.doWithEmulator( emu -> emu.reset() );
        });
        stopButton.setEnabled(false);
        registerKeyReleasedListener(keyAdapter);
    }

    @Override
    protected void windowClosed()
    {
        unregisterKeyReleasedListener(keyAdapter );
    }

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.EMULATOR_STATE;
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
        final boolean isAtBranchInstruction = emulator.cpu.isAtBranchInstruction();
        runOnEDT( () ->
        {
            runButton.setEnabled( true );
            stepButton.setEnabled( true );
            stopButton.setEnabled( false );
            stepOverButton.setEnabled( isAtBranchInstruction );
        });
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
        final boolean isAtBranchInstruction = emulator.cpu.isAtBranchInstruction();
        if ( ! isAtBranchInstruction ) {
            emulator.getBreakpoints().removeAllTemporaryBreakpoints();
        }
        runOnEDT( () -> stepOverButton.setEnabled( isAtBranchInstruction ) );
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
        runOnEDT( () ->
        {
            runButton.setEnabled( false );
            stepButton.setEnabled( false );
            stopButton.setEnabled( true );
            stepOverButton.setEnabled( false );
        });
    }
}