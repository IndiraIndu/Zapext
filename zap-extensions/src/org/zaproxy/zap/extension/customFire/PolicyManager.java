/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Copyright the ZAP Development Team
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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

/**
 * 
 * @author <a href="mailto:indu79455@gmail.com">Indira</a>
 *
 * Nov 29, 2016  org.zaproxy.zap.extension.customFire
 */
public class PolicyManager implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final String POLICY_EXTENSION = ".policy";

	private static final String DEFAULT_POLICY_NAME = "Default Policy";
	public static final String ILLEGAL_POLICY_NAME_CHRS = "/`?*\\<>|\":\t\n\r";

	private List<String> allPolicyNames = null;
	private ExtensionCustomFire extension;

	private static final Logger logger = Logger.getLogger(PolicyManager.class);

	ScannerParam scannerParam = new ScannerParam();

	public PolicyManager(ExtensionCustomFire extension) {
		this.extension = extension;
	}

	public void init() {
		// Force load
		getAllPolicyNames();
	}

	/**
	 * 
	 * @return List<String> `
	 */
	public synchronized List<String> getAllPolicyNames() {

		if (allPolicyNames == null) {
			allPolicyNames = new ArrayList<String>();
			for (String file : Constant.getPoliciesDir().list()) {
				if (file.endsWith(POLICY_EXTENSION)) {
					logger.debug("Found policy file " + file);
					allPolicyNames.add(file.substring(0, file.lastIndexOf(POLICY_EXTENSION)));
				}
			}
			if (allPolicyNames.size() == 0) {
				// No policies. Create a default one
				CustomScanPolicy defaultPolicy = new CustomScanPolicy();
				defaultPolicy.setName(DEFAULT_POLICY_NAME);
				// Load from the 'old' configs
				defaultPolicy.getPluginFactory().loadAllPlugin(scannerParam.getConfig()); 
				try {
					// Note this will add the name to allPolicyNames
					this.savePolicy(defaultPolicy);
				} catch (ConfigurationException e) {
					logger.debug("Failed to create default scan policy in " + Constant.getPoliciesDir().getAbsolutePath(), e);
				}
			}

			Collections.sort(allPolicyNames);
		}
		return allPolicyNames;
	}

	/**
	 * 
	 * @param policy
	 * @throws ConfigurationException void `
	 */
	public void savePolicy (CustomScanPolicy policy) throws ConfigurationException {
		this.savePolicy(policy, null);
	}

	/**
	 * 
	 * @param policy
	 * @param previousName
	 * @throws ConfigurationException void `
	 */
	public void savePolicy (CustomScanPolicy policy, String previousName) throws ConfigurationException {
		logger.debug("Save policy " + policy.getName());

		File file = new File(Constant.getPoliciesDir(), policy.getName() + POLICY_EXTENSION);

		ZapXmlConfiguration conf = new ZapXmlConfiguration();
		conf.setProperty("policy", policy.getName());
		conf.setProperty("scanner.level", policy.getDefaultThreshold().name());
		conf.setProperty("scanner.strength", policy.getDefaultStrength().name());

		policy.getPluginFactory().saveTo(conf);

		conf.save(file);

		if (previousName != null && previousName.length() > 0) {
			allPolicyNames.remove(previousName);
		}
		if (!allPolicyNames.contains(policy.getName())) {
			allPolicyNames.add(policy.getName());
			Collections.sort(allPolicyNames);
		}
	}

	/**
	 * Tells whether or not a scan policy with the given {@code name} exists.
	 *
	 * @param name the name of the scan policy
	 * @return {@code true} if the scan policy exists, {@code false} otherwise
	 * @since 2.4.3
	 */
	public static boolean policyExists(String name) {
		return (new File(Constant.getPoliciesDir(), name + POLICY_EXTENSION)).exists();

	}

	public CustomScanPolicy getPolicy (String name) throws ConfigurationException {
		return this.loadPolicy(new File(Constant.getPoliciesDir(), name + POLICY_EXTENSION));
	}

	public CustomScanPolicy loadPolicy (String name) throws ConfigurationException {
		return this.loadPolicy(new File(Constant.getPoliciesDir(), name + POLICY_EXTENSION));
	}

	private CustomScanPolicy loadPolicy (File file) throws ConfigurationException {
		CustomScanPolicy policy = new CustomScanPolicy(new ZapXmlConfiguration(file));
		if (! file.getName().equals(policy.getName() + POLICY_EXTENSION)) {
			// The file name takes precedence in case there is another policy with the same name
			policy.setName(file.getName().substring(0, file.getName().indexOf(POLICY_EXTENSION)));
		}

		return policy;
	}

	public void importPolicy (File file) throws ConfigurationException, IOException {
		logger.debug("Import policy from " + file.getAbsolutePath());
		CustomScanPolicy policy = new CustomScanPolicy(new ZapXmlConfiguration(file));
		String baseName = file.getName();
		if (baseName.endsWith(POLICY_EXTENSION)) {
			// Strip off the extension for the 'friendly name' and if we need to prevent overwriting an existing one
			baseName = baseName.substring(0, baseName.indexOf(POLICY_EXTENSION));
		}
		String finalName = baseName;
		File newFile = new File(Constant.getPoliciesDir(), finalName + POLICY_EXTENSION);
		int i=2;
		while (newFile.exists()) {
			finalName = baseName + i; 
			newFile = new File(Constant.getPoliciesDir(), finalName + POLICY_EXTENSION);
			i++;
		}
		policy.setName(finalName);
		this.savePolicy(policy);
	}

	public void exportPolicy (CustomScanPolicy policy, File file) throws ConfigurationException {
		logger.debug("Export policy to " + file.getAbsolutePath());
		ZapXmlConfiguration conf = new ZapXmlConfiguration();
		conf.setProperty("policy", policy.getName());
		conf.setProperty("scanner.level", policy.getDefaultThreshold().name());
		conf.setProperty("scanner.strength", policy.getDefaultStrength().name());
		policy.getPluginFactory().saveTo(conf);
		conf.save(file);
	}

	public CustomScanPolicy getTemplatePolicy() throws ConfigurationException {
		return new CustomScanPolicy();
	}

	public void deletePolicy(String name) {
		logger.debug("Delete policy " + name);
		File file = new File(Constant.getPoliciesDir(), name + POLICY_EXTENSION);
		if (file.exists()) {
			file.delete();
		}
		this.allPolicyNames.remove(name);
	}

	public CustomScanPolicy getDefaultScanPolicy() {
		try {
			String policyName = scannerParam.getDefaultPolicy(); //ia
			if (this.policyExists(policyName)) {
				logger.debug("getDefaultScanPolicy: " + policyName);
				return this.loadPolicy(policyName);
			}
			// No good, try the default name
			policyName = DEFAULT_POLICY_NAME;
			if (this.policyExists(policyName)) {
				logger.debug("getDefaultScanPolicy (default name): " + policyName);
				return this.loadPolicy(policyName);
			}
			if (this.allPolicyNames.size() > 0) {
				logger.debug("getDefaultScanPolicy (first one): " + policyName);
				return this.loadPolicy(this.allPolicyNames.get(0));
			}

		} catch (ConfigurationException e) {
			logger.error(e.getMessage(), e);
		}
		// Return a new 'blank' one
		logger.debug("getDefaultScanPolicy (new blank)");
		//returning default policy
		return new CustomScanPolicy();
	}

	public CustomScanPolicy getAttackScanPolicy() {
		try {
			String policyName = extension.getScannerParam().getAttackPolicy();
			if (this.policyExists(policyName)) {
				return this.loadPolicy(policyName);
			}
			// No good, try the default name
			policyName = DEFAULT_POLICY_NAME;
			if (this.policyExists(policyName)) {
				return this.loadPolicy(policyName);
			}
			if (this.allPolicyNames.size() > 0) {
				return this.loadPolicy(this.allPolicyNames.get(0));
			}
		} catch (ConfigurationException e) {
			logger.error(e.getMessage(), e);
		}
		// Return a new 'blank' one
		return new CustomScanPolicy();
	}

	public boolean isLegalPolicyName(String str) {
		for (int i=0; i < str.length(); i++) {
			if (ILLEGAL_POLICY_NAME_CHRS.indexOf(str.charAt(i)) >= 0) {
				return false;
			}
		}
		return true;
	}

}