package de.codersourcery.m68k.disassembler;

import de.codersourcery.m68k.emulator.MMU;
import de.codersourcery.m68k.emulator.Memory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main
{
    public static void main(String[] args) throws IOException
    {
        final File kickRom = new File("/home/tgierke/intellij_workspace/m68k/Kickstart1.2.rom");

        final byte[] data = Files.readAllBytes(kickRom.toPath() );
        final Memory memory = new Memory( new MMU(new MMU.PageFaultHandler() ) );
        memory.bulkWrite(0,data,0,1024);

        memory.bulkWrite(0xF80000,data,0,data.length);
        final Disassembler dis = new Disassembler(memory );
        System.out.println("IRQ vectors");
        System.out.println( dis.disassemble(0, 8 ) );
        System.out.println("Reset routine");
        System.out.println("Offset in file: "+(0xfc00d2-0xF80000));
        System.out.println( dis.disassemble(0xfc00d2, 1024) );
    }
}
