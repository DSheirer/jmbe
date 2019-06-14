package jmbe.codec.imbe;

import jmbe.binary.BinaryFrame;

import java.util.BitSet;

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
 * Utility class to process IMBE Voice Frame interleaving.
 */
public class IMBEInterleave
{
	private static int[] DEINTERLEAVE = new int[] { 0,24,48,72,96,120,25,
		1,73,49,121,97,2,26,50,74,98,122,27,3,75,51,123,99,4,28,52,76,100,124,
		29,5,77,53,125,101,6,30,54,78,102,126,31,7,79,55,127,103,8,32,56,80,104,
		128,33,9,81,57,129,105,10,34,58,82,106,130,35,11,83,59,131,107,12,36,60,
		84,108,132,37,13,85,61,133,109,14,38,62,86,110,134,39,15,87,63,135,111,
		16,40,64,88,112,136,41,17,89,65,137,113,18,42,66,90,114,138,43,19,91,
		67,139,115,20,44,68,92,116,140,45,21,93,69,141,117,22,46,70,94,118,142,
		47,23,95,71,143,119 };

	public static void deinterleave( BinaryFrame frame )
	{
		BitSet original = frame.get( 0, 144 );

		/* Clear block bits in source message */
		frame.clear( 0, 144 );

		/* Iterate set bits of original message and transfer back to imbe frame */
		for (int i = original.nextSetBit( 0 ); 
				 i >= 0 && i < DEINTERLEAVE.length; 
				 i = original.nextSetBit( i + 1 ) ) 
		{
			frame.set( DEINTERLEAVE[ i ] );
		}
	}
}
