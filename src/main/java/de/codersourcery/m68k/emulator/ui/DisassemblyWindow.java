package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.disassembler.Disassembler;
import de.codersourcery.m68k.emulator.Breakpoint;
import de.codersourcery.m68k.emulator.Breakpoints;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.utils.Misc;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class DisassemblyWindow extends AppWindow
        implements ITickListener, Emulator.IEmulatorStateCallback
{
    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private int addressToDisplay;

    private final JTextField addressTextfield = new JTextField("0000");

    // @GuardedBy( LOCK )
    private boolean followProgramCounter = true;

    // @GuardedBy( LOCK )
    private int maxLines = -1;

    // @GuardedBy( LOCK )
    private Breakpoints breakpoints = new Breakpoints();

    // @GuardedBy( LOCK )
    private final List<Disassembler.Line> lines = new ArrayList<>();

    {
        addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentShown(ComponentEvent e)
            {
                synchronized(LOCK) {
                    updateMaxLines();
                }
            }
        });
    }

    private JPanel panel = new JPanel()
    {
        {
            setFont( new Font(Font.MONOSPACED,Font.PLAIN,14 ) );
            setFocusable(true);
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if ( e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1 )
                    {
                        boolean gotMatch = false;
                        int matchAdr = -1;
                        synchronized(LOCK)
                        {
                            for (int i = 0; i < lines.size(); i++)
                            {
                                Disassembler.Line l = lines.get(i);
                                if ( ((Rectangle) l.data).contains(e.getX(),e.getY()) )
                                {
                                    gotMatch = true;
                                    matchAdr = l.pc;
                                }
                            }
                        }
                        if ( gotMatch )
                        {
                            final int finalAdr = matchAdr;
                            runOnEmulator(emulator ->
                            {
                                final Breakpoints emBp = emulator.getBreakpoints();
                                Breakpoint existing = emBp.getBreakpoint(finalAdr);
                                if ( existing == null ) {
                                    emBp.add(new Breakpoint(finalAdr));
                                } else {
                                    emBp.remove(existing);
                                }
                                System.out.println("BREAKPOINTS: "+emBp);
                                tick(emulator);
                            });
                        }
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);

            final FontMetrics metrics = g.getFontMetrics();
            final int lineHeight = (int) (metrics.getHeight()*1.2f);

            synchronized ( LOCK )
            {
                System.out.println("Disassembly paint(): Rendering "+lines.size()+" lines");
                updateMaxLines();
                if ( lines.size() > 0 ) {
                    System.out.println("First line starts at "+Misc.hex(lines.get(0).pc));
                }

                final Rectangle rect = new Rectangle();
                rect.x = 1;
                rect.height = lineHeight;

                for (int y = lineHeight, i = 0; i < lines.size(); i++, y += lineHeight)
                {
                    rect.y = y;

                    final Disassembler.Line line = lines.get(i);
                    final Breakpoint bp = breakpoints.getBreakpoint(line.pc);
                    final String prefix;
                    if ( bp != null ) {
                        if ( breakpoints.isEnabled(bp ) ) {
                            prefix = addressToDisplay == line.pc ? ">>>[B]" : "   [B]";
                        } else {
                            prefix = addressToDisplay == line.pc ? ">>>[ ]" : "   [ ]";
                        }
                    }
                    else
                    {
                        prefix = addressToDisplay == line.pc ? ">>>   " : "      ";
                    }
                    final int y1 = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
                    line.data = new Rectangle(rect.x,rect.y, 50,lineHeight );
                    g.drawString(prefix+line.toString(), rect.x, y1);
                }
            }
        }
    };

    private void updateMaxLines()
    {
        final FontMetrics metrics = getFontMetrics(getFont());
        final int lineHeight = (int) (metrics.getHeight()*2f);
        synchronized ( LOCK )
        {
            maxLines = getHeight() / lineHeight;
            System.out.println("maxLines : "+maxLines);
        }
    }

    private JButton button(String label,Runnable action) {
        final JButton button = new JButton(label);
        button.addActionListener(ev -> action.run() );
        return button;
    }

    public DisassemblyWindow(UI ui)
    {
        super("Disassembly",ui);

        final JToolBar toolbar = new JToolBar(JToolBar.HORIZONTAL );

        addressTextfield.addActionListener( ev -> {

            try
            {
                final int toDisplay = Integer.parseInt(addressTextfield.getText().toLowerCase(), 16);
                System.out.println("No longer following PC, disassembling @ "+Misc.hex(toDisplay) );
                synchronized(LOCK)
                {
                    followProgramCounter = false;
                    addressToDisplay = toDisplay;
                }
                ui.doWithEmulator(this::tick);
            }
            catch(Exception e)
            {
                error(e);
            }
        });

        // Page up
        toolbar.add( button("Up" , ()->
        {
            System.out.println("Up clicked");
            synchronized(LOCK)
            {
                if ( lines.size() > 0 )
                {
                    followProgramCounter = false;
                    addressToDisplay = lines.get(0).pc;
                }
            }
            ui.doWithEmulator(this::tick);
        }));

        // Page down
        toolbar.add( button("Down" , ()->
        {
            synchronized(LOCK)
            {
                if ( lines.size() > 0 )
                {
                    followProgramCounter = false;
                    addressToDisplay = lines.get( lines.size()-1 ).pc;
                }
            }
            ui.doWithEmulator(this::tick);
        }));

        addressTextfield.setColumns( 8 );
        toolbar.add( addressTextfield );

        getContentPane().setLayout( new GridBagLayout() );
        GridBagConstraints cnstrs = cnstrs(0,0);
        cnstrs.weighty = 0;
        cnstrs.fill=GridBagConstraints.HORIZONTAL;
        getContentPane().add( toolbar, cnstrs );
        cnstrs = cnstrs(0,1);
        cnstrs.weighty = 1;
        getContentPane().add( panel, cnstrs);
    }

    @Override
    public void tick(Emulator emulator)
    {
        final Disassembler disasm = new Disassembler(emulator.memory);
        disasm.setDumpHex(true);
        disasm.setResolveRelativeOffsets(true);

        int start;
        synchronized( LOCK )
        {
            if ( emulator.getBreakpoints().hasChanged )
            {
                breakpoints = emulator.getBreakpoints().createCopy();
                emulator.getBreakpoints().hasChanged = false;
            }
            if (followProgramCounter)
            {
                start = emulator.cpu.pc;
                System.out.println("Disassembly tick: Following PC @ "+Misc.hex(start));
                addressToDisplay = start;
            }
            else
            {
                start = addressToDisplay;
            }
            // make sure we always start with an even address,otherwise memory.readWord()/readLong() would crash
            start = start & 0xfffffffe;
            start = start - maxLines * 2;
        }
        final List<Disassembler.Line> result = new ArrayList<>();
        final Disassembler.LineConsumer lineConsumer = new Disassembler.LineConsumer()
        {
            @Override
            public boolean stop(int pc)
            {
                return result.size() >= maxLines;
            }

            @Override
            public void consume(Disassembler.Line line)
            {
                result.add(line.createCopy());
            }
        };
        System.out.println("Starting to disassemble @ "+Misc.hex(start));
        disasm.disassemble(start, lineConsumer);
        synchronized( LOCK )
        {
            lines.clear();
            lines.addAll(result);
        }
        repaint();

        SwingUtilities.invokeLater(() ->
        {
            boolean updateTextField = false;
            int address = 0;
            synchronized( LOCK )
            {
                if( followProgramCounter ) {
                    updateTextField = true;
                    address = addressToDisplay;
                }
            }
            if ( updateTextField )
            {
                addressTextfield.setText( Integer.toHexString(address) );
            }
        });
    }

    @Override
    public void stopped(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
        synchronized (LOCK) {
            System.out.println("Single step finished @ "+Misc.hex(emulator.cpu.pc));
            followProgramCounter=true;
        }
        tick(emulator);
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public String getWindowKey()
    {
        return "disassembly";
    }
}
