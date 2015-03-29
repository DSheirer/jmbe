package jmbe.edac;

import jmbe.binary.BinaryFrame;

/*******************************************************************************
 *     jmbe - Java MBE Library 
 *     Copyright (C) 2015 Dennis Sheirer
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/

public class Golay23
{
	public static final int MAX_CORRECTABLE_ERRORS = 3;
	
	public static final int[] CHECKSUMS = new int[]
	{
	    0x63A, 0x31D, 0x7B4, 0x3DA, 0x1ED, 0x6CC, 0x366, 0x1B3, 
	    0x6E3, 0x54B, 0x49F, 0x475, 0x400, 0x200, 0x100, 0x080, 
	    0x040, 0x020, 0x010, 0x008, 0x004, 0x002, 0x001 
	};

	/**
	 * Implements Golay(23,11,7) error detection and correction.  Returns the
	 * number of detected errors.  If the error count is less than or equal to
	 * the max correctable errors (3), then the error bits are corrected.  
	 * Otherwise the message is left intact and an error count greater than 3
	 * is returned.
	 * 
	 * @param frame - message frame bitset
	 * @param startIndex - first bit index of the golay protected bit sequence
	 * 
	 * @return - number of detected errors
	 */
	public static int checkAndCorrect( BinaryFrame frame, int startIndex )
	{
		int syndrome = getSyndrome( frame, startIndex );
		
		/* No errors */
		if( syndrome == 0 )
		{
			return 0;
		}
		
		BinaryFrame copy = frame.getSubMessage( startIndex, startIndex + 23 );

		int index = -1;
		int syndromeWeight = MAX_CORRECTABLE_ERRORS;
		int errors = 0;
		
		while( index < 23 )
		{
			if( index != -1 )
			{
				/* restore the previous flipped bit */
				if( index > 0 )
				{
					copy.flip( index - 1 );
				}
				
				copy.flip( index );
				
				syndromeWeight = MAX_CORRECTABLE_ERRORS - 1;
			}
			
			syndrome = getSyndrome( copy, 0 );
			
			if( syndrome > 0 )
			{
				for( int i = 0; i < 23; i++ )
				{
					errors = Integer.bitCount( syndrome );
					
					if( errors <= syndromeWeight )
					{
						copy.xor( 12, 11, syndrome );
						
						copy.rotateRight( i, 0, 22 );

						if( index >= 0 )
						{
							errors ++;
						}

						int corrected = copy.getInt( 0, 22 );
						int original = frame.getInt( startIndex, startIndex + 22 );
						
						int errorCount = Integer.bitCount( original ^ corrected );
						
						if( errorCount <= 3 )
						{
							frame.load( startIndex, 23, corrected );
						}

						return errorCount;
					}
					else
					{
						copy.rotateLeft( 0, 22 );
						syndrome = getSyndrome( copy, 0 );
					}
				}
				
				index++;
			}
		}

		/* Return an error count greater than 3 to indicate failed correction attempt */
		return 4;
	}

	private static int getSyndrome( BinaryFrame frame, int startIndex )
	{
		int calculated = calculateChecksum( frame, startIndex );
		
		int checksum = frame.getInt( startIndex + 12, startIndex + 22 );

		return ( checksum ^ calculated );
	}
	
	private static int calculateChecksum( BinaryFrame frame, int startIndex )
	{
		int calculated = 0; //Starting value

		/* Iterate the set bits and XOR running checksum with lookup value */
		for (int i = frame.nextSetBit( startIndex ); 
				 i >= startIndex && i < startIndex + 12; 
				 i = frame.nextSetBit( i+1 ) ) 
		{
			calculated ^= CHECKSUMS[ i - startIndex ];
		}
		
		return calculated;
	}
	
	public static void main( String[] args )
	{
		BinaryFrame frame = BinaryFrame.load( "00000100000011011001100" );
		
		System.out.println( "FRAME: " + frame.toString() );
		
		int errors = checkAndCorrect( frame, 0 );
		
		System.out.println( "FIXED: " + frame.toString() + " Errors: " + errors );
	}
}
