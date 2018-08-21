package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.utils.IBus;
import de.codersourcery.m68k.utils.Misc;

/**
 * TODO: FLAG/PC handshake not implemented
 *
 * - The system hardware selects the CIAs when the upper three address bits are 101
 * - CIAA is selected when A12 is low, A13 high
 * - CIAB is selected when A12 is high, A13 low
 * - CIAA communicates on data bits 7-0
 * - CIAB communicates on data bits 15-8
 * - Address bits A11, A10, A9, and A8 are used to specify which of the 16
 * internal registers you want to access.  All other bits are don't cares.
 *
 * CIAA = BFEr01; CIAB = BFDr00.
 *
 * CIAA is selected by the following binary address: 101x xxxx xx01 rrrr xxxx xxx0
 * CIAB is selected by the following binary address: 101x xxxx xx10 rrrr xxxx xxx1
 *
 * Software must use byte accesses to these address, and no other.
 *
 * SOFTWARE NOTE:
 * The operating system kernel has already allocated the use of
 * several of the 8520 timers.
 *
 * CIAA, timer A - keyboard (used continuously to handshake keystrokes). NOT AVAILABLE.
 * CIAA, timer B - Virtual timer device (used continuously whenever system Exec is in control
 *                 (used for task switching, interrupts and timing).
 * CIAA, TOD - 50/60 Hz timer used by timer.device.
 *             The A1000 uses power line tick. The A500 uses vertical sync. The A2000 has a jumper selection.
 * CIAB, timer A - not used
 * CIAB, timer B - not used
 * CIAB, TOD - graphics.library video beam follower. This timer counts at the horizontal sync rate,
 *       and is used to synchronize graphics events to the video beam.
 *
 * CIAA Address Map
 * ---------------------------------------------------------------------------
 *  Byte    Register                  Data bits
 * Address    Name     7     6     5     4     3     2     1    0
 * ---------------------------------------------------------------------------
 * BFE001    pra     /FIR1 /FIR0  /RDY /TK0  /WPRO /CHNG /LED  OVL
 * BFE101    prb     Parallel port
 * BFE201    ddra    Direction for port A (BFE001);1=output (set to 0x03)
 * BFE301    ddrb    Direction for port B (BFE101);1=output (can be in or out)
 * BFE401    talo    CIAA timer A low byte (.715909 Mhz NTSC; .709379 Mhz PAL)
 * BFE501    tahi    CIAA timer A high byte
 * BFE601    tblo    CIAA timer B low byte (.715909 Mhz NTSC; .709379 Mhz PAL)
 * BFE701    tbhi    CIAA timer B high byte
 * BFE801    todlo   50/60 Hz event counter bits 7-0 (VSync or line tick)
 * BFE901    todmid  50/60 Hz event counter bits 15-8
 * BFEA01    todhi   50/60 Hz event counter bits 23-16
 * BFEB01            not used
 * BFEC01    sdr     CIAA serial data register (connected to keyboard)
 * BFED01    icr     CIAA interrupt control register
 * BFEE01    cra     CIAA control register A
 * BFEF01    crb     CIAA control register B
 *
 * CIAB Address Map
 * ---------------------------------------------------------------------------
 *  Byte     Register                   Data bits
 * Address     Name     7     6     5     4     3     2     1     0
 * ---------------------------------------------------------------------------
 * BFD000    pra     /DTR  /RTS  /CD   /CTS  /DSR   SEL   POUT  BUSY
 * BFD100    prb     /MTR  /SEL3 /SEL2 /SEL1 /SEL0 /SIDE  DIR  /STEP
 * BFD200    ddra    Direction for Port A (BFD000);1 = output (set to 0xFF)
 * BFD300    ddrb    Direction for Port B (BFD100);1 = output (set to 0xFF)
 * BFD400    talo    CIAB timer A low byte (.715909 Mhz NTSC; .709379 Mhz PAL)
 * BFD500    tahi    CIAB timer A high byte
 * BFD600    tblo    CIAB timer B low byte (.715909 Mhz NTSC; .709379 Mhz PAL)
 * BFD700    tbhi    CIAB timer B high byte
 * BFD800    todlo   Horizontal sync event counter bits 7-0
 * BFD900    todmid  Horizontal sync event counter bits 15-8
 * BFDA00    todhi   Horizontal sync event counter bits 23-16
 * BFDB00            not used
 * BFDC00    sdr     CIAB serial data register (unused)
 * BFDD00    icr     CIAB interrupt control register
 * BFDE00    cra     CIAB Control register A
 * BFDF00    crb     CIAB Control register B
 *
 * Note:  CIAA can generate INT2.
 * Note:  CIAB can generate INT6.
 */
