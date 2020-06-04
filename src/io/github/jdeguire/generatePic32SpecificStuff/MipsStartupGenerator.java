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

import com.microchip.crownking.mplabinfo.FamilyDefinitions;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * This class will generate the C startup code for MIPS devices.  This is not strictly necessary
 * because there is not much variation in how the PIC32 MIPS devices are initialized, but this
 * provides symmetry with the Arm startup code and does allow some customization based on device
 * features, such as FPU or TLB support.
 */
public class MipsStartupGenerator {

    /* Just a simple data structure for hold TLB entries for MIPS devices.
     */
    private class TlbEntry {
        public String name;
        public long entryLo0;
        public long entryLo1;
        public long entryHi;
        public long pageMask;
    }

    /* This is used to help initialize the PIC32's PRISS register, which is present on only some
     * devices and is used to map interrupt priority level to shadow register set.  Right now, only
     * the PIC32MZ--with 8 register sets--and the PIC32MK--with 2 register sets--have this feature.
     */
    private static final String[] PRISS_INIT = {"0x00000000", "0x00000000", "0x10000000",
                                                "0x21000000", "0x32100000", "0x43210000",
                                                "0x54321000", "0x65432100", "0x76543210"};

    private final String basepath_;

    /* Create a new MipsStartupGenerator object that can create startup files for multiple 
     * MIPS devices.  The files are source files named as "startup_<device_name>.c".  Each file
     * will be put into a subdirectory under the given path whose name is the full name of the 
     * device in lower-case.
     */
    public MipsStartupGenerator(String basePath) {
        basepath_ = basePath;
    }

