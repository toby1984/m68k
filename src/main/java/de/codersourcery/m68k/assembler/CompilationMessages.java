package de.codersourcery.m68k.assembler;

import de.codersourcery.m68k.parser.TextRegion;
import de.codersourcery.m68k.parser.Token;
import de.codersourcery.m68k.parser.ast.ASTNode;

import java.util.ArrayList;
import java.util.List;

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
    public void error(String message) {
        error(message,(TextRegion) null);
    }

    @Override
    public void error(String message, ASTNode node) {
        error(message,node == null ? null : node.getMergedRegion());
    }

    @Override
    public void error(String message, ASTNode node, Throwable t)
    {
        if ( t != null ) {
            t.printStackTrace();
        }
        error(message,node);
    }

    @Override
    public void error(String message, Token token) {
        error(message,token == null ? null : token.getRegion());
    }

    @Override
    public void error(String message, TextRegion region) {
        messages.add( Message.of(message,Level.ERROR,region) );
    }

    // warnings
    @Override
    public void warn(String message) {
        warn(message,(TextRegion) null);
    }

    @Override
    public void warn(String message, ASTNode node) {
        warn(message,node == null ? null : node.getMergedRegion());
    }

    @Override
    public void warn(String message, Token token) {
        warn(message,token == null ? null : token.getRegion());
    }

    @Override
    public void warn(String message, TextRegion region) {
        messages.add( Message.of(message,Level.WARN,region) );
    }

    // info
    @Override
    public void info(String message) {
        info(message,(TextRegion) null);
    }

    @Override
    public void info(String message, ASTNode node) {
        info(message,node == null ? null : node.getMergedRegion());
    }

    @Override
    public void info(String message, Token token) {
        info(message,token == null ? null : token.getRegion());
    }

    @Override
    public void info(String message, TextRegion region) {
        messages.add( Message.of(message,Level.INFO,region) );
    }
}
