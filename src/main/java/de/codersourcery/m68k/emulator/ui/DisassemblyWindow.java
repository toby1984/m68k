package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.disassembler.Disassembler;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.utils.Misc;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DisassemblyWindow extends AppWindow
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
    private final List<Disassembler.Line> lines = new ArrayList<>();

    private JPanel panel = new JPanel()
    {
        {
            setFont( new Font(Font.MONOSPACED,Font.PLAIN,14 ) );
        }

        private void drawString(Graphics g, FontMetrics metrics, String text, Rectangle rect)
        {
            final int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
            g.drawString(text, rect.x, y);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);

            final FontMetrics metrics = g.getFontMetrics();
            final int lineHeight = (int) (metrics.getHeight()*1.2f);

            synchronized ( LOCK )
            {
                maxLines = getHeight() / lineHeight;

                final Rectangle rect = new Rectangle();
                rect.x = 1;
                rect.height = lineHeight;

                for (int y = lineHeight, i = 0; i < lines.size(); i++, y += lineHeight)
                {
                    rect.y = y;

                    final Disassembler.Line line = lines.get(i);
                    final String prefix = addressToDisplay == line.pc ? ">>>" : "   ";
                    drawString(g, metrics, prefix+line.toString(), rect);
                }
            }
        }
    };

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
                synchronized(LOCK)
                {
                    followProgramCounter = false;
                    addressToDisplay = toDisplay;
                }
                refresh();
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
            refresh();
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
            refresh();
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
    protected void internalTick(Emulator emulator)
    {
        final Disassembler disasm = new Disassembler(emulator.memory);
        disasm.setDumpHex(true);

        int start;
        synchronized( LOCK )
        {
            if (followProgramCounter)
            {
                start = emulator.cpu.pc;
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
        System.out.println("Disassembling "+maxLines+" lines starting at "+ Misc.hex(start));
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
        disasm.disassemble(start, lineConsumer);
        synchronized( LOCK )
        {
            lines.clear();
            lines.addAll(result);
        }
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
}
