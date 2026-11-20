import java.io.File;
import java.io.PrintWriter;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.SourceType;

public class Stage2WalletDecompile extends GhidraScript {
    @Override
    public void run() throws Exception {
        long[] addresses = {0x00412bb0L, 0x0041c00eL, 0x0043b14eL};
        String[] names = {"LoadWallet", "AddKey", "CMainFrame_OnMouseEvents"};
        PrintWriter writer = new PrintWriter(new File(getScriptArgs()[0]), "UTF-8");
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        for (int i = 0; i < addresses.length; i++) {
            Function function = getFunctionAt(toAddr(addresses[i]));
            if (function == null) {
                continue;
            }
            function.setName(names[i], SourceType.USER_DEFINED);
            writer.printf("===== %s @ %s =====%n", names[i], function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 120, monitor);
            writer.println(result.decompileCompleted() ? result.getDecompiledFunction().getC() : result.getErrorMessage());
            for (ghidra.program.model.symbol.Reference reference : getReferencesTo(function.getEntryPoint())) {
                Function caller = getFunctionContaining(reference.getFromAddress());
                writer.printf("XREF %s FROM %s%n", reference.getFromAddress(),
                    caller == null ? "<none>" : caller.getName() + " @ " + caller.getEntryPoint());
            }
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
    }
}
