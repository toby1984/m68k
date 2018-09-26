package de.codersourcery.m68k.disassembler;

import de.codersourcery.m68k.emulator.Emulator;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LibraryCallResolver implements Disassembler.IIndirectCallResolver
{
    public interface ILibraryMatcher
    {
        boolean matches(String libraryName,int libraryVersion);
    }

    private final Emulator emulator;

    private final List<ILibraryMatcher> matchers = new ArrayList<>();
    private final List<Map<Integer, Disassembler.FunctionDescription>> functionDescriptions = new ArrayList<>();

    public LibraryCallResolver(Emulator emulator)
    {
        Validate.notNull( emulator, "emulator must not be null" );
        this.emulator = emulator;
    }

    public void unregisterAll() {
        matchers.clear();
        functionDescriptions.clear();
    }

    public void register(File descriptorFile, ILibraryMatcher matcher) throws IOException
    {
        Validate.notNull( descriptorFile, "descriptorFile must not be null" );
        Validate.notNull( matcher, "matcher must not be null" );

        try ( FileInputStream in = new FileInputStream( descriptorFile ) )
        {
            final TreeMap<Integer, Disassembler.FunctionDescription> parsed = new FunctionDescriptorFileParser().parse( in );
            if ( parsed.isEmpty() ) {
                throw new IOException("File "+descriptorFile.getAbsolutePath()+" did not contain any function descriptions?");
            }
            this.matchers.add( matcher );
            this.functionDescriptions.add( parsed );
        }
    }

    @Override
    public Disassembler.FunctionDescription resolve(int addressRegister, int offset)
    {
        // assume that address register points to a struct Library
        // get version number >> $14
        // get name >> $0a
        if ( ( offset & 1 ) == 0 ) { // need an even address
            final int libBase = emulator.cpu.addressRegisters[ addressRegister ];
            final int version = emulator.memory.readWord( libBase + 0x14 );
            int nameStart = emulator.memory.readLong( libBase + 0x0a );

            final StringBuilder libName = new StringBuilder();
            int idx = 0;
            for ( ; idx < 64 ; idx++ )
            {
                final int c = emulator.memory.readByte( nameStart+idx ) & 0xff;
                if ( c < 32 || c >= 127 ) {
                    break;
                }
                libName.append( (char) c );
            }
            if ( idx > 0 )
            {
                final String name = libName.toString();
                for (int i = 0, matchersSize = matchers.size(); i < matchersSize; i++)
                {
                    final ILibraryMatcher m = matchers.get( i );
                    if ( m.matches( name, version ) )
                    {
                        return functionDescriptions.get( i ).get( Integer.valueOf( offset ) );
                    }
                }
            }
        }
        return null;
    }
}
