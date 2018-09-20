package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Breakpoint;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.IBreakpointCondition;
import de.codersourcery.m68k.utils.Misc;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class EmulatorStateWindow extends AppWindow implements Emulator.IEmulatorStateCallback
{
    private final JButton runButton;
    private final JButton stopButton;
    private final JButton stepButton;
    private final JButton resetButton;
    private final JButton stepOverButton;

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
                System.out.println("*** Installing temporary breakpoint at "+ Misc.hex(bpAdr));
                emu.getBreakpoints().add( new Breakpoint( bpAdr, true, IBreakpointCondition.TRUE ) );
                emu.start();
            });
        });
        stepOverButton.setEnabled(false);
        resetButton = addButton("Reset", () -> {
            ui.doWithEmulator( emu -> emu.reset() );
        });
        stopButton.setEnabled(false);

        registerKeyReleasedListener( event ->
        {
            if ( event.getKeyCode() == KeyEvent.VK_F7 ) {
                ui.doWithEmulator( emu -> emu.singleStep() );
            }
            else if ( event.getKeyCode() == KeyEvent.VK_F9 )
            {
                ui.doWithEmulator( emu -> emu.start() );
            }
        });
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