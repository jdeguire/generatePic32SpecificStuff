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

import com.microchip.crownking.edc.DCR;
import java.io.PrintWriter;
import java.util.List;

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

        outputCodeSections(writer);
        outputInitializationSections(writer);
        outputCtorSections(writer);
        outputReadOnlySections(writer);
        outputDebugDataSection(writer, target.hasFpu(), target.supportsDspR2Ase());
        outputDataSections(writer);
        outputRuntimeMemorySections(writer);
        outputElfDebugSections(writer);

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
            // keep it here for now.  The same goes for the empty kseg0_boot_mem region.
            addMemoryRegion(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC004B0, 0x9FC004B0));
            addMemoryRegion(new LinkerMemoryRegion("debug_exec_mem", 0, 0x9FC20490, 0x9FC23FB0));
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
    
    /* Walk through the list of all target regions and add the ones that the linker script needs,
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

        Utils.writeMultilineCComment(writer, 0, 
                ("Provide for a minimum stack and heap size; these can be overridden using the " +
                 "linker\'s --defsym option on the command line."));
        writer.println("EXTERN (_min_stack_size _min_heap_size)");
        writer.println("PROVIDE(_min_stack_size = 0x400);");
        writer.println("PROVIDE(_min_heap_size = 0);");
        writer.println();

        Utils.writeMultilineCComment(writer, 0, 
                ("Provide symbols for linker and startup code to set up the interrupt table; " +
                 "these can be overridden using the linker\'s --defsym option on the command line."));
        writer.println("PROVIDE(_vector_spacing = 0x0001);");
        writer.println(String.format("PROVIDE(_ebase_address = 0x%08X);", defaultEBaseAddress));
        writer.println();

        Utils.writeMultilineCComment(writer, 0, 
                ("These memory address symbols are used below for locating their appropriate " +
                 "sections.  The TLB Refill and Cache Error address apply only to devices with " +
                 "an L1 cache."));
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

    private void outputCodeSections(PrintWriter writer) {
        writer.println("  .text :");
        writer.println("  {");
        writer.println("    *(.text)");
        writer.println("    *(.text.*)");
        writer.println("    *(.stub .gnu.linkonce.t.*)");
        writer.println("    KEEP (*(.text.*personality*))");
        writer.println("    *(.mips16.fn.*)");
        writer.println("    *(.mips16.call.*)");
        writer.println("    *(.gnu.warning)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();
    }

    private void outputInitializationSections(PrintWriter writer) {
        writer.println("  /* Global-namespace object initialization */");
        writer.println("  .init   :");
        writer.println("  {");
        writer.println("    KEEP (*crti.o(.init))");
        writer.println("    KEEP (*crtbegin.o(.init))");
        writer.println("    KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o *crtn.o ).init))");
        writer.println("    KEEP (*crtend.o(.init))");
        writer.println("    KEEP (*crtn.o(.init))");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        writer.println("  .fini   :");
        writer.println("  {");
        writer.println("    KEEP (*(.fini))");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        writer.println("  .preinit_array   :");
        writer.println("  {");
        writer.println("    PROVIDE_HIDDEN (__preinit_array_start = .);");
        writer.println("writer.println(\"    KEEP (*(.preinit_array))");
        writer.println("    PROVIDE_HIDDEN (__preinit_array_end = .);");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        writer.println("  .init_array   :");
        writer.println("  {");
        writer.println("    PROVIDE_HIDDEN (__init_array_start = .);");
        writer.println("    KEEP (*(SORT(.init_array.*)))");
        writer.println("    KEEP (*(.init_array))");
        writer.println("    PROVIDE_HIDDEN (__init_array_end = .);");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        writer.println("  .fini_array   :");
        writer.println("  {");
        writer.println("    PROVIDE_HIDDEN (__fini_array_start = .);");
        writer.println("    KEEP (*(SORT(.fini_array.*)))");
        writer.println("    KEEP (*(.fini_array))");
        writer.println("    PROVIDE_HIDDEN (__fini_array_end = .);");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();
    }

    private void outputCtorSections(PrintWriter writer) {
        writer.println("  .ctors   :");
        writer.println("  {");
        writer.println("    /* GCC uses crtbegin.o to find the start of");
        writer.println("       the constructors, so we make sure it is");
        writer.println("       first.  Because this is a wildcard, it");
        writer.println("       doesn't matter if the user does not");
        writer.println("       actually link against crtbegin.o; the");
        writer.println("       linker won't look for a file to match a");
        writer.println("       wildcard.  The wildcard also means that it");
        writer.println("       doesn't matter which directory crtbegin.o");
        writer.println("       is in.  */");
        writer.println("    KEEP (*crtbegin.o(.ctors))");
        writer.println("    KEEP (*crtbegin?.o(.ctors))");
        writer.println("    /* We don't want to include the .ctor section from");
        writer.println("       the crtend.o file until after the sorted ctors.");
        writer.println("       The .ctor section from the crtend file contains the");
        writer.println("       end of ctors marker and it must be last */");
        writer.println("    KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o ) .ctors))");
        writer.println("    KEEP (*(SORT(.ctors.*)))");
        writer.println("    KEEP (*(.ctors))");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        writer.println("  .dtors   :");
        writer.println("  {");
        writer.println("    KEEP (*crtbegin.o(.dtors))");
        writer.println("    KEEP (*crtbegin?.o(.dtors))");
        writer.println("    KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o ) .dtors))");
        writer.println("    KEEP (*(SORT(.dtors.*)))");
        writer.println("    KEEP (*(.dtors))");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();
    }

    private void outputReadOnlySections(PrintWriter writer) {
        writer.println("  .rodata   :");
        writer.println("  {");
        writer.println("    *( .gnu.linkonce.r.*)");
        writer.println("    *(.rodata1)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        Utils.writeMultilineCComment(writer, 2, 
                ("Small initialized constant global and static data can be placed in the .sdata2 " +
                 "section.  This is different from .sdata, which contains small initialized " + 
                 "non-constant global and static data."));
        writer.println("  .sdata2 ALIGN(4) :");
        writer.println("  {");
        writer.println("    *(.sdata2 .sdata2.* .gnu.linkonce.s2.*)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        Utils.writeMultilineCComment(writer, 2, 
                ("Uninitialized constant global and static data (i.e., variables which will always " +
                 "be zero).  Again, this is different from .sbss, which contains small " + 
                 "non-initialized, non-constant global and static data."));
        writer.println("  .sbss2 ALIGN(4) :");
        writer.println("  {");
        writer.println("    *(.sbss2 .sbss2.* .gnu.linkonce.sb2.*)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >kseg0_program_mem");
        writer.println();

        writer.println("  .eh_frame_hdr   :");
        writer.println("  {");
        writer.println("    *(.eh_frame_hdr)");
        writer.println("  } >kseg0_program_mem");
        writer.println("    . = ALIGN(4) ;");
        writer.println();

        writer.println("  .eh_frame   : ONLY_IF_RO");
        writer.println("  {");
        writer.println("    KEEP (*(.eh_frame))");
        writer.println("  } >kseg0_program_mem");
        writer.println("    . = ALIGN(4) ;");
        writer.println();

        writer.println("  .gcc_except_table   : ONLY_IF_RO");
        writer.println("  {");
        writer.println("    *(.gcc_except_table .gcc_except_table.*)");
        writer.println("  } >kseg0_program_mem");
        writer.println("    . = ALIGN(4) ;");
        writer.println();
    }

    private void outputDebugDataSection(PrintWriter writer, boolean hasFpu, boolean hasDspr2) {
        writer.println("  .dbg_data (NOLOAD) :");
        writer.println("  {");
        writer.println("    . += (DEFINED (_DEBUGGER) ? 0x200 : 0x0);");

        if(hasDspr2) {
            writer.println("    /* Additional data memory required for DSPr2 registers */");
            writer.println("    . += (DEFINED (_DEBUGGER) ? 0x80 : 0x0);");
        }
        if(hasFpu) {
            writer.println("    /* Additional data memory required for FPU64 registers */");
            writer.println("    . += (DEFINED (_DEBUGGER) ? 0x100 : 0x0);");
        }

        if(null != findRegionByName("kseg0_data_mem"))
            writer.println("  } >kseg0_data_mem");
        else
            writer.println("  } >kseg1_data_mem");
        writer.println();
    }
    
    private void outputDataSections(PrintWriter writer) {
        String dataRegion;

        if(null != findRegionByName("kseg0_data_mem"))
            dataRegion = "kseg0_data_mem";
        else
            dataRegion = "kseg1_data_mem";

        writer.println("  .jcr   :");
        writer.println("  {");
        writer.println("    KEEP (*(.jcr))");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  .eh_frame    : ONLY_IF_RW");
        writer.println("  {");
        writer.println("    KEEP (*(.eh_frame))");
        writer.println("  } >" + dataRegion);
        writer.println("    . = ALIGN(4) ;");
        writer.println();

        writer.println("  .gcc_except_table    : ONLY_IF_RW");
        writer.println("  {");
        writer.println("    *(.gcc_except_table .gcc_except_table.*)");
        writer.println("  } >" + dataRegion);
        writer.println("    . = ALIGN(4) ;");
        writer.println();

        writer.println("  .persist (NOLOAD) :");
        writer.println("  {");
        writer.println("    _persist_begin = .;");
        writer.println("    *(.persist .persist.*)");
        writer.println("    *(.pbss .pbss.*)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("    _persist_end = .;");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  .data   :");
        writer.println("  {");
        writer.println("    *(.data)");
        writer.println("    *(.data.*)");
        writer.println("    *( .gnu.linkonce.d.*)");
        writer.println("    SORT(CONSTRUCTORS)");
        writer.println("    *(.data1)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  . = .;");
        writer.println("  _gp = ALIGN(16) + 0x7ff0;");
        writer.println();

        writer.println("  .got ALIGN(4) :");
        writer.println("  {");
        writer.println("    *(.got.plt) *(.got)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >" + dataRegion);
        writer.println();

        Utils.writeMultilineCComment(writer, 2, 
                ("We want the small data sections together, so single-instruction offsets can " +
                 "access them all, and initialized data all before uninitialized, so we can " + 
                 "shorten the on-disk segment size."));
        writer.println("  .sdata ALIGN(4) :");
        writer.println("  {");
        writer.println("    _sdata_begin = . ;");
        writer.println("    *(.sdata .sdata.* .gnu.linkonce.s.*)");
        writer.println("    . = ALIGN(4) ;");
        writer.println("    _sdata_end = . ;");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  .lit8           :");
        writer.println("  {");
        writer.println("    *(.lit8)");
        writer.println("  } >" + dataRegion);
        writer.println("  .lit4           :");
        writer.println("  {");
        writer.println("    *(.lit4)");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  . = ALIGN (4) ;");
        writer.println("  _data_end = . ;");
        writer.println("  _bss_begin = . ;");
        writer.println();

        writer.println("  .sbss ALIGN(4) :");
        writer.println("  {");
        writer.println("    _sbss_begin = . ;");
        writer.println("    *(.dynsbss)");
        writer.println("    *(.sbss .sbss.* .gnu.linkonce.sb.*)");
        writer.println("    *(.scommon)");
        writer.println("    _sbss_end = . ;");
        writer.println("    . = ALIGN(4) ;");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  .bss     :");
        writer.println("  {");
        writer.println("    *(.bss)");
        writer.println("    *(.bss.*)");
        writer.println("    *(.dynbss)");
        writer.println("    *(.gnu.linkonce.b.*)");
        writer.println("    *(COMMON)");
        writer.println("   /* Align here to ensure that the .bss section occupies space up to");
        writer.println("      _end.  Align after .bss to ensure correct alignment even if the");
        writer.println("      .bss section disappears because there are no input sections. */");
        writer.println("   . = ALIGN(. != 0 ? 4 : 1);");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  . = ALIGN(4) ;");
        writer.println("  _end = . ;");
        writer.println("  _bss_end = . ;");
        writer.println();
    }

    private void outputRuntimeMemorySections(PrintWriter writer) {
        String dataRegion;

        if(null != findRegionByName("kseg0_data_mem"))
            dataRegion = "kseg0_data_mem";
        else
            dataRegion = "kseg1_data_mem";

        writer.println("  .heap :");
        writer.println("  {");
        writer.println("    _heap_start = .");
        writer.println("    . += _min_heap_size");
        writer.println("    _heap_end = .");
        writer.println("  } >" + dataRegion);
        writer.println();

        writer.println("  . = ALIGN(4) ;");
        writer.println();
        
        Utils.writeMultilineCComment(writer, 2,
                ("Allocate some space for a stack at the end of memory because the stack grows " +
                 "downward.  This is just the minimum stack size that will be allowed; the stack " +
                 "can actually grow larger. Use this symbol to check for overflow."));
        writer.println("_stack_limit = .");
        writer.println("  .stack ORIGIN(" + dataRegion + ") + LENGTH(" + dataRegion + ") - _min_stack_size :");
        writer.println("  {");
        writer.println("    . += _min_stack_size");
        writer.println("  } >" + dataRegion);
                
        writer.println("  . = ALIGN(4) ;");
        writer.println("  _stack = . - 4");
        writer.println("  ASSERT(_stack < ORIGIN(" + dataRegion + ") + LENGTH(" + dataRegion + "), \"Error: Not enough room for stack.\")");
        writer.println();
    }

    private void outputElfDebugSections(PrintWriter writer) {
        writer.println("    /* The .pdr section belongs in the absolute section */");
        writer.println("    /DISCARD/ : { *(.pdr) }");
        writer.println("  .gptab.sdata : { *(.gptab.data) *(.gptab.sdata) }");
        writer.println("  .gptab.sbss : { *(.gptab.bss) *(.gptab.sbss) }");
        writer.println("  .mdebug.abi32 0 : { KEEP(*(.mdebug.abi32)) }");
        writer.println("  .mdebug.abiN32 0 : { KEEP(*(.mdebug.abiN32)) }");
        writer.println("  .mdebug.abi64 0 : { KEEP(*(.mdebug.abi64)) }");
        writer.println("  .mdebug.abiO64 0 : { KEEP(*(.mdebug.abiO64)) }");
        writer.println("  .mdebug.eabi32 0 : { KEEP(*(.mdebug.eabi32)) }");
        writer.println("  .mdebug.eabi64 0 : { KEEP(*(.mdebug.eabi64)) }");
        writer.println("  .gcc_compiled_long32 : { KEEP(*(.gcc_compiled_long32)) }");
        writer.println("  .gcc_compiled_long64 : { KEEP(*(.gcc_compiled_long64)) }");
        writer.println("  /* Stabs debugging sections.  */");
        writer.println("  .stab          0 : { *(.stab) }");
        writer.println("  .stabstr       0 : { *(.stabstr) }");
        writer.println("  .stab.excl     0 : { *(.stab.excl) }");
        writer.println("  .stab.exclstr  0 : { *(.stab.exclstr) }");
        writer.println("  .stab.index    0 : { *(.stab.index) }");
        writer.println("  .stab.indexstr 0 : { *(.stab.indexstr) }");
        writer.println("  .comment       0 : { *(.comment) }");
        writer.println("  /* DWARF debug sections used by MPLAB X for source-level debugging. ");
        writer.println("     Symbols in the DWARF debugging sections are relative to the beginning");
        writer.println("     of the section so we begin them at 0.  */");
        writer.println("  /* DWARF 1 */");
        writer.println("  .debug          0 : { *.elf(.debug) *(.debug) }");
        writer.println("  .line           0 : { *.elf(.line) *(.line) }");
        writer.println("  /* GNU DWARF 1 extensions */");
        writer.println("  .debug_srcinfo  0 : { *.elf(.debug_srcinfo) *(.debug_srcinfo) }");
        writer.println("  .debug_sfnames  0 : { *.elf(.debug_sfnames) *(.debug_sfnames) }");
        writer.println("  /* DWARF 1.1 and DWARF 2 */");
        writer.println("  .debug_aranges  0 : { *.elf(.debug_aranges) *(.debug_aranges) }");
        writer.println("  .debug_pubnames 0 : { *.elf(.debug_pubnames) *(.debug_pubnames) }");
        writer.println("  /* DWARF 2 */");
        writer.println("  .debug_info     0 : { *.elf(.debug_info .gnu.linkonce.wi.*) *(.debug_info .gnu.linkonce.wi.*) }");
        writer.println("  .debug_abbrev   0 : { *.elf(.debug_abbrev) *(.debug_abbrev) }");
        writer.println("  .debug_line     0 : { *.elf(.debug_line) *(.debug_line) }");
        writer.println("  .debug_frame    0 : { *.elf(.debug_frame) *(.debug_frame) }");
        writer.println("  .debug_str      0 : { *.elf(.debug_str) *(.debug_str) }");
        writer.println("  .debug_loc      0 : { *.elf(.debug_loc) *(.debug_loc) }");
        writer.println("  .debug_macinfo  0 : { *.elf(.debug_macinfo) *(.debug_macinfo) }");
        writer.println("  /* SGI/MIPS DWARF 2 extensions */");
        writer.println("  .debug_weaknames 0 : { *.elf(.debug_weaknames) *(.debug_weaknames) }");
        writer.println("  .debug_funcnames 0 : { *.elf(.debug_funcnames) *(.debug_funcnames) }");
        writer.println("  .debug_typenames 0 : { *.elf(.debug_typenames) *(.debug_typenames) }");
        writer.println("  .debug_varnames  0 : { *.elf(.debug_varnames) *(.debug_varnames) }");
        writer.println("  .debug_pubtypes 0 : { *.elf(.debug_pubtypes) *(.debug_pubtypes) }");
        writer.println("  .debug_ranges   0 : { *.elf(.debug_ranges) *(.debug_ranges) }");
        writer.println("  /DISCARD/ : { *(.rel.dyn) }");
        writer.println("  .gnu.attributes 0 : { KEEP (*(.gnu.attributes)) }");
        writer.println("  /DISCARD/ : { *(.note.GNU-stack) }");
        writer.println("  /DISCARD/ : { *(.note.GNU-stack) *(.gnu_debuglink) *(.gnu.lto_*) *(.discard) }");
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

        Utils.writeMultilineCComment(writer, 4, 
                ("Symbol __vector_offset_n points to .vector_n if it exists, otherwise it points " +
                 "to the default handler. The startup code uses these value to set up the OFFxxx " +
                 "registers in the interrupt controller."));

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

        Utils.writeMultilineCComment(writer, 4, 
                ("The offset registers hold a 17-bit offset, allowing a max value less than " +
                 "256*1024, so check for that here.  If you see this error, then one of your " +
                 "vectors is too large."));
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
