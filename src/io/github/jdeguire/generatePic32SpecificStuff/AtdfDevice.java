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

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * This represents an Atmel device as read from its corresponding ATDF file.  This is really just
 * a convenient abstraction around the "device" XML node in the file.
 */
public class AtdfDevice {
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
        return Utils.getNodeAttribute(deviceNode_, "architecture", "").toLowerCase();
    }

    /* Get a list of parameters for the device, which generally includes things like CPU
     * revision and whether or not it has an FPU.
     */
    public List<AtdfValue> getBasicParameterValues() {
        ArrayList<AtdfValue> params = new ArrayList<>(16);

        Node parametersNode = Utils.filterFirstChildNode(deviceNode_, "parameters", null, null);

        if(null != parametersNode) {
            List<Node> paramsList = Utils.filterAllChildNodes(parametersNode, "param", null, null);

            for(Node paramNode : paramsList) {
                params.add(new AtdfValue(paramNode));
            }
        }        

        return params;
    }

    /* Get a list of signature/device ID values for the device.
     */
    public List<AtdfValue> getSignatureParameterValues() {
        ArrayList<AtdfValue> props = new ArrayList<>(8);

        Node propGroupsNode = Utils.filterFirstChildNode(deviceNode_, "property-groups", null, null);
        Node signaturesGroupNode = Utils.filterFirstChildNode(propGroupsNode, "property-group", 
                                                              "name", "SIGNATURES");

        if(null != signaturesGroupNode) {
            List<Node> propsList = Utils.filterAllChildNodes(signaturesGroupNode, "property", null, null);

            for(Node propNode : propsList) {
                props.add(new AtdfValue(propNode));
            }
        }        

        return props;
    }

    /* Get a list of electrical characteristic values for the device, such as clock speeds and flash
     * wait states.
     */
    public List<AtdfValue> getElectricalParameterValues() {
        ArrayList<AtdfValue> props = new ArrayList<>(8);

        Node propGroupsNode = Utils.filterFirstChildNode(deviceNode_, "property-groups", null, null);
        Node electricalGroupNode = Utils.filterFirstChildNode(propGroupsNode, "property-group", 
                                                              "name", "ELECTRICAL_CHARACTERISTICS");

        if(null != electricalGroupNode) {
            List<Node> propsList = Utils.filterAllChildNodes(electricalGroupNode, "property", null, null);

            for(Node propNode : propsList) {
                props.add(new AtdfValue(propNode));
            }
        }

        return props;
    }

    /* Get a list of the memory segments on the device.
     */
    public List<AtdfMemSegment> getMemorySegments() {
        ArrayList<AtdfMemSegment> memories = new ArrayList<>(16);

        Node addrSpacesNode = Utils.filterFirstChildNode(deviceNode_, "address-spaces", null, null);
        Node baseSpaceNode = Utils.filterFirstChildNode(addrSpacesNode, "address-space", "id", "base");

        if(null != baseSpaceNode) {
            List<Node> memSegmentList = Utils.filterAllChildNodes(baseSpaceNode, "memory-segment", 
                                                                  null, null);
            
            for(Node memSegmentNode : memSegmentList) {
                memories.add(new AtdfMemSegment(memSegmentNode));
            }
        }

        return memories;
    }

    /* Get a list of event generators or an empty list of this device does not support events.
     */
    public List<AtdfEvent> getEventGenerators() {
        ArrayList<AtdfEvent> events = new ArrayList<>(16);

        Node eventsNode = Utils.filterFirstChildNode(deviceNode_, "events", null, null);
        Node generatorsNode = Utils.filterFirstChildNode(eventsNode, "generators", null, null);

        if(null != generatorsNode) {
            List<Node> eventList = Utils.filterAllChildNodes(generatorsNode, "generator", null, null);
            
            for(Node event : eventList) {
                events.add(new AtdfEvent(event));
            }
        }

        return events;
    }

    /* Get a list of event users or an empty list of this device does not support events.
     */
    public List<AtdfEvent> getEventUsers() {
        ArrayList<AtdfEvent> events = new ArrayList<>(16);

        Node eventsNode = Utils.filterFirstChildNode(deviceNode_, "events", null, null);
        Node usersNode = Utils.filterFirstChildNode(eventsNode, "users", null, null);

        if(null != usersNode) {
            List<Node> eventList = Utils.filterAllChildNodes(usersNode, "user", null, null);
            
            for(Node event : eventList) {
                events.add(new AtdfEvent(event));
            }
        }

        return events;
    }
}

