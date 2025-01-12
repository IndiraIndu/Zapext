/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Copyright 2013 ZAP development team
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 */
package org.zaproxy.zap.extension.customFire;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.Highlight;
import javax.swing.text.Highlighter.HighlightPainter;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.parosproxy.paros.CommandLine;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.core.scanner.VariantUserDefined;
import org.parosproxy.paros.db.DatabaseException;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.OptionsParam;
import org.parosproxy.paros.model.Session;
import org.parosproxy.paros.model.SiteNode;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.view.AbstractParamContainerPanel;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.control.ControlOverrides;
import org.zaproxy.zap.extension.customFire.PolicyAllCategoryPanel.ScanPolicyChangedEventListener;
import org.zaproxy.zap.extension.users.ExtensionUserManagement;
import org.zaproxy.zap.model.Context;
import org.zaproxy.zap.model.StructuralNode;
import org.zaproxy.zap.model.StructuralSiteNode;
import org.zaproxy.zap.model.Target;
import org.zaproxy.zap.users.User;
import org.zaproxy.zap.utils.ZapTextArea;
import org.zaproxy.zap.utils.ZapXmlConfiguration;
import org.zaproxy.zap.view.LayoutHelper;
import org.zaproxy.zap.view.StandardFieldsDialog; 

/**
 * 
 * @author <a href="mailto:indu79455@gmail.com">Indira</a>
 *
 * Nov 29, 2016  org.zaproxy.zap.extension.customFire
 */
public class CustomFireDialog extends StandardFieldsDialog implements Serializable {

	protected static final String[] STD_TAB_LABELS = {
			"customFire.custom.tab.scope",
			"customFire.custom.tab.input",
			"customFire.custom.tab.custom",
			"customFire.custom.tab.tech",
			"customFire.custom.tab.policy" 
			//"customFire.custom.tab.script"   //not used     
	};


	private static final String FIELD_START = "customFire.custom.label.start";
	private static final String FIELD_POLICY = "customFire.custom.label.policy";
	private static final String FIELD_CONTEXT = "customFire.custom.label.context";
	private static final String FIELD_USER = "customFire.custom.label.user";
	private static final String FIELD_RECURSE = "customFire.custom.label.recurse";

	private static final String FIELD_DISABLE_VARIANTS_MSG = "variant.options.disable";

	private static final Logger logger = Logger.getLogger(CustomFireDialog.class);
	private static final long serialVersionUID = 1L;

	private JButton[] extraButtons = null;
	private ExtensionCustomFire extension = null;

	private final ExtensionUserManagement extUserMgmt = 
			(ExtensionUserManagement) Control.getSingleton().getExtensionLoader()
			.getExtension(ExtensionUserManagement.NAME);

	private int headerLength = -1;
	// The index of the start of the URL path eg after https://www.example.com:1234/ - no point attacking this
	private int urlPathStart = -1;
	private Target target = null;

	private ScannerParam scannerParam = null;
	private OptionsParam optionsParam = null;

	private JPanel customPanel = null;
	private JPanel techPanel = null;
	private JPanel sPanel = null;  
	private transient ZapTextArea requestField = null;
	private JButton addCustomButton = null;
	private JButton removeCustomButton = null;
	private JList<Highlight> injectionPointList = null;
	private final DefaultListModel<Highlight> injectionPointModel = new DefaultListModel<>();
	private final JLabel customPanelStatus = new JLabel();
	private JCheckBox disableNonCustomVectors = null;
	private TechnologyTreePanel techTree;  
	private ScriptTreePanel sTree; 
	private String scanPolicyName;
	private CustomScanPolicy scanPolicy = null;
	private OptionsVariantPanel variantPanel = null;
	private List<CustomFirePanel> customPanels = null;
	private ScanPolicyPanel policyPanel;

	PolicyManager p = new PolicyManager(extension);
	ArrayList list = new ArrayList<>();

//	private CommandLine args;
//	private ControlOverrides controlOverrides;

	private boolean reset;

