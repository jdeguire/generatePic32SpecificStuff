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
import org.w3c.dom.NodeList;

/**
 * Peripheral registers are organized into groups to allow for a "master group" and "sub-groups".
 * Most peripherals have only one group, but a few have multiple groups.  For example, the DMA
 * Controller peripheral on some devices has a master group that contains registers to control the
 * whole peripheral, but also has subgroups to control the DMA channels individually.
 */
public class AtdfRegisterGroup {
    private final Node moduleNode_;
    private final Node groupNode_;
    private final ArrayList<AtdfRegister> members_ = new ArrayList<>(10);

    /* Create a new AtdfRegisterGroup based on the given nodes from an ATDF document.  The 
     * 'moduleNode' is a Node that refers to the "module" XML node that contains the desired group
     * indicated by 'groupNode'.  This is handled in the AtdfPeripheral class, so use methods in
     * there to get an AtdfRegisterGroup object for that peripheral instead of calling this directly.
     */
    public AtdfRegisterGroup(Node moduleNode, Node groupNode) {
        moduleNode_ = moduleNode;
        groupNode_ = groupNode;
    }

    /* Get the name of this group.
     */
    public String getName() {
        return Utils.getNodeAttribute(groupNode_, "name", "");
    }

    /* Get the name of the peripheral that owns this group.
     */
    public String getOwningPeripheralName() {
        return Utils.getNodeAttribute(moduleNode_, "name", "");        
    }

    /* Get descriptive text for the peripheral.
     */
    public String getCaption() {
        return Utils.getNodeAttribute(groupNode_, "name", "");
    }

    /* Get the alignment of this group, which indicates the byte boundary upon which the group must
     * start.  Returns 0 if no special alignment is required.
     */
    public int getAlignment() {
        int alignment = Utils.getNodeAttributeAsInt(groupNode_, "aligned", 0);

        if(alignment < 0)
            alignment = 0;

        return alignment;
    }

    /* Get the size in bytes of this group, which may indicate that padding bytes are needed at the 
     * end of the group if this value is larger than the combined sizes of the registers in the 
     * group.  Returns 0 if no special size or padding is required.
     */
    public int getSizeInBytes() {
        int size = Utils.getNodeAttributeAsInt(groupNode_, "size", 0);

        if(size < 0)
            size = 0;

        return size;
    }

    /* Get a list of all of the members of this group.  Use AtdfRegister::isGroupAlias() to check
     * if the member is a normal register (false) or an alias for a subgroup of this group (true).
     */
    public List<AtdfRegister> getAllMembers() {
        if(members_.isEmpty()) {
            NodeList children = groupNode_.getChildNodes();

            for(int i = 0; i < children.getLength(); ++i) {
                Node child = children.item(i);
                String name = child.getNodeName();

                if(name.equals("register")  ||  name.equals("register-group")) {
                    members_.add(new AtdfRegister(moduleNode_, child));
                }
            }
        }

        return members_;
    }

    /* Get a single member of the group by name or null if a member by that name canot be found.
     */
    public AtdfRegister getMember(String name) {
        for(AtdfRegister reg : getAllMembers()) {
            if(name.equals(reg.getName()))
                return reg;
        }

        return null;
    }
}
