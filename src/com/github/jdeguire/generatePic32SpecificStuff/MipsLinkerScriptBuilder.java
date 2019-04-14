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

import com.microchip.crownking.edc.DCR;
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
        List<DCR> dcrList = target.getDCRs();

        populateMemoryRegions(target, intList, dcrList);

        outputLicenseHeader(writer);
        outputMemoryRegionCommand(writer);
        outputConfigRegSectionsCommand(writer, dcrList);
    }


    /* Different MIPS devices families have differently-sized boot memory regions and different ways
     * the debugger reserves memory in them.  We have to figure this out based on the region size 
     * and create the regions needed manually.
     */
    private void setupBootRegionsBySize(LinkerMemoryRegion lmr) {
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
    
    private void populateMemoryRegions(TargetDevice target, InterruptList intList, List<DCR> dcrList) {
        List<LinkerMemoryRegion> targetRegions = target.getMemoryRegions();

        for(LinkerMemoryRegion region : targetRegions) {
            switch(region.getType()) {
                case BOOT:
                    if(MIPS_RESET_PHYS_ADDR == region.getStartAddress()) {
                        setupBootRegionsBySize(region);
                    } else {
                        region.setAsKseg1Region();
                        addMemoryRegion(region);
                    }
                    break;
                case CODE:
                    if(region.getName().equalsIgnoreCase("code")) {
                        region.setName("kseg0_program_mem");
                        region.setAccess(LinkerMemoryRegion.EXEC_ACCESS | LinkerMemoryRegion.READ_ACCESS);
                        region.setAsKseg0Region();
                        addMemoryRegion(region);
                    }
                    break;
                case SRAM:
                    if(region.getName().equalsIgnoreCase("kseg1_data_mem")) {
                        region.setAccess(LinkerMemoryRegion.NOT_EXEC_ACCESS | LinkerMemoryRegion.WRITE_ACCESS);
                        region.setAsKseg1Region();
                        addMemoryRegion(region);
                    } else if(region.getName().equalsIgnoreCase("kseg0_data_mem")) {
                        region.setAccess(LinkerMemoryRegion.NOT_EXEC_ACCESS | LinkerMemoryRegion.WRITE_ACCESS);
                        region.setAsKseg0Region();
                        addMemoryRegion(region);
                    }
                    break;
                case EBI:
                case SQI:
                {
                    LinkerMemoryRegion kseg2_region = new LinkerMemoryRegion(region);
                    LinkerMemoryRegion kseg3_region = new LinkerMemoryRegion(region);

                    kseg2_region.setName("kseg2_" + region.getName());
                    kseg2_region.setAsKseg2Region();
                    addMemoryRegion(kseg2_region);

                    kseg3_region.setName("kseg3_" + region.getName());
                    kseg3_region.setAsKseg3Region();
                    addMemoryRegion(kseg3_region);

                    break;
                }
                case SDRAM:
                    region.setAsKseg0Region();
                    addMemoryRegion(region);
                    break;
                case FUSE:
                case PERIPHERAL:
                    region.setAsKseg1Region();
                    addMemoryRegion(region);
                    break;
                default:
                    break;
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

        // Each device config register has its own region, which makes it easier to place the
        // registers in the correct spots when the user specifies their values in code.
        for(DCR dcr : dcrList) {
            long dcrAddr = target.getRegisterAddress(dcr);
            LinkerMemoryRegion dcrRegion = new LinkerMemoryRegion(dcr.getName(),
                                                                  0,
                                                                  dcrAddr,
                                                                  dcrAddr + 4);
            dcrRegion.setAsKseg1Region();
            addMemoryRegion(dcrRegion);
        }
    }
    
    /* Add a SECTIONS {...} command containing just sections for the device config registers.
     */
    private void outputConfigRegSectionsCommand(PrintWriter writer, List<DCR> dcrList) {
        writer.println("SECTIONS");
        writer.println("{");

        for(DCR dcr : dcrList) {
            String sectionName = "config_" + dcr.getName();

            writer.println("  ." + sectionName + " : {");
            writer.println("    KEEP(*(." + sectionName + "))");
            writer.println(" } > " + sectionName);
            writer.println();
        }

        writer.println("}");
        writer.println();
    }
}
