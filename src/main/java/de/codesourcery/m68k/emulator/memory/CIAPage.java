package de.codesourcery.m68k.emulator.memory;

import de.codesourcery.m68k.emulator.chips.CIA8520;
import de.codesourcery.m68k.emulator.exceptions.MemoryAccessException;

/**
 * {@link MemoryPage Memory page} that implements the Amiga's CIA chips address space.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CIAPage extends MemoryPage
{
    private final CIA8520 ciaa;
    private final CIA8520 ciab;
    private final int startAddress;

            /*
The system hardware selects the CIAs when the upper three address bits are
101.  Furthermore, CIAA is selected when A12 is low, A13 high; CIAB is
selected when A12 is high, A13 low.  CIAA communicates on data bits 7-0,
CIAB communicates on data bits 15-8.

Address bits A11, A10, A9, and A8 are used to specify which of the 16
internal registers you want to access.  This is indicated by "r" in the
address.  All other bits are don't cares.  So, CIAA is selected by the
following binary address:  101x xxxx xx01 rrrr xxxx xxx0. CIAB address:
101x xxxx xx10 rrrr xxxx xxx1

With future expansion in mind, we have decided on the following addresses:
CIAA = BFEr01; CIAB = BFDr00.
             */

    public CIAPage(int startAddress,CIA8520 ciaa,CIA8520 ciab)
    {
        this.startAddress = startAddress;
        this.ciaa = ciaa;
        this.ciab = ciab;
    }

    @Override
    public byte readByte(int offset)
    {
        final int address = startAddress+offset;
        if ( isCIAA(address) ) {
            return (byte) ciaa.readRegister(getRegisterNumber(address ) );
        }
        if ( isCIAB(address) ) {
            return (byte) ciab.readRegister(getRegisterNumber(address ) );
        }
        return 0;
    }

    @Override
    public byte readByteNoSideEffects(int offset)
    {
        final int address = startAddress+offset;
        if ( isCIAA(address) ) {
            return (byte) ciaa.readRegisterNoSideEffects(getRegisterNumber(address ) );
        }
        if ( isCIAB(address) ) {
            return (byte) ciab.readRegisterNoSideEffects(getRegisterNumber(address ) );
        }
        return 0;
    }

    @Override
    public void writeByte(int offset, int value) throws MemoryAccessException
    {
        final int address = startAddress+offset;
        if ( isCIAA(address) ) {
            ciaa.writeRegister(getRegisterNumber(address),value);
        }
        else if ( isCIAB(address) )
        {
            ciab.writeRegister(getRegisterNumber(address),value);
        }
    }

    private static boolean isCIAA(int address)
    {
        return (address & (1<<13|1<<12)) == (1<<13);
    }

    private static boolean isCIAB(int address)
    {
        return (address & (1<<13|1<<12)) == (1<<12);
    }

    private static int getRegisterNumber(int address) {
        // Address bits A11, A10, A9, and A8 are used to specify which of the 16 internal registers you want to access.
        return (address & (0b1111_0000_0000))>>8;
    }
}