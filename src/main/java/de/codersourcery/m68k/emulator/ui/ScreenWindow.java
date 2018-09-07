package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;

public class ScreenWindow extends AppWindow implements ITickListener,
        Emulator.IEmulatorStateCallback
{

    private final Object LOCK = new Object();

    private int[] screenData= new int[0];

    public ScreenWindow(String title, UI ui)
    {
        super( title, ui );
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
    public String getWindowKey()
    {
        return "screen";
    }

    @Override
    public void tick(Emulator emulator)
    {
    }
}