public class CIA8520
{
    public enum Name
    {
        CIAA,CIAB
    }
    /*
Register  Name          Function
0 0       PRA           Port A data register
1 1       PRB           Port B data register
2 2       DDRA          Port A data direction register
3 3       DDRB          Port B data direction register
4 4       TALO          Timer A lower 8 bits
5 5       TAffl         Timer A upper 8 bits
6 6       TBLO          Timer Blower 8 bits
7 7       TBHI          Timer B upper 8 bits
8 8       Event low     Counter bits 0-7
9 9       Event med.    Counter bits 8-15
10 A      Event high    Counter bits 16-23
11 B      _             Unused
12 C      SP            Serial port data register
13 D      ICR           Interrupt control register
14 E      CRA           Control register A
15 F      CRB           Control register B
     */

    public static final int REG_PORTA = 0;
    public static final int REG_PORTB = 1;
    public static final int REG_DDRA = 2;
    public static final int REG_DDRB = 3;
    public static final int REG_TIMERA_LO = 4;
    public static final int REG_TIMERA_HI = 5;
    public static final int REG_TIMERB_LO = 6;
    public static final int REG_TIMERB_HI = 7;
    public static final int REG_EVENT_LO= 8;
    public static final int REG_EVENT_MED= 9;
    public static final int REG_EVENT_HI= 10;
    // 11 is unused
    public static final int REG_SERIAL_DATA = 12;
    public static final int REG_IRQ_CTRL = 13;
    public static final int REG_CTRLA = 14;
    public static final int REG_CTRLB = 15;

    /* interrupt control register bit masks */
    public static final int ICR_TA=1<<0;
    public static final int ICR_TB=1<<1;
    public static final int ICR_ALRM=1<<2;
    public static final int ICR_SP=1<<3;
    public static final int ICR_FLG=1<<4;
    public static final int ICR_IR=1<<7;
    public static final int ICR_SETCLR=1<<7;

    /* control register A bit masks */

    /*
  BIT  NAME     FUNCTION
  ---  ----     --------
   0   START    1 = start Timer A, 0 = stop Timer A.
                    This bit is automatically reset (= 0) when
                    underflow occurs during one-shot mode.
   1   PBON     1 = Timer A output on PB6, 0 = PB6 is normal operation.
   2   OUTMODE  1 = toggle, 0 = pulse.
   3   RUNMODE  1 = one-shot mode, 0 = continuous mode.
   4   LOAD     1 = force load (this is a strobe input, there is no
                    data storage;  bit 4 will always read back a zero
                    and writing a 0 has no effect.)
   5   INMODE   1 = Timer A counts positive CNT transitions,
                0 = Timer A counts 02 pulses.
   6   SPMODE   1 = Serial port=output (CNT is the source of the shift
                    clock)
                0 = Serial port=input  (external shift clock is
                    required)
   7   UNUSED
     */
    public static final int CTRL_START=1<<0;
    public static final int CTRL_PBON=1<<1;
    public static final int CTRL_OUTMODE=1<<2;
    public static final int CTRL_RUNMODE=1<<3;
    public static final int CTRL_LOAD=1<<4;
    public static final int CTRL_INMODE0=1<<5;
    public static final int CTRL_SPMODE=1<<6;
    public static final int CTRL_TODIN=1<<7;

