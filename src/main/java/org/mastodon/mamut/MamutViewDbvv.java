package org.mastodon.mamut;

import bdv.BigDataViewerActions;
import bdv.tools.InitializeViewerState;
import java.awt.Dimension;
import javax.swing.ActionMap;
import net.imglib2.realtransform.AffineTransform3D;
import org.mastodon.app.ui.MastodonFrameViewActions;
import org.mastodon.app.ui.ViewMenu;
import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.app.ui.ViewMenuBuilder.JMenuHandle;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.ui.SelectionActions;
import org.mastodon.ui.coloring.ColoringModel;
import org.mastodon.ui.coloring.GraphColorGeneratorAdapter;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.mastodon.views.bdv.SharedBigDataViewerData;
import org.mastodon.views.bvv.BvvOptions;
import org.mastodon.views.dbvv.DBvvHighlightHandler;
import org.mastodon.views.dbvv.DBvvPanel;
import org.mastodon.views.dbvv.DBvvSelectionBehaviours;
import org.mastodon.views.dbvv.DBvvViewFrame;
import org.mastodon.views.dbvv.IdentityViewGraph;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.separator;
import static org.mastodon.mamut.MamutMenuBuilder.colorMenu;
import static org.mastodon.mamut.MamutMenuBuilder.editMenu;
import static org.mastodon.mamut.MamutMenuBuilder.fileMenu;
import static org.mastodon.mamut.MamutMenuBuilder.tagSetMenu;
import static org.mastodon.mamut.MamutMenuBuilder.viewMenu;

public class MamutViewDbvv extends MamutView< IdentityViewGraph< ModelGraph, Spot, Link >, Spot, Link >
{
	// TODO
	private static int bvvName = 1;

	private final SharedBigDataViewerData sharedBdvData;

	private final DBvvPanel viewer;

	public MamutViewDbvv( final MamutAppModel appModel )
	{
		super( appModel,
				new IdentityViewGraph<>( appModel.getModel().getGraph() ),
				new String[] { KeyConfigContexts.BIGDATAVIEWER } );

		sharedBdvData = appModel.getSharedBdvData();

		final GraphColorGeneratorAdapter< Spot, Link, Spot, Link > coloring =
				new GraphColorGeneratorAdapter<>( viewGraph.getVertexMap(), viewGraph.getEdgeMap() );

		final String windowTitle = "Unwrapped BigVolumeViewer " + ( bvvName++ );
		DBvvViewFrame frame = new DBvvViewFrame(
				windowTitle,
				viewGraph.getGraph(),
				appModel.getModel().getSpatioTemporalIndex(),
				selectionModel,
				highlightModel,
				focusModel,
				coloring,
				sharedBdvData.getSources(),
				sharedBdvData.getNumTimepoints(),
				sharedBdvData.getCache(),
				groupHandle,
				sharedBdvData.getOptions(),
				BvvOptions.options() );
		setFrame( frame );
		viewer = frame.getBvvPanel();

		// initialize transform
		final Dimension dim = viewer.getDisplay().getSize();
		final AffineTransform3D viewerTransform = InitializeViewerState.initTransform( dim.width, dim.height, false, viewer.state() );
		viewer.state().setViewerTransform( viewerTransform );

		frame.getBvvPanel().getTransformEventHandler().install( viewBehaviours );

		MastodonFrameViewActions.install( viewActions, this );
		viewer.getTransformEventHandler().install( viewBehaviours );

		final ViewMenu menu = new ViewMenu( this );
		final ActionMap actionMap = frame.getKeybindings().getConcatenatedActionMap();

		final JMenuHandle menuHandle = new JMenuHandle();
		final JMenuHandle tagSetMenuHandle = new JMenuHandle();
		MainWindow.addMenus( menu, actionMap );
		MamutMenuBuilder.build( menu, actionMap,
				fileMenu(
						separator(),
						item( BigDataViewerActions.LOAD_SETTINGS ),
						item( BigDataViewerActions.SAVE_SETTINGS )
				),
				viewMenu(
						colorMenu( menuHandle ),
						separator(),
						item( MastodonFrameViewActions.TOGGLE_SETTINGS_PANEL )
				),
				editMenu(
						item( UndoActions.UNDO ),
						item( UndoActions.REDO ),
						separator(),
						item( SelectionActions.DELETE_SELECTION ),
						item( SelectionActions.SELECT_WHOLE_TRACK ),
						item( SelectionActions.SELECT_TRACK_DOWNWARD ),
						item( SelectionActions.SELECT_TRACK_UPWARD ),
						separator(),
						tagSetMenu( tagSetMenuHandle )
				),
				ViewMenuBuilder.menu( "Settings",
						item( BigDataViewerActions.BRIGHTNESS_SETTINGS ),
						item( BigDataViewerActions.VISIBILITY_AND_GROUPING )
				)
		);
		appModel.getPlugins().addMenus( menu );

		final Model model = appModel.getModel();
		final ModelGraph modelGraph = model.getGraph();

		final ColoringModel coloringModel = registerColoring( coloring, menuHandle, viewer::requestRepaint );
		registerTagSetMenu( tagSetMenuHandle,  viewer::requestRepaint );

		coloringModel.listeners().add( viewer.getRenderer()::colorsChanged );
		model.getTagSetModel().vertexTagChangeListeners().add( v -> viewer.getRenderer().colorsChanged() );
		model.getTagSetModel().edgeTagChangeListeners().add( v -> viewer.getRenderer().colorsChanged() );

		highlightModel.listeners().add( viewer::requestRepaint );
		focusModel.listeners().add( viewer::requestRepaint );
		modelGraph.addGraphChangeListener( viewer::requestRepaint );
		modelGraph.addVertexPositionListener( v -> viewer.requestRepaint() );
		selectionModel.listeners().add( viewer::requestRepaint );

		frame.setVisible( true );

		viewer.timePointListeners().add( timePointIndex -> timepointModel.setTimepoint( timePointIndex ) );
		timepointModel.listeners().add( () -> viewer.setTimepoint( timepointModel.getTimepoint() ) );

		final DBvvHighlightHandler highlightHandler = new DBvvHighlightHandler( viewGraph.getGraph(), viewer.getRenderer(), highlightModel );
		viewer.getDisplay().addHandler( highlightHandler );
		viewer.transformListeners().add( highlightHandler );

		DBvvSelectionBehaviours.install( viewBehaviours, viewGraph.getGraph(), viewer.getRenderer(), selectionModel, focusModel, navigationHandler );

		// Give focus to display so that it can receive key-presses immediately.
		viewer.getDisplay().requestFocusInWindow();
	}
}
