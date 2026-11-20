import java.io.File;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;

public class BitcoinStringMap extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] needles = {
            "CTransaction::CheckTransaction()",
            "CTransaction::GetValueOut()",
            "GetNextWorkRequired RETARGET",
            "AddToBlockIndex: new best=",
            "ERROR: SignatureHash()",
            "ProcessMessage()",
            "BitcoinMiner started",
            "IRC connect failed",
            "LoadWallet()",
            "CBlock::CheckBlock()"
        };
        File output = new File(getScriptArgs()[0]);
        PrintWriter writer = new PrintWriter(output, "UTF-8");
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        Set<Function> functions = new HashSet<>();
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
                        functions.add(function);
                    }
                }
            }
        }
        for (Function function : functions) {
            writer.printf("%n===== %s @ %s =====%n", function.getName(), function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 120, monitor);
            if (result.decompileCompleted()) {
                writer.println(result.getDecompiledFunction().getC());
            }
            else {
                writer.println(result.getErrorMessage());
            }
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
        println("Wrote " + output.getAbsolutePath());
    }
}
