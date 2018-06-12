package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.assembler.arch.InstructionType;
import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.assembler.arch.Scaling;
import de.codersourcery.m68k.parser.ast.*;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

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

                lexer.next(); // consume instruction
                OperandSize operandSize = t.defaultOperandSize;
                if ( lexer.peek(TokenType.DOT ) )
                {
                    final Token token = lexer.next(); // consume dot
                    region.merge(token.getRegion() );
                    final Token token3 = lexer.peek();
                    if ( ! token3.is(TokenType.TEXT ) || token3.value.length() != 1 )
                    {
                        fail("Expected operand size (.b or .l) but got "+token3.value);
                    }
                    lexer.next();
                    switch( Character.toLowerCase(token3.value.charAt(0) ) ) {
                        case 'b':
                            operandSize = OperandSize.BYTE;
                            break;
                        case 'w':
                            operandSize = OperandSize.WORD;
                            break;
                        case 'l':
                            operandSize = OperandSize.LONG;
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

    private static boolean evaluatesToNumber(ASTNode node)
    {
        // FIXME: Does not recognize expressions yet...
        return node != null && node.is(NodeType.NUMBER);
    }

    private ASTNode parseExpression()
    {
        // FIXME: Implement parsing expressions here...
        return parseAtom();
    }

    private OperandNode parseOperand()
    {
        if ( lexer.peek(TokenType.HASH) ) // move #$0a,d0
        {
            final Token hash = lexer.next();
            ASTNode value = parseExpression();
            if ( value == null ) {
                fail("Failed to parse immediate mode operand");
            }
            final OperandNode op = new OperandNode(AddressingMode.IMMEDIATE_VALUE,Scaling.IDENTITY, hash.getRegion() );
            op.setValue(value);
            return op;
        }

        /*
         * move d0,...     => NO_MEMORY_INDIRECT_ACTION0
         * move a0,...     => NO_MEMORY_INDIRECT_ACTION0
         *
         * move $0a(a0),...   => MEMORY_INDIRECT_WITH_WORD_OUTER_DISPLACEMENT
         * move $0a(a0,d5.w),...   => MEMORY_INDIRECT_WITH_WORD_OUTER_DISPLACEMENT
         * move $0a(a0,d5.w*1),...   => MEMORY_INDIRECT_WITH_WORD_OUTER_DISPLACEMENT
         *
         * move ($0a,a0),...
         * move ($0a,a0,d5.l),...
         * move ($0a,a0,d5.l*2),...
         *
         * move (a0),   => MEMORY_INDIRECT_WITH_WORD_OUTER_DISPLACEMENT
         * move (a0)+,  = AddressingMode.INDIRECT_POSTINDEXED_WITH_WORD_OUTER_DISPLACEMENT
         * move -(a0)
         *
         * ([bd,An],An.W*scale,od)
         */
        final List<Token> tokens = new ArrayList<>();
        Token indirectAddressing = null;
        Token preDecrement = null;
        ASTNode innerDisplacement = null;
        RegisterNode baseRegister = null;
        if ( lexer.peek(TokenType.MINUS ) )
        {
            preDecrement = lexer.next();
            if (lexer.peek(TokenType.PARENS_OPEN)) // MOVE -(a0),...
            {
                tokens.add( preDecrement );
                indirectAddressing = lexer.next();
                tokens.add(indirectAddressing);

                baseRegister = parseRegister();
                if ( baseRegister == null ) {
                    fail("Expected an address register");
                }
                if ( ! baseRegister.isAddressRegister() ) {
                    fail("Expected an address register",baseRegister.getRegion());
                }
                if ( ! lexer.peek(TokenType.PARENS_CLOSE ) ) {
                    fail("Expected closing parens");
                }
                tokens.add(lexer.next());
                final OperandNode op = new OperandNode(AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                op.setValue(baseRegister);
                return op;
            }
            fail("Misplaced unary minus");
            return null; // never reached
        }

        // MOVE (....
        // MOVE $123(
        // MOVE Dx....

        if ( ! lexer.peek(TokenType.PARENS_OPEN ) )
        {
            // expecting inner displacement value outside parens
            innerDisplacement = parseExpression();
            if ( innerDisplacement.is(NodeType.REGISTER) ) // MOVE D3,D4
            {
                Register r = innerDisplacement.asRegister().register;
                AddressingMode mode = null;
                if ( r.isAddress() ) {
                    mode = AddressingMode.ADDRESS_REGISTER_DIRECT;
                } else if ( r.isData() ) {
                    mode = AddressingMode.DATA_REGISTER_DIRECT;
                } else {
                    fail("Expected a data or address register",innerDisplacement.getRegion());
                }
                final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                op.setValue(innerDisplacement);
                return op;
            }
            // MOVE $1234
            if ( ! evaluatesToNumber(innerDisplacement ) ) {
                fail("Expected a displacement value",innerDisplacement.getRegion());
            }
        }

        if ( ! lexer.peek(TokenType.PARENS_OPEN ) ) { // LEA $1234,
            final OperandNode op = new OperandNode(AddressingMode.IMMEDIATE_ADDRESS,Scaling.IDENTITY, Token.getMergedRegion(tokens));
            op.setValue(innerDisplacement);
            return op;
        }
        tokens.add(lexer.next()); // consume opening parens

        if ( innerDisplacement == null )
        {
            // MOVE (

            // check for inner displacement (bd,...) syntax
            ASTNode eaOrRegister = parseExpression();
            if ( eaOrRegister == null ) {
                fail("Expected an address register");
            }
            if ( evaluatesToNumber(eaOrRegister ) )
            {
                // MOVE ($0a,
                innerDisplacement = eaOrRegister;
            }
            else if ( eaOrRegister.is(NodeType.REGISTER ) )
            {
                // move (A0
                if ( ! eaOrRegister.asRegister().isAddressRegister() ) {
                    fail("Expected an address register",eaOrRegister.getRegion());
                }
                baseRegister = eaOrRegister.asRegister();

                if ( lexer.peek(TokenType.PARENS_CLOSE ) ) // MOVE (a0),...
                {
                    tokens.add(lexer.next());

                    if ( lexer.peek(TokenType.PLUS) ) {
                        tokens.add(lexer.next());
                        final OperandNode op = new OperandNode(AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                        op.setValue(baseRegister);
                        return op;
                    }
                    final OperandNode op = new OperandNode(AddressingMode.ADDRESS_REGISTER_INDIRECT,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                    op.setValue(baseRegister);
                    return op;
                }

            } else {
                fail("Expected a displacement value or address register",eaOrRegister.getRegion());
            }

            if ( lexer.peek(TokenType.PARENS_CLOSE) ) {
                tokens.add(lexer.next());
                final ASTNode value;
                final AddressingMode mode;
                if ( baseRegister == null && innerDisplacement != null ) {
                    value = innerDisplacement;
                    innerDisplacement = null;
                    mode = AddressingMode.MEMORY_INDIRECT;
                } else if ( baseRegister != null && innerDisplacement == null ) {
                    mode = AddressingMode.ADDRESS_REGISTER_INDIRECT;
                    value = baseRegister;
                } else if ( baseRegister != null && innerDisplacement != null ) {
                    mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
                    value = baseRegister;
                } else {
                    throw new RuntimeException("Unreachable code reached");
                }
                final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                op.setValue(value);
                op.setInnerDisplacement(innerDisplacement);
                return op;
            }

            // MOVE (stuff,
            if ( ! lexer.peek(TokenType.COMMA) ) {
                fail("Expected a comma followed by an address register");
            }
            tokens.add(lexer.next());
        }

        if ( baseRegister == null )
        {
            ASTNode eaOrRegister = parseExpression();
            if ( eaOrRegister == null ) {
                fail("Expected an address register");
            }
            if ( ! eaOrRegister.is(NodeType.REGISTER ) || ! eaOrRegister.asRegister().isAddressRegister() ) {
                fail("Expected an address register");
            }
            baseRegister = eaOrRegister.asRegister();
        }

        if ( lexer.peek(TokenType.PARENS_CLOSE ) ) {
            tokens.add(lexer.next());
            AddressingMode mode = innerDisplacement != null ? AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT :
                    AddressingMode.ADDRESS_REGISTER_INDIRECT;
            final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
            op.setInnerDisplacement(innerDisplacement);
            op.setValue(baseRegister);
            return op;
        }

        if ( ! lexer.peek(TokenType.COMMA) ) {
            fail("Expected a comma followed by an address register");
        }
        tokens.add(lexer.next());

        // parse index register
        ASTNode eaOrRegister = parseAtom();
        if ( eaOrRegister == null ) {
            fail("Expected an index register");
        }
        if ( ! eaOrRegister.is(NodeType.REGISTER) || ! eaOrRegister.asRegister().isAddressRegister() ) {
            fail("Expected an index register",eaOrRegister.getRegion());
        }
        final RegisterNode indexRegister = eaOrRegister.asRegister();

        Scaling scaling = Scaling.IDENTITY;
        if ( lexer.peek(TokenType.TIMES ) ) {
            tokens.add(lexer.next());
            final ASTNode tmp = parseNumber();
            if ( tmp  == null ) {
                fail("Expected a scaling value (1,2,4,8)");
            }
            final long value = tmp.asNumber().getValue();
            if ( value == 1 ) {
                scaling = Scaling.IDENTITY;
            } else if ( value == 2 ) {
                scaling = Scaling.TWO;
            } else if ( value == 4 ) {
                scaling = Scaling.FOUR;
            } else if ( value == 8 ) {
                scaling = Scaling.EIGHT;
            } else {
                fail("Invalid scaling value ,needs to be one of {1,2,4,8}");
            }
        }

        // parse outer displacement (if any)
        ASTNode outerDisplacement = null;
        if ( lexer.peek(TokenType.COMMA) )
        {
            tokens.add(lexer.peek());
            outerDisplacement = parseAtom();
            if ( outerDisplacement == null ) {
                fail("Expected outer displacement value");
            }
            if( ! evaluatesToNumber(outerDisplacement ) ) {
                fail("Invalid outer displacement value",outerDisplacement.getRegion());
            }
        }

        // parse closing parens
        if ( ! lexer.peek(TokenType.PARENS_CLOSE ) ) {
            fail("Missing closing parens");
        }
        tokens.add(lexer.next());

        AddressingMode mode = null;
        if ( scaling == Scaling.IDENTITY ) {
            mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT_AND_SCALE_OPTIONAL;
        } else {
            mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT_AND_SCALE;
        }

        final OperandNode op = new OperandNode(mode, scaling,Token.getMergedRegion(tokens) );
        op.setValue( baseRegister );
        op.setIndexRegister(indexRegister);

        if ( innerDisplacement != null )
        {
            op.setInnerDisplacement(innerDisplacement);
        }
        if ( outerDisplacement != null ) {
            op.setOuterDisplacement( outerDisplacement );
        }
        return op;
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

    private RegisterNode parseRegister()
    {
        final Token prefix = lexer.peek();
        if ( prefix.is(TokenType.TEXT) )
        {
            lexer.next(); // consume prefix
            Token registerNumber = null;
            Register r = Register.parseRegister(prefix.value);
            if ( r == null )
            {
                if ( ! lexer.peek(TokenType.DIGITS ) ) // not Rx
                {
                    lexer.push(prefix);
                    return null;
                }
                registerNumber = lexer.peek();
                r = Register.parseRegister(prefix.value + registerNumber.value);
                if ( r == null )
                {
                    lexer.push(prefix);
                    return null;
                }
                lexer.next(); // consume register number
            }

            OperandSize operandSize = null;
            Token dot = null;
            Token sizeSpec = null;
            if( lexer.peek(TokenType.DOT) )
            {
                if ( ! r.supportsOperandSizeSpec )
                {
                    fail("Register "+r+" does not support size specification");
                }
                dot = lexer.next();
                if ( ! lexer.peek().is(TokenType.TEXT ) ) {
                    fail("Expected either .w or .l",dot.getRegion());
                }
                sizeSpec = lexer.next();
                switch( sizeSpec.value.toLowerCase() ) {
                    case "w":
                        operandSize = OperandSize.WORD;
                        break;
                    case "l":
                        operandSize = OperandSize.LONG;
                        break;
                    default:
                        fail("Expected either .w or .l",sizeSpec.getRegion());
                }
            }
            return new RegisterNode(r,operandSize, Token.getMergedRegion(prefix,registerNumber,dot,sizeSpec ) );
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