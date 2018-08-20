package de.codersourcery.m68k.utils;

import org.apache.commons.lang3.Validate;

import java.util.List;

public interface IBus
{
    final class Pin {

        public final String name;
        public final int number;

        public Pin(String name,int number)
        {
            Validate.notBlank( name, "name must not be null or blank");
            if ( number < 0 ) {
                throw new IllegalArgumentException("Pins need to have numbers >= 0");
            }
            this.name = name;
            this.number = number;
        }

        @Override
        public String toString()
        {
            return name+"("+number+")";
        }
    }

    String getName();

    Pin[] getPins();

    boolean readPin(Pin pin);
}