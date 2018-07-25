package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.IASTNode;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A compilation debug/info/warning/error message.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationMessages
{
    enum Level {
        ERROR,WARN,INFO,DEBUG;
    }

    final class Message
    {
        public final StackTraceElement[] origin;
        public final CompilationUnit unit;
        public final String text;
        public final Level level;
        public final TextRegion location;

        private Message(CompilationUnit unit,String text, Level level, TextRegion location)
        {
            this.origin = new Exception("dummy").getStackTrace();
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
                    ",origin = "+Arrays.stream(origin).map( x -> x.toString() ).collect(Collectors.joining("\n" ) )+
                '}';
        }
    }

    boolean hasErrors();

    void clearMessages();

    List<Message> getMessages();

    // errors
    void error(CompilationUnit unit,String message);

    void error(CompilationUnit unit,String message, IASTNode node);

    void error(CompilationUnit unit,String message, IASTNode node,Throwable t);

    void error(CompilationUnit unit,String message, Token token);

    void error(CompilationUnit unit,String message, TextRegion region);

    // warnings
    void warn(CompilationUnit unit,String message);

    void warn(CompilationUnit unit,String message, IASTNode node);

    void warn(CompilationUnit unit,String message, Token token);

    void warn(CompilationUnit unit,String message, TextRegion region);

    // info
    void info(CompilationUnit unit,String message);

    void info(CompilationUnit unit,String message, IASTNode node);

    void info(CompilationUnit unit,String message, Token token);

    void info(CompilationUnit unit,String message, TextRegion region);
}
