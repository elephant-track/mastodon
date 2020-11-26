package org.mastodon.views.dbvv;

import com.jogamp.opengl.GL3;
import net.imglib2.realtransform.AffineTransform3D;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.model.FocusModel;
import org.mastodon.model.HighlightModel;
import org.mastodon.model.SelectionModel;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.views.bvv.scene.Cylinder;
import org.mastodon.views.bvv.scene.CylinderMath;
import org.mastodon.views.bvv.scene.Cylinders;
import org.mastodon.views.bvv.scene.Ellipsoid;
import org.mastodon.views.bvv.scene.EllipsoidMath;
import org.mastodon.views.bvv.scene.Ellipsoids;
import org.mastodon.views.bvv.scene.InstancedLink;
import org.mastodon.views.bvv.scene.InstancedSpot;
import org.mastodon.views.bvv.scene.InstancedSpot.SpotDrawingMode;

import static com.jogamp.opengl.GL.GL_BACK;
import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_CCW;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_CULL_FACE;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ONE;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static org.mastodon.views.bvv.scene.InstancedSpot.SpotDrawingMode.ELLIPSOIDS;

public class DBvvRenderer
{
	private final ModelGraph graph;

	private final SpatioTemporalIndex< Spot > index;

	private final DBvvEntities entities;

	private final SelectionModel< Spot, Link > selection;

	private final HighlightModel< Spot, Link > highlight;

	private final FocusModel< Spot, Link > focus;

	private final GraphColorGenerator< Spot, Link > graphColorGenerator;

	// TODO...
	private final double dCam = 2000;
	private final double dClip = 1000;

	private double screenWidth;
	private double screenHeight;

	private final InstancedSpot instancedSpot;
	private final InstancedLink instancedLink;

	public DBvvRenderer(
			final int screenWidth,
			final int screenHeight,
			final ModelGraph graph,
			final SpatioTemporalIndex< Spot > index, // TODO appModel.getModel().getSpatioTemporalIndex(),
			final DBvvEntities entities,
			final SelectionModel< Spot, Link > selection,
			final HighlightModel< Spot, Link > highlight,
			final FocusModel< Spot, Link > focus,
			final GraphColorGenerator< Spot, Link > graphColorGenerator )
	{
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.graph = graph;
		this.index = index;
		this.entities = entities;
		this.selection = selection;
		this.highlight = highlight;
		this.focus = focus;
		this.graphColorGenerator = graphColorGenerator;
		highlights = new Highlights();
		instancedSpot = new InstancedSpot( 3, 10 );
		instancedLink = new InstancedLink( 36, 3, 20 );
		selection.listeners().add( this::selectionChanged );
	}

	public void setScreenSize( final double screenWidth, final double screenHeight )
	{
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
	}

