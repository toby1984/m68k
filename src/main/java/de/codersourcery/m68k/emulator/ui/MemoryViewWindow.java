package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;

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
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryViewWindow extends AppWindow implements Emulator.IEmulatorStateCallback,
        ITickListener
{
    private static final int BYTES_PER_ROW = 16; // TODO: Hard-coded in Memory#hexdump

    private final KeyListener keyAdapter = new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e)
        {
            int address;
            IAdrProvider newProvider = null;
            final AtomicInteger tmpAddress = new AtomicInteger();
            runOnEmulator( emu -> {
                tmpAddress.set( adrProvider.getAddress( null ) );
            });
            synchronized (LOCK)
            {
                address = tmpAddress.get();
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

        expression.setText( "$00000000" );
        expression.addActionListener( ev ->
        {
            final IAdrProvider provider = parseExpression( expression.getText() );
            if ( provider != null ) {
                synchronized (LOCK)
                {
                    adrProvider = provider;
                }
                runOnEmulator( this::update );
            }
        });

        addKeyListener( keyAdapter );
        hexdump.addKeyListener( keyAdapter );
        hexdump.setFocusable( true );
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
        expression.addKeyListener( keyAdapter );
        scrollPane.addKeyListener( keyAdapter );
        scrollPane.setFocusable( true );
        hexdump.addKeyListener( keyAdapter );
        hexdump.setFocusable( true );
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

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.MEMORY_VIEW;
    }

    private void update(Emulator emulator)
    {
        final int adr;
        synchronized (LOCK )
        {
            adr = adrProvider.getAddress( emulator );
        }
        final String tmp = emulator.memory.hexdump( adr , bytesToDump );
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
}