    /* control register B bit masks */
    public static final int CTRL_INMODE1=(1<<6);
    public static final int CTRL_ALARM=(1<<7);

    // control register B INMODE masks
    public static final int INMODE_PHI2=0; // 0b00
    public static final int INMODE_CNT=CTRL_INMODE0; // 0b01
    public static final int INMODE_TA=CTRL_INMODE1; // 0b10
    public static final int INMODE_CNT_TA=CTRL_INMODE1|CTRL_INMODE0; // 0b11

// Port definitions -- what each bit in a cia peripheral register is tied to

    // cia A port A (0xbfe001)
    public static final int CIA_PORTA_GAMEPORT1=1<<7;   // gameport 1, pin 6 (fire button*);
    public static final int CIA_PORTA_GAMEPORT0=1<<6;   // gameport 0, pin 6 (fire button*);
    public static final int CIA_PORTA_DSKRDY=1<<5;  // disk ready*;
    public static final int CIA_PORTA_DSKTRACK0=1<<4;   // disk on track 00*;
    public static final int CIA_PORTA_DSKPROT=1<<3;   // disk write protect*;
    public static final int CIA_PORTA_DSKCHANGE=1<<2;   // disk change*;
    public static final int CIA_PORTA_LED=1<<1;   // led light control (0==>bright);
    public static final int CIA_PORTA_OVERLAY=1<<0;   // memory overlay bit;

// cia A port B (0xbfe101) -- parallel port

    // cia B port A (0xbfd000) -- serial and printer control
    public static final int CIAB_PORTA_COMDTR=1<<7;   // serial Data Terminal Ready*;
    public static final int CIAB_PORTA_COMRTS=1<<6;   // serial Request to Send*;
    public static final int CIAB_PORTA_COMCD=1<<5;    // serial Carrier Detect*;
    public static final int CIAB_PORTA_COMCTS=1<<4;   // serial Clear to Send*;
    public static final int CIAB_PORTA_COMDSR=1<<3;   // serial Data Set Ready*;
    public static final int CIAB_PORTA_PRTRSEL=1<<2;  // printer SELECT;
    public static final int CIAB_PORTA_PRTRPOUT=1<<1; // printer paper out;
    public static final int CIAB_PORTA_PRTRBUSY=1<<0; // printer busy;

    // cia B port B (0xbfd100) -- disk control
    public static final int CIAB_PORB_DSKMOTOR=1<<7;  // disk motorr*;
    public static final int CIAB_PORB_DSKSEL3=1<<6;   // disk select unit 3*;
    public static final int CIAB_PORB_DSKSEL2=1<<5;   // disk select unit 2*;
    public static final int CIAB_PORB_DSKSEL1=1<<4;   // disk select unit 1*;
    public static final int CIAB_PORB_DSKSEL0=1<<3;   // disk select unit 0*;
    public static final int CIAB_PORB_DSKSIDE=1<<2;   // disk side select*;
    public static final int CIAB_PORB_DSKDIREC=1<<1;  // disk direction of seek*;
    public static final int CIAB_PORB_DSKSTEP=1<<0;   // disk step heads*;

    private static final int IRQ_TIMERA_TRIGGERED = 0;
    private static final int IRQ_TIMERB_TRIGGERED = 1;

    /*
     * Timer sources.
     *
     * Timer A:
     *
     * 00 = decrement each clock cycle
     * 01 = decrement each HIGH pulse on CNT line
     *
     * Timer B:
     * 00 = decrement each clock cycle
     * 01 = decrement each HIGH pulse on CNT line
     * 10 = decrement on timer A timeout
     * 11 = Timer A timeout when the CNT line is HIGH
     */
    public static final int CTRL_CNT0 = 1<<5;
    public static final int CTRL_CNT1 = 1<<6;

