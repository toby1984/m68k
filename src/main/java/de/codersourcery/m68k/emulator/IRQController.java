package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.emulator.cpu.CPU;

public class IRQController
{
    private final CPU cpu;

    public IRQController(CPU cpu) {
        this.cpu = cpu;
    }

    public void externalInterrupt(CIA8520 cia)
    {
        /* CIAA can generate INT2.
         * CIAB can generate INT6.
         */
        switch(cia.name)
        {
            case CIAA:
                cpu.externalInterrupt(2);
                break;
            case CIAB:
                cpu.externalInterrupt(6);
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }
    }
}
