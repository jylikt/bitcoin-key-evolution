import java.io.File;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;

public class StringDecompile extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        PrintWriter writer = new PrintWriter(new File(args[0]), "UTF-8");
        Set<Function> functions = new HashSet<>();
        for (Data data : currentProgram.getListing().getDefinedData(true)) {
            Object value = data.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String text = (String)value;
            for (int i = 1; i < args.length; i++) {
                if (!text.contains(args[i])) {
                    continue;
                }
                writer.printf("STRING %s %s%n", data.getAddress(), text);
                for (Reference reference : data.getReferenceIteratorTo()) {
                    Function function = getFunctionContaining(reference.getFromAddress());
                    writer.printf("  REF %s FUNCTION %s%n", reference.getFromAddress(),
                        function == null ? "<none>" : function.getName() + " @ " + function.getEntryPoint());
                    if (function != null) {
                        functions.add(function);
                    }
                }
            }
        }
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        for (Function function : functions) {
            writer.printf("%n===== %s @ %s =====%n", function.getName(), function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 180, monitor);
            writer.println(result.decompileCompleted() ? result.getDecompiledFunction().getC() : result.getErrorMessage());
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
        println("Wrote " + args[0]);
    }
}
