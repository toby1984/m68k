package de.codersourcery.m68k.assembler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IResource
{
    public static IResource stringResource(String s)
    {
        return new IResource()
        {
            @Override
            public InputStream createInputStream() throws IOException
            {
                return new ByteArrayInputStream( s.getBytes("UTF8" ) );
            }

            @Override
            public OutputStream createOutputStream() throws IOException
            {
                throw new IOException("Cannot write to static string");
            }

            @Override
            public boolean exists()
            {
                return true;
            }
        };
    }

    public InputStream createInputStream() throws IOException;

    public OutputStream createOutputStream() throws IOException;

    public boolean exists();
}
