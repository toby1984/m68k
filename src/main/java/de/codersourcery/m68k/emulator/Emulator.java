package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.assembler.arch.CPUType;
import de.codersourcery.m68k.emulator.cpu.CPU;

public class Emulator
{
    private final int romStartAdr = 0xF80000;
    private final int kickRomSize = 512 * 1024; // 512 kB

    private final byte[] kickstartRom;

    private final MMU mmu;
    private final Memory memory;
    private final CPU cpu;

    public Emulator(byte[] kickstartRom)
    {
        // Amiga 500+
        this.kickstartRom = kickstartRom;
        if ( kickstartRom.length != kickRomSize) {
            throw new IllegalArgumentException("Kickstart ROM needs to have "+kickRomSize+" bytes");
        }

        final MMU.PageFaultHandler faultHandler = new MMU.PageFaultHandler();
        this.mmu = new MMU( faultHandler );
        this.memory = new Memory(this.mmu);
        this.cpu = new CPU(CPUType.M68000,memory);
        reset();
    }

    public void reset()
    {
        // reset memory
        mmu.reset();

        // copy kickstart rom into RAM
        memory.bulkWrite(romStartAdr,kickstartRom,0,kickstartRom.length);

        // write-protect kickstart ROM
        mmu.setWriteProtection( romStartAdr,kickRomSize,true);

        // copy first 1 KB from ROM to IRQ vectors starting at 0x00
        memory.bulkWrite(0x000000,kickstartRom,0,1024);

        cpu.reset();
    }
}