    /* Generate a startup file for the given device if it is an Arm Cortex-M device; that is, it 
     * supports the Thumb ISA but not the full Arm ISA.
     */
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        if(target.isMips32()) {
            String devnameForFile = target.getDeviceName().toLowerCase();
            String targetPath = basepath_ + "/" + devnameForFile + "/startup_" + devnameForFile + ".c";

            try(PrintWriter writer = Utils.createUnixPrintWriter(targetPath)) {
                outputLicenseHeader(writer);
                outputPreamble(writer, target);
                outputUserFunctionDeclarations(writer);
                outputDummyHandler(writer);

                if(target.getInterruptList().getNumShadowRegs() >= 2) {
                    outputShadowRegGlobalPointerInitFunction(writer);
                }

                if(FamilyDefinitions.SubFamily.PIC32MZ == target.getSubFamily()) {
                    outputTlbInitFunction(writer, target);
                }

                outputDataInitFunction(writer);

                if(target.hasL1Cache()) {
                    outputL1CacheInitFunction(writer);
                }

                outputCp0InitFunction(writer, target);
                outputVectorSpacingInitFunction(writer);

                if(target.getInterruptList().usesVariableOffsets()) {
                    outputVariableVectorOffsetInitFunction(writer);
                }

                outputLibcInitArrayFunction(writer);
                outputStartupFunction(writer, target);
                outputResetFunction(writer, target);

                outputBootstrapExceptionEntryPoint(writer);
                outputGeneralExceptionEntryPoint(writer);

                if(FamilyDefinitions.SubFamily.PIC32MZ == target.getSubFamily()) {
                    outputSimpleTlbRefillExceptionEntryPoint(writer);
                }

                if(target.hasL1Cache()) {
                    outputCacheErrorExceptionEntryPoint(writer);
                }
            }
        }
    }

    /* Add a permissive license header to the startup file opened by the given writer.
     */
    private void outputLicenseHeader(PrintWriter writer) {
        String header = (Utils.generatedByString() + "\n\n" +
                         Utils.generatorLicenseString() + "\n\n" +
                         "                                               ******\n\n" + 
                         "This file is generated based on source files included with Microchip " +
                         "Technology's XC32 toolchain.  Microchip's license is reproduced below:\n\n" +
                         Utils.microchipBsdLicenseString());

        Utils.writeMultilineCComment(writer, 0, header);
        writer.println();
    }

    /* Output initial stuff such as linker symbol declarations and header files.
     */
    private void outputPreamble(PrintWriter writer, TargetDevice target) {
        writer.println("#include <stdint.h>");
        writer.println("#include <xc.h>");
        writer.println();

        writer.println("/* Symbols defined in the linker script for this device. */");
        writer.println("extern uint32_t _ebase_address;");
        writer.println("extern uint32_t _etext;");
        writer.println("extern uint32_t _srelocate;");
        writer.println("extern uint32_t _erelocate;");
        writer.println("extern uint32_t _szero;");
        writer.println("extern uint32_t _ezero;");
        writer.println("extern uint32_t _estack;");
        writer.println("extern uint32_t _gp;");
        writer.println("extern uint32_t _vector_spacing;");

        if(target.getInterruptList().usesVariableOffsets()) {
            writer.println("extern uint32_t _vector_offset_init_begin;");
            writer.println("extern uint32_t _vector_offset_init_end;");
        }

        writer.println("extern void (*__preinit_array_start)(void);");
        writer.println("extern void (*__preinit_array_end)(void);");
        writer.println("extern void (*__init_array_start)(void);");
        writer.println("extern void (*__init_array_end)(void);");
        writer.println();

        writer.println("extern void _init(void);");
        writer.println("extern int main(void);");
        writer.println("extern void exit(int status);");
        writer.println();
        writer.println("/* This is the default interrupt handler. */");
        writer.println("void __attribute__((weak, nomips16, interrupt(\"eic\"), section(\".vector_default\"))) Default_Handler(void);");
        writer.println("void __attribute__((noreturn, nomips16, section(\".reset.startup\"))) _reset_startup(void);");
        writer.println();
    }

    /* Output weak declarations of functions the user can use to run code during startup.  Weak
     * declarations do not have to be defined and will be 0 if they are not defined by the user.
     */
    private void outputUserFunctionDeclarations(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Define these to run code during startup.  The _on_reset() function is run almost "
              + "immediately, so the cache, DSPr2 ASE, and FPU will probably not be usable unless "
              + "they are enabled in _on_reset().  The _on_bootstrap() function is run just before "
              + "main is called and so everything should be initialized.");
        writer.println("extern void __attribute__((weak, long_call)) _on_reset(void);");
        writer.println("extern void __attribute__((weak, long_call)) _on_bootstrap(void);");
        writer.println();
        writer.println();
    }

    /* Output the definition for the dummy handler, which is the default interrupt and fault handler
     * function.  This will have just an infinite loop in it.
     */
    private void outputDummyHandler(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "A dummy interrupt handler that does nothing.  This is the default handler for " +
                "any unpopulated interrupt vector.");
        writer.println("void Default_Handler(void)");
        writer.println("{");
        writer.println("    while(1) {}");
        writer.println("}");
        writer.println();
    }

    /* Output a function that will initialize the global pointer register on each shadow register
     * set.
     */
    private void outputShadowRegGlobalPointerInitFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0,
                "Copy the current gp register (Global Pointer) value to the gp registers in the "
              + "back-end shadow register sets.");

        writer.println("void __attribute__((weak, nomips16)) _InitShadowRegisterGp(void)");
        writer.println("{");
        writer.println("    uint32_t prev_srsctl = __builtin_mfc0(12, 2);");
        writer.println("    uint32_t which_srs = (prev_srsctl >> 26) & 0x0F;       /* SRSCtl.HSS */");
        writer.println();
        writer.println("    /* Stop at SRS 0 because that is the base set from which gp is copied. */");
        writer.println("    uint32_t srsctl = prev_srsctl;");
        writer.println("    for( ; which_srs > 0; --which_srs)");
        writer.println("    {");
        writer.println("        srsctl &= ~0x03C0;             /* Clear SRSCtl.PSS */");
        writer.println("        srsctl |= (which_srs << 6);    /* Set SRSCtl.PSS to new SRS value */");
        writer.println();
        writer.println("        __builtin_mtc0(12, 2, srsctl);");
        writer.println("        _ehb();");
        writer.println("        __asm__ volatile(\"wrpgpr gp, gp\" : : : \"memory\");");
        writer.println("    }");
        writer.println();
        writer.println("    /* Restore original SRSCtl value */");
        writer.println("    __builtin_mtc0(12, 2, prev_srsctl);");
        writer.println("    _ehb();");
        writer.println("}");
        writer.println();
    }

    /* Output a function to initialize the PIC32 TLB to point to the SQI and EBI memory regions.
     */
    private void outputTlbInitFunction(PrintWriter writer, TargetDevice target) {
        ArrayList<TlbEntry> tlbEntries = new ArrayList<>();

        // Look for the SQI and EBI memory regions and get TLB entries for them.  Memory regions as
        // read from the TargetDevice class (which just calls the MPLAB X API) use physical addresses.
        // On the current PIC32MZ devices, there is only one of each region.
        for(LinkerMemoryRegion lmr : target.getMemoryRegions()) {
            if(LinkerMemoryRegion.Type.SQI == lmr.getType()  ||
               LinkerMemoryRegion.Type.EBI == lmr.getType()) {
                // We need to be able to access the region from both kseg2 and kseg3, so get
                // entries for both.
                lmr.setAsKseg2Region();
                tlbEntries.add(getTlbEntryForMemoryRegion(lmr));
                lmr.setAsKseg3Region();
                tlbEntries.add(getTlbEntryForMemoryRegion(lmr));
            }
        }

        Utils.writeMultilineCComment(writer, 0,
                "Initialize the TLB to point to the SQI and EBI memory regions.");

        writer.println("void __attribute__((weak, nomips16)) _InitTlbForEbiSqi(void)");
        writer.println("{");
        writer.println("    /* Check Config<MT> to see if we have a TLB and bail if not. */");
        writer.println("    if(0x80 != (__builtin_mfc0(16, 0) & 0x0380))");
        writer.println("        return;");
        writer.println();

        Utils.writeMultilineCComment(writer, 4,
                "Init all of the TLB entries by writing unmapped kseg0 address to them so that they "
              + "never match.  The MIPS Architecture For Programmers Vol. III document shows a "
              + "similar (and actually more thorough) routine.");
        writer.println("    /* Get number of TLB entries available from Config1<MMU Size> */");
        writer.println("    uint32_t mmu_size = ((__builtin_mfc0(16, 1) >> 25) & 0x3F) + 1;");
        writer.println("    uint32_t kseg0_addr = 0x80000000");
        writer.println();
        writer.println("    __builtin_mtc0(2, 0, 0);      /* EntryLo0 */");
        writer.println("    __builtin_mtc0(3, 0, 0);      /* EntryLo1 */");
        writer.println("    __builtin_mtc0(5, 0, 0);      /* PageMask */");
        writer.println();
        writer.println("    while(mmu_size--)");
        writer.println("    {");
        writer.println("        __builtin_mtc0(0, 0, mmu_size);          /* Index */");
        writer.println("        __builtin_mtc0(10, 0, kseg0_addr);       /* EntryHi */");
        writer.println("        _ehb();");
        writer.println("        __asm__ volatile(\"tlbwi\" : : : \"memory\");");
        writer.println("        kseg0_addr += (8 * 1024);                /* PageMask of 0 gives two 4kB pages */");
        writer.println("    }");
        writer.println();
        writer.println("    /* Clear PageGrain */");
        writer.println("    __builtin_mtc0(5, 1, 0);");
        writer.println("    /* Set Wired so our entries are non-replaceable. */");
        writer.println("    __builtin_mtc0(6, 0, " + tlbEntries.size() + ");");
        writer.println("    _ehb();");
        writer.println();

        Utils.writeMultilineCComment(writer, 4,
                "Now set up the TLB entries for the SQI and EBI regions by setting the CP0 Index.");

        int index = 0;
        for(TlbEntry entry : tlbEntries) {
            writer.println("    /* " + entry.name + " */");
            writer.println("    __builtin_mtc0(0,  0, " + index + ");");
            writer.println("    __builtin_mtc0(2,  0, 0x" + Long.toHexString(entry.entryLo0).toUpperCase() + ");");
            writer.println("    __builtin_mtc0(3,  0, 0x" + Long.toHexString(entry.entryLo1).toUpperCase() + ");");
            writer.println("    __builtin_mtc0(5,  0, 0x" + Long.toHexString(entry.pageMask).toUpperCase() + ");");
            writer.println("    __builtin_mtc0(10, 0, 0x" + Long.toHexString(entry.entryHi).toUpperCase() + ");");
            writer.println("    _ehb();");
            writer.println("    __asm__ volatile(\"tlbwi\" : : : \"memory\");");
            writer.println();
            ++index;
        }

        writer.println("}");
        writer.println();
    }

    /* Return a single TlbEntry that can cover the given region.  This assumes that the region size
     * is a power of two because that is what the MIPS page sizes allow.
     */
    private TlbEntry getTlbEntryForMemoryRegion(LinkerMemoryRegion lmr) {
        TlbEntry entry = new TlbEntry();

        long lmrLength = lmr.getLength();
        boolean isKseg2Region = lmr.isKseg2Region();
        boolean needBothPages = false;

        // Detemine the page mask from the size of the region.
        long msb = 63 - Long.numberOfLeadingZeros(lmrLength);
        if(0x00 == (msb & 0x01)) {
            // If even, we need to use both pages in the entry with each page covering half.
            // Otherwise, just one of the pages will cover the region.
            needBothPages = true;
            --msb;
        }

        long mask = (1 << msb) - 1;

        long entryLo0Addr = lmr.getPhysicalStartAddress() & ~mask;
        long entryLo0Flags = 0x07;          // (D)irty, (V)alid, and (G)lobal bits set
        long entryLo1Addr;
        long entryLo1Flags;

        if(needBothPages) {
            // Split region in half to cover both pages and use same flags for both pages.
            entryLo1Addr = entryLo0Addr + (lmrLength / 2);
            entryLo1Flags = entryLo0Flags;
        } else {
            // Move EntryLo1 Address just past region and clear (V)alid flag on entryLo1.
            entryLo1Addr = entryLo0Addr + lmrLength;
            entryLo1Flags = entryLo0Flags & ~0x02;
        }

        long cacheableFlag;

        if(isKseg2Region) {
            entry.name = "kseg2_" + lmr.getName();
            cacheableFlag = 0x18;      // set as cacheable
        } else {
            entry.name = "kseg3_" + lmr.getName();
            cacheableFlag = 0x10;      // set as not cacheable
        }

        entry.entryLo0 = entryLo0Addr | entryLo0Flags | cacheableFlag;
        entry.entryLo1 = entryLo1Addr | entryLo1Flags | cacheableFlag;
        entry.entryHi = lmr.getStartAddress() & ~mask;
        entry.pageMask = mask & 0x1FFFE000;      // mask the bits implemented in the PageMask register

        return entry;
    }

    /* Output a function that will initialize the .data and and .bss sections as well as other
     * sections like them.  That is, any sections with either data that needs relocating from flash
     * to RAM or data that just needs zeroing out will be initialzed.  This uses linker script
     * symbols to determine the location of these sections.
     */
    private void outputDataInitFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Initialize data memory regions that either need their data copied from flash to "
              + "RAM or need their data zeroed out.  This uses symbols from the linker script to "
              + "do this.");

        writer.println("void __attribute__((weak)) _InitDataRegions(void)");
        writer.println("{");
        writer.println("    /* Copy variable init data from flash to RAM. */");
        writer.println("    uint32_t *src_ptr = &_etext;");
        writer.println("    uint32_t *dst_ptr = &_srelocate;");
        writer.println("    while(dst_ptr < &_erelocate)");
        writer.println("        *dst_ptr++ = *src_ptr++;");
        writer.println();
        writer.println("    /* Clear uninitialized variables in the .bss and .sbss sections. */");
        writer.println("    uint32_t *bss_ptr = &_szero;");
        writer.println("    while(bss_ptr < &_ezero)");
        writer.println("        *bss_ptr++ = 0;");
        writer.println("}");
        writer.println();
    }

    /* Output a function that will initialize the L1 cache on devices that have it.
     */
    private void outputL1CacheInitFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, "Initialize the L1 cache.");

        writer.println("void __attribute__((weak, nomips16, section(\".cache_init\"))) _InitL1Cache(void)");
        writer.println("{");
        writer.println("    uint32_t cp0_config  = __builtin_mfc0(16, 0);");
        writer.println("    uint32_t cp0_config1 = __builtin_mfc0(16, 1);");
        writer.println();
        writer.println("    /* Ensure cache is disabled while here for kseg0. */");
        writer.println("    cp0_config &= ~0x07;");
        writer.println("    __builtin_mtc0(16, 0, cp0_config | 0x02);");
        writer.println("    _ehb();");
        writer.println();
        writer.println("    __builtin_mtc0(26, 0, 0);     /* ErrCtl */");
        writer.println("    __builtin_mtc0(28, 0, 0);     /* TagLo */");
        writer.println("    /* TagHi is not implemented on the PIC32. */");
        writer.println("    _ehb();");
        writer.println();
        writer.println("    uint32_t cache_sets_per_way;");
        writer.println("    uint32_t cache_line_size;");
        writer.println("    uint32_t cache_ways;");
        writer.println("    uint32_t cache_total_size;");
        writer.println();

        Utils.writeMultilineCComment(writer, 4, "Invalidate instruction cache.");
        writer.println("    cache_sets_per_way = (cp0_config1 >> 22) & 0x07;       /* Config1<IS> */");
        writer.println("    if(0x07 == cache_sets_per_way)");
        writer.println("        cache_sets_per_way = 32;");
        writer.println("    else");
        writer.println("        cache_sets_per_way = 64 << cache_sets_per_way;");
        writer.println();
        writer.println("    cache_line_size = (cp0_config1 >> 19) & 0x07;          /* Config1<IL> */");
        writer.println("    if(0 != cache_line_size)");
        writer.println("        cache_line_size = 2 << cache_line_size;");
        writer.println();
        writer.println("    cache_ways = (cp0_config1 >> 16) & 0x07;               /* Config1<IA> */");
        writer.println("    ++cache_ways;");
        writer.println();
        writer.println("    cache_total_size = cache_sets_per_way * cache_ways * cache_line_size;");
        writer.println();
        writer.println("    if(cache_total_size > 0)");
        writer.println("    {");
        writer.println("        uint32_t idx_addr = 0x9D000000;      /* Need unmapped address for cache instruction */");
        writer.println("        uint32_t end_addr = idx_addr + cache_total_size;");
        writer.println();
        writer.println("        for( ; idx_addr < end_addr; idx_addr += cache_line_size)");
        writer.println("        {");
        writer.println("            __asm__ volatile(\"cache 0x08, 0(%0)\")");
        writer.println("                             : /* no outputs */");
        writer.println("                             : \"r\" (idx_addr)");
        writer.println("                             : \"memory\");");
        writer.println("        }");
        writer.println("    }");
        writer.println();

        Utils.writeMultilineCComment(writer, 4, "Invalidate data cache.");
        writer.println("    cache_sets_per_way = (cp0_config1 >> 13) & 0x07;       /* Config1<DS> */");
        writer.println("    if(0x07 == cache_sets_per_way)");
        writer.println("        cache_sets_per_way = 32;");
        writer.println("    else");
        writer.println("        cache_sets_per_way = 64 << cache_sets_per_way;");
        writer.println();
        writer.println("    cache_line_size = (cp0_config1 >> 10) & 0x07;          /* Config1<DL> */");
        writer.println("    if(0 != cache_line_size)");
        writer.println("        cache_line_size = 2 << cache_line_size;");
        writer.println();
        writer.println("    cache_ways = (cp0_config1 >> 7) & 0x07;                /* Config1<DA> */");
        writer.println("    ++cache_ways;");
        writer.println();
        writer.println("    cache_total_size = cache_sets_per_way * cache_ways * cache_line_size;");
        writer.println();
        writer.println("    if(cache_total_size > 0)");
        writer.println("    {");
        writer.println("        uint32_t idx_addr = 0x80000000;          /* Need unmapped address for cache instruction */");
        writer.println("        uint32_t end_addr = idx_addr + cache_total_size;");
        writer.println();
        writer.println("        for( ; idx_addr < end_addr; idx_addr += cache_line_size)");
        writer.println("        {");
        writer.println("            __asm__ volatile(\"cache 0x09, 0(%0)\")");
        writer.println("                             : /* no outputs */");
        writer.println("                             : \"r\" (idx_addr)");
        writer.println("                             : \"memory\");");
        writer.println("        }");
        writer.println("    }");
        writer.println();

        writer.println("    /* Require completion of pending memory transactions before enabling cache. */");
        writer.println("    __asm__ volatile(\"sync\" : : : \"memory\");");
        writer.println("}");
        writer.println();
    }

    /* Output a function to initialize various CP0 registers.
     */
    private void outputCp0InitFunction(PrintWriter writer, TargetDevice target)
    {
        Utils.writeMultilineCComment(writer, 0, "Initialize CP0 registers.");

        writer.println("void __attribute__((weak, nomips16)) _InitCp0Registers(void)");
        writer.println("{");
        writer.println("    __builtin_mtc0(9,  0, 0);                    /* Clear Count */");
        writer.println("    __builtin_mtc0(11, 0, 0xFFFFFFFF);           /* Set Compare to max value */");
        writer.println("    __builtin_mtc0(15, 1, _ebase_address);       /* Set Ebase to exception base address */");
        writer.println();

        Utils.writeMultilineCComment(writer, 4,
                "Set Cause register to enable vectored interrupts (IV=1), enable Core Timer (DC=0), "
              + "and clear out software interrupt pending flags (IPn=0).");
        writer.println("    __builtin_mtc0(13, 0, 0x00800000);");
        writer.println();

        Utils.writeMultilineCComment(writer, 4,
                "Set up the Status register by:\n"
                        + "  --setting processor into kernel mode (UM=0)\n"
                        + "  --clearing the exception (EXL=0) and error (ERL=0) bits\n"
                        + "  --disabling interrupts (IE=0)\n"
                        + "  --setting the CPU interrupt priority level to 0 (IPLn=0)\n"
                        + "  --disabling endian swap in User Mode (RE=0)\n"
                        + "  --disabling CP0 access in User Mode (CU0=0)\n"
                        + "  --enabling FPU for devices that support it (FR=1, CU1=1)\n"
                        + "  --enabling DSPr2 ASE for devices that support it (MX=1)\n"
              + "The XC32 startup code also checks for CorExtend support, but no PIC32 devices "
              + "support it, so it is not checked here.  The BEV, SR, and NMI flags are preserved.");
        writer.println("    uint32_t status = __builtin_mfc0(12, 0) & 0x00580000;");
        if(target.hasFpu()) {
            writer.println("    status |= 0x24000000;     /* Enable 64-bit FPU (CU1=1, FR=1) */");   
        }
        if(target.supportsDspR2Ase()) {
            writer.println("    status |= 0x01000000;     /* Enable DSPr2 ASE (MX=1) */");
        }
        writer.println("    __builtin_mtc0(12, 0, status);");

        if(FamilyDefinitions.SubFamily.PIC32WK == target.getSubFamily()) {
            writer.println();
            Utils.writeMultilineCComment(writer, 4,
                    "XC32 sets the Config3<ISAONEXEC> flag for the PIC32WK, so do it here too.");
            writer.println("    __builtin_mtc0(16, 3, __builtin_mfc0(16, 3) | (1 << 16));");        
        }

        writer.println("    _ehb();");
        writer.println("}");
        writer.println();
    }

    /* Output a function to set up the vector spacing on the PIC32 using IntCtl.VS.  This also sets 
     * up the INTCON.VS field on PIC32MM devices, which essentially overrides the IntCtl.VS with 
     * smaller spacing for the low-memory microMIPS devices.
     */
    private void outputVectorSpacingInitFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Initialize the vector spacing registers on the device and enable multi-vector mode.");

        writer.println("void __attribute__((weak, nomips16)) _InitVectorSpacing(void)");
        writer.println("{");
        Utils.writeMultilineCComment(writer, 4,
                "Some PIC32 devices override the MIPS vector spacing with their own.  In particular,"
              + " the microMIPS-only PIC32MM does this to get smaller spacing than MIPS normally "
              + "allows.  Check for that and set the vector spacing there, too, if needed.");
        writer.println("#ifdef _INTCON_VS_MASK");
        writer.println("    INTCONCLR = _INTCON_VS_MASK;");
        writer.println("    INTCONSET = (_vector_spacing << _INTCON_VS_POSITION) & _INTCON_VS_MASK;");
        writer.println("#endif");

        writer.println("    /* Enable multi-vector mode if this device supports it. */");
        writer.println("#ifdef _INTCON_MVEC_MASK");
        writer.println("    INTCONSET = _INTCON_MVEC_MASK;");
        writer.println("#endif");
        writer.println();

        writer.println("    /* Set IntCtl<VS> even if overridden by PIC32. */");
        writer.println("    __builtin_mtc0(12, 1, (_vector_spacing & 0x1F) << 5);");
        writer.println("    _ehb();");
        writer.println("}");
        writer.println();
    }

    private void outputVariableVectorOffsetInitFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Initialize the OFFn registers on the device with a list of vector offsets"
              + "provided in the linker script.");

        writer.println("void __attribute__((weak)) _InitVariableVectorOffsets(void)");
        writer.println("{");
        writer.println("    uint32_t *offset_src = &_vector_offset_init_begin;");
        writer.println("    uint32_t *offset_dst = &OFF001");
        writer.println("    uint32_t *offset_end = &_vector_offset_init_end;");
        writer.println("    int span = &OFF002 - &OFF001;");
        writer.println();
        writer.println("    for( ; offset_src < offset_end; offset_src += span)");
        writer.println("        *offset_dst = *offset_src;");
        writer.println("}");
        writer.println();        
    }

    /* Output a function that will call compiler-generated initialization functions to set up
     * objects.
     */
    private void outputLibcInitArrayFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Call compiler-generated initialization routines for C and C++.");

        writer.println("void __attribute__((weak)) _LibcInitArray(void)");
        writer.println("{");
        writer.println("    void (**preinit_ptr)(void) = &__preinit_array_start;");
        writer.println("    while(preinit_ptr < &__preinit_array_end)");
        writer.println("    {");
        writer.println("        (*preinit_ptr)();");
        writer.println("        ++preinit_ptr;");
        writer.println("    }");
        writer.println();
        writer.println("    _init();");
        writer.println();
        writer.println("    void (**init_ptr)(void) = &__init_array_start;");
        writer.println("    while(init_ptr < &__init_array_end)");
        writer.println("    {");
        writer.println("        (*init_ptr)();");
        writer.println("        ++init_ptr;");
        writer.println("    }");
        writer.println("}");
        writer.println();
    }


    /* Output the _reset_startup function, which is what actually sets up the device.  To make things
     * a little simpler, this will mainly call other functions to do its setup stuff.  Such functions
     * will need to be defined in a support library that gets linked in when building for MIPS devices.
     */
    private void outputStartupFunction(PrintWriter writer, TargetDevice target) {
        int numShadowRegs = target.getInterruptList().getNumShadowRegs();

        if(numShadowRegs > 8) {
            numShadowRegs = 8;
        }

        Utils.writeMultilineCComment(writer, 0, "This is what actually sets up the CPU.");

        writer.println("void _reset_startup(void)");
        writer.println("{");
        writer.println("    /* Jump to NMI handler if device reset due to an NMI (watchdog). */");
        writer.println("    if(__builtin_mfc0(12, 0) & (1 << 19))");
        writer.println("        _nmi_handler();");
        writer.println();
        writer.println("    /* Initialize stack and global pointer from linker script symbols. */");
        writer.println("    __asm__ volatile(\"la $sp, %0\"");
        writer.println("                     : /* no outputs */");
        writer.println("                     : \"i\" (_estack)");
        writer.println("                     : \"memory\");");
        writer.println("    __asm__ volatile(\"la $gp, %0\"");
        writer.println("                     : /* no outputs */");
        writer.println("                     : \"i\" (_gp)");
        writer.println("                     : \"memory\");");
        writer.println();

        writer.println("    /* Set Status<BEV> to switch to bootstrap exception vectors. */");
        writer.println("    /* This should already be set on a reset device. */");
        writer.println("    __builtin_mtc0(12, 0, __builtin_mfc0(12, 0) | (1 << 22));");
        writer.println("    _ehb();");
        writer.println();

        if(numShadowRegs >= 2) {
            writer.println("    _InitShadowRegisterGp();");
        } else {
            writer.println("    /* This device does not have extra shadow register. */");
        }
        writer.println();

        writer.println("    if(_on_reset)");
        writer.println("        _on_reset();");
        writer.println();

        if(FamilyDefinitions.SubFamily.PIC32MZ == target.getSubFamily()) {
            writer.println("    _InitTlbForEbiSqi();");
        } else {
            writer.println("    /* This device does not have a TLB to init. */");
        }
        writer.println();

        writer.println("    _InitDataRegions();");
        writer.println();

        if(target.hasL1Cache()) {
            writer.println("    _InitL1Cache();");
        } else {
            writer.println("    /* This device does not have an L1 cache to init. */");
        }
        writer.println("    /* Enable cacheability for kseg0 even if device does not have L1 cache. */");
        writer.println("    __builtin_mtc0(16, 0, cp0_config | 0x03);");
        writer.println("    _ehb();");
        writer.println();

        writer.println("    /* PIC32MX:  BMX initialization for ramfunc usage not yet supported. */");
        writer.println();

        writer.println("    _InitCp0Registers();");
        writer.println("    _InitVectorSpacing();");
        if(target.getInterruptList().usesVariableOffsets()) {
            writer.println("    _InitVariableVectorOffsets();");
        }
        writer.println();

        if(numShadowRegs >= 2) {
            writer.println("#ifdef PRISS");
            writer.println("    PRISS = " + PRISS_INIT[numShadowRegs] + ";");
            writer.println("#endif");
            writer.println();
        }

        if(target.hasFpu()) {
        Utils.writeMultilineCComment(writer, 4,
                "Do not trap on IEEE exception conditions (invalid op, div/0, overflow, underflow) "
              + "and enable round-to-nearest mode (RM=0).  Also, denormal operands are flushed to "
              + "zero instead of causing an Unimplemented Operation exception (FS=1), intermediate "
              + "results of MADD and MSUB-type instructions are kept in an internal format instead "
              + "of being flushed (FO=1), and results are rounded to the closer of 0 or the smallest "
              + "normal number (FN=1).  The MIPS Warrior M5150 (the core used in the PIC32MZ) Users "
              + "Manual describes these three bits and states that setting all three is the "
              + "\"Highest accuracy and performance configuration\" in a table titled Recommend "
              + "FS/FO/FN Settings.  Note that XC32 uses FS=1, FO=0, FN=0.");
            writer.println("    uint32_t fcsr = 0x01600000;");
            writer.println("    __asm__ volatile(\"ctc1 %0, $31\"");
            writer.println("                     : /* no outputs */");
            writer.println("                     : \"r\" (fcsr));");
        } else {
            writer.println("    /* This device does not have an FPU to initialze. */");
        }
        writer.println();

        writer.println("    /* Call compiler-generated init routines for C and C++. */");
        writer.println("    _LibcInitArray();");
        writer.println();
        writer.println("    if(_on_bootstrap)");
        writer.println("        _on_bootstrap();");
        writer.println();
        writer.println("    /* Clear Status<BEV> to switch to normal exception vectors. */");
        writer.println("    __builtin_mtc0(12, 0, __builtin_mfc0(12, 0) & ~(1 << 22));");
        writer.println("    _ehb();");
        writer.println();
        writer.println("    /* The app is ready to go, call main. */");
        writer.println("    main();");
        writer.println("    exit(0);");
        writer.println();
        writer.println("    /* Nothing left to do but spin here forever. */");
        writer.println("    while(1) {}");
        writer.println("}");
        writer.println();
    }

    /* Output the _reset function, which is the very first thing that is run on processor startup.
     * This function is set the appear in the ".reset" section.  The linker script needs to locate
     * this section at the starupt address of 0xBFC00000.  This function just needs to be able to
     * jump to the startup function, but needs to be able to handle both microMIPS and MIPS32.
     * MIPS16 does not need handling because a MIPS CPU cannot start up in MIPS16 mode.
     */
    private void outputResetFunction(PrintWriter writer, TargetDevice target) {
        Utils.writeMultilineCComment(writer, 0, "This is where the CPU starts executing.");

        writer.println("void __attribute__((noreturn, naked, nomips16, section(\".reset\"))) _reset(void)");
        writer.println("{");

        if(target.supportsMips32Isa()  &&  target.supportsMicroMipsIsa()) {
            Utils.writeMultilineCComment(writer, 4,
                    "We do not know if we started in MIPS32 or microMIPS mode, so we need to handle "
                  + "either.  On MIPS32, the first two words here are interpreted by the CPU as an "
                  + "unconditional branch forward (B insn) and an NOP.  On microMIPS, the two words "
                  + "are interpreted as useless ADDI32 and SLL instructions.  The CPU will therefore "
                  + "go to the appropriate instruction sequence in either case.");
            writer.println("    __asm__ volatile(\".set push \\n\\t\"");
            writer.println("                     \".set noat \\n\\t\"");
            writer.println("                     \".word 0x10000005 \\n\\t\"");
            writer.println("                     \".word 0x00000000 \\n\"");
            writer.println("                     \"__reset_jump_micromips: \\n\\t\"");
            writer.println("                     \".set micromips \\n\\t\"");
            writer.println("                     \"lui $at, %%hi(%0) \\n\\t\"");
            writer.println("                     \"ori $at, $at, %%lo(%0) \\n\\t\"");
            writer.println("                     \"jal $at \\n\\t\"");
            writer.println("                     \"nop \\n\"");
            writer.println("                     \"__reset_jump_mips32: \\n\\t\"");
            writer.println("                     \".set mips32 \\n\\t\"");
            writer.println("                     \"lui $at, %%hi(%0) \\n\\t\"");
            writer.println("                     \"ori $at, $at, %%lo(%0) \\n\\t\"");
            writer.println("                     \"jal $at \\n\\t\"");
            writer.println("                     \"nop \\n\\t\"");
            writer.println("                     \".set pop\"");
            writer.println("                     : /* no outputs */");
            writer.println("                     : \"i\" (_reset_startup));");
        } else {
            writer.println("    __asm__ volatile(\"lui $at, %%hi(%0) \\n\\t\"");
            writer.println("                     \"ori $at, $at, %%lo(%0) \\n\\t\"");
            writer.println("                     \"jal $at \\n\\t\"");
            writer.println("                     \"nop \\n\\t\"");
            writer.println("                     : /* no outputs */");
            writer.println("                     : \"i\" (_reset_startup));");
        }

        writer.println("}");
        writer.println();
        writer.println();
    }


    /* Output the entry point for the bootstrap exception vector.  This is placed by the linker 
     * script into the address required by the MIPS CPU, which is 0xBFC00480.
     */
    private void outputBootstrapExceptionEntryPoint(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, "The entry point to the bootstrap exception vector.");

        writer.println("void __attribute__((noreturn, naked, nomips16, section(\".bev_handler\"))) _bev_exception_entry(void)");
        writer.println("{");
        writer.println("    __asm__(\"la  k0, _bootstrap_exception_handler \\n\\t\"");
        writer.println("            \"jr  k0 \\n\\t\"");
        writer.println("            \"nop\");");
        writer.println("}");
    }

    /* Output the entry point for the general exception vector.  This is placed by the linker 
     * script into the address required by the MIPS CPU, which is Ebase + 0x180.
     */
    private void outputGeneralExceptionEntryPoint(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, "The entry point to the general exception vector.");

        writer.println("void __attribute__((noreturn, naked, nomips16, section(\".gen_handler\"))) _gen_exception_entry(void)");
        writer.println("{");
        writer.println("    __asm__(\"la  k0, _general_exception_context \\n\\t\"");
        writer.println("            \"jr  k0 \\n\\t\"");
        writer.println("            \"nop\");");
        writer.println("}");
    }

    /* Output the entry point for the general exception vector.  This is placed by the linker 
     * script into the address required by the MIPS CPU, which is Ebase.
     */
    private void outputSimpleTlbRefillExceptionEntryPoint(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, "The entry point to the simple TLB refill exception vector.");

        writer.println("void __attribute__((noreturn, naked, nomips16, section(\".simple_tlb_refill_vector\"))) _simple_tlb_refill_exception_entry(void)");
        writer.println("{");
        writer.println("    __asm__(\"la  k0, _simple_tlb_refill_exception_context \\n\\t\"");
        writer.println("            \"jr  k0 \\n\\t\"");
        writer.println("            \"nop\");");
        writer.println("}");
    }

    /* Output the entry point for the general exception vector.  This is placed by the linker 
     * script into the address required by the MIPS CPU, which is Ebase + 0x100.
     */
    private void outputCacheErrorExceptionEntryPoint(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, "The entry point to the cache error exception vector.");

        writer.println("void __attribute__((noreturn, naked, nomips16, section(\".cache_err_vector\"))) _cache_err_exception_entry(void)");
        writer.println("{");
        writer.println("    __asm__(\"la  k0, _cache_err_exception_context \\n\\t\"");
        writer.println("            \"jr  k0 \\n\\t\"");
        writer.println("            \"nop\");");
        writer.println("}");
    }

}
