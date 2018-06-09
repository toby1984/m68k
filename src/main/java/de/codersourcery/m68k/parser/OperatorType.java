package de.codersourcery.m68k.parser;

public enum OperatorType
{
    PLUS,MINUS,DIVIDE,MULTIPLY;

    public static OperatorType toOperatorType(char c)
    {
        switch(c)
        {
            case '+':
                return PLUS;
            case '-':
                return MINUS;
            case '*':
                return MULTIPLY;
            case '/':
                return DIVIDE;
            default:
                return null;
        }
    }
}
