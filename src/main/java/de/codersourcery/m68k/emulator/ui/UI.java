package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Amiga;
import de.codersourcery.m68k.emulator.Breakpoints;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.memory.MemoryBreakpoints;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class UI extends JFrame
{
    private static final String MAIN_WINDOW_KEY = "mainWindow";

    final JDesktopPane desktop = new JDesktopPane();

    private Emulator emulator;

    private final List<ITickListener> tickListeners = new ArrayList<>();
    private final List<Emulator.IEmulatorStateCallback> stateChangeListeners = new ArrayList<>();
    private final List<AppWindow> windows = new ArrayList<>();
    private ROMListingViewer romListing;

    private UIConfig uiConfig;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait( () -> new UI().run() );
    }

    public UI()
    {
        super("m68k");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener( new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                saveConfig();
            }
        } );
        setPreferredSize(new Dimension(640,480));
    }

    private byte[] loadKickstartRom() throws IOException
    {
        if ( loadConfig().getKickRomLocation() == null ) {
            throw new IOException("No kickstart ROM file selected");
        }
        final File file = loadConfig().getKickRomLocation();
        System.out.println("*** Loading kickstart ROM from "+file.getAbsolutePath());
        return Files.readAllBytes(file.toPath() );
    }

    private void setupEmulator() throws Exception
    {
        if ( emulator != null )
        {
            emulator.destroy();
            emulator = null;
        }

        System.out.println("Setting up new emulator instance for "+Amiga.AMIGA_500);
        System.out.println("Using kickstart ROM "+loadConfig().getKickRomLocation());

        emulator = new Emulator(Amiga.AMIGA_500,loadKickstartRom());
        emulator.getBreakpoints().populateFrom( loadConfig().getBreakpoints() );
        emulator.memory.breakpoints.populateFrom( loadConfig().getMemoryBreakpoints() );
        emulator.setCallbackInvocationTicks(1000000);

        emulator.setStateCallback( new Emulator.IEmulatorStateCallback()
        {
            @Override
            public void stopped(Emulator emulator)
            {
                stateChangeListeners.forEach( listener -> listener.stopped( emulator ) );
            }

            @Override
            public void singleStepFinished(Emulator emulator)
            {
                stateChangeListeners.forEach( listener -> listener.singleStepFinished( emulator ) );
            }

            @Override
            public void enteredContinousMode(Emulator emulator)
            {
                stateChangeListeners.forEach( listener -> listener.enteredContinousMode( emulator ) );
            }
        } );
        emulator.setTickCallback( e ->
        {
            for (int i = 0, tickListenersSize = tickListeners.size(); i < tickListenersSize; i++)
            {
                tickListeners.get( i ).tick( e );
            }
        });
        refresh();
    }

    private void refresh()
    {
        for (ITickListener l : tickListeners)
        {
            System.out.println("REFRESH: "+l);
            emulator.runOnThread(() -> l.tick(emulator),false);
        }
    }

    private Optional<File> selectFile(File existing, FileFilter filter)
    {
        final JFileChooser fileChooser;
        if ( existing == null ) {
            fileChooser = new JFileChooser(existing);
        } else {
            fileChooser = new JFileChooser();
        }
        fileChooser.setFileFilter(filter);
        final int result = fileChooser.showOpenDialog(this);
        return Optional.ofNullable( result == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null );
    }

    private <T extends AppWindow> T registerWindow(T window)
    {
        windows.add(window);
        desktop.add(window);

        final Optional<WindowState> state =
                loadConfig().getWindowState( window.getWindowKey() );

        if ( state.isPresent() ) {
            System.out.println("Applying window state "+state.get());
            window.applyWindowState( state.get() );
        } else {
            window.pack();
            window.setVisible( true );
        }

        if ( window instanceof ITickListener) {
            tickListeners.add( (ITickListener) window );
        }
        if ( window instanceof Emulator.IEmulatorStateCallback) {
            stateChangeListeners.add( (Emulator.IEmulatorStateCallback) window);
        }
        return window;
    }

    public void run()
    {
        // setup menu bar
        setJMenuBar( createMenuBar() );

        // add internal windows
        registerWindow( new DisassemblyWindow(this) );
        registerWindow( new CPUStateWindow("CPU", this) );
        registerWindow( new EmulatorStateWindow("Emulator", this) );
        registerWindow( new MemoryViewWindow(this) );
        registerWindow( new BreakpointsWindow(this) );
        registerWindow( new MemoryBreakpointsWindow(this) );
        registerWindow( new ScreenWindow("Screen", this) );
        romListing = registerWindow( new ROMListingViewer( "ROM listing", this ) );
        setContentPane( desktop );

        // display main window
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // apply main window state
        final Optional<WindowState> state = loadConfig().getWindowState(MAIN_WINDOW_KEY);
        state.ifPresent(s ->
        {
            setBounds(s.getLocationAndSize());
        });

        final UIConfig config = loadConfig();
        if ( config.getKickRomDisassemblyLocation() != null ) {
            romListing.setKickRomDisasm( config.getKickRomDisassemblyLocation() );
        }

        if ( config.getKickRomLocation() != null && config.getKickRomLocation().exists() )
        {
            try
            {
                setupEmulator();
            }
            catch (Exception e)
            {
                error(e);
            }
        }
    }

    private JMenuBar createMenuBar()
    {
        final JMenuBar menuBar = new JMenuBar();

        final JMenu menu1 = new JMenu("File" );
        menuBar.add( menu1 );

        menu1.add( menuItem("Load kickstart ROM", () ->
        {
            final FileFilter filter = new FileFilter()
            {
                @Override
                public boolean accept(File f)
                {
                    return f.isDirectory() || f.getName().toLowerCase().contains("kick") ||
                            f.getName().toLowerCase().contains("rom");
                }

                @Override
                public String getDescription()
                {
                    return "Kickstart ROMs";
                }
            };

            final Optional<File> selection = selectFile(loadConfig().getKickRomLocation(),filter);
            if ( selection.isPresent() )
            {
                loadConfig().setKickRomLocation(selection.get());
                setupEmulator();
            }
        }));

        menu1.add( menuItem("Load kickstart ROM disassembly", () ->
        {
            final FileFilter filter = new FileFilter()
            {
                @Override
                public boolean accept(File f)
                {
                    return f.isDirectory() || f.getName().toLowerCase().contains("kick") ||
                            f.getName().toLowerCase().contains("rom");
                }

                @Override
                public String getDescription()
                {
                    return "Kickstart ROM disassembly";
                }
            };

            final Optional<File> selection = selectFile(loadConfig().getKickRomDisassemblyLocation(),filter);
            if ( selection.isPresent() )
            {
                romListing.setKickRomDisasm( selection.get() );
                loadConfig().setKickRomDisassemblyLocation( selection.get() );
            }
        }));

        menu1.add( menuItem("Save configuration", () ->
        {
            saveConfig();
            info( "Configuration saved." );
        }));

        menu1.add( menuItem("Quit", () ->
        {
            saveConfig();
            System.exit( 0 );
        } ) );

        return menuBar;
    }

    private interface IThrowingRunnable
    {
        void run() throws Exception;
    }

    private JMenuItem menuItem(String label,IThrowingRunnable action)
    {
        final JMenuItem item = new JMenuItem(label);
        item.addActionListener( ev ->
        {
            try
            {
                action.run();
            }
            catch (Exception e)
            {
                error(e);
            }
        } );
        return item;
    }

    public void error(Throwable e)
    {
        e.printStackTrace();
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try ( PrintWriter writer = new PrintWriter(bos, true) )
        {
            e.printStackTrace(writer);
        }
        JOptionPane.showMessageDialog(UI.this, new String(bos.toByteArray()),"An error occurred",JOptionPane.ERROR_MESSAGE);
    }

    public void info(String message) {
        JOptionPane.showMessageDialog(UI.this, message,"Information",JOptionPane.INFORMATION_MESSAGE);
    }

    public Emulator getEmulator() {
        return emulator;
    }

    /**
     * Execute a callback with the emulator.
     *
     * @param consumer
     * @return <code>true</code> if callback got invoked, <code>false</code> if no emulator was available
     */
    public final boolean doWithEmulator(Consumer<Emulator> consumer)
    {
        final Emulator e = getEmulator();
        if ( e != null )
        {
            e.runOnThread(() -> consumer.accept(e), true );
            return true;
        }
        return false;
    }

    /**
     * Execute a function producing some value with the emulator.
     *
     * If no emulator is available,the default value will be returned.
     * @param defaultValue
     * @param consumer
     * @param <T>
     *
     * @return default value or actual result
     */
    public final <T> T doWithEmulatorAndResult(T defaultValue, Function<Emulator,T> consumer)
    {
        final Emulator e = getEmulator();
        if ( e != null )
        {
            final AtomicReference<T> result = new AtomicReference<>(defaultValue);
            e.runOnThread(() -> result.set( consumer.apply(e) ) , true );
            return result.get();
        }
        return defaultValue;
    }

    private File getConfigPath()
    {
        final String dir = System.getProperty( "user.dir" );
        return new File(dir+File.separator+".m68kconfig");
    }

    private UIConfig loadConfig()
    {
        if ( uiConfig == null )
        {
            final File file = getConfigPath();
            if ( file.exists() )
            {
                try (FileInputStream in = new FileInputStream( file ))
                {
                    uiConfig = UIConfig.read( in );
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
            if ( uiConfig == null )
            {
                uiConfig = new UIConfig();
            }
        }
        return uiConfig;
    }

    private void saveConfig()
    {
        final UIConfig config = loadConfig();

        // persist windows
        windows.stream().map( x->x.getWindowState() )
                .forEach(  config::setWindowState );

        // persist main window state
        final WindowState mainWindow = new WindowState();
        mainWindow.setEnabled(true);
        mainWindow.setVisible(true);
        mainWindow.setWindowKey(MAIN_WINDOW_KEY);
        mainWindow.setLocationAndSize( UI.this.getBounds());
        config.setWindowState(mainWindow);

        final AtomicReference<Breakpoints> bps1 = new AtomicReference<>();
        doWithEmulator(emu -> {
           bps1.set( emu.getBreakpoints().createCopy() );
        });

        if ( bps1.get() != null )
        {
            config.setBreakpoints( bps1.get() );
        }

        final AtomicReference<MemoryBreakpoints> bps2 = new AtomicReference<>();
        doWithEmulator(emu -> {
            bps2.set( emu.memory.breakpoints.createCopy() );
        });

        if ( bps2.get() != null )
        {
            config.setMemoryBreakpoints( bps2.get() );
        }

        final File file = getConfigPath();
        System.out.println("*** Saving configuration to "+file);
        try (FileOutputStream out = new FileOutputStream( file ) )
        {
            config.write( out );
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}