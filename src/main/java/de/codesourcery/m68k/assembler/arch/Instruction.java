package de.codesourcery.m68k.assembler.arch;

import de.codesourcery.m68k.assembler.ICompilationContext;
import de.codesourcery.m68k.parser.ast.IValueNode;
import de.codesourcery.m68k.parser.ast.InstructionNode;
import de.codesourcery.m68k.parser.ast.NodeType;
import de.codesourcery.m68k.parser.ast.NumberNode;
import de.codesourcery.m68k.parser.ast.OperandNode;
import de.codesourcery.m68k.parser.ast.RegisterNode;
import de.codesourcery.m68k.utils.Misc;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.codesourcery.m68k.assembler.arch.AddressingMode.ADDRESS_REGISTER_DIRECT;
import static de.codesourcery.m68k.assembler.arch.AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT;
import static de.codesourcery.m68k.assembler.arch.AddressingMode.DATA_REGISTER_DIRECT;
import static de.codesourcery.m68k.assembler.arch.AddressingMode.IMMEDIATE_VALUE;

/**
 * Enumeration of all M68000 instructions.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum Instruction
{
    NEGX("NEGX",1)
    {
        @Override public boolean supportsExplicitOperandSize() { return true; }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingModeKind(node,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA);
        }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sizeBits().with(
                Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA) );
        }
    },
    CMPM("CMPM",2)
    {
        @Override public boolean supportsExplicitOperandSize() { return true; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sizeBits().with( registerRange(Field.DST_BASE_REGISTER) )
                .with(registerRange(Field.SRC_BASE_REGISTER));
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingMode( node,AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT );
            Instruction.checkDestinationAddressingMode( node,AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT );
        }
    },
    CMP("CMP",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits().with( Instruction.sourceAddressingModes() )
                        .with(registerRange(Field.DST_BASE_REGISTER));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkDestinationAddressingMode(node,AddressingMode.DATA_REGISTER_DIRECT);
                }
            },
    SUBX("SUBX",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits().with( Instruction.registerRange(Field.DST_BASE_REGISTER))
                    .with(Instruction.registerRange(Field.SRC_BASE_REGISTER ) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.source().getValue().isDataRegister() || node.destination().getValue().isDataRegister() ) {
                        // assert both are data registers
                        Instruction.checkSourceAddressingMode(node,AddressingMode.DATA_REGISTER_DIRECT);
                        Instruction.checkDestinationAddressingMode(node,AddressingMode.DATA_REGISTER_DIRECT);
                    } else if ( node.source().hasAddressingMode(AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT) ||
                        node.destination().hasAddressingMode(AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT) )
                    {
                        Instruction.checkSourceAddressingMode(node,AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT);
                        Instruction.checkDestinationAddressingMode(node,AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT);
                    }
                    else
                    {
                        throw new RuntimeException("SUBX needs either two data registers or two address-register pre-decrement operands");
                    }
                }
            },
    SUB("SUB",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    if ( encoding == SUB_DST_DATA_ENCODING)  //  SUB <ea>,Dn
                    {
                        return Instruction.sizeBits().with( Instruction.registerRange(Field.DST_BASE_REGISTER) )
                            .with( Instruction.sourceAddressingModes() );
                    }
                    else if ( encoding == SUB_DST_EA_ENCODING ) // SUB Dn,<ea>
                    {
                        return Instruction.sizeBits().with(Instruction.registerRange(Field.SRC_BASE_REGISTER))
                            .with(Instruction.destAddressingModes(cpuType,
                                AddressingModeKind.ALTERABLE, AddressingModeKind.MEMORY));
                    }
                    throw new RuntimeException("Unreachable code reached");
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.destination().hasAddressingMode(AddressingMode.DATA_REGISTER_DIRECT ) ) {
                        // ok
                    }
                    else
                    {
                        Instruction.checkDestinationAddressingModeKind(node,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
                    }
                }
            },
    ADD("ADD",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    if ( encoding == Instruction.ADD_DST_DATA_ENCODING) {
                        return Instruction.sizeBits().with( Instruction.registerRange(Field.DST_BASE_REGISTER) )
                            .with( Instruction.sourceAddressingModes() );
                    } else if ( encoding == Instruction.ADD_DST_EA_ENCODING) {
                        return Instruction.sizeBits().with(Instruction.registerRange(Field.SRC_BASE_REGISTER))
                            .with(Instruction.destAddressingModes(cpuType,
                                AddressingModeKind.ALTERABLE, AddressingModeKind.MEMORY));
                    }
                    throw new RuntimeException("Unreachable code reached");
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.destination().hasAddressingMode(AddressingMode.DATA_REGISTER_DIRECT) ) {
                        // ok
                    } else {
                        Instruction.checkDestinationAddressingModeKind(node,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
                    }
                }
            },
    ADDX("ADDX",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                        return Instruction.sizeBits().with( Instruction.registerRange(Field.SRC_BASE_REGISTER) )
                            .with( Instruction.registerRange(Field.DST_BASE_REGISTER ) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.source().hasAddressingMode( DATA_REGISTER_DIRECT ) &&
                            node.destination().hasAddressingMode( DATA_REGISTER_DIRECT ) ) {
                        // ok
                    } else if ( node.source().hasAddressingMode( ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT ) &&
                            node.destination().hasAddressingMode( ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT ) ) {
                        // ok
                    } else {
                        throw new RuntimeException("ADDX supports only two data registers or two address registers in indirect pre-decrement mode");
                    }
                }
            },
    SUBI("SUBI",2)
    {
        @Override public boolean supportsExplicitOperandSize() { return true; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sizeBits().with( Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA,
                AddressingModeKind.ALTERABLE));
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingMode(node,AddressingMode.IMMEDIATE_VALUE);
            Instruction.checkDestinationAddressingModeKind(node,
                AddressingModeKind.ALTERABLE,AddressingModeKind.DATA);
        }
    },
    CMPI("CMPI",2)
    {
        @Override public boolean supportsExplicitOperandSize() { return true; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sizeBits().with( Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA));
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingMode(node,AddressingMode.IMMEDIATE_VALUE);
            Instruction.checkDestinationAddressingModeKind(node,AddressingModeKind.DATA );
        }
    },
    ADDI("ADDI",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits().with( Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkSourceAddressingMode(node,AddressingMode.IMMEDIATE_VALUE);
                    Instruction.checkDestinationAddressingModeKind(node,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE );
                }
            },
    CMPA("CMPA",2)
        {
            @Override public boolean supportsExplicitOperandSize() { return true; }

            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                return Instruction.sourceAddressingModes().with( Instruction.registerRange(Field.DST_BASE_REGISTER));
            }

            @Override
            public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
            {
                Instruction.checkDestinationAddressingMode(node,AddressingMode.ADDRESS_REGISTER_DIRECT);
                if( node.hasOperandSize(OperandSize.BYTE ) ) {
                    throw new RuntimeException("CMPA only supports .w or .l");
                }
            }
        },
    SUBA("SUBA",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes().with( Instruction.registerRange(Field.DST_BASE_REGISTER));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkDestinationAddressingMode(node,AddressingMode.ADDRESS_REGISTER_DIRECT);
                    if( node.hasOperandSize(OperandSize.BYTE ) ) {
                        throw new RuntimeException("SUBA only supports .w or .l");
                    }
                }
            },
    ADDA("ADDA",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes().with( Instruction.registerRange(Field.DST_BASE_REGISTER));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkDestinationAddressingMode(node,AddressingMode.ADDRESS_REGISTER_DIRECT);
                    if( node.hasOperandSize(OperandSize.BYTE ) ) {
                        throw new RuntimeException("ADDA only supports .w or .l");
                    }
                }
            },
    SUBQ("SUBQ",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits().with( Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE) )
                        .with( Instruction.range(Field.SRC_VALUE,0,8 ) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkSourceAddressingMode(node,AddressingMode.IMMEDIATE_VALUE);
                    if ( ! estimateSizeOnly ) {
                        int value = node.source().getValue().getBits(ctx);
                        if ( value < 1 || value > 8) {
                            throw new RuntimeException("SUBQ only supports values in the range 1...8");
                        }
                    }
                    Instruction.checkDestinationAddressingModeKind(node,AddressingModeKind.ALTERABLE);
                    if ( node.destination().hasAddressingMode(AddressingMode.ADDRESS_REGISTER_DIRECT) ) {
                        if ( node.hasOperandSize(OperandSize.BYTE) ) {
                            throw new RuntimeException("SUBQ #xx,An does not support byte-sized operands");
                        }
                    }
                }
            },
    ADDQ("ADDQ",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits()
                        .with( Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE) )
                        .with( Instruction.range(Field.SRC_VALUE,0,8 ) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkSourceAddressingMode(node,AddressingMode.IMMEDIATE_VALUE);
                    if ( ! estimateSizeOnly ) {
                        int value = node.source().getValue().getBits(ctx);
                        if ( value < 1 || value > 8) {
                            throw new RuntimeException("ADDQ only supports values in the range 1...8");
                        }
                    }
                    Instruction.checkDestinationAddressingModeKind(node,AddressingModeKind.ALTERABLE);
                    if ( node.destination().hasAddressingMode(AddressingMode.ADDRESS_REGISTER_DIRECT) ) {
                        if ( node.hasOperandSize(OperandSize.BYTE) ) {
                            throw new RuntimeException("ADDQ #xx,An does not support byte-sized operands");
                        }
                    }
                }
            },
    DIVS("DIVS",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes( cpuType , mode -> {
                        return mode == AddressingMode.IMMEDIATE_VALUE ||
                                ( mode.hasKinds( AddressingModeKind.DATA,AddressingModeKind.ALTERABLE ) );
                    }).with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkMultiplyDivide(node);
                }
            },
    DIVU("DIVU",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes( cpuType , mode -> {
                        return mode == AddressingMode.IMMEDIATE_VALUE ||
                                ( mode.hasKinds( AddressingModeKind.DATA,AddressingModeKind.ALTERABLE ) );
                    }).with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkMultiplyDivide(node);
                }
            },
    MULS("MULS",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes( cpuType , mode -> {
                        return mode == AddressingMode.IMMEDIATE_VALUE ||
                                ( mode.hasKinds( AddressingModeKind.DATA,AddressingModeKind.ALTERABLE ) );
                    }).with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkMultiplyDivide(node);
                }
            },
    MULU("MULU",2)
            {
                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes( cpuType , mode -> {
                        return mode == AddressingMode.IMMEDIATE_VALUE ||
                                ( mode.hasKinds( AddressingModeKind.DATA,AddressingModeKind.ALTERABLE ) );
                    }).with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkMultiplyDivide(node);
                }
            },
    EORI("EORI",2)
            {
                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits().with( Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkBinaryLogicalOperation(node,estimateSizeOnly,ctx);
                }
            },
    ORI("ORI",2)
            {
                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits().with( Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkBinaryLogicalOperation(node,estimateSizeOnly,ctx);
                }
            },
    MOVEP("MOVEP",2)
            {
                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.registerRange(Field.SRC_BASE_REGISTER).with(Instruction.registerRange(Field.DST_BASE_REGISTER));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.hasExplicitOperandSize() ) {
                        if ( node.hasOperandSize(OperandSize.BYTE) ) {
                            throw new RuntimeException("MOVEP only supports .w or .l operand sizes");
                        }
                    }
                    if ( node.source().getValue().isDataRegister() )
                    {
                        Instruction.checkDestinationAddressingMode(node,AddressingMode.ADDRESS_REGISTER_INDIRECT,AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT);
                        Instruction.checkOperandSizeSigned(node.destination().getValue(),16,ctx);
                    }
                    else if ( node.destination().getValue().isDataRegister() ) {
                        Instruction.checkSourceAddressingMode(node,AddressingMode.ADDRESS_REGISTER_INDIRECT,AddressingMode.ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT);
                        Instruction.checkOperandSizeSigned(node.source().getValue(),16,ctx);
                    }
                    else {
                        throw new UnsupportedOperationException("Method checkSupports not implemented");
                    }
                }
            },
    MOVEM("MOVEM",2)
            {
                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    if ( encoding == MOVEM_FROM_REGISTERS_ENCODING ) { // registers->memory
                        return intValues(Field.SIZE,0,1).with(
                            Instruction.destAddressingModes(cpuType,
                            mode-> mode == AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT ||
                                mode.hasKinds(AddressingModeKind.ALTERABLE,AddressingModeKind.CONTROL)));
                    }
                    if ( encoding == MOVEM_TO_REGISTERS_ENCODING ) { // memory->registers
                        return intValues(Field.SIZE,0,1).with(
                            Instruction.sourceAddressingModes(cpuType,
                                mode-> mode == AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT ||
                                    mode.hasKind(AddressingModeKind.CONTROL)));
                    } else {
                        throw new RuntimeException("Unreachable code reached");
                    }
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.hasExplicitOperandSize() && !(node.hasOperandSize(OperandSize.WORD) || node.hasOperandSize(OperandSize.LONG) ) )
                    {
                        throw new RuntimeException("MOVEM supports only .w or .l");
                    }
                    // one operand needs to be a register,register list or register range
                    final boolean srcIsRegList = isRegisterList(node.source());
                    final boolean dstIsRegList = isRegisterList(node.destination());

                    if ( ( srcIsRegList ^ dstIsRegList) == false )
                    {
                        throw new RuntimeException("MOVEM requires exactly one register,register range or register list");
                    }
                    final boolean registersToMemory = srcIsRegList;
                    if ( registersToMemory )
                    {
                        // registers -> memory
                        Instruction.checkDestinationAddressingMode(node,
                            mode-> mode == AddressingMode.ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT ||
                                mode.hasKinds(AddressingModeKind.ALTERABLE,AddressingModeKind.CONTROL));
                    }
                    else
                    {
                        // memory -> registers
                        Instruction.checkSourceAddressingMode(node,
                            mode-> mode == AddressingMode.ADDRESS_REGISTER_INDIRECT_POST_INCREMENT ||
                                mode.hasKind(AddressingModeKind.CONTROL));
                    }
                }
            },
    CHK("CHK",2)
        {
            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.DATA)
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER));
            }

            @Override
            public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
            {
                Instruction.checkSourceAddressingModeKind( node,AddressingModeKind.DATA );
                Instruction.checkDestinationAddressingMode( node,AddressingMode.DATA_REGISTER_DIRECT );

                if ( ! node.useImpliedOperandSize )
                {
                    if ( node.getOperandSize() == OperandSize.BYTE )
                    {
                        throw new RuntimeException("CHK only supports .w or .l operand sizes");
                    }
                    if ( node.hasOperandSize(OperandSize.LONG) && ! ctx.options().cpuType.supports(CHK_LONG_ENCODING) ) {
                        throw new RuntimeException("CHK.L needs 68020 or higher");
                    }
                }
            }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    TAS("TAS",1)
    {
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE);
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingModeKind(node,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE);
        }
    },
    STOP("STOP",1) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingMode(node,IMMEDIATE_VALUE);
        }
    },
    NOT("NOT",1)
        {
            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                return Instruction.sizeBits().with( Instruction.sourceAddressingModes(cpuType,
                    AddressingModeKind.ALTERABLE,AddressingModeKind.DATA));
            }

            @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingModeKind( node,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA);
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    TRAPV("TRAPV",0) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
        }
    },
    TST("TST",1)
        {
            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                return Instruction.sizeBits().with( Instruction.sourceAddressingModes());
            }

            @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            final AddressingMode mode = node.source().addressingMode;
            if ( mode == AddressingMode.PC_INDIRECT_WITH_DISPLACEMENT ||
                    mode == AddressingMode.PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT ||
                    mode == AddressingMode.PC_INDIRECT_WITH_INDEX_DISPLACEMENT ||
                    mode == AddressingMode.IMMEDIATE_VALUE ||
                    mode == AddressingMode.ADDRESS_REGISTER_DIRECT ||
                    mode == AddressingMode.PC_MEMORY_INDIRECT_POSTINDEXED ||
                    mode == AddressingMode.PC_MEMORY_INDIRECT_PREINDEXED )
            {
                throw new RuntimeException("TST does not support addressing mode "+mode+" on 68000");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    CLR("CLR",1)
        {
            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                /*
                    InstructionEncoding.of( "01000010SSmmmsss");
                 */
                return Instruction.sizeBits().with( Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,
                    AddressingModeKind.DATA));
            }

            @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingModeKind(node,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA);
        }
        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    BCHG("BCHG",2) {
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == BCHG_DYNAMIC_ENCODING ) {
                return Instruction.registerRange(Field.SRC_BASE_REGISTER).with(
                    Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA));
            }
            if ( encoding == BCHG_STATIC_ENCODING ) {
                return Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA);
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkBitInstructionValid( node,ctx );
        }
    },
    BSET("BSET",2)
        {
            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                if ( encoding == BSET_DYNAMIC_ENCODING ) {
                    return Instruction.registerRange(Field.SRC_BASE_REGISTER).with(
                        Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA));
                }
                if ( encoding == BSET_STATIC_ENCODING ) {
                    return Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA);
                }
                throw new RuntimeException("Unreachable code reached");
            }

            @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkBitInstructionValid( node,ctx );
        }
    },
    BCLR("BCLR",2)
        {
            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                if ( encoding == BCLR_DYNAMIC_ENCODING ) {
                    return Instruction.registerRange(Field.SRC_BASE_REGISTER).with(
                        Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA));
                }
                if ( encoding == BCLR_STATIC_ENCODING ) {
                    return Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA);
                }
                throw new RuntimeException("Unreachable code reached");
            }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkBitInstructionValid( node,ctx );
        }
    },
    BTST("BTST",2)
        {

            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                if ( encoding == BTST_DYNAMIC_ENCODING ) {
                    return Instruction.registerRange(Field.SRC_BASE_REGISTER).with(
                        Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA));
                }
                if ( encoding == BTST_STATIC_ENCODING ) {
                    return Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA);
                }
                throw new RuntimeException("Unreachable code reached");
            }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkDestinationAddressingModeKind( node,AddressingModeKind.DATA );
            if ( node.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                final Integer bitNum = node.source().getValue().getBits( ctx );
                if ( node.destination().addressingMode.hasKind( AddressingModeKind.MEMORY ) ) {
                    if ( bitNum != null && (bitNum < 0 || bitNum > 7) ) {
                        throw new RuntimeException( "BTST with memory locations can only operate on bits 0..7");
                    }
                } else {
                    if ( bitNum != null && (bitNum < 0 || bitNum > 31) ) {
                        throw new RuntimeException( "BTST can only operate on bits 0..31");
                    }
                }
            }
            else if ( ! node.source().getValue().isDataRegister() )
            {
                throw new RuntimeException( "Unsupported source addressing mode for BTST, only immediate or data register are allowed" );
            }
        }
    },
    EXT("EXT",1) {
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.registerRange(Field.SRC_BASE_REGISTER);
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingMode( node,AddressingMode.DATA_REGISTER_DIRECT );
            if ( node.hasOperandSize( OperandSize.BYTE ) ) {
                throw new RuntimeException("Only operand sizes WORD or LONG are supported by EXT");
            }
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ASL("ASL",2) {
        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == ASL_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == ASL_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == ASL_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ASR("ASR",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == ASR_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == ASR_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == ASR_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ROXL("ROXL",2) {
        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == ROXL_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == ROXL_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == ROXL_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ROXR("ROXR",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == ROXR_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == ROXR_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == ROXR_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    LSL("LSL",2) {
        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == LSL_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == LSL_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == LSL_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    LSR("LSR",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == LSR_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == LSR_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == LSR_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ROL("ROL",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == ROL_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == ROL_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == ROL_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    ROR("ROR",2) {

        @Override public int getMinOperandCount() { return 1; }

        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == ROR_IMMEDIATE_ENCODING ) {
                return Instruction.sizeBits().with( Instruction.range(Field.SRC_VALUE,0,8))
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER) );
            }
            if ( encoding == ROR_MEMORY_ENCODING ) {
                return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.MEMORY);
            }
            if ( encoding == ROR_REGISTER_ENCODING ) {
                return Instruction.sizeBits().with( range(Field.DST_VALUE,0,8) )
                    .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
            }
            throw new RuntimeException("Unreachable code reached");
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            checkRotateInstructionValid(node,ctx);
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    NEG("NEG",1)
    {
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sizeBits().with( Instruction.sourceAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA));
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.DATA,AddressingModeKind.ALTERABLE );
        }

        @Override public boolean supportsExplicitOperandSize() { return true; }
    },
    PEA("PEA",1)
    {
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.CONTROL);
        }

        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL );
        }
    },
    RTR("RTR",0) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
        }
    },
    RESET("RESET",0)
            {
                @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) {}
            },
    UNLK("UNLK",1)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.registerRange(Field.SRC_BASE_REGISTER);
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( ! node.source().getValue().isAddressRegister() ) {
                        throw new RuntimeException("Expected an address register as source operand");
                    }
                }
            },
    LINK("LINK",2)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.registerRange(Field.SRC_BASE_REGISTER);
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( ! node.source().getValue().isAddressRegister() ) {
                        throw new RuntimeException("Expected an address register as source operand");
                    }
                    if ( ! node.destination().hasAddressingMode(AddressingMode.IMMEDIATE_VALUE ) ) {
                        throw new RuntimeException("Expected an immediate mode value as destination operand");
                    }
                    Instruction.checkOperandSizeUnsigned( node.destination().getValue() ,16,ctx );
                }
            },
    RTS("RTS",0)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {

                }
            },
    JSR("JSR",1)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.CONTROL);
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL);
                }
            },
    SWAP("SWAP",1)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.registerRange(Field.SRC_BASE_REGISTER);
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( ! node.source().getValue().isDataRegister() ) {
                        throw new RuntimeException("SWAP requires a data requires");
                    }
                    if ( ! node.useImpliedOperandSize && node.getOperandSize() != OperandSize.WORD ) {
                        throw new RuntimeException("SWAP only supports .w");
                    }
                }

                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }
            },
    JMP("JMP",1)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.CONTROL);
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL);
                }
            },
    EOR("EOR",2)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sizeBits()
                        .with( Instruction.registerRange(Field.SRC_BASE_REGISTER))
                        .with( Instruction.destAddressingModes(cpuType,AddressingModeKind.ALTERABLE,AddressingModeKind.DATA));
                }

                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }

                @Override
                public void checkSupports(InstructionNode insn, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkSourceAddressingMode(insn,AddressingMode.DATA_REGISTER_DIRECT);
                }
            },
    OR("OR",2)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    if ( encoding == OR_SRC_EA_ENCODING ) { // OR <ea>,Dn
                        return Instruction.sizeBits().with(
                            Instruction.sourceAddressingModes(cpuType,AddressingModeKind.DATA) )
                            .with(Instruction.registerRange(Field.DST_BASE_REGISTER));
                    }
                    if ( encoding == OR_DST_EA_ENCODING ) { // OR Dn,<ea>
                        return Instruction.sizeBits().with(
                            Instruction.destAddressingModes(cpuType,AddressingModeKind.MEMORY,AddressingModeKind.ALTERABLE) )
                            .with(Instruction.registerRange(Field.SRC_BASE_REGISTER));
                    }
                    throw new RuntimeException("Unreachable code reached");
                }

                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }

                @Override
                public void checkSupports(InstructionNode insn, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkBinaryLogicalOperation(insn,estimateSizeOnly,ctx);
                }
            },
    AND("AND",2)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    if ( encoding == AND_SRC_EA_ENCODING ) { // AND <ea>,Dn
                        return Instruction.sizeBits().with(
                            Instruction.sourceAddressingModes(cpuType,AddressingModeKind.DATA) )
                            .with(Instruction.registerRange(Field.DST_BASE_REGISTER));
                    }
                    if ( encoding == AND_DST_EA_ENCODING ) { // AND Dn,<ea>
                        return Instruction.sizeBits().with(
                            Instruction.destAddressingModes(cpuType,AddressingModeKind.MEMORY,AddressingModeKind.ALTERABLE) )
                            .with(Instruction.registerRange(Field.SRC_BASE_REGISTER));
                    }
                    if ( encoding == ANDI_BYTE_ENCODING ||
                         encoding == ANDI_WORD_ENCODING ||
                         encoding == ANDI_LONG_ENCODING )
                    {
                        return Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA,
                            AddressingModeKind.ALTERABLE);
                    }
                    throw new RuntimeException("Unreachable code reached");
                }

                @Override
                public boolean supportsExplicitOperandSize()
                {
                    return true;
                }

                @Override
                public void checkSupports(InstructionNode insn, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    checkBinaryLogicalOperation(insn,estimateSizeOnly,ctx);
                }
            },
    TRAP("TRAP",1, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.hasDestination() ) {
                        throw new RuntimeException("TRAP only supports one operand");
                    }
                    if ( ! node.source().hasAddressingMode(AddressingMode.IMMEDIATE_VALUE ) ) {
                        throw new RuntimeException("TRAP requires an immediate mode value as operand but was "+node.source().addressingMode);
                    }

                    if ( ! estimateSizeOnly )
                    {
                        final int value = node.source().getValue().getBits(ctx);
                        if ( value < 0 || value > 15 ) {
                            throw new RuntimeException("TRAP # out-of-range (0-15), was "+value);
                        }
                    }
                }

                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return range(Field.SRC_VALUE, 0,16);
                }
            },
    RTE("RTE",0)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                }
            },
    ILLEGAL("ILLEGAL",0) {
        @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
        }
    },
    /*
     * Scc instructions
     */
    ST("ST",1, 0b0000,Condition.BRT,ConditionalInstructionType.SCC) { // always true

        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SF("SF",1, 0b0001,Condition.BSR,ConditionalInstructionType.SCC) { // always false
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SHI("SHI",1, 0b0010,Condition.BHI,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SLS("SLS",1, 0b0011,Condition.BLS,ConditionalInstructionType.SCC)
        {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SCC("SCC",1, 0b0100,Condition.BCC,ConditionalInstructionType.SCC)
    {
        // note that this getValueIterator() actually produces bit-patterns for
        // all Scc instructions and not just SCC
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE)
                .with( Instruction.range(Field.CONDITION_CODE,0,16));
        }
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SCS("SCS",1, 0b0101,Condition.BCS,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SNE("SNE",1, 0b0110,Condition.BNE,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SEQ("SEQ",1, 0b0111,Condition.BEQ,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SVC("SVC",1, 0b1000,Condition.BVC,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SVS("SVS",1, 0b1001,Condition.BVS,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SPL("SPL",1, 0b1010,Condition.BPL,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SMI("SMI",1, 0b1011,Condition.BMI,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SGE("SGE",1, 0b1100,Condition.BGE,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SLT("SLT",1, 0b1101,Condition.BLT,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SGT("SGT",1, 0b1110,Condition.BGT,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    SLE("SLE",1, 0b1111,Condition.BLE,ConditionalInstructionType.SCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkSccInstructionValid(node,ctx); }
    },
    /*
     * DBcc instructions
     */
    DBT("DBT",2, 0b0000,Condition.BRT,ConditionalInstructionType.DBCC) { // aka 'always branch'
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBRA("DBRA",2, 0b0001,Condition.BSR,ConditionalInstructionType.DBCC) { // ignores condition check
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBHI("DBHI",2, 0b0010,Condition.BHI,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBLS("DBLS",2, 0b0011,Condition.BLS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBCC("DBCC",2, 0b0100,Condition.BCC,ConditionalInstructionType.DBCC)
    {
        // note that this getValueIterator() actually produces bit-patterns for
        // all DBcc instructions and not just dbcc
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            return Instruction.range(Field.CONDITION_CODE,0,16)
                .with( Instruction.registerRange(Field.SRC_BASE_REGISTER));
        }

        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBCS("DBCS",2, 0b0101,Condition.BCS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBNE("DBNE",2, 0b0110,Condition.BNE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBEQ("DBEQ",2, 0b0111,Condition.BEQ,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBVC("DBVC",2, 0b1000,Condition.BVC,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBVS("DBVS",2, 0b1001,Condition.BVS,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBPL("DBPL",2, 0b1010,Condition.BPL,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBMI("DBMI",2, 0b1011,Condition.BMI,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBGE("DBGE",2, 0b1100,Condition.BGE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBLT("DBLT",2, 0b1101,Condition.BLT,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBGT("DBGT",2, 0b1110,Condition.BGT,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    DBLE("DBLE",2, 0b1111,Condition.BLE,ConditionalInstructionType.DBCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkDBccInstructionValid(node,ctx); }
    },
    /*
     * Bcc instructions.
     */
    BRA("BRA",1, 0b0000,Condition.BRT,ConditionalInstructionType.BCC) { // aka 'always branch'
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BSR("BSR",1, 0b0001,Condition.BSR,ConditionalInstructionType.BCC) { // TODO: this is essentially "never branch" .... not very useful as NOP exists as well...
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BHI("BHI",1, 0b0010,Condition.BHI,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BLS("BLS",1, 0b0011,Condition.BLS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BCC("BCC",1, 0b0100,Condition.BCC,ConditionalInstructionType.BCC)
    {
        // note that this getValueIterator() actually produces bit-patterns for
        // all Bcc instructions and not just bcc
        @Override
        public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
        {
            if ( encoding == BCC_8BIT_ENCODING ) {
                return Instruction.range(Field.CONDITION_CODE,0,16)
                    .with( range(Field.RELATIVE_OFFSET,1,255) );
            }
            return Instruction.range(Field.CONDITION_CODE,0,16);
        }
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BCS("BCS",1, 0b0101,Condition.BCS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BNE("BNE",1, 0b0110,Condition.BNE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BEQ("BEQ",1, 0b0111,Condition.BEQ,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BVC("BVC",1, 0b1000,Condition.BVC,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BVS("BVS",1, 0b1001,Condition.BVS,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BPL("BPL",1, 0b1010,Condition.BPL,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BMI("BMI",1, 0b1011,Condition.BMI,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BGE("BGE",1, 0b1100,Condition.BGE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BLT("BLT",1, 0b1101,Condition.BLT,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BGT("BGT",1, 0b1110,Condition.BGT,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    BLE("BLE",1, 0b1111,Condition.BLE,ConditionalInstructionType.BCC) {
        @Override public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly) { checkBranchInstructionValid(node,ctx); }
    },
    // Misc
    NOP("nop",0, 0b0100)
            {
                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    if ( node.hasChildren() ) {
                        throw new RuntimeException("NOP does not accept operands");
                    }
                }
            },
    EXG("exg",2, 0b1100)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.registerRange(Field.EXG_ADDRESS_REGISTER)
                        .with( Instruction.registerRange(Field.EXG_DATA_REGISTER ) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    final OperandNode source = node.source();

                    if ( ! source.getValue().isRegister() ) {
                        throw new RuntimeException("Bad operand type, EXG needs a address or data register");
                    }
                    if ( ! ( source.getValue().isDataRegister() || source.getValue().isAddressRegister() ) ) {
                        throw new RuntimeException("Unsupported register, EXG supports address or data registers");
                    }
                    final OperandNode destination = node.destination();
                    if ( ! destination.getValue().isRegister() ) {
                        throw new RuntimeException("Bad operand type, EXG needs a address or data register");
                    }
                    if ( ! ( destination.getValue().isDataRegister() || destination.getValue().isAddressRegister() ) ) {
                        throw new RuntimeException("Unsupported register, EXG supports address or data registers");
                    }
                }
            },
    MOVEA("movea", 2, 0b0000)
        {
            @Override
            public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
            {
                return Instruction.sourceAddressingModes()
                    .with( Instruction.registerRange(Field.DST_BASE_REGISTER));
            }

            @Override
        public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
        {
            if ( ! node.destination().getValue().isAddressRegister() ) {
                throw new RuntimeException("MOVEA requires an address register as destination");
            }
            if ( ! node.useImpliedOperandSize && node.getOperandSize() == OperandSize.BYTE) {
                throw new RuntimeException("MOVEA only supports .w or .l");
            }
        }

        @Override
        public boolean supportsExplicitOperandSize()
        {
            return true;
        }
    },
    MOVEQ("moveq", 2, 0b0111)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.registerRange(Field.DST_BASE_REGISTER)
                        .with( range(Field.SRC_VALUE,0,256 ) );
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    final OperandNode source = node.source();
                    if ( ! source.hasAddressingMode(AddressingMode.IMMEDIATE_VALUE) )
                    {
                        throw new RuntimeException("MOVEQ requires an immediate value as source operand");
                    }
                    final OperandNode destination = node.destination();
                    if ( ! destination.hasAddressingMode(AddressingMode.DATA_REGISTER_DIRECT) )
                    {
                        throw new RuntimeException("MOVEQ requires a data register as destination operand");
                    }
                    Instruction.checkOperandSizeUnsigned(node.source().getValue(), 8,ctx);
                }

            },
    MOVE("move", 2, 0b0000)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    if ( encoding == MOVE_BYTE_ENCODING ||
                        encoding  == MOVE_WORD_ENCODING ||
                        encoding  == MOVE_LONG_ENCODING)
                    {
                        return Instruction.sourceAddressingModes()
                            .with( Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA,
                                AddressingModeKind.ALTERABLE));
                    }
                    if ( encoding == MOVE_TO_CCR_ENCODING )
                    {
                        return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.DATA);
                    }
                    if ( encoding == MOVE_AX_TO_USP_ENCODING ) {
                        return Instruction.registerRange(Field.SRC_BASE_REGISTER);
                    }
                    if ( encoding == MOVE_USP_TO_AX_ENCODING ) {
                        return Instruction.registerRange(Field.DST_BASE_REGISTER);
                    }
                    if ( encoding == MOVE_FROM_SR_ENCODING ) {
                        // 0100000011MMMDDD
                        return Instruction.destAddressingModes(cpuType,AddressingModeKind.DATA,
                            AddressingModeKind.ALTERABLE);
                    }
                    if ( encoding == MOVE_TO_SR_ENCODING ) {
                        return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.DATA);
                    }
                    throw new RuntimeException("Unreachable code reached");
                }

                @Override
                public int getOperationCode(InstructionNode insn)
                {
                    switch (insn.getOperandSize())
                    {
                        case BYTE:
                            return 0b0001;
                        case WORD:
                            return 0b0011;
                        case LONG:
                            return 0b0010;
                    }
                    throw new RuntimeException("Unhandled switch/case: " + insn.getOperandSize());
                }

                @Override public boolean supportsExplicitOperandSize() { return true; }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                }
            },
    LEA("lea", 2, 0b0100)
            {
                @Override
                public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
                {
                    return Instruction.sourceAddressingModes(cpuType,AddressingModeKind.CONTROL)
                        .with( Instruction.registerRange(Field.DST_BASE_REGISTER));
                }

                @Override
                public void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly)
                {
                    Instruction.checkSourceAddressingModeKind(node, AddressingModeKind.CONTROL);

                    final OperandNode source = node.source();
                    final OperandNode destination = node.destination();
                    if ( destination.getValue().isNot(NodeType.REGISTER) || ! destination.getValue().asRegister().isAddressRegister() )
                    {
                        throw new RuntimeException("LEA needs an address register as destination");
                    }
                }
            };

    private static final Logger LOG = LogManager.getLogger( Instruction.class.getName() );

    public final ConditionalInstructionType conditionalType;
    public final Condition condition;
    private final String mnemonic;
    private final int maxOperandCount;
    private final int operationMode; // bits 15-12 of first instruction word

    Instruction(String mnemonic, int maxOperandCount) {
        this(mnemonic,maxOperandCount, 0,null,ConditionalInstructionType.NONE);
    }

    Instruction(String mnemonic, int maxOperandCount, int operationMode) {
        this(mnemonic,maxOperandCount, operationMode,null,ConditionalInstructionType.NONE);
    }

    Instruction(String mnemonic, int maxOperandCount, int operationMode, Condition condition, ConditionalInstructionType conditionalType)
    {
        if ( maxOperandCount > 2 ) {
            throw new IllegalArgumentException("Parser only supports up to 2 operands");
        }
        this.mnemonic = mnemonic.toLowerCase();
        this.maxOperandCount = maxOperandCount;
        this.operationMode = operationMode;
        this.condition = condition;
        this.conditionalType = conditionalType;
    }

    public int getOperationMode()
    {
        return operationMode;
    }

    public abstract void checkSupports(InstructionNode node, ICompilationContext ctx, boolean estimateSizeOnly);

    public int getOperationCode(InstructionNode insn)
    {
        return operationMode;
    }

    public int getMaxOperandCount()
    {
        return maxOperandCount;
    }

    public int getMinOperandCount()
    {
        return maxOperandCount;
    }

    public String getMnemonic()
    {
        return mnemonic;
    }

    public static Instruction getType(String value)
    {
        if (value != null)
        {
            final String lValue = value.toLowerCase();
            for (Instruction t : Instruction.values())
            {
                if (t.mnemonic.equals(lValue))
                {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Generates the code for a given instruction and writes it
     * using the context's current {@link de.codesourcery.m68k.assembler.IObjectCodeWriter}.
     *
     * @param insn
     * @param context
     * @param estimateSizeForUnknownOperands set to <code>true</code> to call {@link de.codesourcery.m68k.assembler.IObjectCodeWriter#allocateBytes(int)}
     *                         instead of writing the actual data bytes. Used while operands may still be unknown due to forward references
     */
    public void generateCode(InstructionNode insn, ICompilationContext context,boolean estimateSizeForUnknownOperands)
    {
        final InstructionEncoding encoding;
        final byte[] data;
        try
        {
            encoding = getEncoding(this, insn, context,estimateSizeForUnknownOperands);
            if ( estimateSizeForUnknownOperands ) {
                final int sizeInBytes = encoding.getSizeInBytes();
                context.getCodeWriter().allocateBytes(sizeInBytes);
                return;
            }

            final Function<Field, Integer> func;
            final Condition condition = insn.instruction.condition;
            if ( condition != null )
            {
                final int instructionAddress = context.getCodeWriter().offset();
                func = field ->
                {
                    switch( insn.instruction.conditionalType )
                    {
                        case SCC:
                            if ( field == Field.CONDITION_CODE)
                            {
                                return condition.bits;
                            }
                            return getValueFor(insn,field,context);
                        case DBCC:
                            if (field == Field.SRC_BASE_REGISTER)
                            { // DBcc Dx,...
                                return insn.source().getValue().getBits(context);
                            }
                            if ( field == Field.CONDITION_CODE)
                            {
                                return condition.bits;
                            }
                            if ( field == Field.RELATIVE_OFFSET )
                            {
                                final Integer branchTargetAddress = insn.destination().getValue().getBits(context);
                                if ( (branchTargetAddress & 1) != 0 ) {
                                    throw new RuntimeException("Relative branch needs an even target address but got "+branchTargetAddress);
                                }
                                return branchTargetAddress - instructionAddress - 2;
                            }
                            break;
                        case BCC:
                            if ( field == Field.CONDITION_CODE)
                            {
                                return condition.bits;
                            }
                            if ( field == Field.RELATIVE_OFFSET )
                            {
                                final Integer branchTargetAddress = insn.source().getValue().getBits(context);
                                if ( (branchTargetAddress & 1) != 0 ) {
                                    throw new RuntimeException("Relative branch needs an even target address but got "+branchTargetAddress);
                                }
                                return branchTargetAddress - instructionAddress - 2;
                            }
                            break;
                        case NONE:
                            throw new RuntimeException("Internal error - Instruction "+insn.instruction+" has non-NULL CC value but conditional instruction type NONE ?");
                        default:
                            throw new RuntimeException("Internal error - Instruction "+insn.instruction+" has unhandled conditional instruction type "+insn.instruction.conditionalType);
                    }
                    throw new RuntimeException("Internal error,unhandled field "+field);
                };
            } else {
                func = field -> getValueFor(insn, field, context);
            }
            data = encoding.apply(func);
            context.getCodeWriter().writeBytes(data);
        }
        catch (Exception e)
        {
            context.error(e.getMessage(), insn, e);
        }
    }

    protected InstructionEncoding getEncoding(Instruction type,
                                              InstructionNode insn,
                                              ICompilationContext context,
                                              boolean estimateSizeOnly)
    {
        String[] extraInsnWords;
        type.checkSupports(insn, context, estimateSizeOnly);
        switch (type)
        {
            case MOVEM:
                insn.setImplicitOperandSize(OperandSize.WORD);
                if ( isRegisterList( insn.source() ) ) {
                    // MOVEM <register list>,<ea>
                    extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                    return MOVEM_FROM_REGISTERS_ENCODING.append(extraInsnWords);
                }
                // MOVEM <ea>,<register list>
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return MOVEM_TO_REGISTERS_ENCODING.append(extraInsnWords);
            case MOVEP:
                insn.setImplicitOperandSize(OperandSize.WORD);

                if ( insn.hasOperandSize(OperandSize.WORD ) ) {
                    if ( insn.source().getValue().isDataRegister() ) {
                        return MOVEP_WORD_TO_MEMORY_ENCODING;
                    }
                    return MOVEP_WORD_FROM_MEMORY_ENCODING;
                }
                // operandSize == OperandSize.LONG
                if ( insn.source().getValue().isDataRegister() ) {
                    return MOVEP_LONG_TO_MEMORY_ENCODING;
                }
                return MOVEP_LONG_FROM_MEMORY_ENCODING;
            case STOP:
                return STOP_ENCODING;
            case CHK:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( insn.hasOperandSize( OperandSize.WORD ) ) {
                    return CHK_WORD_ENCODING.append(extraInsnWords);
                }
                return CHK_LONG_ENCODING.append(extraInsnWords);
            case DIVS:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return DIVS_ENCODING.append(extraInsnWords);
            case DIVU:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return DIVU_ENCODING.append(extraInsnWords);
            case CMP:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return CMP_ENCODING.append(extraInsnWords);
            case SUBX:
                insn.setImplicitOperandSize(OperandSize.WORD);
                if ( insn.source().getValue().isDataRegister() ) {
                    return SUBX_DATA_REG_ENCODING;
                }
                return SUBX_ADDR_REG_ENCODING;
            case SUB:
                if ( insn.destination().hasAddressingMode(DATA_REGISTER_DIRECT) )
                {
                    extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                    return SUB_DST_DATA_ENCODING.append(extraInsnWords);
                }
                extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                return SUB_DST_EA_ENCODING.append(extraInsnWords);
            case ADD:
                if ( insn.destination().hasAddressingMode(DATA_REGISTER_DIRECT) )
                {
                    extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                    return ADD_DST_DATA_ENCODING.append(extraInsnWords);
                }
                extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                return ADD_DST_EA_ENCODING.append(extraInsnWords);
            case ADDX:
                insn.setImplicitOperandSize(OperandSize.WORD);
                if ( insn.source().hasAddressingMode( AddressingMode.DATA_REGISTER_DIRECT ) ) {
                    return ADDX_DATAREG_ENCODING;
                }
                return ADDX_ADDRREG_ENCODING;
            case NEGX:
                insn.setImplicitOperandSize( OperandSize.WORD );
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return NEGX_ENCODING.append(extraInsnWords);
            case CMPM:
                insn.setImplicitOperandSize( OperandSize.WORD );
                return CMPM_ENCODING;
            case CMPI:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                if ( ! insn.hasOperandSize(OperandSize.LONG) )
                {
                    return CMPI_WORD_ENCODING.append(extraInsnWords);
                }
                return CMPI_LONG_ENCODING.append(extraInsnWords);
            case SUBI:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                if ( ! insn.hasOperandSize(OperandSize.LONG) )
                {
                    return SUBI_WORD_ENCODING.append(extraInsnWords);
                }
                return SUBI_LONG_ENCODING.append(extraInsnWords);
            case ADDI:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                if ( ! insn.hasOperandSize(OperandSize.LONG) )
                {
                    return ADDI_WORD_ENCODING.append(extraInsnWords);
                }
                return ADDI_LONG_ENCODING.append(extraInsnWords);
            case CMPA:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( insn.hasOperandSize(OperandSize.WORD) )
                {
                    return CMPA_WORD_ENCODING.append(extraInsnWords);
                }
                return CMPA_LONG_ENCODING.append(extraInsnWords);
            case SUBA:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( insn.hasOperandSize(OperandSize.WORD) )
                {
                    return SUBA_WORD_ENCODING.append(extraInsnWords);
                }
                return SUBA_LONG_ENCODING.append(extraInsnWords);
            case ADDA:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( insn.hasOperandSize(OperandSize.WORD) )
                {
                    return ADDA_WORD_ENCODING.append(extraInsnWords);
                }
                return ADDA_LONG_ENCODING.append(extraInsnWords);
            case SUBQ:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                return SUBQ_ENCODING.append(extraInsnWords);
            case ADDQ:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                return ADDQ_ENCODING.append(extraInsnWords);
            case MULS:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return MULS_ENCODING.append(extraInsnWords);
            case MULU:
                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return MULU_ENCODING.append(extraInsnWords);
            case TAS:
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return TAS_ENCODING.append(extraInsnWords);
            case TRAPV:
                return TRAPV_ENCODING;
            case TST:
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return TST_ENCODING.append(extraInsnWords);
            case NOT:
                LOG.info( "NOT( "+insn.source().addressingMode+")" );
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return NOT_ENCODING.append(extraInsnWords);
            case CLR:
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return CLR_ENCODING.append(extraInsnWords);
            case BCHG:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                    return BCHG_STATIC_ENCODING.append(extraInsnWords);
                }
                return BCHG_DYNAMIC_ENCODING;
            case BSET:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                    return BSET_STATIC_ENCODING.append(extraInsnWords);
                }
                return BSET_DYNAMIC_ENCODING;
            case BCLR:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                    return BCLR_STATIC_ENCODING.append( extraInsnWords );
                }
                return BCLR_DYNAMIC_ENCODING;
            case BTST:
                if ( insn.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
                    extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                    return BTST_STATIC_ENCODING.append(extraInsnWords);
                }
                return BTST_DYNAMIC_ENCODING;
            case EXT:
                if ( insn.useImpliedOperandSize || insn.hasOperandSize( OperandSize.WORD ) ) {
                    return EXTW_ENCODING;
                }
                return EXTL_ENCODING;
            case EOR:
                insn.setImplicitOperandSize(OperandSize.WORD);
                String[] patterns = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn, context);
                return EOR_DST_EA_ENCODING.append(patterns);
            case ORI:
                /*
If the destination is a data register, it must be specified using the
destination Dn mode, not the destination < ea > mode.
                 */

                if ( insn.destination().getValue().isRegister(Register.CCR) )
                {
                    insn.setImplicitOperandSize(OperandSize.BYTE);
                    return ORI_TO_CCR_ENCODING;
                }
                if ( insn.destination().getValue().isRegister(Register.SR) )
                {
                    insn.setImplicitOperandSize(OperandSize.WORD);
                    return ORI_TO_SR_ENCODING;
                }

                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.destination(),Operand.DESTINATION,insn,context);

                if ( ! estimateSizeOnly && insn.getOperandSize() != OperandSize.LONG)
                {
                    return ORI_WORD_ENCODING.append(extraInsnWords);
                }
                return ORI_LONG_ENCODING.append(extraInsnWords);
            case EORI:

                if ( insn.destination().getValue().isRegister(Register.CCR) )
                {
                    insn.setImplicitOperandSize(OperandSize.BYTE);
                    return EORI_TO_CCR_ENCODING;
                }
                if ( insn.destination().getValue().isRegister(Register.SR) )
                {
                    insn.setImplicitOperandSize(OperandSize.WORD);
                    return EORI_TO_SR_ENCODING;
                }

                insn.setImplicitOperandSize(OperandSize.WORD);
                extraInsnWords = getExtraWordPatterns(insn.destination(),Operand.DESTINATION,insn,context);
                if ( ! estimateSizeOnly && insn.getOperandSize() != OperandSize.LONG)
                {
                    return EORI_WORD_ENCODING.append(extraInsnWords);
                }
                return EORI_LONG_ENCODING.append(extraInsnWords);
            case ROXL:
                return selectRotateEncoding( insn,
                        ROXL_MEMORY_ENCODING,
                        ROXL_IMMEDIATE_ENCODING,
                        ROXL_REGISTER_ENCODING,context);
            case ROXR:
                return selectRotateEncoding( insn,
                        ROXR_MEMORY_ENCODING,
                        ROXR_IMMEDIATE_ENCODING,
                        ROXR_REGISTER_ENCODING,context);
            case ASL:
                return selectRotateEncoding( insn,
                        ASL_MEMORY_ENCODING,
                        ASL_IMMEDIATE_ENCODING,
                        ASL_REGISTER_ENCODING,context);
            case ASR:
                return selectRotateEncoding( insn,
                        ASR_MEMORY_ENCODING,
                        ASR_IMMEDIATE_ENCODING,
                        ASR_REGISTER_ENCODING,context);
            case LSL:
                return selectRotateEncoding( insn,
                        LSL_MEMORY_ENCODING,
                        LSL_IMMEDIATE_ENCODING,
                        LSL_REGISTER_ENCODING,context);
            case LSR:
                return selectRotateEncoding( insn,
                        LSR_MEMORY_ENCODING,
                        LSR_IMMEDIATE_ENCODING,
                        LSR_REGISTER_ENCODING,context);
            case ROL:
                return selectRotateEncoding( insn,
                        ROL_MEMORY_ENCODING,
                        ROL_IMMEDIATE_ENCODING,
                        ROL_REGISTER_ENCODING,context);
            case ROR:
                return selectRotateEncoding( insn,
                        ROR_MEMORY_ENCODING,
                        ROR_IMMEDIATE_ENCODING,
                        ROR_REGISTER_ENCODING,context);
            case NEG:
                return NEG_ENCODING;
            case PEA:
                extraInsnWords = getExtraWordPatterns(insn.source(),Operand.SOURCE,insn,context);
                return PEA_ENCODING.append( extraInsnWords );
            case RTR:
                return RTR_ENCODING;
            case RESET:
                return RESET_ENCODING;
            case UNLK:
                return UNLK_ENCODING;
            case LINK:
                return LINK_ENCODING;
            case RTS:
                return RTS_ENCODING;
            case JSR:
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return JSR_ENCODING.append(extraInsnWords);
            case SWAP:
                return SWAP_ENCODING;
            case JMP:
                switch( insn.source().addressingMode )
                {
                    case ADDRESS_REGISTER_INDIRECT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                    case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    case MEMORY_INDIRECT_POSTINDEXED:
                    case MEMORY_INDIRECT_PREINDEXED:
                    case PC_INDIRECT_WITH_DISPLACEMENT:
                    case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                    case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                    case PC_MEMORY_INDIRECT_POSTINDEXED:
                    case PC_MEMORY_INDIRECT_PREINDEXED:
                        return JMP_INDIRECT_ENCODING;
                    case ABSOLUTE_SHORT_ADDRESSING:
                        return JMP_SHORT_ENCODING;
                    case ABSOLUTE_LONG_ADDRESSING:
                        return JMP_LONG_ENCODING;
                    default:
                        throw new RuntimeException("Unsupported addressing mode for JMP: "+insn.source().addressingMode);
                }
            case OR:
                // OR Dn,<ea>
                // OR <ea>,Dn
                insn.setImplicitOperandSize(OperandSize.WORD);
                if ( ! insn.destination().hasAddressingMode(AddressingMode.DATA_REGISTER_DIRECT) ) {
                    patterns = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn, context);
                    return OR_DST_EA_ENCODING.append(patterns);
                }
                patterns = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn, context);
                return OR_SRC_EA_ENCODING.append(patterns);
            case AND:
                if ( insn.source().hasAddressingMode( IMMEDIATE_VALUE ) )
                {
                    // ANDI #xx,<ea>
                    if ( insn.destination().getValue().isRegister(Register.SR) )
                    {
                        // ANDI #xx,SR
                        insn.setImplicitOperandSize(OperandSize.WORD);
                        return ANDI_TO_SR_ENCODING;
                    }
                    if ( insn.destination().getValue().isRegister(Register.CCR) )
                    {
                        // ANDI #xx,SR
                        insn.setImplicitOperandSize(OperandSize.BYTE);
                        return ANDI_TO_CCR_ENCODING;
                    }
                    insn.setImplicitOperandSize(OperandSize.WORD);
                    if ( estimateSizeOnly )
                    {
                        return ANDI_LONG_ENCODING; // TODO: Wrong size estimate if addressing mode requires additional instruction words...
                    }
                    final int value = insn.source().getValue().getBits(context);
                    final int sizeInBits = NumberNode.getSizeInBitsUnsigned(value);
                    if ( sizeInBits <= 8) {
                        return ANDI_BYTE_ENCODING;
                    }
                    if ( sizeInBits <= 16 ) {
                        return ANDI_WORD_ENCODING;
                    }
                    return ANDI_LONG_ENCODING;
                }
                // AND Dn,<ea>
                // AND <ea>,Dn
                insn.setImplicitOperandSize(OperandSize.WORD);
                if ( ! insn.destination().hasAddressingMode(AddressingMode.DATA_REGISTER_DIRECT) ) {
                    patterns = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn, context);
                    return AND_DST_EA_ENCODING.append(patterns);
                }
                patterns = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn, context);
                return AND_SRC_EA_ENCODING.append(patterns);
            case TRAP:
                return TRAP_ENCODING;
            case RTE:
                return RTE_ENCODING;
            case ILLEGAL:
                return ILLEGAL_ENCODING;
            // Scc instructions
            case ST:
            case SF:
            case SHI:
            case SLS:
            case SCC:
            case SCS:
            case SNE:
            case SEQ:
            case SVC:
            case SVS:
            case SPL:
            case SMI:
            case SGE:
            case SLT:
            case SGT:
            case SLE:
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return SCC_ENCODING.append(extraInsnWords);
            // DBcc instructions
            case DBRA:
            case DBT:
            case DBHI:
            case DBLS:
            case DBCC:
            case DBCS:
            case DBNE:
            case DBEQ:
            case DBVC:
            case DBVS:
            case DBPL:
            case DBMI:
            case DBGE:
            case DBLT:
            case DBGT:
            case DBLE:
                return DBCC_ENCODING;
            // Bcc branch instructions
            case BRA:
            case BSR:
            case BHI:
            case BLS:
            case BCC:
            case BCS:
            case BNE:
            case BEQ:
            case BVC:
            case BVS:
            case BPL:
            case BMI:
            case BGE:
            case BLT:
            case BGT:
            case BLE:
                Integer targetAddress = insn.source().getValue().getBits(context);
                if ( targetAddress == null )
                {
                    if ( estimateSizeOnly ) {
                        // unknown destination, assume worst-case scenario
                        if ( context.options().cpuType.supports(  BCC_32BIT_ENCODING ) ) {
                            return BCC_32BIT_ENCODING;
                        }
                        return BCC_16BIT_ENCODING;
                    }
                    throw new RuntimeException("Failed to get displacement from "+insn.source());
                }

                if ( (targetAddress & 1) != 0 ) {
                    throw new RuntimeException("Relative branch needs an even target address but got "+targetAddress);
                }

                final int currentAddress = context.getCodeWriter().offset();
                int relativeOffset = targetAddress - currentAddress -2; // offset is always relative to PC+2
                if ( relativeOffset == 0 ) {
                    /* A branch to the immediately following instruction automatically
                     * uses the 16-bit displacement format because the 8-bit
                     * displacement field contains $00 (zero offset) which indicates
                     * 16-bit displacement format.
                     */
                    return BCC_16BIT_ENCODING;
                }
                int size = NumberNode.getSizeInBitsSigned(relativeOffset);
                if ( size <= 8 )
                {
                    return BCC_8BIT_ENCODING;
                }
                if ( size <= 16 )
                {
                    return BCC_16BIT_ENCODING;
                }
                if ( size <= 32 )
                {
                    if ( ! context.options().cpuType.supports( Instruction.BCC_32BIT_ENCODING) ) {
                        context.error("32-bit relative branch offset only supported on M68020+");
                    }
                    return BCC_32BIT_ENCODING;
                }
                throw new RuntimeException("Internal error - relative branch offset larger than 32 bits?");
            case NOP:
                return NOP_ENCODING;
            case EXG:
                final boolean isData1 = insn.source().getValue().isDataRegister();
                final boolean isData2 = insn.destination().getValue().asRegister().isDataRegister();
                if ( isData1 != isData2 ) {
                    return EXG_DATA_ADR_ENCODING;
                }
                return isData1 ? EXG_DATA_DATA_ENCODING : EXG_ADR_ADR_ENCODING;
            case MOVEQ:
                return MOVEQ_ENCODING;
            case LEA:
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                return LEA_ENCODING.append(extraInsnWords);
            case MOVEA:
            case MOVE:
                // check for MOVE USP
                final int srcIsUSP = insn.source().getValue().isRegister(Register.USP)      ? 0b01 : 0b00;
                final int dstIsUSP = insn.destination().getValue().isRegister(Register.USP) ? 0b10 : 0b00;
                switch( srcIsUSP | dstIsUSP )
                {
                    case 0b00:
                        break;
                    case 0b01:
                        if ( ! insn.destination().getValue().isAddressRegister() )
                        {
                            throw new RuntimeException("MOVE USP,Ax requires  an address register as destination");
                        }
                        if ( ! insn.setImplicitOperandSize( OperandSize.LONG ) &&  !insn.hasOperandSize(OperandSize.LONG) )
                        {
                            throw new RuntimeException("MOVE USP,Ax only works on long-sized operands");
                        }
                        return MOVE_USP_TO_AX_ENCODING;
                    case 0b10:
                        if ( !insn.source().getValue().isAddressRegister())
                        {
                            throw new RuntimeException("MOVE Ax,USP requires an address register as source");
                        }
                        if ( ! insn.setImplicitOperandSize( OperandSize.LONG ) && ! insn.hasOperandSize(OperandSize.LONG) )
                        {
                            throw new RuntimeException("MOVE Ax,USP only works on long-sized operands");
                        }
                        return MOVE_AX_TO_USP_ENCODING;
                    case 0b11:
                        throw new RuntimeException("MOVE USP,USP does not exist");
                }

                insn.setImplicitOperandSize( OperandSize.WORD );

                if ( insn.destination().hasAddressingMode(AddressingMode.ADDRESS_REGISTER_DIRECT ) )
                {
                    if ( insn.hasOperandSize(OperandSize.WORD) )
                    {
                        // MOVEA WORD
                        extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn, context);
                        return MOVEA_WORD_ENCODING.append(extraInsnWords);
                    }
                    if ( insn.hasOperandSize(OperandSize.LONG) )
                    {
                        // MOVEA LONG
                        extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn, context);
                        return MOVEA_LONG_ENCODING.append(extraInsnWords);
                    }
                    throw new RuntimeException("Invalid operation size for MOVEA: "+insn.getOperandSize());
                }

                if ( insn.destination().getValue().isRegister(Register.CCR ) ) {
                    // MOVE to CCR
                    assertOperandSize(insn,OperandSize.WORD);
                    checkSourceAndDestinationNotSameRegister(insn);
                    extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                    return MOVE_TO_CCR_ENCODING.append(extraInsnWords);
                }

                if ( insn.source().getValue().isRegister(Register.SR) ) {
                    // MOVE from SR
                    assertOperandSize(insn,OperandSize.WORD);
                    checkSourceAndDestinationNotSameRegister(insn);
                    extraInsnWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);
                    return MOVE_FROM_SR_ENCODING.append(extraInsnWords);
                }

                if ( insn.destination().getValue().isRegister(Register.SR) ) {
                    // MOVE to SR
                    assertOperandSize(insn,OperandSize.WORD);
                    checkSourceAndDestinationNotSameRegister(insn);
                    extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                    return MOVE_TO_SR_ENCODING.append(extraInsnWords);
                }

                // regular move instruction
                extraInsnWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
                if ( insn.instruction == MOVEA )
                {
                    final InstructionEncoding encoding =
                            insn.getOperandSize() == OperandSize.WORD ? MOVEA_WORD_ENCODING : MOVEA_LONG_ENCODING;
                    return encoding.append( extraInsnWords );
                }
                final String[] extraDstWords = getExtraWordPatterns(insn.destination(), Operand.DESTINATION, insn,context);

                InstructionEncoding encoding;
                switch( insn.getOperandSize() ) {
                    case BYTE:
                        encoding = MOVE_BYTE_ENCODING;
                        break;
                    case WORD:
                        encoding = MOVE_WORD_ENCODING;
                        break;
                    case LONG:
                        encoding = MOVE_LONG_ENCODING;
                        break;
                    default:
                        throw new RuntimeException("MOVE without operand size?");
                }
                if (extraInsnWords != null && extraDstWords == null)
                {
                    return encoding.append(extraInsnWords);
                }
                if (extraInsnWords == null && extraDstWords != null)
                {
                    return encoding.append(extraDstWords);
                }
                if (extraInsnWords != null && extraDstWords != null)
                {
                    return encoding.append(extraInsnWords).append(extraDstWords);
                }
                return encoding;
            default:
                throw new RuntimeException("Internal error,unhandled instruction type " + type);
        }
    }

    protected static void assertOperandSize(InstructionNode insn, OperandSize word) {
        if ( ! insn.hasOperandSize( word ) ) {
            throw new RuntimeException("Unsupported operand size "+insn.getOperandSize()+", only "+word+" is supported");
        }
    }

    // returns any InstructionEncoding patterns
    // needed to accommodate all operand values
    private static String[] getExtraWordPatterns(OperandNode op,
                                                 Operand operandKind,
                                                 InstructionNode insn,
                                                 ICompilationContext ctx)
    {
        if ( op.addressingMode.maxExtensionWords == 0 ) {
            return null;
        }

        // TODO: Handle estimateSizeOnly properly and return the worst-case here...

        switch (op.addressingMode)
        {
            case DATA_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_DIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_POST_INCREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT: return null; // handled
            case ADDRESS_REGISTER_INDIRECT_WITH_DISPLACEMENT:
                Field field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
                return new String[] { StringUtils.repeat(field.c,16) };
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:
                checkOperandSizeSigned( op.getBaseDisplacement(),8,ctx );
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
                return new String[] { operandKind == Operand.SOURCE ? SRC_BRIEF_EXTENSION_WORD : DST_BRIEF_EXTENSION_WORD};
            case ADDRESS_REGISTER_INDIRECT_WITH_INDEX_DISPLACEMENT:
                // TODO: Parser currently sets addressing mode to
                // blabla_INDEX_WITH_INDEX_DISPLACEMENT,
                // I'm currently NOT handling the case with >8bit displacements correctly...
                checkOperandSizeSigned( op.getBaseDisplacement(),8,ctx );
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
                return new String[] { operandKind == Operand.SOURCE ? SRC_BRIEF_EXTENSION_WORD : DST_BRIEF_EXTENSION_WORD};
            case MEMORY_INDIRECT_POSTINDEXED:
                // FIXME: 1-5 extra words
                break;
            case MEMORY_INDIRECT_PREINDEXED:
                // FIXME: 1-5 extra words
                break;
            case PC_INDIRECT_WITH_DISPLACEMENT:
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT: Field.DST_BASE_DISPLACEMENT;
                return new String[] { StringUtils.repeat(field.c,16) };
            case PC_INDIRECT_WITH_INDEX_8_BIT_DISPLACEMENT:

                /*
    DST_REGISTER_KIND('R'), // (bd,br,{Rx},od) D or A
    DST_INDEX_SIZE('Q'), // (bd,br,Rx{.w|.l},od)
    DST_SCALE('E'), // (bd,br,Rx.w{*4},od)
    DST_8_BIT_DISPLACEMENT('W'), // (bd,br,Rx,{od}

BRIEF EXTENSION WORD FORMAT
15 14 13 12 11 10 9 8 7 6 5 4 3 2 1 0
|   +-+--+  |  +--+ 0 +-------------+
D/A   |     |   |           |
    REGISTER|  SCALE      DISPLACEMENT
            W/L
                 */
                checkOperandSizeSigned( op.getBaseDisplacement(),8,ctx );
                field = operandKind == Operand.SOURCE ? Field.SRC_BASE_DISPLACEMENT : Field.DST_BASE_DISPLACEMENT;
                return new String[] { operandKind == Operand.SOURCE ? SRC_BRIEF_EXTENSION_WORD : DST_BRIEF_EXTENSION_WORD};
            case PC_INDIRECT_WITH_INDEX_DISPLACEMENT:
                // FIXME: 1-3 extra words
                break;
            case PC_MEMORY_INDIRECT_POSTINDEXED:
                // FIXME: 1-5 extra words
                break;
            case PC_MEMORY_INDIRECT_PREINDEXED:
                // FIXME: 1-5 extra words
                break;
            case ABSOLUTE_SHORT_ADDRESSING:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,16) };
            case ABSOLUTE_LONG_ADDRESSING:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;
                return new String[] { StringUtils.repeat(field.c,32) };
            case IMMEDIATE_VALUE:
                field = operandKind == Operand.SOURCE ? Field.SRC_VALUE : Field.DST_VALUE;

                // TODO: 8-bit immediate values could actually be stored inline (MOVEQ) instead of wasting a byte here
                // TODO: Maybe add optimization pass that turns regular MOVE into MOVEQ when possible?
                final int words = insn.getOperandSize() == OperandSize.LONG ? 2 : 1;
                return new String[] { StringUtils.repeat(field.c,words*16) };
            case IMPLIED: return null; // handled
        }
        throw new RuntimeException("Unhandled addressing mode: "+op.addressingMode);
    }

    private static InstructionEncoding selectRotateEncoding(InstructionNode insn,
                                                            InstructionEncoding memory,
                                                            InstructionEncoding immediate,
                                                            InstructionEncoding register,
                                                            ICompilationContext context)
    {
        if ( insn.useImpliedOperandSize ) {
            insn.setImplicitOperandSize(OperandSize.WORD);
        }
        if ( insn.operandCount() == 1 ) {
            final String[] extraSrcWords = getExtraWordPatterns(insn.source(), Operand.SOURCE, insn,context);
            return memory.append(extraSrcWords);
        }
        if (insn.source().hasAddressingMode(AddressingMode.IMMEDIATE_VALUE))
        {
            return immediate;
        }
        return register;
    }

    private static void checkOperandSizeUnsigned(IValueNode value, int maxSizeInBits, ICompilationContext ctx)
    {
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return;
        }
        int actualSize = NumberNode.getSizeInBitsUnsigned(nodeValue);
        if ( actualSize > maxSizeInBits ) {
            throw new RuntimeException("Operand out of range, expected at most "+maxSizeInBits+" bits but was "+actualSize);
        }
    }

    private static void checkOperandSizeSigned(IValueNode value, int maxSizeInBits, ICompilationContext ctx)
    {
        if ( value == null ) {
            return;
        }
        Integer nodeValue = value.getBits(ctx);
        if ( nodeValue == null ) {
            return;
        }
        int actualSize = NumberNode.getSizeInBitsSigned(nodeValue);
        if ( actualSize > maxSizeInBits ) {
            throw new RuntimeException("Operand out of range, expected at most "+maxSizeInBits+" bits but was "+actualSize);
        }
    }

    public Condition getCondition()
    {
        return condition;
    }

    /**
     * Whether this instruction supports explicit .b/.w/.l suffixes or not.
     *
     * Defaults to <code>false</code>
     * @return
     */
    public boolean supportsExplicitOperandSize() {
        return false;
    }

    private static void checkSccInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        checkSourceAddressingModeKind(node,AddressingModeKind.ALTERABLE);

        if ( ! node.setImplicitOperandSize(OperandSize.BYTE) && ! node.hasOperandSize(OperandSize.BYTE) )
        {
            throw new RuntimeException("Invalid operand size: "+ node.getOperandSize());
        }
    }

    private static void checkDBccInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        if ( node.source().addressingMode != AddressingMode.DATA_REGISTER_DIRECT ) {
            throw new RuntimeException("Unsupported addressing mode: "+node.source().addressingMode );
        }
        switch( node.destination().addressingMode )
        {
            case ABSOLUTE_LONG_ADDRESSING:
            case ABSOLUTE_SHORT_ADDRESSING:
                break;
            default:
                throw new RuntimeException("Unsupported addressing mode: "+node.destination().addressingMode );
        }
        checkOperandSizeSigned(node.destination().getValue(), 16,ctx);
    }

    private static void checkRotateInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        if ( node.operandCount() == 1 ) {
            // 1 operand => memory location

            checkSourceAddressingMode(node,AddressingMode.ABSOLUTE_SHORT_ADDRESSING,AddressingMode.ABSOLUTE_LONG_ADDRESSING);

            if ( node.hasExplicitOperandSize() && ! node.hasOperandSize( OperandSize.WORD ) ) {
                throw new RuntimeException(node.instruction+" can only operate on WORDs in memory");
            }
            return;
        }
        // 2 operands, register/immediate
        if (node.source().getValue().isDataRegister())
        {
            // register,register
            Instruction.checkDestinationAddressingModeKind(node, AddressingModeKind.DATA);
            return;
        }
        if (node.source().hasAddressingMode( IMMEDIATE_VALUE ) )
        {
            final Integer value = node.source().getValue().getBits( ctx );
            if ( value != null && ( value < 1 || value > 8 ) ) {
                throw new RuntimeException( node.instruction+" only supports rotating 1..8 times");
            }
            checkDestinationAddressingMode(node, AddressingMode.DATA_REGISTER_DIRECT);
            return;
        }
        throw new RuntimeException("Operands have unsupported addressing modes for " + node.instruction);
    }

    private static void checkMultiplyDivide(InstructionNode node) {
        checkSourceAddressingModeKind(node,AddressingModeKind.DATA);
        if ( node.hasExplicitOperandSize() && ! node.hasOperandSize(OperandSize.WORD ) ) {
            throw new RuntimeException(node.instruction.getMnemonic()+"on 68000 only supports word operands");
        }
        if ( ! node.destination().getValue().isDataRegister() ) {
            throw new RuntimeException(node.instruction.getMnemonic()+" needs a data register as destination");
        }
    }

    private static void checkBinaryLogicalOperation(InstructionNode insn,boolean estimateSizeOnly, ICompilationContext ctx)
    {
        checkDestinationIsNotAddressRegisterDirect(insn,ctx);
        checkSourceIsNotAddressRegisterDirect(insn,ctx);

        if ( insn.source().hasAddressingMode( IMMEDIATE_VALUE ) )
        {
            int allowedOperandSizeInBits = 16;

            if (insn.destination().getValue().isRegister(Register.SR))
            {
                // ANDI #xx,SR
                if ( ! insn.useImpliedOperandSize && ! insn.hasOperandSize(OperandSize.WORD ) )
                {
                    throw new RuntimeException( insn.instruction+" only supports word-sized operand");
                }
                allowedOperandSizeInBits = 16;
            }
            else if (insn.destination().getValue().isRegister(Register.CCR))
            {
                // ANDI #xx,CCR
                if ( ! insn.useImpliedOperandSize && ! insn.hasOperandSize(OperandSize.BYTE ) )
                {
                    throw new RuntimeException( insn.instruction+" only supports byte-sized operand");
                }
                allowedOperandSizeInBits = 8;
            }
            else
            {
                if ( insn.hasExplicitOperandSize() ) {
                    switch(insn.getOperandSize()) {

                        case BYTE:
                            allowedOperandSizeInBits = 8;
                            break;
                        case WORD:
                            allowedOperandSizeInBits = 16;
                            break;
                        case LONG:
                            allowedOperandSizeInBits = 32;
                            break;
                        default:
                            throw new RuntimeException("Unreachable code reached");
                    }
                }
            }

            if ( ! estimateSizeOnly )
            {
                final int bits = insn.source().getValue().getBits(ctx);
                if ( NumberNode.getSizeInBitsUnsigned(bits) > allowedOperandSizeInBits ) {
                    throw new RuntimeException( insn.instruction+" needs a "+allowedOperandSizeInBits+"-" +
                            "bit operand, was: "+Misc.hex(bits));
                }
            }
            return;
        }
        // no immediate mode src operand
        final boolean srcIsDataReg =
                insn.source().getValue().isDataRegister() &&
                        insn.source().hasAddressingMode(DATA_REGISTER_DIRECT);
        final boolean dstIsDataReg =
                insn.destination().getValue().isDataRegister() &&
                        insn.destination().hasAddressingMode(DATA_REGISTER_DIRECT);

        if ( ! (srcIsDataReg | dstIsDataReg ) ) {
            throw new RuntimeException(insn.instruction+" needs a data register as either source or destination operand");
        }
    }

    private static void checkBitInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        Instruction.checkDestinationAddressingModeKind( node,AddressingModeKind.DATA,AddressingModeKind.ALTERABLE );
        if ( node.source().hasAddressingMode( AddressingMode.IMMEDIATE_VALUE ) ) {
            final Integer bitNum = node.source().getValue().getBits( ctx );
            if ( node.destination().addressingMode.hasKind( AddressingModeKind.MEMORY ) ) {
                if ( bitNum != null && (bitNum < 0 || bitNum > 7) ) {
                    throw new RuntimeException( node.instruction+" with memory locations can only operate on bits 0..7");
                }
            } else {
                if ( bitNum != null && (bitNum < 0 || bitNum > 31) ) {
                    throw new RuntimeException( node.instruction+" can only operate on bits 0..31");
                }
            }
        }
        else if ( ! node.source().getValue().isDataRegister() )
        {
            throw new RuntimeException( "Unsupported source addressing mode for "+node.instruction+", only immediate or data register are allowed" );
        }
    }

    private static void checkBranchInstructionValid(InstructionNode node,ICompilationContext ctx)
    {
        switch (node.source().addressingMode)
        {
            case ABSOLUTE_LONG_ADDRESSING:
            case ABSOLUTE_SHORT_ADDRESSING:
                break;
            default:
                throw new RuntimeException("Unsupported addressing mode: " + node.source().addressingMode);
        }
        checkOperandSizeSigned(node.source().getValue(), 32, ctx);
    }

    private int getValueFor(InstructionNode insn,Field field, ICompilationContext ctx)
    {
        switch(field)
        {
            case SRC_REGISTER_KIND:
                return insn.source().getIndexRegister().isDataRegister() ? 0 : 1;
            case SRC_INDEX_SIZE:
                return getIndexRegisterSizeBit( insn.source().getIndexRegister() );
            case SRC_SCALE:
                Scaling scaling = insn.source().getIndexRegister().scaling;
                return scaling == null ? Scaling.IDENTITY.bits : scaling.bits;
            case SRC_8_BIT_DISPLACEMENT:
                IValueNode baseDisplacement = insn.source().getBaseDisplacement();
                return baseDisplacement == null ? 0 : baseDisplacement.getBits(ctx);
            case DST_REGISTER_KIND:
                return insn.destination().getIndexRegister().isDataRegister() ? 0 : 1;
            case DST_INDEX_SIZE:
                return getIndexRegisterSizeBit( insn.destination().getIndexRegister() );
            case DST_SCALE:
                scaling = insn.destination().getIndexRegister().scaling;
                return scaling == null ? Scaling.IDENTITY.bits : scaling.bits;
            case DST_8_BIT_DISPLACEMENT:
                baseDisplacement = insn.destination().getBaseDisplacement();
                return baseDisplacement == null ? 0 : baseDisplacement.getBits(ctx);
            case OP_CODE:
                return insn.getInstructionType().getOperationCode(insn);
            case SRC_VALUE:
                return insn.source().getValue().getBits(ctx);
            case SRC_BASE_REGISTER:
                if ( insn.source().addressingMode.eaRegisterField.isFixedValue() ) {
                    return insn.source().addressingMode.eaRegisterField.value();
                }
                return insn.source().getValue().asRegister().getBits(ctx);
            case SRC_INDEX_REGISTER:
                return insn.source().getIndexRegister().getBits(ctx);
            case SRC_BASE_DISPLACEMENT:
                return insn.source().getBaseDisplacement().getBits(ctx);
            case SRC_OUTER_DISPLACEMENT:
                return insn.source().getOuterDisplacement().getBits(ctx);
            case SRC_MODE:
                return insn.source().addressingMode.eaModeField;
            case DST_VALUE:
                return insn.destination().getValue().getBits(ctx);
            case DST_BASE_REGISTER:
                if ( insn.destination().addressingMode.eaRegisterField.isFixedValue() ) {
                    return insn.destination().addressingMode.eaRegisterField.value();
                }
                return insn.destination().getValue().asRegister().getBits(ctx);
            case DST_INDEX_REGISTER:
                return insn.destination().getIndexRegister().getBits(ctx);
            case DST_BASE_DISPLACEMENT:
                return insn.destination().getBaseDisplacement().getBits(ctx);
            case DST_OUTER_DISPLACEMENT:
                return insn.destination().getOuterDisplacement().getBits(ctx);
            case DST_MODE:
                return insn.destination().addressingMode.eaModeField;
            case SIZE:
                if ( insn.getOperandSize() == OperandSize.UNSPECIFIED ) {
                    throw new RuntimeException("Operand size not specified");
                }
                if ( insn.instruction == MOVEA ) {
                    // MOVEA uses non-standard encoding....
                    switch(insn.getOperandSize()) {
                        case WORD: return 0b11;
                        case LONG: return 0b10;
                    }
                    throw new RuntimeException("Unhandled operand size for MOVEA: "+insn.getOperandSize());
                }
                return insn.getOperandSize().bits;
            case EXG_DATA_REGISTER:
            case EXG_ADDRESS_REGISTER:
                final Register srcReg = insn.source().getValue().asRegister().register;
                final Register dstReg = insn.destination().getValue().asRegister().register;
                // data register if EXG used with registers of different types, otherwise either the src data or src address register
                if ( field == Field.EXG_DATA_REGISTER )
                {
                    if ( srcReg.isData() != dstReg.isData() )
                    {
                        return srcReg.isData() ? srcReg.bits : dstReg.bits;
                    }
                    return srcReg.bits;
                }
                // field == Field.EXG_ADDRESS_REGISTER;
                // address register if EXG used with registers of different types, otherwise either the dst data or dst address register
                if ( srcReg.isAddress() != dstReg.isAddress() )
                {
                    return dstReg.isAddress() ? dstReg.bits : srcReg.bits;
                }
                return dstReg.bits;
            case NONE:
                return 0;
            case CONDITION_CODE: // encoded branch condition,stored as operationMode on Instruction
                return insn.getInstructionType().getOperationMode();
            case REGISTER_LIST_BITMASK:
                if ( isRegisterList(insn.source() ) )
                {
                    // MOVEM <register list>,<ea>
                    final AddressingMode mode = insn.destination().addressingMode;
                    final int bits = getMoveMRegisterBitmask( insn.source().getValue(), ctx );
                    return mode == ADDRESS_REGISTER_INDIRECT_PRE_DECREMENT ? Misc.reverseWord(bits) : bits;
                }
                // MOVEM <ea>,<register list>
                return getMoveMRegisterBitmask( insn.destination().getValue(), ctx);
            default:
                throw new RuntimeException("Internal error,unhandled field "+field);
        }
    }

    private static int getMoveMRegisterBitmask(IValueNode node,ICompilationContext ctx)
    {
        int bits = node.getBits(ctx );
        if ( node.is(NodeType.REGISTER) )
        {
            // turn register number into bit mask
            return node.isDataRegister() ? 1<<bits: 1 << (8+bits);
        }
        return bits;
    }

    public static int getIndexRegisterSizeBit(RegisterNode register)
    {
        OperandSize size = register.operandSize;
        if ( size == null )  {
            size = OperandSize.WORD;
        }
        switch(size) {
            case WORD:
                return 0;
            case LONG:
                return 1;
        }
        throw new RuntimeException("Invalid index register operand size "+size);
    }

    private static void checkDestinationIsNotAddressRegisterDirect(InstructionNode insn, ICompilationContext ctx)
    {
        if ( insn.hasDestination() && insn.destination().hasAddressingMode(ADDRESS_REGISTER_DIRECT) )
        {
            if ( insn.destination().getValue().isAddressRegister() ) {
                throw new RuntimeException(insn.instruction.getMnemonic()+" does not support address registers direct as destination operand");
            }
        }
    }

    private static void checkSourceIsNotAddressRegisterDirect(InstructionNode insn, ICompilationContext ctx)
    {
        if ( insn.hasSource() && insn.source().hasAddressingMode(ADDRESS_REGISTER_DIRECT) )
        {
            if ( insn.source().getValue().isAddressRegister() ) {
                throw new RuntimeException(insn.instruction+" does not support address registers direct as source operand");
            }
        }
    }

    private static void checkSourceAddressingMode(InstructionNode insn, AddressingMode mode1,AddressingMode...additional) {
        final Set<AddressingMode> set = toSet(mode1, additional);
        checkSourceAddressingMode(insn, set::contains );
    }

    private static void checkSourceAddressingMode(InstructionNode insn, Predicate<AddressingMode> pred)
    {
        if ( insn.operandCount() < 1 ) {
            throw new RuntimeException( insn+" lacks source operand");
        }
        if ( ! pred.test( insn.source().addressingMode) )
        {
            final List<AddressingMode> modes = Stream.of(AddressingMode.values()).filter(pred).collect(Collectors.toList());
            throw new RuntimeException("Unsupported addressing mode in source operand, " +
                "instruction "+insn.instruction+" only supports "+modes);
        }
    }

    private static void checkDestinationAddressingMode(InstructionNode insn, AddressingMode mode1,AddressingMode...additional) {
        final Set<AddressingMode> set = toSet(mode1, additional);
        checkDestinationAddressingMode(insn, set::contains );
    }

    private static void checkDestinationAddressingMode(InstructionNode insn, Predicate<AddressingMode> pred)
    {
        if ( insn.operandCount() < 2 ) {
            throw new RuntimeException( insn+" lacks destination operand");
        }
        if ( ! pred.test( insn.destination().addressingMode) )
        {
            final List<AddressingMode> modes = Stream.of(AddressingMode.values()).filter(pred).collect(Collectors.toList());
            throw new RuntimeException("Unsupported addressing mode in destination operand, " +
                "instruction "+insn.instruction+" only supports "+modes);
        }
    }

    private static void checkSourceAddressingModeKind(InstructionNode insn, AddressingModeKind kind1,AddressingModeKind...additional)
    {
        final Set<AddressingModeKind> kinds = toSet(kind1, additional);
        if( ! insn.source().addressingMode.hasKinds( kinds ) )
        {
            throw new RuntimeException("Instruction "+insn.instruction+" only supports addressing modes of kind "+kinds+" but was "+insn.source().addressingMode);
        }
    }

    private static void checkSourceAndDestinationNotSameRegister(InstructionNode node) {

        Register src=node.source().getValue().isRegister() ? node.source().getValue().asRegister().register : null;
        Register dst=node.destination().getValue().isRegister() ? node.destination().getValue().asRegister().register : null;
        if ( src != null && src == dst ) {
            throw new RuntimeException("Source and destination register must be different");
        }
    }

    private static void checkDestinationAddressingModeKind(InstructionNode insn, AddressingModeKind kind1,AddressingModeKind... additional)
    {
        final Set<AddressingModeKind> kinds = toSet(kind1, additional);
        if( ! insn.destination().addressingMode.hasKinds( kinds ) )
        {
            throw new RuntimeException("Instruction "+insn.instruction+" only supports addressing modes of kind "+kinds+" but was "+insn.destination().addressingMode);
        }
    }

    private static boolean isRegisterList(OperandNode operand)
    {
        final IValueNode value = operand.getValue();
        return (value.is(NodeType.REGISTER) && operand.addressingMode.isRegisterDirect() ) ||
                value.is(NodeType.REGISTER_RANGE) ||
                value.is(NodeType.REGISTER_LIST);
    }

    private static InstructionEncoding.IValueDecorator fieldDecorator(Field f, Function<Integer, Integer> func)
    {
        return (field, inputValue) ->
        {
            if ( field == f ) {
                return func.apply( inputValue );
            }
            return inputValue;
        };
    }

    public static final String SRC_BRIEF_EXTENSION_WORD = "riiiqee0wwwwwwww";
    public static final String DST_BRIEF_EXTENSION_WORD = "RIIIQEE0WWWWWWWW";

    // src eaMode/eaRegister contained in lower 6 bits
    public static final InstructionEncoding AND_SRC_EA_ENCODING  =
        InstructionEncoding.of("1100DDD0SSmmmsss").withName("AND_SRC_EA_ENCODING");
    // dst eaMode/eaRegister contained in lower 6 bits
    public static final InstructionEncoding AND_DST_EA_ENCODING  =
        InstructionEncoding.of("1100sss1SSMMMDDD").withName("AND_DST_EA_ENCODING");

    public static final InstructionEncoding ANDI_TO_CCR_ENCODING =
        InstructionEncoding.of("0000001000111100","00000000vvvvvvvv").withName("ANDI_TO_CCR_ENCODING");

    public static final InstructionEncoding ANDI_BYTE_ENCODING   =
        InstructionEncoding.of("0000001000MMMDDD","00000000_vvvvvvvv").withName("ANDI_BYTE_ENCODING");
    public static final InstructionEncoding ANDI_WORD_ENCODING   =
        InstructionEncoding.of("0000001001MMMDDD","vvvvvvvv_vvvvvvvv").withName("ANDI_WORD_ENCODING");
    public static final InstructionEncoding ANDI_LONG_ENCODING   =
        InstructionEncoding.of("0000001010MMMDDD", "vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv").withName("ANDI_LONG_ENCODING");
    public static final InstructionEncoding ANDI_TO_SR_ENCODING  =
        InstructionEncoding.of("0000001001111100","vvvvvvvv_vvvvvvvv").withName("ANDI_TO_SR_ENCODING");

    // src eaMode/eaRegister contained in lower 6 bits
    public static final InstructionEncoding OR_SRC_EA_ENCODING  = InstructionEncoding.of("1000DDD0SSmmmsss");
    // dst eaMode/eaRegister contained in lower 6 bits
    public static final InstructionEncoding OR_DST_EA_ENCODING  = InstructionEncoding.of("1000sss1SSMMMDDD");

    public static final InstructionEncoding ORI_TO_CCR_ENCODING  = InstructionEncoding.of("0000000000111100",
            "00000000_vvvvvvvv");

    public static final InstructionEncoding ORI_TO_SR_ENCODING  = InstructionEncoding.of("0000000001111100",
            "vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding ORI_WORD_ENCODING = InstructionEncoding.of("00000000SSMMMDDD","vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding ORI_LONG_ENCODING = InstructionEncoding.of("00000000SSMMMDDD","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    // dst eaMode/eaRegister contained in lower 6 bits
    public static final InstructionEncoding EOR_DST_EA_ENCODING  = InstructionEncoding.of("1011sss1SSMMMDDD");

    public static final InstructionEncoding EORI_TO_CCR_ENCODING = InstructionEncoding.of("0000101000111100",
            "00000000_vvvvvvvv");

    public static final InstructionEncoding EORI_TO_SR_ENCODING = InstructionEncoding.of("0000101001111100",
            "vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding EORI_WORD_ENCODING = InstructionEncoding.of("00001010SSMMMDDD","vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding EORI_LONG_ENCODING = InstructionEncoding.of("00001010SSMMMDDD","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    private static final InstructionEncoding.IValueDecorator MOVEM_SIZE_DECORATOR = (field, origValue) ->
    {
        // MOVEM uses a non-standard one-bit size encoding
        if (field == Field.SIZE)
        {
            switch (origValue)
            {
                case 0b01: return 0; // word transfer
                case 0b10: return 1; // long transfer
            }
            throw new RuntimeException("Unreachable code reached");
        }
        return origValue;
    };

    public static final InstructionEncoding MOVEM_FROM_REGISTERS_ENCODING =
            InstructionEncoding.of("010010001SMMMDDD","LLLLLLLL_LLLLLLLL")
                    .decorateWith(MOVEM_SIZE_DECORATOR);

    public static final InstructionEncoding MOVEM_TO_REGISTERS_ENCODING   =
            InstructionEncoding.of("010011001Smmmsss","LLLLLLLL_LLLLLLLL")
                    .decorateWith(MOVEM_SIZE_DECORATOR);

    public static final InstructionEncoding TRAP_ENCODING = InstructionEncoding.of("010011100100vvvv");

    public static final InstructionEncoding RTE_ENCODING = InstructionEncoding.of( "0100111001110011");

    public static final InstructionEncoding JMP_INDIRECT_ENCODING = InstructionEncoding.of( "0100111011mmmsss");
    public static final InstructionEncoding JMP_SHORT_ENCODING =    InstructionEncoding.of( "0100111011mmmsss","vvvvvvvv_vvvvvvvv");
    public static final InstructionEncoding JMP_LONG_ENCODING =     InstructionEncoding.of( "0100111011mmmsss","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding MOVEP_WORD_FROM_MEMORY_ENCODING = // MOVEP.W d16(An),Dx
            InstructionEncoding.of("0000DDD100001sss", "bbbbbbbb_bbbbbbbb");
    public static final InstructionEncoding MOVEP_LONG_FROM_MEMORY_ENCODING = // // MOVEP.L d16(An),Dx
            InstructionEncoding.of("0000DDD101001sss","bbbbbbbb_bbbbbbbb");
    public static final InstructionEncoding MOVEP_WORD_TO_MEMORY_ENCODING   = // MOVEP.W Dx,d16(An)
            InstructionEncoding.of("0000sss110001DDD","BBBBBBBB_BBBBBBBB");
    public static final InstructionEncoding MOVEP_LONG_TO_MEMORY_ENCODING   = // MOVEP.L Dx,d16(An)
            InstructionEncoding.of("0000sss111001DDD","BBBBBBBB_BBBBBBBB");

    public static final InstructionEncoding MOVE_BYTE_ENCODING = InstructionEncoding.of("0001DDDMMMmmmsss");
    public static final InstructionEncoding MOVE_WORD_ENCODING = InstructionEncoding.of("0011DDDMMMmmmsss");
                                                                                      // 0010000001111100
    public static final InstructionEncoding MOVE_LONG_ENCODING = InstructionEncoding.of("0010DDDMMMmmmsss");
    public static final InstructionEncoding MOVE_TO_CCR_ENCODING = InstructionEncoding.of("0100010011mmmsss");
    public static final InstructionEncoding MOVE_AX_TO_USP_ENCODING = InstructionEncoding.of("0100111001100sss");
    public static final InstructionEncoding MOVE_USP_TO_AX_ENCODING = InstructionEncoding.of("0100111001101DDD");
    public static final InstructionEncoding MOVE_FROM_SR_ENCODING = InstructionEncoding.of(  "0100000011MMMDDD");
    public static final InstructionEncoding MOVE_TO_SR_ENCODING   = InstructionEncoding.of(  "0100011011mmmsss");

    public static final InstructionEncoding MOVEQ_ENCODING = InstructionEncoding.of("0111DDD0vvvvvvvv");

    public static final InstructionEncoding LEA_ENCODING = InstructionEncoding.of("0100DDD111mmmsss");

    public static final InstructionEncoding ILLEGAL_ENCODING = InstructionEncoding.of( "0100101011111100");

    public static final InstructionEncoding EXG_DATA_DATA_ENCODING = InstructionEncoding.of("1100kkk101000lll");
    public static final InstructionEncoding EXG_ADR_ADR_ENCODING   = InstructionEncoding.of("1100kkk101001lll");
    public static final InstructionEncoding EXG_DATA_ADR_ENCODING  = InstructionEncoding.of("1100kkk110001lll");

    public static final InstructionEncoding NOP_ENCODING = InstructionEncoding.of("0100111001110001");

    public static final InstructionEncoding SCC_ENCODING = InstructionEncoding.of( "0101cccc11mmmsss");

    public static final InstructionEncoding DBCC_ENCODING = InstructionEncoding.of( "0101cccc11001sss",
            "CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding BCC_8BIT_ENCODING = InstructionEncoding.of(  "0110ccccCCCCCCCC");
    public static final InstructionEncoding BCC_16BIT_ENCODING = InstructionEncoding.of( "0110cccc00000000","CCCCCCCC_CCCCCCCC");
    public static final InstructionEncoding BCC_32BIT_ENCODING = InstructionEncoding.of( "0110cccc11111111", "CCCCCCCC_CCCCCCCC_CCCCCCCC_CCCCCCCC");

    public static final InstructionEncoding MOVEA_WORD_ENCODING = InstructionEncoding.of(  "0011DDD001mmmsss");
    public static final InstructionEncoding MOVEA_LONG_ENCODING = InstructionEncoding.of(  "0010DDD001mmmsss");

    public static final InstructionEncoding SWAP_ENCODING = InstructionEncoding.of(  "0100100001000sss");

    public static final InstructionEncoding JSR_ENCODING = InstructionEncoding.of( "0100111010mmmsss");

    public static final InstructionEncoding RTS_ENCODING = InstructionEncoding.of( "0100111001110101");

    // TODO: 68020+ supports LONG displacement value as well
    public static final InstructionEncoding LINK_ENCODING = InstructionEncoding.of( "0100111001010sss",
            "VVVVVVVV_VVVVVVVV");
    public static final InstructionEncoding UNLK_ENCODING = InstructionEncoding.of( "0100111001011sss");

    public static final InstructionEncoding RESET_ENCODING = InstructionEncoding.of( "0100111001110000");

    public static final InstructionEncoding RTR_ENCODING = InstructionEncoding.of( "0100111001110111");

    public static final InstructionEncoding PEA_ENCODING = InstructionEncoding.of( "0100100001mmmsss");

    public static final InstructionEncoding ROXL_IMMEDIATE_ENCODING= InstructionEncoding.of( "1110vvv1SS010DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ));
    public static final InstructionEncoding ROL_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv1SS011DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ));
    public static final InstructionEncoding LSL_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv1SS001DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ));
    public static final InstructionEncoding ASL_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv1SS000DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ));

    public static final InstructionEncoding ROXR_IMMEDIATE_ENCODING= InstructionEncoding.of( "1110vvv0SS010DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ) );
    public static final InstructionEncoding ROR_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv0SS011DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ) );
    public static final InstructionEncoding LSR_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv0SS001DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ) );
    public static final InstructionEncoding ASR_IMMEDIATE_ENCODING = InstructionEncoding.of( "1110vvv0SS000DDD").decorateWith(fieldDecorator(Field.SRC_VALUE , x -> x == 8 ? 0 :x ) );

    public static final InstructionEncoding ROXL_MEMORY_ENCODING= InstructionEncoding.of(    "1110010111mmmsss");
    public static final InstructionEncoding ROL_MEMORY_ENCODING = InstructionEncoding.of(    "1110011111mmmsss");
    public static final InstructionEncoding LSL_MEMORY_ENCODING = InstructionEncoding.of(    "1110001111mmmsss");
    public static final InstructionEncoding ASL_MEMORY_ENCODING = InstructionEncoding.of(    "1110000111mmmsss");

    public static final InstructionEncoding ROXR_MEMORY_ENCODING= InstructionEncoding.of(    "1110010011mmmsss");
    public static final InstructionEncoding ROR_MEMORY_ENCODING = InstructionEncoding.of(    "1110011011mmmsss");
    public static final InstructionEncoding LSR_MEMORY_ENCODING = InstructionEncoding.of(    "1110001011mmmsss");
    public static final InstructionEncoding ASR_MEMORY_ENCODING = InstructionEncoding.of(    "1110000011mmmsss");

    public static final InstructionEncoding ROXL_REGISTER_ENCODING= InstructionEncoding.of(  "1110sss1SS110VVV");
    public static final InstructionEncoding ROL_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss1SS111VVV");
    public static final InstructionEncoding LSL_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss1SS101VVV");
    public static final InstructionEncoding ASL_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss1SS100VVV");

    public static final InstructionEncoding ROXR_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss0SS110VVV");
    public static final InstructionEncoding ROR_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss0SS111VVV");
    public static final InstructionEncoding LSR_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss0SS101VVV");
    public static final InstructionEncoding ASR_REGISTER_ENCODING = InstructionEncoding.of(  "1110sss0SS100VVV");

    public static final InstructionEncoding NEG_ENCODING = InstructionEncoding.of( "01000100SSmmmsss");

    public static final InstructionEncoding EXTW_ENCODING =
            InstructionEncoding.of( "0100100010000sss"); // Byte -> Word

    public static final InstructionEncoding EXTL_ENCODING =
            InstructionEncoding.of( "0100100011000sss"); // Word -> Long

    public static final InstructionEncoding BTST_DYNAMIC_ENCODING = // BTST Dn,<ea>
            InstructionEncoding.of( "0000sss100MMMDDD");

    public static final InstructionEncoding BTST_STATIC_ENCODING = // BTST #xx,<ea>
            InstructionEncoding.of( "0000100000MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding BCLR_DYNAMIC_ENCODING = // BCLR Dn,<ea>
            InstructionEncoding.of( "0000sss110MMMDDD");

    public static final InstructionEncoding BCLR_STATIC_ENCODING = // BCLR #xx,<ea>
            InstructionEncoding.of( "0000100010MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding BSET_DYNAMIC_ENCODING = // BSET Dn,<ea>
            InstructionEncoding.of( "0000sss111MMMDDD");

    public static final InstructionEncoding BSET_STATIC_ENCODING = // BSET #xx,<ea>
            InstructionEncoding.of( "0000100011MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding BCHG_DYNAMIC_ENCODING = // BCHG Dn,<ea>
            InstructionEncoding.of( "0000sss101MMMDDD");

    public static final InstructionEncoding BCHG_STATIC_ENCODING = // BCHG #xx,<ea>
            InstructionEncoding.of( "0000100001MMMDDD", "00000000vvvvvvvv");

    public static final InstructionEncoding CLR_ENCODING = // CLR <ea>
            InstructionEncoding.of( "01000010SSmmmsss");

    public static final InstructionEncoding TST_ENCODING = // TST.s <ea>
            InstructionEncoding.of( "01001010SSmmmsss");

    public static final InstructionEncoding TRAPV_ENCODING = // TRAPV
            InstructionEncoding.of( "0100111001110110");

    public static final InstructionEncoding STOP_ENCODING = // STOP
            InstructionEncoding.of( "0100111001110010", "vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding NOT_ENCODING = // NOT
            InstructionEncoding.of( "01000110SSmmmsss");

    public static final InstructionEncoding NEGX_ENCODING = // NEGX <ea>
            InstructionEncoding.of( "01000000SSmmmsss");

    // comparisons

    public static final InstructionEncoding CMPA_WORD_ENCODING = // CMPA.W <ea>,An
        InstructionEncoding.of( "1011DDD011mmmsss");

    public static final InstructionEncoding CMPA_LONG_ENCODING = // CMPA.L <ea>,An
        InstructionEncoding.of( "1011DDD111mmmsss");

    public static final InstructionEncoding CMPI_WORD_ENCODING = // CMPI.w #xx,<ea>
            InstructionEncoding.of( "00001100SSMMMDDD","vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding CMPI_LONG_ENCODING = // CMPI.l #xx,<ea>
            InstructionEncoding.of( "00001100SSMMMDDD","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding CMPM_ENCODING = // CMPM (Ay)+,(Ax)+
            InstructionEncoding.of( "1011DDD1SS001sss");

    public static final InstructionEncoding CMP_ENCODING = // CMP.W <ea>,Dn
            InstructionEncoding.of( "1011DDD0SSmmmsss");

    // subtractions

    public static final InstructionEncoding SUBX_DATA_REG_ENCODING = //  SUBX Dx,Dy
            InstructionEncoding.of( "1001DDD1SS000sss");

    public static final InstructionEncoding SUBX_ADDR_REG_ENCODING = //  SUBX -(Ax),-(Ay)
            InstructionEncoding.of( "1001DDD1SS001sss");

    public static final InstructionEncoding SUBA_WORD_ENCODING = // SUB.W <ea>,An
            InstructionEncoding.of( "1001DDD011mmmsss");

    public static final InstructionEncoding SUBA_LONG_ENCODING = // SUB.L <ea>,An
            InstructionEncoding.of( "1001DDD111mmmsss");

    public static final InstructionEncoding SUBQ_ENCODING = // SUBQ #xx,<ea>
            InstructionEncoding.of( "0101vvv1SSMMMDDD");

    public static final InstructionEncoding SUBI_WORD_ENCODING = // SUBI.w #xx,<ea>
            InstructionEncoding.of( "00000100SSMMMDDD","vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding SUBI_LONG_ENCODING = // SUBI.l #xx,<ea>
            InstructionEncoding.of( "00000100SSMMMDDD","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding SUB_DST_DATA_ENCODING = // SUB <ea>,Dn
            InstructionEncoding.of( "1001DDD0SSmmmsss");

    public static final InstructionEncoding SUB_DST_EA_ENCODING = // // SUB Dn,<ea>
            InstructionEncoding.of( "1001sss1SSMMMDDD");

    // additions

    public static final InstructionEncoding ADD_DST_DATA_ENCODING = // ADD <ea>,Dn
            InstructionEncoding.of( "1101DDD0SSmmmsss");

    public static final InstructionEncoding ADD_DST_EA_ENCODING = // // ADD Dn,<ea>
            InstructionEncoding.of( "1101sss1SSMMMDDD");

    public static final InstructionEncoding ADDX_DATAREG_ENCODING = // ADDX Dx,Dy
            InstructionEncoding.of( "1101DDD1SS000sss");

    public static final InstructionEncoding ADDX_ADDRREG_ENCODING = // ADDX -(Ax),-(Ay)
            InstructionEncoding.of( "1101DDD1SS001sss");

    public static final InstructionEncoding ADDQ_ENCODING = // ADDQ #xx,<ea>
            InstructionEncoding.of( "0101vvv0SSMMMDDD");

    public static final InstructionEncoding ADDI_WORD_ENCODING = // ADDI.w #xx,<ea>
            InstructionEncoding.of( "00000110SSMMMDDD","vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding ADDI_LONG_ENCODING = // ADDI.l #xx,<ea>
            InstructionEncoding.of( "00000110SSMMMDDD","vvvvvvvv_vvvvvvvv_vvvvvvvv_vvvvvvvv");

    public static final InstructionEncoding ADDA_WORD_ENCODING = // ADDA.w <ea>,An
            InstructionEncoding.of( "1101DDD011mmmsss");

    public static final InstructionEncoding ADDA_LONG_ENCODING = // ADDA.l <ea>,An
            InstructionEncoding.of( "1101DDD111mmmsss");

    // divisions

    public static final InstructionEncoding DIVU_ENCODING = // MULS <ea>,Dn
            InstructionEncoding.of( "1000DDD011mmmsss");

    public static final InstructionEncoding DIVS_ENCODING = // MULS <ea>,Dn
            InstructionEncoding.of( "1000DDD111mmmsss");

    public static final InstructionEncoding MULS_ENCODING = // MULS <ea>,Dn
            InstructionEncoding.of( "1100DDD111mmmsss");

    public static final InstructionEncoding MULU_ENCODING = // MULU <ea>,Dn
            InstructionEncoding.of( "1100DDD011mmmsss");

    public static final InstructionEncoding CHK_WORD_ENCODING = // CHK <ea>,Dn
            InstructionEncoding.of( "0100DDD110mmmsss");

    public static final InstructionEncoding CHK_LONG_ENCODING = // CHK <ea>,Dn
            InstructionEncoding.of( "0100DDD100mmmsss");

    public static final InstructionEncoding TAS_ENCODING = // TAS
            InstructionEncoding.of( "0100101011mmmsss");

    public static final IdentityHashMap<InstructionEncoding,Instruction> ALL_ENCODINGS = new IdentityHashMap<>()
    {{
        put(JMP_INDIRECT_ENCODING,JMP);
        put(JMP_SHORT_ENCODING,JMP);
        put(JMP_LONG_ENCODING,JMP);
        put(TRAP_ENCODING,TRAP);
        put(RTE_ENCODING,RTE);

        put(MOVE_BYTE_ENCODING,MOVE);
        put(MOVE_WORD_ENCODING,MOVE);
        put(MOVE_LONG_ENCODING,MOVE);
        put(MOVEQ_ENCODING,MOVEQ);
        put(MOVE_AX_TO_USP_ENCODING,MOVE);
        put(MOVE_USP_TO_AX_ENCODING,MOVE);
        put(MOVEA_LONG_ENCODING,MOVEA);
        put(MOVEA_WORD_ENCODING,MOVEA);
        put(MOVE_TO_CCR_ENCODING,MOVE);
        put(MOVE_FROM_SR_ENCODING,MOVE);
        put(MOVE_TO_SR_ENCODING,MOVE);
        put(MOVEM_FROM_REGISTERS_ENCODING,MOVEM);
        put(MOVEM_TO_REGISTERS_ENCODING,MOVEM);
        put(MOVEP_WORD_FROM_MEMORY_ENCODING,MOVEP);
        put(MOVEP_LONG_FROM_MEMORY_ENCODING,MOVEP);
        put(MOVEP_WORD_TO_MEMORY_ENCODING,MOVEP);
        put(MOVEP_LONG_TO_MEMORY_ENCODING,MOVEP);

        put(LEA_ENCODING,LEA);

        put(ILLEGAL_ENCODING,ILLEGAL);

        put(EXG_DATA_DATA_ENCODING,EXG);
        put(EXG_DATA_ADR_ENCODING,EXG);
        put(EXG_ADR_ADR_ENCODING,EXG);

        put(NOP_ENCODING,NOP);
        put(DBCC_ENCODING,DBCC);
        put(BCC_8BIT_ENCODING,BCC);
        put(BCC_16BIT_ENCODING,BCC);
        put(BCC_32BIT_ENCODING,BCC);

        put(JSR_ENCODING,JSR);
        put(RTS_ENCODING,RTS);
        put(LINK_ENCODING,LINK);
        put(UNLK_ENCODING,UNLK);
        put(RESET_ENCODING,RESET);
        put(RTR_ENCODING,RTR);
        put(PEA_ENCODING,PEA);
        put(NEG_ENCODING,NEG);
        put(ROL_REGISTER_ENCODING,ROL);
        put(ROR_REGISTER_ENCODING,ROR);
        put(ROL_IMMEDIATE_ENCODING,ROL);
        put(ROR_IMMEDIATE_ENCODING,ROR);
        put(ROL_MEMORY_ENCODING,ROL);
        put(ROR_MEMORY_ENCODING,ROR);

        put(LSL_REGISTER_ENCODING,LSL);
        put(LSR_REGISTER_ENCODING,LSR);
        put(LSL_IMMEDIATE_ENCODING,LSL);
        put(LSR_IMMEDIATE_ENCODING,LSR);
        put(LSL_MEMORY_ENCODING,LSL);
        put(LSR_MEMORY_ENCODING,LSR);

        put(ASL_REGISTER_ENCODING,ASL);
        put(ASR_REGISTER_ENCODING,ASR);
        put(ASL_IMMEDIATE_ENCODING,ASL);
        put(ASR_IMMEDIATE_ENCODING,ASR);
        put(ASL_MEMORY_ENCODING,ASL);
        put(ASR_MEMORY_ENCODING,ASR);

        put(ROXL_REGISTER_ENCODING,ROXL);
        put(ROXR_REGISTER_ENCODING,ROXR);
        put(ROXL_IMMEDIATE_ENCODING,ROXL);
        put(ROXR_IMMEDIATE_ENCODING,ROXR);
        put(ROXL_MEMORY_ENCODING,ROXL);
        put(ROXR_MEMORY_ENCODING,ROXR);

        put(EXTW_ENCODING,EXT);
        put(EXTL_ENCODING,EXT);
        put(BTST_DYNAMIC_ENCODING,BTST);
        put(BTST_STATIC_ENCODING,BTST);
        put(BCLR_DYNAMIC_ENCODING,BCLR);
        put(BCLR_STATIC_ENCODING,BCLR);
        put(BSET_DYNAMIC_ENCODING,BSET);
        put(BSET_STATIC_ENCODING,BSET);
        put(BCHG_DYNAMIC_ENCODING,BCHG);
        put(BCHG_STATIC_ENCODING,BCHG);
        put(CLR_ENCODING,CLR);
        put(TST_ENCODING,TST);
        put(TRAPV_ENCODING,TRAPV);
        put(SWAP_ENCODING,SWAP);
        put(NOT_ENCODING,NOT);
        put(CHK_WORD_ENCODING,CHK);
        put(CHK_LONG_ENCODING,CHK);
        put(SCC_ENCODING,SCC);
        put(STOP_ENCODING,STOP);
        put(TAS_ENCODING,TAS);
        put(NEGX_ENCODING,NEGX);

        put(ANDI_TO_SR_ENCODING, AND);
        put(ANDI_TO_CCR_ENCODING, AND);
        put(ANDI_BYTE_ENCODING,AND);
        put(ANDI_WORD_ENCODING,AND);
        put(ANDI_LONG_ENCODING,AND);
        put(AND_SRC_EA_ENCODING,AND);
        put(AND_DST_EA_ENCODING,AND);

        put(ORI_WORD_ENCODING,ORI);
        put(ORI_LONG_ENCODING,ORI);
        put(OR_SRC_EA_ENCODING,OR);
        put(OR_DST_EA_ENCODING,OR);

        put(EORI_WORD_ENCODING,EORI);
        put(EORI_LONG_ENCODING,EORI);

        put(EOR_DST_EA_ENCODING,EOR);
        put(EORI_TO_CCR_ENCODING,EORI);
        put(EORI_TO_SR_ENCODING,EORI);

        put(MULU_ENCODING,MULU);
        put(MULS_ENCODING,MULS);

        put(DIVU_ENCODING,DIVU);
        put(DIVS_ENCODING,DIVS);

        // additions
        put(ADDQ_ENCODING,ADDQ);

        put(ADDA_LONG_ENCODING,ADDA);
        put(ADDA_WORD_ENCODING,ADDA);

        put(ADDI_LONG_ENCODING,ADDI);
        put(ADDI_WORD_ENCODING,ADDI);

        put(ADDX_DATAREG_ENCODING,ADDX);
        put(ADDX_ADDRREG_ENCODING,ADDX);

        put(ADD_DST_DATA_ENCODING,ADD);
        put(ADD_DST_EA_ENCODING,ADD);

        // comparisons
        put(CMPA_WORD_ENCODING,CMPA);
        put(CMPA_LONG_ENCODING,CMPA);
        put(CMPI_LONG_ENCODING,CMPI);
        put(CMPI_WORD_ENCODING,CMPI);
        put(CMPM_ENCODING,CMPM);
        put(CMP_ENCODING,CMP);

        // subtractions
        put(SUBA_WORD_ENCODING,SUBA);
        put(SUBA_LONG_ENCODING,SUBA);

        put(SUBQ_ENCODING,SUBQ);
        put(SUBI_WORD_ENCODING,SUBI);
        put(SUBI_LONG_ENCODING,SUBI);

        put(SUB_DST_DATA_ENCODING,SUB);
        put(SUB_DST_EA_ENCODING,SUB);

        put(SUBX_DATA_REG_ENCODING,SUBX);
        put(SUBX_ADDR_REG_ENCODING,SUBX);

        // sanity check
        for ( Instruction insn : Instruction.values() ) {
            if ( ! values().contains(insn ) )
            {
                // conditional instructions are special as we operate on them in bulk
                if ( insn.conditionalType == ConditionalInstructionType.NONE )
                {
                    LOG.info( "****************************************************" );
                    LOG.info( "Internal error, you forgot to add mappings for instruction " + insn + " to the disassembly map" );
                    LOG.info( "****************************************************" );
                }
            }
        }
    }};

    private static List<EncodingTableGenerator.IValueIterator> addressingModeRegisterGenerator(AddressingMode m1, AddressingMode...additional)
    {
        return Collections.singletonList( new EncodingTableGenerator.AddressingModeIterator(Field.SRC_MODE,Field.SRC_VALUE,toSet(m1,additional)));
    }

    /**
     * Returns the value iterator to be used when
     * enumerating all possible 16-bit opcodes for this instruction
     * during emulator jump-table generation.
     *
     * If the instruction only has a single static/fixed encoding, this method will never get invoked
     * so does not need to be implemented.
     * @return
     */
    public EncodingTableGenerator.IValueIterator getValueIterator(InstructionEncoding encoding, CPUType cpuType)
    {
        throw new UnsupportedOperationException("getValueIterator() not implemented for "+this);
    }

    private static EncodingTableGenerator.IValueIterator sizeBits()
    {
        return intValues(Field.SIZE,0b00,0b01,0b10);
    }


    private static EncodingTableGenerator.IValueIterator registerRange(Field field) {
        return range(field,0,8);
    }

    private static EncodingTableGenerator.IValueIterator range(Field field,int startInclusive,int endValueExclusive) {
        return new EncodingTableGenerator.RangedValueIterator(field,startInclusive,endValueExclusive);
    }

    private static EncodingTableGenerator.IValueIterator intValues(Field field,int value1,int... values) {
        return new EncodingTableGenerator.IntValueIterator(field,value1,values);
    }

    private static EncodingTableGenerator.IValueIterator sourceAddressingModes()
    {
        final AddressingMode m1 = AddressingMode.values()[0];
        final AddressingMode[] additional = Stream.of(AddressingMode.values()).skip(1)
            .toArray(size->new AddressingMode[size]);
        return sourceAddressingModes(m1,additional);
    }

    private static EncodingTableGenerator.IValueIterator sourceAddressingModes(AddressingMode m1,AddressingMode...modes)
    {
        return new EncodingTableGenerator.AddressingModeIterator(
            Field.SRC_MODE,
            Field.SRC_BASE_REGISTER,toSet(m1,modes));
    }

    private static <T> Set<T> toSet(T m1,T...modes) {
        final Set<T> set = new HashSet<>();
        set.add( m1 );
        if ( modes != null ) {
            Stream.of(modes).forEach(set::add );
        }
        return set;
    }

    /**
     * Returns the total number of 16-bit memory accesses the
     * execution of this instruction would take (excluding the fetch of the 16-bit instruction word itself).
     *
     * @param srcMode
     * @param dstMode
     * @return
     */
    public int getMemoryCycleCount(AddressingMode srcMode,AddressingMode dstMode) {
        return 0;
    }

    private static EncodingTableGenerator.IValueIterator sourceAddressingModes(CPUType cpuType,AddressingModeKind kind1,AddressingModeKind... kinds)
    {
        final Set<AddressingModeKind> set = toSet(kind1, kinds);
        return sourceAddressingModes(cpuType, mode -> mode.hasKinds(set) );
    }

    private static EncodingTableGenerator.IValueIterator destAddressingModes()
    {
        final AddressingMode m1 = AddressingMode.values()[0];
        final AddressingMode[] additional = Stream.of(AddressingMode.values()).skip(1).toArray(size->new AddressingMode[size]);
        return destAddressingModes(m1,additional);
    }

    private static EncodingTableGenerator.IValueIterator destAddressingModes(AddressingMode m1,AddressingMode...modes)
    {
        return new EncodingTableGenerator.AddressingModeIterator(Field.DST_MODE,Field.DST_BASE_REGISTER,toSet(m1,modes));
    }

    private static EncodingTableGenerator.IValueIterator destAddressingModes(CPUType cpuType,AddressingModeKind kind1,AddressingModeKind... kinds)
    {
        final Set<AddressingModeKind> set = toSet(kind1, kinds);
        return destAddressingModes(cpuType, mode -> mode.hasKinds(set ) );
    }

    private static EncodingTableGenerator.IValueIterator destAddressingModes(CPUType cpuType, Predicate<AddressingMode> pred)
    {
        final List<AddressingMode> values = Stream.of(AddressingMode.values())
            .filter( x -> x != AddressingMode.IMPLIED )
            .filter( cpuType::supports )
            .filter( pred )
            .collect(Collectors.toList());
        if ( values.size() <= 1 ) {
            return destAddressingModes(values.get(0));
        }
        final AddressingMode[] subArray = values.stream().skip(1).toArray(size->new AddressingMode[size]);
        return destAddressingModes(values.get(0),subArray);
    }

    private static EncodingTableGenerator.IValueIterator sourceAddressingModes(CPUType cpuType, Predicate<AddressingMode> pred)
    {
        final List<AddressingMode> values = Stream.of(AddressingMode.values())
            .filter( x -> x != AddressingMode.IMPLIED )
            .filter( cpuType::supports )
            .filter( pred )
            .collect(Collectors.toList());
        if ( values.size() <= 1 ) {
            return sourceAddressingModes(values.get(0));
        }
        final AddressingMode[] subArray = values.stream().skip(1).toArray(size->new AddressingMode[size]);
        return sourceAddressingModes(values.get(0),subArray);
    }
}