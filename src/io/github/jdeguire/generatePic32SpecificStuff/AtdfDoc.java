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
     * that pertain to the device itself or its peripheral instances.  Property and Parameter XML 
     * nodes are pretty much the same, but we'll have different classes in case that changes in the 
     * future.
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

    /**
     * This encapsulates the info from "property" XML nodes, which contains macro names and values
     * that pertain to the device itself.  Property and Parameter XML nodes are pretty much the 
     * same, but we'll have different classes in case that changes in the future.
     */
    public class Property {
        private final String name_;
        private final String value_;
        private final String caption_;
        
        Property(Node atdfNode) {
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

    /**
     * This encapsulates the info from the "signal" XML nodes, which appear to contain info about
     * the pins used by a particular peripheral instance.
     */
    public class Signal {
        private final String group_;
        private final String index_;
        private final String function_;
        private final String pad_;
        private final String ioset_;

        Signal(Node atdfNode) {
            group_ = Utils.getNodeAttribute(atdfNode, "group", "");
            index_ = Utils.getNodeAttribute(atdfNode, "index", "");
            function_ = Utils.getNodeAttribute(atdfNode, "function", "");
            pad_ = Utils.getNodeAttribute(atdfNode, "pad", "");
            ioset_ = Utils.getNodeAttribute(atdfNode, "ioset", "");
        }

        /* Get the signal group, which indicates the signal's function (Tx, Rx, Ain, etc.). */
        public String getGroup()       { return group_; }

        /* Get the signal index, which is a channel number (such as AIN0, AIN1, etc.). */
        public String getIndex()       { return index_; }

        /* Get the signal function, which appears related to pin muxing. */
        public String getFunction()    { return function_; }

        /* Get the signal pad, which is the IO port (PA0, PB6, etc.) of the pin using this signal. */
        public String getPad()         { return pad_; }

        /* Get the signal io set, which may be related to alternate pin configurations. */
        public String getIoset()       { return ioset_; }
    }

    /**
     * This encapsulates the info from the "memory-segment" XML nodes, which appear to contain info about
     * the pins used by a particular peripheral instance.
     */
    public class MemSegment {
        private final String name_;
        private final long startAddr_;
        private final long totalSize_;
        private final long pageSize_;

        MemSegment(Node atdfNode) {
            name_ = Utils.getNodeAttribute(atdfNode, "name", "");
            startAddr_ = Utils.getNodeAttributeAsLong(atdfNode, "start", 0);
            totalSize_ = Utils.getNodeAttributeAsLong(atdfNode, "size", 0);
            pageSize_ = Utils.getNodeAttributeAsLong(atdfNode, "pagesize", 0);
        }

        /* Get the name of the memory segment, which will be formatted like a C macro. */
        public String getName()        { return name_; }

        /* Get the starting address of the segment. */
        public long getStartAddress()  { return startAddr_; }

        /* Get the total size in bytes of the segment. */
        public long getTotalSize()     { return totalSize_; }

        /* Get the page size of the segment in bytes.  This applied only to flash segments and will 
         * be 0 for other types of memory. */
        public long getPageSize()      { return pageSize_; }
    }


    private static final HashMap<String, String> DOC_CACHE_ = new HashMap<>(100);
    private final Node deviceNode_;
    
    /* Create a new AtdfDoc object by opening the appropriate ATDF file in the MPLAB X database.  If
     * the file cannot be found or if it does not appear to be a valid ATDF file, this will throw
     * an exception to indicate such.
     */
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
            Document doc = docBuilder.parse(atdfFile);
            doc.getDocumentElement().normalize();

            // Get to the "<device>" node, which is under the "<devices>" node.
            Element atdfElement = doc.getDocumentElement();
            Node devicesNode = Utils.filterFirstChildNode((Node)atdfElement, "devices", null, null);
            deviceNode_ = Utils.filterFirstChildNode(devicesNode, "device", "name", devname);

            if(null == deviceNode_) {
                throw new SAXException("Device node not found for device " + devname +
                                        ".  Is " + atdfPath + " a valid ATDF file?");
            }

            // Just try this as a simple sanity check.
            if(null == getPeripheralsNode()) {
                throw new SAXException("Peripherals node not found for device " + devname +
                                        ".  Is " + atdfPath + " a valid ATDF file?");
            }
        } else {
            throw new FileNotFoundException("Cannot find ATDF file for device " + devname);
        }
    }


    /* Get a list of parameters for the device itself, which generally includes things like CPU
     * revision and whether or not it has an FPU.
     */
    public List<Parameter> getDeviceParameters() {
        ArrayList<Parameter> params = new ArrayList<>(16);

        Node parametersNode = Utils.filterFirstChildNode(deviceNode_, "parameters", null, null);

        if(null != parametersNode) {
            List<Node> paramsList = Utils.filterAllChildNodes(parametersNode, "param", null, null);

            for(Node paramNode : paramsList) {
                params.add(new Parameter(paramNode));
            }
        }        

        return params;
    }

    /* Get a list of signature/device ID values for the device.
     */
    public List<Property> getDeviceSignatureProperties() {
        ArrayList<Property> props = new ArrayList<>(8);

        Node propGroupsNode = Utils.filterFirstChildNode(deviceNode_, "property-groups", null, null);
        Node signaturesGroupNode = Utils.filterFirstChildNode(propGroupsNode, "property-group", 
                                                              "name", "SIGNATURES");

        if(null != signaturesGroupNode) {
            List<Node> propsList = Utils.filterAllChildNodes(signaturesGroupNode, "property", null, null);

            for(Node propNode : propsList) {
                props.add(new Property(propNode));
            }
        }        

        return props;
    }

    /* Get a list of electrical characteristic values for the device, such as clock speeds and flash
     * wait states.
     */
    public List<Property> getDeviceElectricalProperties() {
        ArrayList<Property> props = new ArrayList<>(8);

        Node propGroupsNode = Utils.filterFirstChildNode(deviceNode_, "property-groups", null, null);
        Node electricalGroupNode = Utils.filterFirstChildNode(propGroupsNode, "property-group", 
                                                              "name", "ELECTRICAL_CHARACTERISTICS");

        if(null != electricalGroupNode) {
            List<Node> propsList = Utils.filterAllChildNodes(electricalGroupNode, "property", null, null);

            for(Node propNode : propsList) {
                props.add(new Property(propNode));
            }
        }        

        return props;
    }

    /* Get a list of the memory segments on the device.
     */
    public List<MemSegment> getDeviceMemorySegments() {
        ArrayList<MemSegment> memories = new ArrayList<>(16);

        Node addrSpacesNode = Utils.filterFirstChildNode(deviceNode_, "address-spaces", null, null);
        Node baseSpaceNode = Utils.filterFirstChildNode(addrSpacesNode, "address-space", "id", "base");

        if(null != baseSpaceNode) {
            List<Node> memSegmentList = Utils.filterAllChildNodes(baseSpaceNode, "memory-segment", 
                                                                  null, null);
            
            for(Node memSegmentNode : memSegmentList) {
                memories.add(new MemSegment(memSegmentNode));
            }
        }

        return memories;
    }

    /* Get a list of parameters for the given named peripheral instance.  The name of the peripheral
     * would be as it appears in .PIC or .ATDF files.  Peripherals with only one instance usually do
     * not have a number ("USB" or "GMAC") and ones with multiple instances do ("ADC0" or "SERCOM4").
     */
    public List<Parameter> getPeripheralInstanceParameters(String instance) {
        ArrayList<Parameter> params = new ArrayList<>(16);

        Node instanceNode = getPeripheralInstanceNode(instance);
        Node parametersNode = Utils.filterFirstChildNode(instanceNode, "parameters", null, null);

        if(null != parametersNode) {
            List<Node> paramsList = Utils.filterAllChildNodes(parametersNode, "param", null, null);

            for(Node paramNode : paramsList) {
                params.add(new Parameter(paramNode));
            }
        }

        return params;
    }

    /* Get a list of signals for the given named peripheral instance.  The name of the peripheral
     * would be as it appears in .PIC or .ATDF files.  Peripherals with only one instance usually do
     * not have a number ("USB" or "GMAC") and ones with multiple instances do ("ADC0" or "SERCOM4").
     */
    public List<Signal> getPeripheralInstanceSignals(String instance) {
        ArrayList<Signal> signals = new ArrayList<>(8);

        Node instanceNode = getPeripheralInstanceNode(instance);
        Node signalsNode = Utils.filterFirstChildNode(instanceNode, "signals", null, null);

        if(null != signalsNode) {
            List<Node> signalsList = Utils.filterAllChildNodes(signalsNode, "signal", null, null);

            for(Node signalNode : signalsList) {
                signals.add(new Signal(signalNode));
            }
        }

        return signals;
    }

    /* Get the base address of the registers for the given peripheral instance or 0 if the instance
     * could not be found.
     */
    public long getPeripheralInstanceBaseAddress(String instance) {
        Node instanceNode = getPeripheralInstanceNode(instance);
        Node regGroupNode = Utils.filterFirstChildNode(instanceNode, "register-group", null, null);

        return Utils.getNodeAttributeAsLong(regGroupNode, "offset", 0);
    }
    
    /* Get a unique ID string for the given peripheral.  Two peripherals with the same name will have
     * different IDs if they have different implementations.  For example, the ADC on the SAME54 has
     * a different ID from the ADC on the SAMA5D27.  This will return null if the ID for the given
     * peripheral could not be found.
     */
    public String getPeripheralModuleId(String peripheral) {
        Node moduleNode = getPeripheralModuleNode(peripheral);
        String id = null;

        if(null != moduleNode) {
            id = Utils.getNodeAttribute(moduleNode, "id", null);
        }

        return id;
    }


    /* These next three are convenience methods for getting often-used nodes.  The ATDF file 
     * contains a single <peripherals> node in the <device> node.  The <peripherals> node has many
     * <module> nodes and each <module> node has one or more <instance> nodes.
     *
     * These return null if the desired node could not be found.
     */
    private Node getPeripheralsNode() {
        return Utils.filterFirstChildNode(deviceNode_, "peripherals", null, null);        
    }

    private Node getPeripheralModuleNode(String peripheral) {
        String basename = Utils.getInstanceBasename(peripheral);

        if(!basename.isEmpty()) {
            return Utils.filterFirstChildNode(getPeripheralsNode(), "module", "name", basename);
        } else {
            return null;
        }
    }
    
    private Node getPeripheralInstanceNode(String peripheral) {
        Node moduleNode = getPeripheralModuleNode(peripheral);
        return Utils.filterFirstChildNode(moduleNode, "instance", "name", peripheral);
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
