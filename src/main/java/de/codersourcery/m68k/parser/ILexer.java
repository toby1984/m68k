package de.codersourcery.m68k.parser;

public interface ILexer
{
    public boolean eof();

    public Token next();

    public Token peek();

    public void push(Token token);

    public boolean peek(TokenType type);

    public boolean isSkipWhitespace();

    public void setSkipWhitespace(boolean yesNo);
}
