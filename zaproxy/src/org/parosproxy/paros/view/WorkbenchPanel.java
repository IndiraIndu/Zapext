/*
 *
 * Paros and its related class files.
 * 
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 * 
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2011/05/31 Added option to dynamically change the display
// ZAP: 2011/07/25 Added automatically save/restore of divider locations
// ZAP: 2013/02/17 Issue 496: Allow to see the request and response at the same 
// time in the main window
// ZAP: 2013/02/26 Issue 540: Maximised work tabs hidden when response tab
// position changed
// ZAP: 2013/03/03 Issue 546: Remove all template Javadoc comments
// ZAP: 2013/05/02 Removed redundant final modifiers from private methods
// ZAP: 2013/12/13 Added support for 'Full Layout'.
// ZAP: 2014/01/28 Issue 207: Support keyboard shortcuts 
// ZAP: 2014/10/07 Issue 1357: Hide unused tabs
// ZAP: 2015/02/11 Ensure that a tab is always selected when the layout is switched
// ZAP: 2015/12/14 Disable request/response tab buttons location when in full layout

package org.parosproxy.paros.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import org.apache.log4j.Logger;
import org.parosproxy.paros.extension.AbstractPanel;
import org.zaproxy.zap.view.TabbedPanel2;

public class WorkbenchPanel extends JPanel {

	private static final long serialVersionUID = -4610792807151921550L;

	private static final String PREF_DIVIDER_LOCATION = "divider.location";
	private static final String DIVIDER_VERTICAL = "vertical";
	private static final String DIVIDER_HORIZONTAL = "horizontal";

	private JSplitPane splitVert = null;
	private JSplitPane splitHoriz = null;

  /* panels used when presenting views */
	private JPanel paneStatus = null;
	private JPanel paneSelect = null;
	private JPanel paneWork = null;

  /* panels for normal view */
	private TabbedPanel2 tabbedStatus = null;
	private TabbedPanel2 tabbedWork = null;
	private TabbedPanel2 tabbedSelect = null;
  
  /* panels used when going into 'Full Layout' to remember the old tab positions */
	private TabbedPanel2 tabbedOldStatus = null;
	private TabbedPanel2 tabbedOldWork = null;
	private TabbedPanel2 tabbedOldSelect = null;
	
	private int displayOption;
  private int previousDisplayOption = -1;

	private final Preferences preferences;
	private final String prefnzPrefix = this.getClass().getSimpleName()+".";

	private final Logger logger = Logger.getLogger(WorkbenchPanel.class);

	/**
	 * This is the default constructor
	 */
	public WorkbenchPanel(int displayOption) {
		super();
		this.preferences = Preferences.userNodeForPackage(getClass());
		this.displayOption = displayOption;
		this.previousDisplayOption = displayOption;
		initialize();
	}


    /*
     * Minimizes the maximized panels when changing layout to prevent panels not being
     * displayed correctly.
     */
    private void minimizeMaximizedPanels() {
        // minimize the maximized tab when changing layout: solves the problem of presenting
        // empty panel when going into 'Full Layout' view when certain tab is maximized
        if(tabbedSelect != null && tabbedSelect.isInAlternativeParent()) {
          tabbedSelect.alternateParent();
        }
        else if(tabbedStatus != null && tabbedStatus.isInAlternativeParent()) {
          tabbedStatus.alternateParent();
        }
        else if(tabbedWork != null && tabbedWork.isInAlternativeParent()) {
          tabbedWork.alternateParent();
        }
        // special use case for when 'Response' is maximized, which is called only after the 
        // ZAP has already been started. The restoreOriginalParentTabbedPanel returns the 
        // TabbedPanel object, which we're discaring because we don't need it.
        if(this.previousDisplayOption != -1) {
          View.getSingleton().getMessagePanelsPositionController().restoreOriginalParentTabbedPanel();
        }
    }


	/**
	 * This method initializes this
	 */
	private void initialize() {
    // set grid layout for the whole pane: tabbedWork, tabbedSelect and tabbedStatus
		GridBagConstraints consGridBagConstraints1 = new GridBagConstraints();

		this.setLayout(new GridBagLayout());
		consGridBagConstraints1.gridx = 0;
		consGridBagConstraints1.gridy = 0;
		consGridBagConstraints1.weightx = 1.0;
		consGridBagConstraints1.weighty = 1.0;
		consGridBagConstraints1.fill = GridBagConstraints.BOTH;

    View.setDisplayOption(this.displayOption);

    // minimize maximized panels when changing layout
    minimizeMaximizedPanels();

	switch (displayOption) {
      case View.DISPLAY_OPTION_LEFT_FULL:
        this.add(getSplitHoriz(), consGridBagConstraints1);
        break;
      case View.DISPLAY_OPTION_TOP_FULL:
        this.add(getPaneStatus(), consGridBagConstraints1);
        break;
      case View.DISPLAY_OPTION_BOTTOM_FULL:
      default:
        this.add(getSplitVert(), consGridBagConstraints1);
        break;
	}

    /*
     * Correct the tabs position based on the currently selected layout: if Full Layout 
     * was invoked: Request/Response/Script Console/Quickstart/Break tabs.
     */ 
	switch (displayOption) {
      case View.DISPLAY_OPTION_TOP_FULL:
        // save the arrangements of tabs when going into 'Full Layout'
        if(previousDisplayOption != View.DISPLAY_OPTION_TOP_FULL) {
          tabbedOldSelect = tabbedSelect;
          tabbedOldStatus = tabbedStatus;
          tabbedOldWork   = tabbedWork;
        }
        // Tabs in sequence: request, response, output, sites.
        getTabbedStatus().addTab(View.getSingleton().getRequestPanel().getName(), View.getSingleton().getRequestPanel().getIcon(), View.getSingleton().getRequestPanel());
        getTabbedStatus().addTab(View.getSingleton().getResponsePanel().getName(), View.getSingleton().getResponsePanel().getIcon(), View.getSingleton().getResponsePanel());
        getTabbedStatus().addTab(View.getSingleton().getSiteTreePanel().getName(), View.getSingleton().getSiteTreePanel().getIcon(), View.getSingleton().getSiteTreePanel());
     
        // go over all tabs that extensions added and move them to tabbedStatus
        for(Component c: getTabbedWork().getTabList()) {
            if(c instanceof AbstractPanel) {
                getTabbedStatus().addTab((AbstractPanel)c);
            }
        }
        for(Component c: getTabbedSelect().getTabList()) {
            if(c instanceof AbstractPanel) {
                getTabbedStatus().addTab((AbstractPanel)c);
            }
        }
        View.getSingleton().getMessagePanelsPositionController().setEnabled(false);
        break;
      case View.DISPLAY_OPTION_BOTTOM_FULL:
      case View.DISPLAY_OPTION_LEFT_FULL:
      default:
        // we shouldn't check against 'previousDisplayOption == View.DISPLAY_OPTION_TOP_FULL',
        // because the previousDisplayOption can be null when starting ZAP.
        if((previousDisplayOption != View.DISPLAY_OPTION_BOTTOM_FULL) || (previousDisplayOption != View.DISPLAY_OPTION_LEFT_FULL)) {
          // Tabs in sequence: request, response, output, sites.
          getTabbedWork().addTab(View.getSingleton().getRequestPanel().getName(), View.getSingleton().getRequestPanel().getIcon(), View.getSingleton().getRequestPanel());
          getTabbedWork().addTab(View.getSingleton().getResponsePanel().getName(), View.getSingleton().getResponsePanel().getIcon(), View.getSingleton().getResponsePanel());
          getTabbedSelect().addTab(View.getSingleton().getSiteTreePanel().getName(), View.getSingleton().getSiteTreePanel().getIcon(), View.getSingleton().getSiteTreePanel());
        }

        // parse the tabs correctly when previous display option was 'Full Layout'
        if(previousDisplayOption == View.DISPLAY_OPTION_TOP_FULL) {
          for(Component c: getTabbedOldWork().getTabList()) {
              if(c instanceof AbstractPanel) {
                  getTabbedWork().addTab((AbstractPanel)c);
              }
          }
          for(Component c: getTabbedOldSelect().getTabList()) {
              if(c instanceof AbstractPanel) {
                  getTabbedSelect().addTab((AbstractPanel)c);
              }
          }
        }
    	// Ensure that a tab is selected
    	if (getTabbedWork().getSelectedComponent() == null && getTabbedWork().getTabCount() > 0) {
    		getTabbedWork().setSelectedIndex(0);
    	}
    	if (getTabbedSelect().getSelectedComponent() == null && getTabbedSelect().getTabCount() > 0) {
    		getTabbedSelect().setSelectedIndex(0);
    	}
    }

    // Restore state of the MessagePanelsPositionController after changing the layout, so
    // the Request/Response do not appear as empty panels. This should only happen when
    // changing the layout when starting ZAP and when not switching to 'Full Layout'.
    if(this.previousDisplayOption != -1) {
        boolean nonTopFullOption = this.displayOption != View.DISPLAY_OPTION_TOP_FULL;
        if (nonTopFullOption) {
            View.getSingleton().getMessagePanelsPositionController().restoreState();
        }
        View.getSingleton().getMessagePanelsPositionController().setEnabled(nonTopFullOption);
    }

    // save previous display option
    this.previousDisplayOption = this.displayOption;
	}


  /**
   * This method is called whenever we change the layout in preferences or in toolbar.
   * @param displayOption
   */
	public void changeDisplayOption(int displayOption) {
		this.displayOption = displayOption;
		this.removeAll();
		splitVert = null;
		splitHoriz = null;
		initialize();
		this.validate();
		this.repaint();
	}


	/**
	 * This method initializes splitVert
	 * (TOP/BOTTOM (History))
	 * 
	 * @return JSplitPane
	 */
	private JSplitPane getSplitVert() {
		if (splitVert == null) {
			splitVert = new JSplitPane();

			splitVert.setDividerLocation(restoreDividerLocation(DIVIDER_VERTICAL, 300));
			splitVert.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new DividerResizedListener(DIVIDER_VERTICAL));

			splitVert.setDividerSize(3);
			splitVert.setOrientation(JSplitPane.VERTICAL_SPLIT);
			splitVert.setResizeWeight(0.5D);

			switch (displayOption) {
			case View.DISPLAY_OPTION_LEFT_FULL:
				splitVert.setTopComponent(getPaneWork());
				break;
			case View.DISPLAY_OPTION_BOTTOM_FULL:
			default:
				splitVert.setTopComponent(getSplitHoriz());
				break;
			}
			splitVert.setBottomComponent(getPaneStatus());
			splitVert.setContinuousLayout(false);
			splitVert.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}
		return splitVert;
	}

	/**
	 * This method initializes splitHoriz
	 * 
	 * Site Panel / Work
	 * 
	 * @return JSplitPane
	 */
	private JSplitPane getSplitHoriz() {
		if (splitHoriz == null) {
			splitHoriz = new JSplitPane();
			splitHoriz.setLeftComponent(getPaneSelect());
			switch (displayOption) {
			case View.DISPLAY_OPTION_LEFT_FULL:
				splitHoriz.setRightComponent(getSplitVert());
				break;
			case View.DISPLAY_OPTION_BOTTOM_FULL:
			default:
				splitHoriz.setRightComponent(getPaneWork());
				break;
			}

			splitHoriz.setDividerLocation(restoreDividerLocation(DIVIDER_HORIZONTAL, 300));
			splitHoriz.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new DividerResizedListener(DIVIDER_HORIZONTAL));

			splitHoriz.setDividerSize(3);
			splitHoriz.setResizeWeight(0.3D);
			splitHoriz.setContinuousLayout(false);
			splitHoriz.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}
		return splitHoriz;
	}


	/**
	 * This method initializes paneStatus
	 * 
	 * @return JPanel
	 */
	private JPanel getPaneStatus() {
		if (paneStatus == null) {
			paneStatus = new JPanel();
			paneStatus.setLayout(new BorderLayout(0,0));
			paneStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
			paneStatus.add(getTabbedStatus());
		}
		return paneStatus;
	}
	
	/**
	 * This method initializes paneSelect
	 * 
	 * @return JPanel
	 */
	private JPanel getPaneSelect() {
		if (paneSelect == null) {
			paneSelect = new JPanel();
			paneSelect.setLayout(new BorderLayout(0,0));
			paneSelect.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
			paneSelect.add(getTabbedSelect());
		}
		return paneSelect;
	}

	/**
	 * This method initializes paneWork, which is used for request/response/break/script console.
	 *
	 * @return JPanel
	 */
	private JPanel getPaneWork() {
		if (paneWork == null) {
			paneWork = new JPanel();
			paneWork.setLayout(new BorderLayout(0,0));
			paneWork.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
			paneWork.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			paneWork.add(getTabbedWork());
		}
		return paneWork;
	}

	public void splitPaneWorkWithTabbedPanel(TabbedPanel tabbedPanel, int orientation) {
		getPaneWork().removeAll();

		JSplitPane split = new JSplitPane(orientation);
		split.setDividerSize(3);
		split.setResizeWeight(0.5D);
		split.setContinuousLayout(false);
		split.setDoubleBuffered(true);
		split.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

		split.setRightComponent(tabbedPanel);
		split.setLeftComponent(getTabbedWork());

		getPaneWork().add(split);
		getPaneWork().validate();
	}
	
	public void removeSplitPaneWork() {
		getPaneWork().removeAll();
		getPaneWork().add(getTabbedWork());
		getPaneWork().validate();
	}

	/**
	 * This method initializes tabbedStatus
	 * 
	 * @return org.parosproxy.paros.view.ParosTabbedPane
	 */
	public TabbedPanel2 getTabbedStatus() {
		if (tabbedStatus == null) {
			tabbedStatus = new TabbedPanel2();
			tabbedStatus.setPreferredSize(new Dimension(800, 200));
			// ZAP: Move tabs to the top of the panel
			tabbedStatus.setTabPlacement(JTabbedPane.TOP);
			tabbedStatus.setName("tabbedStatus");
			tabbedStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}
		return tabbedStatus;
	}

	public TabbedPanel2 getTabbedOldStatus() {
		if (tabbedOldStatus == null) {
			tabbedOldStatus = new TabbedPanel2();
			tabbedOldStatus.setPreferredSize(new Dimension(800, 200));
			// ZAP: Move tabs to the top of the panel
			tabbedOldStatus.setTabPlacement(JTabbedPane.TOP);
			tabbedOldStatus.setName("tabbedOldStatus");
			tabbedOldStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}
		return tabbedOldStatus;
	}

	/**
	 * This method initializes tabbedWork
	 * 
	 * @return org.parosproxy.paros.view.ParosTabbedPane
	 */
	public TabbedPanel2 getTabbedWork() {
		if (tabbedWork == null) {
			tabbedWork = new TabbedPanel2();
			tabbedWork.setPreferredSize(new Dimension(600, 400));
			tabbedWork.setName("tabbedWork");
			tabbedWork.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}
		return tabbedWork;
	}

	public TabbedPanel2 getTabbedOldWork() {
		if (tabbedOldWork == null) {
			tabbedOldWork = new TabbedPanel2();
			tabbedOldWork.setPreferredSize(new Dimension(600, 400));
			tabbedOldWork.setName("tabbedOldWork");
			tabbedOldWork.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}
		return tabbedOldWork;
	}


  /**
   * Set the old tabbed panels called from ExtensionLoader.java and used with 'Full Layout'.
   * @param t
   */
  public void setTabbedOldWork(TabbedPanel2 t) {
    this.tabbedOldWork = t.clone(t);
  }
  public void setTabbedOldStatus(TabbedPanel2 t) {
    this.tabbedOldStatus = t.clone(t);
  }
  public void setTabbedOldSelect(TabbedPanel2 t) {
    this.tabbedOldSelect = t.clone(t);
  }


  /**
   * Toggle the the tab names when Tools - Options - Display - 'Show tab names' is used.
   */
  public void toggleTabNames(boolean showTabNames) {
    getTabbedStatus().setShowTabNames(showTabNames);
    getTabbedSelect().setShowTabNames(showTabNames);
    getTabbedWork().setShowTabNames(showTabNames);
  }

	/**
	 * This method initializes tabbedSelect
	 * 
	 * @return org.parosproxy.paros.view.ParosTabbedPane
	 */
	public TabbedPanel2 getTabbedSelect() {
		if (tabbedSelect == null) {
			tabbedSelect = new TabbedPanel2();
			tabbedSelect.setPreferredSize(new Dimension(200, 400));
			tabbedSelect.setName("tabbedSelect");
			tabbedSelect.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}

		return tabbedSelect;
	}

	public TabbedPanel2 getTabbedOldSelect() {
		if (tabbedOldSelect == null) {
			tabbedOldSelect = new TabbedPanel2();
			tabbedOldSelect.setPreferredSize(new Dimension(200, 400));
			tabbedOldSelect.setName("tabbedOldSelect");
			tabbedOldSelect.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}

		return tabbedOldSelect;
	}
	
	/**
	 * @param prefix
	 * @param location
	 */
	private void saveDividerLocation(String prefix, int location) {
		if (location > 0) {
			if (logger.isDebugEnabled()) logger.debug("Saving preference " + prefnzPrefix+prefix + "." + PREF_DIVIDER_LOCATION + "=" + location);
			this.preferences.put(prefnzPrefix+prefix + "." + PREF_DIVIDER_LOCATION, Integer.toString(location));
			// immediate flushing
			try {
				this.preferences.flush();
			} catch (final BackingStoreException e) {
				logger.error("Error while saving the preferences", e);
			}
		}
	}
	
	/**
	 * @param prefix
	 * @param fallback
	 * @return the size of the frame OR fallback value, if there wasn't any preference.
	 */
	private int restoreDividerLocation(String prefix, int fallback) {
		int result = fallback;
		final String sizestr = preferences.get(prefnzPrefix+prefix + "." + PREF_DIVIDER_LOCATION, null);
		if (sizestr != null) {
			int location = 0;
			try {
				location = Integer.parseInt(sizestr.trim());
			} catch (final Exception e) {
				// ignoring, cause is prevented by default values;
			}
			if (location > 0 ) {
				result = location;
				if (logger.isDebugEnabled()) logger.debug("Restoring preference " + prefnzPrefix+prefix + "." + PREF_DIVIDER_LOCATION + "=" + location);
			}
		}
		return result;
	}
	
	/*
	 * ========================================================================
	 */
	
	private final class DividerResizedListener implements PropertyChangeListener {

		private final String prefix;
		
		public DividerResizedListener(String prefix) {
			super();
			assert prefix != null;
			this.prefix = prefix;
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			JSplitPane component = (JSplitPane) evt.getSource();
			if (component != null) {
				if (logger.isDebugEnabled()) logger.debug(prefnzPrefix+prefix + "." + "location" + "=" + component.getDividerLocation());
				saveDividerLocation(prefix, component.getDividerLocation());
			}
		}
		
	}

}
