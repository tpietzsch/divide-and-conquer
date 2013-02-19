package divide;

import graphcut.GraphCut;
import graphcut.GraphCut.Terminal;

import java.util.Arrays;
import java.util.HashMap;
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

public class BinaryRestoration
{
	static final float pottsWeight = 1;

	public static Map< Integer, Integer > binaryRestoration( final RandomAccessibleInterval< UnsignedByteType > img, final Set< Integer > region )
	{
		final int n = img.numDimensions();
		final long[] dimensions = new long[ n ];
		img.dimensions( dimensions );

		final long numNodes = region.size();
		final long numEdges = n * numNodes;

		final GraphCut graphCut = new GraphCut( ( int ) numNodes, ( int ) numEdges );

		final HashMap< Integer, Integer > variableToGraphCutNode = new HashMap< Integer, Integer >();
		int j = 0;
		for ( final int variable : region )
			variableToGraphCutNode.put( variable, j++ );

		// set terminal weights
		final RandomAccess< UnsignedByteType > a = img.randomAccess();
		final long[] position = new long[ n ];
		for ( final int variable : region )
		{
			IntervalIndexer.indexToPosition( variable, dimensions, position );
			a.setPosition( position );
			final int Ipo = ( a.get().get() <= 0 ) ? 0 : 1;
			final float source = 1 - Ipo;
			final float sink = Ipo;
			final int nodeNum = variableToGraphCutNode.get( variable );
			graphCut.setTerminalWeights( nodeNum, source, sink );
		}

		// set edge weights
		final int[][] neighborOffsets;
		neighborOffsets = new int[ n ][ n ];
		for ( int d = 0; d < n; d++ )
		{
			Arrays.fill( neighborOffsets[ d ], 0 );
			neighborOffsets[ d ][ d ] = 1;
		}
		final int numNeighbors = neighborOffsets.length;
		for ( final int variable : region )
		{
			A: for ( int i = 0; i < numNeighbors; ++i )
			{
				IntervalIndexer.indexToPosition( variable, dimensions, position );
				for ( int d = 0; d < n; ++d )
				{
					position[ d ] += neighborOffsets[ i ][ d ];
					if ( position[ d ] < 0 || position[ d ] >= dimensions[ d ] )
						continue A;
				}
				final Integer neighborVariable = new Integer( ( int ) IntervalIndexer.positionToIndex( position, dimensions ) );
				if ( region.contains( neighborVariable ) )
				{
					final int nodeNum = variableToGraphCutNode.get( variable );
					final int neighborNum = variableToGraphCutNode.get( neighborVariable );
					graphCut.setEdgeWeight( nodeNum, neighborNum, pottsWeight );
				}
			}
		}

		graphCut.computeMaximumFlow( false, null );

		// create solution
		final Map< Integer, Integer > solution = new HashMap< Integer, Integer >();
		for ( final int variable : region )
		{
			final int nodeNum = variableToGraphCutNode.get( variable );
			if ( graphCut.getTerminal( nodeNum ) == Terminal.FOREGROUND )
				solution.put( variable, 0 );
			else
				solution.put( variable, 1 );
		}

		return solution;
	}

	public static final Img< UnsignedByteType > binaryRestoration( final RandomAccessibleInterval< UnsignedByteType > img )
	{
		final int n = img.numDimensions();
		final long[] dimensions = new long[ n ];
		img.dimensions( dimensions );

		final long numNodes = Intervals.numElements( img );

		// (four-connected)
		long numEdges = 0;
		for ( int d = 0; d < n; d++ )
			numEdges += numNodes - numNodes / dimensions[ d ];

		final GraphCut graphCut = new GraphCut( ( int ) numNodes, ( int ) numEdges + 1000 );

		// set terminal weights
		final RandomAccess< UnsignedByteType > a = img.randomAccess();
		final long[] position = new long[ n ];
		for ( long nodeNum = 0; nodeNum < numNodes; ++nodeNum )
		{
			IntervalIndexer.indexToPosition( nodeNum, dimensions, position );
			a.setPosition( position );
			final int Ipo = ( a.get().get() <= 0 ) ? 0 : 1;
			final float source = 1 - Ipo;
			final float sink = Ipo;
			graphCut.setTerminalWeights( ( int ) nodeNum, source, sink );
		}

		// set edge weights
		final int[][] neighborOffsets;
		neighborOffsets = new int[ n ][ n ];
		for ( int d = 0; d < n; d++ )
		{
			Arrays.fill( neighborOffsets[ d ], 0 );
			neighborOffsets[ d ][ d ] = 1;
		}
		final int numNeighbors = neighborOffsets.length;
		for ( long nodeNum = 0; nodeNum < numNodes; ++nodeNum )
		{
			A: for ( int i = 0; i < numNeighbors; ++i )
			{
				IntervalIndexer.indexToPosition( nodeNum, dimensions, position );
				for ( int d = 0; d < n; ++d )
				{
					position[ d ] += neighborOffsets[ i ][ d ];
					if ( position[ d ] < 0 || position[ d ] >= dimensions[ d ] )
						continue A;
				}
				final long neighborNum = IntervalIndexer.positionToIndex( position, dimensions );
				graphCut.setEdgeWeight( ( int ) nodeNum, ( int ) neighborNum, pottsWeight );
			}
		}

		graphCut.computeMaximumFlow( false, null );

		// create segmentation image
		final UnsignedByteType type = new UnsignedByteType();
		final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
		final Img< UnsignedByteType > restored = factory.create( img, type );

		final RandomAccess< UnsignedByteType > access = restored.randomAccess();
		for ( long nodeNum = 0; nodeNum < numNodes; ++nodeNum )
		{
			IntervalIndexer.indexToPosition( nodeNum, dimensions, position );
			access.setPosition( position );
			if ( graphCut.getTerminal( ( int ) nodeNum ) == Terminal.FOREGROUND )
				access.get().set( 0 );
			else
				access.get().set( 255 );
		}
		return restored;
	}

	public static void main( final String[] args ) throws ImgIOException
	{
		final String fn = "/Users/tobias/workspace/data/binary-noisy.tif";
		final UnsignedByteType type = new UnsignedByteType();
		final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
		final ImgOpener opener = new ImgOpener();
		final Img< UnsignedByteType > img = opener.openImg( fn, factory, type );

		ImageJFunctions.show( img );
		final Img< UnsignedByteType > restored = binaryRestoration( img );
		ImageJFunctions.show( restored );
	}
}