	public void init( final GL3 gl )
	{
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );
	}

	private class Highlights
	{
		final Vector3fc HIGHLIGHT_COLOR = new Vector3f( 0, 1, 1 );
		final Vector3fc FOCUS_COLOR = new Vector3f( 0, 1, 0 );
		final Vector3fc HIGHLIGHT_FOCUS_COLOR = new Vector3f( 0.5f, 1.5f, 1 );

		final Ellipsoids highlightedVertices = new Ellipsoids( 2 );
		final EllipsoidMath ellipsoidMath = new EllipsoidMath();

		final Cylinders highlightedEdges = new Cylinders( 2 );
		final CylinderMath cylinderMath = new CylinderMath( graph );

		boolean isEmpty()
		{
			return highlightedVertices.size() == 0 && highlightedEdges.size() == 0;
		}

		void update( final SceneRenderData renderData )
		{
			final int timepoint = renderData.getTimepoint();
			final int timeLimit = renderData.getLinkTimeLimit();

			final Spot vref = graph.vertexRef();
			final Spot vref2 = graph.vertexRef();
			final Link eref = graph.edgeRef();
			final Ellipsoid elref = highlightedVertices.createRef();
			final Cylinder cyref = highlightedEdges.createRef();

			// ----------------------------------------------------

			highlightedVertices.clear();

			final Spot hVertex = highlight.getHighlightedVertex( vref );
			final Spot fVertex = focus.getFocusedVertex( vref2 );
			final boolean hfSame = hVertex != null && hVertex.equals( fVertex );
			if ( hVertex != null && hVertex.getTimepoint() == timepoint )
			{
				final Ellipsoid ellipsoid = highlightedVertices.getOrAdd( 0, elref );
				ellipsoidMath.setFromVertex( hVertex, ellipsoid );
				ellipsoid.rgb.set( hfSame ? HIGHLIGHT_FOCUS_COLOR : HIGHLIGHT_COLOR );
			}
			if ( fVertex != null && fVertex.getTimepoint() == timepoint && !hfSame )
			{
				final Ellipsoid ellipsoid = highlightedVertices.getOrAdd( 1, elref );
				ellipsoidMath.setFromVertex( fVertex, ellipsoid );
				ellipsoid.rgb.set( FOCUS_COLOR );
			}

			// ----------------------------------------------------

			highlightedEdges.clear();

			final Link edge = highlight.getHighlightedEdge( eref );
			if ( edge != null )
			{
				final int et = edge.getTarget( vref ).getTimepoint();
				if ( et <= timepoint && et > timepoint - timeLimit )
				{
					final Cylinder cylinder = highlightedEdges.getOrAdd( 0, cyref );
					cylinderMath.setFromEdge( edge, cylinder );
					cylinder.rgb.set( HIGHLIGHT_COLOR );
				}
			}

			graph.releaseRef( vref );
			graph.releaseRef( vref2 );
			graph.releaseRef( eref );
			highlightedVertices.releaseRef( elref );
			highlightedEdges.releaseRef( cyref );
		}
	}

	private final Highlights highlights;

	private final SceneRenderData renderData = new SceneRenderData();

	public void display(
			final GL3 gl,
			final AffineTransform3D worldToScreen,
			final int timepoint )
	{
		final SceneRenderData data = new SceneRenderData( timepoint, worldToScreen, dCam, dClip, dClip, screenWidth, screenHeight );
		renderData.set( data );
		final Matrix4fc pv = data.getPv();
		final Matrix4fc camview = data.getCamview();

		gl.glClearColor( 0.0f, 0.0f, 0.0f, 0.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		gl.glEnable( GL_DEPTH_TEST );
		gl.glEnable( GL_CULL_FACE );
		gl.glCullFace( GL_BACK );
		gl.glFrontFace( GL_CCW );

		final Spot vref = graph.vertexRef();
		final Link eref = graph.edgeRef();
		final Spot sref = graph.vertexRef();
		final Spot tref = graph.vertexRef();

		// -- paint vertices --------------------------------------------------
		final DColoredEllipsoids ellipsoids = entities.forTimepoint( timepoint ).ellipsoids;
		final Vector3fc defaultColor = new Vector3f( 0.7f, 0.7f, 0.7f );
		final Vector3fc selectedColor = new Vector3f( 1.0f, 0.7f, 0.7f );
		final Vector3f tmp = new Vector3f();
		ellipsoids.updateColors( colorModCount, v -> {
			if ( selection.isSelected( v ) )
				return selectedColor;
			final int i = graphColorGenerator.color( v );
			return i == 0
					? defaultColor
					: colorToVertex3f( i, tmp );
		} );

		int highlightId = ellipsoids.indexOf( highlight.getHighlightedVertex( vref ) );

		instancedSpot.draw( gl, pv, camview,
				ellipsoids.getEllipsoids(), highlightId,
				data.getSpotDrawingMode(), data.getSpotRadius(), false );


		// -- paint edges -----------------------------------------------------
		// the maximum number of time-points into the past for which outgoing edges are painted
		final int timeLimit = renderData.getLinkTimeLimit();
		final float rHead = renderData.getLinkRadiusHead();
		final float rTail = renderData.getLinkRadiusTail();
		final float f = ( rTail - rHead ) / ( timeLimit + 1 );

		for ( int t = Math.max( 0, timepoint - timeLimit + 1 ); t <= timepoint; ++t )
		{
			final DColoredCylinders cylinders = entities.forTimepoint( t ).cylinders;
			cylinders.updateColors( colorModCount, e -> {
				if ( selection.isSelected( e ) )
					return selectedColor;
				final int i = graphColorGenerator.color( e, e.getSource( sref ), e.getTarget( tref ) );
				return i == 0
						? defaultColor
						: colorToVertex3f( i, tmp );
			} );
			highlightId = cylinders.indexOf( highlight.getHighlightedEdge( eref ) );
			final float r0 = rHead + f * ( timepoint - t );
			instancedLink.draw( gl, pv, camview,
					cylinders.getCylinders(), highlightId, r0 + f, r0, false );
		}


		// -- paint highlighted vertices and edges ----------------------------------------
		highlights.update( renderData );
		if ( !highlights.isEmpty() )
		{
			gl.glEnable( GL_BLEND );
			gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE );
//			gl.glDisable( GL_DEPTH_TEST );
			if ( highlights.highlightedVertices.size() > 0 )
			{
				instancedSpot.draw( gl, pv, camview,
						highlights.highlightedVertices, 0,
						data.getSpotDrawingMode(), data.getSpotRadius(), true );
			}
			if ( highlights.highlightedEdges.size() > 0 )
			{
				final int t = highlight.getHighlightedEdge( eref ).getTarget( sref ).getTimepoint();
				final float r0 = rHead + f * ( timepoint - t );
				instancedLink.draw( gl, pv, camview,
						highlights.highlightedEdges, 0, r0 + f, r0, true );
			}
			gl.glDisable( GL_BLEND );
		}


		graph.releaseRef( vref );
		graph.releaseRef( eref );
		graph.releaseRef( sref );
		graph.releaseRef( tref );
	}

	private static Vector3fc colorToVertex3f( final int argb, final Vector3f dest )
	{
		final int a0 = ( argb >> 24 ) & 0xff;
		final int r0 = ( argb >> 16 ) & 0xff;
		final int g0 = ( argb >> 8 ) & 0xff;
		final int b0 = ( argb ) & 0xff;

		final float a = a0 / 255f;
		final float r = r0 / 255f;
		final float g = g0 / 255f;
		final float b = b0 / 255f;

		return dest.set( r, g, b );
	}

	private int colorModCount = 1;

	private void selectionChanged()
	{
		++colorModCount;
	}

	public void colorsChanged()
	{
		++colorModCount;
	}







	// ---------------------------------------------------------
	// -- find closest objects for highlighting and selecting --
	// ---------------------------------------------------------

	public final class Closest< T >
	{
		private final T t;

		private final float distance;

		public Closest( final T t, final float distance )
		{
			this.t = t;
			this.distance = distance;
		}

		public T get()
		{
			return t;
		}

		public float distance()
		{
			return distance;
		}
	}

	/**
	 * Returns the vertex currently painted close to the specified location.
	 * <p>
	 * It is the responsibility of the caller to lock the graph it inspects for
	 * reading operations, prior to calling this method. A typical call from
	 * another method would happen like this:
	 *
	 * <pre>
	 * ReentrantReadWriteLock lock = graph.getLock();
	 * lock.readLock().lock();
	 * try
	 * {
	 * 	V vertex = renderer.getVertexAt( x, y, POINT_SELECT_DISTANCE_TOLERANCE, ref );
	 * 	... // do something with the vertex
	 * 	... // vertex is guaranteed to stay valid while the lock is held
	 * }
	 * finally
	 * {
	 * 	lock.readLock().unlock();
	 * }
	 * </pre>
	 *
	 * @param x
	 *            the x location to search, in viewer coordinates (screen).
	 * @param y
	 *            the y location to search, in viewer coordinates (screen).
	 * @param tolerance
	 *            the distance tolerance to accept close vertices.
	 * @param ref
	 *            a vertex reference, that might be used to return the vertex
	 *            found.
	 * @return the closest vertex within tolerance, or <code>null</code> if it
	 *         could not be found.
	 */
	public Closest< Spot > getVertexAt( final int x, final int y, final double tolerance, final Spot ref )
	{
		// TODO: graph locking?
		// TODO: KDTree clipping to region around ray.

		final SceneRenderData data = renderData.copy();
		final SpotDrawingMode spotDrawingMode = data.getSpotDrawingMode();
		final float spotRadius = data.getSpotRadius();

		final int timepoint = data.getTimepoint();
		final int w = ( int ) data.getScreenWidth();
		final int h = ( int ) data.getScreenHeight();
		final int[] viewport = { 0, 0, w, h };

		final DColoredEllipsoids ellipsoids = entities.forTimepoint( timepoint ).ellipsoids;

		// ray through pixel
		final Matrix4f pvinv = new Matrix4f();
		final Vector3f pNear = new Vector3f();
		final Vector3f pFarMinusNear = new Vector3f();

		data.getPv().invert( pvinv );
		pvinv.unprojectInv( x + 0.5f, h - y - 0.5f, 0, viewport, pNear );
		pvinv.unprojectInv( x + 0.5f, h - y - 0.5f, 1, viewport, pFarMinusNear ).sub( pNear );

		final Matrix3f inve = new Matrix3f();
		final Vector3f t = new Vector3f();
		final Vector3f a = new Vector3f();
		final Vector3f b = new Vector3f();
		final Vector3f a1 = new Vector3f();

		float bestDist = Float.POSITIVE_INFINITY;
		Spot best = null;

		for ( final Ellipsoid ellipsoid : ellipsoids.getEllipsoids() )
		{
			if ( spotDrawingMode == ELLIPSOIDS )
			{
				ellipsoid.invte.get( inve ).transpose();
				inve.transform( ellipsoid.t.get( t ).sub( pNear ), a );
				inve.transform( pFarMinusNear, b );
			}
			else // if ( spotDrawingMode == SPHERES )
			{
				ellipsoid.t.get( t ).sub( pNear ).div( spotRadius, a );
				pFarMinusNear.div( spotRadius, b );
			}

			final float abbb = a.dot( b ) / b.dot( b );
			b.mul( abbb, a1 );
			final float aa1squ = a.sub( a1 ).lengthSquared();
			if ( aa1squ <= 1.0f )
			{
				final float dx = ( float ) Math.sqrt( ( 1.0 - aa1squ ) / b.lengthSquared() );
				final float d = abbb > dx ? abbb - dx : abbb + dx;
				if ( d >= 0 && d <= 1 )
				{
					if ( d <= bestDist )
					{
						bestDist = d;
						best = ellipsoids.getVertex( ellipsoid, ref );
					}
				}
			}
		}
		return new Closest<>( best, bestDist );
	}

	/**
	 * Returns the edge currently painted close to the specified location.
	 * <p>
	 * It is the responsibility of the caller to lock the graph it inspects for
	 * reading operations, prior to calling this method. A typical call from
	 * another method would happen like this:
	 *
	 * <pre>
	 * ReentrantReadWriteLock lock = graph.getLock();
	 * lock.readLock().lock();
	 * boolean found = false;
	 * try
	 * {
	 * 	E edge = renderer.getEdgeAt( x, y, EDGE_SELECT_DISTANCE_TOLERANCE, ref )
	 * 	... // do something with the edge
	 * 	... // edge is guaranteed to stay valid while the lock is held
	 * }
	 * finally
	 * {
	 * 	lock.readLock().unlock();
	 * }
	 * </pre>
	 *
	 * @param x
	 *            the x location to search, in viewer coordinates (screen).
	 * @param y
	 *            the y location to search, in viewer coordinates (screen).
	 * @param tolerance
	 *            the distance tolerance to accept close edges.
	 * @param ref
	 *            an edge reference, that might be used to return the vertex
	 *            found.
	 * @return the closest edge within tolerance, or <code>null</code> if it
	 *         could not be found.
	 */
	public Closest< Link > getEdgeAt( final int x, final int y, final double tolerance, final Link ref )
	{
		// TODO: graph locking?
		// TODO: prune candidate edges by some fast lookup structure

		final SceneRenderData data = renderData.copy();

		final int timepoint = data.getTimepoint();
		final int w = ( int ) data.getScreenWidth();
		final int h = ( int ) data.getScreenHeight();
		final int[] viewport = { 0, 0, w, h };

		// ray through pixel
		final Matrix4f pvinv = new Matrix4f();
		final Vector3f pNear = new Vector3f();
		final Vector3f pFar = new Vector3f();

		data.getPv().invert( pvinv );
		pvinv.unprojectInv( x + 0.5f, h - y - 0.5f, 0, viewport, pNear );
		pvinv.unprojectInv( x + 0.5f, h - y - 0.5f, 1, viewport, pFar );

		// TODO: these should come from SceneRenderData
		// the maximum number of time-points into the past for which outgoing edges are painted
		final int timeLimit = renderData.getLinkTimeLimit();
		final float rHead = renderData.getLinkRadiusHead();
		final float rTail = renderData.getLinkRadiusTail();
		final float f = ( rTail - rHead ) / ( timeLimit + 1 );

		final Spot vref = graph.vertexRef(); // TODO release
		final float[] spos = new float[ 3 ];
		final float[] tpos = new float[ 3 ];
		final Vector3f vspos = new Vector3f();
		final Vector3f vtpos = new Vector3f();

		float bestDist = Float.POSITIVE_INFINITY;
		Link best = null;

		final EdgeDistance edgeDistance = new EdgeDistance( pNear, pFar );
		for ( int t = Math.max( 0, timepoint - timeLimit + 1 ); t <= timepoint; ++t )
		{
			final float r1 = rHead + f * ( timepoint - t + 1 );
			final SpatialIndex< Spot > si = index.getSpatialIndex( t );
			for ( final Spot target : si )
			{
				target.localize( tpos );
				vtpos.set( tpos );
				for ( final Link edge : target.incomingEdges() )
				{
					final Spot source = edge.getSource( vref );
					source.localize( spos );
					vspos.set( spos );
					final float d = edgeDistance.to( vspos, vtpos, r1 );
					if ( d < bestDist )
					{
						bestDist = d;
						best = ref.refTo( edge );
					}
				}
			}
		}
		return new Closest<>( best, bestDist );
	}

	private static class EdgeDistance
	{
		private static final float SMALL_NUM = 1e-08f;

		private final Vector3fc p0;
		private final Vector3fc p1;
		private final Vector3fc u;
		private final float a;
		private final Vector3f v;
		private final Vector3f w;

		public EdgeDistance( final Vector3fc pNear, final Vector3fc pFar )
		{
			this.p0 = pNear;
			this.p1 = pFar;
			u = p1.sub( p0, new Vector3f() ); // always >= 0
			a = u.dot( u );
			v = new Vector3f();
			w = new Vector3f();
		}

		/**
		 * Adapted from:
		 * https://geomalgorithms.com/a07-_distance.html#dist3D_Segment_to_Segment()
		 *
		 * Copyright 2001 softSurfer, 2012 Dan Sunday
		 * This code may be freely used and modified for any purpose
		 * providing that this copyright notice is included with it.
		 * SoftSurfer makes no warranty for this code, and cannot be held
		 * liable for any real or imagined damage resulting from its use.
		 * Users of this code must verify correctness for their application.
		 *
		 * @param q0
		 * 		start point of segment S2
		 * @param q1
		 * 		end point of segment S2
		 *
		 * @return the shortest distance between S1 and S2
		 */
		private float to( final Vector3fc q0, final Vector3fc q1, final float tolerance )
		{
			// TODO use the fact that S1 is always pNear, pFar for one frame
			q1.sub( q0, v );
			p0.sub( q0, w );
			final float b = u.dot( v );
			final float c = v.dot( v ); // always >= 0
			final float d = u.dot( w );
			final float e = v.dot( w );
			final float D = a * c - b * b; // always >= 0
			float sc, sN, sD = D; // sc = sN / sD, default sD = D >= 0
			float tc, tN, tD = D; // tc = tN / tD, default tD = D >= 0

			// compute the line parameters of the two closest points
			if ( D < SMALL_NUM ) // the lines are almost parallel
			{
				sN = 0f; // force using point P0 on segment S1
				sD = 1f; // to prevent possible division by 0.0 later
				tN = e;
				tD = c;
			}
			else // get the closest points on the infinite lines
			{
				sN = ( b * e - c * d );
				tN = ( a * e - b * d );
				if ( sN < 0f ) // sc < 0 => the s=0 edge is visible
				{
					sN = 0f;
					tN = e;
					tD = c;
				}
				else if ( sN > sD ) // sc > 1  => the s=1 edge is visible
				{
					sN = sD;
					tN = e + b;
					tD = c;
				}
			}

			if ( tN < 0f ) // tc < 0 => the t=0 edge is visible
			{
				tN = 0f;
				// recompute sc for this edge
				if ( -d < 0f )
					sN = 0f;
				else if ( -d > a )
					sN = sD;
				else
				{
					sN = -d;
					sD = a;
				}
			}
			else if ( tN > tD ) // tc > 1 => the t=1 edge is visible
			{
				tN = tD;
				// recompute sc for this edge
				if ( ( -d + b ) < 0.0 )
					sN = 0;
				else if ( ( -d + b ) > a )
					sN = sD;
				else
				{
					sN = ( -d + b );
					sD = a;
				}
			}
			// finally do the division to get sc and tc
			sc = ( Math.abs( sN ) < SMALL_NUM ? 0f : sN / sD );
			tc = ( Math.abs( tN ) < SMALL_NUM ? 0f : tN / tD );

			// get the difference of the two closest points
			final Vector3f dP = w.add( u.mul( sc, new Vector3f() ).sub( v.mul( tc ) ) ); // =  S1(sc) - S2(tc)

			if ( dP.length() < tolerance )
				return sc;
			return Float.POSITIVE_INFINITY;
		}
	}
}
