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


/**
 * This represents a single register or alias from a register group in an ATDF document.
 * 
 * Register groups can contain either actual registers or placeholders to indicate that the group
 * contains a subgroup.  This class refers to such a placeholder as a "group alias".  This class 
 * handles both because a subgroup is presented similarly to a register that has no fields.
 */
public class AtdfRegister {
    public static final int REG_READ  = 0x01;
    public static final int REG_WRITE = 0x02;
    public static final int REG_RW    = 0x03;

    private final Node moduleNode_;
    private final Node regNode_;
    private final ArrayList<AtdfBitfield> bitfields_ = new ArrayList<>(32);


    /* Create a new AtdfRegister based on the given nodes from an ATDF document.  The 'moduleNode' 
     * is a Node that refers to the "module" XML node that contains the desired register indicated
     * by 'regNode'.  This is handled in the AtdfRegisterGroup class, so use methods in there to get
     * an AtdfRegister object for that peripheral instead of calling this directly.
     */
    public AtdfRegister(Node moduleNode, Node regNode) {
        moduleNode_ = moduleNode;
        regNode_ = regNode;
    }


    /* Get the register name as it appears in the ATDF document.
     */
     public String getName() {
         return Utils.getNodeAttribute(regNode_, "name", "");
     }

    /* Get the regiser name formatted for use as a C variable.  Some names in the ATDF document will
     * have the owning peripheral at the start like "OWNER_REGISTER".  This function will remove 
     * that prefix and return either "Register" (first letter only capitalized) if this is a group 
     * alias or "REGISTER" (all caps) otherwise.
     */
    public String getCName() {
        String name = getName();
        String owner = getOwningPeripheralName();

        if(name.startsWith(owner)) {
            name = name.substring(owner.length());

            // In case the name in the ATDF doc was something like "OWNER_REGISTER".
            if(name.startsWith("_")) {
                name = name.substring(1);
            }
        }

        if(isGroupAlias()  &&  name.length() > 1) {
            return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        } else {
            return name.toUpperCase();
        }
    }

    /* Return a string to be used as the typename of a C struct representing this register.
     * If this is a group alias, the name will be returned as "OwnerGroup" normally or as "Group" 
     * if the group and owner names are the same.
     * If this is not a group alias, then the name returns will be "OWNER_REGISTER_Type".
     */
    public String getTypeName() {
        String name = getCName();
        String owner = getOwningPeripheralName();

        if(isGroupAlias()) {
            owner = owner.substring(0, 1).toUpperCase() + owner.substring(1).toLowerCase();
            return owner + name;
        } else {
            return owner.toUpperCase() + "_" + name + "_Type";
        }
    }

    /* Get the name of the peripheral that owns this register.
     */
    public String getOwningPeripheralName() {
        return Utils.getNodeAttribute(moduleNode_, "name", "");        
    }

    /* Get descriptive text for this register.
     */
    public String getCaption() {
        return Utils.getNodeAttribute(regNode_, "caption", "");
    }

    /* Get number of bytes from which this register is offset from the base peripheral instance
     * address.
     */
    public long getBaseOffset() {
        return Utils.getNodeAttributeAsLong(regNode_, "offset", 0);
    }

    /* Return a string indicating the read/write access of this register.  This will return either
     * "R", "W", or "RW".
     */
    public String getRwAsString() {
        return Utils.getNodeAttribute(regNode_, "rw", "R");
    }
    
    /* Return a value indicating the read/write access of this register.  Use the flags at the top
     * of this file to check the access.
     */
    public int getRwAsInt() {
        int rw = 0;
        char[] rwChars = getRwAsString().toCharArray();

        for(char c : rwChars) {
           if('r' == c  ||  'R' == c) {
               rw |= REG_READ;
           } else if('w' == c  ||  'W' == c) {
               rw |= REG_WRITE;
           }
       }

        return rw;
    }

    /* Get the size of the register in bytes.  If this is a group alias, then the size can be larger
     * than the total size of the registers in the group to indicate that it requires extra pad 
     * bytes at the end.  If no extra padding is required for an alias, then this will return 0.
     */
    public int getSizeInBytes() {
        int defaultSize = 1;

        if(isGroupAlias())
            defaultSize = 0;

        return Utils.getNodeAttributeAsInt(regNode_, "size", defaultSize);
    }

    /* Get the number of registers that match this definition.  If this is greater than 1, then this
     * register represents an array of registers of the same format.
     */
    public int getNumRegisters() {
        return Utils.getNodeAttributeAsInt(regNode_, "count", 1);
    }

    /* Get the initial value of the register on device reset or power-up.
     */
    public long getInitValue() {
        return Utils.getNodeAttributeAsInt(regNode_, "initval", 0);
    }

    /* Return a mask in which bits set to 1 are actually used by the register.
     */
    public long getMask() {
        long mask = 0;
        
        for(AtdfBitfield bf : getAllBitfields()) {
            mask |= bf.getMask();
        }

        return mask;
    }

    /* Return True if this object actually represents a register group instead of just a single 
     * register.  The two are presented similarly and so there is not much difference between the 
     * two.  A group will of course have no bitfields on its own.
     */
    public boolean isGroupAlias() {
        return regNode_.getNodeName().equals("register-group");
    }


    /* Get a list of all of the instances for this peripheral.
     */
    public List<AtdfBitfield> getAllBitfields() {
        if(bitfields_.isEmpty()) {
            List<Node> bfList = Utils.filterAllChildNodes(regNode_, "bitfield", null, null);

            for(Node bfNode : bfList) {
                bitfields_.add(new AtdfBitfield(moduleNode_, regNode_, bfNode));
            }
        }

        return bitfields_;
    }

    /* Get a single bitfield by name or null if a bitfield by that name canot be found.
     */
    public AtdfBitfield getBitfield(String name) {
        for(AtdfBitfield bf : getAllBitfields()) {
            if(name.equals(bf.getName()))
                return bf;
        }

        return null;
    }
}
