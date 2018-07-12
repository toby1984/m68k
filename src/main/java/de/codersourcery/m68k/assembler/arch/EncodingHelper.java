package de.codersourcery.m68k.assembler.arch;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public class EncodingHelper
{
    public static void main(String[] args) throws IllegalAccessException
    {
        final Predicate<String> pred = name -> name.contains("chk");
        final Map<String,String> entries = new TreeMap<>();
        for ( Field f : Instruction.class.getFields() )
        {
            final int mod = f.getModifiers();
            if ( Modifier.isStatic(mod) && Modifier.isFinal(mod) && f.getType() == InstructionEncoding.class )
            {
                final String name = f.getName();
                f.setAccessible(true);
                final InstructionEncoding encoding = (InstructionEncoding) f.get(null);
                final String andMask = "0b"+StringUtils.leftPad(Integer.toBinaryString(encoding.getInstructionWordAndMask()),16,'0');
                final String value = "0b"+StringUtils.leftPad(Integer.toBinaryString(encoding.getInstructionWordMask()),16,'0');
                final String s = "if ( ( insnWord & "+andMask+" ) == "+value+" ) {\n// "+name+" \n\n";
                entries.put(name,s);
            }
        }
        entries.entrySet().stream().filter( x -> pred.test( x.getKey().toLowerCase() ) ).forEach( entry -> System.out.println(entry.getValue()));
    }
}
