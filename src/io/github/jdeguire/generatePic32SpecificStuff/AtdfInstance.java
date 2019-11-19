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
 * This represents a peripheral instance as shown in "instance" XML nodes in an ATDF document.
 *
 * An AtdfPerpiheral represents one or more instances of a device peripheral.  For example, a device
 * with UART0, UART1, and UART2 would have an AtdfPeriphral object for the UART peripheral with 3
 * instances.  This contains info such as the base address of the instance and any instance-specific
 * parameter values.
 */
public class AtdfInstance {
    private final String name_;
    private final Node regGroupNode_;
    private final Node signalsNode_;
    private final Node parametersNode_;
    private final ArrayList<AtdfSignal> signals_ = new ArrayList<>(20);
    private final ArrayList<AtdfValue> parameters_ = new ArrayList<>(20);
    private int instanceId_ = -1;

    /* Create a new AtdfInstance based on the given node from an ATDF document.  The Node needs to
     * be for an "instance" XML node.  This is handled in the AtdfPeripheral class, so use methods
     * in there to get an AtdfInstance object for that peripheral instead of calling this directly.
     */
    public AtdfInstance(Node atdfInstanceNode) throws SAXException {
        name_ = Utils.getNodeAttribute(atdfInstanceNode, "name", "");
        regGroupNode_ = Utils.filterFirstChildNode(atdfInstanceNode, "register-group", "name", name_);
        signalsNode_ = Utils.filterFirstChildNode(atdfInstanceNode, "signals", null, null);
        parametersNode_ = Utils.filterFirstChildNode(atdfInstanceNode, "parameters", null, null);

        /* Not all peripherals have signals or parameters, so we can't check for those.  We look for
         * the "base" or "root" register group node that is the same name as the peripheral because
         * that gives us the true base address of the instance.  However, there may be peripherals
         * that do not have a useful single base address (ex: FUSES) or that do not have a public 
         * interface (ex: PUKCC), so we'll throw on that and let the caller decide what to do.
         */
        if(null == regGroupNode_) {
            throw new SAXException("Register node not found when creating an AtdfInstance.  " +
                                   "Either the given node is not a valid ATDF instance or this " +
                                   "is an odd peripheral instance (ex: FUSES or PUKCC) that does " +
                                   "not have a useful register node.");
        }
    }


    /* Return the name of this instance.  If a peripheral has only once instance, then this name
     * will usually match the name of the peripheral.  Otherwise, it will usually be the peripheral
     * name followed by a decimal number or letter.
     */
    public String getName() {
        return name_;
    }

    /* Get the base address of this peripheral instance, which is where in memory its peripheral
     * registers are located.
     */
    public long getBaseAddress() {
        return Utils.getNodeAttributeAsLong(regGroupNode_, "offset", 0);
    }

    /* Get a device-specific ID for this instance or a value less than zero if one could not be found.
     */
    public int getInstanceId() {
        if(instanceId_ < 0) {
            Node instanceNode = Utils.filterFirstChildNode(parametersNode_, "param", "name", "INSTANCE_ID");
            if(null != instanceNode) {
                instanceId_ = Utils.getNodeAttributeAsInt(instanceNode, "value", -1);
            }
        }

        return instanceId_;
    }

    /* Get a list of parameter values for this peripheral instance that give peripheral-specific 
     * info.  This returns an empty list if this instance has no parameters.
     */
    public List<AtdfValue> getParameterValues() {
        if(null != parametersNode_  &&  parameters_.isEmpty()) {
            List<Node> paramsList = Utils.filterAllChildNodes(parametersNode_, "param", null, null);

            for(Node paramNode : paramsList) {
                // This instance ID is treated differently in Atmel headers, so we'll handle that here.
                if(Utils.getNodeAttribute(paramNode, "name", "").equals("INSTANCE_ID")) {
                    instanceId_ = Utils.getNodeAttributeAsInt(paramNode, "value", -1);
                } else {
                    parameters_.add(new AtdfValue(paramNode)); 
                }
            }
        }

        return parameters_;
    }

    /* Get a list of signals for this peripheral instance.  Signals provide info about the pins on 
     * the device this instance can use.  This returns an empty string if this instance has no
     * external signals.
     */
    public List<AtdfSignal> getSignals() {
        if(null != signalsNode_  &&  signals_.isEmpty()) {
            List<Node> sigList = Utils.filterAllChildNodes(signalsNode_, "signal", null, null);

            for(Node sigNode : sigList) {
                signals_.add(new AtdfSignal(sigNode));
            }
        }

        return signals_;
    }
}
