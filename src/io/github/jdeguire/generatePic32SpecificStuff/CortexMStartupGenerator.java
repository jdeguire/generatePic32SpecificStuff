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

import java.io.PrintWriter;

/**
 * This class will generate the C startup code for Cortex-M devices.  This code also includes the
 * interrupt vectors for the device and will set up device-specific stuff like cache and the FPU,
 * which is why we need to generate code for each device.
 * 
 * Note that MIPS devices do not need device-specific code because there only a few different 
 * variations on the startup code based on whether the device has an L1 cache, an FPU, and microMIPS
 * support, and so there is no complementary MipsStartupGenerator class.
 */
public class CortexMStartupGenerator {

    private final String basepath_;

    /* Create a new CortexMStartupGenerator object that can create startup files for multiple 
     * Cortex-M devices.  The files are source files named as "startup_<device_name>.c".  Each file
     * will be put into a subdirectory under the given path whose name is the full name of the 
     * device in lower-case.
     */
    public CortexMStartupGenerator(String basePath) {
        basepath_ = basePath;
    }

    /* Generate a startup file for the given device if it is an Arm Cortex-M device; that is, it 
     * supports the Thumb ISA but not the full Arm ISA.
     */
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        if(target.isArm()  &&  !target.supportsArmIsa()) {
            String devnameForFile = target.getDeviceName().toLowerCase();
            String targetPath = basepath_ + "/" + devnameForFile + "/startup_" + devnameForFile + ".c";

            try(PrintWriter writer = Utils.createUnixPrintWriter(targetPath)) {
                outputLicenseHeader(writer);
                outputPreamble(writer);
                outputInterruptHandlerDeclarations(writer, target);
                outputInterruptTable(writer, target);
                outputDummyHandler(writer);
                outputCmsisInitFunctions(writer);
                outputFpuEnableFunction(writer);
                outputCmccCacheEnableFunction(writer);
                outputCpuCacheEnableFunction(writer);
                outputLibcInitArrayFunction(writer);
                outputUserFunctionDeclarations(writer);
                outputResetFunction(writer);
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
                         Utils.apacheLicenseString());

        Utils.writeMultilineCComment(writer, 0, header);
        writer.println();
    }

    /* Output initial stuff such as linker symbol declarations and header files.
     */
    private void outputPreamble(PrintWriter writer) {
        writer.println("#include <stdint.h>");
        writer.println("#include <xc.h>");
        writer.println();

        writer.println("/* Symbols defined in the linker script for this device. */");
        writer.println("extern uint32_t __svectors;");
        writer.println("extern uint32_t _etext;");
        writer.println("extern uint32_t _srelocate;");
        writer.println("extern uint32_t _erelocate;");
        writer.println("extern uint32_t _szero;");
        writer.println("extern uint32_t _ezero;");
        writer.println("extern uint32_t _estack;");
        writer.println("extern void (*__preinit_array_start)(void);");
        writer.println("extern void (*__preinit_array_end)(void);");
        writer.println("extern void (*__init_array_start)(void);");
        writer.println("extern void (*__init_array_end)(void);");
        writer.println();

        writer.println("extern int main(void);");
        writer.println("extern void _init(void);");
        writer.println();
        writer.println("/* This is where the program starts execution. */");
        writer.println("void Reset_Handler(void);");
        writer.println();
        writer.println("/* This is the default interrupt and fault handler. */");
        writer.println("void Dummy_Handler(void);");
        writer.println();
    }

    /* Output function declarations for all of the interrupt and fault handlers, except for the Reset
     * handler that has been already provided.  These are all weak aliases to the dummy handler that
     * are meant to be overridden by the application firmware.
     */
    private void outputInterruptHandlerDeclarations(PrintWriter writer, TargetDevice target) {
        InterruptList intList = target.getInterruptList();
        boolean needSkipReset = true;

        String functionAttr = "__attribute__((weak, alias(\"Dummy_Handler\")))";

        for(InterruptList.Interrupt vector : intList.getInterruptVectors()) {
            if(needSkipReset  &&  vector.getName().equals("Reset")) {
                needSkipReset = false;
            } else {
                String functionDecl = "void " + vector.getName() + "_Handler(void)";
                String fullDecl = Utils.padStringWithSpaces(functionDecl, 40, 4) + functionAttr + ";";

                writer.println(fullDecl);
            }
        }

        writer.println();
    }

