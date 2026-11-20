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

public class Stage2Map extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        File output = new File(args[0]);
        boolean dll = currentProgram.getName().toLowerCase().contains("libeay32");
        String[] names = dll ? new String[] {
            "RAND_SSLeay", "RAND_get_rand_method", "RAND_bytes", "RAND_pseudo_bytes",
            "RAND_add", "RAND_seed", "RAND_poll", "RAND_screen", "RAND_status",
            "EC_KEY_generate_key", "EC_KEY_new_by_curve_name", "BN_rand", "BN_rand_range",
            "BN_pseudo_rand", "BN_pseudo_rand_range", "i2d_ECPrivateKey", "SSLeay_version"
        } : new String[] {
            "EC_KEY_generate_key", "RAND_screen", "RAND_add", "RAND_bytes", "RAND_poll",
            "RAND_status", "QueryPerformanceCounter", "RegQueryValueExA", "i2d_ECPrivateKey",
            "d2i_ECPrivateKey", "EC_KEY_new_by_curve_name", "i2o_ECPublicKey"
        };
        String[] strings = dll ? new String[] {
            "OpenSSL 0.9.8h", "PRNG not seeded", "ADVAPI32.DLL", "CryptAcquireContextW",
            "CryptGenRandom", "CreateToolhelp32Snapshot", "Intel Hardware Cryptographic Service Provider"
        } : new String[] {
            "GenerateNewKey()", "CKey::MakeNewKey()", "RandAddSeed()", "Error in wallet.dat",
            "Loading wallet", "Loading block index", "CKey::GetPrivKey()", "CWalletDB"
        };
        PrintWriter writer = new PrintWriter(output, "UTF-8");
        writer.println("PROGRAM " + currentProgram.getName() + " IMAGE BASE " + currentProgram.getImageBase());
        Set<Function> selected = new HashSet<>();
        for (Function function : currentProgram.getFunctionManager().getFunctions(true)) {
            for (String name : names) {
                String fname = function.getName();
                if (fname.equals(name) || fname.equals("_" + name)) {
                    writer.printf("FUNCTION %s @ %s thunk=%s external=%s%n", function.getName(),
                        function.getEntryPoint(), function.isThunk(), function.isExternal());
                    for (Reference reference : getReferencesTo(function.getEntryPoint())) {
                        Function caller = getFunctionContaining(reference.getFromAddress());
                        writer.printf("  XREF %s FROM %s%n", reference.getFromAddress(),
                            caller == null ? "<data/none>" : caller.getName() + " @ " + caller.getEntryPoint());
                    }
                    if (dll && !function.isThunk() && !function.isExternal()) {
                        selected.add(function);
                    }
                }
            }
        }
        for (Data data : currentProgram.getListing().getDefinedData(true)) {
            Object value = data.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String text = (String)value;
            for (String needle : strings) {
                if (text.contains(needle)) {
                    writer.printf("STRING %s %s%n", data.getAddress(), text);
                    for (Reference reference : data.getReferenceIteratorTo()) {
                        Function caller = getFunctionContaining(reference.getFromAddress());
                        writer.printf("  XREF %s FROM %s%n", reference.getFromAddress(),
                            caller == null ? "<data/none>" : caller.getName() + " @ " + caller.getEntryPoint());
                    }
                }
            }
        }
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        for (Function function : selected) {
            writer.printf("%n===== %s @ %s =====%n", function.getName(), function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 120, monitor);
            writer.println(result.decompileCompleted() ? result.getDecompiledFunction().getC() : result.getErrorMessage());
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
        println("Wrote " + output.getAbsolutePath());
    }
}
