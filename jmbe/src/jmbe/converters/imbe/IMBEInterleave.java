package jmbe.converters.imbe;

import java.util.BitSet;

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


/**
 * Utility class to process IMBE Voice Frame interleaving.
 */
public class IMBEInterleave
{
	private static int[] DEINTERLEAVE = new int[] { 0,24,48,72,96,120,25,
		1,73,49,121,97,2,26,50,74,98,122,27,3,75,51,123,99,4,28,52,76,100,124,
		29,5,77,53,125,101,6,30,54,78,102,126,31,7,79,55,127,103,8,32,56,80,104,
		128,33,9,81,57,129,105,10,34,58,82,106,130,35,11,83,59,131,107,12,36,60,
		84,108,132,37,13,85,61,133,109,14,38,62,86,110,134,39,87,15,63,135,111,
		16,40,64,88,112,136,41,17,89,65,137,113,18,42,66,90,114,138,43,19,91,
		67,139,115,20,44,68,92,116,140,45,21,93,69,141,117,22,46,70,94,118,142,
		47,23,95,71,143,119 };

	private static int[] INTERLEAVE = new int[] { 0,7,12,19,24,31,36,43,
		48,55,60,67,72,79,84,91,96,103,108,115,120,127,132,139,1,6,13,18,25,30,
		37,42,49,54,61,66,73,78,85,90,97,102,109,114,121,126,133,138,2,9,14,21,
		26,33,38,45,50,57,62,69,74,81,86,93,98,105,110,117,122,129,134,141,3,8,
		15,20,27,32,39,44,51,56,63,68,75,80,87,92,99,104,111,116,123,128,135,
		140,4,11,16,23,28,35,40,47,52,59,64,71,76,83,88,95,100,107,112,119,124,
		131,136,143,5,10,17,22,29,34,41,46,53,58,65,70,77,82,89,94,101,106,113,
		118,125,130,137,142 };
	
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
