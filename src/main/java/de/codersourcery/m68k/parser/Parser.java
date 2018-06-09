package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.assembler.AddressingMode;
import de.codersourcery.m68k.parser.ast.*;
import org.apache.commons.lang3.Validate;

public class Parser
{
    private ILexer lexer;

    public AST parse(ILexer lexer) throws ParseException
    {
        Validate.notNull(lexer, "lexer must not be null");
        this.lexer = lexer;
        final AST result = new AST();
        while ( ! lexer.eof() )
        {
            while ( lexer.peek(TokenType.EOL) ) {
                lexer.next();
            }
            if ( lexer.eof() ) {
                break;
            }

            final StatementNode node = parseStatement();
            if ( node == null )
            {
                fail("Syntax error");
            }
            result.add(node);
        }
        return result;
    }

    private StatementNode parseStatement()
    {
        final StatementNode result = new StatementNode();

        ASTNode node = parseInstruction();
        if ( node != null ) {
            result.add(node);
        }

        node = parseLabel();
        if ( node != null ) {
            result.add(node);
        }

        node = parseInstruction();
        if ( node != null ) {
            result.add(node);
        }

        node = parseComment();
        if ( node != null ) {
            result.add(node);
        }
        return result.hasChildren() ? result : null;
    }

    private CommentNode parseComment()
    {
        Token token = lexer.peek();
        if ( token.is(TokenType.SEMICOLON ) )
        {
            lexer.next();
            final TextRegion region = token.getRegion();
            final StringBuilder buffer = new StringBuilder();
            lexer.setSkipWhitespace(false);
            try
            {
                token = lexer.peek();
                while ( ! token.isEOF() && ! token.isEOL() )
                {
                    region.merge(token.getRegion());
                    buffer.append( token.value );
                    lexer.next();
                    token = lexer.peek();
                }
            }
            finally
            {
                lexer.setSkipWhitespace(true);
            }
            return new CommentNode(buffer.toString(),region );
        }
        return null;
    }

    private InstructionNode parseInstruction()
    {
        final Token tok = lexer.peek();
        if ( tok.is(TokenType.TEXT) )
        {
            final InstructionType t = InstructionType.getType(tok.value);
            if ( t != null )
            {
                TextRegion region = tok.getRegion();

                final Token token = lexer.next();
                InstructionNode.OperandSize operandSize = InstructionNode.OperandSize.DEFAULT;
                if ( token.is(TokenType.DOT ) ) {
                    lexer.next();
                    region.merge(token.getRegion() );
                    final Token token3 = lexer.peek();
                    if ( ! token3.is(TokenType.TEXT ) || token3.value.length() != 1 )
                    {
                        fail("Expected operand size (.b or .l) but got "+token3.value);
                    }
                    lexer.next();
                    switch( Character.toLowerCase(token3.value.charAt(0) ) ) {
                        case 'b':
                            operandSize = InstructionNode.OperandSize.BYTE;
                            break;
                        case 'l':
                            operandSize = InstructionNode.OperandSize.LONG;
                            break;
                        default:
                            fail("Expected operand size (.b or .l) but got "+token3.value);
                    }
                }
                final InstructionNode insn = new InstructionNode(t,operandSize,region);
                ASTNode operand = null;
                switch( t.getOperandCount() )
                {
                    case 0:
                        break;
                    case 1:
                        operand = parseOperand();
                        if ( operand == null ) {
                            fail("Failed to parse operand");
                        }
                        insn.add( operand );
                        break;
                    case 2:
                        operand = parseOperand();
                        if ( operand == null ) {
                            fail("Failed to parse operand");
                        }
                        insn.add( operand );

                        if ( ! lexer.peek(TokenType.COMMA ) ) {
                            fail("Missing comma, instruction "+t+" requires 2 operands");
                        }
                        lexer.next();
                        operand = parseOperand();
                        if ( operand == null ) {
                            fail("Failed to parse second operand");
                        }
                        insn.add( operand );
                        break;
                    default:
                        throw new RuntimeException("Unsupported operand count: "+t);
                }
                return insn;
            }
        }
        return null;
    }