	/**
	 * 
	 * @param ext
	 * @param tabLabels
	 * @param customFirePanels
	 * @param owner
	 * @param dim
	 */
	public CustomFireDialog(ExtensionCustomFire ext, String[] tabLabels,List<CustomFirePanel> customFirePanels, Frame owner, Dimension dim ) {
		super(owner, "customFire.dialog.title", dim, tabLabels);

		this.extension = ext;
		this.customPanels = customFirePanels;
		this.policyPanel = new ScanPolicyPanel(
				this,
				extension,
				Constant.messages.getString("customFire.custom.tab.policy"),
				new CustomScanPolicy());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				scanPolicy = null;
			}
		});

		// The first time init to the default options set, after that keep own copies
		reset(false);
	}

	/**
	 * 
	 * @param target void `
	 */
	public void init(Target target) {
		if (target != null) {
			// If one isn't specified then leave the previously selected one
			this.target = target;
		}

		logger.debug("init " + this.target);

		this.removeAllFields();

		this.injectionPointModel.clear();
		this.headerLength = -1;
		this.urlPathStart = -1;

		if (scanPolicyName != null && PolicyManager.policyExists(scanPolicyName)) {
			try {
				scanPolicy = p.getPolicy(scanPolicyName); 
			} catch (ConfigurationException e) {
				logger.warn("Failed to load scan policy (" + scanPolicyName + "):", e);
			}
		}

		if (scanPolicy == null) {

			scanPolicy = p.getDefaultScanPolicy(); 

			scanPolicyName = scanPolicy.getName();

		}
		
		//Open session file : SiteMap in scope
		Control.getSingleton().getMenuFileControl().openSession(); 
		
		this.addTargetSelectField(0, FIELD_START, this.target, false, false);
		this.addComboField(0, FIELD_POLICY, p.getAllPolicyNames(), scanPolicy.getName()); 
		this.addComboField(0, FIELD_CONTEXT, new String[]{}, "");
		this.addComboField(0, FIELD_USER, new String[]{}, "");
		this.addCheckBoxField(0, FIELD_RECURSE, true);
		// This option is always read from the 'global' options

		this.addFieldListener(FIELD_POLICY, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				policySelected();
			}
		});

		this.addPadding(0);

		// Default to Recurse, so always set the warning
		customPanelStatus.setText(Constant.messages.getString("customFire.custom.status.recurse"));

		this.addFieldListener(FIELD_CONTEXT, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setUsers();
				setTech();
			}
		});

		this.addFieldListener(FIELD_RECURSE, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setFieldStates(); 
			}
		});

		//Input vectors panel
		this.getVariantPanel().initParam(getScannerParam());
		this.setCustomTabPanel(1, getVariantPanel());	

		// Custom vectors panel
		this.setCustomTabPanel(2, getSerCustomPanel());      

		// Script vectors panel--No longer used
		//this.setCustomTabPanel(5, getScriptPanel());   

		// Technology panel
		this.setCustomTabPanel(3, getTechPanel());		

		// Policy panel
		policyPanel.resetAndSetPolicy(scanPolicy.getName());

		this.setCustomTabPanel(4, policyPanel);


		// add custom panels
		int cIndex = 6;
		if (this.customPanels != null) {
			for (CustomFirePanel customPanel : this.customPanels) {
				this.setCustomTabPanel(cIndex, customPanel.getPanel(true));
				cIndex++;
			}
		}

		if (target != null) {
			// Set up the fields if a node has been specified, otherwise leave as previously set
			// If target is not null then set up fields only if custom vectors file is not selected/loaded during reset.
			if(!reset){
				this.populateRequestField(this.target.getStartNode());
			}
			this.targetSelected(FIELD_START, this.target); 
			this.setUsers(); 
			this.setTech();
		}
		this.pack();
	}

	@Override
	public String getHelpIndex() {
		return "ui.dialogs.advascan";
	}

	private void policySelected() {
		String policyName = getStringValue(FIELD_POLICY);
		try {
			scanPolicy = p.getPolicy(policyName); 
			policyPanel.setScanPolicy(scanPolicy);
			scanPolicyName = policyName;
		} catch (ConfigurationException e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * 
	 * @param node void `
	 */
	private void populateRequestField(SiteNode node) {
		try {
			if (node == null || node.getHistoryReference() == null || node.getHistoryReference().getHttpMessage() == null) {
				this.getRequestField().setText("");

			} else {
				// Populate the custom vectors http pane
				HttpMessage msg = node.getHistoryReference().getHttpMessage();
				String header = msg.getRequestHeader().toString();
				StringBuilder sb = new StringBuilder();
				sb.append(header);
				this.headerLength = header.length();
				this.urlPathStart = header.indexOf("/", header.indexOf("://") + 2) + 1;	// Ignore <METHOD> http(s)://host:port/
				sb.append(msg.getRequestBody().toString());
				this.getRequestField().setText(sb.toString());

				// Only set the recurse option if the node has children, and disable it otherwise
				JCheckBox recurseChk = (JCheckBox) this.getField(FIELD_RECURSE);
				recurseChk.setEnabled(node.getChildCount() > 0); 
				recurseChk.setSelected(node.getChildCount() > 0); 
			}
			this.setFieldStates();
		} catch (HttpMalformedHeaderException | DatabaseException e) {
			this.getRequestField().setText("");
		}

	}

	@Override
	public void targetSelected(String field, Target node) {
		List<String> ctxNames = new ArrayList<>();
		if (node != null) {
			// The user has selected a new node
			this.target = node;
			if (node.getStartNode() != null) {
				if(!reset){//If target is not null then set up fields only if no file is loaded during reset.
					populateRequestField(node.getStartNode());
				}
				Session session = Model.getSingleton().getSession();
				List<Context> contexts = session.getContextsForNode(node.getStartNode());
				for (Context context : contexts) {
					ctxNames.add(context.getName());
				}

			} else if (node.getContext() != null) {
				ctxNames.add(node.getContext().getName());
			}
			this.setTech();
		}
		this.setComboFields(FIELD_CONTEXT, ctxNames, "");
		this.getField(FIELD_CONTEXT).setEnabled(ctxNames.size() > 0);
	}

	/**
	 * 
	 * @return Context `
	 */
	private Context getSelectedContext() {
		String ctxName = this.getStringValue(FIELD_CONTEXT);
		if (this.extUserMgmt != null && !this.isEmptyField(FIELD_CONTEXT)) {
			Session session = Model.getSingleton().getSession();
			return session.getContext(ctxName);
		}
		return null;
	}

	private User getSelectedUser() {
		Context context = this.getSelectedContext();
		if (context != null) {
			String userName = this.getStringValue(FIELD_USER);
			List<User> users = this.extUserMgmt.getContextUserAuthManager(context.getIndex()).getUsers();
			for (User user : users) {
				if (userName.equals(user.getName())) {
					return user;
				}
			}
		}
		return null;
	}

	private void setUsers() {
		Context context = this.getSelectedContext();
		List<String> userNames = new ArrayList<>();
		if (context != null) {
			List<User> users = this.extUserMgmt.getContextUserAuthManager(context.getIndex()).getUsers();
			userNames.add("");	// The default should always be 'not specified'
			for (User user : users) {
				userNames.add(user.getName());
			}
		}
		this.setComboFields(FIELD_USER, userNames, "");
		this.getField(FIELD_USER).setEnabled(userNames.size() > 1);	// There's always 1..
	}

	private void setTech() {
		Context context = this.getSelectedContext();
		if (context != null) {		
			techTree.reset();//Selecting all Technologies. should be based on selection.
			//techTree.setTechSet(context.getTechSet());//TODO techTree is set based on context! Changed to complete reset..
		} else {
			techTree.reset();
		}
	}

	/**
	 * 
	 * @return ZapTextArea `
	 */
	private ZapTextArea getRequestField() {
		if (requestField == null) {
			requestField = new ZapTextArea();
			requestField.setEditable(false);
			requestField.setLineWrap(true);
			requestField.getCaret().setVisible(true);
		}
		return requestField;
	}


	/**
	 * 
	 * @return OptionsVariantPanel `
	 */
	private OptionsVariantPanel getVariantPanel() { 
		if (variantPanel == null) {
			variantPanel = new OptionsVariantPanel();            
		}
		return variantPanel;

	}

	
	/**
	 * 
	 * @return JPanel - customPanel with saved injectionList 
	 */
	private JPanel getSerCustomPanel(){
		customPanel = getCustomPanel();
		ArrayList highlightList = new ArrayList<>();
		ArrayList textAreaList = new ArrayList<>();
		JFileChooser chooser = new JFileChooser(Constant.getZapHome());
		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) {
					return true;
				} else if (file.isFile() && file.getName().endsWith(".ser")) {
					return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				return Constant.messages.getString("customFire.custom.file.format.csp.ser");
			}
		});
		File file = null;
		chooser.setDialogTitle("Open Custom Vectors");
		int rc = chooser.showOpenDialog(View.getSingleton().getMainFrame());
		if (rc == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
			if (file == null || !file.getName().equalsIgnoreCase("Custom Vectors.ser") ) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.tech.ser.mismatch.error"));
				return customPanel;
			}
			try {
				FileInputStream fis = new FileInputStream(file.getPath());
				ObjectInputStream ois = new ObjectInputStream(fis);
				textAreaList = (ArrayList<String>)ois.readObject();
				ois.close();
				fis.close();
				highlightList = (ArrayList) textAreaList.get(0);
				String httpReq = (String) textAreaList.get(1);

				customPanel = getSavedCustomPanel(highlightList, httpReq); 
				reset = true;

			} catch ( IOException | ClassNotFoundException e1) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.ser.load.error"));
				return customPanel;
			}
		}
		return customPanel;
	}

	/**
	 * 
	 * @param highlightList
	 * @param httpReq
	 * @return JPanel 
	 * Returns CustomPanel with saved injectionList 
	 */
	private JPanel getSavedCustomPanel(ArrayList highlightList, String httpReq){ 

		customPanel = new JPanel(new GridBagLayout());

		JScrollPane scrollPane = new JScrollPane(); //gT
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setViewportView(getRequestField());

		JPanel buttonPanel = new JPanel(new GridBagLayout());

		getRequestField().addCaretListener(new CaretListener() {
			@Override
			public void caretUpdate(CaretEvent event) {
				setFieldStates();

			}
		});

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(0, 0, 1, 0.5));	// Spacer
		buttonPanel.add(getAddCustomButton(), LayoutHelper.getGBC(1, 0, 1, 1, 0.0D, 0.0D,
				GridBagConstraints.BOTH, GridBagConstraints.NORTHWEST, new Insets(5, 5, 5, 5)));

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(2, 0, 1, 0.5));	// Spacer

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(0, 1, 1, 0.5));	// Spacer
		buttonPanel.add(getRemoveCustomButton(), LayoutHelper.getGBC(1, 1, 1, 1, 0.0D, 0.0D,
				GridBagConstraints.BOTH, GridBagConstraints.NORTHWEST, new Insets(5, 5, 5, 5)));

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(2, 1, 1, 0.5));	// Spacer

		JScrollPane scrollPane2 = new JScrollPane(getSavedInjectionPointList(highlightList,httpReq)); 

		scrollPane2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

		buttonPanel.add(new JLabel(Constant.messages.getString("customFire.custom.label.vectors")),
				LayoutHelper.getGBC(0, 2, 3, 0.0D, 0.0D));

		buttonPanel.add(scrollPane2, LayoutHelper.getGBC(0, 3, 3, 1.0D, 1.0D)); //gT

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, buttonPanel);
		splitPane.setDividerLocation(550);

		customPanel.add(splitPane, LayoutHelper.getGBC(0, 0, 1, 1, 1.0D, 1.0D));
		customPanel.add(customPanelStatus, LayoutHelper.getGBC(0, 1, 1, 1, 1.0D, 0.0D));
		customPanel.add(getDisableNonCustomVectors(), LayoutHelper.getGBC(0, 2, 1, 1, 1.0D, 0.0D));

		return customPanel;
	}

	/**
	 * 
	 * @param highlightList
	 * @param httpReq
	 * @return JList<Highlight> 
	 * Request field-HTTPMsg is highlighted based on saved list of injection points and add them to new JList<Highlight>
	 */
	private JList<Highlight> getSavedInjectionPointList(ArrayList highlightList, String httpReq) {

		getRequestField().setText(httpReq);

		//If list instance is null then add elements from highlightList to list or remove everything from list and add from saved highlightList
		list.clear();

		//Highlight the coords
		Highlighter highLighter = getRequestField().getHighlighter();
		highLighter.removeAllHighlights();
		HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(Color.RED);

		for( int i=0; i<highlightList.size(); i++ ){
			String s = highlightList.get(i).toString();

			try {
				//Find co ords from string
				String[] s1 = s.split(",");
				int start = Integer.parseInt(s1[0].replaceAll("[\\D]", ""));
				int end = ((Number)NumberFormat.getInstance().parse(s1[1])).intValue();

				headerLength = httpReq.trim().length();
				urlPathStart = httpReq.indexOf("/", httpReq.indexOf("://") + 2) + 1;
				if (!(start < urlPathStart || (start < headerLength && end > headerLength))) {
					highLighter.addHighlight(start, end, painter);
					list.add(s);
				}

			} catch (BadLocationException | ParseException e) {
				e.printStackTrace();
			}

		}
		Highlight[] hls = highLighter.getHighlights();
		if (hls != null && hls.length > 0) {
			for (Highlight hl : hls) {
				injectionPointModel.addElement(hl);
			}
		}
		injectionPointList = new JList<>(injectionPointModel);
		ListCellRendererEx listCellrender = new ListCellRendererEx(getRequestField());
		injectionPointList.setCellRenderer(listCellrender);

		return injectionPointList;
	}

	/**
	 * 
	 * @return JPanel `
	 */
	private JPanel getCustomPanel() { 
		
		customPanel = new JPanel(new GridBagLayout());

		JScrollPane scrollPane = new JScrollPane(); 
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setViewportView(getRequestField());

		JPanel buttonPanel = new JPanel(new GridBagLayout());

		getRequestField().addCaretListener(new CaretListener() {
			@Override
			public void caretUpdate(CaretEvent event) {
				setFieldStates();

			}
		});

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(0, 0, 1, 0.5));	// Spacer
		buttonPanel.add(getAddCustomButton(), LayoutHelper.getGBC(1, 0, 1, 1, 0.0D, 0.0D,
				GridBagConstraints.BOTH, GridBagConstraints.NORTHWEST, new Insets(5, 5, 5, 5)));

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(2, 0, 1, 0.5));	// Spacer

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(0, 1, 1, 0.5));	// Spacer
		buttonPanel.add(getRemoveCustomButton(), LayoutHelper.getGBC(1, 1, 1, 1, 0.0D, 0.0D,
				GridBagConstraints.BOTH, GridBagConstraints.NORTHWEST, new Insets(5, 5, 5, 5)));

		buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(2, 1, 1, 0.5));	// Spacer

		JScrollPane scrollPane2 = new JScrollPane(getInjectionPointList());
		scrollPane2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

		buttonPanel.add(new JLabel(Constant.messages.getString("customFire.custom.label.vectors")),
				LayoutHelper.getGBC(0, 2, 3, 0.0D, 0.0D));

		buttonPanel.add(scrollPane2, LayoutHelper.getGBC(0, 3, 3, 1.0D, 1.0D)); //gT

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, buttonPanel);
		splitPane.setDividerLocation(550);

		customPanel.add(splitPane, LayoutHelper.getGBC(0, 0, 1, 1, 1.0D, 1.0D));
		customPanel.add(customPanelStatus, LayoutHelper.getGBC(0, 1, 1, 1, 1.0D, 0.0D));
		customPanel.add(getDisableNonCustomVectors(), LayoutHelper.getGBC(0, 2, 1, 1, 1.0D, 0.0D));
		//}

		return customPanel;
	}

	private JButton getAddCustomButton() {
		if (addCustomButton == null) {
			addCustomButton = new JButton(Constant.messages.getString("customFire.custom.button.pt.add"));
			addCustomButton.setEnabled(false);

			addCustomButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					// Add the selected injection point
					int userDefStart = getRequestField().getSelectionStart();
					if (userDefStart >= 0) {
						int userDefEnd = getRequestField().getSelectionEnd();
						Highlighter hl = getRequestField().getHighlighter();


						HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(Color.RED);
						try {
							Highlight hlt = (Highlight) hl.addHighlight(userDefStart, userDefEnd, painter);

							list.add("["+userDefStart+","+userDefEnd+"]:"+getRequestField().getText(userDefStart, userDefEnd-userDefStart));
							injectionPointModel.addElement(hlt);
							// Unselect the text
							getRequestField().setSelectionStart(userDefEnd);
							getRequestField().setSelectionEnd(userDefEnd);
							getRequestField().getCaret().setVisible(true);

						} catch (BadLocationException e1) {
							logger.error(e1.getMessage(), e1);
						}
					}

				}
			});

		}
		return addCustomButton;
	}

	private JButton getRemoveCustomButton() {
		if (removeCustomButton == null) {
			removeCustomButton = new JButton(Constant.messages.getString("customFire.custom.button.pt.rem"));
			removeCustomButton.setEnabled(false);

			removeCustomButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) { 
					// Remove any selected injection points
					int userDefStart = getRequestField().getSelectionStart();
					if (userDefStart >= 0) {
						int userDefEnd = getRequestField().getSelectionEnd();
						Highlighter hltr = getRequestField().getHighlighter();
						Highlight[] hls = hltr.getHighlights();

						if (hls != null && hls.length > 0) {
							for (Highlight hl : hls) {
								if (selectionIncludesHighlight(userDefStart, userDefEnd, hl)) {
									try {
										int i = list.indexOf("["+userDefStart+","+userDefEnd+"]:"+getRequestField().getText(userDefStart, userDefEnd-userDefStart));
										list.remove(i);
									} catch (BadLocationException e1) {
										e1.printStackTrace();
									}
									hltr.removeHighlight(hl);
									injectionPointModel.removeElement(hl);
								}
							}
						}

						// Unselect the text
						getRequestField().setSelectionStart(userDefEnd);
						getRequestField().setSelectionEnd(userDefEnd);
						getRequestField().getCaret().setVisible(true);
					}
				}
			});
		}

		return removeCustomButton;
	}

	private JCheckBox getDisableNonCustomVectors() {
		if (disableNonCustomVectors == null) {
			disableNonCustomVectors = new JCheckBox(Constant.messages.getString("customFire.custom.label.disableiv"));
			disableNonCustomVectors.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					// Enable/disable all of the input vector options as appropriate
					getVariantPanel().setAllInjectableAndRPC(!disableNonCustomVectors.isSelected());

					if (disableNonCustomVectors.isSelected()) {
						setFieldValue(FIELD_DISABLE_VARIANTS_MSG,//
								Constant.messages.getString("customFire.custom.warn.disabled"));

					} else {
						setFieldValue(FIELD_DISABLE_VARIANTS_MSG, "");
					}

				}
			});

		}
		return disableNonCustomVectors;
	}

	private JPanel getTechPanel() {
		//if (techPanel == null) {
		techPanel = new JPanel(new GridBagLayout());

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setViewportView(getTechTree());
		scrollPane.setBorder(javax.swing.BorderFactory
				.createEtchedBorder(javax.swing.border.EtchedBorder.RAISED));

		techPanel.add(scrollPane, LayoutHelper.getGBC(0, 0, 1, 1, 1.0D, 1.0D));
		//}

		return techPanel;
	}

	/**
	 * Not used as of now
	 * @return JPanel 
	 */
	private JPanel getScriptPanel() {
		//if (sPanel == null) {
		sPanel = new JPanel(new GridBagLayout());

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setViewportView(getSTree());
		scrollPane.setBorder(javax.swing.BorderFactory
				.createEtchedBorder(javax.swing.border.EtchedBorder.RAISED));

		sPanel.add(scrollPane, LayoutHelper.getGBC(0, 0, 1, 1, 1.0D, 1.0D));
		//}

		return sPanel;
	}

	/**
	 * 
	 * @return TechnologyTreePanel `
	 */
	private TechnologyTreePanel getTechTree() {
		TechnologyTreePanel ttp = new TechnologyTreePanel(Constant.messages.getString("customFire.custom.tab.tech.node"));
		JFileChooser chooser = new JFileChooser(Constant.getZapHome());
		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) {
					return true;
				} else if (file.isFile() && file.getName().endsWith(".ser")) {
					return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				return Constant.messages.getString("customFire.custom.file.format.csp.ser");
			}
		});
		File file = null;
		chooser.setDialogTitle("Open Technology");
		int rc = chooser.showOpenDialog(View.getSingleton().getMainFrame());
		if (rc == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
			if (file == null || !file.getName().equalsIgnoreCase("Tech.ser") ) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.tech.ser.mismatch.error"));
				return ttp;
			}
			try {
				FileInputStream fis = new FileInputStream(file.getPath());
				ObjectInputStream ois = new ObjectInputStream(fis);
				ttp = (TechnologyTreePanel)ois.readObject();
				ttp.addTechTreeListener(ttp, true); 
				ois.close();
				fis.close();

			} catch ( IOException | ClassNotFoundException e1) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.ser.load.error"));
			}
		}

		techTree = ttp;

		return techTree;
	}	


	/**
	 * Not used as of now
	 * @return ScriptTreePanel `
	 */
	public ScriptTreePanel getSTree() {
		//if (sTree == null) {

		ScriptTreePanel stp = new ScriptTreePanel(Constant.messages.getString("customFire.custom.tab.script.node"));
		JFileChooser chooser = new JFileChooser(Constant.getZapHome());
		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) {
					return true;
				} else if (file.isFile() && file.getName().endsWith(".ser")) {
					return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				return Constant.messages.getString("customFire.custom.file.format.csp.ser");
			}
		});
		File file = null;
		chooser.setDialogTitle("Open Scripts settings");
		int rc = chooser.showOpenDialog(View.getSingleton().getMainFrame());
		if (rc == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
			if (file == null || !file.getName().equalsIgnoreCase("Scripts.ser") ) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.scripts.ser.mismatch.error"));
				return stp;
			}
			try {
				FileInputStream fis = new FileInputStream(file.getPath());
				ObjectInputStream ois = new ObjectInputStream(fis);
				stp = (ScriptTreePanel)ois.readObject();
				stp.addScriptTreeListener(stp, true);
				ois.close();
				fis.close();

			} catch ( IOException | ClassNotFoundException e1) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.ser.load.error"));
			}
		}

		sTree = stp;
		return sTree;
	}

	/**
	 * 	Gets Input Vectors tab
	 *  void `
	 */
	private ScannerParam getScannerParam(){

		ScannerParam sp = new ScannerParam();
		JFileChooser chooser = new JFileChooser(Constant.getZapHome());
		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) {
					return true;
				} else if (file.isFile() && file.getName().endsWith(".ser")) {
					return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				return Constant.messages.getString("customFire.custom.file.format.csp.ser");
			}
		});
		File file = null;
		chooser.setDialogTitle("Open Input Vectors");
		int rc = chooser.showOpenDialog(View.getSingleton().getMainFrame());
		if (rc == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
			if (file == null || !file.getName().equalsIgnoreCase("Input Vectors.ser") ) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.inputVectors.ser.mismatch.error"));
				return scannerParam;
			}
			try {
				FileInputStream fis = new FileInputStream(file.getPath());
				ObjectInputStream ois = new ObjectInputStream(fis);
				sp = (ScannerParam)ois.readObject();
				ois.close();
				fis.close();
				return sp;

			} catch ( IOException | ClassNotFoundException e1) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.ser.load.error"));
				return scannerParam;
			}
		}

		return scannerParam; 
	}

	private void setFieldStates() { 
		int userDefStart = getRequestField().getSelectionStart();

		if (getBoolValue(FIELD_RECURSE)) {
			// Dont support custom vectors when recursing
			customPanelStatus.setText(Constant.messages.getString("customFire.custom.status.recurse"));
			getAddCustomButton().setEnabled(false);
			getRemoveCustomButton().setEnabled(false);
			getDisableNonCustomVectors().setEnabled(false);

		} else {
			customPanelStatus.setText(Constant.messages.getString("customFire.custom.status.highlight"));
			if (userDefStart >= 0) {
				int userDefEnd = getRequestField().getSelectionEnd();
				if (selectionIncludesHighlight(userDefStart, userDefEnd,
						getRequestField().getHighlighter().getHighlights())) {
					getAddCustomButton().setEnabled(false);
					getRemoveCustomButton().setEnabled(true);

				} else if (userDefStart < urlPathStart) {
					// No point attacking the method, hostname or port 
					getAddCustomButton().setEnabled(false);

				} else if (userDefStart < headerLength && userDefEnd > headerLength) {
					// The users selection cross the header / body boundry - thats never going to work well
					getAddCustomButton().setEnabled(false);
					getRemoveCustomButton().setEnabled(false);

				} else {
					getAddCustomButton().setEnabled(true);
					getRemoveCustomButton().setEnabled(false);
				}

			} else {
				// Nothing selected
				getAddCustomButton().setEnabled(false);
				getRemoveCustomButton().setEnabled(false);
			}

			getDisableNonCustomVectors().setEnabled(true);
		}

		getRequestField().getCaret().setVisible(true);
	}


	/**
	 * 
	 * @return JList<Highlight> `
	 */
	private JList<Highlight> getInjectionPointList() {
		if (injectionPointList == null) { 
			injectionPointList = new JList<>(injectionPointModel);
			ListCellRendererEx listCellrender = new ListCellRendererEx(getRequestField());
			injectionPointList.setCellRenderer(listCellrender);
		}
		return injectionPointList;
	}


	/**
	 * 
	 * @param start
	 * @param end
	 * @param hl
	 * @return boolean `
	 */
	private boolean selectionIncludesHighlight(int start, int end, Highlight hl) {
		if (hl.getPainter() instanceof DefaultHighlighter.DefaultHighlightPainter) {
			DefaultHighlighter.DefaultHighlightPainter ptr = (DefaultHighlighter.DefaultHighlightPainter) hl.getPainter();
			if (ptr.getColor() != null && ptr.getColor().equals(Color.RED)) {
				// Test for 'RED' needed to prevent matching the users selection
				return start < hl.getEndOffset() && end > hl.getStartOffset();
			}
		}
		return false;
	}

	private boolean selectionIncludesHighlight(int start, int end, Highlight[] hls) {
		for (Highlight hl : hls) {
			if (this.selectionIncludesHighlight(start, end, hl)) {
				return true;
			}
		}
		return false;
	}

	private void reset(boolean refreshUi) {

		// From Apache Commons source code:
		// Note: This method won't work well on hierarchical configurations because it is not able to 
		// copy information about the properties' structure. 
		// So when dealing with hierarchical configuration objects their clone() methods should be used.        
		//        FileConfiguration fileConfig = new XMLConfiguration();
		//        ConfigurationUtils.copy(extension.getScannerParam().getConfig(), fileConfig);

		XMLConfiguration fileConfig = (XMLConfiguration)ConfigurationUtils.cloneConfiguration(extension.getScannerParam().getConfig());
		//  XMLConfiguration fileConfig = null; 

		scannerParam = new ScannerParam(); 
		scannerParam.load(fileConfig);

		optionsParam = new OptionsParam();
		optionsParam.load(fileConfig);

		if (refreshUi) {
			init(target);
			repaint();
			//sTree.reset();
		}
	}

	@Override
	public String getSaveButtonText() {
		return Constant.messages.getString("customFire.custom.button.scan");
	}

	@Override
	public JButton[] getExtraButtons() {
		if (extraButtons == null) {

			JButton resetButton = new JButton(Constant.messages.getString("customFire.custom.button.reset"));
			resetButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					reset(true);
				}
			});

			JButton saveStatesButton = new JButton(Constant.messages.getString("customFire.custom.button.saveState"));
			saveStatesButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					if(sTree==null){
						sTree = new ScriptTreePanel(Constant.messages.getString("customFire.custom.tab.script.node"));
					}
					if(techTree==null){
						techTree = new TechnologyTreePanel(Constant.messages.getString("customFire.custom.tab.tech.node"));
					}
					if(scannerParam==null){
						scannerParam = new ScannerParam();
					}
					//sTree.saveScriptsState(); // not being used
					techTree.saveTechState();
					getVariantPanel().saveParam(scannerParam);
					saveScannerParam(scannerParam);	
					saveCustomVectors(list, getRequestField().getText());
					/*try {
						initModel();
					} catch (Exception e1) {
						e1.printStackTrace();
					}*/
					saveScope(); 

				}
			});

			extraButtons = new JButton[]{resetButton,saveStatesButton};
		}
		return extraButtons;
	}

	/**
	 * Initialises the model, all bootstrap implementations should call this method after base {@code start()}.
	 *
	 * @throws Exception if an error occurs while initialising the {@code Model}
	 * @see #start()
	 * @see Model
	 *//*
	protected void initModel() throws Exception {
		Model.getSingleton().init(new ControlOverrides());
		Model.getSingleton().getOptionsParam().setGUI(true);
		//		Model.getSingleton().getOptionsParam().setGUI(new C);

	}

	
	 * Gets the command line arguments.
	 *
	 * @return the command line arguments
	 
	protected CommandLine getArgs() {
		return args;
	}

	*//**
	 * Gets the control overrides. Necessary for initialising {@code Control} and {@code Model} singletons.
	 *
	 * @return the control overrides
	 * @see org.parosproxy.paros.control.Control
	 * @see Model
	 *//*
	protected ControlOverrides getControlOverrides() {
		return controlOverrides;
	}*/

	
	/**
	 *  Saves current session
	 *  void `
	 */
	protected void saveScope(){
		Control.getSingleton().getMenuFileControl().saveAsSession();
		}
	
	
	/**
	 * Saves custom vectors tab
	 * @param list
	 * @param httpRequest void `
	 */
	protected void saveCustomVectors(ArrayList list, String httpRequest) { 

		JFileChooser chooser = new JFileChooser(Constant.getPoliciesDir());
		File file = new File(Constant.getZapHome(), "Custom Vectors.ser");
		chooser.setSelectedFile(file);

		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) {
					return true;
				} else if (file.isFile() && file.getName().endsWith(".ser")) {
					return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				return Constant.messages.getString("customFire.custom.file.format.csp.ser");
			}
		});
		int rc = chooser.showSaveDialog(View.getSingleton().getMainFrame());
		if (rc == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
			if (file == null) {
				return;
			}

			ArrayList woi=new ArrayList<>();
			woi.add(list);
			woi.add(httpRequest);

			try {

				FileOutputStream fos = new FileOutputStream(file);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(woi);
				oos.close();
				fos.close();
				View.getSingleton().showMessageDialog(Constant.messages.getString("customFire.custom.ser.saveCV.success"));

			} catch (IOException e1) {
				e1.printStackTrace();
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.ser.saveCV.error"));
				return;
			}
		}
	}

	/**
	 * Saves input vectors tab
	 * @param scannerParam void `
	 */
	protected void saveScannerParam(ScannerParam scannerParam) {

		JFileChooser chooser = new JFileChooser(Constant.getPoliciesDir());
		File file = new File(Constant.getZapHome(), "Input Vectors.ser");
		chooser.setSelectedFile(file);

		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) {
					return true;
				} else if (file.isFile() && file.getName().endsWith(".ser")) {
					return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				return Constant.messages.getString("customFire.custom.file.format.csp.ser");
			}
		});
		int rc = chooser.showSaveDialog(View.getSingleton().getMainFrame());
		if (rc == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
			if (file == null) {
				return;
			}
			try {

				FileOutputStream fos = new FileOutputStream(file);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(scannerParam);
				oos.close();
				fos.close();
				View.getSingleton().showMessageDialog(Constant.messages.getString("customFire.custom.ser.saveIV.success"));

			} catch (IOException e1) {
				View.getSingleton().showWarningDialog(Constant.messages.getString("customFire.custom.ser.saveIV.error"));
				return;
			}
		}
		if (rc == JFileChooser.CANCEL_OPTION) {
			chooser.setVisible(false);
			return;
		}
	}


	/**
	 * Use the save method to launch a scan
	 */
	@Override
	public void save() {

		List<Object> contextSpecificObjects = new ArrayList<Object>();

		contextSpecificObjects.add(policyPanel.getScanPolicy());


		if (target == null && this.customPanels != null) {

			// One of the custom scan panels must have specified a target 
			for (CustomFirePanel customPanel : this.customPanels) {

				target = customPanel.getTarget();

				if (target != null) {
					break;
				}
			}
		}

		// Save all Variant configurations 
		getVariantPanel().saveParam(scannerParam);


		// If all other vectors has been disabled
		// force all injectable params and rpc model to NULL
		if (getDisableNonCustomVectors().isSelected()) {
			scannerParam.setTargetParamsInjectable(0);
			scannerParam.setTargetParamsEnabledRPC(0);  
		}

		if (!getBoolValue(FIELD_RECURSE) && injectionPointModel.getSize() > 0) {
			int[][] injPoints = new int[injectionPointModel.getSize()][];
			for (int i = 0; i < injectionPointModel.getSize(); i++) {
				Highlight hl = injectionPointModel.elementAt(i);
				injPoints[i] = new int[2];
				injPoints[i][0] = hl.getStartOffset();
				injPoints[i][1] = hl.getEndOffset();
			}

			try {
				if (target != null && target.getStartNode() != null) {
					VariantUserDefined.setInjectionPoints(
							this.target.getStartNode().getHistoryReference().getURI().toString(),
							injPoints);

					enableUserDefinedRPC();
				}

			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}

		scannerParam.setHostPerScan(extension.getScannerParam().getHostPerScan());

		scannerParam.setThreadPerHost(extension.getScannerParam().getThreadPerHost());

		scannerParam.setHandleAntiCSRFTokens(extension.getScannerParam().getHandleAntiCSRFTokens());

		scannerParam.setMaxResultsToList(extension.getScannerParam().getMaxResultsToList());

		contextSpecificObjects.add(scannerParam);
		contextSpecificObjects.add(techTree.getTechSet());
		//contextSpecificObjects.add(getTechTree().getTechSet());

		if (this.customPanels != null) {
			for (CustomFirePanel customPanel : this.customPanels) {
				Object[] objs = customPanel.getContextSpecificObjects();
				if (objs != null) {
					for (Object obj : objs) {
						contextSpecificObjects.add(obj);
					}
				}
			}
		}

		target.setRecurse(this.getBoolValue(FIELD_RECURSE));

		if (target.getContext() == null && getSelectedContext() != null) {
			target.setContext(getSelectedContext());
		}

		this.extension.startScan(
				target,
				getSelectedUser(),
				contextSpecificObjects.toArray());

	}

	@Override
	public void setVisible(boolean show) {
		super.setVisible(show);

		if (!show) {
			scanPolicy = null;
		}
	}

	@Override
	public String validateFields() {
		if (Control.Mode.safe == Control.getSingleton().getMode()) {

			return Constant.messages.getString("customFire.custom.notSafe.error");
		}

		if (this.customPanels != null) {
			// Check all custom panels validate ok
			for (CustomFirePanel customPanel : this.customPanels) {
				String fail = customPanel.validateFields();
				if (fail != null) {
					return fail;
				}
			}
			// Check if they support a custom target
			for (CustomFirePanel customPanel : this.customPanels) {
				Target target = customPanel.getTarget();
				if (target != null && target.isValid()) {
					// They do, everything is ok
					return null;
				}
			}
		}

		if (this.target == null || !this.target.isValid()) {
			return Constant.messages.getString("customFire.custom.nostart.error");
		}

		switch (Control.getSingleton().getMode()) {
		case protect:
			List<StructuralNode> nodes = target.getStartNodes();
			if (nodes != null) {
				for (StructuralNode node : nodes) {
					if (node instanceof StructuralSiteNode) {
						SiteNode siteNode = ((StructuralSiteNode) node).getSiteNode();
						if (!siteNode.isIncludedInScope()) {
							return Constant.messages.getString(
									"customFire.custom.targetNotInScope.error",
									siteNode.getHierarchicNodeName());
						}
					}
				}
			}
			break;
		default:
		}

		return null;
	}


	/**
	 * Force UserDefinedRPC setting
	 */
	public void enableUserDefinedRPC() {
		int enabledRpc = scannerParam.getTargetParamsEnabledRPC();
		enabledRpc |= ScannerParam.RPC_USERDEF;
		scannerParam.setTargetParamsEnabledRPC(enabledRpc);
	}


	/**
	 * An {@code AbstractParamContainerPanel} that allows to configure {@link CustomScanPolicy scan policies}.
	 */
	private static class ScanPolicyPanel extends AbstractParamContainerPanel {

		private static final long serialVersionUID = -7997974525786756431L;

		private PolicyAllCategoryPanel policyAllCategoryPanel = null;
		private List<PolicyCategoryPanel> categoryPanels = Collections.emptyList();
		private CustomScanPolicy scanPolicy;

		public ScanPolicyPanel(Window parent, ExtensionCustomFire extension, String rootName, CustomScanPolicy scanPolicy) {
			super(rootName);

			this.scanPolicy = scanPolicy;
			String[] ROOT = {};

			policyAllCategoryPanel = new PolicyAllCategoryPanel(parent, extension, scanPolicy, true);
			policyAllCategoryPanel.setName(Constant.messages.getString("customFire.custom.tab.policy"));
			//TODO update the listener
			policyAllCategoryPanel.addScanPolicyChangedEventListener(new ScanPolicyChangedEventListener() {

				@Override
				public void scanPolicyChanged(CustomScanPolicy scanPolicy) {
					ScanPolicyPanel.this.scanPolicy = scanPolicy;
					for (PolicyCategoryPanel panel : categoryPanels) {
						panel.setPluginFactory(scanPolicy.getPluginFactory(), scanPolicy.getDefaultThreshold());
					}
				}
			});
			addParamPanel(null, policyAllCategoryPanel, false);

			categoryPanels = new ArrayList<>(Category.getAllNames().length);
			for (int i = 0; i < Category.getAllNames().length; i++) {
				PolicyCategoryPanel panel = new PolicyCategoryPanel(
						i,
						this.scanPolicy.getPluginFactory(),
						scanPolicy.getDefaultThreshold());
				addParamPanel(ROOT, Category.getName(i), panel, true);
				this.categoryPanels.add(panel);
			}
			showDialog(true);
		}

		public void resetAndSetPolicy(String scanPolicyName) {
			policyAllCategoryPanel.reloadPolicies(scanPolicyName);
		}

		public void setScanPolicy(CustomScanPolicy scanPolicy) {
			policyAllCategoryPanel.setScanPolicy(scanPolicy);
		}

		public CustomScanPolicy getScanPolicy() {
			return scanPolicy;
		}
	}

	/**
	 * Resets the custom fire dialogue to its default state.
	 * 
	 * @since TODO add version
	 */
	void reset() {
		target = null;
		reset(true);
	}

}
