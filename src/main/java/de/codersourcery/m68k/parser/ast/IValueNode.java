package de.codersourcery.m68k.parser.ast;

public interface IValueNode
{
    /**
     * Returns the value of this node sign-extended to a given number of bits.
     *
     * @param inputBits number of bits to read from the underlying input value
     * @param outputBits Number of bits to return,only 8,16 or 32 are supported.
     *                     If <code>outputBits</code> == <code>inputBits</code> the value will be returned
     *                     unaltered
     * @return
     * @throws IllegalArgumentException if sign-extending to the requested number of bits is not supported
     */
    public int getBits(int inputBits,int outputBits);
}
