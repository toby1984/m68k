package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.Emulator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.TreeMap;

public class ROMListingViewer extends AppWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    private static final Logger LOG = LogManager.getLogger( ROMListingViewer.class.getName() );

    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private boolean followPC = true;

    // @GuardedBy( LOCK )
    private int addressToDisplay = 0xFC0000;

    // @GuardedBy( LOCK )
    private boolean addressChanged = false;

    private String searchText;

    private final TreeMap<Integer,Integer> linesByAddress=new TreeMap<>();

    private final JTextPane textfield = new JTextPane();
    private final JScrollPane scrollpane;

    private final Style highlightStyle;
    private final Style defaultStyle;

    private final JTextField addressTextfield = new JTextField("fc0000");

    private Highlight currentHighlight;

    protected final class Highlight
    {
        public final int offset;
        public final int length;

        public Highlight(int offset, int length)
        {
            this.offset = offset;
            this.length = length;
        }

        public void clear()
        {
            document().setCharacterAttributes( offset,length,defaultStyle,true );
        }

        public void highlight() {
            document().setCharacterAttributes( offset,length,highlightStyle,true );
        }
    }

    private StyledDocument document() {
        return (StyledDocument) textfield.getStyledDocument();
    }

    public void showAddress(int address)
    {
        synchronized(LOCK) {
            followPC = false;
            addressToDisplay = address;
            addressChanged = true;
        }
        runOnEmulator(this::tick );
    }

    public ROMListingViewer(UI ui)
    {
        super( "ROM listing" , ui );

        this.textfield.setEditable( false );
        this.textfield.addKeyListener( new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e)
            {
                if ( e.getKeyCode() == KeyEvent.VK_F )
                {
                    if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) != 0 )
                    {
                        String input = JOptionPane.showInputDialog( null, "Enter text",searchText);
                        if ( StringUtils.isNotBlank(input) )
                        {
                            searchText = input;
                            search( input );
                        }
                    }
                }
                else if ( e.getKeyCode() == KeyEvent.VK_F3 )
                {
                    if ( StringUtils.isNotBlank(searchText) ) {
                        search(searchText);
                    }
                }
            }
        } );
        getContentPane().setLayout( new GridBagLayout() );
        addressTextfield.setColumns(8);
        addressTextfield.addActionListener( ev ->
        {
            final int adr;
            try
            {
                adr = parseNumber( addressTextfield.getText().trim() );
            }
            catch(Exception e) {
                error(e);
                return;
            }
            synchronized (LOCK)
            {
                followPC = false;
                addressToDisplay = adr;
                addressChanged = true;
                LOG.info( "Now displaying: "+adr );
            }
            ui.doWithEmulator(this::tick);
        });
        GridBagConstraints cnstrs = cnstrs(0, 0);
        cnstrs.fill=GridBagConstraints.HORIZONTAL;
        cnstrs.weighty=0.1;cnstrs.weightx=1;
        getContentPane().add( addressTextfield , cnstrs);

        cnstrs = cnstrs(0,1);
        cnstrs.weighty=0.9;cnstrs.weightx=1;
        scrollpane = new JScrollPane(textfield);
        attachKeyListeners(textfield,scrollpane);
        getContentPane().add( scrollpane , cnstrs );

        highlightStyle = document().addStyle( "highlightstyle", null );
        StyleConstants.setBackground(highlightStyle,Color.RED);
        defaultStyle = document().addStyle( "defaultstyle", null );
    }

    private void search(String searchString)
    {
        if ( searchString == null || searchString.trim().isEmpty() )
        {
            return;
        }
        final int searchStringLen = searchString.length();
        final int searchStart = textfield.getCaretPosition()+1;

        final String text = textfield.getText();
outer:
        for ( int i = searchStart ,len = text.length() ; i < len ; i++ )
        {
            if ( text.charAt( i ) == searchString.charAt( 0 ) )
            {
                for (int j = 0; j < searchStringLen; j++)
                {
                    if ( text.charAt( i + j ) != searchString.charAt( j ) )
                    {
                        continue outer;
                    }
                }
                // found match
                textfield.setCaretPosition( i );
                return;
            }
        }
    }

    public void setKickRomDisasm(File file)
    {
        final StringBuilder buffer = new StringBuilder();
        try ( BufferedReader reader = new BufferedReader( new FileReader(file) ) )
        {
            final TreeMap<Integer,Integer> tmp =new TreeMap<>();
            String line = null;
            while ( ( line = reader.readLine() ) != null )
            {
                String hex = getHexNumber( line );
                if ( hex != null )
                {
                    tmp.put( Integer.parseInt( hex, 16 ), buffer.length() );
                }
                buffer.append( line ).append( "\n" );
            }
            synchronized( LOCK )
            {
                this.linesByAddress.clear();
                this.linesByAddress.putAll(tmp);
            }
            this.textfield.setText(buffer.toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to load rom listing "+file.getAbsolutePath(),e);
        }
    }

    private int getEndOfLine(int start)
    {
        final String text = textfield.getText();
        int i = start;
        for ( int len = text.length() ; i < len ; i++ ) {
            if ( text.charAt(i) == '\n' ) {
                return i;
            }
        }
        return i;
    }

    private String getHexNumber(String line) {

        int i = 0;
        for ( int len = line.length() ; i < len ; i++)
        {
            final char c = line.charAt(i);
            if ( (c < 'a' || c > 'f') && (c < '0' || c > '9') && (c < 'A' || c > 'F') )
            {
                break;
            }
        }
        return i == 0 ? null : line.substring(0,i);
    }

    @Override
    public WindowKey getWindowKey()
    {
        return WindowKey.ROM_LISTING;
    }

    @Override
    public void stopped(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
        synchronized(LOCK)
        {
            followPC = true;
        }
        tick(emulator);
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
    }

    @Override
    public void tick(Emulator emulator)
    {
        final boolean needsUpdate;
        synchronized(LOCK)
        {
            if ( followPC ) {
                addressToDisplay = emulator.cpu.pc;
                addressChanged = true;
            }
            needsUpdate = addressChanged;
        }
        if ( needsUpdate)
        {
            runOnEDT( () ->
            {
                synchronized (LOCK)
                {
                    Integer textOffset = linesByAddress.get( addressToDisplay );
                    if ( textOffset == null )
                    {
                        Integer previous = null;
                        for (var entry : linesByAddress.entrySet())
                        {
                            if ( entry.getKey() >= addressToDisplay )
                            {
                                break;
                            }
                            previous = entry.getValue();
                        }
                        textOffset = previous;
                    }
                    if ( textOffset != null )
                    {
//                        textfield.setCaretPosition( textOffset );
                        final int end = getEndOfLine( textOffset );
                        if ( currentHighlight != null )
                        {
                            currentHighlight.clear();
                            currentHighlight = null;
                        }
                        currentHighlight = new Highlight( textOffset, end - textOffset );
                        currentHighlight.highlight();

                        Rectangle position = null;
                        try
                        {
                            position = textfield.modelToView( textOffset );
                        }
                        catch (BadLocationException e)
                        {
                            error(e);
                            return;
                        }

                        // adjust viewport so that highlighted area is in the middle
                        // of the scrollpane
                        LOG.info( "Caret at "+position );
                        final JViewport pane = (JViewport) textfield.getParent();
                        LOG.info( "Viewport size: "+scrollpane.getViewport().getView().getSize() );
                        LOG.info( "Viewport bounds: "+scrollpane.getBounds() );
                        LOG.info( "Visible rect: "+pane.getVisibleRect() );
                        position.y += scrollpane.getBounds().height/4;
                        if ( position.y >= 0 )
                        {
                            LOG.info( "Scrolling to "+position );
                            textfield.scrollRectToVisible( position );
                        }
                    }
                    addressChanged = false;
                }
            } );
        }
    }
}