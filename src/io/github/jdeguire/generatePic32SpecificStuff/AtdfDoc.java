/* Copyright (c) 2019, Jesse DeGuire
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * This is meant to be a convenient wrapper around the .atdf documents used to describe Atmel parts.
 * There is information in these files that is not yet present in the .pic files used by the MPLAB X
 * API, and so we need a way to access the ATDF files for it.
 */
public class AtdfDoc {

    /**
     * This encapsulates the info from "parameter" XML nodes, which contains macro names and values
     * that pertain to the device itself or its peripheral instances.
     */
    public class Parameter{
        private final String name_;
        private final String value_;
        private final String caption_;
        
        Parameter(Node atdfNode) {
           name_ = Utils.getNodeAttribute(atdfNode, "name", "");
           value_ = Utils.getNodeAttribute(atdfNode, "value", "");
           caption_ = Utils.getNodeAttribute(atdfNode, "caption", "");
        }

        /* Get the parameter name, which will be formatted like a C macro. */
        public String getName()     { return name_; }

        /* Get the parameter value. */
        public String getValue()    { return value_; }

        /* Get the parameter caption, which would be a C comment describing the parameter. */
        public String getCaption()  { return caption_; }
    }


    private static final HashMap<String, String> DOC_CACHE_ = new HashMap<>(100);
    private final Document doc_;

    AtdfDoc(String devname) throws ParserConfigurationException, 
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
            doc_ = docBuilder.parse(atdfFile);
            doc_.getDocumentElement().normalize();
        } else {
            throw new FileNotFoundException("Cannot find ATDF file for device " + devname);
        }
    }

    public List<Parameter> getAtdfParameters(String peripheral) {
        ArrayList<Parameter> params = new ArrayList<>(16);
        Element atdfElement = doc_.getDocumentElement();

        // Get to the "<device>" node, which is under the "<devices>" node.
        Node devicesNode = Utils.filterFirstChildNode((Node)atdfElement, "devices", null, null);
        Node deviceNode = Utils.filterFirstChildNode(devicesNode, "device", null, null);
        Node targetNode;

        // Navigate to peripheral node if a peripheral is provided.
        if(null != peripheral  &&  !peripheral.isEmpty()) {
            // We need to search for the peripheral's basename--that is, a peripheral name minus
            // the instance number.  So "ADC1" would have a basename of "ADC".
            int basesplit = peripheral.length()-1;
            while(basesplit > 0  &&  Character.isDigit(peripheral.charAt(basesplit)))
                --basesplit;

            String basename = peripheral.substring(0, basesplit+1);
            Node peripheralsNode = Utils.filterFirstChildNode(deviceNode, "peripherals", null, null);
            Node moduleNode = Utils.filterFirstChildNode(peripheralsNode, "module", "name", basename);

            targetNode = Utils.filterFirstChildNode(moduleNode, "instance", "name", peripheral);
        } else {
            // Else we'll get the parameters for the device itself.
            targetNode = deviceNode;
        }

        Node parametersNode = Utils.filterFirstChildNode(targetNode, "parameters", null, null);
        if(null != parametersNode) {
            List<Node> paramsList = Utils.filterAllChildNodes(parametersNode, "param", null, null);

            for(Node paramNode : paramsList) {
                params.add(new Parameter(paramNode));
            }
        }

        return params;
    }


    private void populateDocumentCache() {
        File packsdir = new File(System.getProperty("packslib.packsfolder"));
        String exts[] = {"atdf", "ATDF"};
        Collection<File> atdfFiles = FileUtils.listFiles(packsdir, exts, true);

        for(File f : atdfFiles) {
            String basename = f.getName();
            basename = basename.substring(0, basename.lastIndexOf('.'));

            DOC_CACHE_.put(basename, f.getAbsolutePath());
        }
    }

}
