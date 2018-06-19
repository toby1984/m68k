package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.ASTNode;
import org.apache.logging.log4j.message.Message;

import java.util.List;

/**
 * A compilation debug/info/warning/error message.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationMessages
{
    public enum Level {
        ERROR,WARN,INFO,DEBUG;
    }

    public static final class Message
    {
        public final CompilationUnit unit;
        public final String text;
        public final Level level;
        public final TextRegion location;

        private Message(CompilationUnit unit,String text, Level level, TextRegion location)
        {
            this.unit = unit;
            this.text = text;
            this.level = level;
            this.location = location;
        }

        public static Message of(CompilationUnit unit,String text, Level level) {
            return new Message(unit,text,level,null);
        }

        public static Message of(CompilationUnit unit,String text, Level level, TextRegion location) {
            return new Message(unit,text,level,location);
        }

        @Override
        public String toString()
        {
            return "Message{" +
                "unit=" + unit +
                ", text='" + text + '\'' +
                ", level=" + level +
                ", location=" + location +
                '}';
        }
    }

    boolean hasErrors();

    void clearMessages();

    List<Message> getMessages();

    // errors
    void error(CompilationUnit unit,String message);

    void error(CompilationUnit unit,String message, ASTNode node);

    void error(CompilationUnit unit,String message, ASTNode node,Throwable t);

    void error(CompilationUnit unit,String message, Token token);

    void error(CompilationUnit unit,String message, TextRegion region);

    // warnings
    void warn(CompilationUnit unit,String message);

    void warn(CompilationUnit unit,String message, ASTNode node);

    void warn(CompilationUnit unit,String message, Token token);

    void warn(CompilationUnit unit,String message, TextRegion region);

    // info
    void info(CompilationUnit unit,String message);

    void info(CompilationUnit unit,String message, ASTNode node);

    void info(CompilationUnit unit,String message, Token token);

    void info(CompilationUnit unit,String message, TextRegion region);
}
