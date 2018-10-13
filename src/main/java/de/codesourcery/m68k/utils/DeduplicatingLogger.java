package de.codesourcery.m68k.utils;

import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

public class DeduplicatingLogger
{
    private final Logger delegate;

    protected final class LogHelper
    {
        private String lastMessage;
        private int repeatCount;
        private final BiConsumer<Logger,String> func;

        public LogHelper(BiConsumer<Logger,String> func)
        {
            this.func = func;
        }

        public void log(String message)
        {
            if ( lastMessage == null )
            {
                delegate.info(message);
                lastMessage = message;
                repeatCount = 0;
            } else if ( lastMessage.equals( message ) ) {
                if ( (++repeatCount & 0b100000) == 0b100000 ) {

                    func.accept( delegate,message +" (repeated "+repeatCount+" times)");
                    repeatCount = 0;
                }
            } else {
                func.accept( delegate,lastMessage+" (repeated "+repeatCount+" times)");
                func.accept( delegate, message );
                repeatCount = 0;
                lastMessage = message;
            }
        }
    }

    private final LogHelper infoHelper = new LogHelper( (log,msg) -> log.info( msg ) );
    private final LogHelper warnHelper = new LogHelper( (log,msg) -> log.warn( msg ) );
    private final LogHelper debugHelper = new LogHelper( (log,msg) -> log.debug( msg ) );

    public DeduplicatingLogger(Logger delegate)
    {
        this.delegate = delegate;
    }

    public void warn(String message) {
        warnHelper.log(message);
    }

    public void debug(String message) {
        debugHelper.log(message);
    }

    public void info(String message) {
        infoHelper.log(message);
    }
}
