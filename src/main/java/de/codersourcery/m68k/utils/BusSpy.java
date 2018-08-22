package de.codersourcery.m68k.utils;

import org.apache.commons.lang3.Validate;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Tiny UI application that implements a logic analyzer to
 * display {@link IBus Bus} state.
 *
 * Samples get stored in a ring-buffer with a fixed (configurable) size.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class BusSpy
{
    private static final Stroke DASHED =
            new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

    private final Color[] lineColors;
    private final IBus bus;

    private MyWindow window;

    private final int bufferSize; // only power-of-two buffer sizes are supported
    private final int bufferMask; // AND mask used to avoid modulo operations
    private int writePtr;
    private int[] dataArray;
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
                protected void paintComponent(Graphics gfx)
                {
                    final Graphics2D g = (Graphics2D) gfx;

                    // clear background
                    g.setColor(Color.WHITE);
                    g.fillRect(0,0,getWidth(),getHeight());
                    g.setColor(Color.BLACK);

                    // calculate size of header area
                    // by using the max width/height of the
                    // pin name's bounding box as
                    // header width / row height
                    final String[] pinNames = bus.getPinNames();
                    final Rectangle2D[] stringBounds = new Rectangle2D[pinNames.length];

                    int maxWidth = 0;

                    for ( int j = 0 ; j < pinNames.length ; j++)
                    {
                        final Rectangle2D bounds = g.getFontMetrics().getStringBounds(pinNames[j], g);
                        stringBounds[j]=bounds;
                        if ( bounds.getWidth() > maxWidth ) {
                            maxWidth = (int) bounds.getWidth();
                        }
                    }

                    final int yStartOffset = 5;
                    final int xStartOffset = 5;

                    final int rowHeight = (getHeight()-yStartOffset) / pinNames.length;
                    final int headerWidth = xStartOffset + (int) (maxWidth*1.1);
                    final int chartRowHeight = (int) (rowHeight*0.5f);
                    final int chartBaselineYOffset = (rowHeight - chartRowHeight)/2;

                    // render pin names
                    final Rectangle rect = new Rectangle();
                    rect.x = xStartOffset;
                    rect.height = rowHeight;
                    for ( int j = 0 ; j < pinNames.length ; j++)
                    {
                        rect.y = yStartOffset + j*rowHeight;
                        g.setColor(lineColors[j]);
                        drawString(g,g.getFontMetrics(),pinNames[j],rect);

                        g.setColor(Color.LIGHT_GRAY);
                        final Stroke old = g.getStroke();
                        g.setStroke(DASHED);
                        g.drawLine(0,rect.y + rect.height ,getWidth() ,rect.y+rect.height);
                        g.setColor(Color.BLACK);
                        g.setStroke(old);
                    }
                    g.drawLine(headerWidth,0,headerWidth,getHeight());

                    final int sampleCount = getSampleCount();
                    if ( sampleCount == 0 ) {
                        return;
                    }

                    final int drawingAreaWidth = getWidth() - xStartOffset - headerWidth;

                    final int xStep = drawingAreaWidth / (sampleCount+1);

                    final int readPtr = isFull ? (writePtr-bufferSize) & bufferMask : 0;
                    int previousState = dataArray[readPtr];
                    int previousX = headerWidth;
                    for ( int itemIdx = (readPtr+1)%bufferSize, pos = 0 ; pos < sampleCount; itemIdx = (itemIdx+1) % bufferSize , pos++)
                    {
                        int currentX = previousX + xStep;
                        final int currentState = dataArray[itemIdx];
                        int mask = 1<<0;
                        for ( int pinIdx = 0 ; pinIdx < pinNames.length ; pinIdx++, mask<<=1)
                        {
                            g.setColor(lineColors[pinIdx]);
                            final int yBaseline = yStartOffset + (1+pinIdx)*rowHeight - chartBaselineYOffset ;
                            if ( (currentState & mask) != 0 )
                            {
                                if ( ( previousState & mask ) != 0) {
                                    // high -> high
                                    final int y = yBaseline - chartRowHeight;
                                    g.drawLine(previousX,y,currentX,y);
                                } else {
                                    // low -> high
                                    g.drawLine(previousX,yBaseline,currentX,yBaseline);
                                    final int y = yBaseline - chartRowHeight;
                                    g.drawLine(currentX,yBaseline,currentX,y);
                                }
                            }
                            else
                            {
                                if ( ( previousState & mask ) != 0 ) {
                                    // high -> low
                                    final int y0 = yBaseline - chartRowHeight;
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
                    Toolkit.getDefaultToolkit().sync();
                }
            };
            panel.setPreferredSize(new Dimension(320,200));
            getContentPane().add( panel );
        }

        public void drawString(Graphics g, FontMetrics metrics, String text, Rectangle rect)
        {
            final int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
            g.drawString(text, rect.x, y);
        }
    }

    /**
     * Create instance.
     *
     * @param bus the bus to display
     * @param bufferSize size of ring buffer.
     *
     * @see #sampleBus()
     */
    public BusSpy(IBus bus,int bufferSize)
    {
        Validate.notNull(bus, "Bus must not be null");
        if ( bufferSize < 2 ) {
            throw new IllegalArgumentException("Buffer size needs to be >= 2");
        }
        if ( Integer.bitCount(bufferSize) != 1 ) {
            throw new IllegalArgumentException("Buffer size needs to be a power of two but was "+bufferSize);
        }
        this.bus = bus;
        this.dataArray = new int[bufferSize];
        this.lineColors = genColors(bus.getPinNames().length);
        this.bufferSize = bufferSize;
        this.bufferMask = bufferSize-1;
    }

    private static Color[] genColors(int count)
    {
        final Set<Integer> existing = new HashSet<>();
        final Color[] colors = new Color[count];
        final Random rnd = new Random(0xdeadbeef);
        for ( int i = 0 ; i <count ; i++ ) {
            final int color = rnd.nextInt(0xffffff + 1 );
            if ( ! existing.contains(color) )
            {
                final int r = (color & 0xff0000)>>16;
                final int g = (color & 0x00ff00)>> 8;
                final int b = (color & 0x0000ff);
                colors[i] = new Color( r,g,b);
                existing.add(color);
            }
        }
        return colors;
    }

    public boolean isVisible()
    {
        return window.isVisible();
    }

    /**
     * Show this window.
     */
    public void show()
    {
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if ( window == null ) {
                    window = new MyWindow();
                }
                if ( ! window.isVisible() )
                {
                    window.pack();
                    window.setLocationRelativeTo(null);
                    window.setVisible(true);
                    window.toFront();
                }
            });
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private int getSampleCount()
    {
        return isFull ? bufferSize : writePtr;
    }

    private boolean hasData() {
        return getSampleCount() != 0;
    }

    /**
     * Hide this window.
     */
    public void hide()
    {
        SwingUtilities.invokeLater(() ->
        {
            if ( window != null && window.isVisible() )
            {
                window.dispose();
                window = null;
            }
        });
    }

    /**
     * Force UI repaint.
     */
    public void repaint()
    {
        SwingUtilities.invokeLater(() ->
        {
            if ( window != null && window.isVisible() )
            {
                window.repaint();
            }
        });
    }

    /**
     * Samples the current bus state, stores it
     * in the internal ringbuffer and updates the UI.
     *
     * Invoking this method does <b>not</b> repaint
     * the UI, use {@link #repaint()} for that.
     */
    public void sampleAndRepaint() {
        sampleBus();
        repaint();
    }


    /**
     * Samples the current bus state and stores it
     * in the internal ringbuffer.
     *
     * Invoking this method does <b>not</b> repaint
     * the UI, use {@link #repaint()} for that.
     */
    public void sampleBus()
    {
        dataArray[writePtr] = bus.readPins();
        writePtr = (writePtr+1) & bufferMask;
        if ( !isFull && writePtr == 0 )
        {
            isFull = true;
        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        final String[] pins = {
                "Pin #0",
                "Pin #1",
                "Pin #2",
                "Pin #3"
        };
        final IBus bus = new IBus()
        {
            private final Random rnd = new Random(0xdeadbeef);

            @Override
            public String getName()
            {
                return "Test Bus";
            }

            @Override
            public String[] getPinNames()
            {
                return pins;
            }

            @Override
            public int readPins()
            {
                return rnd.nextInt(16);
            }
        };
        final BusSpy spy = new BusSpy(bus, 16);

        spy.show();
        while ( true )
        {
            spy.sampleBus();
            spy.repaint();
            Thread.sleep(500);
        }
    }
}
