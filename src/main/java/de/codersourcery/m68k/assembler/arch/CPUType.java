package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.parser.ast.InstructionNode;

@SuppressWarnings("unused")
public enum CPUType
{
    M68000(0b0000_0001),
    M68010(0b0000_0011),
    M68020(0b0000_0111),
    M68030(0b0000_1111),
    M68040(0b0001_1111);

    public static final CPUType BEST = M68040;

    private final int featureMask;

    CPUType(int featureMask)
    {
        this.featureMask = featureMask;
    }

    public boolean supports(InstructionNode insn, ICompilationContext context)
    {
        switch(insn.instruction)
        {
            case CHK:
                if ( insn.hasExplicitOperandSize() && insn.hasOperandSize(OperandSize.LONG ) )
                {
                    context.error(this+" does not support .l with CHK (MC68020+ only)", insn);
                    return false;
                }
                break;
        }
        return true;
    }

    public boolean isCompatibleWith(CPUType other)
    {
        return (this.featureMask & other.featureMask) == other.featureMask;
    }

    public boolean isNotCompatibleWith(CPUType other)
    {
        return (this.featureMask & other.featureMask) != other.featureMask;
    }
}