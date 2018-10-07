package de.codesourcery.m68k.utils;

import org.apache.logging.log4j.Logger;

public class DeduplicatingLogger
{
    private final Logger delegate;
    private String lastMessage;
    private int repeatCount;

    public DeduplicatingLogger(Logger delegate)
    {
        this.delegate = delegate;
    }

    public void warn(String message) {
        delegate.warn(message);
    }

    public void debug(String message) {
        delegate.debug(message);
    }

    public void info(String message) {
        if ( lastMessage == null )
        {
            delegate.info(message);
            lastMessage = message;
            repeatCount = 0;
        } else if ( lastMessage.equals( message ) ) {
            if ( (++repeatCount & 0b100000) == 0b100000 ) {
                delegate.info( message +" (repeated "+repeatCount+" times)");
                repeatCount = 0;
            }
        } else {
            delegate.info( lastMessage+" (repeated "+repeatCount+" times)");
            delegate.info( message );
            repeatCount = 0;
            lastMessage = message;
        }
    }
}
