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

package com.github.jdeguire.generatePic32SpecificStuff;

import com.microchip.crownking.mplabinfo.FamilyDefinitions.SubFamily;
import com.microchip.mplab.crownkingx.xMemoryPartition;
import java.io.PrintWriter;
import java.util.List;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * A subclass of the LinkerScriptBuilder that handles MIPS devices.
 */
public class MipsLinkerScriptBuilder extends LinkerScriptBuilder {

    // This is the physical address at which the CPU begin execution when it is reset.
    private static final long MIPS_RESET_PHYS_ADDR  = 0x1FC00000;


    MipsLinkerScriptBuilder(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        String basename = target.getDeviceName().substring(3);
        PrintWriter writer = createNewLinkerFile(basename, "p" + basename);
        InterruptList intList = new InterruptList(target.getPic());
        
        populateMemoryRegions(target, intList);

        addLicenseHeader(writer);
    }


    /* Different MIPS devices families have differently-sized boot memory regions and different ways
     * the debugger reserves memory in them.  We have to figure this our by the device family and 
     * create the regions needed manually.
     */
    private void setupBootRegionsByFamily(TargetDevice target, LinkerMemoryRegion lmr) {
        /* The size of the boot flash depends on the device subfamily and is the main factor in how 
         * the regions are laid out.  There are 4 different sizes of boot flash--3kB, 12kB, 20kB, 
         * and 80kB--so we can use that to figure out what we need.  The given region will be a bit 
         * smaller than those sizes because the flash has other stuff, like the config registers.
         */

        if(lmr.getLength() <= (3 * 1024)) {
            // PIC32MM and small PIC32MX:
            addMemoryRegion(new LinkerMemoryRegion("debug_exec_mem", 0, 0x9FC00490, 0x9FC00BF0));
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC00490, 0x9FC00490));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000, 0xBFC00490));
        } else if(lmr.getLength() <= (12 * 1024)) {
            // Large PIC32MX
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC00490, 0x9FC00E00));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000, 0xBFC00490));
            addMemoryRegion(new LinkerMemoryRegion("debug_exec_mem", 0, 0xBFC02000, 0xBFC00FF0));

        } else if(lmr.getLength() <= (20 * 1024)) {
            // PIC32MK
            // The gap between 0x9FC000480 and 0x9FC004B0 is present in the XC32 scripts, so we'll
            // keep it here for now.  The same goes for the two empty kseg0_boot_mem regions.
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC004B0, 0x9FC004B0));
            addMemoryRegion(new LinkerMemoryRegion("debug_exec_mem", 0, 0x9FC20490, 0x9FC23FB0));
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC20490, 0x9FC20490));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000, 0xBFC00480));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem_4B0", 0, 0xBFC004B0, 0xBFC03FB0));
        } else {
            // PIC32MZ
            // The gap between 0x9FC000480 and 0x9FC004B0 is present in the XC32 scripts, so we'll
            // keep it here for now.  The same goes for the empty kseg0_boot_mem region.
            // The PIC32MZ does not need to reserve flash for the debugger.
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC004B0, 0x9FC004B0));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000, 0xBFC00480));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem_4B0", 0, 0xBFC004B0, 0xBFC0FF00));
        }
   }
    
    private void populateMemoryRegions(TargetDevice target, InterruptList intList) {
/*        xMemoryPartition mainPartition = target.getPic().getMainPartition();
        NamedNodeMap attributes;
        String regionId;
        String beginAddr;
        String endAddr;
        LinkerMemoryRegion lmr;
*/
        List<LinkerMemoryRegion> targetRegions = target.getMemoryRegions();

        for(LinkerMemoryRegion region : targetRegions) {
            if(MIPS_RESET_PHYS_ADDR == region.getStartAddress()) {
                setupBootRegionsByFamily(target, region);
            } else {

            }
        }

        // Some devices have a separate region for the exception vectors, so add it here.
        if(!intList.usesVariableOffsets()  &&  SubFamily.PIC32MM != target.getSubFamily()) {
            long startAddr = intList.getDefaultBaseAddress();

            if(0 == startAddr)
                startAddr = 0x9D000000;

            // The vectors start at 0x200.  Before that are things like the general exception vector
            // and TLB refill exception.  Each spot in the vector table has 32 bytes by defualt.
            long endAddr = startAddr + (0x200 + (32 * (intList.getLastVectorNumber() + 1)));

            addMemoryRegion(new LinkerMemoryRegion("execption_mem", 0, startAddr, endAddr));
        }    
        
/*        for(Node bootRegion : mainPartition.getBootConfigRegions()) {
            attributes = bootRegion.getAttributes();
            regionId = attributes.getNamedItem("edc:regionid").getNodeValue();
            beginAddr = attributes.getNamedItem("edc:beginaddr").getNodeValue();
            endAddr = attributes.getNamedItem("edc:endaddr").getNodeValue();

            if(Long.decode(beginAddr) == MIPS_RESET_PHYS_ADDR) {
                lmr = new LinkerMemoryRegion(regionId, 0, beginAddr, endAddr);
                setupBootRegionByFamily(target, lmr);
            } else {
                lmr = new LinkerMemoryRegion(regionId, 0, beginAddr, endAddr, KSEG1_MASK);
                addMemoryRegion(lmr);
            }
        }

        for(Node codeRegion : mainPartition.getCodeRegions()) {
            attributes = codeRegion.getAttributes();
            regionId = attributes.getNamedItem("edc:regionid").getNodeValue();
            beginAddr = attributes.getNamedItem("edc:beginaddr").getNodeValue();
            endAddr = attributes.getNamedItem("edc:endaddr").getNodeValue();

            if(regionId.equals("code")) {
                int access = LinkerMemoryRegion.EXEC_ACCESS | LinkerMemoryRegion.READ_ACCESS;
                lmr = new LinkerMemoryRegion(regionId, access, beginAddr, endAddr, KSEG1_MASK);
                addMemoryRegion(lmr);
            }
        }

        // This actually seems to be for RAM regions despite its name.
        for(Node gprRegion : mainPartition.getGPRRegions()) {
            attributes = gprRegion.getAttributes();
            regionId = attributes.getNamedItem("edc:regionid").getNodeValue();
            beginAddr = attributes.getNamedItem("edc:beginaddr").getNodeValue();
            endAddr = attributes.getNamedItem("edc:endaddr").getNodeValue();

            int access = LinkerMemoryRegion.NOT_EXEC_ACCESS | LinkerMemoryRegion.WRITE_ACCESS;

            switch(regionId) {
                case "kseg0_data_mem":
                    lmr = new LinkerMemoryRegion(regionId, access, beginAddr, endAddr, KSEG0_MASK);
                    addMemoryRegion(lmr);
                    break;
                case "kseg1_data_mem":
                    lmr = new LinkerMemoryRegion(regionId, access, beginAddr, endAddr, KSEG1_MASK);
                    addMemoryRegion(lmr);
                    break;
                default:
                    break;
            }
        }
*/
    }
}
