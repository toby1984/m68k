package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.Emulator;

/**
 * Called periodically while the {@link Emulator}
 * is running in continous (not single-step) mode.
 *
 * @see Emulator#setCallbackInvocationTicks(int)
 * @see Emulator#setTickCallback(Emulator.ITickCallback)
 */
public interface ITickListener
{
    /**
     * Called periodically every {@link Emulator#setCallbackInvocationTicks(int) ticks}.
     *
     * Note that this method gets called directly by the emulator thread
     * so its safe to manipulate/inspect the emulator's thread
     * from inside this method.
     * Also note that this method needs to finish as quickly as possible as
     * to not slow down the emulation unnecessarily.
     *
     * @param emulator
     */
    void tick(Emulator emulator);
}
