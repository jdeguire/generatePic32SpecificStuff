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
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
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

import com.microchip.mplab.crownkingx.xPIC;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is a list of interrupts for a target device.  This will also contain info about the device's
 * interrupt controller, such as the number of shadow registers and whether the device uses a
 * variable offset vector table.
 *
 * This info is available from a xPIC object, but requires manual parsing of XML nodes.  This class
 * is a more convenient wrapper for that information.
 */
public class InterruptList {

    /**
     * This is a single interrupt in the list.
     */
    public class Interrupt {

        final private String name_;
        final private String description_;
        final private String owningPeripheral_;
        final private int irq_;

        /* Create a new Interrupt object by parsing the attributes of the given Node.  It is up to
         * the caller to ensure that the Node actually represents an interrupt.  So far, valid Nodes
         * are named "edc:Interrupt" and "edc:InterruptRequest".
         */
        Interrupt(Node node) {
            name_ = Utils.getNodeAttribute(node, "edc:cname", "");
            description_ = Utils.getNodeAttribute(node, "edc:desc", "");
            owningPeripheral_ = Utils.getNodeAttribute(node, "ltx:memberofperipheral", "");
            irq_ = Utils.getNodeAttributeAsInt(node, "edc:irq", -1);
        }

        /* Get the name of the interrupt as it would be used in code. */
        public String getName()              { return name_; }

        /* Get a description of the interrupt as would be used in a simple comment. */
        public String getDescription()       { return description_; }

        /* Get the name of the peripheral that uses this interrupt.  This is not used by MIPS
         * devices and so will return an empty string. */
        public String getOwningPeripheral()  { return owningPeripheral_; }

        /* Get the IRQ or vector number for this interrupt, depending on if this was created from an
         * "edc:Interrupt" (vector) or "edc:InterruptRequest" (IRQ) node. */
        public int getIntNumber()            { return irq_; }
    }


    final private ArrayList<Interrupt> vectors_;
    final private ArrayList<Interrupt> requests_;
    private long defaultBaseAddr_ = 0;
    private int shadowRegs_ = 1;
    private boolean usesVariableOffsets_ = false;
    private int lastVector_ = 0;

    public InterruptList(xPIC pic) {
        vectors_ = new ArrayList<>(32);
        requests_ = new ArrayList<>(32);

        Node intListNode = pic.first("InterruptList");     // The "edc:" prefix is not needed here.

        // Look for attributes pertaining to the list node itself.
        //
        defaultBaseAddr_ = Utils.getNodeAttributeAsLong(intListNode, "ltx:defaultbaseaddr", 0) & 0xFFFFFFFF;
        shadowRegs_ = Utils.getNodeAttributeAsInt(intListNode, "edc:shadowsetcount", 1);
        usesVariableOffsets_ = Utils.getNodeAttributeAsBool(intListNode, "edc:hasvariableoffsets", false);

        // Now look at the child nodes, which will be the interrupt vectors ("edc:Interrupt") and 
        // interrupt requests ("edc:InterruptRequest").  The latter is present only on PIC32MX 
        // because some requests share vectors and so the vector number and IRQ number may be 
        // different.
        //
        NodeList intNodes = intListNode.getChildNodes();
        for(int i = 0; i < intNodes.getLength(); ++i) {
            Node currentNode = intNodes.item(i);

            if(currentNode.getNodeName().equals("edc:Interrupt")) {
                Interrupt iv = new Interrupt(currentNode);

                if(iv.getIntNumber() > lastVector_)
                    lastVector_ = iv.getIntNumber();

                vectors_.add(iv);
            } else if(currentNode.getNodeName().equals("edc:InterruptRequest")) {
                requests_.add(new Interrupt(currentNode));            
            }
        }
    }
    
    public List<Interrupt> getInterruptVectors() {
        return vectors_;
    }

    public boolean hasSeparateInterruptRequests() {
        return !requests_.isEmpty();
    }

    public List<Interrupt> getInterruptRequests() {
        return requests_;
    }

    public long getDefaultBaseAddress() {
        return defaultBaseAddr_;
    }

    public int getNumShadowRegs() {
        return shadowRegs_;
    }

    public boolean usesVariableOffsets() {
        return usesVariableOffsets_;
    }

    public int getLastVectorNumber() {
        return lastVector_;
    }
}
