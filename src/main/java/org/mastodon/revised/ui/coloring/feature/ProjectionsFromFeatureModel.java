package org.mastodon.revised.ui.coloring.feature;

import org.mastodon.feature.Feature;
import org.mastodon.feature.FeatureModel;
import org.mastodon.feature.FeatureProjection;
import org.mastodon.feature.FeatureProjectionSpec;
import org.mastodon.feature.FeatureSpec;

/**
 * Provides mapping from {@link FeatureProjectionId} to
 * {@link FeatureProjection}s declared in a {@link FeatureModel}.
 *
 * @author Tobias Pietzsch
 */
public class ProjectionsFromFeatureModel implements Projections
{
	private final FeatureModel featureModel;

	public ProjectionsFromFeatureModel( final FeatureModel featureModel )
	{
		this.featureModel = featureModel;
	}

	@Override
	public FeatureProjection< ? > getFeatureProjection( final FeatureProjectionId id )
	{
		final FeatureSpec< ?, ? > featureSpec = featureModel.getFeatureSpecs().stream()
				.filter( spec -> spec.getKey().equals( id.getFeatureKey() ) )
				.findFirst()
				.orElse( null );
		return getFeatureProjection( id, featureSpec );
	}

	@Override
	public < T > FeatureProjection< T > getFeatureProjection( final FeatureProjectionId id, final Class< T > target )
	{
		@SuppressWarnings( "unchecked" )
		final FeatureSpec< ?, T > featureSpec = ( FeatureSpec< ?, T > ) featureModel.getFeatureSpecs().stream()
				.filter( spec -> target.isAssignableFrom( spec.getTargetClass() ) )
				.filter( spec -> spec.getKey().equals( id.getFeatureKey() ) )
				.findFirst()
				.orElse( null );
		return getFeatureProjection( id, featureSpec );
	}

	private final < T > FeatureProjection< T > getFeatureProjection( final FeatureProjectionId id, final FeatureSpec< ?, T > featureSpec )
	{
		if ( featureSpec == null )
			return null;

		@SuppressWarnings( "unchecked" )
		final Feature< T > feature = ( Feature< T > ) featureModel.getFeature( featureSpec );
		if ( feature == null )
			return null;

		final FeatureProjectionSpec projectionSpec = featureSpec.getProjectionSpecs().stream()
				.filter( spec -> spec.getKey().equals( id.getProjectionKey() ) )
				.findFirst()
				.orElse( null );
		if ( projectionSpec == null )
			return null;

		final int[] sourceIndices;
		switch ( id.getMultiplicity() )
		{
		default:
		case SINGLE:
			sourceIndices = new int[] {};
			break;
		case ON_SOURCES:
			sourceIndices = new int[] { id.getI0() };
			break;
		case ON_SOURCE_PAIRS:
			sourceIndices = new int[] { id.getI0(), id.getI1() };
			break;
		}
		return feature.project( projectionSpec, sourceIndices );
	}
}
