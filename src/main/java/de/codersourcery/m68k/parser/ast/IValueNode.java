package de.codersourcery.m68k.parser.ast;

public interface IValueNode
{
    /**
     * Returns a bit representation of this node's value suitabe for inclusion in an instruction word.
     *
     * This method is called during code generation.
     *
     * @return value
     */
    public int getBits();
}
