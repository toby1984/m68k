package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Breakpoints;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

public class UIConfig
{
    private static final String KEY_KICKROM_LOCATION = "kickRomLocation";

    private final Map<String,WindowState> windowStates=
            new HashMap<>();

    private Breakpoints breakpoints = new Breakpoints();

    private File kickRomLocation;

    public List<WindowState> getWindowStates()
    {
        return windowStates.values().stream()
                .map(x->x.createCopy()).collect( Collectors.toList());
    }

    public File getKickRomLocation()
    {
        return kickRomLocation;
    }

    public void setKickRomLocation(File kickRomLocation)
    {
        this.kickRomLocation = kickRomLocation;
    }

    public Optional<WindowState> getWindowState(String windowKey) {
        return Optional.ofNullable( windowStates.get( windowKey ) );
    }

    public void setWindowState(WindowState state)
    {
        windowStates.put( state.getWindowKey(), state.createCopy() );
    }

    public void write(OutputStream out) throws IOException {

        final Properties props = new Properties();
        windowStates.forEach( (key,value) ->
        {
            final Map<String, String> tmp = value.asMap();
            props.putAll( tmp );
        });
        if ( kickRomLocation != null ) {
            props.put( KEY_KICKROM_LOCATION, kickRomLocation.getAbsolutePath() );
        }
        final Map<String, String> tmp = new HashMap<>();
        breakpoints.save(tmp);
        props.putAll(tmp);
        props.store( out ,"Automatically gnerated");
    }

    public static UIConfig read(InputStream in) throws IOException
    {
        final UIConfig result = new UIConfig();

        final Properties props = new Properties();
        props.load(in);

        // kickrom location
        final String location = props.getProperty(KEY_KICKROM_LOCATION);
        if ( location != null ) {
            final File file = new File(location);
            if ( file.exists() && file.isFile() && file.canRead() ) {
                result.kickRomLocation = file;
            }
        }
        final Map<String,String> map = new HashMap<>();
        for ( String key : props.stringPropertyNames() ) {
            map.put( key, props.getProperty( key ) );
        }
        final List<WindowState> states = WindowState.fromMap( map );
        states.forEach( state -> result.windowStates.put( state.getWindowKey(), state ) );

        result.breakpoints = Breakpoints.load(map);
        return result;
    }

    public Breakpoints getBreakpoints()
    {
        return breakpoints;
    }

    public void setBreakpoints(Breakpoints breakpoints)
    {
        Validate.notNull(breakpoints, "breakpoints must not be null");
        this.breakpoints = breakpoints;
    }
}
