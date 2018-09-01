package de.codersourcery.m68k.emulator.memory;

public class MemoryBus
{
/*

                 BIT#  FUNCTION    DESCRIPTION
                 ----  ---------   -----------------------------------
                 15    SET/CLR     Set/clear control bit. Determines
                                   if bits written with a 1 get set or
                                   cleared.  Bits written with a zero
                                   are unchanged.
                 14    BBUSY       Blitter busy status bit (read only)
                 13    BZERO       Blitter logic  zero status bit
                                   (read only).
                 12    X
                 11    X
                 10    BLTPRI      Blitter DMA priority
                                   (over CPU micro) (also called
                                   "blitter nasty") (disables /BLS
                                   pin, preventing micro from
                                   stealing any bus cycles while
                                   blitter DMA is running).
                 09    DMAEN       Enable all DMA below
                 08    BPLEN       Bitplane DMA enable
                 07    COPEN       Copper DMA enable
                 06    BLTEN       Blitter DMA enable
                 05    SPREN       Sprite DMA enable
                 04    DSKEN       Disk DMA enable
                 03    AUD3EN      Audio channel 3 DMA enable
                 02    AUD2EN      Audio channel 2 DMA enable
                 01    AUD1EN      Audio channel 1 DMA enable
                 00    AUD0EN      Audio channel 0 DMA enable
 */
    public enum Owner
    {
        BITPLANE_DMA,
        COPPER_DMA,
        BLITTER_DMA,
        SPRITE_DMA,
        DISK_DMA,
        AUDIO0_DMA,
        AUDIO1_DMA,
        AUDIO2_DMA,
        AUDIO3_DMA,
    }

    private int cyclesOccupied;
    private Owner owner;

    public Owner getOwner() {
        return owner;
    }

    public boolean hasOwner(Owner o) {
        return this.owner == o;
    }

    public boolean isFree() {
        return owner == null;
    }

    public boolean isOccupied()
    {
        return owner != null;
    }

    public boolean occupy(Owner owner)
    {
        this.owner = owner;
    }

    public void tick()
    {
        if (cyclesOccupied > 0 )
        {
            cyclesOccupied--;
            if ( cyclesOccupied == 0 ) {
                owner = null;
            }
        }
    }
}
