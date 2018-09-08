package de.codersourcery.m68k.emulator.ui;

import de.codersourcery.m68k.emulator.Emulator;
import de.codersourcery.m68k.emulator.memory.Memory;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.util.Arrays;

public class ScreenWindow extends AppWindow implements ITickListener,
        Emulator.IEmulatorStateCallback
{
    private final Object LOCK = new Object();

    // @GuaredBy(LOCK)
    private int amigaWidth;
    // @GuaredBy(LOCK)
    private int amigaHeight;
    // @GuaredBy(LOCK)
    private int[] screenData = new int[0];

    private final class MyPanel extends JPanel
    {
        private int previousWidth;
        private int previousHeight;
        private BufferedImage image;

        private BufferedImage getImage()
        {
            if ( image == null || previousHeight != amigaHeight || previousWidth != amigaWidth)
            {
                image = new BufferedImage( amigaWidth,amigaHeight,BufferedImage.TYPE_INT_RGB );
                previousWidth = amigaHeight;
                previousHeight = amigaWidth;
            }
            return image;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            final BufferedImage image;
            synchronized(LOCK)
            {
                image = getImage();
                final WritableRaster raster = image.getRaster();
                final int[] dst = ( (DataBufferInt) raster.getDataBuffer() ).getData();
                System.arraycopy(screenData, 0, dst, 0, screenData.length);
            }
            g.drawImage( image,0,0,getWidth(),getHeight(),null );
        }
    };

    private final MyPanel panel = new MyPanel();

    public ScreenWindow(String title, UI ui)
    {
        super( title, ui );

        final GridBagConstraints cnstrs = cnstrs( 0, 0 );
        cnstrs.weightx=1;
        cnstrs.weighty=1;
        cnstrs.fill = GridBagConstraints.BOTH;
        getContentPane().setLayout( new GridBagLayout() );
        getContentPane().add( panel, cnstrs );
    }

    @Override
    public void stopped(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public void singleStepFinished(Emulator emulator)
    {
        tick(emulator);
    }

    @Override
    public void enteredContinousMode(Emulator emulator)
    {
    }

    @Override
    public String getWindowKey()
    {
        return "screen";
    }

    @Override
    public void tick(Emulator emulator)
    {
        synchronized (LOCK)
        {
            final int height = emulator.video.getDisplayHeight();
            final int width = emulator.video.getDisplayWidth();
            amigaHeight = height;
            amigaWidth = width;
            if ( screenData.length != height*width )
            {
                System.out.println("Screen resolution: "+width+"x"+height);
                screenData = new int[height * width];
            }
            emulator.video.convertDisplayData( screenData, emulator.dmaController.isBitplaneDMAEnabled() );
        }
        repaint();
    }
}