package de.codersourcery.m68k.parser;

import de.codersourcery.m68k.assembler.arch.AddressingMode;
import de.codersourcery.m68k.assembler.arch.InstructionType;
import de.codersourcery.m68k.assembler.arch.OperandSize;
import de.codersourcery.m68k.assembler.arch.Register;
import de.codersourcery.m68k.assembler.arch.Scaling;
import de.codersourcery.m68k.parser.ast.*;
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

    public void testMoveQOperandSizesFail() {
        assertFails( () -> parse("moveq.b #$70,d0") );
        assertFails( () -> parse("moveq.w #$70,d0") );
        assertFails( () -> parse("moveq.l #$70,d0") );
    }

    // IMMEDIATE_VALUE
    public void testImmediateMode()
    {
        final OperandNode src = parseSourceOperand("MOVE #$1234,D0");
        assertEquals(AddressingMode.IMMEDIATE_VALUE,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.NUMBER) );
        assertEquals( 0x1234 , src.getValue().asNumber().getValue() );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // DATA_REGISTER_DIRECT
    public void testDataRegisterDirect()
    {
        final OperandNode src = parseSourceOperand("MOVE D0,D1");
        assertEquals(AddressingMode.DATA_REGISTER_DIRECT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        final RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.D0, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // ADDRESS_REGISTER_DIRECT
    public void testAdddressRegisterDirect()
    {
        final OperandNode src = parseSourceOperand("MOVE A0,D1");
        assertEquals(AddressingMode.ADDRESS_REGISTER_DIRECT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        final RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A0, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // ADDRESS_REGISTER_INDIRECT
    public void testAdddressRegisterIndirect()
    {
        final OperandNode src = parseSourceOperand("MOVE (A0),D1");
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A0, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // ADDRESS_REGISTER_INDIRECT_POST_INCREMENT
    public void testAdddressRegisterIndirectPostIncrement()
    {
        final OperandNode src = parseSourceOperand("MOVE (A0)+,D1");
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        final RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A0, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT
    public void testAddressRegisterIndirectPreDecrement() {
        // ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT
        final OperandNode src = parseSourceOperand("MOVE -(A0),D1");
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        final RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A0, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT
    public void testAddressRegisterIndirectWithDisplacement() {

        OperandNode src = parseSourceOperand("MOVE $1234(A0),D1");
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A0, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );
        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x1234,baseDisplacement);
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // PC_INDIRECT_WITH_DISPLACEMENT
    public void testPCRegisterIndirectWithDisplacement() {

        OperandNode src = parseSourceOperand("MOVE $1234(PC),D1");
        assertEquals(AddressingMode.PC_INDIRECT_WITH_DISPLACEMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.PC, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );
        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x1234,baseDisplacement);
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    public void testTooLargeDisplacement() {
        try
        {
            parseSourceOperand("MOVE $123467(PC),D1");
            fail("Should've failed to parse");
        } catch(Exception e) {
            // ok
        }
    }

    // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
    public void testAddressRegisterIndirectWithIndex8BitDisplacement() {

        OperandNode src = parseSourceOperand("MOVE $12(A1,A2.l*2),D1");
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A1, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getIndexRegister() );
        RegisterNode indexRegister = src.getIndexRegister();
        assertEquals( Register.A2, indexRegister.register );
        assertEquals(OperandSize.LONG, indexRegister.operandSize );
        assertEquals(Scaling.TWO, indexRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );
        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x12,baseDisplacement);
        assertNull( src.getOuterDisplacement() );
    }

        // ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT
    public void testAddressRegisterIndirectWihIndex16BitDisplacement() {

        OperandNode src = parseSourceOperand("MOVE $1234(A1,A2.l*2),D1");
        assertEquals(AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A1, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getIndexRegister() );
        RegisterNode indexRegister = src.getIndexRegister();
        assertEquals( Register.A2, indexRegister.register );
        assertEquals(OperandSize.LONG, indexRegister.operandSize );
        assertEquals(Scaling.TWO, indexRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );
        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x1234,baseDisplacement);
        assertNull( src.getOuterDisplacement() );
    }

    // MEMORY_INDIRECT_POSTINDEXED
    public void testMemoryIndirectPostIndexed() {

        OperandNode src = parseSourceOperand("MOVE $1234(A1,A2.l*2,$ab),D1");
        assertEquals(AddressingMode.MEMORY_INDIRECT_POSTINDEXED,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.A1, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getIndexRegister() );
        RegisterNode indexRegister = src.getIndexRegister();
        assertEquals( Register.A2, indexRegister.register );
        assertEquals(OperandSize.LONG, indexRegister.operandSize );
        assertEquals(Scaling.TWO, indexRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );

        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x1234,baseDisplacement);

        assertTrue( src.getOuterDisplacement().is(NodeType.NUMBER));
        long outerDisplacement = src.getOuterDisplacement().asNumber().getValue();
        assertEquals(0xab,outerDisplacement);
    }

    // PC_MEMORY_INDIRECT_POSTINDEXED
    public void testPCMemoryIndirectPostIndexed() {

        OperandNode src = parseSourceOperand("MOVE $1234(PC,A2.l*2,$ab),D1");
        assertEquals(AddressingMode.PC_MEMORY_INDIRECT_POSTINDEXED,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.PC, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getIndexRegister() );
        RegisterNode indexRegister = src.getIndexRegister();
        assertEquals( Register.A2, indexRegister.register );
        assertEquals(OperandSize.LONG, indexRegister.operandSize );
        assertEquals(Scaling.TWO, indexRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );

        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x1234,baseDisplacement);

        assertTrue( src.getOuterDisplacement().is(NodeType.NUMBER));
        long outerDisplacement = src.getOuterDisplacement().asNumber().getValue();
        assertEquals(0xab,outerDisplacement);
    }

    // PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT
    public void testPCIndirectWithIndex8BitDisplacement() {

        OperandNode src = parseSourceOperand("MOVE $12(PC,A2.l*2),D1");
        assertEquals(AddressingMode.PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.PC, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getIndexRegister() );
        RegisterNode indexRegister = src.getIndexRegister();
        assertEquals( Register.PC, baseRegister.register );
        assertEquals(OperandSize.LONG, indexRegister.operandSize );
        assertEquals(Scaling.TWO, indexRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );

        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x12,baseDisplacement);

        assertNull( src.getOuterDisplacement() );
    }

    // PC_INDIRECT_WITH_INDEX_DISPLACEMENT
    public void testPCIndirectWithIndex16BitDisplacement() {

        OperandNode src = parseSourceOperand("MOVE $1245(PC,A2.l*2),D1");
        assertEquals(AddressingMode.PC_INDIRECT_WITH_INDEX_DISPLACEMENT,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.REGISTER) );
        RegisterNode baseRegister = src.getValue().asRegister();
        assertEquals( Register.PC, baseRegister.register );
        assertNull( baseRegister.operandSize );
        assertNull( baseRegister.scaling );

        assertNotNull( src.getIndexRegister() );
        RegisterNode indexRegister = src.getIndexRegister();
        assertEquals( Register.A2, indexRegister.register );
        assertEquals(OperandSize.LONG, indexRegister.operandSize );
        assertEquals(Scaling.TWO, indexRegister.scaling );

        assertNotNull( src.getBaseDisplacement() );

        assertTrue( src.getBaseDisplacement().is(NodeType.NUMBER));
        long baseDisplacement = src.getBaseDisplacement().asNumber().getValue();
        assertEquals(0x1245,baseDisplacement);

        assertNull( src.getOuterDisplacement() );
    }

    // ABSOLUTE_SHORT_ADDRESSING
    public void testAbsoluteShortAddressing() {

        OperandNode src = parseSourceOperand("LEA $1234,A0");
        assertEquals(AddressingMode.ABSOLUTE_SHORT_ADDRESSING,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.NUMBER) );
        assertEquals( 0x1234 , src.getValue().asNumber().getValue() );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // ABSOLUTE_LONG_ADDRESSING
    public void testAbsoluteLongAddressing()
    {
        OperandNode src = parseSourceOperand("LEA $12345678,D0");
        assertEquals(AddressingMode.ABSOLUTE_LONG_ADDRESSING,src.addressingMode);
        assertTrue( src.getValue().is(NodeType.NUMBER) );
        assertEquals( 0x12345678 , src.getValue().asNumber().getValue() );
        assertNull( src.getBaseDisplacement() );
        assertNull( src.getIndexRegister() );
        assertNull( src.getOuterDisplacement() );
    }

    // TODO: MEMORY_INDIRECT_PREINDEXED
    // TODO: PC_MEMORY_INDIRECT_PREINDEXED

    private OperandNode parseSourceOperand(String expression)
    {
        AST ast = parse(expression);
        assertEquals(1,ast.childCount());
        final StatementNode stmt = ast.child(0).asStatement();
        final InstructionNode insn = stmt.getInstruction();
        assertNotNull("Instruction not found",insn);
        final OperandNode source = insn.source();
        assertNotNull("Instruction has no source operand ?",insn);
        return source;
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
        assertEquals(AddressingMode.ABSOLUTE_SHORT_ADDRESSING,source.addressingMode);
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

    private void assertFails(Runnable r)
    {
        try {
            r.run();
            fail("Should've failed");
        }
        catch(Exception e) {
            // ok
        }
    }
}
