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

import java.util.List;

/**
 * A subclass of the LinkerScriptBuilder that handles ARM Cortex-M devices.
 */
public class CortexMLinkerScriptBuilder extends LinkerScriptBuilder {
    
    public CortexMLinkerScriptBuilder(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        createNewLinkerFile(target);

        clearMemoryRegions();
        populateMemoryRegions(target);

        outputLicenseHeader();
        outputPreamble();
        outputMemoryRegionCommand();

        writer_.println("__rom_end = ORIGIN(rom) + LENGTH(rom);");
        writer_.println("__ram_end = ORIGIN(ram) + LENGTH(ram);");
        writer_.println();

        writer_.println("SECTIONS");
        writer_.println("{");

        outputVectorsSection();
        outputTextSection();
        outputArmStackUnwindSection();
        outputDataSections(target.hasL1Cache());
        outputRuntimeMemorySections();

        writer_.println("  . = ALIGN(4);");
        writer_.println("  _end = . ;");
        writer_.println("  _ram_end_ = ORIGIN(ram) + LENGTH(ram) -1 ;");

        outputElfDebugSections();

        writer_.println("}");

        closeLinkerFile();
    }

    /* Walk through the list of all target regions and add the ones that the linker script needs,
     * renaming them along the way to match XC32 (or Atmel GCC).
     */
    private void populateMemoryRegions(TargetDevice target) {
        List<LinkerMemoryRegion> targetRegions = target.getMemoryRegions();

        for(LinkerMemoryRegion region : targetRegions) {
            switch(region.getType()) {
                case CODE:
                    if(region.getName().equalsIgnoreCase("IFLASH")) {
                        region.setName("rom");
                        region.setAccess(LinkerMemoryRegion.EXEC_ACCESS | LinkerMemoryRegion.READ_ACCESS);
                        addMemoryRegion(region);
                    } else if(region.getName().equalsIgnoreCase("ITCM")) {
                        region.setName("itcm");
                        region.setAccess(LinkerMemoryRegion.ALL_ACCESS);
                        addMemoryRegion(region);
                    }
                    break;
                case SRAM:
                    if(region.getName().equalsIgnoreCase("IRAM")  ||  region.getName().equalsIgnoreCase("HSRAM")) {
                        region.setName("ram");
                        region.setAccess(LinkerMemoryRegion.ALL_ACCESS);
                        addMemoryRegion(region);
                    } else if(region.getName().equalsIgnoreCase("DTCM")) {
                        region.setName("dtcm");
                        region.setAccess(LinkerMemoryRegion.ALL_ACCESS);
                        addMemoryRegion(region);
                    }
                    break;
                case EBI:
                case SQI:
                case SDRAM:
                    region.setName(region.getName().toLowerCase());
                    addMemoryRegion(region);
                    break;
                default:
                    break;
            }
        }
    }

