import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.SourceType;

public class LabelBitcoinFunctions extends GhidraScript {
    @Override
    public void run() throws Exception {
        long[][] mappings = {
            {0x0040944eL, 0},
            {0x00421b2eL, 1},
            {0x004263faL, 2},
            {0x0042f028L, 3},
            {0x004bd0e0L, 4},
            {0x008d55a4L, 5},
            {0x008d5aa4L, 6}
        };
        String[] names = {
            "SignatureHash",
            "GetNextWorkRequired",
            "CBlock_AddToBlockIndex",
            "BitcoinMiner",
            "ThreadIRCSeed",
            "CTransaction_GetValueOut",
            "CTransaction_CheckTransaction"
        };
        for (long[] mapping : mappings) {
            Function function = getFunctionAt(toAddr(mapping[0]));
            if (function != null) {
                function.setName(names[(int)mapping[1]], SourceType.USER_DEFINED);
                println(names[(int)mapping[1]] + " @ " + function.getEntryPoint());
            }
        }
        Function signatureHash = getFunctionAt(toAddr(0x0040944eL));
        if (signatureHash != null) {
            for (Reference reference : getReferencesTo(signatureHash.getEntryPoint())) {
                Function caller = getFunctionContaining(reference.getFromAddress());
                if (caller != null) {
                    println("SignatureHash caller: " + caller.getName() + " @ " + caller.getEntryPoint());
                }
            }
        }
    }
}
