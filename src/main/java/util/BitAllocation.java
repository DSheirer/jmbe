package util;

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


/**
 * Utility to generate the message bit indexes for each of the gain and DCT
 * coefficient values, for all values of harmonics (L) between 9 and 56.  This
 * was used to create the coefficients maps in the Harmonics enumeration.
 * 
 * The imbe frame is 144 bits long broken up into blocks of allocable bits after
 * the golay and hamming error protection bit indices are removed, as follows:
 * 
 * u0 = 000 001 002 003 004 005 006 007 008 009 010 011
 * u1 = 023 024 025 026 027 028 029 030 031 032 033 034
 * u2 = 046 047 048 049 050 051 052 053 054 055 056 057
 * u3 = 069 070 071 072 073 074 075 076 077 078 079 080
 * u4 =     092 093 094 095 096 097 098 099 100 101 102
 * u5 =     107 108 109 110 111 112 113 114 115 116 117
 * u6 =     122 123 124 125 126 127 128 129 130 131 132
 * u7 =                     137 138 139 140 141 142 143
 * 
 * The following bit indices are reserved for bit information vectors b0 - b2
 * 
 *  Pitch Fundamental Frequency
 * 	b0 = 000,001,002,003,004,005,141,142
 * 
 *  Voiced / Un-voiced Band Bitmap
 *  b1 =         092,093,094 (k=3)
 *  b1 =         092,093,094,095 (k=4)
 *  b1 =         092,093,094,095,096 (k=5)
 *  b1 =         092,093,094,095,096,097 (k=6)
 *  b1 =         092,093,094,095,096,097,098 (k=7)
 *  b1 =         092,093,094,095,096,097,098,099 (k=8)
 *  b1 =         092,093,094,095,096,097,098,099,100 (k=9)
 *  b1 =         092,093,094,095,096,097,098,099,100,101 (k=10)
 *  b1 =         092,093,094,095,096,097,098,099,100,101,102 (k=11)
 *  b1 =         092,093,094,095,096,097,098,099,100,101,102,107 (k=12)
 *  
 *  To Use: update L, K and the bit lengths array from annexes F (b3 - b7) and
 *  Annex G (b8 - L+1) and run for each value of L.
 */
public class BitAllocation
{
	public static void main( String[] args )
	{
		log( "Starting ..." );

		/* Number of harmonics (L) value.  Run this utility for all values of
		 * L between (9 <= L <= 56) to produce the array of coefficient array
		 * indexes */
		int L = 56;
		
		/* Number of frequency bands (K) value.  This value changes according
		 * to L */
		int K = 12;
		
		/* bit vector lengths from Annex F and Annex G */ 
		int[] lengths = new int[] { 3,3,2,2,2,3,2,2,1,1,1,1,0,3,2,2,1,1,1,1,0,2,2,1,1,1,1,1,0,2,2,1,1,1,0,0,0,2,1,1,1,1,0,0,0,0,2,1,1,1,0,0,0,0,0 };

		/* Golay protected bit indices available for allocation */ 
		int[] golay_indexes = new int[] { 9,10,11,
							  23,24,25,26,27,28,29,30,31,32,33,34,
							  46,47,48,49,50,51,52,53,54,55,56,57,
							  69,70,71,72,73,74,75,76,77,78,79,80 };

		/* Hamming protected bit indices and un-protected bit indices available 
		 * for allocation.  Note: 92-95 and a variable amount between 96 and 
		 * 109 are used for b1 according to the size of K */
		int[] hamming_indexes = new int[] { 92,93,94,95,96,97,98,99,100,101,102,
							  107,108,109,110,111,112,113,114,115,116,117,
							  122,123,124,125,126,127,128,129,130,131,132,
							  137,138,139 };

		int longestBitLength = 0;

		/* Find longest bit length */
		for( int x: lengths )
		{
			if( x > longestBitLength )
			{
				longestBitLength = x;
			}
		}

		/* Array of string builders to hold each of the coefficient arrays */
		StringBuilder[] sbs = new StringBuilder[ lengths.length ];
		
		for( int x = 0; x < lengths.length; x++ )
		{
			sbs[ x ] = new StringBuilder();
			sbs[ x ].append( "{ " );
		}
		
		boolean flag = true;

		/* Pointer, max, and index set for iterating golay and hamming index sets */
		int[] indexes = golay_indexes;
		int max = golay_indexes.length;
		int pointer = 0;

		/* Allocate the bits to each of the coefficient arrays */
		while( flag && longestBitLength > 0 )
		{
			flag = false;
			
			for( int x = 0; x < lengths.length; x++ )
			{
				if( lengths[ x ] == longestBitLength )
				{
					lengths[ x ]--;
					sbs[ x ].append( indexes[ pointer++ ] + "," );
					
					flag = true;
					
					if( pointer >= max )
					{
						indexes = hamming_indexes;
						max = hamming_indexes.length;
						pointer = K + 2;
					}
				}
			}
			
			longestBitLength--;
		}

		/* Compile the final array of coefficient arrays */
		StringBuilder sb = new StringBuilder();
		
		sb.append( "new int[][] { " );
		
		for( int x = 0; x < lengths.length; x++ )
		{
			if( sbs[ x ].length() > 0 )
			{
				sbs[ x ].deleteCharAt( sbs[ x ].length() - 1 );
			}
			
			sbs[ x ].append( " }" );
			
			log( sbs[ x ].toString() );
			
			sb.append( sbs[ x ].toString() );
			
			if( x < lengths.length - 1 )
			{
				sb.append( ", " );
			}
		}
		
		sb.append( " }" );
		
		log( sb.toString() );
		
		log( "Finished" );
	}

	public static void log( String message )
	{
		System.out.println( message );
	}
}
