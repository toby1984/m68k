package de.codesourcery.m68k.emulator.ui;

import de.codesourcery.m68k.emulator.CPU;
import de.codesourcery.m68k.emulator.Emulator;
import de.codesourcery.m68k.utils.Misc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CPUStateWindow extends AppWindow implements ITickListener, Emulator.IEmulatorStateCallback
{
    private static final Logger LOG = LogManager.getLogger( CPUStateWindow.class.getName() );

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
    private JTextField statusRegister;

    // @GuardedBy( DATA_LOCK )
    private JTextField sspTextfield;

    // @GuardedBy( DATA_LOCK )
    private JTextField uspTextfield;

    // @GuardedBy( DATA_LOCK )
    private int pc;

    // @GuardedBy( DATA_LOCK )
    private int flags;

    // @GuardedBy( DATA_LOCK )
    private final int[] dataRegisters = new int[8];

    // @GuardedBy( DATA_LOCK )
    private final int[] addressRegisters = new int[8];

    private static final class PanelWithTextField {
        public final JPanel panel;
        public final JTextField textfield;

        private PanelWithTextField(JPanel panel, JTextField textfield)
        {
            this.panel = panel;
            this.textfield = textfield;
        }
    }
    private static PanelWithTextField createTextFieldWithLabel(String label,Consumer<JTextField> listener)
    {
        final JPanel panel = new JPanel();
        panel.setLayout(  new GridBagLayout() );

        GridBagConstraints cnstrs = cnstrs( 0, 0 );
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.weighty = 0;
        cnstrs.weightx = 1;
        panel.add( new JLabel(label) ,cnstrs);
        final JTextField textfield = createTextField( listener );
        cnstrs = cnstrs( 1, 0 );
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.weighty = 0;
        cnstrs.weightx = 1;
        panel.add( textfield, cnstrs );
        return new PanelWithTextField(panel,textfield);
    }

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
            LOG.info( "Set[ D"+regNum+"] = "+adr );
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
            LOG.info( "Set[ A"+regNum+"] = "+adr );
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
            LOG.info( (isSet ?"Setting":"Clearing")+" flag "+flag );
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

        // create register textfields
        for ( int i = 0 ; i < 8 ; i++ )
        {
            final int regNum = i;
            dataRegistersTextfields.add( createTextField( tf -> updateDataRegister(regNum,tf) ) );
            addressRegistersTextfields.add( createTextField( tf -> updateAddressRegister(regNum,tf)) );
        }

        // create status register checkboxes
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

        final JPanel topRow = new JPanel();
        topRow.setLayout( new FlowLayout() );

        // add PC
        PanelWithTextField panelWithTF = createTextFieldWithLabel( "PC" , tf -> doWithNumber(tf, adr ->
        {
            ui.doWithEmulator(emu ->
            {
                LOG.info( "Setting PC to "+ Misc.hex(adr&~1) );
                emu.cpu.cycles = 1;
                emu.cpu.pc = (adr & ~1);
                emu.invokeTickCallback();
            });
        }));
        pcTextfield = panelWithTF.textfield;
        topRow.add( panelWithTF.panel );

        // add supervisor stack ptr
        panelWithTF = createTextFieldWithLabel( "SSP", tf -> doWithNumber(tf, adr ->
        {
            ui.doWithEmulator(emu ->
            {
                LOG.info( "Setting SSP to "+ Misc.hex(adr&~1) );
                emu.cpu.supervisorModeStackPtr = adr & ~1;
            });
        }));
        sspTextfield = panelWithTF.textfield;
        topRow.add( panelWithTF.panel );

        // add user-mode stack ptr
        panelWithTF = createTextFieldWithLabel( "USP", tf -> doWithNumber(tf, adr ->
        {
            ui.doWithEmulator(emu ->
            {
                LOG.info( "Setting USP to "+ Misc.hex(adr&~1) );
                emu.cpu.userModeStackPtr = adr & ~1;
            });
        }));
        uspTextfield = panelWithTF.textfield;
        topRow.add( panelWithTF.panel );

        GridBagConstraints cnstrs = cnstrs(0,0);
        cnstrs.gridwidth = 1;
        cnstrs.weightx = 1;
        cnstrs.weighty = 0.1;
        cnstrs.fill= GridBagConstraints.HORIZONTAL;
        getContentPane().add( topRow , cnstrs );

        // add CPU state checkboxes
        final JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout( new FlowLayout() );

        statusRegister = createTextField( ev ->
        {
            final int value = parseNumber( statusRegister.getText() );
            ui.doWithEmulator(emu ->
            {
                LOG.info( "Setting SR to "+ Misc.hex(value) );
                emu.cpu.setStatusRegister( value );
            });
        });
        statusRegister.setColumns( 5 );

        checkboxPanel.add(statusRegister);
        for ( int i = checkboxLabels.length-1 ; i >= 0 ; i-- ) {
            checkboxPanel.add( cpuFlagCheckboxes.get(i) );
        }
        cnstrs = cnstrs(0,1);
        cnstrs.weightx = 1;
        cnstrs.weighty = 0.5;
        cnstrs.fill= GridBagConstraints.BOTH;
        getContentPane().add( checkboxPanel, cnstrs );

        final JPanel registerPanel = new JPanel();
        registerPanel.setLayout(  new GridBagLayout() );
        int y = 0;
        for ( int i = 0 ; i < 8 ; i+=2 ) {
            registerPanel.add( new JLabel("D"+i), cnstrsNoResize( 0, y ) );
            registerPanel.add( dataRegistersTextfields.get(i) , cnstrs( 1, y ));
            registerPanel.add( new JLabel("D"+(i+1)), cnstrsNoResize( 2, y ) );
            registerPanel.add( dataRegistersTextfields.get(i+1) , cnstrs( 3, y ));
            registerPanel.add( new JLabel("A"+i), cnstrsNoResize( 4, y ) );
            registerPanel.add( addressRegistersTextfields.get(i) , cnstrs( 5, y ));
            registerPanel.add( new JLabel("A"+(i+1)), cnstrsNoResize( 6, y ) );
            registerPanel.add( addressRegistersTextfields.get(i+1) , cnstrs( 7, y ));
            y += 1;
        }
        cnstrs = cnstrs(0,2);
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
        final int usp;
        final int ssp;
        final int sr;
        synchronized(DATA_LOCK)
        {
            sr = emulator.cpu.statusRegister;
            usp = emulator.cpu.userModeStackPtr;
            ssp = emulator.cpu.supervisorModeStackPtr;
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
                statusRegister.setText( hex( sr ) );
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
                uspTextfield.setText( hex( usp ) );
                sspTextfield.setText( hex( ssp ) );
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
