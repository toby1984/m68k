package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.CPU;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.utils.Misc;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CPUStateWindow extends AppWindow implements ITickListener
{
    private final Object DATA_LOCK = new  Object();

    // @GuardedBy( DATA_LOCK )
    private final List<JCheckBox> cpuFlagCheckboxes =
            new ArrayList<>();

    // @GuardedBy( DATA_LOCK )
    private final List<JTextField> dataRegistersTextfields =
            new ArrayList<>();

    // @GuardedBy( DATA_LOCK )
    private final List<JTextField> addressRegistersTextfields =
            new ArrayList<>();

    // @GuardedBy( DATA_LOCK )
    private JTextField pcTextfield = new JTextField("");

    // @GuardedBy( DATA_LOCK )
    private int pc;

    // @GuardedBy( DATA_LOCK )
    private int flags;
    // @GuardedBy( DATA_LOCK )
    private final int[] dataRegisters = new int[8];
    // @GuardedBy( DATA_LOCK )
    private final int[] addressRegisters = new int[8];

    private static JTextField createTextField()  {
        final JTextField result = new JTextField("00000000");
        result.setColumns( 8 );
        result.setHorizontalAlignment( JTextField.RIGHT );
        return result;
    }

    private static String hex(int value)
    {
        return Integer.toHexString( value );
    }

    public CPUStateWindow(String title, UI ui)
    {
        super( title, ui );
        setPreferredSize( new Dimension(600,200 ) );
        getContentPane().setLayout( new GridBagLayout() );
        for ( int i = 0 ; i < 8 ; i++ ) {
            dataRegistersTextfields.add( createTextField() );
            addressRegistersTextfields.add( createTextField() );
        }

        final String[] checkboxLabels = {"C","V","Z","N","X","I0","I1","I2","Master-IRQ","S","T0","T1"};
        for ( int i = 0 ; i < checkboxLabels.length ; i++ ) {
            cpuFlagCheckboxes.add( new JCheckBox( checkboxLabels[i],false ) );
        }

        // add PC
        pcTextfield = createTextField();
        GridBagConstraints cnstrs = cnstrsNoResize( 0, 0 );
        cnstrs.gridwidth = 1;
        getContentPane().add( pcTextfield, cnstrs );

        // add CPU state checkboxes
        final JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout( new FlowLayout() );
        for ( int i = 0 ; i < checkboxLabels.length ; i++ ) {
            checkboxPanel.add( cpuFlagCheckboxes.get(i) );
        }
        cnstrs = cnstrs(0,1);
        cnstrs.gridwidth = 1;
        cnstrs.weightx = 1;
        cnstrs.weighty = 0.5;
        cnstrs.fill= GridBagConstraints.BOTH;
        getContentPane().add( checkboxPanel, cnstrs );

        final JPanel registerPanel = new JPanel();
        registerPanel.setLayout(  new GridBagLayout() );
        int y = 0;
        for ( int i = 0 ; i < 8 ; i+=2 ) {
            registerPanel.add( new JLabel("D"+i), cnstrsNoResize( 0, y ) );
            registerPanel.add( dataRegistersTextfields.get(i) , cnstrsNoResize( 1, y ));
            registerPanel.add( new JLabel("D"+(i+1)), cnstrsNoResize( 2, y ) );
            registerPanel.add( dataRegistersTextfields.get(i+1) , cnstrsNoResize( 3, y ));
            registerPanel.add( new JLabel("A"+i), cnstrsNoResize( 4, y ) );
            registerPanel.add( addressRegistersTextfields.get(i) , cnstrsNoResize( 5, y ));
            registerPanel.add( new JLabel("A"+(i+1)), cnstrsNoResize( 6, y ) );
            registerPanel.add( addressRegistersTextfields.get(i+1) , cnstrsNoResize( 7, y ));
            y += 1;
        }
        cnstrs = cnstrs(0,2);
        cnstrs.gridwidth = 1;
        cnstrs.weightx = 1;
        cnstrs.weighty = 0.5;
        cnstrs.fill= GridBagConstraints.BOTH;
        getContentPane().add( registerPanel, cnstrs );
    }

    @Override
    public String getWindowKey()
    {
        return "cpustate";
    }

    @Override
    public void tick(Emulator emulator)
    {
        synchronized(DATA_LOCK)
        {
            pc = emulator.cpu.pc;
            flags = emulator.cpu.statusRegister;
            for (int i = 0; i < 8; i++)
            {
                dataRegisters[i] = emulator.cpu.dataRegisters[i];
                addressRegisters[i] = emulator.cpu.addressRegisters[i];
            }
        }
        SwingUtilities.invokeLater( () ->
        {
            synchronized( DATA_LOCK)
            {
                cpuFlagCheckboxes.get( 0 ).setSelected( (flags & CPU.FLAG_CARRY) != 0 );
                cpuFlagCheckboxes.get( 1 ).setSelected( (flags & CPU.FLAG_OVERFLOW) != 0 );
                cpuFlagCheckboxes.get( 2 ).setSelected( (flags & CPU.FLAG_ZERO) != 0 );
                cpuFlagCheckboxes.get( 3 ).setSelected( (flags & CPU.FLAG_NEGATIVE) != 0 );
                cpuFlagCheckboxes.get( 4 ).setSelected( (flags & CPU.FLAG_EXTENDED) != 0 );
                cpuFlagCheckboxes.get( 5 ).setSelected( (flags & CPU.FLAG_I0) != 0 );
                cpuFlagCheckboxes.get( 6 ).setSelected( (flags & CPU.FLAG_I1) != 0 );
                cpuFlagCheckboxes.get( 7 ).setSelected( (flags & CPU.FLAG_I2) != 0 );
                cpuFlagCheckboxes.get( 8 ).setSelected( (flags & CPU.FLAG_MASTER_INTERRUPT) != 0 );
                cpuFlagCheckboxes.get( 9 ).setSelected( (flags & CPU.FLAG_SUPERVISOR_MODE) != 0 );
                cpuFlagCheckboxes.get( 10 ).setSelected( (flags & CPU.FLAG_T0) != 0 );
                cpuFlagCheckboxes.get( 11 ).setSelected( (flags & CPU.FLAG_T1) != 0 );
                for (int i = 0; i < 8; i++)
                {
                    dataRegistersTextfields.get( i ).setText( hex( dataRegisters[i] ) );
                    addressRegistersTextfields.get( i ).setText( hex( addressRegisters[i] ) );
                }
                pcTextfield.setText( hex( pc ) );
            }
        });
    }
}
