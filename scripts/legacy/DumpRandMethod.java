import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.symbol.Symbol;

public class DumpRandMethod extends GhidraScript {
    @Override
    public void run() throws Exception {
        for (Symbol symbol : currentProgram.getSymbolTable().getAllSymbols(false)) {
            if (symbol.getName().contains("rand_ssleay_meth")) {
                Address address = symbol.getAddress();
                println(symbol.getName() + " @ " + address);
                String[] fields = {"seed", "bytes", "cleanup", "add", "pseudo_bytes", "status"};
                for (int i = 0; i < fields.length; i++) {
                    Address pointer = address.add(i * 4);
                    int value = getInt(pointer);
                    println(fields[i] + " pointer " + pointer + " -> " + toAddr(value));
                }
            }
        }
    }
}
