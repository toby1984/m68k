package de.codersourcery.m68k.assembler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An abstraction for a (filesystem) resource that knows how to create {@link InputStream}s or {@link OutputStream}s
 * to read/write it.
 *
 * @author tobias.gierke@code-sourcery.de
 */
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

    /**
     * Returns a new input stream to read from this resource.
     *
     * @return
     * @throws IOException
     */
    public InputStream createInputStream() throws IOException;

    /**
     * Returns a new output stream to write to this resource.
     *
     * @return
     * @throws IOException
     */
    public OutputStream createOutputStream() throws IOException;

    /**
     * Returns whether the underlying resource exists.
     *
     * @return
     */
    public boolean exists();
}
