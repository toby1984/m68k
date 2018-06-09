package de.codersourcery.m68k.parser;

import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LexerTest extends TestCase
{
    public void testLexNoWhiteSpace()
    {
        var expected = Arrays.asList(
                token(TokenType.TEXT,"abc",0),
                token(TokenType.DIGITS,"1234",4),
                token(TokenType.COLON,":",9),
                token(TokenType.COMMA,",",11),
                token(TokenType.DOT,".",13),
                token(TokenType.DOUBLE_QUOTE,'"',15),
                token(TokenType.SINGLE_QUOTE,"'",17),
                token(TokenType.EOL,'\n',19),

                token(TokenType.PLUS,'+',21),
                token(TokenType.MINUS,'-',22),
                token(TokenType.TIMES,'*',23),
                token(TokenType.SLASH,'/',24),
                token(TokenType.PARENS_OPEN,'(',25),
                token(TokenType.PARENS_CLOSE,')',26),
                token(TokenType.EOF,null,27)
        );
        var tokens = lex("abc 1234 : , . \" ' \n +-*/()", true );
        assertEquals( expected,tokens);
    }

    public void testLexWhiteSpace()
    {
        var expected = Arrays.asList(
                token(TokenType.TEXT,"abc",0),
                token(TokenType.WHITESPACE," ",3),
                token(TokenType.DIGITS,"1234",4),
                token(TokenType.WHITESPACE," ",8),
                token(TokenType.COLON,":",9),
                token(TokenType.WHITESPACE," ",10),
                token(TokenType.COMMA,",",11),
                token(TokenType.WHITESPACE," ",12),
                token(TokenType.DOT,".",13),
                token(TokenType.WHITESPACE," ",14),
                token(TokenType.DOUBLE_QUOTE,'"',15),
                token(TokenType.WHITESPACE," ",16),
                token(TokenType.SINGLE_QUOTE,"'",17),
                token(TokenType.WHITESPACE,"  ",18),
                token(TokenType.EOL,'\n',20),
                token(TokenType.EOF,null,21)
        );
        var tokens = lex("abc 1234 : , . \" '  \n" , false );
        assertEquals( expected,tokens);
    }

    private static void assertEquals(List<Token> expected,List<Token> actual)
    {
        final int padLen = 30;
        boolean fail = false;
        fail |= expected.size() != actual.size();
        final int len = Math.max( expected.size() , actual.size() );
        for ( int i = 0 ; i < len ; i++ )
        {
            String v1 = i < expected.size() ? expected.get(i).toString() : "--";
            String v2 = i < actual.size() ? actual.get(i).toString() : "--";

            final String error;
            if ( v1.equals(v2) ) {
                error = "";
            }
            else
            {
                error = " MISMATCH";
                fail = true;
            }
            System.out.println(StringUtils.rightPad(v1,30,' ') + " <-> " + v2 + error);
        }
        if ( fail )
        {
            fail("List's do not match");
        }
    }

    private static Token token(TokenType t,String value,int offset) {
        return new Token(t,value,offset);
    }

    private static Token token(TokenType t,char value,int offset) {
        return new Token(t,Character.toString(value),offset);
    }

    private static List<Token> lex(String input,boolean skipWhitespace)
    {
         var result = new ArrayList<Token>();
         final var lexer = new Lexer(new StringScanner(input));
         lexer.setSkipWhitespace(skipWhitespace);
         Token token = null;
         do
         {
            token = lexer.next();
            result.add(token);
         } while ( ! token.isEOF() );
         return result;
    }
}
