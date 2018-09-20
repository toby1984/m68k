package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.CPU;
import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.utils.Misc;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CPUStateWindow extends AppWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    private final Object DATA_LOCK = new  Object();

    // @GuardedBy( DATA_LOCK )
    private final List<JCheckBox> cpuFlagCheckboxes = new ArrayList<>();

    // @GuardedBy( DATA_LOCK )
    private final List<JTextField> dataRegistersTextfields = new ArrayList<>();

    // @GuardedBy( DATA_LOCK )
    private final List<JTextField> addressRegistersTextfields = new ArrayList<>();

    // @GuardedBy( DATA_LOCK )
    private JTextField pcTextfield;

    // @GuardedBy( DATA_LOCK )
    private int pc;

    // @GuardedBy( DATA_LOCK )
    private int flags;

    // @GuardedBy( DATA_LOCK )
    private final int[] dataRegisters = new int[8];

    // @GuardedBy( DATA_LOCK )
    private final int[] addressRegisters = new int[8];

    private static JTextField createTextField(Consumer<JTextField> listener)
    {
        final JTextField result = new JTextField("00000000");
        result.setColumns( 8 );
        result.setHorizontalAlignment( JTextField.RIGHT );
        result.addActionListener(ev ->
        {
            listener.accept(result);
        });
        return result;
    }

    private void doWithNumber(JTextField tf, Consumer<Integer>c)
    {
        final String text = tf.getText();
        try
        {
            if ( text != null && text.trim().length() > 0 )
            {
                final int value = Integer.parseInt(text, 16);
                c.accept(value );
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    private void updateDataRegister(int regNum,JTextField tf)
    {
        doWithNumber(tf, adr ->
        {
            System.out.println("Set[ D"+regNum+"] = "+adr);
            ui.doWithEmulator(emu ->
            {
                emu.cpu.dataRegisters[regNum] = adr;
                emu.invokeTickCallback();
            });
        });
    }

    private void updateAddressRegister(int regNum,JTextField tf)
    {
        doWithNumber(tf, adr ->
        {
            System.out.println("Set[ A"+regNum+"] = "+adr);
            ui.doWithEmulator(emu ->
            {
                emu.cpu.addressRegisters[regNum] = adr;
                emu.invokeTickCallback();
            });
        });
    }

    private void updateCPUFlag(int flag,JCheckBox cb)
    {
        boolean isSet = cb.isSelected();
        ui.doWithEmulator(emu ->
        {
            System.out.println((isSet ?"Setting":"Clearing")+" flag "+flag);
            if ( isSet ) {
                emu.cpu.setFlags(flag);
            } else {
                emu.cpu.clearFlags(flag);
            }
            emu.invokeTickCallback();
        });
    }

    private static String hex(int value)
    {
        return Integer.toHexString( value );
    }

    private JCheckBox createCheckbox(String label, Consumer<JCheckBox> cb)
    {
        final JCheckBox result = new JCheckBox( label ,false );
        result.addActionListener(ev -> {
            cb.accept(result);
        });
        return result;
    }

    public CPUStateWindow(UI ui)
    {
        super( "CPU State", ui );
        setPreferredSize( new Dimension(600,200 ) );
        getContentPane().setLayout( new GridBagLayout() );
        for ( int i = 0 ; i < 8 ; i++ )
        {
            final int regNum = i;
            dataRegistersTextfields.add( createTextField( tf -> updateDataRegister(regNum,tf) ) );
            addressRegistersTextfields.add( createTextField( tf -> updateAddressRegister(regNum,tf)) );
        }

        final int[] flagMasks =
        {
            CPU.FLAG_CARRY,
            CPU.FLAG_OVERFLOW,
            CPU.FLAG_ZERO,
            CPU.FLAG_NEGATIVE,
            CPU.FLAG_EXTENDED,
            CPU.FLAG_I0,
            CPU.FLAG_I1,
            CPU.FLAG_I2,
            CPU.FLAG_MASTER_INTERRUPT,
            CPU.FLAG_SUPERVISOR_MODE,
            CPU.FLAG_T0,
            CPU.FLAG_T1
        };
        final String[] checkboxLabels = {"C","V","Z","N","X","I0","I1","I2","Master-IRQ","S","T0","T1"};
        for ( int i = 0 ; i < checkboxLabels.length ; i++ )
        {
            final int maskBits = flagMasks[i];
            cpuFlagCheckboxes.add( createCheckbox( checkboxLabels[i],cb -> updateCPUFlag(maskBits,cb)) );
        }

        // add PC
        pcTextfield = createTextField( tf -> doWithNumber(tf, adr ->
        {
            ui.doWithEmulator(emu ->
            {
                System.out.println("Setting PC to "+ Misc.hex(adr&~1));
                emu.cpu.cycles = 1;
                emu.cpu.pc = (adr & ~1);
                emu.invokeTickCallback();
            });
        }));
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
    public WindowKey getWindowKey()
    {
        return WindowKey.CPU_STATE;
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

    private void updateUIState(boolean enableInput) {

        runOnEDT(() ->
        {
            dataRegistersTextfields.forEach( x -> x.setEditable(enableInput));
            addressRegistersTextfields.forEach( x -> x.setEditable(enableInput));
            cpuFlagCheckboxes.forEach(cb -> cb.setEnabled(enableInput) );
            pcTextfield.setEditable(enableInput);
        });
    }

    @Override
    public void stopped(Emulator emulator)
    {
        tick(emulator);
        updateUIState(true);
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
        tick(emulator);
        updateUIState(true);
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
        updateUIState(false);
    }
}
