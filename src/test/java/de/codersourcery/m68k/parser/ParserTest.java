package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.assembler.arch.InstructionType;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.parser.ast.AST;
import de.codersourcery.m68k.parser.ast.CommentNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.LabelNode;
import de.codersourcery.m68k.parser.ast.OperandNode;
import de.codersourcery.m68k.parser.ast.RegisterNode;
import de.codersourcery.m68k.parser.ast.StatementNode;
import junit.framework.TestCase;

public class ParserTest extends TestCase
{
    private AST ast;

    public void testParseEmpty()
    {
        ast = parse("");
        assertNotNull(ast);
        assertTrue(ast.hasNoChildren());
        assertTrue(ast.hasNoParent());
    }

    public void testParseBlankLine()
    {
        ast = parse("         ");
        assertNotNull(ast);
        assertTrue(ast.hasNoChildren());
        assertTrue(ast.hasNoParent());
    }

    public void testParseLabel()
    {
        ast = parse("label:");
        assertNotNull(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(1,stmt.childCount());
        final LabelNode label = stmt.child(0).asLabel();

        assertEquals( new Label(new Identifier("label") ) , label.getValue() );
        assertEquals( new TextRegion(0,5), label.getMergedRegion() );
        assertEquals( new TextRegion(0,5), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,5), ast.getMergedRegion() );
    }

