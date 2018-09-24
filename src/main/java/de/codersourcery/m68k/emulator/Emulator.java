package de.codersourcery.m68k.emulator;

import de.codersourcery.m68k.disassembler.Disassembler;
import de.codersourcery.m68k.emulator.chips.CIA8520;
import de.codersourcery.m68k.emulator.chips.IRQController;
import de.codersourcery.m68k.emulator.memory.Blitter;
import de.codersourcery.m68k.emulator.memory.DMAController;
import de.codersourcery.m68k.emulator.memory.MMU;
import de.codersourcery.m68k.emulator.memory.Memory;
import de.codersourcery.m68k.emulator.memory.Video;
import de.codersourcery.m68k.emulator.ui.ITickListener;
import de.codersourcery.m68k.utils.Misc;
import org.apache.commons.lang3.Validate;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class Emulator
{
    /**
     * Called whenever the emulation changes state.
     *
     * @author tobias.gierke@code-sourcery.de
     */
    public interface IEmulatorStateCallback
    {
        /**
         * Called right after the emulation stopped
         * after having run in continous (non single-step) mode.
         */
        void stopped(Emulator emulator);

        /**
         * Called right after single-stepping an instruction has finished.
         */
        void singleStepFinished(Emulator emulator);

        /**
         * Called right before the emulation starts to run
         * on continous mode.
         */
        void enteredContinousMode(Emulator emulator);
    }

    private enum CommandType
    {
        START, STOP, RESET, SINGLE_STEP, CALLBACK, DESTROY
    }

    private enum EmulatorMode
    {
        RUNNING, STOPPED;
    }

    private static class EmulatorCommand
    {
        public final CommandType type;
        public final CountDownLatch ack = new CountDownLatch(1);

        public EmulatorCommand(CommandType type)
        {
            this.type = type;
        }
    }

    private static class EmulatorCallback extends EmulatorCommand
    {
        public final Consumer<EmulatorThread> callback;

        public EmulatorCallback(Consumer<EmulatorThread> callback)
        {
            super(CommandType.CALLBACK);
            this.callback = callback;
        }
    }

    private final Breakpoints breakpoints = new Breakpoints();

    // after how many ticks the callback should get invoked
    public final byte[] kickstartRom;
    public final Amiga amiga;
    public final MMU mmu;
    public final DMAController dmaController;
    public final Blitter blitter;
    public final Video video;
    public final Memory memory;
    public final CPU cpu;
    public final CIA8520 ciaa;
    public final CIA8520 ciab;
    public final IRQController irqController;

    private final Object EMULATOR_LOCK = new Object();

    // @GuardedBy(EMULATOR_LOCK)
    private EmulatorThread emulatorThread;

    private final Object CMD_QUEUE_LOCK = new Object();

    // @GuardedBy( CMD_QUEUE_LOCK );
    private BlockingQueue<EmulatorCommand> commandQueue = new ArrayBlockingQueue<>(1000, true);

    public Emulator(Amiga amiga, byte[] kickstartRom)
    {
        this.amiga = amiga;

        this.kickstartRom = kickstartRom;
        if (kickstartRom.length != amiga.getKickRomSize())
        {
            throw new IllegalArgumentException("Kickstart ROM for "+amiga+" needs to have " + amiga.getKickRomSize() + " bytes but had "+kickstartRom.length);
        }
        this.dmaController = new DMAController();
        this.blitter = new Blitter(this.dmaController);
        this.video = new Video(amiga);
        final MMU.PageFaultHandler faultHandler = new MMU.PageFaultHandler(amiga,blitter,video);
        this.mmu = new MMU(faultHandler);
        this.memory = new Memory(this.mmu);
        video.setMemory( this.memory );
        this.blitter.setMemory( this.memory );
        this.cpu = new CPU(amiga.getCPUType(), memory);
        this.irqController = new IRQController(this.cpu);
        faultHandler.setIRQController( irqController );
        this.blitter.setIRQController( irqController );
        this.ciaa = new CIA8520(CIA8520.Name.CIAA, irqController);
        this.ciab = new CIA8520(CIA8520.Name.CIAB, irqController);
        faultHandler.setCIAA(this.ciaa);
        faultHandler.setCIAB(this.ciab);
    }

    public void destroy()
    {
        sendCommand(new EmulatorCommand(CommandType.DESTROY), true);
    }

    public void runOnThread(Runnable r, boolean waitForCompletion)
    {
        sendCommand(new EmulatorCallback(t -> r.run()), waitForCompletion);
    }

    public void reset()
    {
        sendCommand(new EmulatorCommand(CommandType.RESET));
    }

    public void start()
    {
        sendCommand(new EmulatorCommand(CommandType.START));
    }

    public void stop()
    {
        sendCommand(new EmulatorCommand(CommandType.STOP));
    }

    public void singleStep()
    {
        sendCommand(new EmulatorCommand(CommandType.SINGLE_STEP));
    }

    public boolean hasMode(EmulatorMode mode)
    {
        return mode.equals(getMode());
    }

    public EmulatorMode getMode()
    {
        final AtomicReference<EmulatorMode> result = new AtomicReference<>();
        sendCommand(new EmulatorCallback(thread -> result.set(thread.mode)), true);
        return result.get();
    }


    private void sendCommand(EmulatorCommand cmd)
    {
        sendCommand(cmd, false);
    }

    private void sendCommand(EmulatorCommand cmd, boolean waitForCompletion)
    {
        Validate.notNull(cmd, "cmd must not be null");
        synchronized (EMULATOR_LOCK)
        {
            if (emulatorThread == null || !emulatorThread.isAlive())
            {
                if ( cmd.type == CommandType.DESTROY ) {
                    return;
                }
                emulatorThread = new EmulatorThread();
                emulatorThread.start();
            }
        }

        synchronized( CMD_QUEUE_LOCK )
        {
            commandQueue.add( cmd );
            CMD_QUEUE_LOCK.notifyAll();
        }

        if (waitForCompletion)
        {
            try
            {
                cmd.ack.await();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
                Thread.interrupted();
            }
        }
    }

    private class EmulatorThread extends Thread
    {
        private int callbackInvocationTicks = 1000;
        private ITickListener callback = e -> {};
        private IEmulatorStateCallback stateCallback = new IEmulatorStateCallback()
        {
            @Override public void stopped(Emulator emulator) { }

            @Override public void singleStepFinished(Emulator emulator) { }

            @Override public void enteredContinousMode(Emulator emulator) { }
        };

        private EmulatorMode mode = EmulatorMode.STOPPED;

        {
            setName("emulator-thread");
            setDaemon(true);
            doReset();
        }

        private void waitForCommand()
        {
            while (true)
            {
                synchronized (CMD_QUEUE_LOCK)
                {
                    if ( commandQueue.peek() != null ) {
                        return;
                    }
                    try
                    {
                        CMD_QUEUE_LOCK.wait();
                    }
                    catch (InterruptedException e)
                    {
                    }
                }
            }
        }

        private void doReset()
        {
            // reset MMU, unmapping all pages
            mmu.reset();

            // copy kickstart rom into RAM
            System.out.println("Writing "+kickstartRom.length+" bytes of kickstart ROM to "+ Misc.hex(amiga.getKickRomStartAddress()));
            memory.bulkWrite(amiga.getKickRomStartAddress(), kickstartRom, 0, kickstartRom.length);

            // write-protect kickstart ROM
            mmu.setWriteProtection(amiga.getKickRomStartAddress(), kickstartRom.length, true);

            // copy first 1 KB from ROM to IRQ vectors starting at 0x00
            memory.bulkWrite(0x000000, kickstartRom, 0, 1024);

            cpu.reset();

            // clear current memory breakpoint,just in
            // case one was triggered already
            memory.breakpoints.lastHit = null;
            mode = EmulatorMode.STOPPED;
        }

        @Override
        public void run()
        {
            boolean regularTermination = false;
            try
            {
                System.out.println("Emulator thread start");
                internalRun();
                regularTermination = true;
            }
            finally
            {
                if ( regularTermination ) {
                    System.err.println( "Emulator thread finished." );
                }
                else
                {
                    System.err.println( "Emulator thread died unexpectedly." );
                    final AtomicReference<ITickListener> finalCallback = new AtomicReference<>( this.callback );
                    final AtomicReference<IEmulatorStateCallback> finalStateCallback =
                            new AtomicReference<>( this.stateCallback );
                    final AtomicInteger finalTickCnt = new AtomicInteger( this.callbackInvocationTicks );

                    final Thread t = new Thread( () ->
                    {
                        try
                        {
                            System.err.println( "Restarting emulator thread in 10 seconds" );
                            Thread.sleep( 10 * 1000 );
                        } catch (Exception e)
                        {
                            e.printStackTrace();
                            ;
                        }
                        synchronized (EMULATOR_LOCK)
                        {
                            if ( emulatorThread == null || !emulatorThread.isAlive() )
                            {
                                System.err.println( "Restarting emulator thread" );
                                emulatorThread = new EmulatorThread();
                                emulatorThread.callback = finalCallback.get();
                                emulatorThread.callbackInvocationTicks = finalTickCnt.get();
                                emulatorThread.stateCallback = finalStateCallback.get();
                                emulatorThread.start();
                            }
                            else
                            {
                                System.err.println( "Emulator thread already running" );
                            }
                        }
                    } );
                    t.setDaemon( true );
                    t.start();
                }
            }
        }

        private void printBacktrace() {

            if ( cpu.isBackTraceAvailable() )
            {
                System.err.println("=== BACKTRACE ===\n");
                Disassembler disasm = new Disassembler( memory );
                disasm.setResolveRelativeOffsets( true );
                disasm.setDumpHex( true );

                final int[] adrs = new int[ CPU.MAX_BACKTRACE_SIZE ];
                final int length = cpu.getBackTrace(adrs);
                final AtomicBoolean gotLine = new AtomicBoolean();
                for ( int i = 0 ; i < length ; i++ )
                {
                    final int adr = adrs[i];
                    gotLine.set(false);
                    final Disassembler.LineConsumer consumer = new Disassembler.LineConsumer()
                    {
                        @Override
                        public boolean stop(int pc)
                        {
                            return gotLine.get();
                        }

                        @Override
                        public void consume(Disassembler.Line line)
                        {
                            gotLine.set(true);
                            System.err.println( line.toString() );
                        }
                    };
                    disasm.disassemble( adr,consumer );
                }
                System.err.println();
            }
        }

        @SuppressWarnings( "deprecation" )
        public void internalRun()
        {
            int tickCount = 0;

            while (true)
            {
                EmulatorCommand cmd;

                synchronized (CMD_QUEUE_LOCK)
                {
                    cmd = commandQueue.poll();
                    if ( cmd == null && mode != EmulatorMode.RUNNING ) {
                        waitForCommand();
                        continue;
                    }
                }

                if (cmd != null)
                {
                    final EmulatorMode oldMode = mode;
                    switch (cmd.type)
                    {

                        case DESTROY:
                            cmd.ack.countDown();
                            return; /* terminate thread */
                        case START:
                            mode = EmulatorMode.RUNNING;
                            if (oldMode != mode)
                            {
                                stateCallback.enteredContinousMode(Emulator.this);
                            }
                            break;
                        case STOP:
                            mode = EmulatorMode.STOPPED;
                            if (oldMode != mode)
                            {
                                stateCallback.stopped(Emulator.this);
                            }
                            break;
                        case RESET:
                            doReset();
                            stateCallback.stopped(Emulator.this);
                            break;
                        case SINGLE_STEP:
                            mode = EmulatorMode.STOPPED;
                            try
                            {
                                tickCount++;
                                mmu.tick();
                                cpu.executeOneInstruction();
                            }
                            catch (Exception e)
                            {
                                printBacktrace();
                                e.printStackTrace();
                            }
                            finally
                            {
                                stateCallback.singleStepFinished(Emulator.this);
                            }
                            break;
                        case CALLBACK:
                            ((EmulatorCallback) cmd).callback.accept(this);
                            break;
                        default:
                            throw new RuntimeException("Unhandled command: " + cmd);
                    }
                    cmd.ack.countDown();
                    continue;
                }
                if (mode == EmulatorMode.RUNNING)
                {
                    try
                    {
                        tickCount++;
                        mmu.tick();
                        cpu.executeOneCycle();
                        if ((tickCount % callbackInvocationTicks) == 0)
                        {
                            callback.tick(Emulator.this);
                        }
                    }
                    catch (Exception e)
                    {
                        printBacktrace();
                        e.printStackTrace();
                        mode = EmulatorMode.STOPPED;
                        System.err.println("*** emulation stopped because of error ***");
                        stateCallback.stopped(Emulator.this);
                    }
                    final boolean cpuBreakpointHit = breakpoints.hasEnabledBreakpoints() &&
                            cpu.cycles == 1 &&
                            breakpoints.checkBreakpointHit(Emulator.this );

                    if ( cpuBreakpointHit || memory.breakpoints.lastHit != null )
                    {
                        mode = EmulatorMode.STOPPED;

                        if ( cpuBreakpointHit ) {
                            System.err.println("*** emulation stopped because of CPU breakpoint ***");
                        }
                        else
                        {
                            System.err.println( "*** emulation stopped because of memory breakpoint ***" );
                        }
                        try
                        {
                            stateCallback.stopped( Emulator.this );
                        } finally {
                            memory.breakpoints.lastHit = null;
                        }
                    }
                }
            }
        }
    }

    private void internalAsyncSendCommand(Consumer<EmulatorThread> action)
    {
        sendCommand(new EmulatorCallback(action),false);
    }

    private void internalSyncSendCommand(Consumer<EmulatorThread> action)
    {
        sendCommand(new EmulatorCallback(action),true);
    }

    /**
     * Set callback to be invoked every {@link #setCallbackInvocationTicks(int)} emulation
     * ticks.
     *
     * This callback gets invoked by the emulator thread.
     * @param cb
     */
    public void setTickCallback(final ITickListener cb)
    {
        Validate.notNull(cb, "callback must not be null");
        internalAsyncSendCommand( thread ->
        {
            thread.callback = cb;
            System.out.println("Emulator callback updated,invoking it");
            thread.callback.tick(Emulator.this );
        });
    }

    /**
     * Set callback to be invoked whenever the emulator changes state.
     *
     * This callback gets invoked by the emulator thread.
     * @param cb
     */
    public void setStateCallback(final IEmulatorStateCallback cb)
    {
        Validate.notNull(cb, "callback must not be null");
        internalAsyncSendCommand( thread -> thread.stateCallback = cb );
    }

    /**
     * Externally trigger an invocation of the emulator's tick callback.
     *
     */
    public void invokeTickCallback()
    {
        internalAsyncSendCommand( thread -> thread.callback.tick(Emulator.this ) );
    }

    public void setCallbackInvocationTicks(int callbackInvocationTicks)
    {
        if (callbackInvocationTicks < 1)
        {
            throw new IllegalArgumentException("tick count needs to be >= 1");
        }
        internalAsyncSendCommand( thread ->
        {
            thread.callbackInvocationTicks = callbackInvocationTicks;
            System.out.println("Emulator callback will be invoked every "+callbackInvocationTicks+" ticks.");
            thread.callback.tick(Emulator.this );
        });
    }

    /**
     * Returns the emulator's breakpoints.
     *
     * Breakpoints may ONLY be manipulated from inside the emulator thread.
     *
     * @return
     */
    public Breakpoints getBreakpoints()
    {
        return breakpoints;
    }
}