    public final Name name;

    private int timerAUnderflowCount;
    private int cycle;

    private int portADDR; // bit set to 0 => INPUT pin

    private int portALine; // actual state of portA pins
    private int portA; // portA register (applies only for pins where corresponding bit in DDR is set to 1)

    private int portBDDR; // bit set to 0 => INPUT pin
    private int portBLine;
    private int portB;

    private int timerA;
    private int timerALatch;
    private int ctrlA;

    private int timerB;
    private int timerBLatch;
    private int ctrlB;

    private boolean previousCnt;
    private boolean cntOut; // I/O
    private boolean cntIn; // I/O

    private boolean serialPin; // I/O
    private boolean serialDataAvailable;
    private int shiftRegisterBits=0; // how many data bits are in the shift register
    private int serialShiftReg;
    private int serialDataReg;

    private int triggeredInterrupts;
    private int irqMaskRegister;

    private boolean previousTODLine;
    private boolean currentTODLine; // I/O

    private boolean eventCounterLatched;
    private boolean eventCounterRunning;
    private int eventCounterAlarm;
    private int eventCounterAlarmLatch;
    private int eventCounter;

    private final IRQController irqController;

    public CIA8520(CIA8520.Name name, IRQController irqController) {
        this.name = name;
        this.irqController = irqController;
        reset();
    }

    @Override
    public String toString()
    {
        return name.toString();
    }

    private void writePortA(int value) {
        // clear all bits where portA DDR indicates there is an input pin (=0)
        // so that we ignore them
        value &= portADDR;
        portALine &= ~portADDR; // clear value of all output pins
        portALine |= value; // set output pins accordingly
    }

    private void writePortB(int value) {
        // clear all bits where portA DDR indicates there is an input pin (=0)
        // so that we ignore them
        value &= portBDDR;
        portBLine &= ~portBDDR; // clear value of all output pins
        portBLine |= value; // set output pins accordingly
    }

    private int readPortA() {
        // reading bits that are configured as output (=1)
        // in the DDR will return bits from portA
        int value = portALine & ~portADDR; // read value and clear all data bits where the DDR == 1 (=output pin)
        int regValue = portA & portADDR;
        return value | regValue;
    }

    private int readPortB() {
        // reading bits that are configured as output (=1)
        // in the DDR will return bits from portA
        int value = portBLine & ~portBDDR; // read value and clear all data bits where the DDR == 1 (=output pin)
        int regValue = portB & portBDDR;
        return value | regValue;
    }

