package de.codersourcery.m68k.emulator.cpu;

import de.codersourcery.m68k.utils.Misc;

// TODO: Quick hack to make unit test pass until proper interrupt/exception handling has been implemented
public class IllegalInstructionException extends RuntimeException
{
    public final int pc;
    public final int instructionWord;

    public IllegalInstructionException(int pc, int instructionWord)
    {
        super("Illegal instruction "+Misc.binary16Bit(instructionWord)+" @ "+Misc.hex(pc) );
        this.pc = pc;
        this.instructionWord = instructionWord;
    }
}
