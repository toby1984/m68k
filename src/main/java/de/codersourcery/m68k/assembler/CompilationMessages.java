package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.IASTNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for compilation debug/info/warning/error messages.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CompilationMessages implements ICompilationMessages
{
    private final List<Message> messages = new ArrayList<>();

    @Override
    public boolean hasErrors() {
        return messages.stream().anyMatch(m -> m.level == Level.ERROR );
    }

    @Override
    public void clearMessages() {
        messages.clear();
    }

    @Override
    public List<Message> getMessages()
    {
        return messages;
    }

    // errors
    @Override
    public void error(CompilationUnit unit,String message) {
        error(unit,message,(TextRegion) null);
    }

    @Override
    public void error(CompilationUnit unit,String message, IASTNode node) {
        error(unit,message,node == null ? null : node.getMergedRegion());
    }

    @Override
    public void error(CompilationUnit unit,String message, IASTNode node, Throwable t)
    {
        if ( t != null ) {
            t.printStackTrace();
        }
        error(unit,message,node);
    }

    @Override
    public void error(CompilationUnit unit,String message, Token token) {
        error(unit,message,token == null ? null : token.getRegion());
    }

    @Override
    public void error(CompilationUnit unit,String message, TextRegion region) {
        messages.add( Message.of(unit,message,Level.ERROR,region) );
    }

    // warnings
    @Override
    public void warn(CompilationUnit unit,String message) {
        warn(unit,message,(TextRegion) null);
    }

    @Override
    public void warn(CompilationUnit unit,String message, IASTNode node) {
        warn(unit,message,node == null ? null : node.getMergedRegion());
    }

    @Override
    public void warn(CompilationUnit unit,String message, Token token) {
        warn(unit,message,token == null ? null : token.getRegion());
    }

    @Override
    public void warn(CompilationUnit unit,String message, TextRegion region) {
        messages.add( Message.of(unit,message,Level.WARN,region) );
    }

    // info
    @Override
    public void info(CompilationUnit unit,String message) {
        info(unit,message,(TextRegion) null);
    }

    @Override
    public void info(CompilationUnit unit,String message, IASTNode node) {
        info(unit,message,node == null ? null : node.getMergedRegion());
    }

    @Override
    public void info(CompilationUnit unit,String message, Token token) {
        info(unit,message,token == null ? null : token.getRegion());
    }

    @Override
    public void info(CompilationUnit unit,String message, TextRegion region) {
        messages.add( Message.of(unit,message,Level.INFO,region) );
    }
}
