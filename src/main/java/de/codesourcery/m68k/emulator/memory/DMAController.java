package de.codesourcery.m68k.emulator.memory;

public class DMAController
{
    /*
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
    public int flags;

    public void reset() {
        flags = 0;
    }

    public boolean isDMAEnabled() {
        return (flags&1<<9) != 0;
    }

    public String toString()
    {
        final StringBuilder enabled = new StringBuilder("DMA: ");
        enabled.append( isDMAEnabled() ? "global" : "");
        enabled.append( isBitplaneDMAEnabled() ? ",bitplane" : "");
        enabled.append( isCopperDMAEnabled() ? ",copper" : "");
        enabled.append( isBlitterDMAEnabled( ) ? ",blitter" : "");
        enabled.append( isSpriteDMAEnabled() ? ",sprite" : "");
        enabled.append( isDiskDMAEnabled() ? ",disk" : "");
        enabled.append( isAudio3DMAEnabled() ? ",audio3" : "");
        enabled.append( isAudio2DMAEnabled() ? ",audio2" : "");
        enabled.append( isAudio1DMAEnabled() ? ",audio1" : "");
        enabled.append( isAudio0DMAEnabled() ? ",audio0" : "");
        return enabled.toString();
    }

    public boolean isBitplaneDMAEnabled() {
        return (flags & 0b1100000000) == 0b1100000000;
    }

    public boolean isCopperDMAEnabled() {
        return (flags & 0b1010000000) == 0b1010000000;
    }

    public boolean isBlitterDMAEnabled() {
        return (flags & 0b1001000000) == 0b1001000000;
    }

    public boolean isSpriteDMAEnabled() {
        return (flags & 0b1000100000) == 0b1000100000;
    }

    public boolean isDiskDMAEnabled() {
        return (flags & 0b1000010000) == 0b1000010000;
    }

    public boolean isAudio3DMAEnabled() {
        return (flags & 0b1000001000) == 0b1000001000;
    }

    public boolean isAudio2DMAEnabled() {
        return (flags & 0b1000000100) == 0b1000000100;
    }

    public boolean isAudio1DMAEnabled() {
        return (flags & 0b1000000010) == 0b1000000010;
    }

    public boolean isAudio0DMAEnabled() {
        return (flags & 0b1000000001) == 0b1000000001;
    }
}