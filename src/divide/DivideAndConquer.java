package divide;

import ij.ImageJ;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.io.ImgIOException;
import net.imglib2.io.ImgOpener;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

public class DivideAndConquer
{
	static class BinaryRestorationGraph extends Algorithm.ProblemGraph< Integer, Integer >
	{
		final RandomAccessibleInterval< UnsignedByteType > img;

		final int n;

		final long[] dimensions;

		final int[][] neighborOffsets;

		final HashSet< Integer > variables;

		public BinaryRestorationGraph( final RandomAccessibleInterval< UnsignedByteType > img )
		{
			n = img.numDimensions();
			dimensions = new long[ n ];
			img.dimensions( dimensions );
			final long numNodes = Intervals.numElements( img );

			neighborOffsets = new int[2*n][n];
			for ( int d = 0; d < n; d++ )
			{
				Arrays.fill( neighborOffsets[ 2 * d ], 0 );
				neighborOffsets[ 2 * d ][ d ] = 1;
				Arrays.fill( neighborOffsets[ 2 * d + 1 ], 0 );
				neighborOffsets[ 2 * d + 1 ][ d ] = -1;
			}

			this.img = img;

			variables = new HashSet< Integer >( ( int ) numNodes );
			for ( int v = 0; v < numNodes; ++v )
				variables.add( new Integer( v ) );
		}

		@Override
		public Set< Integer > allVariables()
		{
			return variables;
		}

		@Override
		public Map< Integer, Integer > solve( final Set< Integer > region )
		{
			return BinaryRestoration.binaryRestoration( img, region );
		}

		@Override
		public Set< Integer > gamma( final Set< Integer > region )
		{
			final HashSet< Integer > gammaR = new HashSet< Integer >( region );
			final int numNeighbors = neighborOffsets.length;
			final long[] position = new long[ n ];
			for ( final int nodeNum : region )
			{
	A:			for ( int i = 0; i < numNeighbors; ++i )
				{
					IntervalIndexer.indexToPosition( nodeNum, dimensions, position );
					for ( int d = 0; d < n; ++d )
					{
						position[ d ] += neighborOffsets[ i ][ d ];
						if ( position[ d ] < 0 || position[ d ] >= dimensions[ d ] )
							continue A;
					}
					gammaR.add( ( int ) IntervalIndexer.positionToIndex( position, dimensions ) );
				}
			}

			return gammaR;
		}

		public void showSolution( final Map< Integer, Integer > solution )
		{
			// create segmentation image
			final UnsignedByteType type = new UnsignedByteType();
			final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
			final Img< UnsignedByteType > restored = factory.create( img, type );

			final RandomAccess< UnsignedByteType > access = restored.randomAccess();
			final long[] position = new long[ n ];
			for ( final Map.Entry< Integer, Integer > entry : solution.entrySet() )
			{
				final int nodeNum = entry.getKey();
				final int value = entry.getValue();
				IntervalIndexer.indexToPosition( nodeNum, dimensions, position );
				access.setPosition( position );
				if ( value == 0 )
					access.get().set( 0 );
				else
					access.get().set( 255 );
			}

			ImageJFunctions.show( restored );
		}

		public void showConflicts( final Deque< Integer > conflicts )
		{
			final UnsignedByteType type = new UnsignedByteType();
			final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
			final Img< UnsignedByteType > restored = factory.create( img, type );

			final RandomAccess< UnsignedByteType > access = restored.randomAccess();
			final long[] position = new long[ n ];
			for ( final int v : conflicts )
			{
				IntervalIndexer.indexToPosition( v, dimensions, position );
				access.setPosition( position );
				access.get().set( 255 );
			}

			ImageJFunctions.show( restored );
		}

		public void showKappas( final Map< Integer, Integer > kappas )
		{
			final UnsignedByteType type = new UnsignedByteType();
			final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
			final Img< UnsignedByteType > restored = factory.create( img, type );

			final RandomAccess< UnsignedByteType > access = restored.randomAccess();
			final long[] position = new long[ n ];
			for ( final Map.Entry< Integer, Integer > entry : kappas.entrySet() )
			{
				final int nodeNum = entry.getKey();
				final int value = entry.getValue();
				IntervalIndexer.indexToPosition( nodeNum, dimensions, position );
				access.setPosition( position );
				access.get().set( value );
			}

			ImageJFunctions.show( restored );
		}
	}

	public static void main( final String[] args ) throws ImgIOException
	{
		final String fn = "binary-noisy.tif";
		final UnsignedByteType type = new UnsignedByteType();
		final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
		final ImgOpener opener = new ImgOpener();
		final Img< UnsignedByteType > img = opener.openImg( fn, factory, type );

		new ImageJ();
		ImageJFunctions.show( img );
		ImageJFunctions.show( BinaryRestoration.binaryRestoration( img ), "graph cut" );

		final BinaryRestorationGraph graph = new BinaryRestorationGraph( img );
		final Map< Integer, Integer > solution = Algorithm.solve( graph, 1, new Algorithm.KappaUpdateFunction()
		{
			@Override
			public final int next( final int kappa )
			{
				return kappa + 1;
			}
		} );
		graph.showSolution( solution );
//		graph.showConflicts( ( Deque< Integer > ) Algorithm.conflictsRemaining );
		graph.showKappas( ( Map< Integer, Integer > ) Algorithm.kappasFinal );
	}
}
