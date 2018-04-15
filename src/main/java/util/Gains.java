package util;

import java.text.DecimalFormat;

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
 * Utility class for calculating the gains of the higher order dct coefficients
 * using the values from Tables 3 (steps) and 4 (coefficients) in combination
 * with the step size parameters from Annex G.
 * 
 * Note: the first 5 gains (b3 - b7) are obtained directly from Annex F and
 * the remaining gains are calculated with this utility for appending to 
 * the first 5 gains.
 */
public class Gains
{
	public static final DecimalFormat FORMAT = new DecimalFormat( "0.000000" );
	
	public static void main( String[] args )
	{
		log( "Starting ..." );

		/* Table 3 bit sizes */
		double[] bitStepSizes = new double[] { 1.20, 0.85, 0.65, 0.40, 0.28, 0.15, 0.08, 0.04, 0.02, 0.01 }; 
		
		/* Table 4 coefficients */
		double[] coefficient = new double[] { 0.307, 0.241, 0.207, 0.190, 0.179, 0.173, 0.165, 0.170, 0.170 };
		
		double[][] table = new double[ bitStepSizes.length ][ coefficient.length ];
		
		for( int x = 0; x < bitStepSizes.length; x++ )
		{
			for( int y = 0; y < coefficient.length; y++ )
			{
				table[ x ][ y ] = bitStepSizes[ x ] * coefficient[ y ];
			}
		}
		
//		StringBuilder sb = new StringBuilder();
//		sb.append( "BITS  1       2       3       4       5       6       7       8       9       10\n" );
//
//		for( int y = 0; y < coefficient.length; y++ )
//		{
//			sb.append( "Ci," );
//			
//			sb.append( (y + 2) );
//
//			if( !( y + 2 == 10 ) )
//			{
//				sb.append( " " );
//			}
//			
//			sb.append( " " );
//			
//			for( int x = 0; x < bitStepSizes.length; x++ )
//			{
//				sb.append( FORMAT.format( table[ x ][ y ] ) );
//				sb.append( " " );
//			}
//			
//			sb.append( " (" );
//			sb.append( FORMAT.format( coefficient[ y ] ) );
//			sb.append( " )" );
//			sb.append( "\n" );
//		}
//		
//		log( sb.toString() );
		
		int L = 56;
		int[] k = new int[] { 2,3,4,5,6,7,8,9,2,3,4,5,6,7,8,9,2,3,4,5,6,7,8,9,2,3,4,5,6,7,8,9,2,3,4,5,6,7,8,9,10,2,3,4,5,6,7,8,9,10 };
		int[] b = new int[] { 3,2,2,1,1,1,1,0,3,2,2,1,1,1,1,0,2,2,1,1,1,1,1,0,2,2,1,1,1,0,0,0,2,1,1,1,1,0,0,0, 0,2,1,1,1,0,0,0,0, 0 };

		StringBuilder sb = new StringBuilder();
		
		sb.append( ", " );
		
		for( int x = 0; x < k.length; x++ )
		{
			if( b[ x ] == 0 )
			{
				sb.append( FORMAT.format( 0 ) );
			}
			else
			{
				sb.append( FORMAT.format( table[ b[ x ] - 1 ][ k[ x ] - 2 ] ) );
			}
			
			if( x != k.length - 1 )
			{
				sb.append( ", " );
			}
		}
		
		log( sb.toString() + "\n" );
		
		log( "Finished!" );
	}

	public static void log( String message )
	{
		System.out.println( message );
	}
}
