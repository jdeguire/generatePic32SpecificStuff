/* Copyright (c) 2020, Jesse DeGuire
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.github.jdeguire.generatePic32SpecificStuff;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * This is meant to be a convenient wrapper around the .atdf documents used to describe Atmel parts.
 * There is information in these files that is not yet present in the .pic files used by the MPLAB X
 * API, and so we need a way to access the ATDF files for it.
 * 
 * Consider this class as the "root" of all of the other Atdf___ classes.  That is, when trying to
 * get info from an ATDF document, start by getting the appropriate object using one of these methods
 * and then, if needed, use that object to drill down further.
 */
public class AtdfDoc {
    private static final HashMap<String, String> DOC_CACHE_ = new HashMap<>(100);
    private final Node deviceNode_;
    private final Node modulesNode_;
    private final Node rootNode_;
    private final String deviceName_;
    private AtdfDevice atdfDevice_ = null;

    private List<AtdfPeripheral> atdfPeripherals_ = null;

 
    /* Create a new AtdfDoc object by opening the appropriate ATDF file in the MPLAB X database.  If
     * the file cannot be found or if it does not appear to be a valid ATDF file, this will throw
     * an exception to indicate such.
     */
    public AtdfDoc(String devname) throws ParserConfigurationException, 
                                           SAXException,
                                           IOException, 
                                           FileNotFoundException {
        if(DOC_CACHE_.isEmpty())
            populateDocumentCache();

        if(devname.startsWith("SAM"))
            devname = "AT" + devname;

        String atdfPath = DOC_CACHE_.get(devname);
        
        // Based on example code from:
        // https://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/
        if(null != atdfPath) {
            File atdfFile = new File(atdfPath);

            DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = docBuilder.parse(atdfFile);
            doc.getDocumentElement().normalize();

            // Get to the "<device>" node, which is under the "<devices>" node.
            rootNode_ = (Node)doc.getDocumentElement();
            Node devicesNode = Utils.filterFirstChildNode(rootNode_, "devices", null, null);
// TODO:  We might be able to remove these nodes in the future.
            deviceNode_ = Utils.filterFirstChildNode(devicesNode, "device", "name", devname);
            modulesNode_ = Utils.filterFirstChildNode(rootNode_, "modules", null, null);

            // Use these as simple sanity checks to see that we have a valid ATDF file.
            if(null == deviceNode_) {
                throw new SAXException("Device node not found for device " + devname +
                                        ".  Is " + atdfPath + " a valid ATDF file?");
            }

            if(null == modulesNode_) {
                throw new SAXException("Modules node not found for device " + devname +
                                        ".  Is " + atdfPath + " a valid ATDF file?");
            }

            deviceName_ = devname;
            
        } else {
            throw new FileNotFoundException("Cannot find ATDF file for device " + devname);
        }
    }


    /* Return the name of the device represented by this AtdfDoc object.
     */
    public String getName() {
        return deviceName_;
    }

    /* Get an object that provides information about the device referenced in this AtdfDoc, such as
     * its memory layout and electrical parameters.  This will throw an SAXException if the object
     * could not be created as that would indicate an issue with the ATDF document itself.
     */
    public AtdfDevice getDevice() throws SAXException {
        if(null == atdfDevice_)
            atdfDevice_ = new AtdfDevice(rootNode_);

        return atdfDevice_;
    }
 
    /* Get a list of all of the peripherals provided in this ATDF document.  This returns null if
     * the peripherals cannot be read for some reason.
     */
    public List<AtdfPeripheral> getAllPeripherals() {
        if(null == atdfPeripherals_) {
            atdfPeripherals_ = AtdfPeripheral.getAllPeripherals(rootNode_);
        }

        return atdfPeripherals_;
    }

    /* Get the peripheral object for the peripheral with the given name (case-sensitive).  The
     * peripheral object represents all instances of the peripheral on the device and so this needs
     * only the basename of the peripheral.  For example, "ADC", "ADC0", and "ADC1" would all return
     * the same thing.
     */
    public AtdfPeripheral getPeripheral(String peripheralName) {
        String basename = Utils.getInstanceBasename(peripheralName);

        for(AtdfPeripheral p : getAllPeripherals()) {
            if(basename.equals(p.getName()))
                return p;
        }

        return null;
    }
 
 
    /* Walk the directory tree at which the MPLAB X packs are located to find all of the ATDF files
     * and fill our cache with them.  This is a long operation and so we should do it only once.
     */
    private void populateDocumentCache() {
        File packsdir = new File(System.getProperty("packslib.packsfolder"));
        String exts[] = {"atdf", "ATDF"};

        // MPLAB X has a very old Apache Commons package that doesn't support Java generics.
        @SuppressWarnings("unchecked")
        Collection<File> atdfFiles = FileUtils.listFiles(packsdir, exts, true);

        for(File f : atdfFiles) {
            String basename = f.getName();
            basename = basename.substring(0, basename.lastIndexOf('.'));

            DOC_CACHE_.put(basename, f.getAbsolutePath());
        }
    }
}
