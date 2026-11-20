import java.io.File;
import java.io.PrintWriter;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;

public class Stage2PRNGDecompile extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] needles = {"ssleay_rand", "bnrand", "bn_rand_range", "readtimer", "readscreen"};
        PrintWriter writer = new PrintWriter(new File(getScriptArgs()[0]), "UTF-8");
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        for (Function function : currentProgram.getFunctionManager().getFunctions(true)) {
            boolean match = false;
            for (String needle : needles) {
                match |= function.getName().contains(needle);
            }
            if (!match) {
                continue;
            }
            writer.printf("===== %s @ %s =====%n", function.getName(), function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 120, monitor);
            writer.println(result.decompileCompleted() ? result.getDecompiledFunction().getC() : result.getErrorMessage());
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
    }
}
