package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TokenType;

public enum NodeType
{
    STATEMENT,
    LABEL,
    INSTRUCTION,
    IDENTIFIER,
    STRING,
    COMMENT,
    NUMBER,
    DIRECTIVE, // ORG
    AST, REGISTER, OPERAND;
}
