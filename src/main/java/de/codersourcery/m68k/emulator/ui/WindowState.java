package de.codersourcery.m68k.emulator.ui;

import org.apache.commons.lang3.StringUtils;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WindowState
{
    private String windowKey;
    private Rectangle locationAndSize;
    private boolean isEnabled;
    private boolean isVisible;

    public WindowState() {
    }

    @Override
    public String toString()
    {
        return "WindowState{" +
                "windowKey='" + windowKey + '\'' +
                ", locationAndSize=" + locationAndSize +
                ", isEnabled=" + isEnabled +
                ", isVisible=" + isVisible +
                '}';
    }

    public WindowState(WindowState other) {
        this.windowKey = other.windowKey;
        this.locationAndSize = new Rectangle( other.locationAndSize );
        this.isEnabled = other.isEnabled;
        this.isVisible = other.isVisible;
    }

    public WindowState createCopy() {
        return new WindowState(this);
    }

    public boolean isVisible()
    {
        return isVisible;
    }

    public void setVisible(boolean visible)
    {
        isVisible = visible;
    }

    public boolean isEnabled()
    {
        return isEnabled;
    }

    public void setEnabled(boolean enabled)
    {
        isEnabled = enabled;
    }

    public Rectangle getLocationAndSize()
    {
        return locationAndSize;
    }

    public void setLocationAndSize(Rectangle locationAndSize)
    {
        this.locationAndSize = locationAndSize;
    }

    public String getWindowKey()
    {
        return windowKey;
    }

    public void setWindowKey(String windowKey)
    {
        this.windowKey = windowKey;
    }

    public Map<String,String>  asMap()
    {
        final Map<String,String> map = new HashMap<>();
        map.put("window."+windowKey+".x", Integer.toString( locationAndSize.x ) );
        map.put("window."+windowKey+".y", Integer.toString( locationAndSize.y ) );
        map.put("window."+windowKey+".w", Integer.toString( locationAndSize.width ) );
        map.put("window."+windowKey+".h", Integer.toString( locationAndSize.height ) );
        map.put("window."+windowKey+".enabled", Boolean.toString( isEnabled) );
        map.put("window."+windowKey+".visible", Boolean.toString( isVisible) );
        return map;
    }

    public static List<WindowState> fromMap(Map<String,String> map)
    {
        final List<WindowState> result = new ArrayList<>();
        final Pattern keyPattern =
                Pattern.compile("^window\\.([_a-zA-Z0-9]+)\\.([_a-zA-Z0-9]+)");
        final Set<String> keys = new HashSet<>(map.keySet());
outer:
        while ( true )
        {
            for (String key : keys)
            {
                Matcher m = keyPattern.matcher( key );
                if ( m.matches() )
                {
                    final String windowKey = m.group( 1 );
                    final String prefix = "window." + windowKey+".";
                    final Function<String,String> prop = param ->
                    {
                        final String k = prefix + param;
                        final String value = map.get( k );
                        if ( StringUtils.isBlank(value) ) {
                            throw new RuntimeException("Internal error, config file lacks key '"+k+"'");
                        }
                        return value;
                    };
                    final int x = Integer.parseInt( prop.apply("x") );
                    final int y = Integer.parseInt( prop.apply("y") );
                    final int w = Integer.parseInt( prop.apply("w") );
                    final int h = Integer.parseInt( prop.apply("h") );
                    final boolean enabled = Boolean.parseBoolean( prop.apply("enabled"));
                    final boolean visible = Boolean.parseBoolean( prop.apply("visible"));

                    final WindowState state = new WindowState();
                    state.locationAndSize = new Rectangle(x,y,w,h);
                    state.windowKey = windowKey;
                    state.isEnabled = enabled;
                    state.isVisible = visible;
                    result.add(state);
                    keys.removeIf( s -> s.startsWith( prefix ) );
                    continue outer;
                }
            }
            break;
        }
        return result;
    }
}