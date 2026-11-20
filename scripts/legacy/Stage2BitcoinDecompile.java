import java.io.File;
import java.io.PrintWriter;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.SourceType;

public class Stage2BitcoinDecompile extends GhidraScript {
    @Override
    public void run() throws Exception {
        long[] addresses = {
            0x00401444L, 0x00402486L, 0x00411fb4L, 0x0041c582L,
            0x004687e8L, 0x008b3020L, 0x008b3618L, 0x008b4f2cL, 0x008e38a8L
        };
        String[] names = {
            "RandAddSeed", "GetRand", "CWalletDB_LoadWallet", "GenerateNewKey",
            "CMyApp_OnInit2", "CKey_MakeNewKey", "CKey_constructor", "CInit_constructor", "CKey_GetPrivKey"
        };
        PrintWriter writer = new PrintWriter(new File(getScriptArgs()[0]), "UTF-8");
        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);
        for (int i = 0; i < addresses.length; i++) {
            Function function = getFunctionAt(toAddr(addresses[i]));
            if (function == null) {
                writer.println("MISSING " + Long.toHexString(addresses[i]));
                continue;
            }
            function.setName(names[i], SourceType.USER_DEFINED);
            writer.printf("===== %s @ %s =====%n", names[i], function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 120, monitor);
            writer.println(result.decompileCompleted() ? result.getDecompiledFunction().getC() : result.getErrorMessage());
            writer.flush();
        }
        decompiler.dispose();
        writer.close();
    }
}
