package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.parser.ast.AST;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.CommentNode;
import de.codersourcery.m68k.parser.ast.InstructionNode;
import de.codersourcery.m68k.parser.ast.LabelNode;
import de.codersourcery.m68k.parser.ast.NodeType;
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

        final RegisterNode regA = insn.child(0).asRegister();
        assertEquals(Register.D0, regA.register );

        final RegisterNode regB = insn.child(1).asRegister();
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

        final RegisterNode regA = insn.child(0).asRegister();
        assertEquals(Register.D0, regA.register );

        final RegisterNode regB = insn.child(1).asRegister();
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

        assertEquals( new TextRegion(0,4), insn.getRegion() );
        assertEquals( new TextRegion(0,10), insn.getMergedRegion() );
        assertEquals( new TextRegion(0,10), stmt.getMergedRegion() );
        assertEquals( new TextRegion(0,10), ast.getMergedRegion() );
    }

    private AST parse(String source) {

        final ILexer lexer = new Lexer(new StringScanner(source ) );
        return new Parser().parse(lexer);
    }
}