    /* Output a struct that provides the definition of the device's interrupt vector table.
     */
    private void outputInterruptTable(PrintWriter writer, TargetDevice target) {
        writer.println("const DeviceVectors exception_table __attribute__ ((section(\".vectors\"))) =");
        writer.println("{");
        writer.println("  // Initialize the stack pointer using a symbol from the linker script.");
        writer.println("  void *pvStack = (void *)&_estack;");
        writer.println();

        InterruptList intList = target.getInterruptList();
        int nextVectorNumber = 99999;

        for(InterruptList.Interrupt vector : intList.getInterruptVectors()) {
            int vectorNumber = vector.getIntNumber();
            String vectorString;

            while(vectorNumber > nextVectorNumber) {
                // Fill in any gaps we come across.

                if(nextVectorNumber < 0) {
                    vectorString = "  .pvReservedM" + (-1 * nextVectorNumber);
                } else {
                    vectorString = "  .pvReserved" + nextVectorNumber;
                }

                vectorString = Utils.padStringWithSpaces(vectorString, 36, 4);
                vectorString += "= (void *)0,";

                writer.println(vectorString);
                ++nextVectorNumber;
            }

            String handlerName = vector.getName() + "_Handler";

            vectorString = "  .pfn" + handlerName;
            vectorString = Utils.padStringWithSpaces(vectorString, 36, 4);
            vectorString += "= (void *)" + handlerName;

            if(vectorNumber != intList.getLastVectorNumber()) {
                vectorString += ",";
            }

            String descString = String.format("/* %3d %s */", vectorNumber, vector.getDescription());

            writer.println(Utils.padStringWithSpaces(vectorString, 80, 4) + descString);

            nextVectorNumber = vectorNumber + 1;
        }

        writer.println("};");
        writer.println();        
    }

