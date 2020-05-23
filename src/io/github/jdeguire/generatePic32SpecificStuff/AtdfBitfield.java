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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Represents a single bitfield within a register.  This encapsulates the "bitfield" XML nodes found
 * in ATDF documents.
 */
public class AtdfBitfield {
    private final Node moduleNode_;
    private final Node regNode_;
    private final Node bfNode_;
    private final ArrayList<AtdfValue> values_ = new ArrayList<>(8);


    /* Create a new AtdfBitfield based on the given nodes from an ATDF document.  The 'moduleNode' 
     * is a Node that refers to the "module" XML node that contains the owning register indicated
     * by 'regNode', which in turn owns the Node representing the bitfield in 'bfNode'.  This is 
     * handled in the AtdfRegister class, so use methods in there to get an AtdfBitfield object for 
     * that register instead of calling this directly.
     */
    public AtdfBitfield(Node moduleNode, Node regNode, Node bfNode) {
        moduleNode_ = moduleNode;
        regNode_ = regNode;
        bfNode_ = bfNode;
    }


    /* Get the bitfield name.
     */
    public String getName() {
        return Utils.getNodeAttribute(bfNode_, "name", "");  
    }

    /* Get the name of the register that owns this bitfield.
     */
    public String getOwningRegisterName() {
        return Utils.getNodeAttribute(regNode_, "name", "");        
    }

    /* Get a list of modes to which this bitfield applies.  Some registers can operate differently
     * under different circumstances and that may affect the bitfield available in the register.
     * The default mode name is "DEFAULT", so any bitfield that does not list any modes will have
     * that mode returned.
     */
    public List<String> getModes() {
        List<String> modes = new ArrayList<>(3);
        String modesString = Utils.getNodeAttribute(bfNode_, "modes", "");

        if(!modesString.isEmpty()) {
            Collections.addAll(modes, modesString.split(" "));
        } else {
            // Ensure we at least have the default mode if no others are present.
            modes.add("DEFAULT");
        }

        return modes;
    }

    /* Get descriptive text for this bitfield.
     */
    public String getCaption() {
        return Utils.getNodeAttribute(bfNode_, "caption", "");
    }

    /* Return a mask to indicate which bits in the owning register are for this field.
     */
    public long getMask() {
        return Utils.getNodeAttributeAsLong(bfNode_, "mask", 0xFFFFFFFF);
    }

    /* Return the number of the most-significant bit that is set in the mask or 64 if the mask is 0.
     */
    public int getMsb() {
        long mask = getMask();
        
        if(0 == mask)
            return 64;
        else
            return 63 - Long.numberOfLeadingZeros(getMask());
    }

    /* Return the number of the least-significant bit that is set in the mask or 64 if the mask is 0.
     */
    public int getLsb() {
        return Long.numberOfTrailingZeros(getMask());
    }

    /* Return the width in bits of this field as indicated by the mask.
     */
    public int getBitWidth() {
        return Long.bitCount(getMask());
    }


    /* Get a list that indicates the meaning of values that this bitfield can contain.  Returns an
     * empty list if this field does not have any special values.  This will throw an SAXException
     * if this field should have values, but they cannot be found in the ATDF file.  This may indicate
     * that the incorrect module node was given to the constructor of this class.
     */
    public List<AtdfValue> getFieldValues() throws SAXException {
        String valueName = Utils.getNodeAttribute(bfNode_, "values", "");

        // If the name is empty, then this field does not have any special values.
        if(!valueName.isEmpty()  &&  values_.isEmpty()) {
            Node valGroupNode = Utils.filterFirstChildNode(moduleNode_, "value-group", "name", valueName);

            // We're here because our bitfield claims to have special values, so we'll bail if we
            // can't find them.
            if(null == valGroupNode) {
                String moduleName = Utils.getNodeAttribute(moduleNode_, "name", "<unknown>");
                throw new SAXException("Could not find \"value\" XML node for bitfield " + 
                                        getName() + " expecting value set " + valueName + ".  " +
                                        "This looked under the module node for " + moduleName + ".");
            } else {
                List<Node> valueList = Utils.filterAllChildNodes(valGroupNode, "value", null, null);

                for(Node valueNode : valueList) {
                    values_.add(new AtdfValue(valueNode));
                }
            }
        }

        return values_;
    }

    /* Return True if this bitfield equals another--that is, if both have the same name and mask.
     */
    public boolean equals(AtdfBitfield other) {
        return (getName().equals(other.getName())  &&  getMask() == other.getMask());
    }

    @Override
    public boolean equals(Object other) {
        if(other instanceof AtdfBitfield) {
            return equals((AtdfBitfield)other);
        } else {
            return false;
        }
    }

    /* Netbeans suggested I add this and generated it for me when I made equals(Object). */
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.moduleNode_);
        hash = 89 * hash + Objects.hashCode(this.regNode_);
        hash = 89 * hash + Objects.hashCode(this.bfNode_);
        return hash;
    }
}