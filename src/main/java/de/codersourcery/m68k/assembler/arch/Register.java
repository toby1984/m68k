package de.codersourcery.m68k.assembler.arch;

public enum Register
{
    D0("d0",Type.DATA,0,true),
    D1("d1",Type.DATA,1,true),
    D2("d2",Type.DATA,2,true),
    D3("d3",Type.DATA,3,true),
    D4("d4",Type.DATA,4,true),
    D5("d5",Type.DATA,5,true),
    D6("d6",Type.DATA,6,true),
    D7("d7",Type.DATA,7,true),
    //
    A0("a0",Type.ADDRESS,0,true),
    A1("a1",Type.ADDRESS,1,true),
    A2("a2",Type.ADDRESS,2,true),
    A3("a3",Type.ADDRESS,3,true),
    A4("a4",Type.ADDRESS,4,true),
    A5("a5",Type.ADDRESS,5,true),
    A6("a6",Type.ADDRESS,6,true),
    A7("a7",Type.ADDRESS,Type.STACKPTR,7,true),
    PC("pc",Type.PC,0,false),
    SR("sr",Type.SR,0,false),
    CCR("ccr",Type.CCR,0,false);

    public final String name;
    public final Type type1;
    public final Type type2;
    public final int bits;
    public final boolean supportsOperandSizeSpec; // whether R.w / R.l syntax is allowed

    private Register(String name,Type type1,int index,boolean supportsOperandSizeSpec) {
        this(name,type1,null,index,supportsOperandSizeSpec);
    }

    private Register(String name,Type type1,Type type2,int index,boolean supportsOperandSizeSpec) {
        this.name = name;
        this.type1 = type1;
        this.type2 = type2;
        this.bits = index;
        this.supportsOperandSizeSpec = supportsOperandSizeSpec;
    }

    public boolean isData()
    {
        switch(this) {

            case D0:
            case D1:
            case D2:
            case D3:
            case D4:
            case D5:
            case D6:
            case D7:
                return true;
            default:
                return false;
        }
    }

    public boolean isAddress()
    {
        switch(this)
        {
            case A0:
            case A1:
            case A2:
            case A3:
            case A4:
            case A5:
            case A6:
            case A7:
                return true;
            default:
                return false;
        }
    }

    public static enum Type {
        DATA,
        ADDRESS,
        STACKPTR,
        SR,
        PC,CCR;
    }

    public static Register parseRegister(String s)
    {
        if ( s != null )
        {
            switch (s)
            {
                case "d0": case "D0": return Register.D0;
                case "d1": case "D1": return Register.D1;
                case "d2": case "D2": return Register.D2;
                case "d3": case "D3": return Register.D3;
                case "d4": case "D4": return Register.D4;
                case "d5": case "D5": return Register.D5;
                case "d6": case "D6": return Register.D6;
                case "d7": case "D7": return Register.D7;
                case "a0": case "A0": return Register.A0;
                case "a1": case "A1": return Register.A1;
                case "a2": case "A2": return Register.A2;
                case "a3": case "A3": return Register.A3;
                case "a4": case "A4": return Register.A4;
                case "a5": case "A5": return Register.A5;
                case "a6": case "A6": return Register.A6;
                case "a7": case "A7": return Register.A7;
                case "ccr": case "CCR": return Register.CCR;
                case "sr": case "SR": return Register.SR;
                case "sp": case "SP": case "sP": case "Sp": return Register.A7;
                case "pc": case "PC": case "pC": case "Pc": return Register.PC;
            }
        }
        return null;
    }
}
