/*
Paros and its related class files.
Paros is an HTTP/HTTPS proxy for assessing web application security.
Copyright (C) 2003-2004 Chinotec Technologies Company

This program is free software; you can redistribute it and/or
modify it under the terms of the Clarified Artistic License
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
Clarified Artistic License for more details.

You should have received a copy of the Clarified Artistic License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2011/11/04 Correct entityEncode
// ZAP: 2011/12/19 Escape invalid XML characters
// ZAP: 2012/08/07 synchronize calls on staticDateFormat
// ZAP: 2013/01/23 Clean up of exception handling/logging.
// ZAP: 2013/09/26 Issue 802: XML report generated by API differs from GUI report
// ZAP: 2015/08/19 Issue 1804: Disable processing of XML external entities by default
// ZAP: 2015/11/16 Issue 1555: Rework inclusion of HTML tags in reports 

package org.parosproxy.paros.extension.report;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.zaproxy.zap.utils.XMLStringUtil;
import org.zaproxy.zap.utils.XmlUtils;

public class ReportGenerator {

	private static final Logger logger = Logger.getLogger(ReportGenerator.class);

	// private static Pattern patternWindows = Pattern.compile("window", Pattern.CASE_INSENSITIVE);
	// private static Pattern patternLinux = Pattern.compile("linux", Pattern.CASE_INSENSITIVE);

	private static final SimpleDateFormat staticDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss");

	public static File XMLToHtml(Document xmlDocument, String infilexsl, File outFile) {
		File stylesheet = null;

		outFile = new File(outFile.getAbsolutePath());
		try {
			stylesheet = new File(infilexsl);

			DOMSource source = new DOMSource(xmlDocument);

			// Use a Transformer for output
			TransformerFactory tFactory = TransformerFactory.newInstance();
			StreamSource stylesource = new StreamSource(stylesheet);
			Transformer transformer = tFactory.newTransformer(stylesource);

			// Make the transformation and write to the output file
			StreamResult result = new StreamResult(outFile);
			transformer.transform(source, result);

		} catch (TransformerException e) {
			logger.error(e.getMessage(), e);
		}

		return outFile;
	}

	public static File stringToHtml(String inxml, String infilexsl, String outfilename) {
		if (infilexsl != null) {
			Document doc = null;
	
			// factory.setNamespaceAware(true);
			// factory.setValidating(true);
			File stylesheet = null;
			File outfile = null;
			StringReader inReader = new StringReader(inxml);
			String tempOutfilename = outfilename + ".temp"; 
	
			try {
				stylesheet = new File(infilexsl);
				outfile = new File(tempOutfilename);
	
				DocumentBuilder builder = XmlUtils.newXxeDisabledDocumentBuilderFactory().newDocumentBuilder();
				doc = builder.parse(new InputSource(inReader));
	
				// Use a Transformer for output
				TransformerFactory tFactory = TransformerFactory.newInstance();
				StreamSource stylesource = new StreamSource(stylesheet);
				Transformer transformer = tFactory.newTransformer(stylesource);
	
				DOMSource source = new DOMSource(doc);
				StreamResult result = new StreamResult(outfile);
				transformer.transform(source, result);
	
			} catch (TransformerException | SAXException | ParserConfigurationException | IOException e) {
				logger.error(e.getMessage(), e);
				// Save the xml for diagnosing the problem
				BufferedWriter bw = null;
	
				try {
					bw = new BufferedWriter(new FileWriter(outfilename + "-orig.xml"));
					bw.write(inxml);
				} catch (IOException e2) {
					logger.error(e.getMessage(), e);
				} finally {
					try {
						if (bw != null) {
							bw.close();
						}
					} catch (IOException ex) {
					}
				}
	
			} finally {
	
			}
			// Replace the escaped tags used to make the report look slightly better.
			// This is a temp fix to ensure reports always get generated
			// we should really adopt something other than XSLT ;)
			BufferedReader br = null;
			BufferedWriter bw = null;
			String line;
	
			try {
				br = new BufferedReader(new FileReader(tempOutfilename));
				bw = new BufferedWriter(new FileWriter(outfilename));
	
				while ((line = br.readLine()) != null) {
					bw.write(line.
							replace("&lt;p&gt;", "<p>").
							replace("&lt;/p&gt;", "</p>"));
					bw.newLine();
				}
	
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			} finally {
				try {
					if (br != null) {
						br.close();
					}
					if (bw != null) {
						bw.close();
					}
				} catch (IOException ex) {
				}
			}
			// Remove the temporary file
			outfile.delete();
		} else {
			// No XSLT file specified, just output the XML straight to the file
			BufferedWriter bw = null;
			
			try {
				bw = new BufferedWriter(new FileWriter(outfilename));
				bw.write(inxml);
			} catch (IOException e2) {
				logger.error(e2.getMessage(), e2);
			} finally {
				try {
					if (bw != null) {
						bw.close();
					}
				} catch (IOException ex) {
				}
			}
		}

		return new File (outfilename);
	}

	public static String stringToHtml(String inxml, String infilexsl) {
		Document doc = null;

		// factory.setNamespaceAware(true);
		// factory.setValidating(true);
		File stylesheet = null;
		StringReader inReader = new StringReader(inxml);
		StringWriter writer = new StringWriter();

		try {
			stylesheet = new File(infilexsl);

			DocumentBuilder builder = XmlUtils.newXxeDisabledDocumentBuilderFactory().newDocumentBuilder();
			doc = builder.parse(new InputSource(inReader));

			// Use a Transformer for output
			TransformerFactory tFactory = TransformerFactory.newInstance();
			StreamSource stylesource = new StreamSource(stylesheet);
			Transformer transformer = tFactory.newTransformer(stylesource);

			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(writer);
			transformer.transform(source, result);

		} catch (TransformerException | SAXException | ParserConfigurationException | IOException e) {
			logger.error(e.getMessage(), e);
		} finally {

		}

		// Replace the escaped tags used to make the report look slightly better.
		// This is a temp fix to ensure reports always get generated
		// we should really adopt something other than XSLT ;)
		return writer.toString().
				replace("&lt;p&gt;", "<p>").
				replace("&lt;/p&gt;", "</p>");
	}

	public static File fileToHtml(String infilexml, String infilexsl, String outfilename) {
		Document doc = null;

		// factory.setNamespaceAware(true);
		// factory.setValidating(true);
		File stylesheet = null;
		File datafile = null;
		File outfile = null;

		try {
			stylesheet = new File(infilexsl);
			datafile = new File(infilexml);
			outfile = new File(outfilename);

			DocumentBuilder builder = XmlUtils.newXxeDisabledDocumentBuilderFactory().newDocumentBuilder();
			doc = builder.parse(datafile);

			// Use a Transformer for output
			TransformerFactory tFactory = TransformerFactory.newInstance();
			StreamSource stylesource = new StreamSource(stylesheet);
			Transformer transformer = tFactory.newTransformer(stylesource);

			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(outfile);
			transformer.transform(source, result);

		} catch (TransformerException | SAXException | ParserConfigurationException | IOException e) {
			logger.error(e.getMessage(), e);
		} finally {

		}

		return outfile;

	}

	/**
	 * Encode entity for HTML or XML output.
	 */
	public static String entityEncode(String text) {
		String result = text;

		if (result == null) {
			return result;
		}

		// The escapeXml function doesnt cope with some 'special' chrs

		return StringEscapeUtils.escapeXml(XMLStringUtil.escapeControlChrs(result));
	}

	/**
	 * Get today's date string.
	 */
	public static String getCurrentDateTimeString() {
		Date dateTime = new Date(System.currentTimeMillis());
		return getDateTimeString(dateTime);

	}

	public static String getDateTimeString(Date dateTime) {
		// ZAP: fix unsafe call to DateFormats
		synchronized (staticDateFormat) {
			return staticDateFormat.format(dateTime);
		}
	}

	public static void addChildTextNode(Document doc, Element parent, String nodeName, String text) {
		Element child = doc.createElement(nodeName);
		child.appendChild(doc.createTextNode(text));
		parent.appendChild(child);
	}

	public static String getDebugXMLString(Document doc) throws TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		StringWriter writer = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(writer));
		return writer.getBuffer().toString().replaceAll("\n|\r", "");
	}
}
