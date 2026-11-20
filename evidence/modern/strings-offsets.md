# Полный dump строк Bitcoin Core 32.1

Файл `bitcoin-qt-32.1-strings-offsets.txt` содержит 208 260 строк и 4 656 288 байта исходного inventory-вывода для исследованного ARM64 Mach-O. Он не сокращён и не изменён, а приложен к релизу `v1.0.0` как отдельный evidence-asset, чтобы не хранить крупный текстовый blob в Git-истории.

- [Скачать `bitcoin-qt-32.1-strings-offsets.txt`](https://github.com/jylikt/bitcoin-key-evolution/releases/download/v1.0.0/bitcoin-qt-32.1-strings-offsets.txt)
- SHA-256: `e3c62284c342943986d45a6dea1d54087d7d8e6aa011c06a3ca91e2d967f6e27`

Проверка на macOS:

```bash
curl -fL https://github.com/jylikt/bitcoin-key-evolution/releases/download/v1.0.0/bitcoin-qt-32.1-strings-offsets.txt \
  -o bitcoin-qt-32.1-strings-offsets.txt
shasum -a 256 bitcoin-qt-32.1-strings-offsets.txt
```

В отчётах сокращение `strings-offsets.txt` относится именно к этому полному файлу. Для вывода об отсутствии OpenSSL-импортов дополнительно доступен непосредственно в репозитории `nm-imports.txt`.
