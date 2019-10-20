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
 * This represents a peripheral on an Atmel device as read from its corresponding ATDF file.  This
 * will encapsulate the peripheral's registers and information about it, such as the number of
 * instances and its unique ID.
 * 
 * Each peripheral is made of one or more instances, such as SPI0 and SPI1 being two instances of
 * the SPI peripheral.  This object will contain per-instance info about the peripherals.  Each
 * peripheral also has a unique ID that differentiates it from other peripherals.  For example, the
 * ADC peripheral on the SAMA5 has a different ID than the ADC on the SAME54 because they have
 * different implementations.
 */
public class AtdfPeripheral {
    /* We need two Nodes to describe a peripheral for a given device:  one to describe its instances
     * as used on this particular device and the other to describe the peripheral's registers.
     */
    private final Node moduleInstancesNode_;
    private final Node moduleRegistersNode_;

    /* Create a new AtdfPeripheral for the peripheral of the given name by searching the ATDF 
     * document represented by the given Node for the needed info.  The Node must be the root 
     * element of an ATDF document.  This is handled in the AtdfDoc class, so use methods in
     * there to get an AtdfPeripheral object instead of calling this directly.
     */
    public AtdfPeripheral(Node atdfRootNode, String periphName) throws SAXException {
        Node devicesNode = Utils.filterFirstChildNode(atdfRootNode, "devices", null, null);
        Node deviceNode = Utils.filterFirstChildNode(devicesNode, "device", null, null);
        Node peripheralsNode = Utils.filterFirstChildNode(deviceNode, "peripherals", null, null);        
        Node modulesNode = Utils.filterFirstChildNode(atdfRootNode, "modules", null, null);        

        moduleInstancesNode_ = Utils.filterFirstChildNode(peripheralsNode, "module", "name", periphName);
        moduleRegistersNode_ = Utils.filterFirstChildNode(modulesNode, "module", "name", periphName);

        if(null == moduleInstancesNode_  ||  null == moduleRegistersNode_) {
            throw new SAXException("Module XML nodes not found when creating an AtdfPeripheral for " +
                                   "peripheral " + periphName +
                                   ".  The given Node needs to be the root of an AtdfDoc.");
        }
    }

    /* This is a private constructor used by the getAllPeripherals() method to directly set the 
     * object's members without any checking.
     */
    private AtdfPeripheral(Node instancesNode, Node registersNode) {
        moduleInstancesNode_ = instancesNode;
        moduleRegistersNode_ = registersNode;
    }


    /* Get the name of the peripheral to which this object applies.
     */
    public String getName() {
        return Utils.getNodeAttribute(moduleInstancesNode_, "name", "");
    }


    /* Search through the ATDF document representd by the given Node for all of the peripherals on
     * the device represented by the document and return a list of them.  This will return an empty
     * list if the Node does not represent a valid ATDF file.  The Node must be the root node of the
     * file.  This is handled in the AtdfDoc class, so use methods in there to get peripherals rather
     * than calling this directly.
     */
    public static List<AtdfPeripheral> getAllPeripherals(Node atdfRootNode) {
        ArrayList<AtdfPeripheral> peripherals = new ArrayList<>(20);

        Node devicesNode = Utils.filterFirstChildNode(atdfRootNode, "devices", null, null);
        Node deviceNode = Utils.filterFirstChildNode(devicesNode, "device", null, null);
        Node peripheralsNode = Utils.filterFirstChildNode(deviceNode, "peripherals", null, null);        
        Node modulesNode = Utils.filterFirstChildNode(atdfRootNode, "modules", null, null);        

        List<Node> instanceNodes = Utils.filterAllChildNodes(peripheralsNode, "module", "name", null);
        for(Node instNode : instanceNodes) {
            String periphName = Utils.getNodeAttribute(instNode, "name", "");
            Node regNode = Utils.filterFirstChildNode(modulesNode, "module", "name", periphName);

            if(null != regNode) {
                peripherals.add(new AtdfPeripheral(instNode, regNode));
            }
        }

        return peripherals;
    }
}