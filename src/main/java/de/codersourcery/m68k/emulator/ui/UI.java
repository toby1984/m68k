package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Amiga;
import de.codersourcery.m68k.emulator.Emulator;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
    final JDesktopPane desktop = new JDesktopPane();

    private File kickstartRom = new File("/home/tgierke/Downloads/kickstart_1.2.rom");
    private Emulator emulator;

    private final List<AppWindow> windows = new ArrayList<>();

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            new UI().run();
        });
    }

    public UI()
    {
        super("m68k");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(640,480));
    }

    private byte[] loadKickstartRom() throws IOException
    {
        if ( kickstartRom == null ) {
            throw new IOException("No kickstart ROM file selected");
        }
        return Files.readAllBytes(kickstartRom.toPath() );
    }

    private void setupEmulator() throws Exception
    {
        if ( emulator != null )
        {
            emulator.destroy();
            emulator = null;
        }

        System.out.println("Setting up new emulator instance for "+Amiga.AMIGA_500);

        emulator = new Emulator(Amiga.AMIGA_500,loadKickstartRom());
        emulator.setCallbackInvocationTicks(1000000);
        emulator.setCallback( e ->
        {
            for (AppWindow window : windows)
            {
                System.out.println("Refreshing "+window);
                window.tick(e);
            }
        });
    }

    private Optional<File> selectFile(File existing) {
        return selectFile(existing,new FileFilter() {

            @Override
            public boolean accept(File f)
            {
                return true;
            }

            @Override
            public String getDescription()
            {
                return "*.*";
            }
        });
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

    private void registerWindow(AppWindow window)
    {
        window.pack();
        window.setVisible(true);
        windows.add(window);
        desktop.add(window);
    }

    public void run()
    {
        // setup menu bar
        setJMenuBar( createMenuBar() );

        // add internal windows
        registerWindow( new DisassemblyWindow(this) );
        setContentPane( desktop );

        // display window
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        if ( kickstartRom != null && kickstartRom.exists() ) {
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

    public void refreshAllWindows()
    {
        doWithEmulator( e -> e.invokeCallback() );
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
                    return f.isDirectory() || f.getName().toLowerCase().contains("kick");
                }

                @Override
                public String getDescription()
                {
                    return "Kickstart ROMs";
                }
            };

            final Optional<File> selection = selectFile(kickstartRom,filter);
            if ( selection.isPresent() ) {
                kickstartRom = selection.get();
                setupEmulator();
            }
        }));
        menu1.add( menuItem("Quit", () -> System.exit(0) ) );

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
}