package de.codesourcery.m68k.utils;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class OpcodeFileReader
{
    public static final String INSTRUCTION_MAPPINGS = "/68000_instructions.properties";

    public interface IOpcodeConsumer
    {
        void consume(int opcode, String instruction, String encoding);
    }

    public static void parseFile(IOpcodeConsumer consumer) throws IOException
    {
        final InputStream input = OpcodeFileReader.class.getResourceAsStream(INSTRUCTION_MAPPINGS);
        try ( final BufferedReader in = new BufferedReader(new InputStreamReader(input) ) )
        {
            String line = null;

            int lineNo = 0;
            while ( ( line = in.readLine() ) != null )
            {
                lineNo++;
                final int len = line.length();

                int i = 0;
                // parse opcode (integer) value
                while ( i < len && Character.isDigit( line.charAt( i ) ) )
                {
                    i++;
                }
                if ( i == len )
                {
                    throw new EOFException( "Premature EOF at line " + lineNo );
                }
                final int opCode = Integer.parseInt( line.substring( 0, i ) );
                // skip non-letters
                while ( i < len && !Character.isLetter( line.charAt( i ) ) )
                {
                    i++;
                }
                // parse instruction encoding name
                if ( i == len )
                {
                    throw new EOFException( "Premature EOF at line " + lineNo );
                }
                int start = i;
                while ( i < len )
                {
                    final char c = line.charAt( i );
                    if ( Character.isLetter( c ) || c == '_' || Character.isDigit( c ) )
                    {
                        i++;
                    }
                    else
                    {
                        break;
                    }
                }
                if ( i == len )
                {
                    throw new EOFException( "Premature EOF at line " + lineNo );
                }
                final String insnEncName = line.substring( start, i );
                // skip non-letters
                while ( i < len && !Character.isLetter( line.charAt( i ) ) )
                {
                    i++;
                }
                if ( i == len )
                {
                    throw new EOFException( "Premature EOF at line " + lineNo );
                }
                // parse instruction name
                start = i;
                while ( i < len )
                {
                    final char c = line.charAt( i );
                    if ( Character.isLetter( c ) || c == '_' || Character.isDigit( c ) )
                    {
                        i++;
                    }
                    else
                    {
                        break;
                    }
                }
                if ( i == len )
                {
                    throw new EOFException( "Premature EOF at line " + lineNo );
                }
                final String insnName = line.substring( start, i );
                consumer.consume( opCode,insnName,insnEncName );
            }
        }
    }
}
