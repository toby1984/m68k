package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Breakpoints;
import de.codersourcery.m68k.emulator.memory.MemoryBreakpoints;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public class UIConfig
{
    private static final String KEY_KICKROM_LOCATION = "kickRomLocation";
    private static final String KEY_KICKROM_DISASM_LOCATION = "kickRomDisasmLocation";
    private static final String KEY_LIBRARY_MAPPING = "librarymapping";
    private static final String KEY_LIBRARY_FUNCTION_DESC_BASEDIR = "libraryfunctiondescbasedir";

    private final Map<AppWindow.WindowKey,WindowState> windowStates = new HashMap<>();

    private Breakpoints breakpoints = new Breakpoints();
    private MemoryBreakpoints memoryBreakpoints = new MemoryBreakpoints();

    private File kickRomDisassemblyLocation;
    private File kickRomLocation;
    private File libraryFunctionDescBaseDir;

    private final List<LibraryMapping> libraryMappings = new ArrayList<>();

    public UIConfig()
    {
    }

    public void deleteLibraryMappings()
    {
        libraryMappings.clear();
    }

    public static final class LibraryMapping
    {
        public final String libraryNameRegex;
        public final String descFileRegex;
        public final int index;

        public LibraryMapping(String libraryNameRegex, String descFileRegex, int index)
        {
            Validate.notNull( libraryNameRegex, "libraryNameRegex must not be null");
            Validate.notNull( descFileRegex, "descFileRegex must not be null");
            try
            {
                Pattern.compile( libraryNameRegex );
            }
            catch(Exception e) {
                throw new IllegalArgumentException( "Invalid regex: '"+libraryNameRegex+"'",e );
            }
            try
            {
                Pattern.compile( descFileRegex );
            }
            catch(Exception e) {
                throw new IllegalArgumentException( "Invalid regex: '"+descFileRegex+"'",e );
            }
            this.libraryNameRegex = libraryNameRegex;
            this.descFileRegex = descFileRegex;
            this.index = index;
        }

        public LibraryMapping withLibraryNameRegex(String regex) {
            return new LibraryMapping(regex,this.descFileRegex,this.index);
        }

        public LibraryMapping withDescFileRegex(String regex) {
            return new LibraryMapping(this.libraryNameRegex,regex,this.index);
        }

        @Override
        public String toString()
        {
            return "LibraryMapping[" +
                    "libraryNameRegex='" + libraryNameRegex + '\'' +
                    ", descFileRegex='" + descFileRegex + '\'' +
                    ", index=" + index +
                    ']';
        }
    }

    public List<LibraryMapping> getLibraryMappings() {
        return new ArrayList<>(libraryMappings);
    }

    public LibraryMapping addLibraryMapping(String libraryNameRegex, String descFileRegex)
    {
        final OptionalInt highestIndex = this.libraryMappings.stream().mapToInt( x -> x.index ).max();
        final int newIndex = highestIndex.isPresent() ? highestIndex.getAsInt()+1 : 0;
        final LibraryMapping result = new LibraryMapping(libraryNameRegex,descFileRegex,newIndex);
        this.libraryMappings.add(result);
        return result;
    }

    public boolean deleteLibraryMapping(LibraryMapping mapping)
    {
        Validate.notNull( mapping, "mapping must not be null" );
        return libraryMappings.removeIf( x ->x.index == mapping.index);
    }

    public File getKickRomLocation()
    {
        return kickRomLocation;
    }

    public void setKickRomLocation(File kickRomLocation)
    {
        this.kickRomLocation = kickRomLocation;
    }

    public Optional<WindowState> getWindowState(AppWindow.WindowKey windowKey) {
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
        if ( kickRomDisassemblyLocation != null ) {
            props.put( KEY_KICKROM_DISASM_LOCATION, kickRomDisassemblyLocation.getAbsolutePath() );
        }
        if ( libraryFunctionDescBaseDir != null ) {
            props.put( KEY_LIBRARY_FUNCTION_DESC_BASEDIR, libraryFunctionDescBaseDir.getAbsolutePath() );
        }

        for ( LibraryMapping mapping : libraryMappings ) {
            final String prefix = KEY_LIBRARY_MAPPING+"."+mapping.index+".";
            props.put( prefix+"libraryNameRegex", mapping.libraryNameRegex );
            props.put( prefix+"descFileRegex", mapping.descFileRegex);
        }

        final Map<String, String> tmp = new HashMap<>();
        breakpoints.save(tmp);
        memoryBreakpoints.save(tmp);
        props.putAll(tmp);
        props.store( out ,"Automatically gnerated");
    }

    public static UIConfig read(InputStream in) throws IOException
    {
        final UIConfig result = new UIConfig();

        final Properties props = new Properties();
        props.load(in);

        result.kickRomLocation = getFile(props,KEY_KICKROM_LOCATION);
        result.kickRomDisassemblyLocation = getFile(props,KEY_KICKROM_DISASM_LOCATION);
        result.libraryFunctionDescBaseDir = getDirectory(props,KEY_LIBRARY_FUNCTION_DESC_BASEDIR);

        final Map<String,String> map = new HashMap<>();
        for ( String key : props.stringPropertyNames() ) {
            map.put( key, props.getProperty( key ) );
        }

        final Set<Integer> alreadyParsed = new HashSet<>();
        for ( String key : map.keySet() )
        {
            if ( key.startsWith(KEY_LIBRARY_MAPPING) )
            {
                final int first = key.indexOf( '.' );
                final int last = key.indexOf( '.' , first+1 );
                final Integer idx = Integer.parseInt( key.substring( first+1,last ) );

                if ( alreadyParsed.contains( idx ) )
                {
                    continue;
                }
                alreadyParsed.add( idx );

                final String param = key.substring( last+1 );

                final String value = map.get(key);

                if ( param.equals("libraryNameRegex" ) )
                {
                    final String otherKey = KEY_LIBRARY_MAPPING+"."+idx+".descFileRegex";
                    final String otherValue = map.get(otherKey);
                    if ( otherValue == null ) {
                        throw new RuntimeException("Missing map key '"+otherKey+"'");
                    }
                    result.libraryMappings.add( new LibraryMapping( value, otherValue, idx ) );
                }
                else if ( param.equals("descFileRegex" ) )
                {
                    final String otherKey = KEY_LIBRARY_MAPPING+"."+idx+".libraryNameRegex";
                    final String otherValue = map.get(otherKey);
                    if ( otherValue == null ) {
                        throw new RuntimeException("Missing map key '"+otherKey+"'");
                    }
                    result.libraryMappings.add( new LibraryMapping( otherValue, value, idx ) );
                }
                else
                {
                    throw new RuntimeException("Unknown key '"+key+"'");
                }
            }
        }
        final List<WindowState> states = WindowState.fromMap( map );
        states.forEach( state -> result.windowStates.put( state.getWindowKey(), state ) );

        result.breakpoints = Breakpoints.load(map);
        result.memoryBreakpoints = MemoryBreakpoints.load(map);
        return result;
    }

    private static File getFile(Properties props,String key) {
        String location = props.getProperty(key);
        if ( location != null ) {
            final File file = new File(location);
            if ( file.exists() && file.isFile() && file.canRead() ) {
                return file;
            }
        }
        return null;
    }

    private static File getDirectory(Properties props,String key) {
        String location = props.getProperty(key);
        if ( location != null ) {
            final File file = new File(location);
            if ( file.exists() && file.isDirectory() && file.canRead() ) {
                return file;
            }
        }
        return null;
    }

    public Breakpoints getBreakpoints()
    {
        return breakpoints;
    }

    public void setMemoryBreakpoints(MemoryBreakpoints memoryBreakpoints)
    {
        Validate.notNull(memoryBreakpoints, "breakpoints must not be null");
        this.memoryBreakpoints = memoryBreakpoints;
    }

    public MemoryBreakpoints getMemoryBreakpoints()
    {
        return memoryBreakpoints;
    }

    public void setBreakpoints(Breakpoints breakpoints)
    {
        Validate.notNull(breakpoints, "breakpoints must not be null");
        this.breakpoints = breakpoints;
    }

    public File getKickRomDisassemblyLocation()
    {
        return kickRomDisassemblyLocation;
    }

    public void setKickRomDisassemblyLocation(File kickRomDisassemblyLocation)
    {
        this.kickRomDisassemblyLocation = kickRomDisassemblyLocation;
    }

    public File getLibraryFunctionDescBaseDir()
    {
        return libraryFunctionDescBaseDir;
    }

    public void setLibraryFunctionDescBaseDir(File libraryFunctionDescBaseDir)
    {
        this.libraryFunctionDescBaseDir = libraryFunctionDescBaseDir;
    }
}
