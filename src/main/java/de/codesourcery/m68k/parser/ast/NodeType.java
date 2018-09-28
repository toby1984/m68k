package de.codesourcery.m68k.parser.ast;

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
