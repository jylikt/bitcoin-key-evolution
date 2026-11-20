# Идентификаторы исследованных артефактов

Хеши фиксируют именно те файлы, к которым относятся адреса Ghidra и динамические наблюдения. Совпадение версии без совпадения хеша недостаточно для переноса адресов.

## Bitcoin 0.1.x

| Файл | Алгоритм | Значение |
|---|---|---|
| `bitcoin.exe` | SHA-256 | `fbcac071d92e26d82ec917214e334bd43850c0691f113bab1d4741c9bdd30d2d` |
| `libeay32.dll` | SHA-256 | `d108cdff2922a60e0054718a84178a40526ba00401fa34a87fb055947c77a182` |
| `bitcoin-0.1.0.rar` | MD5, сокращённый | `91e2dfa2…5500` |
| `bitcoin-0.1.0.rar` | SHA-1, сокращённый | `ec9ed4cc…90dd` |

Последние два значения сохранились только в сокращённой записи исходного отчёта и приведены без недоступных средних символов. Полные SHA-256 EXE и DLL вычислены непосредственно и являются основными идентификаторами.

Атрибуция EXE: обновление 10 января 2009, `VERSION=101`, About `0.1.1 Alpha`. DLL содержит строку OpenSSL 0.9.8h от 28 May 2008. Наличие подтверждённой launch-day копии 9 января не утверждается.

## Bitcoin Core 32.1

| Файл | SHA-256 |
|---|---|
| `Bitcoin-Qt.app/Contents/MacOS/Bitcoin-Qt` | `4d9270db7f19ecb6c375730eecf673dd6bc6d6a1cf2ff3a55bd13b89cba63a05` |
| `bitcoin-32.1-arm64-apple-darwin.zip` | `6fb94e4d2254bf45f0f58a6679c10f16206c5e885be8541fd974d395fe49d46c` |
| `bitcoin-32.1.tar.gz` | `a5f2f61e32cf7f790641c9ec306a3c88ca7b5a556946956b1f35ffd7e27813f9` |
| `bitcoin-qt-32.1-strings-offsets.txt`, release asset | `e3c62284c342943986d45a6dea1d54087d7d8e6aa011c06a3ca91e2d967f6e27` |

Официальный манифест: <https://bitcoincore.org/bin/bitcoin-core-32.1/SHA256SUMS>. Размер исследованного Mach-O — 32 929 752 байта, thin ARM64 PIE. Подпись: `Developer ID Application: Bitcoin Core Code Signing Association (YZC7WH3MRU)`.

## Ранние архивы хронологии

| Архив | SHA-256 |
|---|---|
| `bitcoin-nov08.rar` | `f0327ebbea17f7d6e14be5f5534c6ff16c7648f588cbb096fc8fdfcb7e071abf` |
| `bitcoin-0.1.3.rar` | `3d73b1a80ce775e0cec7f9476644a6bf9b361e99567fd143807ad1d1c81b1756` |
| `v0.1.5.tar.gz` | `b0679fea68d65007f0c05c81cd9a296acfc299767cc7c46992fe8ea9928e6cb0` |

Для Git-тегов дополнительным идентификатором служат сами теги `v0.2.0`–`v0.3.19` в официальной истории [bitcoin/bitcoin](https://github.com/bitcoin/bitcoin). Машинная хронология сохраняет commit и author date из `git blame` для соответствующих строк.
