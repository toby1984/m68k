package de.codersourcery.m68k.parser;

import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

public class Lexer implements ILexer
{
    private IScanner scanner;
    private final List<Token> tokens = new ArrayList<>();
    private boolean skipWhitespace = true;
    private final StringBuilder buffer = new StringBuilder();

    public Lexer(IScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public boolean eof()
    {
        return peek().isEOF();
    }

    @Override
    public Token next()
    {
        if ( tokens.isEmpty() )
        {
            parse();
        }
        return tokens.remove(0);
    }

    @Override
    public Token peek()
    {
        if ( tokens.isEmpty() )
        {
            parse();
        }
        return tokens.get(0);
    }

    @Override
    public void push(Token token)
    {
        if ( token == null )
        {
            throw new IllegalArgumentException("token must not be null");
        }
        tokens.add(0,token);
    }

    @Override
    public boolean peek(TokenType type)
    {
        return peek().is(type);
    }

    private void token(TokenType type,String value, int offset)
    {
        tokens.add(new Token(type,value,offset));
    }

    private void charToken(TokenType t)
    {
        int offset = scanner.offset();
        tokens.add( new Token(t,Character.toString(scanner.next()),offset));
    }

    private void addEOF(int offset)
    {
        token(TokenType.EOF,null,offset);
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
    }

    private void parse()
    {
        if ( ! tokens.isEmpty() ) {
            return;
        }
        if ( scanner.eof() )
        {
            addEOF(scanner.offset());
            return;
        }
        buffer.setLength(0);

        int start = scanner.offset();

        if ( skipWhitespace )
        {
            while (!scanner.eof() && isWhitespace(scanner.peek()))
            {
                scanner.next();
            }
        }
        else
            {
            while (!scanner.eof() && isWhitespace(scanner.peek()))
            {
                buffer.append( scanner.next() );
            }
            if ( buffer.length() > 0 )
            {
                token(TokenType.WHITESPACE,buffer.toString(),start );
                return;
            }
        }

        start = scanner.offset();
outer:
        while ( ! scanner.eof() )
        {
            final char c = scanner.peek();
            switch(c)
            {
                case '#':
                    parseBuffer(start);
                    charToken(TokenType.HASH);
                    return;
                case '+':
                    parseBuffer(start);
                    charToken(TokenType.PLUS);
                    return;
                case '-':
                    parseBuffer(start);
                    charToken(TokenType.MINUS);
                    return;
                case '*':
                    parseBuffer(start);
                    charToken(TokenType.TIMES);
                    return;
                case '/':
                    parseBuffer(start);
                    charToken(TokenType.SLASH);
                    return;
                case '\n':
                    parseBuffer(start);
                    charToken(TokenType.EOL);
                    return;
                case '\t':
                case ' ':
                    break outer;
                case '(':
                    parseBuffer(start);
                    charToken(TokenType.PARENS_OPEN);
                    return;
                case ')':
                    parseBuffer(start);
                    charToken(TokenType.PARENS_CLOSE);
                    return;
                case ',':
                    parseBuffer(start);
                    charToken(TokenType.COMMA);
                    return;
                case ':':
                    parseBuffer(start);
                    charToken(TokenType.COLON);
                    return;
                case ';':
                    parseBuffer(start);
                    charToken(TokenType.SEMICOLON);
                    return;
                case '.':
                    parseBuffer(start);
                    charToken(TokenType.DOT);
                    return;
                case '"':
                    parseBuffer(start);
                    charToken(TokenType.DOUBLE_QUOTE);
                    return;
                case '\'':
                    parseBuffer(start);
                    charToken(TokenType.SINGLE_QUOTE);
                    return;
                default:
            }
            buffer.append(scanner.next());
        }
        parseBuffer(start);

        if ( tokens.isEmpty() )
        {
            addEOF(scanner.offset());
        }
    }

    private void parseBuffer(int offset)
    {
        final int len = buffer.length();
        if ( len == 0 ) {
            return;
        }
        final String s = buffer.toString();
        buffer.setLength(0);

        boolean allDigits = true;
        for ( int i = len-1; i>=0 ; i-- )
        {
            if ( ! Character.isDigit(s.charAt(i) ) ) {
                allDigits = false;
                break;
            }
        }
        if ( allDigits )
        {
            token(TokenType.DIGITS,s,offset);
        } else {
            token(TokenType.TEXT,s,offset);
        }
    }

    @Override
    public void setSkipWhitespace(boolean newValue)
    {
        if ( newValue && ! this.skipWhitespace)
        {
            // keep whitespace -> ignore whitespace
            // remove any whitespace tokens we have might've parsed already
            tokens.removeIf(tok -> tok.is(TokenType.WHITESPACE));
        }
        else if ( ! newValue && this.skipWhitespace )
        {
            // ignore whitespace -> keep whitespace
            // reset scanner just in case we parsed ahead and already skipped some whitespace
            if ( ! tokens.isEmpty() )
            {
                scanner.setOffset(tokens.get(0).offset);
                tokens.clear();
            }
        }
        this.skipWhitespace = newValue;
    }

    @Override
    public boolean isSkipWhitespace()
    {
        return skipWhitespace;
    }

    @Override
    public String toString()
    {
        if ( tokens.isEmpty() ) {
            parse();
        }
        return tokens.get(0).toString();
    }
}
