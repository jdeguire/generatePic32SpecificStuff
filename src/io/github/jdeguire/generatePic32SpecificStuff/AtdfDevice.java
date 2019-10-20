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

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * This represents an Atmel device as read from its corresponding ATDF file.  This is really just
 * a convenient abstraction around the "device" XML node in the file.
 */
public class AtdfDevice {

    /**
     * This encapsulates the info from "parameter" and "property" XML nodes, which contain macro 
     * names and values that pertain to the device itself or its peripheral instances.
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
     * This encapsulates the info from the "memory-segment" XML nodes, which contain info about how
     * the address spaces on the device are used.
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

        /* Get the page size of the segment in bytes.  This applies only to flash segments and will 
         * be 0 for other types of memory. */
        public long getPageSize()      { return pageSize_; }
    }


    private final Node deviceNode_;
    private final String name_;
    
    /* Create a new AtdfDevice uing the given Node.  The Node must be the root element of
     * an ATDF document.  This is handled in the AtdfDoc class, so use methods in there to get an
     * AtdfDevice object instead of calling this directly.
     */
    public AtdfDevice(Node atdfRootNode) throws SAXException {
        Node devicesNode = Utils.filterFirstChildNode(atdfRootNode, "devices", null, null);
        deviceNode_ = Utils.filterFirstChildNode(devicesNode, "device", null, null);
        name_ = Utils.getNodeAttribute(deviceNode_, "name", "");

        if(null == deviceNode_) {
            throw new SAXException("Node \"device\" XML node not found when creating an AtdfDevice." +
                                   "  The given Node needs to be the root of an AtdfDoc.");
        }
    }


    /* Get the name of the device associated with this device object, such as "ATSAME54P20A".
     */
    public String getDeviceName() {
        return name_;
    }

    /* Get the name of the CPU in lower-case, such as "cortex-m4".  This should return the same name
     * as calling TargetDevice.getCpuName() for this device.
     */
    public String getCpuName() {
        return Utils.getNodeAttribute(deviceNode_, "architecture", "");
    }

    /* Get a list of parameters for the device, which generally includes things like CPU
     * revision and whether or not it has an FPU.
     */
    public List<Parameter> getBasicParameters() {
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
    public List<Parameter> getSignatureParameters() {
        ArrayList<Parameter> props = new ArrayList<>(8);

        Node propGroupsNode = Utils.filterFirstChildNode(deviceNode_, "property-groups", null, null);
        Node signaturesGroupNode = Utils.filterFirstChildNode(propGroupsNode, "property-group", 
                                                              "name", "SIGNATURES");

        if(null != signaturesGroupNode) {
            List<Node> propsList = Utils.filterAllChildNodes(signaturesGroupNode, "property", null, null);

            for(Node propNode : propsList) {
                props.add(new Parameter(propNode));
            }
        }        

        return props;
    }

    /* Get a list of electrical characteristic values for the device, such as clock speeds and flash
     * wait states.
     */
    public List<Parameter> getElectricalParameters() {
        ArrayList<Parameter> props = new ArrayList<>(8);

        Node propGroupsNode = Utils.filterFirstChildNode(deviceNode_, "property-groups", null, null);
        Node electricalGroupNode = Utils.filterFirstChildNode(propGroupsNode, "property-group", 
                                                              "name", "ELECTRICAL_CHARACTERISTICS");

        if(null != electricalGroupNode) {
            List<Node> propsList = Utils.filterAllChildNodes(electricalGroupNode, "property", null, null);

            for(Node propNode : propsList) {
                props.add(new Parameter(propNode));
            }
        }        

        return props;
    }

    /* Get a list of the memory segments on the device.
     */
    public List<MemSegment> getMemorySegments() {
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
}
