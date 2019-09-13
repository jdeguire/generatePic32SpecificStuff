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
        InterruptList intList = new InterruptList(target.getPic());
        List<DCR> dcrList = target.getDCRs();

        createNewLinkerFile(target);

        clearMemoryRegions();
        populateMemoryRegions(target, intList, dcrList);

        outputLicenseHeader();
        outputPreamble(intList.getDefaultBaseAddress());
        outputMemoryRegionCommand();
        outputConfigRegSectionsCommand(dcrList);

        writer_.println("SECTIONS");
        writer_.println("{");

        outputCommonInitialSections(target);
        if(intList.usesVariableOffsets())
            outputVariableOffsetVectors(intList);

        outputCodeSections();
        outputInitializationSections();
        outputCtorSections();
        outputReadOnlySections();
        outputDebugDataSection(target.hasFpu(), target.supportsDspR2Ase());
        outputDataSections(target.hasL1Cache());
        outputRuntimeMemorySections();
        outputElfDebugSections();

        // We output this after other code sections because we need the linker to have 
        // allocated the interrupt handlers before trying to allocate the table.  The 
        // table refers to the sections directly in order to generate trampolines, which 
        // will not work unless the linker already knows where those sections are.
        if(!intList.usesVariableOffsets())
            outputFixedOffsetVectors(intList, target);

        writer_.println("}");

        closeLinkerFile();
    }
    

    /* Different MIPS device families have differently-sized boot memory regions and different ways
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
            // PIC32MM and small PIC32MX
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
    private void outputPreamble(long defaultEBaseAddress) {
        if(0 == defaultEBaseAddress)
            defaultEBaseAddress = 0x9D000000;

        writer_.println("OUTPUT_FORMAT(\"elf32-tradlittlemips\")");
        writer_.println("ENTRY(_reset)");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 0, 
                ("Provide for a minimum stack and heap size; these can be overridden using the " +
                 "linker\'s --defsym option on the command line."));
        writer_.println("EXTERN (_min_stack_size _min_heap_size)");
        writer_.println("PROVIDE(_min_stack_size = 0x400);");
        writer_.println("PROVIDE(_min_heap_size = 0);");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 0, 
                ("Provide symbols for linker and startup code to set up the interrupt table; " +
                 "these can be overridden using the linker\'s --defsym option on the command line."));
        writer_.println("PROVIDE(_vector_spacing = 0x0001);");
        writer_.println(String.format("PROVIDE(_ebase_address = 0x%08X);", defaultEBaseAddress));
        writer_.println();

        Utils.writeMultilineCComment(writer_, 0, 
                ("These memory address symbols are used below for locating their appropriate " +
                 "sections.  The TLB Refill and Cache Error address apply only to devices with " +
                 "an L1 cache."));
        writer_.println("_RESET_ADDR                    = 0xBFC00000;");
        writer_.println("_BEV_EXCPT_ADDR                = 0xBFC00380;");
        writer_.println("_DBG_EXCPT_ADDR                = 0xBFC00480;");
        writer_.println("_SIMPLE_TLB_REFILL_EXCPT_ADDR  = _ebase_address + 0;");
        writer_.println("_CACHE_ERR_EXCPT_ADDR          = _ebase_address + 0x100;");
        writer_.println("_GEN_EXCPT_ADDR                = _ebase_address + 0x180;");
        writer_.println();
        writer_.println();
    }
    
    /* Add a SECTIONS {...} command containing just sections for the device config registers.
     */
    private void outputConfigRegSectionsCommand(List<DCR> dcrList) {
        writer_.println("SECTIONS");
        writer_.println("{");

        for(DCR dcr : dcrList) {
            String sectionName = "config_" + dcr.getName();

            writer_.println("  ." + sectionName + " : {");
            writer_.println("    KEEP(*(." + sectionName + "))");
            writer_.println("  } > " + sectionName);
            writer_.println();
        }

        writer_.println("}");
        writer_.println();
    }

    /* Output small sections that are found at the start of the main SECTIONS command.  These are 
     * dictated by the MIPS hardware and are used to handle the placement of the reset vector as 
     * well as some common exception vectors.
     */
    private void outputCommonInitialSections(TargetDevice target) {
        String outputExceptionRegion;

        if(null != findRegionByName("exception_mem")) {
            outputExceptionRegion = "exception_mem";
        } else {
            outputExceptionRegion = "kseg0_program_mem";
        }

        writer_.println("  /* MIPS CPU starts executing here. */");
        writer_.println("  .reset _RESET_ADDR :");
        writer_.println("  {");
        writer_.println("    KEEP(*(.reset))");
        writer_.println("    KEEP(*(.reset.startup))");
        writer_.println("  } > kseg1_boot_mem");
        writer_.println();
        
        writer_.println("  /* Boot exception vector; location fixed by hardware. */");
        writer_.println("  .bev_excpt _BEV_EXCPT_ADDR :");
        writer_.println("  {");
        writer_.println("    KEEP(*(.bev_handler))");
        writer_.println("  } > kseg1_boot_mem");
        writer_.println();

        writer_.println("  /* Debugger exception vector; location fixed by hardware. */");
        writer_.println("  .dbg_excpt _DBG_EXCPT_ADDR (NOLOAD) :");
        writer_.println("  {");
        writer_.println("    . += (DEFINED (_DEBUGGER) ? 0x16 : 0x0);");
        writer_.println("  } > kseg1_boot_mem");
        writer_.println();

        if(target.hasL1Cache()) {
            writer_.println("  .cache_init :");
            writer_.println("  {");
            writer_.println("    *(.cache_init)");
            writer_.println("    *(.cache_init.*)");
            writer_.println("  } > kseg1_boot_mem_4B0");
            writer_.println();

            writer_.println("  /* TLB refill vector; location based on EBase address. */");
            writer_.println("  .simple_tlb_refill_excpt _SIMPLE_TLB_REFILL_EXCPT_ADDR :");
            writer_.println("  {");
            writer_.println("    KEEP(*(.simple_tlb_refill_vector))");
            writer_.println("  } > " + outputExceptionRegion);
            writer_.println();

            writer_.println("  /* Cache error vector; location based on EBase address. */");
            writer_.println("  .cache_err_excpt _CACHE_ERR_EXCPT_ADDR :");
            writer_.println("  {");
            writer_.println("    KEEP(*(.cache_err_vector))");
            writer_.println("  } > " + outputExceptionRegion);
            writer_.println();
        }

        writer_.println("  /* General exception vector; location based on EBase address. */");
        writer_.println("  .app_excpt _GEN_EXCPT_ADDR :");
        writer_.println("  {");
        writer_.println("    KEEP(*(.gen_handler))");
        writer_.println("  } > " + outputExceptionRegion);
        writer_.println();
    }

    /* Output the .text section, which contains most code.
     */
    private void outputCodeSections() {
        writer_.println("  .text :");
        writer_.println("  {");
        writer_.println("    *(.text .text.* .stub .gnu.linkonce.t.*)");
        writer_.println("    KEEP (*(.text.*personality*))");
        writer_.println("    *(.mips16.fn.*)");
        writer_.println("    *(.mips16.call.*)");
        writer_.println("    *(.gnu.warning)");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();
    }

    /* Output sections that contain initialization data used by the library to set up statically-
     * allocated symbols.
     */
    private void outputInitializationSections() {
        writer_.println("  /* Global-namespace object initialization */");
        writer_.println("  .init   :");
        writer_.println("  {");
        writer_.println("    KEEP (*crti.o(.init))");
        writer_.println("    KEEP (*crtbegin.o(.init))");
        writer_.println("    KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o *crtn.o ).init))");
        writer_.println("    KEEP (*crtend.o(.init))");
        writer_.println("    KEEP (*crtn.o(.init))");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        writer_.println("  .fini   :");
        writer_.println("  {");
        writer_.println("    KEEP (*(.fini))");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        writer_.println("  .preinit_array   :");
        writer_.println("  {");
        writer_.println("    PROVIDE_HIDDEN (__preinit_array_start = .);");
        writer_.println("    KEEP (*(.preinit_array))");
        writer_.println("    PROVIDE_HIDDEN (__preinit_array_end = .);");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        writer_.println("  .init_array   :");
        writer_.println("  {");
        writer_.println("    PROVIDE_HIDDEN (__init_array_start = .);");
        writer_.println("    KEEP (*(SORT(.init_array.*)))");
        writer_.println("    KEEP (*(.init_array))");
        writer_.println("    PROVIDE_HIDDEN (__init_array_end = .);");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        writer_.println("  .fini_array   :");
        writer_.println("  {");
        writer_.println("    PROVIDE_HIDDEN (__fini_array_start = .);");
        writer_.println("    KEEP (*(SORT(.fini_array.*)))");
        writer_.println("    KEEP (*(.fini_array))");
        writer_.println("    PROVIDE_HIDDEN (__fini_array_end = .);");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();
    }

    /* Output sections for initialization and de-initialization routines.  A quick search online 
     * suggests that these are older and have essentially been replaced by the sections in the 
     * method above.
     */
    private void outputCtorSections() {
        writer_.println("  .ctors   :");
        writer_.println("  {");
        writer_.println("    /* GCC uses crtbegin.o to find the start of");
        writer_.println("       the constructors, so we make sure it is");
        writer_.println("       first.  Because this is a wildcard, it");
        writer_.println("       doesn't matter if the user does not");
        writer_.println("       actually link against crtbegin.o; the");
        writer_.println("       linker won't look for a file to match a");
        writer_.println("       wildcard.  The wildcard also means that it");
        writer_.println("       doesn't matter which directory crtbegin.o");
        writer_.println("       is in.  */");
        writer_.println("    KEEP (*crtbegin.o(.ctors))");
        writer_.println("    KEEP (*crtbegin?.o(.ctors))");
        writer_.println("    /* We don't want to include the .ctor section from");
        writer_.println("       the crtend.o file until after the sorted ctors.");
        writer_.println("       The .ctor section from the crtend file contains the");
        writer_.println("       end of ctors marker and it must be last */");
        writer_.println("    KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o ) .ctors))");
        writer_.println("    KEEP (*(SORT(.ctors.*)))");
        writer_.println("    KEEP (*(.ctors))");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        writer_.println("  .dtors   :");
        writer_.println("  {");
        writer_.println("    KEEP (*crtbegin.o(.dtors))");
        writer_.println("    KEEP (*crtbegin?.o(.dtors))");
        writer_.println("    KEEP (*(EXCLUDE_FILE (*crtend.o *crtend?.o ) .dtors))");
        writer_.println("    KEEP (*(SORT(.dtors.*)))");
        writer_.println("    KEEP (*(.dtors))");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();
    }

    /* Output sections that contain read-only data.  These will always put read-only data into
     * program space.
     */
    private void outputReadOnlySections() {
        writer_.println("  .rodata   :");
        writer_.println("  {");
        writer_.println("    *( .gnu.linkonce.r.*)");
        writer_.println("    *(.rodata1)");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 2, 
                ("Small initialized constant global and static data can be placed in the .sdata2 " +
                 "section.  This is different from .sdata, which contains small initialized " + 
                 "non-constant global and static data."));
        writer_.println("  .sdata2 ALIGN(4) :");
        writer_.println("  {");
        writer_.println("    *(.sdata2 .sdata2.* .gnu.linkonce.s2.*)");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 2, 
                ("Uninitialized constant global and static data (i.e., variables which will always " +
                 "be zero).  Again, this is different from .sbss, which contains small " + 
                 "non-initialized, non-constant global and static data."));
        writer_.println("  .sbss2 ALIGN(4) :");
        writer_.println("  {");
        writer_.println("    *(.sbss2 .sbss2.* .gnu.linkonce.sb2.*)");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >kseg0_program_mem");
        writer_.println();

        writer_.println("  .eh_frame_hdr   :");
        writer_.println("  {");
        writer_.println("    *(.eh_frame_hdr)");
        writer_.println("  } >kseg0_program_mem");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println();

        writer_.println("  .eh_frame   : ONLY_IF_RO");
        writer_.println("  {");
        writer_.println("    KEEP (*(.eh_frame))");
        writer_.println("  } >kseg0_program_mem");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println();

        writer_.println("  .gcc_except_table   : ONLY_IF_RO");
        writer_.println("  {");
        writer_.println("    *(.gcc_except_table .gcc_except_table.*)");
        writer_.println("  } >kseg0_program_mem");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println();
    }

    /* Output a section used to reserve RAM for the debugger.
     */
    private void outputDebugDataSection(boolean hasFpu, boolean hasDspr2) {
        writer_.println("  .dbg_data (NOLOAD) :");
        writer_.println("  {");
        writer_.println("    . += (DEFINED (_DEBUGGER) ? 0x200 : 0x0);");

        if(hasDspr2) {
            writer_.println("    /* Additional data memory required for DSPr2 registers */");
            writer_.println("    . += (DEFINED (_DEBUGGER) ? 0x80 : 0x0);");
        }
        if(hasFpu) {
            writer_.println("    /* Additional data memory required for FPU64 registers */");
            writer_.println("    . += (DEFINED (_DEBUGGER) ? 0x100 : 0x0);");
        }

        if(null != findRegionByName("kseg0_data_mem"))
            writer_.println("  } >kseg0_data_mem");
        else
            writer_.println("  } >kseg1_data_mem");
        writer_.println();
    }
    
    /* Output sections for data as opposed to code.
    */
    private void outputDataSections(boolean hasCache) {
        String dataRegion;

        if(null != findRegionByName("kseg0_data_mem"))
            dataRegion = "kseg0_data_mem";
        else
            dataRegion = "kseg1_data_mem";

        writer_.println("  .jcr   :");
        writer_.println("  {");
        writer_.println("    KEEP (*(.jcr))");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        writer_.println("  .eh_frame    : ONLY_IF_RW");
        writer_.println("  {");
        writer_.println("    KEEP (*(.eh_frame))");
        writer_.println("  } >" + dataRegion);
        writer_.println("    . = ALIGN(4) ;");
        writer_.println();

        writer_.println("  .gcc_except_table    : ONLY_IF_RW");
        writer_.println("  {");
        writer_.println("    *(.gcc_except_table .gcc_except_table.*)");
        writer_.println("  } >" + dataRegion);
        writer_.println("    . = ALIGN(4) ;");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 2, 
                ("Use the \'section\' attribute to put data in this section that you want to " +
                 "persist through software resets."));
        writer_.println("  .persist (NOLOAD) :");
        writer_.println("  {");
        if(hasCache) {
            writer_.println("    /* Ensure normal and persistent sections do not overlap 16-byte cache line. */");
            writer_.println("    . = ALIGN(16) ;");
        }
        writer_.println("    _persist_begin = .;");
        writer_.println("    __persist_start__ = .;");
        writer_.println("    *(.persist .persist.*)");
        writer_.println("    *(.pbss .pbss.*)");
        if(hasCache) {
            writer_.println("    . = ALIGN(16) ;");
        } else {
            writer_.println("    . = ALIGN(4) ;");
        }
        writer_.println("    __persist_end__ = .;");
        writer_.println("    _persist_end = .;");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        writer_.println("  __data_start__ = .;");
        writer_.println();

        writer_.println("  .data   :");
        writer_.println("  {");
        writer_.println("    *(.data)");
        writer_.println("    *(.data.*)");
        writer_.println("    *( .gnu.linkonce.d.*)");
        writer_.println("    SORT(CONSTRUCTORS)");
        writer_.println("    *(.data1)");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        writer_.println("  . = .;");
        writer_.println("  _gp = ALIGN(16) + 0x7ff0;");
        writer_.println();

        writer_.println("  .got ALIGN(4) :");
        writer_.println("  {");
        writer_.println("    *(.got.plt) *(.got)");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        Utils.writeMultilineCComment(writer_, 2, 
                ("We want the small data sections together, so single-instruction offsets can " +
                 "access them all, and initialized data all before uninitialized, so we can " + 
                 "shorten the on-disk segment size."));
        writer_.println("  .sdata ALIGN(4) :");
        writer_.println("  {");
        writer_.println("    _sdata_begin = . ;");
        writer_.println("    *(.sdata .sdata.* .gnu.linkonce.s.*)");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("    _sdata_end = . ;");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        writer_.println("  .lit8           :");
        writer_.println("  {");
        writer_.println("    *(.lit8)");
        writer_.println("  } >" + dataRegion);
        writer_.println("  .lit4           :");
        writer_.println("  {");
        writer_.println("    *(.lit4)");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        writer_.println("  . = ALIGN (4) ;");
        writer_.println("  _data_end = . ;");
        writer_.println("  __data_end__ = .;");
        writer_.println("  _bss_begin = . ;");
        writer_.println("  __bss_start__ = .;");
        writer_.println();

        writer_.println("  .sbss ALIGN(4) :");
        writer_.println("  {");
        writer_.println("    _sbss_begin = . ;");
        writer_.println("    *(.dynsbss)");
        writer_.println("    *(.sbss .sbss.* .gnu.linkonce.sb.*)");
        writer_.println("    *(.scommon)");
        writer_.println("    _sbss_end = . ;");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        writer_.println("  .bss     :");
        writer_.println("  {");
        writer_.println("    *(.bss)");
        writer_.println("    *(.bss.*)");
        writer_.println("    *(.dynbss)");
        writer_.println("    *(.gnu.linkonce.b.*)");
        writer_.println("    *(COMMON)");
        writer_.println("   /* Align here to ensure that the .bss section occupies space up to");
        writer_.println("      _end.  Align after .bss to ensure correct alignment even if the");
        writer_.println("      .bss section disappears because there are no input sections. */");
        writer_.println("   . = ALIGN(. != 0 ? 4 : 1);");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        writer_.println("  . = ALIGN(4) ;");
        writer_.println("  _end = . ;");
        writer_.println("  _bss_end = . ;");
        writer_.println("  __bss_end__ = .;");
        writer_.println();
    }

    /* Output sections for a heap and stack.  The heap size is fixed to _min_heap_size, which is 0
     * by default and settable on the command-line.  The stack is at least _min_stack_size, but can
     * actually grow as long as there's room.
     */
    private void outputRuntimeMemorySections() {
        String dataRegion;

        if(null != findRegionByName("kseg0_data_mem"))
            dataRegion = "kseg0_data_mem";
        else
            dataRegion = "kseg1_data_mem";

        writer_.println("  .heap :");
        writer_.println("  {");
        writer_.println("    _sheap = . ;");
        writer_.println("    . += _min_heap_size ;");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("    _eheap = . ;");
        writer_.println("    __HeapLimit = . ;");
        writer_.println("  } >" + dataRegion);
        writer_.println();

        Utils.writeMultilineCComment(writer_, 2,
                ("Allocate some space for a stack at the end of memory because the stack grows " +
                 "downward.  This is just the minimum stack size that will be allowed; the stack " +
                 "can actually grow larger. Use this symbol to check for overflow."));
        writer_.println("  __StackLimit = . ;");
        writer_.println("  /* Ensure stack size is properly aligned. */");
        writer_.println("  _min_stack_size = (_min_stack_size + 3) & 0x03 ;");
        writer_.println("  .stack ORIGIN(" + dataRegion + ") + LENGTH(" + dataRegion + ") - _min_stack_size :");
        writer_.println("  {");
        writer_.println("    _sstack = . ;");
        writer_.println("    . += _min_stack_size ;");
        writer_.println("    _estack = . ;");
        writer_.println("    __StackTop = . ;");
        writer_.println("  } >" + dataRegion);
        writer_.println("  PROVIDE(__stack = __StackTop);");
        writer_.println();
        writer_.println("  ASSERT((_estack - __StackLimit) >= _min_stack_size, \"Error: Not enough room for stack.\");");
        writer_.println();
    }

    /* Output sections related to ELF debugging info.
     */
    private void outputElfDebugSections() {
        writer_.println("    /* The .pdr section belongs in the absolute section */");
        writer_.println("    /DISCARD/ : { *(.pdr) }");
        writer_.println("  .gptab.sdata : { *(.gptab.data) *(.gptab.sdata) }");
        writer_.println("  .gptab.sbss : { *(.gptab.bss) *(.gptab.sbss) }");
        writer_.println("  .mdebug.abi32 0 : { KEEP(*(.mdebug.abi32)) }");
        writer_.println("  .mdebug.abiN32 0 : { KEEP(*(.mdebug.abiN32)) }");
        writer_.println("  .mdebug.abi64 0 : { KEEP(*(.mdebug.abi64)) }");
        writer_.println("  .mdebug.abiO64 0 : { KEEP(*(.mdebug.abiO64)) }");
        writer_.println("  .mdebug.eabi32 0 : { KEEP(*(.mdebug.eabi32)) }");
        writer_.println("  .mdebug.eabi64 0 : { KEEP(*(.mdebug.eabi64)) }");
        writer_.println("  .gcc_compiled_long32 : { KEEP(*(.gcc_compiled_long32)) }");
        writer_.println("  .gcc_compiled_long64 : { KEEP(*(.gcc_compiled_long64)) }");
        writer_.println("  /* Stabs debugging sections.  */");
        writer_.println("  .stab          0 : { *(.stab) }");
        writer_.println("  .stabstr       0 : { *(.stabstr) }");
        writer_.println("  .stab.excl     0 : { *(.stab.excl) }");
        writer_.println("  .stab.exclstr  0 : { *(.stab.exclstr) }");
        writer_.println("  .stab.index    0 : { *(.stab.index) }");
        writer_.println("  .stab.indexstr 0 : { *(.stab.indexstr) }");
        writer_.println("  .comment       0 : { *(.comment) }");
        writer_.println("  /* DWARF debug sections used by MPLAB X for source-level debugging. ");
        writer_.println("     Symbols in the DWARF debugging sections are relative to the beginning");
        writer_.println("     of the section so we begin them at 0.  */");
        writer_.println("  /* DWARF 1 */");
        writer_.println("  .debug          0 : { *.elf(.debug) *(.debug) }");
        writer_.println("  .line           0 : { *.elf(.line) *(.line) }");
        writer_.println("  /* GNU DWARF 1 extensions */");
        writer_.println("  .debug_srcinfo  0 : { *.elf(.debug_srcinfo) *(.debug_srcinfo) }");
        writer_.println("  .debug_sfnames  0 : { *.elf(.debug_sfnames) *(.debug_sfnames) }");
        writer_.println("  /* DWARF 1.1 and DWARF 2 */");
        writer_.println("  .debug_aranges  0 : { *.elf(.debug_aranges) *(.debug_aranges) }");
        writer_.println("  .debug_pubnames 0 : { *.elf(.debug_pubnames) *(.debug_pubnames) }");
        writer_.println("  /* DWARF 2 */");
        writer_.println("  .debug_info     0 : { *.elf(.debug_info .gnu.linkonce.wi.*) *(.debug_info .gnu.linkonce.wi.*) }");
        writer_.println("  .debug_abbrev   0 : { *.elf(.debug_abbrev) *(.debug_abbrev) }");
        writer_.println("  .debug_line     0 : { *.elf(.debug_line) *(.debug_line) }");
        writer_.println("  .debug_frame    0 : { *.elf(.debug_frame) *(.debug_frame) }");
        writer_.println("  .debug_str      0 : { *.elf(.debug_str) *(.debug_str) }");
        writer_.println("  .debug_loc      0 : { *.elf(.debug_loc) *(.debug_loc) }");
        writer_.println("  .debug_macinfo  0 : { *.elf(.debug_macinfo) *(.debug_macinfo) }");
        writer_.println("  /* SGI/MIPS DWARF 2 extensions */");
        writer_.println("  .debug_weaknames 0 : { *.elf(.debug_weaknames) *(.debug_weaknames) }");
        writer_.println("  .debug_funcnames 0 : { *.elf(.debug_funcnames) *(.debug_funcnames) }");
        writer_.println("  .debug_typenames 0 : { *.elf(.debug_typenames) *(.debug_typenames) }");
        writer_.println("  .debug_varnames  0 : { *.elf(.debug_varnames) *(.debug_varnames) }");
        writer_.println("  .debug_pubtypes 0 : { *.elf(.debug_pubtypes) *(.debug_pubtypes) }");
        writer_.println("  .debug_ranges   0 : { *.elf(.debug_ranges) *(.debug_ranges) }");
        writer_.println("  /DISCARD/ : { *(.rel.dyn) }");
        writer_.println("  .gnu.attributes 0 : { KEEP (*(.gnu.attributes)) }");
        writer_.println("  /DISCARD/ : { *(.note.GNU-stack) }");
        writer_.println("  /DISCARD/ : { *(.note.GNU-stack) *(.gnu_debuglink) *(.gnu.lto_*) *(.discard) }");
        writer_.println();
    }

    /* Output an interrupt vector section assuming that the device supports variable offset vectors.
     * This will set up the vector table so that the interrupt handler is always inline in the table.
     *
     * This differs from XC32, in which the user can use a GCC attribute to choose whether to use a
     * trampoline for each handler (like fixed offset devices) or to inline it.  Here, the user will 
     * not get a choice.
     */
    private void outputVariableOffsetVectors(InterruptList intList) {
        writer_.println("  .vectors _ebase_address + 0x200 :");
        writer_.println("  {");

        Utils.writeMultilineCComment(writer_, 4, 
                ("Symbol __vector_offset_n points to .vector_n if it exists, otherwise it points " +
                 "to the default handler. The startup code uses these values to set up the OFFxxx " +
                 "registers in the interrupt controller by referencing the init table found after " +
                 "this vectors section."));

        for(int vectorNum = 0; vectorNum <= intList.getLastVectorNumber(); ++vectorNum) {
            writer_.println("    . = ALIGN(4) ;");
            writer_.println("    KEEP(*(.vector_" + vectorNum + "))");
            writer_.println("    __vector_offset_" + vectorNum + " = (SIZEOF(.vector_" + vectorNum + ") > 0 ? (. - _ebase_address - SIZEOF(.vector_" + vectorNum + ")) : __vector_offset_default);");
        }

        writer_.println("    /* Default interrupt handler */");
        writer_.println("    . = ALIGN(4) ;");
        writer_.println("    __vector_offset_default = . - _ebase_address;");
        writer_.println("    KEEP(*(.vector_default))");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 4, 
                ("The offset registers hold a 17-bit offset, allowing a max value less than " +
                 "256*1024, so check for that here.  If you see this error, then one of your " +
                 "vectors is too large."));
        writer_.println("    ASSERT(__vector_offset_default < 256K, \"Error: Vector offset too large.\");");

        writer_.println("  } > kseg0_program_mem");
        writer_.println();

        /* Put the vector offset init table in kseg1_boot_mem_4B0 since there isn't much else there.
         */
        writer_.println("  .vector_offset_init");
        writer_.println("  {");
        writer_.println("    _vector_offset_init_begin = . ;");

        for(int vectorNum = 0; vectorNum <= intList.getLastVectorNumber(); ++vectorNum) {
            writer_.println("    LONG(__vector_offset_" + vectorNum + ");");
        }

        writer_.println("    _vector_offset_init_end = . ;");
        writer_.println("  } > kseg1_boot_mem_4B0");
        writer_.println();
    }

    /* Output an interrupt vector section assuming that the device use fixed offset vectors.
     * This will add instructions into the vector table to act as trampolines to the actual 
     * handlers.
     *
     * This differs from XC32, in which the user can use a GCC attribute to choose whether to use a
     * trampoline or to inline the handler (like on variable offset devices).  Here, the user will
     * not get a choice.
     */
    private void outputFixedOffsetVectors(InterruptList intList, TargetDevice target) {
        if(target.supportsMicroMipsIsa()  &&  !target.supportsMips32Isa()) {
            writer_.println("  /* j (.vector_n >> 1)");
            writer_.println("   * ssnop");
            writer_.println("   */");

            for(int vectorNum = 0; vectorNum <= intList.getLastVectorNumber(); ++vectorNum) {
                writer_.println("  .vector_dispatch_" + vectorNum + " _ebase_address + 0x200 + ((_vector_spacing << 3) * " + vectorNum + ") :");
                writer_.println("  {");
                writer_.println("    __vector_target_" + vectorNum + " = (SIZEOF(.vector_ " + vectorNum + ") > 0 ? ADDR(.vector_ " + vectorNum + ") : ADDR(.vector_default));");
                writer_.println("     LONG(0xD4000000 | ((__vector_target_ " + vectorNum + " >> 1) & 0x03FFFFFF));");
                writer_.println("     LONG(0x00000800);");
                writer_.println("  } > exception_mem");
            }
        } else {
            writer_.println("  /* lui k0, %hi(.vector_n)");
            writer_.println("   * ori k0, k0, %lo(.vector_n)");
            writer_.println("   * jr k0");
            writer_.println("   * ssnop");
            writer_.println("   */");

            for(int vectorNum = 0; vectorNum <= intList.getLastVectorNumber(); ++vectorNum) {
                writer_.println("  .vector_dispatch_" + vectorNum + " _ebase_address + 0x200 + ((_vector_spacing << 5) * " + vectorNum + ") :");
                writer_.println("  {");
                writer_.println("    __vector_target_" + vectorNum + " = (SIZEOF(.vector_ " + vectorNum + ") > 0 ? ADDR(.vector_ " + vectorNum + ") : ADDR(.vector_default));");
                writer_.println("     LONG(0x3C1A0000 | ((__vector_target_ " + vectorNum + " >> 16) & 0xFFFF));");
                writer_.println("     LONG(0x375A0000 | ((__vector_target_ " + vectorNum + ") & 0xFFFF));");
                writer_.println("     LONG(0x03400008);");
                writer_.println("     LONG(0x00000040);");
                writer_.println("  } > exception_mem");
            }
        }

        writer_.println();
    }
}
