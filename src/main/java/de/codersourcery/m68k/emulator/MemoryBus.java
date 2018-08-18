package de.codersourcery.m68k.emulator;

public class MemoryBus
{
    private final Memory memory;

    private boolean isFree;

    private int address;
    private int value;

    private IReader reader;
    private IWriter writer;

    public static final int EP_DENISE = 1<<0;
    public static final int EP_PAULA = 1<<1;
    public static final int EP_AGNUS = 1<<2;
    public static final int EP_CPU = 1<<3;

    public abstract class IReader
    {
        public abstract void dataReady(int value);
    }

    public abstract class IWriter
    {
        public abstract void writeCompleted();
    }

    private int delay;

    public MemoryBus(Memory memory) {
        this.memory = memory;
    }

    public boolean isFree() {
        return isFree;
    }

    public void write(int address, int value,IWriter writer)
    {
        this.isFree = false;
        this.address = address;
        this.value = value;
        this.delay = 2;
        this.writer = writer;
    }

    public void read(int address, IReader reader)
    {
        this.isFree = false;
        this.address = address;
        this.delay = 2;
        this.reader = reader;
    }

    public void tick()
    {
        if ( ! isFree )
        {
            delay--;
            if ( delay == 0 )
            {
                if ( reader != null )
                {
                    try
                    {
                        final int tmp = memory.readWord(address);
                        reader.dataReady(tmp);
                    }
                    finally
                    {
                        reader = null;
                    }
                }
                else
                {
                    try
                    {
                        memory.writeWord(address, value);
                        writer.writeCompleted();
                    }
                    finally
                    {
                        writer = null;
                    }
                }
                isFree = true;
            }
        }
    }
}
