package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;
import org.apache.commons.lang3.StringUtils;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryViewWindow extends AppWindow implements Emulator.IEmulatorStateCallback,
        ITickListener
{
    private static final int BYTES_PER_ROW = 16; // TODO: Hard-coded in Memory#hexdump

    private static final Pattern ADR_PATTERN = Pattern.compile("a([0-7]{1})",Pattern.CASE_INSENSITIVE);
    private final KeyListener keyAdapter = new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e)
        {
            int address;
            IAdrProvider newProvider = null;
            synchronized (LOCK)
            {
                if ( ! adrProvider.isFixedAddress() )
                {
                    return;
                }
                address = adrProvider.getAddress( null );
            }
            if ( e.getKeyCode() == KeyEvent.VK_PAGE_UP ) {
                newProvider = new FixedAdrProvider(  address - bytesToDump/2 );
            }
            else if ( e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ) {
                newProvider = new FixedAdrProvider( address + bytesToDump/2 );
            }
            else if ( e.getKeyCode() == KeyEvent.VK_UP) {
                newProvider = new FixedAdrProvider(address - BYTES_PER_ROW);
            }
            else if ( e.getKeyCode() == KeyEvent.VK_DOWN) {
                newProvider = new FixedAdrProvider( address + BYTES_PER_ROW);
            }
            if ( newProvider != null ) {
                synchronized (LOCK) {
                    adrProvider = newProvider;
                }
                ui.doWithEmulator( MemoryViewWindow.this::tick );
            }
        }
    };

    protected static final class FixedAdrProvider implements IAdrProvider {

        private final int address;

        public FixedAdrProvider(int address)
        {
            this.address = address;
        }

        @Override
        public int getAddress(Emulator emulator)
        {
            return address;
        }

        @Override
        public boolean isFixedAddress()
        {
            return true;
        }
    }

    private final Object LOCK = new Object();

    // @Guardedby( LOCK )
    private int bytesToDump = 128;

    // @Guardedby( LOCK )
    private IAdrProvider adrProvider = new FixedAdrProvider( 0);

    private final JTextField expression = new JTextField();
    private final JLabel hexdump = new JLabel();

    public MemoryViewWindow(UI ui)
    {
        super( "Memory View", ui );

        hexdump.setFont( new Font(Font.MONOSPACED,Font.PLAIN,12 ) );

        expression.setText( "00000000" );
        expression.addActionListener( ev -> parseExpression( expression.getText() ) );

        addKeyListener( keyAdapter );
        setFocusable( true );
        getContentPane().setLayout( new GridBagLayout() );
        GridBagConstraints cnstrs = cnstrs( 0, 0 );
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx=1;cnstrs.weighty=0.1;
        expression.setColumns( 10 );
        getContentPane().add( expression,cnstrs );

        cnstrs = cnstrs( 0, 1 );
        cnstrs.weightx=1;cnstrs.weighty=0.9;
        cnstrs.fill=GridBagConstraints.BOTH;
        final JScrollPane scrollPane = new JScrollPane( hexdump );
        hexdump.addKeyListener( keyAdapter );
        scrollPane.addKeyListener( keyAdapter );
        getContentPane().add( scrollPane,cnstrs );
        addComponentListener( new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                recalcHeight();
            }

            private void recalcHeight()
            {
                synchronized (LOCK)
                {
                    final int height = hexdump.getFontMetrics( hexdump.getFont() ).getHeight();
                    bytesToDump = BYTES_PER_ROW * (int) (0.9f*(scrollPane.getHeight() / height));
                }
                ui.doWithEmulator( MemoryViewWindow.this::tick );
            }

            @Override
            public void componentShown(ComponentEvent e)
            {
                recalcHeight();
            }
        });
    }

    private void parseExpression(String value)
    {
        if ( ! StringUtils.isBlank(value) )
        {
            final Matcher matcher = ADR_PATTERN.matcher( value.trim() );

            if ( matcher.matches() )
            {
                final int regNum = Integer.parseInt( matcher.group( 1 ) );
                synchronized (LOCK)
                {
                    adrProvider = new IAdrProvider()
                    {
                        @Override
                        public int getAddress(Emulator emulator)
                        {
                            return emulator.cpu.addressRegisters[regNum];
                        }

                        @Override
                        public boolean isFixedAddress()
                        {
                            return false;
                        }
                    };
                }
            }
            else if ( value.trim().equalsIgnoreCase( "pc" ) ) {
                synchronized (LOCK)
                {
                    adrProvider = new IAdrProvider()
                    {
                        @Override
                        public int getAddress(Emulator emulator)
                        {
                            return emulator.cpu.pc;
                        }

                        @Override
                        public boolean isFixedAddress()
                        {
                            return false;
                        }
                    };
                }
            }
            else
            {
                final int adr = parseNumber( value.trim() );
                synchronized (LOCK)
                {
                    adrProvider = new FixedAdrProvider( adr );
                }
            }
        }
        ui.doWithEmulator( this::update );
    }

    @Override
    public String getWindowKey()
    {
        return "memoryview";
    }

    private void update(Emulator emulator)
    {
        final int adr;
        synchronized (LOCK )
        {
            adr = adrProvider.getAddress( emulator );
        }
        final String tmp = emulator.memory.hexdump( adr , bytesToDump );
        System.out.println( tmp );
        SwingUtilities.invokeLater( () -> {
            hexdump.setText(
                    "<html>"+tmp.replaceAll( "\n","<br>" )+"</html>");
        });
    }

    @Override
    public void stopped(Emulator emulator)
    {
        update(emulator);
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
        update(emulator);
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
    }

    @Override
    public void tick(Emulator emulator)
    {
        update( emulator );
    }

    private interface IAdrProvider
    {
        int getAddress(Emulator emulator);
        public boolean isFixedAddress();
    }
}