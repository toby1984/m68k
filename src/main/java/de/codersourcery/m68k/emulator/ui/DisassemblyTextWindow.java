package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.disassembler.Disassembler;
import de.codersourcery.m68k.disassembler.LibraryCallResolver;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.ui.AbstractDisassemblyWindow;
import de.codersourcery.m68k.emulator.ui.AppWindow;
import de.codersourcery.m68k.emulator.ui.ITickListener;
import de.codersourcery.m68k.emulator.ui.UI;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DisassemblyTextWindow extends AbstractDisassemblyWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    private JTextField address = new JTextField("$0");
    private JTextArea textfield = new JTextArea();

    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private int startAddress = 0;

    // @GuardedBy( LOCK )
    private boolean addressChanged = true;

    public DisassemblyTextWindow(UI ui)
    {
        super( "Disassembly Text", ui );

        address.setColumns( 8 );
        address.addActionListener( ev -> {

            if ( StringUtils.isNotBlank( address.getText() ) )
            {
                try
                {
                    final IAdrProvider adrProvider = parseExpression( address.getText() );
                    if ( adrProvider != null )
                    {
                        final AtomicInteger newAddress = new AtomicInteger();

                        runOnEmulator(emu -> newAddress.set(adrProvider.getAddress(emu) & ~1));

                        synchronized (LOCK)
                        {
                            if (newAddress.get() != startAddress)
                            {
                                addressChanged = true;
                                startAddress = newAddress.get();
                            }
                        }
                    }
                }
                catch(Exception e) {
                    error(e);
                    return;
                }
                runOnEmulator( this::tick );
            }
        });

        // add stuff to layout
        getContentPane().setLayout( new GridBagLayout() );

        GridBagConstraints cnstrs = cnstrsNoResize( 0, 0 );
        cnstrs.weightx = 1;
        cnstrs.fill=GridBagConstraints.HORIZONTAL;
        getContentPane().add( address , cnstrs );

        cnstrs = cnstrsNoResize( 0, 1 );
        cnstrs.weightx = 1; cnstrs.weighty = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        getContentPane().add( new JScrollPane(textfield), cnstrs );
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
    public WindowKey getWindowKey()
    {
        return WindowKey.DISASSEMBLY_TEXT;
    }

    @Override
    public void tick(Emulator emulator)
    {
        final int adr;
        synchronized (LOCK)
        {
            if ( ! addressChanged ) {
                return;
            }
            adr = startAddress;
        }

        final Disassembler disasm = new Disassembler( emulator.memory );
        disasm.setResolveRelativeOffsets( true );
        disasm.setDumpHex( false );
        disasm.setIndirectCallResolver( proxyCallResolver );
        disasm.setChipRegisterResolver( proxyRegisterResolver );
        disasm.setVerboseRegisterDescriptions(true);

        final String newText = disasm.disassemble( adr, 1024 );
        runOnEDT( () ->
        {
            textfield.setText(newText);
            textfield.setCaretPosition(0);
        });
    }
}