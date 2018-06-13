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

    private boolean fitsIn16Bits(ASTNode node) {
        int bits = ((IValueNode) node).getBits();
        return ( ( bits & ~0b111_1111_1111_1111) == 0 );
    }

    private OperandNode parseOperand()
    {
        /*
         * #$1234                        => IMMEDIATE_VALUE (ok)
         *  Dn                           => DATA_REGISTER_DIRECT (ok)
         *  An                           => ADDRESS_REGISTER_DIRECT (ok)
         *  (An)                         => ADDRESS_REGISTER_INDIRECT (ok)
         *  (An)+                        => ADDRESS_REGISTER_INDIRECT_POST_INCREMENT (ok)
         *  -(An)                        => ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT (ok)
         *
         *  d16(An)                      => ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT (ok)
         * (d16 ,PC)                     => PC_INDIRECT_WITH_DISPLACEMENT (ok)
         *
         * (d8,An, Xn.SIZE*SCALE)        => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
         * (bd,An,Xn.SIZE*SCALE)         => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
         *
         * ([bd,An],Xn.SIZE*SCALE,od)    => MEMORY_INDIRECT_POSTINDEXED
         * ([bd,PC],Xn.SIZE*SCALE,od)    => PC_MEMORY_INDIRECT_POSTINDEXED
         *
         * ([bd,An,Xn.SIZE*SCALE],od)    => MEMORY_INDIRECT_PREINDEXED
         * ([bd,PC,Xn.SIZE*SCALE],od)    => PC_MEMORY_INDIRECT_PREINDEXED
         *
         * (d8,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
         * (bd,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_DISPLACEMENT
         *
         * ($1234).w                     => ABSOLUTE_SHORT_ADDRESSING (ok)
         * ($1234).L                     => ABSOLUTE_LONG_ADDRESSING (ok)
         *
         * LEA $1234,A0                  => ABSOLUTE_SHORT_ADDRESSING (ok)
         * LEA $12345678,A0              => ABSOLUTE_LONG_ADDRESSING (ok)
         *
         */
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

        final List<Token> tokens = new ArrayList<>();
        Token indirectAddressing = null;
        Token preDecrement = null;
        ASTNode baseDisplacement = null;
        RegisterNode baseRegister = null;
        if ( lexer.peek(TokenType.MINUS ) )
        {
            preDecrement = lexer.next();
            if (lexer.peek(TokenType.PARENS_OPEN)) // MOVE -(a0),...
            {
                tokens.add( preDecrement );
                indirectAddressing = lexer.next();
                tokens.add(indirectAddressing);

                baseRegister = parseRegister(false);
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
            baseDisplacement = parseExpression();
            if ( baseDisplacement.isRegister() ) // MOVE D3,D4
            {
                final Register r = baseDisplacement.asRegister().register;
                AddressingMode mode = null;
                if ( r.isAddress() ) {
                    mode = AddressingMode.ADDRESS_REGISTER_DIRECT;
                } else if ( r.isData() ) {
                    mode = AddressingMode.DATA_REGISTER_DIRECT;
                } else {
                    fail("Expected a data or address register",baseDisplacement.getRegion());
                }
                final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                op.setValue(baseDisplacement);
                return op;
            }
            // MOVE $1234
            if ( ! evaluatesToNumber(baseDisplacement ) ) {
                fail("Expected an address value",baseDisplacement.getRegion());
            }
        }

        if ( ! lexer.peek(TokenType.PARENS_OPEN ) )
        {
            // LEA $1234,...
            final int bits = ((IValueNode) baseDisplacement).getBits();
            final AddressingMode mode = ( ( bits & ~0b111_1111_1111_1111) == 0 ) ?
                    AddressingMode.ABSOLUTE_SHORT_ADDRESSING : AddressingMode.ABSOLUTE_LONG_ADDRESSING;
            final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
            op.setValue(baseDisplacement);
            return op;
        }
        tokens.add(lexer.next()); // consume opening parens

        if ( baseDisplacement != null ) {
            // MOVE $10(
            baseRegister = parseRegister(false );
            if ( baseRegister == null ) {
                fail("Expected a register");
            }
            if ( ! ( baseRegister.isAddressRegister() || baseRegister.isPC() ) ) {
                fail("Expected PC or an address register");
            }
            if ( lexer.peek(TokenType.PARENS_CLOSE) )
            {
                // MOVE $10(A0)
                tokens.add(lexer.next());
                if ( ! fitsIn16Bits(baseDisplacement ) ) {
                    fail("Displacement out-of-range, must fit in 16 bits");
                }
                final AddressingMode mode;
                if ( baseRegister.isPC() ) {
                    mode = AddressingMode.PC_INDIRECT_WITH_DISPLACEMENT;
                } else {
                    mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
                }
                final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                op.setBaseDisplacement(baseDisplacement);
                op.setValue(baseRegister);
                return op;
            }
            if ( ! lexer.peek(TokenType.COMMA ) ) {
                fail("Expected a comma");
            }
            tokens.add(lexer.next());
            // MOVE (A0,
        }
        else
        {
            // baseDisplacement == NULL   here

            // check for inner displacement (bd,...) syntax
            ASTNode eaOrRegister = parseExpression();
            if ( eaOrRegister == null ) {
                fail("Expected an address register or displacement value");
            }
            if ( evaluatesToNumber(eaOrRegister) )
            {
                // MOVE ($0a
                baseDisplacement = eaOrRegister;
                if ( lexer.peek(TokenType.PARENS_CLOSE) )
                {
                    tokens.add(lexer.next());
                    final AddressingMode mode = fitsIn16Bits(baseDisplacement) ?
                        AddressingMode.ABSOLUTE_SHORT_ADDRESSING : AddressingMode.ABSOLUTE_LONG_ADDRESSING;
                    final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                    op.setBaseDisplacement(baseDisplacement);
                    return op;
                }
                if ( ! lexer.peek(TokenType.COMMA ) ) {
                    fail("Expected a comma");
                }
                tokens.add(lexer.next());

                // MOVE ($0a,
                eaOrRegister = parseExpression();
                if ( eaOrRegister == null ) {
                    fail("Expected an address register or PC");
                }
                if ( ! ( eaOrRegister.isAddressRegister() || eaOrRegister.isPCRegister() ) ) {
                    fail("Expected an address register or PC");
                }
                baseRegister = eaOrRegister.asRegister();
                if ( lexer.peek(TokenType.PARENS_CLOSE) ) // TODO: Duplicate code (1)
                {
                    tokens.add(lexer.next());
                    final AddressingMode mode;
                    if ( baseRegister.isPC() ) {
                        mode = AddressingMode.PC_INDIRECT_WITH_DISPLACEMENT;
                    } else {
                        mode = AddressingMode.ADDRESS_REGISTER_INDIRECT;
                    }
                    final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                    op.setValue(baseRegister);
                    // fake zero displacement value
                    op.setBaseDisplacement(new NumberNode(0,NumberNode.NumberType.DECIMAL,new TextRegion(0,0) ) );
                    return op;
                }
                if ( ! lexer.peek(TokenType.COMMA ) ) {
                    fail("Expected a comma");
                }
                tokens.add(lexer.next());
                // MOVE ($0a,Ax|PC,
            }
            else if ( eaOrRegister.isRegister() )
            {
                // MOVE (A0
                if ( ! (eaOrRegister.isAddressRegister() || eaOrRegister.isPCRegister() ) )
                {
                    fail("Expected PC or an address register");
                }
                baseRegister = eaOrRegister.asRegister();

                if ( lexer.peek(TokenType.PARENS_CLOSE) ) // TODO: Duplicate code (1)
                {
                    tokens.add(lexer.next());
                    final AddressingMode mode;
                    if ( baseRegister.isPC() ) {
                        mode = AddressingMode.PC_INDIRECT_WITH_DISPLACEMENT;
                    } else {
                        mode = AddressingMode.ADDRESS_REGISTER_INDIRECT;
                    }
                    final OperandNode op = new OperandNode(mode,Scaling.IDENTITY, Token.getMergedRegion(tokens));
                    op.setValue(baseRegister);
                    // fake zero displacement value
                    op.setBaseDisplacement(new NumberNode(0,NumberNode.NumberType.DECIMAL,new TextRegion(0,0) ) );
                    return op;
                }
                if ( ! lexer.peek(TokenType.COMMA ) ) {
                    fail("Expected a comma");
                }
                tokens.add(lexer.next());
                // MOVE (A0,
            } else {
                fail("Syntax error");
            }
        }
        // state here:
        // MOVE ($0a,Ax|PC,
        // MOVE (Ax|PC,


        // ======================== TODO: Parse missing address modes
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

            result = parseRegister(false);
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

    private RegisterNode parseRegister(boolean operandSizeSuffixSupported)
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
            final OperandSize operandSize = parseOperandSize();
            if ( operandSize != null )
            {
                if ( ! operandSizeSuffixSupported ) {
                    fail("Size specification not supported here");
                }
                if ( ! r.supportsOperandSizeSpec ) {
                    fail("Register "+r+" does not support a size specification (.w/.l)");
                }
            }
            final TextRegion region = Token.getMergedRegion(prefix,registerNumber);
            if ( operandSize != null ) {
                region.incLength(2); // +2 characters ('.w' or '.l')
            }
            return new RegisterNode(r,operandSize, region);
        }
        return null;
    }

    private OperandSize parseOperandSize()
    {
        if( lexer.peek(TokenType.DOT) )
        {
            final Token dot = lexer.next();
            if ( ! lexer.peek().is(TokenType.TEXT ) )
            {
                fail("Expected size specifier (.w or .l)");
                return null; // never reached
            }
            final Token sizeSpec = lexer.next();
            switch( sizeSpec.value.toLowerCase() )
            {
                case "w": return OperandSize.WORD;
                case "l": return OperandSize.LONG;
                default:
                    fail("Invalid size specifier , only .w or .l are supported");
            }
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