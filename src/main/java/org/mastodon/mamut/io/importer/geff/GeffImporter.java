/*-
 * #%L
 * Mastodon
 * %%
 * Copyright (C) 2014 - 2026 Tobias Pietzsch, Jean-Yves Tinevez
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.mamut.io.importer.geff;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.options.N5FactoryOptions;
import org.mastodon.feature.Dimension;
import org.mastodon.feature.FeatureModel;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.io.importer.ModelImporter;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.properties.DoublePropertyMap;
import org.mastodon.properties.IntPropertyMap;

import org.mastodon.geff.GeffEdge;
import org.mastodon.geff.GeffMetadata;
import org.mastodon.geff.GeffNode;

public class GeffImporter extends ModelImporter
{

	/**
	 * The group of the Zarr container that holds the GEFF dataset.
	 */
	private static final String GROUP = "/";

	/**
	 * The number of standard deviations the ellipsoid of an imported spot is
	 * scaled to.
	 * <p>
	 * Mastodon uses the covariance of a spot as the matrix of the ellipsoid
	 * itself, so that a spot of radius {@code r} has the covariance
	 * {@code r²I} and the ellipsoid spans one standard deviation of the
	 * Gaussian the covariance describes. The GEFF spec defines no such
	 * convention for its {@code covariance2d}/{@code covariance3d} ellipsoid
	 * properties, which typically hold the plain covariance of a fit or of a
	 * segmented region, and whose one-sigma ellipsoid is much smaller than the
	 * object it describes. Scale by this many sigmas to match the default of
	 * the TGMM importer, which renders two-sigma ellipsoids as well.
	 *
	 * @see org.mastodon.mamut.io.importer.tgmm.TgmmImporter
	 */
	public static final double N_SIGMAS = 2;

	/**
	 * Imports the Geff Zarr dataset at {@code zarrPath} into the model of
	 * {@code projectModel}.
	 */
	public static void importGeff( final String zarrPath, final ProjectModel projectModel ) throws IOException
	{
		importGeff( zarrPath, projectModel.getModel() );
	}

	/**
	 * Imports the Geff Zarr dataset at {@code zarrPath} into {@code model}.
	 */
	public static void importGeff( final String zarrPath, final Model model ) throws IOException
	{
		new GeffImporter( model ).read( zarrPath );
	}

	private final Model model;

	private GeffImporter( final Model model )
	{
		super( model );
		this.model = model;
	}

	private void read( final String zarrPath ) throws IOException
	{
		final ModelGraph graph = model.getGraph();
		final FeatureModel featureModel = model.getFeatureModel();

		final Spot vRef1 = graph.vertexRef();
		final Spot vRef2 = graph.vertexRef();
		final Link eRef = graph.edgeRef();

		startImport();
		try
		{
			final GeffMetadata metadata;
			final List< GeffNode > nodes;
			final List< GeffEdge > edges;
			// The GeffXxx.readFromZarr() methods are hard-wired to a Zarr v2
			// reader, which finds no attributes at all in a Zarr v3 container.
			// Let the N5 universe factory pick the reader matching the Zarr
			// version of the container instead.
			try ( final N5Reader reader =
					new N5Factory().setOptions( new N5FactoryOptions().cacheAttributes( true ) ).openReader( zarrPath ) )
			{
				metadata = GeffMetadata.readFromN5( reader, GROUP );
				nodes = GeffNode.readFromN5( reader, GROUP, metadata );
				edges = GeffEdge.readFromN5( reader, GROUP, metadata.getGeffVersion() );
			}

			// Feature storage
			final GeffImportedSpotFeatures spotFeatures = new GeffImportedSpotFeatures();
			final GeffImportedLinkFeatures linkFeatures = new GeffImportedLinkFeatures();
			final String noUnits = Dimension.NONE.getUnits( model.getSpaceUnits(), model.getTimeUnits() );
			final IntPropertyMap< Spot > segmentIdMap = new IntPropertyMap<>( graph.vertices(), Integer.MIN_VALUE );
			final DoublePropertyMap< Link > scoreMap = new DoublePropertyMap<>( graph.edges(), Double.NaN );
			final DoublePropertyMap< Link > distanceMap = new DoublePropertyMap<>( graph.edges(), Double.NaN );

			// Import nodes → Spots
			final Map< Integer, Spot > spotMap = new HashMap<>( nodes.size() );
			final double[] pos = new double[ 3 ];
			for ( final GeffNode node : nodes )
			{
				pos[ 0 ] = node.getX();
				pos[ 1 ] = node.getY();
				// A dataset without a third spatial axis reports z as NaN;
				// put its spots into the z = 0 plane.
				pos[ 2 ] = Double.isNaN( node.getZ() ) ? 0 : node.getZ();

				final Spot spot = addSpot( graph, vRef1, node, pos );

				// Store segmentId as feature if set
				if ( node.getSegmentId() != 0 )
					segmentIdMap.set( spot, node.getSegmentId() );

				// Keep a copy of the Spot reference for link creation
				final Spot copy = graph.vertexRef();
				copy.refTo( spot );
				spotMap.put( node.getId(), copy );
			}

			// Import edges → Links
			for ( final GeffEdge edge : edges )
			{
				final Spot source = spotMap.get( edge.getSourceNodeId() );
				final Spot target = spotMap.get( edge.getTargetNodeId() );
				if ( source == null || target == null )
					continue;

				final Link link = graph.addEdge( source, target, eRef ).init();

				if ( edge.getScore() != GeffEdge.DEFAULT_SCORE )
					scoreMap.set( link, edge.getScore() );
				if ( edge.getDistance() != GeffEdge.DEFAULT_DISTANCE )
					distanceMap.set( link, edge.getDistance() );
			}

			// Release copied vertex refs
			for ( final Spot s : spotMap.values() )
				graph.releaseRef( s );

			// Register features
			spotFeatures.store( "Segment ID", Dimension.NONE, noUnits, segmentIdMap );
			linkFeatures.store( "Score", Dimension.NONE, noUnits, scoreMap );
			linkFeatures.store( "Distance", Dimension.NONE, noUnits, distanceMap );

			featureModel.pauseListeners();
			featureModel.declareFeature( spotFeatures );
			featureModel.declareFeature( linkFeatures );
		}
		finally
		{
			graph.releaseRef( vRef1 );
			graph.releaseRef( vRef2 );
			graph.releaseRef( eRef );
			model.getFeatureModel().resumeListeners();
			finishImport();
		}
	}

	/**
	 * Adds a {@link Spot} for {@code node}, shaping it from the node's
	 * covariance matrix when available, falling back to a radius otherwise.
	 */
	private static Spot addSpot( final ModelGraph graph, final Spot vRef, final GeffNode node, final double[] pos )
	{
		final double[][] cov = covariance( node );
		return cov != null
				? graph.addVertex( vRef ).init( node.getT(), pos, cov )
				: graph.addVertex( vRef ).init( node.getT(), pos, effectiveRadius( node ) );
	}

	/**
	 * The 3×3 covariance matrix to shape the spot of {@code node} with, or
	 * {@code null} if the node carries no shape information.
	 * <p>
	 * {@code covariance3d} takes precedence; {@code covariance2d} is used for
	 * datasets without a third spatial axis, which declare that one only. An
	 * identity matrix is what Geff falls back to for a covariance the dataset
	 * does not store, and carries no shape information either way.
	 * <p>
	 * The returned matrix is scaled to {@link #N_SIGMAS} sigmas. A radius, in
	 * contrast, is a length rather than a standard deviation and is used
	 * unscaled; the two agree for a dataset whose radius is the extent its
	 * covariance describes.
	 */
	private static double[][] covariance( final GeffNode node )
	{
		final double[] cov3d = node.getCovariance3d();
		if ( cov3d != null )
		{
			if ( cov3d.length != 9 )
				throw new IllegalArgumentException(
						"GEFF node covariance3d must be the 9 elements of a row-major 3×3 matrix, but got length "
								+ cov3d.length );
			if ( !Arrays.equals( cov3d, GeffNode.DEFAULT_COVARIANCE_3D ) )
				return scale( flatToMatrix3x3( cov3d ), N_SIGMAS * N_SIGMAS );
		}

		final double[] cov2d = node.getCovariance2d();
		if ( cov2d != null )
		{
			if ( cov2d.length != 4 )
				throw new IllegalArgumentException(
						"GEFF node covariance2d must be the 4 elements of a row-major 2×2 matrix, but got length "
								+ cov2d.length );
			if ( !Arrays.equals( cov2d, GeffNode.DEFAULT_COVARIANCE_2D ) )
				return scale( flat2x2ToMatrix3x3( cov2d ), N_SIGMAS * N_SIGMAS );
		}

		return null;
	}

	private static double effectiveRadius( final GeffNode node )
	{
		return node.getRadius() > 0 ? node.getRadius() : GeffNode.DEFAULT_RADIUS;
	}

	/**
	 * Multiplies {@code m} by {@code factor} in place and returns it.
	 */
	private static double[][] scale( final double[][] m, final double factor )
	{
		for ( final double[] row : m )
			for ( int c = 0; c < row.length; ++c )
				row[ c ] *= factor;
		return m;
	}

	/**
	 * Unflattens the 9 elements {@code [c0,...,c8]} of the row-major 3×3
	 * covariance matrix used by Geff:
	 * <pre>
	 * [[c0, c1, c2],
	 *  [c3, c4, c5],
	 *  [c6, c7, c8]]
	 * </pre>
	 */
	static double[][] flatToMatrix3x3( final double[] c )
	{
		return new double[][] {
				{ c[ 0 ], c[ 1 ], c[ 2 ] },
				{ c[ 3 ], c[ 4 ], c[ 5 ] },
				{ c[ 6 ], c[ 7 ], c[ 8 ] }
		};
	}

	/**
	 * Expands the 4 elements {@code [c0,...,c3]} of the row-major 2×2
	 * covariance matrix used by Geff to a 3×3 matrix:
	 * <pre>
	 * [[c0, c1, 0 ],
	 *  [c2, c3, 0 ],
	 *  [ 0,  0, zz]]
	 * </pre>
	 * A dataset that stores a 2D covariance says nothing about the extent
	 * along z, so {@code zz} is the mean of the two in-plane variances, which
	 * makes the spot about as thick as it is wide and keeps the matrix
	 * positive definite.
	 */
	static double[][] flat2x2ToMatrix3x3( final double[] c )
	{
		final double zz = 0.5 * ( c[ 0 ] + c[ 3 ] );
		return new double[][] {
				{ c[ 0 ], c[ 1 ], 0 },
				{ c[ 2 ], c[ 3 ], 0 },
				{ 0, 0, zz }
		};
	}
}
