package net.sourceforge.texlipse.treeview.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import net.sourceforge.texlipse.TexlipsePlugin;
import net.sourceforge.texlipse.editor.TexEditor;
import net.sourceforge.texlipse.model.OutlineNode;
import net.sourceforge.texlipse.model.TexOutlineInput;
import net.sourceforge.texlipse.outline.TexContentProvider;
import net.sourceforge.texlipse.outline.TexLabelProvider;
import net.sourceforge.texlipse.outline.TexOutlineFilter;
import net.sourceforge.texlipse.outline.TexOutlineNodeComparer;
import net.sourceforge.texlipse.properties.TexlipseProperties;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.ViewPart;

/**
 * The view for the full outline.
 *
 */
public class TexOutlineTreeView extends ViewPart implements  ISelectionChangedListener, ISelectionProvider, IPartListener { 
	
	private TreeViewer treeViewer;
	private TexOutlineInput input;
	private TexOutlineFilter filter;
	private TexEditor editor;
	
	private HashMap outlineActions;
	private int expandLevel;
	
	private ListenerList selectionChangedListeners = new ListenerList();
	
	private static final String ACTION_UPDATE = "update";
	private static final String ACTION_COLLAPSE = "collapse";
	private static final String ACTION_EXPAND = "expand";
	private static final String ACTION_HIDE_SEC = "hideSec";
	private static final String ACTION_HIDE_SUBSEC = "hideSubSec";
	private static final String ACTION_HIDE_SUBSUBSEC = "hideSubSubSec";
	private static final String ACTION_HIDE_PARAGRAPH = "hidePara";
	private static final String ACTION_HIDE_FLOAT = "hideFloat";
	
	/**
	 * The constructor.
	 *
	 */
	public TexOutlineTreeView() {
		super();
		this.outlineActions = new HashMap();
		expandLevel = 1;
		
		TexlipsePlugin.getDefault().getPreferenceStore().addPropertyChangeListener(new  
				IPropertyChangeListener() {
			
			public void propertyChange(PropertyChangeEvent event) {
				
				String property = event.getProperty();
				if (TexlipseProperties.OUTLINE_PART.equals(property) || 
						TexlipseProperties.OUTLINE_CHAPTER.equals(property) ||
						TexlipseProperties.OUTLINE_SECTION.equals(property) ||
						TexlipseProperties.OUTLINE_SUBSECTION.equals(property) ||
						TexlipseProperties.OUTLINE_SUBSUBSECTION.equals(property) ||
						TexlipseProperties.OUTLINE_PARAGRAPH.equals(property) ||
						TexlipseProperties.OUTLINE_ENVS.equals(property)) {
					getOutlinePreferences();
					resetToolbarButtons();
					if (treeViewer != null) {
						Control control= treeViewer.getControl();
						if (control != null && !control.isDisposed()) {
							treeViewer.refresh();
						}
					}	
				}
			}	
		});    
	}
	
	/**
	 * Creates the viewer. Registeres the full outline at the document model.
	 */
	public void createPartControl(Composite parent) {
		createActions();
		treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL
				| SWT.V_SCROLL);
		
		treeViewer.addSelectionChangedListener(this);
		treeViewer.setContentProvider(new TexContentProvider());
		treeViewer.setLabelProvider(new TexLabelProvider());
		treeViewer.setComparer(new TexOutlineNodeComparer());
		this.filter = new TexOutlineFilter();
		
		// get and apply the preferences
		this.getOutlinePreferences();
		treeViewer.addFilter(filter);
		
		// create the menu bar and the context menu
		createToolbar();
		resetToolbarButtons();
		
		// finally set the input
		if (this.input != null) {
			treeViewer.setInput(this.input.getRootNodes());
			
			// set update button status and also the context actions
			((IAction)outlineActions.get(ACTION_UPDATE)).setEnabled(false);
		}
		
