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
        outputPreamble(writer, intList.getDefaultBaseAddress());
        outputMemoryRegionCommand(writer);
        outputConfigRegSectionsCommand(writer, dcrList);
        
        writer.println("SECTIONS");
        writer.println("{");

        outputCommonInitialSections(writer, target);
        if(intList.usesVariableOffsets())
            outputVariableOffsetVectors(writer, intList);

// TODO:  Add other sections here


        // NOTE:  We output this after other code sections because we need the linker to have 
        //        allocated the interrupt handlers before trying to allocate the table.  The table
        //        refers to the sections directly in order to generate trampolines, which will not
        //        work unless the linker already knows where those sections are.
        if(!intList.usesVariableOffsets())
            outputFixedOffsetVectors(writer, intList, target);

        writer.println("}");
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
            // The gap just before 0x9FC004B0 is present in the XC32 scripts, so we'll
            // keep it here for now.  The same goes for the two empty kseg0_boot_mem regions.
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC004B0, 0x9FC004B0));
            addMemoryRegion(new LinkerMemoryRegion("debug_exec_mem", 0, 0x9FC20490, 0x9FC23FB0));
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC20490, 0x9FC20490));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000, 0xBFC00490));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem_4B0", 0, 0xBFC004B0, 0xBFC03FB0));
        } else {
            // PIC32MZ
            // The gap just before 0x9FC004B0 is present in the XC32 scripts, so we'll
            // keep it here for now.  The same goes for the empty kseg0_boot_mem region.
            // The PIC32MZ does not need to reserve flash for the debugger.
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC004B0, 0x9FC004B0));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000, 0xBFC00490));
            addMemoryRegion(new LinkerMemoryRegion("kseg1_boot_mem_4B0", 0, 0xBFC004B0, 0xBFC0FF00));
        }
   }
    
    /* Walk through the list of all target regions and add the ones that the linker scripts needs,
     * possibly modifying them along the way.  This will also add regions for the device config
     * registers.
     */
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

        if(!intList.usesVariableOffsets()) {
            long startAddr = intList.getDefaultBaseAddress();
            long sizePerVector = 32;

            if(0 == startAddr)
                startAddr = 0x9D000000;

            // MicroMIPS-only devices have smaller vectors.
            if(target.supportsMicroMipsIsa()  &&  !target.supportsMips32Isa())
                sizePerVector = 8;
                
            // The vectors start at 0x200.  Before that are things like the general exception vector
            // and TLB refill exception.
            long endAddr = startAddr + (0x200 + (sizePerVector * (intList.getLastVectorNumber() + 1)));

            addMemoryRegion(new LinkerMemoryRegion("execption_mem", 0, startAddr, endAddr));
        }

        // Each device config register has its own region, which makes it easier to place the
        // registers in the correct spots when the user specifies their values in code.
        for(DCR dcr : dcrList) {
            long dcrAddr = target.getRegisterAddress(dcr);
            LinkerMemoryRegion dcrRegion = new LinkerMemoryRegion("config_" + dcr.getName(),
                                                                  0,
                                                                  dcrAddr,
                                                                  dcrAddr + 4);
            dcrRegion.setAsKseg1Region();
            addMemoryRegion(dcrRegion);
        }
    }

    /* Output symbol definitions and commands that are set at the top of the linker script before
     * any other regions or sections are defined.
     */
    private void outputPreamble(PrintWriter writer, long defaultEBaseAddress) {
        if(0 == defaultEBaseAddress)
            defaultEBaseAddress = 0x9D000000;

        writer.println("OUTPUT_FORMAT(\"elf32-tradlittlemips\")");
        writer.println("ENTRY(_reset)");
        writer.println();

        writer.println("/*");
        writer.println(" * Provide for a minimum stack and heap size; these can be overridden");
        writer.println(" * using the linker\'s --defsym option on the command line.");
        writer.println(" */");
        writer.println("EXTERN (_min_stack_size _min_heap_size)");
        writer.println("PROVIDE(_min_stack_size = 0x400);");
        writer.println();

        writer.println("/*");
        writer.println(" * Provide symbols for linker and startup code to set up the interrupt table;");
        writer.println(" * these can be overridden using the linker\'s --defsym option on the command line.");
        writer.println(" */");
        writer.println("PROVIDE(_vector_spacing = 0x0001);");
        writer.println(String.format("PROVIDE(_ebase_address = 0x%08X);", defaultEBaseAddress));
        writer.println();

        writer.println("/*");
        writer.println(" * These memory address symbols are used below for locating their appropriate;");
        writer.println(" * sections.  The TLB Refill and Cache Error address apply only to devices with");
        writer.println(" * an L1 cache.");
        writer.println(" */");
        writer.println("_RESET_ADDR                    = 0xBFC00000;");
        writer.println("_BEV_EXCPT_ADDR                = 0xBFC00380;");
        writer.println("_DBG_EXCPT_ADDR                = 0xBFC00480;");
        writer.println("_SIMPLE_TLB_REFILL_EXCPT_ADDR  = _ebase_address + 0;");
        writer.println("_CACHE_ERR_EXCPT_ADDR          = _ebase_address + 0x100;");
        writer.println("_GEN_EXCPT_ADDR                = _ebase_address + 0x180;");
        writer.println();
        writer.println();
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
            writer.println("  } > " + sectionName);
            writer.println();
        }

        writer.println("}");
        writer.println();
    }

    /* Output small sections that are found at the start of the main SECTIONS command.  These are 
     * dictated by the MIPS hardware and are used to handle the placement of the reset vector as 
     * well as some common exception vectors.
     */
    private void outputCommonInitialSections(PrintWriter writer, TargetDevice target) {
        String outputExceptionRegion;

        if(null != findRegionByName("exception_mem")) {
            outputExceptionRegion = "exception_mem";
        } else {
            outputExceptionRegion = "kseg0_program_mem";
        }

        writer.println("  /* MIPS CPU starts executing here. */");
        writer.println("  .reset _RESET_ADDR :");
        writer.println("  {");
        writer.println("    KEEP(*(.reset))");
        writer.println("    KEEP(*(.reset.startup))");
        writer.println("  } > kseg1_boot_mem");
        writer.println();
        
        writer.println("  /* Boot exception vector; location fixed by hardware. */");
        writer.println("  .bev_excpt _BEV_EXCPT_ADDR :");
        writer.println("  {");
        writer.println("    KEEP(*(.bev_handler))");
        writer.println("  } > kseg1_boot_mem");
        writer.println();

        writer.println("  /* Debugger exception vector; location fixed by hardware. */");
        writer.println("  .dbg_excpt _DBG_EXCPT_ADDR (NOLOAD) :");
        writer.println("  {");
        writer.println("    . += (DEFINED (_DEBUGGER) ? 0x16 : 0x0);");
        writer.println("  } > kseg1_boot_mem");
        writer.println();

        if(target.hasL1Cache()) {
            writer.println("  .cache_init :");
            writer.println("  {");
            writer.println("    *(.cache_init)");
            writer.println("    *(.cache_init.*)");
            writer.println("  } > kseg1_boot_mem_4B0");
            writer.println();

            writer.println("  /* TLB refill vector; location based on EBase address. */");
            writer.println("  .simple_tlb_refill_excpt _SIMPLE_TLB_REFILL_EXCPT_ADDR :");
            writer.println("  {");
            writer.println("    KEEP(*(.simple_tlb_refill_vector))");
            writer.println("  } > " + outputExceptionRegion);
            writer.println();

            writer.println("  /* Cache error vector; location based on EBase address. */");
            writer.println("  .cache_err_excpt _CACHE_ERR_EXCPT_ADDR :");
            writer.println("  {");
            writer.println("    KEEP(*(.cache_err_vector))");
            writer.println("  } > " + outputExceptionRegion);
            writer.println();
        }

        writer.println("  /* General exception vector; location based on EBase address. */");
        writer.println("  .app_excpt _GEN_EXCPT_ADDR :");
        writer.println("  {");
        writer.println("    KEEP(*(.gen_handler))");
        writer.println("  } > " + outputExceptionRegion);
        writer.println();
    }

    /* Output an interrupt vector section assuming that the device supports variable offset vectors.
     * This will set up the vector table so that the interrupt handler is always inline in the table.
     *
     * This differs from XC32, in which the user can use a GCC attribute to choose whether to use a
     * trampoline for each handler (like fixed offset devices) or to inline it.  Here, the user will 
     * not get a choice.
     */
    private void outputVariableOffsetVectors(PrintWriter writer, InterruptList intList) {
        writer.println("  .vectors _ebase_address + 0x200 :");
        writer.println("  {");
        writer.println("    /*  Symbol __vector_offset_n points to .vector_n if it exists,");
        writer.println("     *  otherwise it points to the default handler. The startup code");
        writer.println("     *  uses these value to set up the OFFxxx registers in the ");
        writer.println("     *  interrupt controller.");
        writer.println("     */");

        for(int vectorNum = 0; vectorNum <= intList.getLastVectorNumber(); ++vectorNum) {
            writer.println("    . = ALIGN(4) ;");
            writer.println("    KEEP(*(.vector_" + vectorNum + "))");
            writer.println("     __vector_offset_" + vectorNum + " = (SIZEOF(.vector_" + vectorNum + ") > 0 ? (. - _ebase_address - SIZEOF(.vector_" + vectorNum + ")) : __vector_offset_default);");
        }

        writer.println("    /* Default interrupt handler */");
        writer.println("    . = ALIGN(4) ;");
        writer.println("    __vector_offset_default = . - _ebase_address;");
        writer.println("    KEEP(*(.vector_default))");
        writer.println();

        writer.println("    /*  The offset registers hold a 17-bit offset, allowing a max value less");
        writer.println("     *  than 256*1024, so check for that here.  If you see this error, then ");
        writer.println("     *  one of your vectors is too large.");
        writer.println("     */");
        writer.println("    ASSERT(__vector_offset_default < 256K, \"Error: Vector offset too large.\")");

        writer.println("  } > kseg0_program_mem");
        writer.println();
    }

    /* Output an interrupt vector section assuming that the device use fixed offset vectors.
     * This will add instructions into the vector table to act as trampolines to the actual 
     * handlers.
     *
     * This differs from XC32, in which the user can use a GCC attribute to choose whether to use a
     * trampoline or to inline the handler (like on variable offset devices).  Here, the user will
     * not get a choice.
     */
    private void outputFixedOffsetVectors(PrintWriter writer, InterruptList intList, TargetDevice target) {
        if(target.supportsMicroMipsIsa()  &&  !target.supportsMips32Isa()) {
            writer.println("  /* j (.vector_n >> 1)");
            writer.println("   * ssnop");
            writer.println("   */");

            for(int vectorNum = 0; vectorNum <= intList.getLastVectorNumber(); ++vectorNum) {
                writer.println("  .vector_dispatch_" + vectorNum + " _ebase_address + 0x200 + ((_vector_spacing << 3) * " + vectorNum + ") :");
                writer.println("  {");
                writer.println("    __vector_target_" + vectorNum + " = (SIZEOF(.vector_ " + vectorNum + ") > 0 ? ADDR(.vector_ " + vectorNum + ") : ADDR(.vector_default))");
                writer.println("     LONG(0xD4000000 | ((__vector_target_ " + vectorNum + " >> 1) & 0x03FFFFFF))");
                writer.println("     LONG(0x00000800)");
                writer.println("  } > exception_mem");
            }
        } else {
            writer.println("  /* lui k0, %hi(.vector_n)");
            writer.println("   * ori k0, k0, %lo(.vector_n)");
            writer.println("   * jr k0");
            writer.println("   * ssnop");
            writer.println("   */");

            for(int vectorNum = 0; vectorNum <= intList.getLastVectorNumber(); ++vectorNum) {
                writer.println("  .vector_dispatch_" + vectorNum + " _ebase_address + 0x200 + ((_vector_spacing << 5) * " + vectorNum + ") :");
                writer.println("  {");
                writer.println("    __vector_target_" + vectorNum + " = (SIZEOF(.vector_ " + vectorNum + ") > 0 ? ADDR(.vector_ " + vectorNum + ") : ADDR(.vector_default))");
                writer.println("     LONG(0x3C1A0000 | ((__vector_target_ " + vectorNum + " >> 16) & 0xFFFF))");
                writer.println("     LONG(0x375A0000 | ((__vector_target_ " + vectorNum + ") & 0xFFFF))");
                writer.println("     LONG(0x03400008)");
                writer.println("     LONG(0x00000040)");
                writer.println("  } > exception_mem");
            }
        }

        writer.println();
    }
}
