package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.disassembler.Disassembler;
import de.codesourcery.m68k.disassembler.LibraryCallResolver;
import de.codesourcery.m68k.disassembler.RegisterDescription;

public abstract class AbstractDisassemblyWindow extends AppWindow
{
    private LibraryCallResolver libraryCallResolver;

    private Disassembler.IChipRegisterResolver registerResolver;

    protected final Disassembler.IIndirectCallResolver proxyCallResolver = new Disassembler.IIndirectCallResolver() {

        @Override
        public Disassembler.FunctionDescription resolve(int addressRegister, int offset)
        {
            return libraryCallResolver == null ? null : libraryCallResolver.resolve(addressRegister,offset);
        }
    };

    protected final Disassembler.IChipRegisterResolver proxyRegisterResolver = new Disassembler.IChipRegisterResolver()
    {
        @Override
        public RegisterDescription resolve(int addressRegister, int offset)
        {
            return registerResolver == null ? null : registerResolver.resolve(addressRegister,offset);
        }

        @Override
        public RegisterDescription resolve(int address)
        {
            return registerResolver == null ? null : registerResolver.resolve(address);
        }
    };

    public AbstractDisassemblyWindow(String title, UI ui)
    {
        super(title, ui);
    }

    public final void setLibraryCallResolver(LibraryCallResolver libraryCallResolver)
    {
        this.libraryCallResolver = libraryCallResolver;
    }

    public final void setChipRegisterResolver(Disassembler.IChipRegisterResolver resolver)
    {
        this.registerResolver = resolver;
    }
}