		// add a part listener if the editor isn't available when the view is created.
		getViewSite().getPage().addPartListener(this);
		// register it directly if the view is already created.
		IEditorPart part = getViewSite().getPage().getActiveEditor();
		if(part!=null && part instanceof TexEditor) {
			editor = (TexEditor) part;
			editor.registerFullOutline(this);	
		}
	}
	
	public void setFocus() {
	}
	
	/**
	 * Updates the outline with the new input.
	 * @param input the new input.
	 */
	public void update(TexOutlineInput input) {
		this.input = input; 
		if (treeViewer != null) {
			Control control= treeViewer.getControl();
			if (control != null && !control.isDisposed()) {
				control.setRedraw(false);
				// save viewer state
				//ISelection selection = viewer.getSelection();
				treeViewer.getTree().deselectAll();
				
				Object[] expandedElements = treeViewer.getExpandedElements();
				
				
				// set new input
				treeViewer.setInput(this.input.getRootNodes());
				
				// restore viewer state
				treeViewer.setExpandedElements(expandedElements);
				
				treeViewer.refresh();
				
				control.setRedraw(true);
				
				// disable the refresh button
				((IAction)outlineActions.get(ACTION_UPDATE)).setEnabled(false);
			}
		}
	}
	
	/**
	 * Reads the preferences for the outline.
	 *
	 */
	private void getOutlinePreferences()  {
		filter.reset();
		
		// add node types to be included
		boolean preamble = TexlipsePlugin.getDefault().getPreferenceStore().getBoolean(TexlipseProperties.OUTLINE_PREAMBLE);
		boolean part = TexlipsePlugin.getDefault().getPreferenceStore().getBoolean(TexlipseProperties.OUTLINE_PART);
		boolean chapter = TexlipsePlugin.getDefault().getPreferenceStore().getBoolean(TexlipseProperties.OUTLINE_CHAPTER);
		boolean section = TexlipsePlugin.getDefault().getPreferenceStore().getBoolean(TexlipseProperties.OUTLINE_SECTION);
		boolean subsection = TexlipsePlugin.getDefault().getPreferenceStore().getBoolean(TexlipseProperties.OUTLINE_SUBSECTION);
		boolean subsubsection = TexlipsePlugin.getDefault().getPreferenceStore().getBoolean(TexlipseProperties.OUTLINE_SUBSUBSECTION);
		boolean paragraph = TexlipsePlugin.getDefault().getPreferenceStore().getBoolean(TexlipseProperties.OUTLINE_PARAGRAPH);
		
		if (preamble) {
			filter.toggleType(OutlineNode.TYPE_PREAMBLE, true);
		}
		if (part) {
			filter.toggleType(OutlineNode.TYPE_PART, true);
		}
		if (chapter) {
			filter.toggleType(OutlineNode.TYPE_CHAPTER, true);
		}
		if (section) {
			filter.toggleType(OutlineNode.TYPE_SECTION, true);
		}
		if (subsection) {
			filter.toggleType(OutlineNode.TYPE_SUBSECTION, true);
		}
		if (subsubsection) {
			filter.toggleType(OutlineNode.TYPE_SUBSUBSECTION, true);
		}
		if (paragraph) {
			filter.toggleType(OutlineNode.TYPE_PARAGRAPH, true);
		}
		
		// add floats to be included (and env type)
		filter.toggleType(OutlineNode.TYPE_ENVIRONMENT, true);
		String[] environments = TexlipsePlugin.getPreferenceArray(TexlipseProperties.OUTLINE_ENVS);
		for (int i = 0; i < environments.length; i++) {
			filter.toggleEnvironment(environments[i], true);
		}    
	}
	
	/**
	 * Resets the toolbar buttons.
	 *
	 */
	private void resetToolbarButtons() {
		((Action)outlineActions.get(ACTION_HIDE_SEC)).setChecked(!filter.isTypeVisible(OutlineNode.TYPE_SECTION));
		((Action)outlineActions.get(ACTION_HIDE_SUBSEC)).setChecked(!filter.isTypeVisible(OutlineNode.TYPE_SUBSECTION));
		((Action)outlineActions.get(ACTION_HIDE_SUBSUBSEC)).setChecked(!filter.isTypeVisible(OutlineNode.TYPE_SUBSUBSECTION));
		((Action)outlineActions.get(ACTION_HIDE_PARAGRAPH)).setChecked(!filter.isTypeVisible(OutlineNode.TYPE_PARAGRAPH));
		((Action)outlineActions.get(ACTION_HIDE_FLOAT)).setChecked(!filter.isTypeVisible(OutlineNode.TYPE_ENVIRONMENT));
	}
	
	/**
	 * Focuses the editor to the text of the selected item. Opens a new editor if
	 * the node is from a different file.
	 * 
	 * @param event the selection event
	 */
	public void selectionChanged(SelectionChangedEvent event) {
		fireSelectionChanged(event.getSelection());
		ISelection selection = event.getSelection();
		if (selection.isEmpty()) {
			editor.resetHighlightRange();
		}
		else {
			OutlineNode node = (OutlineNode) ((IStructuredSelection) selection).getFirstElement();

			if(node.getIFile()!=null){
				FileEditorInput input = new FileEditorInput(node.getIFile());
				try {
					// open the editor and go to the correct position.
					// this position must be calculated here, because
					// the position of a node in an other file isn't available.
					editor = (TexEditor) TexlipsePlugin.getCurrentWorkbenchPage().openEditor(input, "net.sourceforge.texlipse.TexEditor");
					IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
					int beginOffset = doc.getLineOffset(node.getBeginLine()-1);
					int length = doc.getLineOffset(node.getEndLine() - 1) - beginOffset;
					editor.setHighlightRange(beginOffset, length, true);
				} catch (PartInitException e) {
					TexlipsePlugin.log("Can't open editor.", e);
				} catch (BadLocationException e) {
					editor.resetHighlightRange();
				}
			}
		}
	}      
	
	/**
	 * Fires a selection changed event.
	 *
	 * @param selection the new selection
	 */
	protected void fireSelectionChanged(ISelection selection) {
		// create an event
		final SelectionChangedEvent event = new SelectionChangedEvent(this,
				selection);
		
		// fire the event
		Object[] listeners = selectionChangedListeners.getListeners();
		for (int i = 0; i < listeners.length; ++i) {
			final ISelectionChangedListener l = (ISelectionChangedListener) listeners[i];
			Platform.run(new SafeRunnable() {
				public void run() {
					l.selectionChanged(event);
				}
			});
		}
	}
	
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		selectionChangedListeners.add(listener);
	}
	
	public ISelection getSelection() {
		if (treeViewer == null)
			return StructuredSelection.EMPTY;
		return treeViewer.getSelection();
	}
	
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		selectionChangedListeners.remove(listener);
	}
	
	public void setSelection(ISelection selection) {
		if (treeViewer != null)
			treeViewer.setSelection(selection);
	}
	
	/**
	 * Returns whether the current TexDocumentModel is dirty
	 * 
	 * @return if current model is dirty.
	 */
	public boolean isModelDirty() {
		return editor.isModelDirty();
	}
	
	/**
	 * Creates the actions assosiated with the outline. 
	 */
	private void createActions() {
		// toolbar actions
		TexFullOutlineActionUpdate update = new TexFullOutlineActionUpdate(this);
		this.outlineActions.put(ACTION_UPDATE, update);
		
		Action collapse = new Action("Collapse one level", IAction.AS_PUSH_BUTTON) {
			
			public void run() {
				if (expandLevel > 1) {
					expandLevel--;
					treeViewer.collapseAll();
					treeViewer.expandToLevel(expandLevel);
				}
			}
		};
		collapse.setToolTipText("Collapse one level");
		collapse.setImageDescriptor(TexlipsePlugin.getImageDescriptor("collapse"));
		this.outlineActions.put(ACTION_COLLAPSE, collapse);
		
		Action expand = new Action("Expand one level", IAction.AS_PUSH_BUTTON) {
			public void run() {
				if (expandLevel < input.getTreeDepth()) {
					expandLevel++;
				}
				treeViewer.collapseAll();
				treeViewer.expandToLevel(expandLevel);
			}
		};
		expand.setToolTipText("Expand one level");
		expand.setImageDescriptor(TexlipsePlugin.getImageDescriptor("expand"));
		this.outlineActions.put(ACTION_EXPAND, expand);
		
		Action hideSections = new Action("Hide sections", IAction.AS_CHECK_BOX) {
			public void run() {
				boolean oldState = filter.isTypeVisible(OutlineNode.TYPE_SECTION);
				filter.toggleType(OutlineNode.TYPE_SECTION, !oldState);
				if (oldState == false) {
					revealNodes(OutlineNode.TYPE_SECTION);
				}
				treeViewer.refresh();
			}
		};
		hideSections.setToolTipText("Hide sections");
		hideSections.setImageDescriptor(TexlipsePlugin.getImageDescriptor("hide_sec"));
		this.outlineActions.put(ACTION_HIDE_SEC, hideSections);
		
		Action hideSubSections = new Action("Hide subsections", IAction.AS_CHECK_BOX) {
			public void run() {
				boolean oldState = filter.isTypeVisible(OutlineNode.TYPE_SUBSECTION);
				filter.toggleType(OutlineNode.TYPE_SUBSECTION, !oldState);
				if (oldState == false) {
					revealNodes(OutlineNode.TYPE_SUBSECTION);
				}
				treeViewer.refresh();
			}
		};
		hideSubSections.setToolTipText("Hide subsections");
		hideSubSections.setImageDescriptor(TexlipsePlugin.getImageDescriptor("hide_sub"));
		this.outlineActions.put(ACTION_HIDE_SUBSEC, hideSubSections);
		
		Action hideSubSubSections = new Action("Hide subsubsections", IAction.AS_CHECK_BOX) {
			public void run() {
				boolean oldState = filter.isTypeVisible(OutlineNode.TYPE_SUBSUBSECTION);
				filter.toggleType(OutlineNode.TYPE_SUBSUBSECTION, !oldState);
				if (oldState == false) {
					revealNodes(OutlineNode.TYPE_SUBSUBSECTION);
				}
				treeViewer.refresh();
			}
		};
		hideSubSubSections.setToolTipText("Hide subsubsections");
		hideSubSubSections.setImageDescriptor(TexlipsePlugin.getImageDescriptor("hide_subsub"));
		this.outlineActions.put(ACTION_HIDE_SUBSUBSEC, hideSubSubSections);
		
		Action hideParagraphs = new Action("Hide paragraphs", IAction.AS_CHECK_BOX) {
			public void run() {
				boolean oldState = filter.isTypeVisible(OutlineNode.TYPE_PARAGRAPH);
				filter.toggleType(OutlineNode.TYPE_PARAGRAPH, !oldState);
				if (oldState == false) {
					revealNodes(OutlineNode.TYPE_PARAGRAPH);
				}
				treeViewer.refresh();
			}
		};
		hideParagraphs.setToolTipText("Hide paragraphs");
		hideParagraphs.setImageDescriptor(TexlipsePlugin.getImageDescriptor("hide_para"));
		this.outlineActions.put(ACTION_HIDE_PARAGRAPH, hideParagraphs);
		
		Action hideFloats = new Action("Hide floating environments", IAction.AS_CHECK_BOX) {
			public void run() {
				boolean oldState = filter.isTypeVisible(OutlineNode.TYPE_ENVIRONMENT);
				filter.toggleType(OutlineNode.TYPE_ENVIRONMENT, !filter.isTypeVisible(OutlineNode.TYPE_ENVIRONMENT));
				if (oldState == false) {
					revealNodes(OutlineNode.TYPE_ENVIRONMENT);
				}
				treeViewer.refresh();
			}
		};
		hideFloats.setToolTipText("Hide floating environments");
		hideFloats.setImageDescriptor(TexlipsePlugin.getImageDescriptor("hide_env"));
		this.outlineActions.put(ACTION_HIDE_FLOAT, hideFloats);
	}
	
	/**
	 * Reveals all the nodes of certain type in the outline tree.
	 * 
	 * @param nodeType the type of nodes to be revealed
	 */
	private void revealNodes(int nodeType) {
		ArrayList nodeList = input.getTypeList(nodeType);
		if (nodeList != null) {
			for (Iterator iter = nodeList.iterator(); iter.hasNext();) {
				treeViewer.reveal((OutlineNode)iter.next());
			}
		}
	}
	
	/**
	 * Create the toolbar.
	 *
	 */
	private void createToolbar() {
		
		// add actions to the toolbar
		IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();
		toolbarManager.add((IAction)outlineActions.get(ACTION_UPDATE));
		toolbarManager.add((IAction)outlineActions.get(ACTION_COLLAPSE));
		toolbarManager.add((IAction)outlineActions.get(ACTION_EXPAND));
		toolbarManager.add((IAction)outlineActions.get(ACTION_HIDE_SEC));
		toolbarManager.add((IAction)outlineActions.get(ACTION_HIDE_SUBSEC));
		toolbarManager.add((IAction)outlineActions.get(ACTION_HIDE_SUBSUBSEC));
		toolbarManager.add((IAction)outlineActions.get(ACTION_HIDE_PARAGRAPH));
		toolbarManager.add((IAction)outlineActions.get(ACTION_HIDE_FLOAT));
	}	
	
	/**
	 * Called by the TexDocumentModel when it gets dirty. Enables
	 * the update button.
	 */
	public void modelGotDirty() {
		((IAction)outlineActions.get(ACTION_UPDATE)).setEnabled(true);
	}
	
	/**
	 * 
	 * @return the editor
	 */
	public TexEditor getEditor() {
		return editor;
	}
	
	/**
	 * 
	 * @param editor the editor.
	 */
	public void setEditor(TexEditor editor) {
		this.editor = editor;
	}
	
	/**
	 * registers the full outline, when the editor is activated.
	 */
	public void partActivated(IWorkbenchPart part) {
		if (part instanceof TexEditor) {
			editor = (TexEditor) part;
			editor.registerFullOutline(this);
		}
	}
	
	/**
	 * Not used.
	 */
	public void partBroughtToTop(IWorkbenchPart part) {
	}
	
	/**
	 * Not used.
	 */
	public void partClosed(IWorkbenchPart part) {
	}
	
	/**
	 * Not used.
	 */
	public void partDeactivated(IWorkbenchPart part) {
	}
	
	/**
	 * Not used.
	 */
	public void partOpened(IWorkbenchPart part) {			
	}

	/**
	 * unregisteres the full outline.
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		super.dispose();
		editor.unregisterFullOutline(this);
	}
}