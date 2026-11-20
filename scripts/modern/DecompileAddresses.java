import java.io.File;
import java.io.PrintWriter;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;

public class DecompileAddresses extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        PrintWriter writer = new PrintWriter(new File(args[0]), "UTF-8");
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        for (int i = 1; i < args.length; i++) {
            Address address = toAddr(args[i]);
            Function function = getFunctionAt(address);
            if (function == null) {
                function = getFunctionContaining(address);
            }
            if (function == null) {
                writer.printf("NO_FUNCTION %s%n", args[i]);
                continue;
            }
            writer.printf("%n===== %s @ %s =====%n", function.getName(), function.getEntryPoint());
            writer.println("CALLERS:");
            for (Reference reference : getReferencesTo(function.getEntryPoint())) {
                Function caller = getFunctionContaining(reference.getFromAddress());
                writer.printf("  %s FROM %s%n", reference.getFromAddress(),
                    caller == null ? "<data/none>" : caller.getName() + " @ " + caller.getEntryPoint());
            }
            DecompileResults result = decompiler.decompileFunction(function, 180, monitor);
            writer.println(result.decompileCompleted() ? result.getDecompiledFunction().getC() : result.getErrorMessage());
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
        println("Wrote " + args[0]);
    }
}
