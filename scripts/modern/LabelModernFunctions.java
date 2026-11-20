import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.SourceType;

public class LabelModernFunctions extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[][] labels = {
            {"10117364c", "GenerateRandomKey__CKey_MakeNewKey_inlined"},
            {"10129e684", "GetStrongRandBytes"},
            {"10129e318", "GetRandBytes"},
            {"10129e378", "ProcRand"},
            {"10129f0a4", "SeedSlow__GetOSRand_inlined"},
            {"10129e974", "Random_SanityCheck__GetOSRand_inlined"},
            {"10129f39c", "RandFailure"},
            {"10129eb94", "RNGState_MixExtract"},
            {"10129f184", "RNGState_SeedEvents"},
            {"10129f3e4", "RandAddDynamicEnv"},
            {"10129f7f4", "RandAddStaticEnv"},
            {"10129f228", "SeedStrengthen"},
            {"10129e6d4", "RandAddPeriodic"},
            {"10129ed84", "RandomInit"},
            {"101172460", "CKey_GetPubKey"},
            {"101262210", "secp256k1_ec_seckey_verify"},
            {"101262288", "secp256k1_ec_pubkey_create"},
            {"101173a58", "CExtKey_SetSeed"},
            {"101173ca0", "CExtKey_Neuter"},
            {"100dc2ab8", "CWallet_SetupOwnDescriptorScriptPubKeyMans"},
            {"100dc296c", "CWallet_SetupDescriptorScriptPubKeyMans_batch"},
            {"100dc2c54", "CWallet_SetupDescriptorScriptPubKeyMans"},
            {"100d6bf6c", "DescriptorScriptPubKeyMan_GenerateNewSingleSig"},
            {"100df64f4", "GenerateWalletDescriptor"},
            {"100d6c300", "DescriptorScriptPubKeyMan_AddDescriptorKeyWithDB"},
            {"100de3c74", "WalletBatch_WriteDescriptor"},
            {"100de3880", "WalletBatch_WriteDescriptorKey"},
            {"100de3ab0", "WalletBatch_WriteCryptedDescriptorKey"},
            {"100d6c838", "DescriptorScriptPubKeyMan_TopUpWithDB"},
            {"100da7a80", "CWallet_CreateNew"},
            {"100da80dc", "CWallet_EncryptWallet"},
            {"100da6c14", "CreateWallet"},
            {"100c77614", "WalletLoaderImpl_createWallet"},
            {"100136ab8", "CreateWalletActivity_createWallet"},
            {"100137328", "CreateWalletActivity_create"},
            {"10013b7d4", "CreateWalletActivity_createWallet_lambda"},
            {"100d9f948", "SQLiteBatch_WriteKey"}
        };
        for (String[] item : labels) {
            Address address = toAddr(item[0]);
            Function function = getFunctionAt(address);
            if (function == null) {
                throw new IllegalStateException("No function at " + item[0]);
            }
            function.setName(item[1], SourceType.USER_DEFINED);
            println(item[0] + " " + item[1]);
        }
    }
}
