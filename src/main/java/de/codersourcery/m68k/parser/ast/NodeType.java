package de.codersourcery.m68k.parser.ast;

import de.codersourcery.m68k.parser.TokenType;

public enum NodeType
{
    STATEMENT,
    LABEL,
    INSTRUCTION,
    IDENTIFIER,
    REGISTER_LIST,
    REGISTER_RANGE,
    STRING,
    COMMENT,
    NUMBER,
    DIRECTIVE, // ORG
    AST, REGISTER, OPERAND;
}
