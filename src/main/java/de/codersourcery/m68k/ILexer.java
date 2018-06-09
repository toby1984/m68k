package de.codersourcery.m68k;

public interface ILexer
{
    public boolean eof();

    public Token next();

    public Token peek();

    public boolean peek(TokenType type);
}