    public void writeRegister(int regNum,int value)
    {
    /*
Register  Name          Function
0 0       PRA           Port A data register
1 1       PRB           Port B data register
2 2       DDRA          Port A data direction register
3 3       DDRB          Port B data direction register
4 4       TALO          Timer A lower 8 bits
5 5       TAffl         Timer A upper 8 bits
6 6       TBLO          Timer Blower 8 bits
7 7       TBHI          Timer B upper 8 bits
8 8       Event low     Counter bits 0-7
9 9       Event med.    Counter bits 8-15
10 A      Event high    Counter bits 16-23
11 B      _             Unused
12 C      SP            Serial port data register
13 D      ICR           Interrupt control register
14 E      CRA           Control register A
15 F      CRB           Control register B
        */
        switch( regNum )
        {
            case REG_PORTA:
                writePortA(value);
                break;
            case REG_PORTB:
                writePortB(value);
                break;
            case REG_DDRA:
                // DDR = 0 => INPUT pin
                // DDR = 1 => OUTPUT pin
                portADDR = value;
                int v = portA & portADDR;
                portALine = (portALine & ~portADDR) | v;
                break;
            case REG_DDRB:
                // DDR = 0 => INPUT pin
                // DDR = 1 => OUTPUT pin
                portBDDR = value;
                v = portA & portBDDR;
                portBLine = (portBLine & ~portBDDR) | v;
                break;
            case REG_TIMERA_LO:
                setTimerALo(value);
                break;
            case REG_TIMERA_HI:
                setTimerAHi(value);
                break;
            case REG_TIMERB_LO:
                setTimerBLo(value);
                break;
            case REG_TIMERB_HI:
                setTimerBHi(value);
                break;
            case REG_EVENT_LO:
                if ( (ctrlB & CTRL_ALARM) != 0 ) {
                    eventCounterAlarmLatch = (eventCounterAlarmLatch & 0xffff00) | (value & 0xff);
                    eventCounterAlarm = eventCounterAlarmLatch;
                }
                else
                {
                    eventCounter = (eventCounter & 0xffff00) | (value & 0xff);
                }
                eventCounterRunning = true;
                break;
            case REG_EVENT_MED:
                if ( (ctrlB & CTRL_ALARM) != 0 ) {
                    eventCounterAlarmLatch = (eventCounterAlarmLatch & 0xff00ff) | ((value & 0xff) << 8);
                }
                else
                {
                    eventCounter = (eventCounter & 0xff00ff) | ((value & 0xff) << 8);
                }
                eventCounterRunning = false;
                break;
            case REG_EVENT_HI:
                if ( (ctrlB & CTRL_ALARM) != 0 )
                {
                    eventCounterAlarmLatch = (eventCounterAlarmLatch & 0x00ffff) | ((value & 0xff) << 16);
                }
                else
                {
                    eventCounter = (eventCounter & 0x00ffff) | ((value & 0xff) << 16);
                }
                eventCounterRunning = false;
                break;
            case REG_SERIAL_DATA:
                serialDataReg = value & 0xff;
                serialDataAvailable = true;
                break;
            case REG_IRQ_CTRL:
                if ( (value & ICR_SETCLR) != 0 ) { // bit = 1
                    // set bits
                    irqMaskRegister |= (value & ~ICR_SETCLR);
                } else {
                    // clear bits
                    irqMaskRegister &= ~value;
                }
                System.out.println("IRQ mask is now: "+ Misc.binary8Bit(irqMaskRegister));
                System.out.println("IRQs active    : "+ Misc.binary8Bit(triggeredInterrupts));
                break;
            case REG_CTRLA:
                final boolean serialModeChanged = (ctrlA & CTRL_SPMODE) != (value & CTRL_SPMODE);
                ctrlA = value & ~CTRL_LOAD;
                if ( (value & CTRL_LOAD) != 0 ) {
                    loadTimerA();
                }
                if ( serialModeChanged ) {
                    shiftRegisterBits = 0;
                    serialDataAvailable = false;
                    serialShiftReg = 0;
                    if ( isSerialOutput() ) {
                        serialPin = false;
                    } else {
                        serialPin = true;
                    }
                }
                break;
            case REG_CTRLB:
                ctrlB = value & ~CTRL_LOAD;
                if ( (value & CTRL_LOAD) != 0 ) {
                    loadTimerB();
                }
                break;
            default:
                System.err.println("Unhandled register "+regNum+" on "+this);
        }
    }