    /* Output the definition for the dummy handler, which is the default interrupt and fault handler
     * function.  This will have just an infinite loop in it.
     */
    private void outputDummyHandler(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "A dummy handler that does nothing.  This is the default handler.");
        writer.println("void __attribute__((weak)) Dummy_Handler(void)");
        writer.println("{");
        writer.println("    while(1) {}");
        writer.println("}");
        writer.println();
    }

    /* Output dummy function to keep compatible with CMSIS requirements.  Looking at various Atmel
     * source files showed them having only bare implementations that use a fixed value for a clock.
    */
    private void outputCmsisInitFunctions(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Some dummy items to fulfill some CMSIS requirements.  "
              + "These do not seem to have been used by Atmel or Microchip.");
        writer.println("uint32_t __attribute__((weak)) SystemCoreClock = 1000000;");
        writer.println();

        writer.println("void __attribute__((weak)) SystemInit(void)");
        writer.println("{");
        writer.println("}");
        writer.println();

        writer.println("void __attribute__((weak)) SystemCoreClockUpdate(void)");
        writer.println("{");
        writer.println("}");
        writer.println();
    }

    /* Output a function that will conditionally enable the FPU on devices that actually have it.
     * This makes use of the Arm-specific macro __ARM_FP to check for FPU support.
     */
    private void outputFpuEnableFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, "Enable the FPU for devices that have one.");
        writer.println("void __attribute__((weak)) _EnableFpu(void)");
        writer.println("{");
        writer.println("#if defined(__ARM_FP) && (4 == __ARM_FP || 14 == __ARM_FP)");
        writer.println("    SCB->CPACR |= 0x00F00000;");
        writer.println("    __DSB();");
        writer.println("    __ISB();");
        writer.println("#endif");
        writer.println("}");
        writer.println();
    }

    /* Output a function that will enable the cache for device with the Cortex-M Cache Controller.
     * This appears to be a device-level cache added by Atmel to add a cache to the Cortex-M4 that 
     * normally does not have one.
     */
    private void outputCmccCacheEnableFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Enable the Cortex-M Cache Controller with default values.  This is used to "
              + "supplement Cortex-M devices that do not have a CPU cache.");
        writer.println("void __attribute__((weak)) _EnableCmccCache(void)");
        writer.println("{");
        writer.println("#if defined(ID_CMCC)");
        writer.println("    CMCC->CTRL.bit.CEN = 1;");
        writer.println("#endif");
        writer.println("}");
        writer.println();
    }

    /* Output a function that will enable the cache for device with the Cortex-M Cache Controller.
     * This appears to be a device-level cache added by Atmel to add a cache to the Cortex-M4 that 
     * normally does not have one.
     */
    private void outputCpuCacheEnableFunction(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Enable the Cortex-M CPU instruction and data caches. Only the Cortex-M7 has a "
              + "CPU-level cache at this time.");
        writer.println("void __attribute__((weak)) _EnableCpuCache(void)");
        writer.println("{");
        writer.println("    // These invalidate the caches before enabling them.");
        writer.println("#if __ICACHE_PRESENT == 1");
        writer.println("    SCB_EnableICache();");
        writer.println("#endif");
        writer.println("#if __DCACHE_PRESENT == 1");
        writer.println("    SCB_EnableDCache();");
        writer.println("#endif");
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

    /* Output weak declarations of functions the user can use to run code during startup.  Weak
     * declarations do not have to be defined and will be 0 if they are not defined by the user.
     */
    private void outputUserFunctionDeclarations(PrintWriter writer) {
        Utils.writeMultilineCComment(writer, 0, 
                "Define these to run code during startup.  The _on_reset() function is run almost "
              + "immediately, so the cache and FPU will probably not be usable unless they are "
              + "enabled in _on_reset().  The _on_bootstrap() function is run just before main is "
              + "called and so everything should be initialized.");
        writer.println("extern void __attribute__((weak, long_call)) _on_reset(void);");
        writer.println("extern void __attribute__((weak, long_call)) _on_bootstrap(void);");
        writer.println();
    }

    /* Output the Reset function that runs whenever the device is reset.  This is what will call all
     * of the other functions that have been defined in this file so far.
     */
    private void outputResetFunction(PrintWriter writer) {
        writer.println("void __attribute__((weak)) Reset_Handler(void)");
        writer.println("{");
        writer.println("    /* The stack is initialized by the CPU at startup, so we do not have to. */");
        writer.println();
        writer.println("    if(_on_reset)");
        writer.println("        _on_reset();");
        writer.println();
        writer.println("    /* Enable caches and FPU, if they are present. */");
        writer.println("    _EnableFpu();");
        writer.println("    _EnableCpuCache();");
        writer.println("    _EnableCmccCache();");
        writer.println();
        writer.println("    /* Copy variable init data from flash to RAM. */");
        writer.println("    uint32_t *src_ptr = &_etext;");
        writer.println("    uint32_t *dst_ptr = &_srelocate;");
        writer.println("    while(dst_ptr < &_erelocate)");
        writer.println("        *dst_ptr++ = *src_ptr++;");
        writer.println();
        writer.println("    /* Clear uninitialized variables in the .bss section. */");
        writer.println("    uint32_t *bss_ptr = &_szero;");
        writer.println("    while(bss_ptr < &_ezero)");
        writer.println("        *bss_ptr++ = 0;");
        writer.println();
        writer.println("    /* Set the vector table base address, if supported by this device. */");
        writer.println("    /* The __svectors symbol is defined in the linker script. */");
        writer.println("#ifdef SCB_VTOR_TBLOFF_Msk");
        writer.println("    uint32_t vtor_addr = &__svectors;");
        writer.println("    SCB->VTOR = ((uint32_t)vtor_addr & SCB_VTOR_TBLOFF_Msk);");
        writer.println("#endif");
        writer.println();
        writer.println("    /* Call compiler-generated init routines for C and C++. */");
        writer.println("    _LibcInitArray();");
        writer.println();
        writer.println("    if(_on_bootstrap)");
        writer.println("        _on_bootstrap();");
        writer.println();
        writer.println("    /* The app is ready to go, call main. */");
        writer.println("    main();");
        writer.println();
        writer.println("    /* Nothing left to do but spin here forever. */");
        writer.println("    while(1) {}");
        writer.println("}");
    }
}
