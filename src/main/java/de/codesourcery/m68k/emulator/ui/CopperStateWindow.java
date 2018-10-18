package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.disassembler.ChipRegisterResolver;
import de.codesourcery.m68k.disassembler.RegisterDescription;
import de.codesourcery.m68k.emulator.Emulator;
import de.codesourcery.m68k.emulator.memory.Video;
import de.codesourcery.m68k.utils.Misc;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Font;

public class CopperStateWindow extends AppWindow implements
        ITickListener, Emulator.IEmulatorStateCallback
{
    private final JTextArea textArea = new JTextArea("n/a");
    private final ChipRegisterResolver resolver =
            new ChipRegisterResolver( null );

    public CopperStateWindow(UI ui)
    {
        super( "Copper State", ui );

        textArea.setRows( 20 );
        textArea.setColumns( 40 );
        textArea.setFont( new Font(Font.MONOSPACED,Font.PLAIN,12 ) );
        getContentPane().add( new JScrollPane( textArea ) );
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
        tick(emulator);
    }

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.COPPER_STATE;
    }

    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void tick(Emulator emulator)
    {
        buffer.setLength( 0 );
        final Video.Copper copper = emulator.video.copper;

        buffer.append("Copper DMA enabled: ").append( emulator.dmaController.isCopperDMAEnabled() ).append("\n\n");

        buffer.append("Active list: ").append( copper.list1Active ? "#1" : "#2" ).append("\n");
        buffer.append("PC @ ").append( Misc.hex( copper.pc ) ).append("\n\n");
        buffer.append("COPLST1 = ").append( Misc.hex( copper.list1Addr ) ).append("\n\n");
        buffer.append("COPLST2 = ").append( Misc.hex( copper.list2Addr ) ).append("\n\n");

        disassemble( copper.list1Addr, 20 , emulator,copper.pc);
        buffer.append("\n\n===================\n\n");

        buffer.append("COPLST2 = ").append( Misc.hex( copper.list2Addr ) ).append("\n\n");
        disassemble( copper.list2Addr, 20 , emulator,copper.pc);

        final String text = buffer.toString();
        runOnEDT( () ->
        {
            textArea.setText( text );
            textArea.setCaretPosition( 0 );
        } );
    }

    private void disassemble(int startAddress,int instructionCount,Emulator emulator,int currentInsnAdr)
    {
        for (int i = instructionCount, pc = startAddress & ~1 ; i >0 ; i--, pc += 4)
        {
            final int words = emulator.memory.readLong( pc );
            final int word1 = words >>> 16;
            final int word2 = words & 0xffff;

            if ( pc == currentInsnAdr ) {
                buffer.append(">> ");
            } else {
                buffer.append("   ");
            }

            // append address
            final String adrString = Integer.toHexString( pc );
            for ( int padding = 8 - adrString.length() ; padding > 0 ; padding-- ) {
                buffer.append('0');
            }
            buffer.append( adrString ).append(": ");

            // append hex dump
            int hi = (words & 0xf0000000) >>> 28;
            int lo = (words & 0x0f000000) >>> 24;
            buffer.append( Misc.HEX_CHARS[hi]).append( Misc.HEX_CHARS[lo] );
            hi = (words & 0x00f00000) >>> 20;
            lo = (words & 0x000f0000) >>> 16;
            buffer.append( Misc.HEX_CHARS[hi]).append( Misc.HEX_CHARS[lo] ).append(" ");

            hi = (words & 0x0000f000) >>> 12;
            lo = (words & 0x00000f00) >>>  8;
            buffer.append( Misc.HEX_CHARS[hi]).append( Misc.HEX_CHARS[lo] );

            hi = (words & 0x000000f0) >>> 4;
            lo = (words & 0x0000000f);
            buffer.append( Misc.HEX_CHARS[hi]).append( Misc.HEX_CHARS[lo] ).append(" ");

            // disassemble instruction
            if ( (word1 & 1) == 0 )
            {
                /*
                 * MOVE instruction.
                 *
                 * FIRST MOVE INSTRUCTION WORD (IR1)
                 * ---------------------------------
                 * Bit 0           Always set to 0.
                 * Bits 8 - 1      Register destination address (DA8-1).
                 * Bits 15 - 9     Not used, but should be set to 0.
                 *
                 * SECOND MOVE INSTRUCTION WORD (IR2)
                 * ----------------------------------
                 * Bits 15 - 0     16 bits of data to be transferred (moved) to the register destination.
                 */
                final int adr = 0xdff000 + (word1 & 0b111111110);
                buffer.append( "MOVE #" + Misc.hex( word2 ) ).append( ", " );
                final RegisterDescription resolved = resolver.resolve( adr );
                if ( resolved != null ) {
                    buffer.append( resolved.name ).append(" (").append( Misc.hex(adr) ).append(")");
                } else {
                    buffer.append( Misc.hex(adr) );
                }
            }
            else if ( (word2 & 1) == 0 )
            {
                /*
                 * WAIT instruction.
                 *
                 * FIRST WAIT INSTRUCTION WORD (IR1)
                 * ---------------------------------
                 * Bit 0           Always set to 1.
                 * Bits 15 - 8      Vertical beam position  (called VP).
                 * Bits 7 - 1       Horizontal beam position  (called HP).
                 *
                 * SECOND WAIT INSTRUCTION WORD (IR2)
                 * ----------------------------------
                 * Bit 0           Always set to 0.
                 * Bit 15          The  blitter-finished-disable bit .  Normally, this bit is a 1. (See the "Advanced Topics" section below.)
                 * Bits 14 - 8     Vertical position compare enable bits (called VE).
                 * Bits 7 - 1      Horizontal position compare enable bits (called HE).
                 */
                moveOrSkip( "WAIT", word1, word2 );
            }
            else
            {
                /*
                 * FIRST SKIP INSTRUCTION WORD (IR1)
                 * ---------------------------------
                 * Bit 0           Always set to 1.
                 * Bits 15 - 8     Vertical position  (called VP).
                 * Bits 7 - 1      Horizontal position  (called HP).
                 *
                 *                 Skip if the beam counter is equal to or
                 *                 greater than these combined bits
                 *                 (bits 15 through 1).
                 *
                 *
                 * SECOND SKIP INSTRUCTION WORD (IR2)
                 * ----------------------------------
                 * Bit 0           Always set to 1.
                 * Bit 15          The  blitter-finished-disable bit . (See "Using the Copper with the Blitter" below.)
                 * Bits 14 - 8     Vertical position compare enable bits (called VE).
                 * Bits 7 - 1      Horizontal position compare enable bits (called HE).
                 */
                moveOrSkip( "SKIP", word1, word2 );
            }
            buffer.append("\n");
        }
    }

    private void moveOrSkip(String cmd,int word1, int word2)
    {
        int vpos = word1 >>> 8;
        int hpos = word1 & 0b11111110;
        final int vmask = word2 >>> 8;
        final int hmask = word2 & 0b11111110;
        vpos &= vmask;
        hpos &= hmask;

        final String blitWait = (word2 & 1<<15) == 0 ? "blitDone && " : "";
        buffer.append( cmd ).append(" ").append( blitWait )
                .append("vpos >= " )
                .append( vpos ).append(" && hpos >= ")
                .append( hpos )
                .append(" ; vmask=" )
                .append(Integer.toBinaryString( vmask ) )
                .append(" ; hmask=" )
                .append(Integer.toBinaryString( hmask ) );
    }
}