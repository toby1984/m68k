package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.disassembler.ChipRegisterResolver;
import de.codersourcery.m68k.disassembler.LibraryCallResolver;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UI extends JFrame
{
    final JDesktopPane desktop = new JDesktopPane();

    private Emulator emulator;

    private final List<ITickListener> tickListeners = new ArrayList<>();
    private final List<Emulator.IEmulatorStateCallback> stateChangeListeners = new ArrayList<>();
    private final List<AppWindow> windows = new ArrayList<>();

    private ChipRegisterResolver registerResolver;
    private LibraryCallResolver libraryCallResolver;

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
            registerResolver = null;
            libraryCallResolver = null;
        }

        System.out.println("Setting up new emulator instance for "+Amiga.AMIGA_500);
        System.out.println("Using kickstart ROM "+loadConfig().getKickRomLocation());

        emulator = new Emulator(Amiga.AMIGA_500,loadKickstartRom());

        emulator.getBreakpoints().populateFrom( loadConfig().getBreakpoints() );
        emulator.memory.breakpoints.populateFrom( loadConfig().getMemoryBreakpoints() );
        emulator.setCallbackInvocationTicks(1000000);

        setupLibraryCallResolver(emulator);

        windows.forEach( this::initializeWindow );

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

    private void setupLibraryCallResolver(Emulator emulator) throws IOException
    {
        registerResolver = new ChipRegisterResolver(emulator );
        libraryCallResolver = new LibraryCallResolver( emulator );

        final UIConfig config = loadConfig();
        final Set<File> descFiles = new HashSet<>();
        if ( config.getLibraryFunctionDescBaseDir() != null )
        {
            final File baseDir = config.getLibraryFunctionDescBaseDir();
            final File[] files = baseDir.listFiles();
            if ( files != null )
            {
                for ( File f : files )
                {
                    if ( ! f.isDirectory() )
                    {
                        descFiles.add( f );
                    }
                }
            }
        }

        for (UIConfig.LibraryMapping mapping : config.getLibraryMappings())
        {
            final Pattern fileNamePattern = Pattern.compile( mapping.descFileRegex , Pattern.CASE_INSENSITIVE );
            final Pattern libraryNamePattern = Pattern.compile( mapping.libraryNameRegex , Pattern.CASE_INSENSITIVE );
            final List<File> matches = descFiles.stream().filter( x -> fileNamePattern.matcher( x.getName() ).matches() ).collect( Collectors.toList() );
            if ( matches.size() == 1 )
            {
                System.out.println("Registering "+matches.get(0).getAbsolutePath()+" => "+mapping);
                final LibraryCallResolver.ILibraryMatcher matcher = (libraryName, libraryVersion) -> libraryNamePattern.matcher( libraryName ).matches();
                libraryCallResolver.register( matches.get( 0 ), matcher );
            }
            else if ( matches.size() > 1 )
            {
                throw new RuntimeException("File name pattern '"+fileNamePattern+"' matches multiple files: "+matches.stream().map(x->x.getAbsolutePath()).collect( Collectors.joining(",")));
            }
            System.err.println( "No matching files for " + mapping.descFileRegex );
        }
    }

    private void refresh()
    {
        for (ITickListener l : tickListeners)
        {
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

    public Optional<AppWindow>  getWindow(AppWindow.WindowKey key) {
        return windows.stream().filter( w -> w.getClass() == key.clazz ).findFirst();
    }

    private void unregisterWindow(AppWindow.WindowKey key)
    {
        final Optional<AppWindow> optWindow = getWindow( key );
        if ( ! optWindow.isPresent() ) {
            return;
        }
        final AppWindow window = optWindow.get();
        windows.remove( window );
        window.dispose();
        desktop.remove(  window );

        if ( window instanceof ITickListener) {
            tickListeners.remove( (ITickListener) window );
        }
        if ( window instanceof Emulator.IEmulatorStateCallback) {
            stateChangeListeners.remove( (Emulator.IEmulatorStateCallback) window);
        }
    }

    private AppWindow registerWindow(AppWindow.WindowKey key)
    {
        final Optional<WindowState> state = loadConfig().getWindowState( key );

        if ( state.isPresent() && ! state.get().isEnabled() ) {
            System.out.println("Window "+key+" is disabled.");
            return null;
        }
        final AppWindow window = key.newInstance( this );
        windows.add(window);
        desktop.add(window);

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

        initializeWindow(window);
        return window;
    }

    private void initializeWindow(AppWindow window)
    {
        if ( window instanceof ROMListingViewer)
        {
            final ROMListingViewer romListing = (ROMListingViewer) window;
            if ( loadConfig().getKickRomDisassemblyLocation() != null )
            {
                try
                {
                    romListing.setKickRomDisasm( loadConfig().getKickRomDisassemblyLocation() );
                }
                catch(Exception e) {
                    error(e);
                }
            }
        }
        if ( window instanceof AbstractDisassemblyWindow)
        {
            ((AbstractDisassemblyWindow) window).setChipRegisterResolver(registerResolver );
            ((AbstractDisassemblyWindow) window).setLibraryCallResolver(libraryCallResolver);
        }
    }

    public void run()
    {
        // setup menu bar
        setJMenuBar( createMenuBar() );

        // add internal windows
        Stream.of( AppWindow.WindowKey.values() )
                .filter( x -> x != AppWindow.WindowKey.MAIN_WINDOW )
                .forEach( this::registerWindow );

        setContentPane( desktop );

        // display main window
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // apply main window state
        final Optional<WindowState> state = loadConfig().getWindowState( AppWindow.WindowKey.MAIN_WINDOW );
        state.ifPresent(s -> setBounds(s.getLocationAndSize()) );

        final UIConfig config = loadConfig();

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
        final JMenu menu2 = new JMenu("View" );

        final UIConfig config = loadConfig();
        for ( AppWindow.WindowKey key : AppWindow.WindowKey.values() )
        {
            if ( key == AppWindow.WindowKey.MAIN_WINDOW )
            {
                continue;
            }
            final Optional<WindowState> state = config.getWindowState( key );
            final boolean enabled = state.map( x -> x.isEnabled() ).orElse( Boolean.TRUE );
            final JCheckBoxMenuItem item = new JCheckBoxMenuItem( key.uiLabel , enabled );
            item.addActionListener( ev ->
            {
                final Optional<AppWindow> window = getWindow( key );
                if ( window.isPresent() )
                {
                    final WindowState newState = window.get().getWindowState();
                    newState.setEnabled( false );
                    unregisterWindow( key );
                    config.setWindowState( newState );
                }
                else
                {
                    final Optional<WindowState> newState = config.getWindowState( key );
                    if ( newState.isPresent() ) {
                        newState.get().setEnabled( true );
                        config.setWindowState( newState.get() );
                    }
                    final AppWindow w = registerWindow( key );
                    if ( w != null ) {
                        config.setWindowState( w.getWindowState() );
                    }
                }
            });
            menu2.add(item);
        }
        menuBar.add( menu1 );
        menuBar.add( menu2 );

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

        menu1.add( menuItem("Library function resolution...", () -> new LibraryFunctionResolutionDialog().showDialog( loadConfig() ) ));

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
                final Optional<AppWindow> window = getWindow( AppWindow.WindowKey.ROM_LISTING );
                window.ifPresent(  w -> ((ROMListingViewer) w).setKickRomDisasm( selection.get() ) );
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
            System.out.println("Trying to load configuration from "+file.getAbsolutePath());
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
        for ( AppWindow.WindowKey key : AppWindow.WindowKey.values() )
        {
            if ( key == AppWindow.WindowKey.MAIN_WINDOW) {
                continue; // handles as a special case below
            }
            final Optional<AppWindow> window = getWindow( key );
            if ( window.isPresent() ) {
                config.setWindowState( window.get().getWindowState() );
            } else {
                final Optional<WindowState> state = config.getWindowState( key );
                if ( state.isPresent() ) {
                    state.get().setEnabled( false );
                    config.setWindowState( state.get() );
                }
            }
        }

        // persist main window state
        final WindowState mainWindow = new WindowState();
        mainWindow.setEnabled(true);
        mainWindow.setVisible(true);
        mainWindow.setWindowKey( AppWindow.WindowKey.MAIN_WINDOW );
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