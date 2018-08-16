package de.codersourcery.m68k.emulator;

import org.apache.commons.lang3.Validate;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class Emulator
{
    private enum CommandType
    {
        START,STOP,RESET,SINGLE_STEP,CALLBACK
    }

    private enum EmulatorMode
    {
        RUNNING,STOPPED;
    }

    private static class EmulatorCommand
    {
        public final CommandType type;
        public final CountDownLatch ack = new CountDownLatch(1);

        public EmulatorCommand(CommandType type) {
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

    private final byte[] kickstartRom;

    private final Amiga amiga;
    private final MMU mmu;
    private final Memory memory;
    private final CPU cpu;

    private final Object EMULATOR_LOCK = new Object();

    // @GuardedBy(EMULATOR_LOCK)
    private EmulatorThread emulatorThread;

    private BlockingQueue<EmulatorCommand> cmdQueue = new ArrayBlockingQueue<>(1000,true);

    public Emulator(Amiga amiga,byte[] kickstartRom)
    {
        this.amiga = amiga;

        this.kickstartRom = kickstartRom;
        if ( kickstartRom.length != amiga.getKickRomSize() ) {
            throw new IllegalArgumentException("Kickstart ROM needs to have "+amiga.getKickRomSize()+" bytes");
        }

        final MMU.PageFaultHandler faultHandler = new MMU.PageFaultHandler();
        this.mmu = new MMU( faultHandler );
        this.memory = new Memory(this.mmu);
        this.cpu = new CPU(amiga.getCPUType(),memory);
    }

    public void runOnThread(Runnable r)
    {
        sendCommand( new EmulatorCallback(t -> r.run() ) );
    }

    public void reset()
    {
        sendCommand( new EmulatorCommand(CommandType.RESET ) );
    }

    public void start()
    {
        sendCommand( new EmulatorCommand(CommandType.START ) );
    }

    public void stop()
    {
        sendCommand( new EmulatorCommand(CommandType.STOP ) );
    }

    public void singleStep()
    {
        sendCommand( new EmulatorCommand(CommandType.SINGLE_STEP ) );
    }

    public boolean hasMode(EmulatorMode mode)
    {
        return mode.equals( getMode() );
    }

    public EmulatorMode getMode()
    {
        final AtomicReference<EmulatorMode> result = new AtomicReference<>();
        sendCommand( new EmulatorCallback(thread -> result.set( thread.mode ) ) , true );
        return result.get();
    }


    private void sendCommand(EmulatorCommand cmd) {
        sendCommand(cmd,false);
    }

    private void sendCommand(EmulatorCommand cmd,boolean waitForCompletion)
    {
        Validate.notNull(cmd, "cmd must not be null");
        cmdQueue.add( cmd );
        synchronized (EMULATOR_LOCK)
        {
            if ( emulatorThread == null || ! emulatorThread.isAlive() ) {
                emulatorThread = new EmulatorThread();
                emulatorThread.start();
            }
            emulatorThread.wakeup();
        }
        if ( waitForCompletion )
        {
            try
            {
                cmd.ack.await();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                Thread.interrupted();
            }
        }
    }

    private class EmulatorThread extends Thread
    {
        private final Object STOP_LOCK = new Object();

        private EmulatorMode mode = EmulatorMode.STOPPED;

        {
            setName("emulator-thread");
            setDaemon(true);
            doReset();
        }

        private void waitForCommand()
        {
            while ( cmdQueue.peek() == null )
            {
                System.out.println("Emulator is going to sleep");
                synchronized(STOP_LOCK)
                {
                    try
                    {
                        STOP_LOCK.wait();
                        System.out.println("Emulator woke up");
                    }
                    catch (InterruptedException e)
                    {
                    }
                }
            }
        }

        public void wakeup()
        {
            synchronized(STOP_LOCK)
            {
                STOP_LOCK.notifyAll();
            }
        }

        private void doReset()
        {
            // reset MMU, unmapping all pages
            mmu.reset();

            // copy kickstart rom into RAM
            memory.bulkWrite(amiga.getKickRomStartAddress(),kickstartRom,0,kickstartRom.length);

            // write-protect kickstart ROM
            mmu.setWriteProtection( amiga.getKickRomStartAddress(),kickstartRom.length,true);

            // copy first 1 KB from ROM to IRQ vectors starting at 0x00
            memory.bulkWrite(0x000000,kickstartRom,0,1024);

            cpu.reset();

            mode = EmulatorMode.STOPPED;
        }

        @Override
        public void run()
        {
            try
            {
                internalRun();
            }
            finally {
                System.err.println("Emulator thread died unexpectedly.");
                Thread t = new Thread( () ->
                {
                    try {
                        System.err.println("Restarting emulator thread in 10 seconds");
                        Thread.sleep(10*1000 );
                    } catch(Exception e) {
                        e.printStackTrace();;
                    }
                    synchronized( EMULATOR_LOCK)
                    {
                        if ( emulatorThread == null || ! emulatorThread.isAlive() )
                        {
                            System.err.println("Restarting emulator thread");
                            emulatorThread = new EmulatorThread();
                            emulatorThread.start();
                        } else {
                            System.err.println("Emulator thread already running");
                        }
                    }
                });
                t.setDaemon(true);
                t.start();
            }
        }

        public void internalRun()
        {
            while ( true )
            {
                EmulatorCommand cmd = cmdQueue.poll();
                if ( cmd != null )
                {
                    switch( cmd.type) {

                        case START:
                            mode = EmulatorMode.RUNNING;
                            break;
                        case STOP:
                            mode = EmulatorMode.STOPPED;
                            break;
                        case RESET:
                            doReset();
                            break;
                        case SINGLE_STEP:
                            mode = EmulatorMode.STOPPED;
                            cpu.executeOneInstruction();
                            break;
                        case CALLBACK:
                            ((EmulatorCallback) cmd).callback.accept(this);
                            break;
                        default:
                            throw new RuntimeException("Unhandled command: "+cmd);
                    }
                    cmd.ack.countDown();
                    continue;
                }
                if ( mode == EmulatorMode.RUNNING) {
                    cpu.executeOneCycle();
                } else {
                    waitForCommand();
                }
            }
        }
    }
}