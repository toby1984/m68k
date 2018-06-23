package de.codersourcery.m68k.assembler.arch;

import de.codersourcery.m68k.emulator.cpu.CPU;
import de.codersourcery.m68k.utils.Misc;

/**
 * Enumeration of all CPU flag condition tests supported by M68k.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum Condition
{
    BRT(0b0000),  // always true
    BRF(0b0001),  // always false
    BHI(0b0010),
    BLS(0b0011),
    BCC(0b0100),
    BCS(0b0101),
    BNE(0b0110),
    BEQ(0b0111),
    BVC(0b1000),
    BVS(0b1001),
    BPL(0b1010),
    BMI(0b1011),
    BGE(0b1100),
    BLT(0b1101),
    BGT(0b1110),
    BLE(0b1111);

    public final int bits;

    Condition(int cc) {
        this.bits = cc;
    }

    public static boolean isTrue(CPU cpu,int cc)
    {
        final boolean C = cpu.isCarry();
        final boolean N = cpu.isNegative();
        final boolean V = cpu.isOverflow();
        final boolean Z = cpu.isZero();

                /*
See https://en.wikibooks.org/wiki/68000_Assembly

Mnemonic Condition Encoding Test
BRA* True            0000 = 1
F*   False           0001 = 0
BHI High             0010 = !C & !Z
BLS Low or Same      0011 = C | Z
BCC/BHI Carry Clear  0100 = !C
BCS/BLO Carry Set    0101 = C
BNE Not Equal        0110 = !Z
BEQ Equal            0111 = Z
BVC Overflow Clear   1000 = !V
BVS Overflow Set     1001 = V
BPL Plus             1010 = !N
BMI Minus            1011 = N
BGE Greater or Equal 1100 = (N &  V) | (!N & !V)
BLT Less Than        1101 = (N & !V) | (!N & V)
BGT Greater Than     1110 = ((N & V) | (!N & !V)) & !Z;
BLE Less or Equal    1111 = Z | (N & !V) | (!N & V)

*Not available for the Bcc instruction.
                 */

        switch( cc )
        {
            case 0b0000:
                return true;
            case 0b0001:
                return false;
            case 0b0010:
                return !C & !Z;
            case 0b0011:
                return C | Z;
            case 0b0100:
                return !C;
            case 0b0101:
                return C;
            case 0b0110:
                return !Z;
            case 0b0111:
                return Z;
            case 0b1000:
                return !V;
            case 0b1001:
                return V;
            case 0b1010:
                return !N;
            case 0b1011:
                return N;
            case 0b1100:
                return (N &  V) | (!N & !V);
            case 0b1101:
                return (N & !V) | (!N & V);
            case 0b1110:
                return ((N & V) | (!N & !V)) & !Z;
            case 0b1111:
                return Z | (N & !V) | (!N & V);
            default:
                throw new RuntimeException("Unreachable code reached: "+Misc.binary16Bit(cc));
        }
    }
}
