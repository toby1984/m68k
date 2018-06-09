package de.codersourcery.m68k.parser;

import java.io.Serializable;
import java.util.List;

/**
 * A text region.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class TextRegion implements Serializable
{
    private int startingOffset;
    private int length;
    
    public TextRegion(List<? extends TextRegion> ranges) 
    {
    	if (ranges == null) {
			throw new IllegalArgumentException("ranges must not be NULL");
		}
    	if ( ranges.isEmpty() ) {
    		throw new IllegalArgumentException("ranges cannot be empty");
    	}
    	TextRegion first = ranges.get(0);
    	this.startingOffset = first.getStartingOffset();
    	this.length = first.getLength();
    	
    	if ( ranges.size() > 1 ) {
    		merge( ranges.subList( 1 , ranges.size() ) );
    	}
    }
    
    public TextRegion createCopy() {
        return new TextRegion( this );
    }
    
    public static TextRegion of(int start,int len) {
        return new TextRegion(start,len);
    }

    public boolean equals(Object obj) {
    	if ( obj instanceof TextRegion) {
    		return this.startingOffset == ((TextRegion) obj).getStartingOffset() &&
    			   this.length == ((TextRegion) obj).getLength();
    	}
    	return false;
    }
    
    public static int hashCode(TextRegion range) 
    {
    	if ( range == null ) {
    		return 0;
    	}
    	return range.getStartingOffset()*13 + range.getLength();
    }
    
    public TextRegion(TextRegion range) {
    	this( range.getStartingOffset() , range.getLength() );
    }
    
    public TextRegion(int startingOffset, int length)
    {
        if ( startingOffset < 0 ) {
            throw new IllegalArgumentException("startingOffset must not be >= 0");
        }
        if ( length < 0 ) {
            throw new IllegalArgumentException("length must not be >= 0");
        }           
        this.startingOffset = startingOffset;
        this.length = length;
    }

    /**
     * Returns the starting offset of this text region.
     * @return
     */    
    public int getStartingOffset()
    {
        return startingOffset;
    }
    
    /**
     * Returns the length of this text region in characters.
     *
     * @return
     */    
    public int getLength()
    {
        return length;
    }

    /**
     * Calculates the union of this text region with another.
     *
     * @param other
     */    
    public void merge(TextRegion other)
    {
    	// order of calculations is IMPORTANT here, otherwise it's yielding wrong results!
        final int newEnd = this.getEndOffset() > other.getEndOffset() ? this.getEndOffset() : other.getEndOffset();
        this.startingOffset = this.getStartingOffset() < other.getStartingOffset() ? this.getStartingOffset() : other.getStartingOffset();
        this.length = newEnd - this.startingOffset;
    }
    
    /**
     * Subtracts another text range from this one.
     *
     * <p>
     * Note that this method (intentionally) does <b>not</b> handle
     * intersections where the result would actually be two non-adjactent
     * regions of text.</p>
     * @param other
     * @throws UnsupportedOperationException
     */    
    public void subtract(TextRegion other)
    {
        if ( isSame( other ) ) 
        {
        	this.length =  0;
            return;
        }
        
        if ( other.getStartingOffset() == this.getStartingOffset() ) {
            // both ranges starts share the same starting offset
            final int length = this.getLength() - other.getLength();
            if ( length < 0 ) {
                throw new IllegalArgumentException("Cannot subtract range "+other+" that is longer than "+this);
            }      
            this.startingOffset = other.getEndOffset();
            this.length = length;
            return;
        }
        else if ( other.getEndOffset() == this.getEndOffset() ) 
        {
            final int length = this.getLength() - other.getLength();
            if ( length < 0 ) {            
                throw new IllegalArgumentException("Cannot subtract range "+other+" that starts before "+this);
            }
            this.length = length;
            return;
        } 
        else if ( ! this.contains( other ) ) {
        	return;
        }
        throw new UnsupportedOperationException("Cannot calculate "+this+" MINUS "+other+" , would yield two non-adjactent ranges");
    }
    
    /**
     * Check whether this text region fully contains another region.
     * @param other
     * @return
     */    
    public boolean contains(TextRegion other)
    {
        return other.getStartingOffset() >= this.getStartingOffset() && other.getEndOffset() <= this.getEndOffset();
    }
    
    /**
     * Check whether this text region overlaps with another.
     *
     * @param other
     * @return
     */    
    public boolean overlaps(TextRegion other)
    {
        return this.contains( other ) || other.contains( this ) || 
           ( ! this.contains( other.getStartingOffset() ) && this.contains( other.getEndOffset() ) ) ||
           ( this.contains( other.getStartingOffset() ) && ! this.contains( other.getEndOffset() ) );
    }
    
    /**
     * Calculates the intersection of this text region with another.
     *
     * @param other text region to calculate intersection with
     * @throws IllegalArgumentException if <code>other</code> is <code>null</code>
     * or does not overlap with this text range at all.
     */    
    public void intersect(TextRegion other) {
    	
    	/*    |-- this --|
    	 * |-other-|
    	 */
    	if ( ! contains( other.getStartingOffset() ) && contains( other.getEndOffset()-1 ) ) {
    		// ORDER of calculations is important here! 
    		this.length = other.getEndOffset() - this.getStartingOffset();
    		this.startingOffset = other.getStartingOffset();
    		return; 
    	}
    	
    	/*   |-- this --|
    	 *     |-other-|
    	 */
    	if ( contains( other.getStartingOffset() ) && contains( other.getEndOffset() ) ) {
    		this.startingOffset = other.getStartingOffset();
    		this.length = other.getLength();
    		return; 
    	}
    	
    	/*   |-- this --|
    	 *         |-other-|
    	 */
    	if ( contains( other.getStartingOffset() ) && ! contains( other.getEndOffset() ) ) {
    		this.length = getEndOffset() - other.getStartingOffset();
    		this.startingOffset = other.getStartingOffset();
    		return; 
    	}   
    	
    	/*   |-- this --|
    	 * |-----other-----|
    	 */
    	if ( other.contains( getStartingOffset() ) && other.contains( getEndOffset() ) ) {
    		// this range already is the intersection
    		return; 
    	}     	
    	throw new IllegalArgumentException( this+" has no intersection with "+other);
    }
    
    /**
     * Returns the end offset of this text region.
     *
     * @return end offset ( starting offset + length )
     */    
    public int getEndOffset()
    {
        return startingOffset+length;
    }

    /**
     * Check whether this text region covers a given offset.
     * @param offset
     * @return
     */    
    public boolean contains(int offset)
    {
        return offset >= this.getStartingOffset() && offset < this.getEndOffset();
    }

    /**
     * Check whether this text region is the same as another.
     *
     * @param other
     * @return <code>true</code> if this region as the same length and starting offset
     * as the argument
     */    
    public boolean isSame(TextRegion other)
    {
        return this == other || ( this.getStartingOffset() == other.getStartingOffset() && this.getLength() == other.getLength() );
    }

    /**
     * Extract the region denoted by this text region from a string.
     *
     * @param string
     * @return
     */    
    public String apply(String string)
    {
    	try {
    		return string.substring( getStartingOffset() , getEndOffset() );
    	} catch(StringIndexOutOfBoundsException e) {
    		throw new StringIndexOutOfBoundsException("TextRegion out of bounds, cannot apply "+this+" to "+
    				" string of length "+string.length());
    	}
    }    
    
    public String toString()
    {
        return "["+getStartingOffset()+","+getEndOffset()+"[";
                
    }

    /**
     * Calculates the union of this text region with several others.
     * @param ranges
     */      
	public void merge(List<? extends TextRegion> ranges) 
	{
		if ( ranges.isEmpty() ) {
			return;
		}
		for ( TextRegion r : ranges ) {
			merge( r );
		}
	}
}
