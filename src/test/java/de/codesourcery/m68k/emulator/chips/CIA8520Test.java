package de.codesourcery.m68k.emulator.chips;

import de.codesourcery.m68k.assembler.arch.CPUType;
import de.codesourcery.m68k.emulator.Amiga;
import de.codesourcery.m68k.emulator.CPU;
import de.codesourcery.m68k.emulator.memory.Blitter;
import de.codesourcery.m68k.emulator.memory.DMAController;
import de.codesourcery.m68k.emulator.memory.MMU;
import de.codesourcery.m68k.emulator.memory.Memory;
import de.codesourcery.m68k.emulator.memory.Video;
import de.codesourcery.m68k.utils.BusSpy;
import de.codesourcery.m68k.utils.Misc;
import junit.framework.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CIA8520Test extends TestCase
{
    private static final Logger LOG = LogManager.getLogger( CIA8520Test.class.getName() );

    private CIA8520 cia;
    private Runnable irqListener = () -> {};

    @Override
    protected void setUp()
    {
        final DMAController dmaCtrl = new DMAController();
        final Blitter blitter = new Blitter( dmaCtrl );
        final Amiga amiga = Amiga.AMIGA_500;
        final Video video = new Video(amiga,blitter,dmaCtrl);
        final MMU.PageFaultHandler faultHandler = new MMU.PageFaultHandler( amiga, blitter, video );
        final MMU mmu = new MMU( faultHandler );
        final Memory memory = new Memory(mmu);
        blitter.setMemory( memory );
        video.setMemory( memory );
        final CPU cpu = new CPU(CPUType.M68000, memory);
        final IRQController irqController = new IRQController(cpu)
        {
            @Override
            public void externalInterrupt(CIA8520 cia)
            {
                irqListener.run();
            }
        };
        blitter.setIRQController( irqController );
        faultHandler.setIRQController( irqController );
        cia = new CIA8520(CIA8520.Name.CIAA, amiga, irqController);
    }

    public void testSerialInput()
    {
        final int[] irqCnt = {0};
        irqListener = () -> {
            irqCnt[0]++;
        };

        // serial output rate is timerARate / 2
        loadTimerA(2 );

        // enable serial IRQ
        change(CIA8520.REG_IRQ_CTRL).set(CIA8520.ICR_SETCLR|CIA8520.ICR_SP).apply();

        // assert that the serial pin is set to HIGH
        // when entering serial output mode
        assertTrue(cia.readSerialPin());

        final int data = 0b10110111;

        int mask = 1<<7;
        for ( int i = 0 ; i < 8 ; i++, mask >>>= 1 ) {
            cia.setCntIn(false);
            cia.tick();
            cia.setSerialPin( (data & mask) != 0);
            cia.setCntIn(true);
            cia.tick();
        }
        assertEquals(1,irqCnt[0]);
        int irqs = cia.readRegister(CIA8520.REG_IRQ_CTRL);
        assertEquals( "Got "+Misc.binary8Bit(irqs),CIA8520.ICR_SETCLR|CIA8520.ICR_SP, irqs );
        final int read = cia.readRegister(CIA8520.REG_SERIAL_DATA);
        assertEquals("Expected "+ Misc.binary8Bit(data)+" but got "+Misc.binary8Bit(read),data,read);
    }

    public void testSerialOutput()
    {
        // serial output rate is timerARate / 2
        loadTimerA(2 );
        change(CIA8520.REG_IRQ_CTRL).value(CIA8520.ICR_SETCLR|CIA8520.ICR_SP).apply();

        // enable serial output mode, start timer
        change(CIA8520.REG_CTRLA).set(CIA8520.CTRL_SPMODE|CIA8520.CTRL_START).apply();

        // assert that the serial pin is set to LOW
        // when entering serial output mode
        assertFalse(cia.readSerialPin());

        final int data = 0b10110101;
        change(CIA8520.REG_SERIAL_DATA).value(data).apply();

        final BusSpy spy = new BusSpy(cia.getBus(),1024);
        // spy.show();
        int read = 0;
        // output is at half the timerA rate => one bit every 4 ticks
        for ( int i = 0 ; i < 8 ; i++ )
        {
            cia.tick(); spy.sampleAndRepaint();
            // timerA = 1
            cia.tick(); spy.sampleAndRepaint();
//            assertTrue( cia.readCnt() );
            // timerA = 2 ==> BIT OUT
            read <<= 1;
            if ( cia.readSerialPin() ) {
                read |= 1;
            }
            LOG.info( "READ: "+(read&1) );

            cia.tick(); spy.sampleAndRepaint();
            cia.tick(); spy.sampleAndRepaint();
//            assertFalse( cia.readCnt() );
            cia.tick(); spy.sampleAndRepaint();
            cia.tick(); spy.sampleAndRepaint();
            cia.tick(); spy.sampleAndRepaint();
            cia.tick(); spy.sampleAndRepaint();
        }
        int irqs = cia.readRegister(CIA8520.REG_IRQ_CTRL);
        assertTrue( (irqs & (CIA8520.ICR_SETCLR|CIA8520.ICR_SP)) == (CIA8520.ICR_SETCLR|CIA8520.ICR_SP) );
        assertEquals("Expected "+ Misc.binary8Bit(data)+" but got "+Misc.binary8Bit(read),data,read);
    }

    public void testTimerBContinuous()
    {
        loadTimerB(2 );

        final int[] irqCnt = {0};
        irqListener = () -> {
            irqCnt[0]++;
        };

        // enable IRQ Timer B
        change(CIA8520.REG_IRQ_CTRL).set(CIA8520.ICR_SETCLR|CIA8520.ICR_TB).apply();

        // start timer
        change(CIA8520.REG_CTRLB).set(CIA8520.CTRL_START).apply();

        // run test;
        cia.tick();
        cia.tick();
        cia.tick();
        cia.tick();
        cia.tick();

        // verify
        assertEquals(2, irqCnt[0]);
    }

    public void testTimerBOneShot()
    {
        loadTimerB(2 );

        final int[] irqCnt = {0};
        irqListener = () -> {
            irqCnt[0]++;
        };

        // enable IRQ Timer A
        change(CIA8520.REG_IRQ_CTRL).set(CIA8520.ICR_SETCLR|CIA8520.ICR_TB).apply();

        // start timer in one-shot mode
        change(CIA8520.REG_CTRLB).set(CIA8520.CTRL_START).set(CIA8520.CTRL_RUNMODE).apply();

        // run test;
        cia.tick();
        cia.tick(); // irq
        cia.tick();
        cia.tick();  // irq
        cia.tick();

        // verify
        assertEquals(1, irqCnt[0]);
    }

    public void testTimerAContinuous()
    {
        loadTimerA(2 );

        final int[] irqCnt = {0};
        irqListener = () -> {
            irqCnt[0]++;
        };

        // enable IRQ Timer A
        change(CIA8520.REG_IRQ_CTRL).set(CIA8520.ICR_SETCLR|CIA8520.ICR_TA).apply();

        // start timer
        change(CIA8520.REG_CTRLA).set(CIA8520.CTRL_START).apply();

        // run test;
        cia.tick();
        cia.tick();
        cia.tick();
        cia.tick();
        cia.tick();

        // verify
        assertEquals(2, irqCnt[0]);
    }

    public void testTimerAOneShot()
    {
        loadTimerA(1 );

        final int[] irqCnt = {0};
        irqListener = () -> {
            irqCnt[0]++;
        };

        // enable IRQ Timer A
        change(CIA8520.REG_IRQ_CTRL).set(CIA8520.ICR_SETCLR|CIA8520.ICR_TA).apply();

        // start timer in one-shot mode
        change(CIA8520.REG_CTRLA).set(CIA8520.CTRL_START).set(CIA8520.CTRL_RUNMODE).apply();

        // run test;
        cia.tick();
        cia.tick();
        cia.tick();
        cia.tick();
        cia.tick();

        // verify
        assertEquals(1, irqCnt[0]);
    }

    protected final class BitHelper
    {
        private final int register;
        public int value;

        public BitHelper(int register)
        {
            this.value = cia.readRegister(register );
            this.register = register;
        }

        public BitHelper set(int bitsToSet)
        {
            this.value |= bitsToSet;
            return this;
        }

        public BitHelper value(int value)
        {
            this.value = value;
            return this;
        }

        public void apply() {
            cia.writeRegister(register, value );
        }

        public BitHelper clear(int bitsToSet)
        {
            this.value &= ~bitsToSet;
            return this;
        }
    }

    private int readTimerA()
    {
        int lo = cia.readRegister(CIA8520.REG_TIMERA_LO);
        int hi = cia.readRegister(CIA8520.REG_TIMERA_HI);
        return hi<<8|lo;
    }

    private int readTimerB()
    {
        int lo = cia.readRegister(CIA8520.REG_TIMERB_LO);
        int hi = cia.readRegister(CIA8520.REG_TIMERB_HI);
        return hi<<8|lo;
    }

    private void loadTimerA(int value)
    {
        // order is important here
        cia.writeRegister(CIA8520.REG_TIMERA_LO, value & 0xff );
        cia.writeRegister(CIA8520.REG_TIMERA_HI, (value & 0xff00)>>8 );
    }

    private void loadTimerB(int value)
    {
        // order is important here
        cia.writeRegister(CIA8520.REG_TIMERB_LO, value & 0xff );
        cia.writeRegister(CIA8520.REG_TIMERB_HI, (value & 0xff00)>>8 );
    }

    private BitHelper change(int register)
    {
        return new BitHelper(register);
    }

    private void set(int register,int bitsToSet)
    {
        new BitHelper(register).set(bitsToSet).apply();
    }

    private void clear(int register,int bitsToSet)
    {
        new BitHelper(register).clear(bitsToSet).apply();
    }
}