    private OperandNode parseOperand()
    {
        final ASTNode child = parseAtom();
        if ( child != null )
        {
            // TODO: Determine addressing mode
            final OperandNode op = new OperandNode(AddressingMode.NO_MEMORY_INDIRECT_ACTION0);
            op.add(child);
            return op;
        }
        return null;
    }

    private ASTNode parseAtom()
    {
        Token token = lexer.peek();
        ASTNode result = null;
        if ( token.is(TokenType.DIGITS) )
        {
            return parseNumber();
        }

        if ( token.is(TokenType.TEXT) )
        {
            result = parseNumber(); // $ff or %111011
            if ( result != null) {
                return result;
            }

            result = parseRegister();
            if ( result != null) {
                return result;
            }

            if ( Identifier.isValid( token.value ) )
            {
                lexer.next();
                return new IdentifierNode(new Identifier(token.value),token.getRegion());
            }
        }
        return parseString();
    }

    private ASTNode parseRegister()
    {
        final Token tok = lexer.peek();
        if ( tok.is(TokenType.TEXT) )
        {
            Register r = Register.parseRegister(tok.value);
            if ( r != null ) {
                lexer.next();
                return new RegisterNode(r,tok.getRegion());
            }
            lexer.next();
            final Token tok2 = lexer.peek();
            if ( tok2.is(TokenType.DIGITS ) )
            {
                r = Register.parseRegister(tok.value + tok2.value);
                if (r != null)
                {
                    lexer.next();
                    return new RegisterNode(r, tok.getRegion());
                }
            }
            lexer.push(tok);
            return null;
        }
        return null;
    }

    private StringNode parseString()
    {
        Token token = lexer.peek();
        if ( token.is( TokenType.DOUBLE_QUOTE) || token.is(TokenType.SINGLE_QUOTE ) ) {

            lexer.next();
            final TokenType expectedDelimiter = token.type;
            final StringBuilder buffer = new StringBuilder();
            final TextRegion region = token.getRegion();
            lexer.setSkipWhitespace(false);
            try
            {
                token = lexer.peek();
                while ( ! token.isEOF() && ! token.isEOL() && ! token.is(expectedDelimiter) )
                {
                    region.merge(token.getRegion());
                    buffer.append( token.value );
                    lexer.next();
                    token = lexer.peek();
                }
            }
            finally
            {
                lexer.setSkipWhitespace(true);
            }
            if ( ! token.is(expectedDelimiter) )
            {
                fail("Missing string delimiter, expected delimiter "+expectedDelimiter);
            }
            lexer.next();
            region.merge( lexer.next().getRegion() );
            return new StringNode(buffer.toString(),region );
        }
        return null;
    }

    private NumberNode parseNumber()
    {
        Token token = lexer.peek();
        final NumberNode.NumberType type;
        if ( token.is(TokenType.TEXT) )
        {
            if ( NumberNode.isHexNumber(token.value ) )
            {
                type = NumberNode.NumberType.HEXADECIMAL;
            }
            else if ( NumberNode.isBinaryNumber(token.value) )
            {
                type = NumberNode.NumberType.BINARY;
            } else {
                return null;
            }
        } else if ( token.is(TokenType.DIGITS) ) {
            type = NumberNode.NumberType.DECIMAL;
        } else {
            return null;
        }
        lexer.next();
        long value = NumberNode.parse(token.value,type);
        return new NumberNode(value,type,token.getRegion());
    }
    private ASTNode parseLabel()
    {
        final Token token1 = lexer.peek();
        if ( token1.is(TokenType.TEXT) )
        {
            if ( Identifier.isValid(token1.value ) )
            {
                lexer.next();
                final Token token2 = lexer.peek();
                if ( ! token2.is(TokenType.COLON ) )
                {
                    lexer.push(token1);
                    return null;
                }
                lexer.next();
                final TextRegion region = token1.getRegion();
                final Identifier identifier = new Identifier(token1.value);
                return new LabelNode(new Label(identifier), region);
            }
        }
        return null;
    }

    private void fail(String message) {
        fail(message,lexer.peek().offset);
    }

    private void fail(String message,Token token) {
        fail(message,token.getRegion());
    }

    private void fail(String message,TextRegion region) {
        fail(message,region.getStartingOffset());
    }

    private void fail(String message,int offset) {
        throw new ParseException(message, offset );
    }
}