    public int readRegister(int regNum) {
    /*
Register  Name          Function
0 0       PRA           Port A data register
1 1       PRB           Port B data register
2 2       DDRA          Port A data direction register
3 3       DDRB          Port B data direction register
4 4       TALO          Timer A lower 8 bits
5 5       TAffl         Timer A upper 8 bits
6 6       TBLO          Timer Blower 8 bits
7 7       TBHI          Timer B upper 8 bits
8 8       Event low     Counter bits 0-7
9 9       Event med.    Counter bits 8-15
10 A      Event high    Counter bits 16-23
11 B      _             Unused
12 C      SP            Serial port data register
13 D      ICR           Interrupt control register
14 E      CRA           Control register A
15 F      CRB           Control register B
        */
        switch( regNum )
        {
            case REG_PORTA:
                return readPortA();
            case REG_PORTB:
                return readPortB();
            case REG_DDRA:
                return portADDR;
            case REG_DDRB:
                return portBDDR;
            case REG_TIMERA_LO:
                return timerA & 0xff;
            case REG_TIMERA_HI:
                return (timerA & 0xff00) >> 8;
            case REG_TIMERB_LO:
                return timerB & 0xff;
            case REG_TIMERB_HI:
                return (timerA & 0xff00) >> 8;
            case REG_EVENT_LO:
                if ( (ctrlB & CTRL_ALARM) != 0 )
                {
                    if ( eventCounterLatched )
                    {
                        eventCounterLatched = false;
                        return eventCounterAlarmLatch & 0xff;
                    }
                }
                return eventCounter & 0xff;
            case REG_EVENT_MED:
                if ( (ctrlB & CTRL_ALARM) != 0 )
                {
                    if ( ! eventCounterLatched )
                    {
                        eventCounterAlarmLatch = eventCounter;
                        eventCounterLatched = true;
                    }
                    return (eventCounterAlarmLatch & 0xff00)>>8;
                }
                return (eventCounter & 0xff00) >> 8;
            case REG_EVENT_HI:
                if ( (ctrlB & CTRL_ALARM) != 0 )
                {
                    if ( ! eventCounterLatched )
                    {
                        eventCounterAlarmLatch = eventCounter;
                        eventCounterLatched = true;
                    }
                    return (eventCounterAlarmLatch & 0xff0000)>>16;
                }
                if ( eventCounterRunning ) {
                    eventCounterRunning = false;
                }
                return (eventCounter & 0xff0000) >> 16;
            case REG_SERIAL_DATA:
                return serialDataReg;
            case REG_IRQ_CTRL:
                int result = triggeredInterrupts;
                triggeredInterrupts = 0;
                return result;
            case REG_CTRLA:
                return ctrlA;
            case REG_CTRLB:
                return ctrlB;
            default:
                System.err.println("Unhandled register read #" + regNum + " on " + this);
                return 0;
        }
    }

    private void setTimerALo(int value)
    {
        timerALatch = (timerALatch & 0xff) | (value & 0xff);
    }

    private void setTimerAHi(int value)
    {
        timerALatch = (timerALatch & 0x00ff) | ((value & 0xff) << 8);
        if ( ! isTimerARunning() ) {
            loadTimerA();
        }
    }

    private void setTimerBLo(int value)
    {
        timerBLatch = (timerBLatch & 0xff) | (value & 0xff);
    }

    private void setTimerBHi(int value)
    {
        timerBLatch = (timerBLatch & 0x00ff) | ((value & 0xff) << 8);
        if ( ! isTimerBRunning() ) {
            loadTimerB();
        }
    }

    private boolean isTimerARunning() {
        return (ctrlA & CTRL_START) != 0;
    }

    private boolean isTimerBRunning() {
        return (ctrlB & CTRL_START) != 0;
    }

    private boolean isTimerAOneShot() {
        return (ctrlA & CTRL_RUNMODE) != 0;
    }

    private boolean isTimerAContinous() {
        return (ctrlA & CTRL_RUNMODE) == 0;
    }

    private boolean isTimerBOneShot() {
        return (ctrlB & CTRL_RUNMODE) != 0;
    }

    private void loadTimerA() {
        timerA = timerALatch;
    }

    private void loadTimerB() {
        timerB = timerBLatch;
    }

    private boolean isSerialInput() {
        // SPMODE   0 = Serial port=input  (external shift clock is required)
        return (ctrlA & CTRL_SPMODE) == 0;
    }

    private boolean isSerialOutput() {
        // SPMODE   1 = Serial port=output (CNT is the source of the shift clock)
        return (ctrlA & CTRL_SPMODE) != 0;
    }

