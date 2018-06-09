package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.ASTNode;
import org.apache.logging.log4j.message.Message;

import java.util.List;

public interface ICompilationMessages
{
    public enum Level {
        ERROR,WARN,INFO,DEBUG;
    }

    public static final class Message
    {
        public final String text;
        public final Level level;
        public final TextRegion location;

        private Message(String text, Level level, TextRegion location)
        {
            this.text = text;
            this.level = level;
            this.location = location;
        }

        public static Message of(String text, Level level) {
            return new Message(text,level,null);
        }

        public static Message of(String text, Level level, TextRegion location) {
            return new Message(text,level,location);
        }
    }

    boolean hasErrors();

    void clearMessages();

    List<Message> getMessages();

    // errors
    void error(String message);

    void error(String message, ASTNode node);

    void error(String message, ASTNode node,Throwable t);

    void error(String message, Token token);

    void error(String message, TextRegion region);

    // warnings
    void warn(String message);

    void warn(String message, ASTNode node);

    void warn(String message, Token token);

    void warn(String message, TextRegion region);

    // info
    void info(String message);

    void info(String message, ASTNode node);

    void info(String message, Token token);

    void info(String message, TextRegion region);
}
