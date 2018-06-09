package de.codersourcery.m68k.assembler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IResource
{
    public InputStream createInputStream() throws IOException;

    public OutputStream createOutputStream() throws IOException;

    public boolean exists();
}
