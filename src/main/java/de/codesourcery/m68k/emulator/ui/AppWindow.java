package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.Emulator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Abstract base class for application windows.
 *
 * Windows that need access to emulator state or
 * want to be notified about emulator state changes
 * can implement {@link ITickListener} and/or
 * {@link de.codesourcery.m68k.emulator.Emulator.IEmulatorStateCallback}
 * and the {@link UI} will take care of forwarding those calls
 * to the interested windows.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AppWindow extends JInternalFrame
{
    private static final Pattern ARITH_PATTERN =
            Pattern.compile(".*?(\\+|-)\\s*(\\$?[0-9a-fA-F]+)",Pattern.CASE_INSENSITIVE);

    private static final Pattern ADR_REGISTER_PATTERN =
            Pattern.compile("a([0-7]{1})",Pattern.CASE_INSENSITIVE);

    private static final Pattern DATA_REGISTER_PATTERN =
            Pattern.compile("d([0-7]{1})",Pattern.CASE_INSENSITIVE);

    private static final List<Consumer<KeyEvent>> keyReleasedListener =
            new ArrayList<>();

    private static final KeyAdapter KEY_ADAPTER =
            new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent event)
                {
                    for (Consumer<KeyEvent> e : keyReleasedListener )
                    {
                        try {
                            e.accept( event );
                        } catch(Exception ex) {
                            ex.printStackTrace();;
                        }
                    }
                }
            };

    protected final UI ui;

    public enum WindowKey
    {
        BACKTRACE( "backtrace" , "Backtrace", BacktraceWindow.class ),
        BREAKPOINTS( "breakpoints", "Breakpoints", BreakpointsWindow.class ),
        CIA_STATE( "ciastate", "CIAs", CIAStateWindow.class ),
        CPU_STATE( "cpustate" , "CPU State", CPUStateWindow.class),
        DISASSEMBLY( "disassembly" , "Disassembly (PC)", DisassemblyWindow.class),
        EMULATOR_STATE( "emulatorcontrol" , "Emulator", EmulatorStateWindow.class ),
        MAIN_WINDOW("mainWindow", "Main Window", null),
        MEMORY_VIEW( "memoryview" , "Memory", MemoryViewWindow.class ),
        MEMORY_BREAKPOINTS( "membreakpoints" , "Memory Breakpoints", MemoryBreakpointsWindow.class ),
        ROM_LISTING("romlisting", "ROM Listing", ROMListingViewer.class ),
        STRUCT_EXPLORER( "struct-explorer" , "Struct Explorer", StructExplorer.class ),
        SCREEN( "screen" , "Screen", ScreenWindow.class ),
        DISASSEMBLY_TEXT( "disassembly-text", "Disassembly (text)" , DisassemblyTextWindow.class );

        public final String id;
        public final String uiLabel;
        public final Class<? extends AppWindow> clazz;

        public static final String WINDOW_KEY_PATTERN = "[_\\-a-zA-Z0-9]+";

        WindowKey(String id, String uiLabel, Class<? extends AppWindow> clazz)
        {
            this.id = id;
            if ( ! Pattern.compile( WINDOW_KEY_PATTERN ).matcher( id ).matches() ) {
                throw new IllegalArgumentException( "Invalid window key '"+id+", needs to match "+ WINDOW_KEY_PATTERN );
            }
            this.uiLabel = uiLabel;
            this.clazz = clazz;
        }

        public AppWindow newInstance(UI ui)
        {
            try
            {
                final Constructor<? extends AppWindow> constructor = clazz.getConstructor( new Class[]{UI.class} );
                return constructor.newInstance( ui );
            }
            catch(Exception e) {
                throw new RuntimeException("Failed to instantiate "+clazz.getName(),e);
            }
        }

        public static WindowKey fromId(String windowKey)
        {
            return Stream.of( WindowKey.values() ).filter( x -> x.id.equals(windowKey))
                    .findFirst().orElseThrow();
        }
    }

    public AppWindow(String title,UI ui)
    {
        super(title);
        this.ui = ui;
        setPreferredSize(new Dimension(320,200));
        setResizable(true);
        setMaximizable(true);
        setFocusable(true);
        addKeyListener( KEY_ADAPTER );
        addInternalFrameListener(new InternalFrameAdapter()
        {
            @Override
            public void internalFrameClosed(InternalFrameEvent e)
            {
                windowClosed();
            }
        });
    }

    protected void windowClosed() {
    }

    protected static <T extends Component> T attachKeyListeners(T component,T... additional) {
        component.addKeyListener( KEY_ADAPTER );
        if (additional != null ) {
            Arrays.stream(additional).forEach(c -> c.addKeyListener(KEY_ADAPTER) );
        }
        return component;
    }

    protected static void registerKeyReleasedListener(Consumer<KeyEvent> l)
    {
        Validate.notNull( l, "l must not be null" );
        keyReleasedListener.add(l);
    }

    protected static void unregisterKeyReleasedListener(Consumer<KeyEvent> l)
    {
        Validate.notNull( l, "l must not be null" );
        keyReleasedListener.remove(l);
    }

    public static final GridBagConstraints cnstrs(int x, int y)
    {
        final GridBagConstraints result = new GridBagConstraints();
        result.gridx = x; result.gridy = y;
        result.gridwidth = 1 ; result.gridheight = 1;
        result.weightx = 1; result.weighty = 1;
        result.insets = new Insets(1,1,1,1);
        result.fill = GridBagConstraints.BOTH;
        return result;
    }

    public static final GridBagConstraints cnstrsNoResize(int x,int y)
    {
        final GridBagConstraints result = cnstrs(x,y);
        result.weightx = 0; result.weighty = 0;
        result.fill = GridBagConstraints.NONE;
        return result;
    }

    protected final void error(Throwable cause) {
        ui.error(cause);
    }

    protected final void runOnEmulator(Consumer<Emulator> c) {
        ui.doWithEmulator(c);
    }

    protected final void runOnEDT(Runnable r) {
        if ( SwingUtilities.isEventDispatchThread() ) {
            r.run();
        } else {
            SwingUtilities.invokeLater( r );
        }
    }

    public abstract WindowKey getWindowKey();

    public void applyWindowState(WindowState state)
    {
        if ( ! getWindowKey().equals(state.getWindowKey()) ) {
            throw new IllegalArgumentException("Window state belongs to "+state.getWindowKey()+" but this is "+getWindowKey());
        }
        setVisible(state.isVisible());
        setBounds( state.getLocationAndSize() );
    }

    public WindowState getWindowState()
    {
        final WindowState state = new WindowState();
        state.setLocationAndSize( getBounds() );
        state.setEnabled( true );
        state.setVisible( isVisible() );
        state.setWindowKey( getWindowKey() );
        return state;
    }

    protected static int parseNumber(String value)
    {
        value = value.trim().toLowerCase();
        if ( value.startsWith("$" ) )
        {
            return Integer.parseInt( value.substring(1),16 );
        }
        return Integer.parseInt( value,16 );
    }

    protected interface IAdrProvider
    {
        int getAddress(Emulator emulator);
        public boolean isFixedAddress();
    }

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

    protected final IAdrProvider parseExpression(String value)
    {
        final int offset;
        if ( ! StringUtils.isBlank(value) ) {
            final Matcher m = ARITH_PATTERN.matcher( value );
            if ( m.matches() ) {
                final String op = m.group(1);
                final String operand = m.group(2);
                offset = ("-".equals(op) ? -1 : 1) * parseNumber( operand );
                value = value.substring( 0, value.indexOf(op) );
            } else {
                offset = 0;
            }
        } else {
            offset = 0;
        }

        if ( ! StringUtils.isBlank(value) )
        {
            Matcher matcher = ADR_REGISTER_PATTERN.matcher( value.trim() );

            if ( matcher.matches() )
            {
                final int regNum = Integer.parseInt( matcher.group( 1 ) );
                return new IAdrProvider()
                {
                    @Override
                    public int getAddress(Emulator emulator)
                    {
                        return emulator.cpu.addressRegisters[regNum] + offset;
                    }

                    @Override
                    public boolean isFixedAddress()
                    {
                        return false;
                    }
                };
            }

            matcher = DATA_REGISTER_PATTERN.matcher( value.trim() );

            if ( matcher.matches() )
            {
                final int regNum = Integer.parseInt( matcher.group( 1 ) );
                return new IAdrProvider()
                {
                    @Override
                    public int getAddress(Emulator emulator)
                    {
                        return emulator.cpu.dataRegisters[regNum] + offset;
                    }

                    @Override
                    public boolean isFixedAddress()
                    {
                        return false;
                    }
                };
            }

            if ( value.trim().equalsIgnoreCase( "pc" ) ) {
                return new IAdrProvider()
                {
                    @Override
                    public int getAddress(Emulator emulator)
                    {
                        return emulator.cpu.pc + offset;
                    }

                    @Override
                    public boolean isFixedAddress()
                    {
                        return false;
                    }
                };
            }
            final int adr = parseNumber( value.trim() );
            return new FixedAdrProvider( adr+offset );
        }
        return null;
    }
}