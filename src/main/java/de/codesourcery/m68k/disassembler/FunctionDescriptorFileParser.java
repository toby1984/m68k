package de.codesourcery.m68k.disassembler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FunctionDescriptorFileParser
{
    private static final Logger LOG = LogManager.getLogger( FunctionDescriptorFileParser.class.getName() );

    private static final Pattern BIAS_LINE = Pattern.compile("##bias\\s+([0-9]+).*");
    private static final String ARG_LIST = "\\w*(?:,\\w+)*";
    private static final String REG_LIST = "\\w*(?:[,/]\\w+)*";
    private static final Pattern FUNC_LINE = Pattern.compile("([_a-zA-Z0-9]+)\\(("+ ARG_LIST +")\\)\\(("+ REG_LIST +")\\)");

    public TreeMap<Integer, Disassembler.FunctionDescription> parse(InputStream fileInput) throws IOException
    {
        final TreeMap<Integer, Disassembler.FunctionDescription> result = new TreeMap<>();
        final StringBuilder signature = new StringBuilder();

        int offset = -30;
        boolean isPublic = true;
        int lineNo = 0;
        try ( final BufferedReader reader = new BufferedReader( new InputStreamReader(fileInput) ) )
        {
            String line;
            while ( ( line = reader.readLine() ) != null )
            {
                lineNo++;
                if ( line.startsWith( "*" ) )
                {
                    continue;
                }

                Matcher m = BIAS_LINE.matcher( line );
                if ( m.matches() ) {
                    offset = -(Integer.parseInt( m.group(1) ) );
                    continue;
                }
                if ( line.startsWith("##public" ) ) {
                    isPublic = true;
                    continue;
                }
                if ( line.startsWith("##private" ) ) {
                    isPublic = false;
                    continue;
                }
                m = FUNC_LINE.matcher( line.trim() );
                if ( m.matches() )
                {
                    final String function = m.group(1);
                    final String[] paramNames = m.group(2).split(",");
                    final String[] registerNames = m.group(3).split("[,/]");
                    if ( paramNames.length != registerNames.length )
                    {
                        LOG.info(  "OFFENDING LINE: >"+line+"<" );
                        throw new RuntimeException("Argument count does not match register count on line "+lineNo);
                    }
                    signature.setLength( 0 );
                    for ( int i = 0 ; i < paramNames.length ; i++ )
                    {
                        if ( signature.length() > 0 ) {
                            signature.append(",");
                        }
                        signature.append(paramNames[i]+"("+registerNames[i]+")");
                    }
                    final Disassembler.FunctionDescription existing = result.put( offset, new Disassembler.FunctionDescription( function, offset, isPublic, signature.toString() ) );
                    if ( existing != null ) {
                        LOG.info(  "OFFENDING LINE: >"+line+"<" );
                        throw new RuntimeException("Duplicate offset "+offset+" already used by "+existing);
                    }
                    offset -= 6;
                } else {
                    LOG.info( "UNMATCHED: >"+line+"<" );
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception
    {
        final File file = new File("/home/tgierke/Downloads/tmp/NDK_3.9/Include/fd/exec_lib.fd");
        final TreeMap<Integer, Disassembler.FunctionDescription> result = new FunctionDescriptorFileParser().parse( new FileInputStream( file ) );
        result.values().stream().filter( x -> x.isPublic).forEach( System.out::println );
    }

    /*
#!/usr/bin/python
# usage: fd2lvo lib.fd >lib.i

from uuid import uuid4
from sys import argv, exit
from re import match

if len(argv) != 2:  exit("usage: fd2lvo lib.fd >lib.i")

with open(argv[1]) as f:
  head = 'I'+uuid4().hex[:8].upper()
  print " ifnd "+head+'\n'+head+" = 1"

  offs = -30
  pub = True

  for line in f.readlines():
    l = line.lower()

    if l[0] == '*':  continue
    elif match(r"##public", l):  pub = True
    elif match(r"##private", l):  pub = False

    m = match(r"##bias (?P<v>[0-9]+)", l)
    if m:  offs = -int(m.group('v'))

    m = match(r"(?P<v>\w+)[(]", line)
    if m:
      if pub:  print "LVO"+m.group('v')+" = "+str(offs)
      offs -= 6

  print " endif"
     */
}