    private void writePB6(boolean onOff)
    {
        if ( onOff ) {
            portBDDR |= 1<<6;
        } else {
            portBDDR &= ~(1<<6);
        }
        if ( ( portBDDR & 1<<6) != 0 ) {
            // port is set to output
            portBLine = ( portBLine & ~(1<<6) ) | (portBDDR & 1<<6);
        }
    }

    private boolean readPB6() {
        return (portBLine & 1<<6)!=0;
    }

    private void writePB7(boolean onOff)
    {
        if ( onOff ) {
            portBDDR |= 1<<7;
        } else {
            portBDDR &= ~(1<<7);
        }
        if ( ( portBDDR & 1<<7) != 0 ) {
            // port is set to output
            portBLine = ( portBLine & ~(1<<7) ) | (portBDDR & 1<<7);
        }
    }

    private boolean readPB7() {
        return (portBLine & 1<<7)!=0;
    }

    public void tick() {

        System.out.println("--- TICK ---");
        cycle++;

        // event counter (counting TOD positive edges)
        if ( eventCounterRunning && !previousTODLine && currentTODLine ) {
            eventCounter++;
            if ( (ctrlB & CTRL_ALARM) != 0 && eventCounter == eventCounterAlarm )
            {
                triggerInterrupt(ICR_ALRM);
                eventCounter = 0;
            }
        }
        previousTODLine = currentTODLine;

        // check CNT
        boolean cntPulse = ! previousCnt && cntIn;
        previousCnt = cntIn;

        // serial input
        if ( isSerialInput() && cntPulse )
        {
            // MSB is transmitted first
            serialShiftReg <<= 1;
            serialShiftReg |= (serialPin ? 1 : 0);
            shiftRegisterBits++;
            if ( shiftRegisterBits == 8 )
            {
                serialDataReg = serialShiftReg & 0xff;
                // shift register full, trigger IRQ
                triggerInterrupt(ICR_SP);
                shiftRegisterBits = 0;
            }
        }

        boolean timerATimeout=false;
        if ( isTimerARunning() )
        {
            if ( (ctrlA & CTRL_INMODE0 ) == 0) {
                timerA--;
            }
            else
            {
                if ( cntPulse )
                {
                    timerA--;
                    cntPulse = false;
                }
            }
            if ( timerA == 0 )
            {
                triggerInterrupt(ICR_TA);

                // serial: shift-out bits at tickerA/2 rate
                if ( isSerialOutput() && (++timerAUnderflowCount & 1) != 0 && isTimerAContinous() )
                {
                    boolean hasData = shiftRegisterBits > 0;
                    if ( ! hasData && serialDataAvailable)
                    {
                        serialShiftReg = serialDataReg;
                        shiftRegisterBits = 8;
                        serialDataAvailable = false;
                        triggerInterrupt(ICR_SP);
                        hasData = true;
                    }

                    if ( hasData )
                    {
                        cntOut = ! cntOut;
                        System.out.println("CNT: "+cntOut);
                        if ( cntOut )
                        {
                            // data becomes available on the rising edge of CNT
                            // and stays available until the next rising edge
                            serialPin = (serialShiftReg & 0b1000_0000) != 0;
                            System.out.println("BIT OUT: "+serialPin);
                            serialShiftReg <<= 1;
                            shiftRegisterBits--;
                            if (shiftRegisterBits == 0)
                            {
                                System.out.println("Shift register empty.");
                                triggerInterrupt(ICR_SP);
                            }
                        }
                    }
                }

                if ( ( ctrlA & CTRL_PBON ) != 0 )
                {
                    if ( (ctrlA & CTRL_OUTMODE) == 0 ) {
                        // PULSE
                        writePB6(true );
                    } else {
                        // toggle
                        writePB6(! readPB6() );
                    }
                }
                timerATimeout=true;
                loadTimerA();
                if ( isTimerAOneShot() ) {
                    // stop timer
                    ctrlA &= ~CTRL_START;
                }
            }
            else if ( ( ctrlA & CTRL_PBON ) != 0 ) {
                // output to PB6

                if ( (ctrlA & CTRL_OUTMODE) == 0 )
                {
                    // PULSE
                    writePB6(false);
                }
            }
        }

        if ( isTimerBRunning() )
        {
            switch( (ctrlB & (CTRL_INMODE0|CTRL_INMODE1)) >> 5 )
            {
                case 0b00:
                    // clk cycles
                    timerB--;
                    break;
                case 0b01:
                    // cnt pulses
                    if ( cntPulse ) {
                        timerB--;
                    }
                    break;
                case 0b10:
                    // timer A timeouts
                    if ( timerATimeout ) {
                        timerB--;
                    }
                    break;
                case 0b11:
                    // timer A timeouts when CNT is HI
                    if ( timerATimeout && cntIn) {
                        timerB--;
                    }
                    break;
                default:
                    throw new RuntimeException("Unreachable code reached");
            }

            if ( timerB == 0 )
            {
                triggerInterrupt(ICR_TB);
                if ( ( ctrlB & CTRL_PBON ) != 0 ) {
                    if ( (ctrlB & CTRL_OUTMODE) == 0 ) {
                        // PULSE
                        writePB7(true );
                    } else {
                        // toggle
                        writePB7(! readPB7() );
                    }
                }
                loadTimerB();
                if ( isTimerBOneShot() ) {
                    ctrlB &= ~CTRL_START; // stop timer
                }
            } else if ( ( ctrlB & CTRL_PBON ) != 0 ) {
                // output to PB7
                if ( (ctrlB & CTRL_OUTMODE) == 0 )
                {
                    // pulse
                    writePB7(false);
                }
            }
        }
    }

