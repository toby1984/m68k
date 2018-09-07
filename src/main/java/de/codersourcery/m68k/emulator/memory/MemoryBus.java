package de.codersourcery.m68k.emulator.memory;

public class MemoryBus
{
    /*
     * 227.5 DMA slots per scan line,
     * 226 slots are actually available.
     * 1 DMA slot = 2 CPU cycles
     *
     * 68k needs 2 cycles to set address lines and 2 more cycles to do the data transfer, CPU and
     *
     * Fixed cycle assignments
     *
     *   0 -  3  4 DMA cycles for memory refresh
     *   4 -  6  3 DMA cycles for disk DMA
     *   7 - 10  4 DMA cycles for audio DMA (2 bytes per channel)
     *  11 - 26 16 DMA cycles for sprite DMA (2 words per channel)
     *  27 - 107   80 DMA cycles for bitplane DMA (even- or odd-numbered slots
     *            according to the display size used)
     * 108 - 225  FFA
     */

    /**
     * Possible owners of the cip memory
     * bus.
     */
    public enum Owner
    {
        CPU(1<<0),
        BITPLANE_DMA(1<<1),
        COPPER_DMA(1<<2),
        BLITTER_DMA(1<<3),
        SPRITE_DMA(1<<4),
        DISK_DMA(1<<5),
        AUDIO_DMA(1<<6),
        MEMORY_REFRESH(1<<7);

        public final int bitMask;

        private Owner(int mask) {
            this.bitMask = mask;
        }
    }

    private boolean blitterNice;
    private int cpuWaitCount = 0;

    private int applicantsMask; // bit mask
    private Owner granted;
    private int cyclePtr = 0;

    public void beforeTick()
    {
        applicantsMask = 0;
    }

    public void applyForOwnership(Owner owner) {
        applicantsMask |= owner.bitMask;
    }

    public void grantOwnership()
    {
        Owner newOwner = doGrantOwnership();

        if ( blitterNice && ( applicantsMask & Owner.CPU.bitMask) != 0 && newOwner == Owner.BLITTER_DMA ) { // CPU wants a cycle but blitter got it
            if ( cpuWaitCount == 3 )
            {
                cpuWaitCount = 0;
                newOwner = Owner.CPU;
            }
        }
        granted = newOwner;
    }

    public Owner doGrantOwnership()
    {
        /*
         *   0 -  3  4 DMA cycles for memory refresh
         *   4 -  6  3 DMA cycles for disk DMA
         *   7 - 10  4 DMA cycles for audio DMA (2 bytes per channel)
         *  11 - 26 16 DMA cycles for sprite DMA (2 words per channel)
         *  27 - 107   80 DMA cycles for bitplane DMA (even- or odd-numbered slots
         *            according to the display size used)
         * 108 - 225  FFA
         */
        final int applicantsMask = this.applicantsMask;
        switch( cyclePtr ) {
            case 0: // Memory DMA
            case 1:
            case 2:
            case 3:
                return Owner.MEMORY_REFRESH;
            case 4: // Disk DMA
            case 5:
            case 6:
                if ( (applicantsMask & Owner.DISK_DMA.bitMask) != 0 )
                {
                    return Owner.DISK_DMA;
                }
                break;
            case 7: // Audio DMA
            case 8:
            case 9:
            case 10:
                if ( (applicantsMask & Owner.AUDIO_DMA.bitMask) != 0 )
                {
                    return Owner.AUDIO_DMA;
                }
                break;
                // Sprite DMA
            case 11: case 12: case 13: case 14: case 15: case 16:
            case 17: case 18: case 19: case 20: case 21: case 22:
            case 23: case 24: case 25: case 26:
        }
        if ( (applicantsMask & Owner.BITPLANE_DMA.bitMask) != 0 )
        {
            return Owner.BITPLANE_DMA;
        }
        // DMA cycle is up for grabs, check who wants it
        if ( (applicantsMask & Owner.COPPER_DMA.bitMask) != 0 )
        {
            return Owner.COPPER_DMA;
        }
        if ( (applicantsMask & Owner.BLITTER_DMA.bitMask) != 0 )
        {
            return Owner.BLITTER_DMA;
        }
        if ( (applicantsMask & Owner.CPU.bitMask) != 0 )
        {
            return Owner.CPU;
        }
        return null;
    }

    public boolean isGranted(Owner owner) {
        return granted == owner;
    }

    public void tick()
    {
        cyclePtr = (cyclePtr+1) % 226;
    }

    public void reset() {
        cpuWaitCount = 0;
        granted = null;
        cyclePtr = 0;
    }

    public boolean isBlitterNice()
    {
        return blitterNice;
    }

    public void setBlitterNice(boolean blitterNice)
    {
        this.blitterNice = blitterNice;
    }
}