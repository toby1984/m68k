package de.codersourcery.m68k.emulator.ui;

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
    private final Map<String,WindowState> windowStates=
            new HashMap<>();

    public List<WindowState> getWindowStates()
    {
        return windowStates.values().stream()
                .map(x->x.createCopy()).collect( Collectors.toList());
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
        props.store( out ,"Automatically gnerated");
    }

    public static UIConfig read(InputStream in) throws IOException
    {
        final UIConfig result = new UIConfig();

        final Properties props = new Properties();
        props.load(in);
        final Map<String,String> map = new HashMap<>();
        for ( String key : props.stringPropertyNames() ) {
            map.put( key, props.getProperty( key ) );
        }
        final List<WindowState> states = WindowState.fromMap( map );
        states.forEach( state -> result.windowStates.put( state.getWindowKey(), state ) );
        return result;
    }
}