    private void triggerInterrupt(int maskBit)
    {
        System.out.println("IRQ triggered: "+ Misc.binary8Bit(maskBit));
        System.out.println("IRQ mask: "+ Misc.binary8Bit(irqMaskRegister));
        if ( (irqMaskRegister & maskBit) != 0)
        {
            System.out.println("Triggered external IRQ");
            triggeredInterrupts |= (maskBit|ICR_SETCLR);
            irqController.externalInterrupt(this);
        } else {
            triggeredInterrupts |= maskBit;
        }
    }

    public void reset()
    {
        ctrlA = ctrlB = 0;
        portA = portB = 0;
        portADDR = portBDDR = 0;
        timerA = timerALatch = 0;
        timerB = timerBLatch = 0;
        previousCnt = false;
        cntIn = cntOut = false;
        serialPin = true;
        serialDataAvailable = false;
        shiftRegisterBits = 0;
        serialShiftReg = 0;
        serialDataReg = 0;
        triggeredInterrupts = 0;
        irqMaskRegister = 0;
        previousTODLine = false;
        currentTODLine = false;
        eventCounter = 0;
        eventCounterAlarmLatch = 0;
        eventCounterAlarm = 0;
        eventCounterRunning = false;
        eventCounterLatched = false;
    }

    private final IBus bus = new IBus()
    {
        private final String[] pins = {
            "Serial","CNT_OUT","CNT_IN","IRQ"
        };

        @Override
        public String getName()
        {
            return name.toString();
        }

        @Override
        public String[] getPinNames()
        {
            return pins;
        }

        @Override
        public int readPins()
        {
            int result = 0;
            if ( serialPin ) {
                result |= 1<<0;
            }
            if ( cntOut ) {
                result |= 1<<1;
            }
            if ( cntIn ) {
                result |= 1<<2;
            }
            if ( (triggeredInterrupts & ICR_SETCLR) != 0 ) {
                result |= 1<<3;
            }
            return result;
        }
    };

    public IBus getBus()
    {
        return bus;
    }

    public void setSerialPin(boolean value)
    {
        serialPin = value;
    }

    public boolean readSerialPin()
    {
        return serialPin;
    }

    public boolean readCnt() {
        return cntOut;
    }

    public void setCntIn(boolean value) {
        this.cntIn = value;
    }
}
