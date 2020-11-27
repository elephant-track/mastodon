package org.mastodon.views.dbvv;

import java.util.function.Function;
import org.joml.Vector3fc;
import org.mastodon.RefPool;
import org.mastodon.collection.RefCollections;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.views.bvv.scene.Ellipsoid;
import org.mastodon.views.bvv.scene.EllipsoidMath;
import org.mastodon.views.bvv.scene.Ellipsoids;

public class DColoredEllipsoids
{
	private final ModelGraph graph;

	private final Ellipsoids ellipsoids = new Ellipsoids();

	private final EllipsoidMath math = new EllipsoidMath();

	/**
	 * Tracks potential modifications to ellipsoid colors because of {@code selectionChanged()} events, etc.
	 * These events do not immediately trigger a color update, because it would need to go through all timepoints whether or not they are currently painted.
	 * Rather, the {@link BvvRenderer} actively does the update when required.
	 */
	private int colorModCount = -1;

	public DColoredEllipsoids( final ModelGraph graph )
	{
		this.graph = graph;
	}

	public Ellipsoids getEllipsoids()
	{
		return ellipsoids;
	}

	public void addOrUpdate( final Spot vertex )
	{
		colorModCount = -1;
		final Ellipsoid ref = ellipsoids.createRef();
		final int key = graph.getGraphIdBimap().getVertexId( vertex );
		math.setFromVertex( vertex, ellipsoids.getOrAdd( key, ref ) );
		ellipsoids.releaseRef( ref );
	}

	public void remove( final Spot vertex )
	{
		final int key = graph.getGraphIdBimap().getVertexId( vertex );
		ellipsoids.remove( key );
	}

	public Spot getVertex( final Ellipsoid ellipsoid, final Spot ref )
	{
		return graph.getGraphIdBimap().getVertex( ellipsoids.keyOf( ellipsoid ), ref );
	}

	/**
	 * Returns the index of the ellisoid corresponding to {@code vertex}
	 * if {@code vertex} is represented in {@code ellipsoids}.
	 * Otherwise, returns -1.
	 */
	public int indexOf( final Spot vertex )
	{
		if ( vertex == null )
			return -1;

		final int key = graph.getGraphIdBimap().getVertexId( vertex );
		final Ellipsoid ref = ellipsoids.createRef();
		try
		{
			final Ellipsoid ellipsoid = ellipsoids.get( key, ref );
			return ( ellipsoid == null ) ? -1 : ellipsoid.getInternalPoolIndex();
		}
		finally
		{
			ellipsoids.releaseRef( ref );
		}
	}

	public void updateColors( final int modCount, final Function< Spot, Vector3fc > coloring )
	{
		if ( colorModCount != modCount )
		{
			colorModCount = modCount;
			final RefPool< Spot > vertexPool = RefCollections.tryGetRefPool( graph.vertices() );
			final Spot ref = vertexPool.createRef();
			for ( Ellipsoid ellipsoid : ellipsoids )
			{
				final Spot vertex = vertexPool.getObjectIfExists( ellipsoids.keyOf( ellipsoid ), ref );
				if ( vertex != null )
					ellipsoid.rgb.set( coloring.apply( vertex ) );
			}
			vertexPool.releaseRef( ref );
		}
	}
}
