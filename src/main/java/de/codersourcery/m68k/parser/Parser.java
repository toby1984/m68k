package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.assembler.ICompilationContext;
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
                    if ( ! t.supportsExplicitOperandSize() ) {
                        fail("Instruction does not support explicit operand sizes",tok);
                    }

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

    private static boolean evaluatesToNumber(IValueNode node)
    {
        // Method MUST be NULL safe
        // FIXME: Does not recognize expressions yet...
        return node != null && node.is(NodeType.NUMBER);
    }

    private IValueNode parseExpression(boolean operandSizeSupported,boolean registerScalingSupported,boolean registerSupported)
    {
        // FIXME: Implement parsing expressions here...
        return parseAtom(operandSizeSupported,registerScalingSupported,registerSupported);
    }

    private boolean fitsIn16Bits(IValueNode node)
    {
        if ( node == null ) {
            return true;
        }
        if ( !  evaluatesToNumber(node) ) {
            fail("Expected a 16-bit number");
        }
        int bits = ((IValueNode) node).getBits();

        return (bits & 0xffff0000) == 0 || (bits & 0xffffff00) == 0xffff0000;
    }

    private boolean fitsIn8Bits(IValueNode node)
    {
        if ( node == null ) {
            return true;
        }
        int bits = node.getBits();
        return (bits & 0xffffff00) == 0 || (bits & 0xffffff00) == 0xffffff00;
    }

    private Token consumeComma() {
        if ( ! lexer.peek(TokenType.COMMA ) ) {
            fail("Expected a comma");
        }
        return lexer.next();
    }

    private Token consumeClosingParens() {
        if ( ! lexer.peek(TokenType.PARENS_CLOSE) ) {
            fail("Expected closing parens");
        }
        return lexer.next();
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
         * ([bd,An],Xn.SIZE*SCALE,od)    => MEMORY_INDIRECT_POSTINDEXED (ok)
         * ([bd,PC],Xn.SIZE*SCALE,od)    => PC_MEMORY_INDIRECT_POSTINDEXED (ok)
         *
         * ([bd,An,Xn.SIZE*SCALE],od)    => MEMORY_INDIRECT_PREINDEXED
         * ([bd,PC,Xn.SIZE*SCALE],od)    => PC_MEMORY_INDIRECT_PREINDEXED
         *
         * (d8,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT (ok)
         * (bd,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_DISPLACEMENT (ok)
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
            IValueNode value = parseExpression(false,false,false);
            if ( value == null ) {
                fail("Failed to parse immediate mode operand");
            }
            final OperandNode op = new OperandNode(AddressingMode.IMMEDIATE_VALUE,hash.getRegion() );
            op.setValue(value);
            return op;
        }

        final List<Token> tokens = new ArrayList<>();

        IValueNode baseDisplacement = null;
        IValueNode outerDisplacement = null;
        RegisterNode baseRegister = null;
        RegisterNode indexRegister = null;

        if ( lexer.peek(TokenType.MINUS ) )
        {
            tokens.add( lexer.next() );
            if (lexer.peek(TokenType.PARENS_OPEN)) // MOVE -(a0),...
            {
                tokens.add( lexer.next() );

                baseRegister = parseRegister(false,false);
                if ( baseRegister == null ) {
                    fail("Expected an address register");
                }
                if ( ! baseRegister.isAddressRegister() ) {
                    fail("Expected an address register",baseRegister);
                }
                tokens.add( consumeClosingParens() );
                final OperandNode op = new OperandNode(AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT,Token.getMergedRegion(tokens));
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
            baseDisplacement = parseExpression(false,false,true);
            if ( baseDisplacement.isRegister() ) // MOVE D3,D4
            {
                final Register r = baseDisplacement.asRegister().register;
                AddressingMode mode = null;
                if ( r.isAddress() ) {
                    mode = AddressingMode.ADDRESS_REGISTER_DIRECT;
                } else if ( r.isData() ) {
                    mode = AddressingMode.DATA_REGISTER_DIRECT;
                } else {
                    fail("Expected a data or address register",baseDisplacement);
                }
                final OperandNode op = new OperandNode(mode,Token.getMergedRegion(tokens));
                op.setValue(baseDisplacement);
                return op;
            }
            // MOVE $1234
            if ( ! evaluatesToNumber(baseDisplacement ) ) {
                fail("Expected an address value",baseDisplacement);
            }
        }

        // // MOVE $1234
        if ( ! lexer.peek(TokenType.PARENS_OPEN ) )
        {
            final int bits = ((IValueNode) baseDisplacement).getBits();
            final AddressingMode mode = ( ( bits & ~0b111_1111_1111_1111) == 0 ) ?
                    AddressingMode.ABSOLUTE_SHORT_ADDRESSING : AddressingMode.ABSOLUTE_LONG_ADDRESSING;
            final OperandNode op = new OperandNode(mode,Token.getMergedRegion(tokens));
            op.setValue(baseDisplacement);
            return op;
        }

        tokens.add( lexer.next() ); // consume opening parens

        final List<IValueNode> arguments;
        if ( baseDisplacement == null ) {
            // MOVE
            arguments = parseCommaSeparatedList(4,true);
            if ( evaluatesToNumber((IValueNode) arguments.get(0) ) )
            {
                baseRegister = null;
                baseDisplacement = (IValueNode) arguments.get(0);
                arguments.remove(0);

                if ( arguments.isEmpty() )
                {
                    tokens.add( consumeClosingParens() );
                    final AddressingMode mode = fitsIn16Bits(baseDisplacement ) ?
                            AddressingMode.ABSOLUTE_SHORT_ADDRESSING : AddressingMode.ABSOLUTE_LONG_ADDRESSING;
                    final OperandNode op = new OperandNode(mode,Token.getMergedRegion(tokens));
                    op.setValue(baseDisplacement);
                    return op;
                }
            }
        } else {
            arguments = parseCommaSeparatedList(3,true);
        }
        tokens.add( consumeClosingParens() );

        if ( lexer.peek(TokenType.PLUS ) )
        {
            tokens.add(lexer.next());

            if ( arguments.size() != 1 ) {
                fail("Expected one address register for post-increment");
            }
            baseRegister = arguments.get(0).asRegister();
            if ( ! baseRegister.isAddressRegister() ) {
                fail("Expected one address register for post-increment");
            }
            if ( baseRegister.hasScaling() || baseRegister.hasOperandSize() ) {
                fail("Cannot use scaling and/or operand size spec with post increment");
            }
            if ( baseDisplacement != null ) {
                fail("Cannot use post-increment with displacement");
            }

            final OperandNode op = new OperandNode(AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT,Token.getMergedRegion(tokens));
            op.setValue(baseRegister);
            return op;
        }

        if ( ! fitsIn16Bits(baseDisplacement)) {
            fail("Displacement does not fit in 16 bits",baseDisplacement);
        }

        // base register displacement parsed,
        // we now have at most three more arguments

        /*
         * (d8,An, Xn.SIZE*SCALE)        => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
         * (bd,An,Xn.SIZE*SCALE)         => ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
         *
         * ([bd,An],Xn.SIZE*SCALE,od)    => MEMORY_INDIRECT_POSTINDEXED (ok)
         * ([bd,PC],Xn.SIZE*SCALE,od)    => PC_MEMORY_INDIRECT_POSTINDEXED (ok)
         *
         * ([bd,An,Xn.SIZE*SCALE],od)    => MEMORY_INDIRECT_PREINDEXED
         * ([bd,PC,Xn.SIZE*SCALE],od)    => PC_MEMORY_INDIRECT_PREINDEXED
         *
         * (d8,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
         * (bd,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_DISPLACEMENT
         */

        // (baseRegister,indexRegister.SIZE*SCALE,outerDisplacement)
        if ( ! arguments.get(0).isRegister() ) {
            fail("Expected a register");
        }
        baseRegister = arguments.get(0).asRegister();
        if ( baseRegister.isPC() )
        {
            // MOVE $0d(PC
            // MOVE (PC
            // MOVE ($0d,PC
            if ( arguments.size() == 1 )
            {
                final OperandNode op = new OperandNode(AddressingMode.PC_INDIRECT_WITH_DISPLACEMENT,Token.getMergedRegion(tokens));
                op.setValue(baseRegister);
                op.setBaseDisplacement(baseDisplacement);
                return op;
            }

            if ( ! arguments.get(1).isAddressRegister() ) {
                fail("Expected an address register",arguments.get(1));
            }
            indexRegister = arguments.get(1).asRegister();

            if ( arguments.size() == 2 )
            {
                /*
                 * (d8,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
                 * (bd,PC,Xn.SIZE*SCALE)         => PC_INDIRECT_WITH_INDEX_DISPLACEMENT
                 */
                AddressingMode mode = AddressingMode.PC_INDIRECT_WITH_INDEX_DISPLACEMENT;
                if ( fitsIn8Bits( baseDisplacement ) ) {
                    mode = AddressingMode.PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
                }
                final OperandNode op = new OperandNode(mode,Token.getMergedRegion(tokens));
                op.setValue(baseRegister);
                op.setBaseDisplacement(baseDisplacement);
                op.setIndexRegister(indexRegister);
                return op;
            }
            outerDisplacement = (IValueNode) arguments.get(2);
            if ( ! fitsIn16Bits(outerDisplacement ) ) {
                fail("Outer displacement out of 16-bit range",outerDisplacement);
            }
            // * ([bd,PC],Xn.SIZE*SCALE,od)    => PC_MEMORY_INDIRECT_POSTINDEXED (ok)
            final OperandNode op = new OperandNode(AddressingMode.PC_MEMORY_INDIRECT_POSTINDEXED,Token.getMergedRegion(tokens));
            op.setValue(baseRegister);
            op.setBaseDisplacement(baseDisplacement);
            op.setIndexRegister(indexRegister);
            op.setOuterDisplacement(outerDisplacement);
            return op;
        }
        if ( ! baseRegister.isAddressRegister() ) {
            fail("Unsupported register: "+baseRegister);
        }

        baseRegister = arguments.get(0).asRegister();
        if ( baseRegister.hasOperandSize() || baseRegister.hasScaling() ) {
            fail("No scaling or operand size allowed on base register",baseRegister);
        }

        if ( arguments.size() == 1 )
        {
            AddressingMode mode;
            if ( baseDisplacement == null ) {
                mode = AddressingMode.ADDRESS_REGISTER_INDIRECT;
            }
            else
            {
                mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT;
            }
            final OperandNode op = new OperandNode(mode,Token.getMergedRegion(tokens));
            op.setValue(baseRegister);
            op.setBaseDisplacement(baseDisplacement);
            op.setIndexRegister(null);
            op.setOuterDisplacement(null);
            return op;
        }

        indexRegister = arguments.get(1).asRegister();

        if ( arguments.size() == 2 )
        {
            AddressingMode mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT;
            if ( ! fitsIn8Bits(baseDisplacement ) ) {
                mode = AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT;
            }
            final OperandNode op = new OperandNode(mode,Token.getMergedRegion(tokens));
            op.setValue(baseRegister);
            op.setBaseDisplacement(baseDisplacement);
            op.setIndexRegister(indexRegister);
            op.setOuterDisplacement(outerDisplacement);
            return op;
        }

        outerDisplacement = (IValueNode) arguments.get(2);
        if ( ! fitsIn16Bits(outerDisplacement ) ) {
            fail("Outer displacement out of 16-bit range",outerDisplacement);
        }

        final OperandNode op = new OperandNode(AddressingMode.MEMORY_INDIRECT_POSTINDEXED,Token.getMergedRegion(tokens));
        op.setValue(baseRegister);
        op.setBaseDisplacement(baseDisplacement);
        op.setIndexRegister(indexRegister);
        op.setOuterDisplacement(outerDisplacement);
        return op;
    }

    private List<IValueNode> parseCommaSeparatedList(int maxElementCount,boolean operandSizeSupported)
    {
        final var result = new ArrayList<IValueNode>(maxElementCount);

        boolean valueExpected = false;
        do  {
            final IValueNode node = parseExpression(operandSizeSupported,true,true);
            if ( node == null )
            {
                if ( valueExpected ) {
                    fail("Value expected after comma");
                }
                break;
            }
            result.add( node );
            if ( lexer.peek(TokenType.COMMA ) )
            {
                if ( result.size()+1 > maxElementCount ) {
                    fail("Too many arguments, expected at most "+maxElementCount);
                }
                lexer.next();
                valueExpected = true;
            } else {
                valueExpected = false;
            }
        }
        while ( result.size() < maxElementCount );

        if ( valueExpected ) {
            fail("Value expected after comma");
        }
        if ( result.isEmpty() ) {
            fail("Expected at least one argument after opening parens");
        }
        return result;
    }

    private Scaling parseScaling(List<Token> tokens)
    {
        Scaling scaling = null;
        if ( lexer.peek(TokenType.TIMES ) )
        {
            tokens.add(lexer.next()); // consume '*'
            if (! lexer.peek(TokenType.DIGITS))
            {
                fail("Expected scaling factor for 1,2,4 or 8");
            }
            final Token numberToken = lexer.next();
            tokens.add(numberToken);
            try
            {
                switch (Integer.parseInt(numberToken.value))
                {
                    case 1: return Scaling.IDENTITY;
                    case 2: return Scaling.TWO;
                    case 4: return Scaling.FOUR;
                    case 8: return Scaling.EIGHT;
                }
                fail("Expected scaling factor for 1,2,4 or 8");
            }
            catch (Exception e)
            {
            }
            fail("Not a valid number", numberToken.getRegion());
        }
        return scaling;
    }

    private IValueNode parseAtom(boolean operandSizeSupported,boolean registerScalingSupported,boolean registerNameSupported)
    {
        Token token = lexer.peek();
        IValueNode result = null;
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

            if ( registerNameSupported )
            {
                result = parseRegister(operandSizeSupported, registerScalingSupported);
                if (result != null)
                {
                    return result;
                }
            }

            if ( Identifier.isValid( token.value ) )
            {
                lexer.next();
                return new IdentifierNode(new Identifier(token.value),token.getRegion());
            }
        }
        return parseString();
    }

    private RegisterNode parseRegister(boolean operandSizeSuffixSupported,boolean scalingSupported)
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
                if ( ! r.isAddress() ) {
                    fail("Operand size can only be specified on address registers",prefix);
                }
            }
            final TextRegion region = Token.getMergedRegion(prefix,registerNumber);
            if ( operandSize != null ) {
                region.incLength(2); // +2 characters ('.w' or '.l')
            }
            final List<Token> tokens = new ArrayList<>();
            Scaling scaling = null;
            if ( scalingSupported )
            {
                scaling = parseScaling(tokens);
                if (scaling != null)
                {
                    if (!r.isAddress())
                    {
                        fail("Scaling non-address registers is not possible", prefix);
                    }
                    region.merge(Token.getMergedRegion(tokens));
                }
            }
            return new RegisterNode(r,operandSize, scaling, region);
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

    private void fail(String message,IASTNode node)
    {
        TextRegion r;
        if ( node == null ) {
            r = lexer.peek().getRegion();
        }
        else
        {
            r = node.getRegion();
            if ( r == null ) {
                r = node.getMergedRegion();
                if (r == null ) {
                    r = lexer.peek().getRegion();
                }
            }
        }
        fail(message,r.getStartingOffset());
    }

    private void fail(String message,TextRegion region) {
        fail(message,region.getStartingOffset());
    }

    private void fail(String message,int offset) {
        throw new ParseException(message, offset );
    }
}