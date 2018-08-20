package de.codersourcery.m68k.utils;

import org.apache.commons.lang3.Validate;

import javax.swing.*;
import java.awt.*;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;

public class BusSpy
{
    private static final int ROW_HEIGHT = 25;

    private static final Stroke CURRENT =
            new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

    private final IBus bus;

    private MyWindow window;

    private int writePtr;
    private boolean[][] dataArray;
    private boolean isFull;

    protected final class MyWindow extends JFrame
    {
        public MyWindow()
        {
            super(bus.getName());
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            final JPanel panel = new JPanel()
            {
                @Override
                protected void paintComponent(Graphics g)
                {
                    final Graphics2D gfx = (Graphics2D) g;
                    super.paintComponent(g);

                    g.setColor(Color.BLACK);

                    final int maxIdx = isFull ? dataArray.length : writePtr;
                    System.out.println("Painting "+maxIdx+" states...");
                    final IBus.Pin[] pins = bus.getPins();

                    for ( int j = 0 ; j < pins.length ; j++)
                    {
                        final int yBaseline = ROW_HEIGHT + (int) (j * ROW_HEIGHT * 1.25f);
                        final IBus.Pin pin = pins[j];
                        final LineMetrics metrics = g.getFontMetrics().getLineMetrics(pin.name, g);
                        final Rectangle2D bounds = g.getFontMetrics().getStringBounds(pin.name, g);
                        final int textBaselineY = yBaseline - ROW_HEIGHT / 2;
                        g.drawString(pin.name, 5, (int) (textBaselineY + metrics.getDescent()));

                    }
                    final int xStep = getWidth() / (maxIdx+1);

                    boolean[] previousState = dataArray[0];
                    int previousX = 100;
                    for ( int i = 1 ; i < maxIdx ; i++ )
                    {
                        int currentX = previousX + xStep;
                        final boolean[] currentState = dataArray[i];
                        if ( i == writePtr ) {
                            g.setColor(Color.RED);
                            final Stroke oldStroke = gfx.getStroke();
                            gfx.setStroke( CURRENT );
                            g.drawLine(currentX,0,currentX,getHeight());
                            g.setColor(Color.BLACK);
                            gfx.setStroke( oldStroke );
                        }
                        for ( int j = 0 ; j < pins.length ; j++)
                        {
                            final int yBaseline = ROW_HEIGHT+(int) (j*ROW_HEIGHT*1.25f);

                            if ( currentState[j] )
                            {
                                if ( previousState[j] ) {
                                    // high -> high
                                    final int y = yBaseline - ROW_HEIGHT + 1;
                                    g.drawLine(previousX,y,currentX,y);
                                } else {
                                    // low -> high
                                    g.drawLine(previousX,yBaseline,currentX,yBaseline);
                                    final int y = yBaseline - ROW_HEIGHT + 1;
                                    g.drawLine(currentX,yBaseline,currentX,y);
                                }
                            } else
                            {
                                if ( previousState[j] ) {
                                    // high -> low
                                    final int y0 = yBaseline - ROW_HEIGHT + 1;
                                    g.drawLine(previousX,y0,currentX,y0);
                                    final int y1 = yBaseline;
                                    g.drawLine(currentX,y0,currentX,y1);
                                } else {
                                    // low -> low
                                    g.drawLine(previousX,yBaseline,currentX,yBaseline);
                                }
                            }
                        }
                        previousX = currentX;
                        previousState = currentState;
                    }
                }
            };
            panel.setPreferredSize(new Dimension(320,200));
            getContentPane().add( panel );
        }
    }

    public BusSpy(IBus bus,int maxLen) {
        Validate.notNull(bus, "bus must not be null");
        if ( maxLen < 1 ) {
            throw new IllegalArgumentException("maxLen needs to be >= 1");
        }
        this.bus = bus;
        dataArray = new boolean[maxLen][];
    }

    public void show() {
        SwingUtilities.invokeLater(() ->
        {
            if ( window == null ) {
                window = new MyWindow();
            }
            if ( ! window.isVisible() )
            {
                window.pack();
                window.setLocationRelativeTo(null);
                window.setVisible(true);
            }
        });
    }

    public void hide() {
        SwingUtilities.invokeLater(() ->
        {
            if ( window.isVisible() )
            {
                window.dispose();
                window = null;
            }
        });
    }

    public void repaint() {
        SwingUtilities.invokeLater(() ->
        {
            if ( window.isVisible() )
            {
                window.repaint();
            }
        });
    }

    public void takeSample()
    {
        final IBus.Pin[] pins = bus.getPins();
        final boolean[] states = new boolean[pins.length];
        for (int i = 0; i < pins.length ; i++)
        {
            states[i] = bus.readPin( pins[i] );
        }
        dataArray[writePtr] = states;
        System.out.println("Sampled states, now got "+writePtr+" samples");
        writePtr++;
        if ( writePtr == dataArray.length ) {
            isFull = true;
            writePtr = 0;
        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        final IBus.Pin[] pins = {
                new IBus.Pin("Pin #0",0),
                new IBus.Pin("Pin #1",1),
                new IBus.Pin("Pin #2",2),
                new IBus.Pin("Pin #3",3),
        };
        final IBus bus = new IBus() {

            @Override
            public String getName()
            {
                return "Test Bus";
            }

            @Override
            public Pin[] getPins()
            {
                return pins;
            }

            @Override
            public boolean readPin(Pin pin)
            {
                return new Random().nextBoolean();
            }
        };
        final BusSpy spy =
                new BusSpy(bus, 10);

        spy.show();
        while ( true )
        {
            spy.takeSample();
            spy.repaint();
            Thread.sleep(500);
        }
    }
}
