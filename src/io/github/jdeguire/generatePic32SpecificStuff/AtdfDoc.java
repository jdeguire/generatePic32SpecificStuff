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
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * This is meant to be a convenient wrapper around the .atdf documents used to describe Atmel parts.
 * There is information in these files that is not yet present in the .pic files used by the MPLAB X
 * API, and so we need a way to access the ATDF files for it.
 */
public class AtdfDoc {

    /**
     * This encapsulates the info from "parameter" XML nodes, which contain macro names and values
     * that pertain to the device itself or its peripheral instances.  Property and Parameter XML 
     * nodes are pretty much the same, but we'll have different classes in case that changes in the 
     * future.
     */
    public class Parameter {
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
     * This encapsulates the info from "register" XML nodes, which contain basic info about a 
     * peripheral register, such as its access type and offset.
     */
    public class Register {
        public static final int REG_READ  = 0x01;
        public static final int REG_WRITE = 0x02;
        public static final int REG_RW    = 0x03;

        private final String name_;
        private final long offset_;
        private final int rw_;
        private final int size_;
        private final int count_;
        private final long init_;
        private final String caption_;
        private final String group_;

        Register(Node atdfNode, String group) {
           name_ = Utils.getNodeAttribute(atdfNode, "name", "");
           offset_ = Utils.getNodeAttributeAsLong(atdfNode, "offset", 0);
           
           int rw_temp = 0;
           String readwrite = Utils.getNodeAttribute(atdfNode, "rw", "R");
           for(char c : readwrite.toCharArray()) {
               if('r' == c  ||  'R' == c) {
                   rw_temp |= REG_READ;
               } else if('w' == c  ||  'W' == c) {
                   rw_temp |= REG_WRITE;
               }
           }
           rw_ = rw_temp;

           size_ = Utils.getNodeAttributeAsInt(atdfNode, "size", 1);
           count_ = Utils.getNodeAttributeAsInt(atdfNode, "count", 1);
           init_ = Utils.getNodeAttributeAsLong(atdfNode, "initval", 0);
           caption_ = Utils.getNodeAttribute(atdfNode, "caption", "");
           group_ = group;
        }

        Register(Node atdfNode) {
            this(atdfNode, "");
        }
        
        /* Get the regiser name. */
        public String getName()        { return name_; }

        /* Get number of bytes from which this register is offset from the base peripheral address. */
        public long getBaseOffset()    { return offset_; }

        /* Return a value indicating the read/write access of this register. */
        public int getRw()             { return rw_; }

        /* Get the size of the register in bytes. */
        public int getSize()           { return size_; }

        /* Get the number of registers that match this definition. */
        public int getCount()          { return count_; }

        /* Get the initial value of the register. */
        public long getInitValue()     { return init_; }

        /* Get the regiser caption, which would be a C comment describing the register. */
        public String getCaption()     { return caption_; }

        /* Return the register group this is a member of or an empty string if it is a member of the
         * default group.  This is used, for example, by the DMA controller to split up the main
         * from the channel-specific registers.  For most registers, this will be empty becaus there
         * is only one group and it is the same as the name of the owning peripheral.
         */
        public String getGroup()       { return group_; }
    }


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
     * could not be found.  The name of the peripheral would be as it appears in .PIC or .ATDF 
     * files.  Peripherals with only one instance usually do not have a number ("USB" or "GMAC") and
     * ones with multiple instances do ("ADC0" or "SERCOM4").
     */
    public long getPeripheralInstanceBaseAddress(String instance) {
        Node instanceNode = getPeripheralInstanceNode(instance);
        Node regGroupNode = Utils.filterFirstChildNode(instanceNode, "register-group", null, null);

        return Utils.getNodeAttributeAsLong(regGroupNode, "offset", 0);
    }
    
    /* Get a unique ID string for the given peripheral.  Two peripherals with the same name will have
     * different IDs if they have different implementations.  For example, the ADC on the SAME54 has
     * a different ID from the ADC on the SAMA5D27.  This will return null if the ID for the given
     * peripheral could not be found.  This needs only the basename of a peripheral, so "ADC0", 
     * "ADC1", or just "ADC" will work and return the same ID.
     */
    public String getPeripheralModuleId(String peripheral) {
        Node moduleNode = getPeripheralModuleNode(peripheral);
        String id = null;

        if(null != moduleNode) {
            id = Utils.getNodeAttribute(moduleNode, "id", null);
        }

        return id;
    }

    /* Get a string representing the version of the module.  Versions appear to come in two flavors:
     * either a three-place decimal value (eg. "1.0.2") or a series of letters (eg. "B" or "ZJ").
     * This will return null if the version for the given peripheral could not be found.  This needs
     * only the basename of a peripheral, so "ADC0", "ADC1", or just "ADC" will work and return the 
     * same version.
     */
    public String getPeripheralModuleVersion(String peripheral) {
        Node moduleNode = getPeripheralModuleNode(peripheral);
        String version = null;

        if(null != moduleNode) {
            version = Utils.getNodeAttribute(moduleNode, "version", null);
        }

        return version;
    }

    /* Get a Register object for the given named register as part of the named peripheral.  The 
     * object will contain some information about the register, such as its size and its read/write
     * access.  The 'peripheral' input can be the basename of the peripheral instead of a specific 
     * instance.  This will return null if the register could not be found.  If that happens, then
     * try calling this again using the basename of the register (ie. without any trailing numbers).
     */
    public Register getPeripheralRegister(String peripheral, String register) {
        String peripheralBase = Utils.getInstanceBasename(peripheral);
        Node moduleNode = Utils.filterFirstChildNode(modulesNode_, "module", "name", peripheralBase);
        Node regGroupNode = Utils.filterFirstChildNode(moduleNode, "register-group", "name", peripheralBase);
        Node registerNode = Utils.filterFirstChildNode(regGroupNode, "register", "name", register);

        if(null != registerNode) {
            return new Register(registerNode);
        } else {
            return null;
        }
    }

    /* Like above, but allows you to search in a particular register group instead of assuming the 
     * default.  This is rarely used and so should be a fallback if the above cannot find the register.
     * Groups appear to be used to organize registers into submodules.  For example, the DMA controller
     * uses groups to separate channel-specific registers from the main DMA registers.
    */
    public Register getPeripheralRegisterInGroup(String peripheral, String group, String register) {
        String peripheralBase = Utils.getInstanceBasename(peripheral);
        Node moduleNode = Utils.filterFirstChildNode(modulesNode_, "module", "name", peripheralBase);
        Node regGroupNode = Utils.filterFirstChildNode(moduleNode, "register-group", "name", group);
        Node registerNode = Utils.filterFirstChildNode(regGroupNode, "register", "name", register);

        if(null != registerNode) {
            return new Register(registerNode, group);
        } else {
            return null;
        }        
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