    /* Output symbol definitions and commands that are set at the top of the linker script before
     * any other regions or sections are defined.
     */
    private void outputPreamble() {
        writer_.println("OUTPUT_FORMAT(\"elf32-littlearm\", \"elf32-littlearm\", \"elf32-littlearm\")");
        writer_.println("OUTPUT_ARCH(arm)");
        writer_.println("SEARCH_DIR(.)");
        writer_.println();

        writer_.println("ENTRY(Reset_Handler)");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 0, 
                ("Provide for a minimum stack and heap size; these can be overridden using the " +
                 "linker\'s --defsym option on the command line."));
        writer_.println("EXTERN (_min_stack_size _min_heap_size)");
        writer_.println("PROVIDE(_min_stack_size = 0x400);");
        writer_.println("PROVIDE(_min_heap_size = 0);");
        writer_.println();
        writer_.println();
    }

    /* Output a section containing the interrupt vector table.
     */
    private void outputVectorsSection() {
        writer_.println("  .vectors :");
        writer_.println("  {");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    _sfixed = .;");
        writer_.println("    __svectors = .;");
        writer_.println("    KEEP(*(.vectors .vectors.* .vectors_default .vectors_default.*))");
        writer_.println("    KEEP(*(.isr_vector))");
        writer_.println("    KEEP(*(.reset*))");
        writer_.println("    KEEP(*(.after_vectors))");
        writer_.println("    __evectors = .;");
        writer_.println("  } > rom");
        writer_.println();
    }

    /* Output the .text section, which contains the program code and data initialization code.
     */
    private void outputTextSection() {
        writer_.println("  .text :");
        writer_.println("  {");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    *(.text .text.* .gnu.linkonce.t.*)");
        writer_.println("    *(.glue_7t) *(.glue_7)");
        writer_.println("    *(.rodata .rodata* .gnu.linkonce.r.*)");
        writer_.println("    *(.ARM.extab* .gnu.linkonce.armextab.*)");
        writer_.println();
        writer_.println("    /* Support C constructors, and C destructors in both user code");
        writer_.println("       and the C library. This also provides support for C++ code. */");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    KEEP(*(.init))");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    __preinit_array_start = .;");
        writer_.println("    KEEP (*(.preinit_array))");
        writer_.println("    __preinit_array_end = .;");
        writer_.println();
        writer_.println("    . = ALIGN(4);");
        writer_.println("    __init_array_start = .;");
        writer_.println("    KEEP (*(SORT(.init_array.*)))");
        writer_.println("    KEEP (*(.init_array))");
        writer_.println("    __init_array_end = .;");
        writer_.println();
        writer_.println("    . = ALIGN(0x4);");
        writer_.println("    KEEP (*crtbegin.o(.ctors))");
        writer_.println("    KEEP (*(EXCLUDE_FILE (*crtend.o) .ctors))");
        writer_.println("    KEEP (*(SORT(.ctors.*)))");
        writer_.println("    KEEP (*crtend.o(.ctors))");
        writer_.println();
        writer_.println("    . = ALIGN(4);");
        writer_.println("    KEEP(*(.fini))");
        writer_.println();
        writer_.println("    . = ALIGN(4);");
        writer_.println("    __fini_array_start = .;");
        writer_.println("    KEEP (*(.fini_array))");
        writer_.println("    KEEP (*(SORT(.fini_array.*)))");
        writer_.println("    __fini_array_end = .;");
        writer_.println();
        writer_.println("    KEEP (*crtbegin.o(.dtors))");
        writer_.println("    KEEP (*(EXCLUDE_FILE (*crtend.o) .dtors))");
        writer_.println("    KEEP (*(SORT(.dtors.*)))");
        writer_.println("    KEEP (*crtend.o(.dtors))");
        writer_.println();
        writer_.println("    . = ALIGN(4);");
        writer_.println();
        writer_.println("    KEEP(*(.eh_frame*))");
        writer_.println();
        writer_.println("    _efixed = .;            /* End of text section */");
        writer_.println("  } > rom");
        writer_.println();
    }

    /* Output a section that is, according to Stack Overflow, used for stack unwind tables.
     */
    private void outputArmStackUnwindSection() {
        writer_.println("  /* .ARM.exidx is sorted, so has to go in its own output section.  */");
        writer_.println("  PROVIDE_HIDDEN (__exidx_start = .);");
        writer_.println("  .ARM.exidx :");
        writer_.println("  {");
        writer_.println("    *(.ARM.exidx* .gnu.linkonce.armexidx.*)");
        writer_.println("  } > rom");
        writer_.println("  PROVIDE_HIDDEN (__exidx_end = .);");
        writer_.println();
        writer_.println("  _etext = ALIGN(4);");
        writer_.println("  __etext = _etext;");
        writer_.println();
    }

    /* Output sections for data as opposed to code.
    */
    private void outputDataSections(boolean hasCache) {
        writer_.println("  .relocate : AT (_etext)");
        writer_.println("  {");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    _srelocate = .;");
        writer_.println("    __data_start__ = .;");
        if(hasCache) {
            writer_.println("    /* For data that should bypass the cache (eg. will be accessed by DMA). */");
            writer_.println("    /* User code must configure the MPU for this section. */");
            writer_.println("    __uncached_data_start__ = .;");
            writer_.println("    *(.uncacheddata .uncacheddata.*)");
            writer_.println("    . = ALIGN(. - _suncacheddata <= 32 ? 32 : 1 << LOG2CEIL(. - _suncacheddata));");
            writer_.println("    __uncached_data_end__ = .;");
        }
        writer_.println("    *(.ramfunc .ramfunc.*);");
        writer_.println("    *(.data .data.*);");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    __data_end__ = .;");
        writer_.println("    _erelocate = .;");
        writer_.println("  } > ram");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 2, 
                ("Use the \'section\' attribute to put data in this section that you want to " +
                 "persist through software resets."));
        writer_.println("  .persist (NOLOAD) :");
        writer_.println("  {");
        if(hasCache) {
            writer_.println("    /* Ensure normal and persistent sections do not overlap 32-byte cache line. */");
            writer_.println("    . = ALIGN(32) ;");
        }
        writer_.println("    _persist_begin = .;");
        writer_.println("    __persist_start__ = .;");
        writer_.println("    *(.persist .persist.*)");
        writer_.println("    *(.pbss .pbss.*)");
        if(hasCache) {
            writer_.println("    . = ALIGN(32) ;");
        } else {
            writer_.println("    . = ALIGN(4) ;");
        }
        writer_.println("    __persist_end__ = .;");
        writer_.println("    _persist_end = .;");
        writer_.println("  } > ram");
        writer_.println();
        
        writer_.println("  .bss (NOLOAD) :");
        writer_.println("  {");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    __bss_start__ = .;");
        writer_.println("    _sbss = . ;");
        writer_.println("    _szero = .;");
        writer_.println("    *(.bss .bss.*);");
        writer_.println("    *(COMMON)");
        writer_.println("    . = ALIGN(4);");
        writer_.println("    __bss_end__ = .;");
        writer_.println("    _ebss = . ;");
        writer_.println("    _ezero = .;");
        writer_.println("  } > ram");
        writer_.println();
    }

    /* Output sections for a heap and stack.  The heap size is fixed to _min_heap_size, which is 0
     * by default and settable on the command-line.  The stack is at least _min_stack_size, but can
     * actually grow as long as there's room.
     */
    private void outputRuntimeMemorySections() {
        writer_.println("  .heap :");
        writer_.println("  {");
        writer_.println("    . = ALIGN(8) ;");
        writer_.println("    _sheap = . ;");
        writer_.println("    . += _min_heap_size ;");
        writer_.println("    . = ALIGN(8) ;");
        writer_.println("    _eheap = . ;");
        writer_.println("    __HeapLimit = . ;");
        writer_.println("  } > ram");
        writer_.println();

        Utils.writeMultilineCComment(writer_, 2,
                ("Allocate some space for a stack at the end of memory because the stack grows " +
                 "downward.  This is just the minimum stack size that will be allowed; the stack " +
                 "can actually grow larger. Use this symbol to check for overflow."));
        writer_.println("  __StackLimit = . ;");
        writer_.println("  /* Ensure stack size is properly aligned. */");
        writer_.println("  _min_stack_size = (_min_stack_size + 7) & 0x07 ;");
        writer_.println("  .stack ORIGIN(ram) + LENGTH(ram) - _min_stack_size :");
        writer_.println("  {");
        writer_.println("    . = ALIGN(8) ;");
        writer_.println("    _sstack = . ;");
        writer_.println("    . += _min_stack_size ;");
        writer_.println("    _estack = . ;");
        writer_.println("    __StackTop = . ;");
        writer_.println("  } > ram");
        writer_.println("  PROVIDE(__stack = __StackTop);");
        writer_.println();
        writer_.println("  ASSERT((_estack - __StackLimit) >= _min_stack_size, \"Error: Not enough room for stack.\");");
        writer_.println();
    }

    /* Output sections related to ELF debugging info.
     */
    private void outputElfDebugSections() {
        // Do nothing for now; XC32 linker scripts do not have these sections.
    }
}