    public void testParseLabelWithInstruction()
    {
        ast = parse("label: move d0,d1");
        assertNotNull(ast);
        System.out.println(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(2,stmt.childCount());
        final LabelNode label = stmt.child(0).asLabel();
        assertEquals( new Label(new Identifier("label") ) , label.getValue() );

        final InstructionNode insn = stmt.child(1).asInstruction();
        assertEquals(2,insn.childCount());
        assertEquals(InstructionType.MOVE, insn.getInstructionType() );

        final RegisterNode regA = insn.source().getValue().asRegister();
        assertEquals(Register.D0, regA.register );

        final RegisterNode regB = insn.destination().getValue().asRegister();
        assertEquals(Register.D1, regB.register );
    }

    public void testParseLabelWithInstructionAndComment()
    {
        ast = parse("label: move d0,d1 ; some comment");
        assertNotNull(ast);
        System.out.println(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(3,stmt.childCount());
        final LabelNode label = stmt.child(0).asLabel();
        assertEquals( new Label(new Identifier("label") ) , label.getValue() );

        final InstructionNode insn = stmt.child(1).asInstruction();
        assertEquals(2,insn.childCount());
        assertEquals(InstructionType.MOVE, insn.getInstructionType() );

        final RegisterNode regA = insn.source().getValue().asRegister();
        assertEquals(Register.D0, regA.register );

        final RegisterNode regB = insn.destination().getValue().asRegister();
        assertEquals(Register.D1, regB.register );

        final CommentNode comment = stmt.child(2).asComment();
        assertEquals(" some comment", comment.getValue() );
    }

    public void testComment()
    {
        ast = parse("; this is a comment");
        assertNotNull(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(1,stmt.childCount());
        final CommentNode comment = stmt.child(0).asComment();
        assertEquals( " this is a comment", comment.getValue() );
        assertEquals( new TextRegion(0,19), comment.getMergedRegion() );
        assertEquals( new TextRegion(0,19), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,19), ast.getMergedRegion() );
    }

    public void testParseInstruction()
    {
        ast = parse("move d0,d1");
        assertNotNull(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(1,stmt.childCount());

        final InstructionNode insn = stmt.child(0).asInstruction();
        assertEquals(InstructionType.MOVE, insn.getInstructionType() );

        final OperandNode source = insn.source();
        assertEquals(AddressingMode.DATA_REGISTER_DIRECT,source.addressingMode);
        assertEquals(Register.D0,source.child(0).asRegister().register);

        final OperandNode dest = insn.destination();
        assertEquals(AddressingMode.DATA_REGISTER_DIRECT,dest.addressingMode);
        assertEquals(Register.D1,dest.child(0).asRegister().register);

        assertEquals( new TextRegion(0,4), insn.getRegion() );
        assertEquals( new TextRegion(0,10), insn.getMergedRegion() );
        assertEquals( new TextRegion(0,10), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,10), ast.getMergedRegion() );
    }

    public void testParseAddressImmediate()
    {
        ast = parse("LEA $1234,A0");
        assertNotNull(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(1,stmt.childCount());

        final InstructionNode insn = stmt.child(0).asInstruction();
        assertEquals(InstructionType.LEA, insn.getInstructionType() );

        final OperandNode source = insn.source();
        assertEquals(AddressingMode.IMMEDIATE_ADDRESS,source.addressingMode);
        assertEquals(0x1234,source.getValue().asNumber().getValue());

        final OperandNode dest = insn.destination();
        assertEquals(AddressingMode.ADDRESS_REGISTER_DIRECT,dest.addressingMode);
        assertEquals(Register.A0,dest.getValue().asRegister().register);

        assertEquals( new TextRegion(0,3), insn.getRegion() );
        assertEquals( new TextRegion(0,12), insn.getMergedRegion() );
        assertEquals( new TextRegion(0,12), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,12), ast.getMergedRegion() );
    }

    public void testParseInstructionRegisterIndirect()
    {
        ast = parse("move (a0),d1");
        assertNotNull(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(1,stmt.childCount());

        final InstructionNode insn = stmt.child(0).asInstruction();
        assertEquals(InstructionType.MOVE, insn.getInstructionType() );

        final OperandNode source = insn.source();
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT,source.addressingMode);
        assertEquals(Register.A0,source.child(0).asRegister().register);

        final OperandNode dest = insn.destination();
        assertEquals(AddressingMode.DATA_REGISTER_DIRECT,dest.addressingMode);
        assertEquals(Register.D1,dest.child(0).asRegister().register);

        assertEquals( new TextRegion(0,4), insn.getRegion() );
        assertEquals( new TextRegion(0,12), insn.getMergedRegion() );
        assertEquals( new TextRegion(0,12), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,12), ast.getMergedRegion() );
    }

    public void testParseInstructionRegisterIndirectPostincrement()
    {
        ast = parse("move (a0)+,d1");
        assertNotNull(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(1,stmt.childCount());

        final InstructionNode insn = stmt.child(0).asInstruction();
        assertEquals(InstructionType.MOVE, insn.getInstructionType() );

        final OperandNode source = insn.source();
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT,source.addressingMode);
        assertEquals(Register.A0,source.getValue().asRegister().register);

        final OperandNode dest = insn.destination();
        assertEquals(AddressingMode.DATA_REGISTER_DIRECT,dest.addressingMode);
        assertEquals(Register.D1,dest.child(0).asRegister().register);

        assertEquals( new TextRegion(0,4), insn.getRegion() );
        assertEquals( new TextRegion(0,13), insn.getMergedRegion() );
        assertEquals( new TextRegion(0,13), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,13), ast.getMergedRegion() );
    }

    public void testParseInstructionRegisterIndirectPredecrement()
    {
        ast = parse("move -(a0),d1");
        assertNotNull(ast);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        assertEquals(1,stmt.childCount());

        final InstructionNode insn = stmt.child(0).asInstruction();
        assertEquals(InstructionType.MOVE, insn.getInstructionType() );

        final OperandNode source = insn.source();
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT,source.addressingMode);
        assertEquals(Register.A0,source.getValue().asRegister().register);

        final OperandNode dest = insn.destination();
        assertEquals(AddressingMode.DATA_REGISTER_DIRECT,dest.addressingMode);
        assertEquals(Register.D1,dest.child(0).asRegister().register);

        assertEquals( new TextRegion(0,4), insn.getRegion() );
        assertEquals( new TextRegion(0,13), insn.getMergedRegion() );
        assertEquals( new TextRegion(0,13), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,13), ast.getMergedRegion() );
    }

    private AST parse(String source) {

        final ILexer lexer = new Lexer(new StringScanner(source ) );
        return new Parser().parse(lexer);
    }
}
