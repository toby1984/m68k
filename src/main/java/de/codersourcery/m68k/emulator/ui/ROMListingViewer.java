package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.TreeMap;

public class ROMListingViewer extends AppWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private boolean followPC = false;

    // @GuardedBy( LOCK )
    private int addressToDisplay = 0xFC0000;

    private final TreeMap<Integer,Integer> linesByAddress=new TreeMap<>();

    private final JTextPane textfield = new JTextPane();

    private final Style highlightStyle;
    private final Style defaultStyle;

    private final JTextField addressTextfield = new JTextField("$fc0000");

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

    public ROMListingViewer(String title, UI ui)
    {
        super( title, ui );

        this.textfield.setEditable( false );
        getContentPane().setLayout( new GridBagLayout() );
        addressTextfield.setColumns(8);
        addressTextfield.addActionListener( ev ->
        {
            final String text = addressTextfield.getText();
            if (StringUtils.isNotBlank(text) )
            {
                final int adr = parseNumber(text);
                synchronized (LOCK)
                {
                    followPC = false;
                    addressToDisplay = adr;
                }
                ui.doWithEmulator(this::tick );
            }
        });
        GridBagConstraints cnstrs = cnstrs(0, 0);
        cnstrs.fill=GridBagConstraints.HORIZONTAL;
        cnstrs.weighty=0.1;cnstrs.weightx=1;
        getContentPane().add( addressTextfield , cnstrs);

        cnstrs = cnstrs(0,1);
        cnstrs.weighty=0.9;cnstrs.weightx=1;
        getContentPane().add( new JScrollPane(textfield) , cnstrs );

        highlightStyle = document().addStyle( "highlightstyle", null );
        StyleConstants.setBackground(highlightStyle,Color.RED);
        defaultStyle = document().addStyle( "defaultstyle", null );
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
    public String getWindowKey()
    {
        return "romlisting";
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
        synchronized(LOCK)
        {
            if ( followPC ) {
                addressToDisplay = emulator.cpu.pc;
            }
        }
        if ( followPC ) {
            runOnEDT( () ->
            {
                synchronized(LOCK)
                {
                    if ( followPC )
                    {
                        Integer textOffset = linesByAddress.get( addressToDisplay );
                        if ( textOffset == null )
                        {
                            Integer previous = null;
                            for ( var entry : linesByAddress.entrySet() )
                            {
                                if ( entry.getKey() >= addressToDisplay ) {
                                    break;
                                }
                                previous = entry.getValue();
                            }
                            textOffset = previous;
                        }
                        if ( textOffset != null )
                        {
                            textfield.setCaretPosition(textOffset);
                            final int end = getEndOfLine( textOffset );
                            if ( currentHighlight != null ) {
                                currentHighlight.clear();
                                currentHighlight = null;
                            }
                            System.out.println("HIGHLIGHT: "+textOffset+" -> "+end);
                            currentHighlight = new Highlight( textOffset,end-textOffset );
                            currentHighlight.highlight();
                        }
                    }
                }
            });
        }
    }
}