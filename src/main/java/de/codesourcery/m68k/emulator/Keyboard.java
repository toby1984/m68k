package de.codesourcery.m68k.emulator;

import de.codesourcery.m68k.emulator.chips.CIA8520;

/**
 * Keyboard emulation.
 * @author tobias.gierke@code-sourcery.de
 */
public class Keyboard
{
    /*
     * The keyboard data line (KDAT) is connected to the  SP pin ,
     * the keyboard clock (KCLK) is connected to the CNT pin .
     *
     * See http://amigadev.elowar.com/read/ADCD_2.1/Hardware_Manual_guide/node0197.html
     *
     * The  CNT line  is used as a clock for the keyboard. On each transition of
     * this line, one bit of data is clocked in from the keyboard. The keyboard
     * sends this clock when each data bit is stable on the  SP line . The clock
     * is an active low pulse. The rising edge of this pulse clocks in the data.
     *
     * After a data byte has been received from the keyboard, an  interrupt  from
     * the 8520 is issued to the processor.  The keyboard waits for a handshake
     * signal from the system before transmitting any more keystrokes. This
     * handshake is issued by the processor pulsing the  SP line  low then high.
     * While some keyboards can detect a 1 microsecond handshake pulse, the pulse
     * must be at least 85 microseconds for operation with all models of Amiga
     * keyboards.
     *
     * If another keystroke is received before the previous one has been accepted
     * by the processor, the keyboard microprocessor holds keys in a 10 keycode
     * type-ahead buffer.
     *
     *  IntLvlTwoPorts:
     *		movem.l	d0-d1/a0-a2,-(a7)
     *
     *		lea	_custom,a0
     *		moveq	#INTF_PORTS,d0
     *
     *	    ;check if is it level 2 interrupt
     *		move.w	intreqr(a0),d1
     *		and.w	d0,d1
     *		beq.b	.end
     *
     *	    ;check if SP cause interrupt, hopefully CIAICRF_SP = 8
     *		lea	_ciaa,a1
     *		move.b	ciaicr(a1),d1
     *		and.b	d0,d1
     *		beq.b	.end
     *
     *		move.b	ciasdr(a1),d1			;get keycode
     *		or.b	#CIACRAF_SPMODE,ciacra(a1)	;start SP handshaking
     *
     *		lea	dt+keys(pc),a2
     *		not.b	d1
     *		lsr.b	#1,d1
     *		scc	(a2,d1.w)
     *
     *	    ;handshake
     *		moveq	#3-1,d1
     *.wait1		move.b	vhposr(a0),d0
     *.wait2		cmp.b	vhposr(a0),d0
     *		beq.b	.wait2
     *		dbf	d1,.wait1
     *
     *	    ;set input mode
     *		and.b	#~(CIACRAF_SPMODE),ciacra(a1)
     *
     *.end	move.w	#INTF_PORTS,intreq(a0)
     *		tst.w	intreqr(a0)
     *		movem.l	(a7)+,d0-d1/a0-a2
     *		rte
     */
    private final CIA8520 cia;

    public Keyboard(CIA8520 cia) {
        this.cia = cia;
    }

    public void tick() {

    }
}
