import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;

public class ModernMap extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] names = {"getentropy", "mach_absolute_time", "clock_gettime", "gettimeofday", "sysctl"};
        String[] needles = {
            "Error: cannot process db transaction for descriptors setup",
            "writing descriptor master private key failed",
            "writing descriptor failed",
            "Wallet completed creation",
            "Wallet completed loading",
            "Wallet name cannot be empty",
            "Creating wallet",
            "SQLiteDatabase: Failed to create new database",
            "Using SQLite Version",
            "Wallet contains both unencrypted and encrypted keys",
            "[libsecp256k1] illegal argument",
            "pubkey != NULL",
            "seckey != NULL",
            "Feeding %i bytes of environment data into RNG",
            "Feeding %i bytes of dynamic environment data into RNG",
            "GenerateNewSingleSig",
            "SetupOwnDescriptorScriptPubKeyMans",
            "SetupDescriptorScriptPubKeyMans",
            "EncryptWallet",
            "ProcRand",
            "Failed to read randomness, aborting",
            "OS cryptographic RNG sanity check failure",
            "Bitcoin Core version",
            "Create Wallet"
        };
        File output = new File(getScriptArgs()[0]);
        PrintWriter writer = new PrintWriter(output, "UTF-8");
        writer.printf("PROGRAM %s IMAGE_BASE %s FUNCTIONS %d%n", currentProgram.getName(),
            currentProgram.getImageBase(), currentProgram.getFunctionManager().getFunctionCount());
        Set<Function> selected = new HashSet<>();
        Map<Function, Integer> depths = new HashMap<>();
        ArrayDeque<Function> queue = new ArrayDeque<>();
        for (Function function : currentProgram.getFunctionManager().getFunctions(true)) {
            String fname = function.getName();
            for (String name : names) {
                if (fname.equals(name) || fname.equals("_" + name)) {
                    writer.printf("FUNCTION %s @ %s thunk=%s external=%s%n", fname,
                        function.getEntryPoint(), function.isThunk(), function.isExternal());
                    for (Reference reference : getReferencesTo(function.getEntryPoint())) {
                        Function caller = getFunctionContaining(reference.getFromAddress());
                        writer.printf("  XREF %s FROM %s%n", reference.getFromAddress(),
                            caller == null ? "<data/none>" : caller.getName() + " @ " + caller.getEntryPoint());
                        if (caller != null && fname.contains("getentropy") && !caller.isThunk()) {
                            depths.put(caller, 0);
                            queue.add(caller);
                        }
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            Function function = queue.removeFirst();
            int depth = depths.get(function);
            if (depth <= 2) {
                selected.add(function);
            }
            writer.printf("CALLER_DEPTH %d %s @ %s%n", depth, function.getName(), function.getEntryPoint());
            if (depth >= 7) {
                continue;
            }
            for (Reference reference : getReferencesTo(function.getEntryPoint())) {
                Function caller = getFunctionContaining(reference.getFromAddress());
                if (caller != null && !caller.isThunk() && !depths.containsKey(caller)) {
                    depths.put(caller, depth + 1);
                    queue.add(caller);
                }
            }
        }
        for (Data data : currentProgram.getListing().getDefinedData(true)) {
            Object value = data.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String text = (String)value;
            for (String needle : needles) {
                if (!text.contains(needle)) {
                    continue;
                }
                writer.printf("STRING %s %s%n", data.getAddress(), text);
                for (Reference reference : data.getReferenceIteratorTo()) {
                    Address from = reference.getFromAddress();
                    Function function = getFunctionContaining(from);
                    writer.printf("  REF %s FUNCTION %s%n", from,
                        function == null ? "<none>" : function.getName() + " @ " + function.getEntryPoint());
                    if (function != null) {
                        selected.add(function);
                    }
                }
            }
        }
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        for (Function function : selected) {
            writer.printf("%n===== %s @ %s =====%n", function.getName(), function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 180, monitor);
            writer.println(result.decompileCompleted() ? result.getDecompiledFunction().getC() : result.getErrorMessage());
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
        println("Wrote " + output.getAbsolutePath());
    }
}
