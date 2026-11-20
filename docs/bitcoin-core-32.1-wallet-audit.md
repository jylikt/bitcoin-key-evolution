# Bitcoin Core 32.1.0 для macOS arm64: реверс-инжиниринг кошелька и генерации ключей

Дата анализа: 20 ноября 2026 года.

Объект: официальный `Bitcoin-Qt.app` для macOS ARM64; точные хеши приведены в [манифесте](artifact-hashes.md).

Публичные доказательства: `evidence/modern/`; скрипты: `scripts/modern/`.

## 1. Краткий результат

Исследуемое приложение — официальная сборка **Bitcoin Core 32.1.0 (32.1), Mach-O arm64**. Его содержимое побайтово совпадает с официальным ZIP. Подпись Developer ID и локальная проверка Gatekeeper успешны.

Современный путь создания ключей принципиально отличается от Bitcoin 0.1.0:

- OpenSSL и `EC_KEY_generate_key()` больше не используются;
- исходные 32 байта получаются через собственный RNG Bitcoin Core с обязательным вызовом macOS `getentropy`;
- валидность кандидата проверяется `libsecp256k1`;
- создаётся один BIP32 master seed, а не независимый ключ для каждого адреса;
- новые кошельки создаются только как descriptor wallets с SQLite;
- создаются восемь descriptors: Legacy, P2SH-SegWit, Bech32 и Bech32m, каждый во внешнем и внутреннем варианте;
- при шифровании используются AES-256-CBC и итеративный SHA-512 KDF с динамическим числом раундов;
- отказ `getentropy` приводит к `abort`, а не к генерации ключа на пониженной энтропии.

Это целевой анализ кошелька, RNG и их связи с GUI, а не исчерпывающий аудит всех 59 572 функций, обнаруженных Ghidra. Подтверждённой уязвимости в исследованном пути не найдено.

## 2. Доказательства и шкала уверенности

- **Высокая:** официальный хеш и подпись; прямое чтение машинного кода; совпадение с исходниками; локальный воспроизводимый эксперимент.
- **Средне-высокая:** ручное сопоставление stripped-функций с исходниками по строкам, вызовам и структуре декомпиляции.
- **Гипотеза:** утверждение, которое требует другой ОС, fault injection или дополнительных измерений. Такие утверждения не выдаются здесь за подтверждённые дефекты.

Адреса Ghidra — статические VMA при image base `0x100000000`, без runtime ASLR slide.

Официальный исходный архив использовался как проверяемый справочник. Независимая reproducible-build пересборка и побитовое сравнение результата компиляции не выполнялись.

## 3. Идентификация сборки

| Параметр | Подтверждённое значение | Доказательство | Уверенность |
|---|---|---|---|
| Версия | `32.1.0`, официальный релиз `32.1` | `Contents/Info.plist:27-31`; вывод `Bitcoin-Qt -version`; `CMakeLists.txt:30-35` | Высокая |
| Архитектура | thin Mach-O, ARM64, PIE | `file`, `otool -hv`; `evidence/modern/macho-load-commands.txt` | Высокая |
| Размер исполняемого файла | 32 929 752 байта | `ls -l` | Высокая |
| SHA-256 исполняемого файла | `4d9270db7f19ecb6c375730eecf673dd6bc6d6a1cf2ff3a55bd13b89cba63a05` | Локальный `shasum`; совпадение с файлом внутри официального ZIP | Высокая |
| SHA-256 официального ZIP | `6fb94e4d2254bf45f0f58a6679c10f16206c5e885be8541fd974d395fe49d46c` | Официальный `SHA256SUMS` и локальный `shasum` | Высокая |
| SHA-256 исходного архива | `a5f2f61e32cf7f790641c9ec306a3c88ca7b5a556946956b1f35ffd7e27813f9` | Официальный `SHA256SUMS` и локальный `shasum` | Высокая |
| Подпись | `Developer ID Application: Bitcoin Core Code Signing Association (YZC7WH3MRU)` | `codesign -dv --verbose=4` | Высокая |
| Gatekeeper | `accepted`, `Notarized Developer ID` | `spctl -a -vv`; `codesign --verify --deep --strict` | Высокая |
| Минимальная ОС / SDK | macOS 14.0 / 14.0 | `evidence/modern/macho-load-commands.txt:493-500` | Высокая |
| Qt | 6.8.4, static | Строки бинарника и `evidence/modern/dynamic/qt-regtest.log:4-11` | Высокая |
| SQLite | 3.50.4 | `depends/packages/sqlite.mk:2-5`; журнал запуска | Высокая |
| libsecp256k1 | 0.8.1, статически связана | `src/secp256k1/CMakeLists.txt:6-13`; машинный код внутри главного Mach-O | Высокая |

Официальные источники:

- [Объявление релиза 32.1](https://bitcoincore.org/en/2026/10/27/release-32.1/)
- [Официальный SHA256SUMS](https://bitcoincore.org/bin/bitcoin-core-32.1/SHA256SUMS)

Дополнительная проверка `stapler validate` не завершилась из-за таймаута сервера Apple. Она не отменяет успешные локальные `codesign` и `spctl`, но отдельный результат online-валидации тикета здесь не утверждается.

В bundle отсутствуют собственные динамические библиотеки: основной Mach-O содержит статически связанные Qt, SQLite, libsecp256k1 и остальной код. `otool -L` показывает только системные frameworks и библиотеки Apple. Импортов `RAND_*`, `EC_KEY_*`, `EVP_*`, `SSL_*`, а также строк OpenSSL/libcrypto не обнаружено (`evidence/modern/nm-imports.txt`, `evidence/modern/strings-offsets.md`).

## 4. Карта Ghidra-проекта

Проект при воспроизведении: `work/ghidra/bitcoin-core-32.1.gpr`. Сама Ghidra-база не включена в репозиторий.

Полный анализ занял 912 секунд и успешно сохранился. Ghidra обнаружил 59 572 функции; это результат эвристического анализа stripped-бинарника, а не точное исходное количество функций.

Основные артефакты:

- `evidence/modern/import-analysis.log` — импорт и статистика анализа;
- `evidence/modern/modern-map.txt` — строки, ссылки и первичная декомпиляция;
- `evidence/modern/key-wallet-decompile.txt` — RNG, ключи и запись descriptor-ключей;
- `evidence/modern/gui-wallet-decompile.txt` — GUI создания кошелька;
- `evidence/modern/wallet-write-decompile.txt` — создание wallet и SQLite-записи;
- `evidence/modern/sqlite-write-decompile.txt` — `SQLiteBatch::WriteKey`;
- `scripts/modern/LabelModernFunctions.java` — восстановленные имена для повторного применения к проекту.

Во время автоматического анализа были ошибки Objective-C analyzer и отдельные предупреждения декомпилятора. Итоговый статус — `Analysis succeeded`; исследованные функции дополнительно проверены вручную по декомпиляции.

## 5. Полный путь создания кошелька и seed

```text
BitcoinGUI::createWallet
  work/modern/bitcoin-32.1/src/qt/bitcoingui.cpp:1276-1283
  |
  +-> CreateWalletActivity::create
  |   work/modern/bitcoin-32.1/src/qt/walletcontroller.cpp:296-328
  |   Ghidra 0x100137328
  |
  +-> CreateWalletActivity::createWallet
  |   work/modern/bitcoin-32.1/src/qt/walletcontroller.cpp:247-280
  |   Ghidra 0x100136ab8
  |
  +-> QTimer lambda
  |   walletcontroller.cpp:270-280
  |   Ghidra 0x10013b7d4
  |
  +-> WalletLoaderImpl::createWallet
  |   work/modern/bitcoin-32.1/src/wallet/interfaces.cpp:569-584
  |   Ghidra 0x100c77614
  |
  +-> CreateWallet
  |   work/modern/bitcoin-32.1/src/wallet/wallet.cpp:387-455
  |   Ghidra 0x100da6c14
  |
  +-> CWallet::CreateNew
  |   wallet.cpp:3133-3185
  |   Ghidra 0x100da7a80
  |
  +-> SetupWalletGeneration / SetupDescriptorScriptPubKeyMans
  |   wallet.cpp:3676-3684, 3726-3736
  |   Ghidra 0x100dc2c54
  |
  +-> SetupOwnDescriptorScriptPubKeyMans
  |   wallet.cpp:3660-3673
  |   Ghidra 0x100dc2ab8
  |
  +-> GenerateRandomKey + inlined CKey::MakeNewKey
  |   key.cpp:163-169, 354-359
  |   Ghidra 0x10117364c
  |   |
  |   |  Повторять до валидного secp256k1-кандидата:
  |   |
  |   +-> GetStrongRandBytes(32)
  |   |   random.cpp:607-610
  |   |   Ghidra 0x10129e684
  |   |   |
  |   |   +-> ProcRand(out, 32, SLOW=1, always_use_real_rng=1)
  |   |   |   random.cpp:562-589
  |   |   |   Ghidra 0x10129e378
  |   |   |   |
  |   |   |   +-> SeedSlow + inlined GetOSRand
  |   |   |   |   random.cpp:315-318, 484-503
  |   |   |   |   Ghidra 0x10129f0a4
  |   |   |   |   |
  |   |   |   |   +-> getentropy(buf, 32)
  |   |   |   |       call site 0x10129f104
  |   |   |   |       stub 0x1013073d0
  |   |   |   |
  |   |   |   +-> RNGState::MixExtract
  |   |   |       random.cpp:412-447
  |   |   |       Ghidra 0x10129eb94
  |   |   |
  |   +-> secp256k1_ec_seckey_verify
  |       key.cpp:159-167
  |       secp256k1/src/secp256k1.c:611-620
  |       Ghidra 0x101262210
  |
  +-> CKey::GetPubKey
  |   key.cpp:184-194
  |   Ghidra 0x101172460
  |   +-> secp256k1_ec_pubkey_create
  |       secp256k1.c:632-648
  |       Ghidra 0x101262288
  |
  +-> CExtKey::SetSeed — HMAC-SHA512("Bitcoin seed", seed32)
  |   key.cpp:381-392
  |   Ghidra 0x101173a58
  |
  +-> SetupDescriptorScriptPubKeyMans(batch, master_key)
  |   wallet.cpp:3650-3658
  |   Ghidra 0x100dc296c
  |   |
  |   +-> 4 output types × {external, internal}
  |       |
  |       +-> DescriptorScriptPubKeyMan::GenerateNewSingleSig
  |       |   scriptpubkeyman.cpp:868-895
  |       |   Ghidra 0x100d6bf6c
  |       |   |
  |       |   +-> GenerateWalletDescriptor
  |       |   |   walletutil.cpp:35-86
  |       |   |   Ghidra 0x100df64f4
  |       |   |
  |       |   +-> AddDescriptorKeyWithDB
  |       |       scriptpubkeyman.cpp:1199-1228
  |       |       Ghidra 0x100d6c300
  |       |       |
  |       |       +-> WalletBatch::WriteDescriptorKey
  |       |       |   walletdb.cpp:227-233
  |       |       |   Ghidra 0x100de3880
  |       |       |
  |       |       +-> WalletBatch::WriteCryptedDescriptorKey
  |       |           walletdb.cpp:235-241
  |       |           Ghidra 0x100de3ab0
  |       |
  |       +-> WalletBatch::WriteDescriptor
  |           walletdb.cpp:244-247
  |           Ghidra 0x100de3c74
  |
  +-> SQLiteBatch::WriteKey
      sqlite.cpp:496-527
      Ghidra 0x100d9f948
```

### Важные уточнения по оптимизации

В бинарнике нет отдельного тела `CKey::MakeNewKey`: оно встроено компилятором в `GenerateRandomKey` по адресу `0x10117364c`. Там непосредственно виден цикл:

```text
GetStrongRandBytes(keydata, 0x20)
secp256k1_ec_seckey_verify(...)
repeat while invalid
```

`GetOSRand` также встроен в `SeedSlow` (`0x10129f0a4`) и `Random_SanityCheck` (`0x10129e974`). Поэтому адрес функции и адрес инструкции `BL getentropy` указаны раздельно.

## 6. Источники случайности и поведение при отказах

| Источник | Роль в этой arm64-сборке | Исходники / Ghidra | Отказ | Уверенность |
|---|---|---|---|---|
| macOS `getentropy` | 32 байта в каждом strong-запросе; обязательный источник | `random.cpp:285-338`; `0x10129f0a4`, call `0x10129f104` | `RandFailure()` → `std::abort()` | Высокая |
| Предыдущее состояние RNG | 32 байта состояния и 64-битный счётчик смешиваются через SHA-512 | `random.cpp:340-356, 412-447`; `0x10129eb94` | Не отдельный внешний источник | Высокая |
| Адрес стека и высокоточные часы | Дополнительное перемешивание fast/slow seed | `random.cpp:60-77, 463-482` | Не требуется как самостоятельный источник | Высокая |
| События | SHA-256 хеш событий и младшие 32 бита performance counter | `random.cpp:369-396`; `0x10129f184` | Отсутствие событий не блокирует OS RNG | Высокая |
| Динамическое окружение | Часы, resource usage, sysctl, адреса стека/кучи | `randomenv.cpp:193-270`; `0x10129f3e4` | Best effort; ошибки отдельных данных не отменяют `getentropy` | Высокая |
| Статическое окружение | Компилятор/версия, hostname, интерфейсы, OS/filesystem, env, process IDs | `randomenv.cpp:272-474`; `0x10129f7f4` | Best effort при startup | Высокая |
| Усиление SHA-512 | 100 мс при первом startup, 10 мс периодически | `random.cpp:234-260, 505-553`; `0x10129f228` | Не заменяет обязательный OS RNG | Высокая |
| RdRand/RdSeed | Не входят в ARM64 build | `random.cpp:80-232`; соответствующие строки отсутствуют в Mach-O | Неприменимо к этой сборке | Высокая |
| `RAND_screen`, Windows CryptoAPI, OpenSSL PRNG | Отсутствуют | Нет соответствующих imports/strings | Неприменимо | Высокая |

### Проверки при запуске

`RandomInit()` вызывается из `kernel::Context`:

- `work/modern/bitcoin-32.1/src/kernel/context.cpp:15-22`;
- `RandomInit`, Ghidra `0x10129ed84`.

`NodeImpl::baseInitialize` затем вызывает `AppInitSanityChecks`:

- `work/modern/bitcoin-32.1/src/node/interfaces.cpp:129-142`;
- `work/modern/bitcoin-32.1/src/kernel/checks.cpp:15-22`;
- функция проверки, Ghidra `0x1001d19bc`.

`Random_SanityCheck` находится по адресу `0x10129e974`, а соответствующий `getentropy` вызывается в `0x10129e9cc`. Проверка подтверждает, что вывод перезаписывается и performance counter изменяется. Сам комментарий прямо оговаривает: **качество энтропии этот тест не измеряет** (`random.cpp:641-684`).

### Защита от тестового детерминированного RNG

В `GetStrongRandBytes` машинный код передаёт `SLOW=1` и `always_use_real_rng=1` в `ProcRand` (`0x10129e684`). Это соответствует `random.cpp:607-610`: тестовый `MakeDeterministic` не должен подменять вывод для приватных ключей. Уровень уверенности — высокий.

### Поведение при ошибках

- **`getentropy` вернул ошибку:** аварийное завершение, ключ не возвращается. Подтверждено исходником и машинной веткой.
- **Sanity check не пройден:** инициализация узла прекращается с `OS cryptographic RNG sanity check failure`.
- **Отдельные environment-данные недоступны:** сбор продолжается best effort; криптографическая опора остаётся на обязательном OS RNG.
- **32-байтовый кандидат равен нулю или не входит в допустимый диапазон secp256k1:** новый 32-байтовый кандидат запрашивается в цикле.
- **Ошибка записи SQLite:** создание кошелька завершается ошибкой/исключением, а не сообщается как успешное.

Fault injection для `getentropy`, sysctl и filesystem не выполнялся; таблица описывает подтверждённый код обработки, а не результаты таких экспериментов.

## 7. BIP32 и descriptors

`CExtKey::SetSeed` вычисляет HMAC-SHA512 с ключом `"Bitcoin seed"`:

- левая половина результата становится приватным master key;
- правая — chain code;
- `nDepth = 0`, `nChild = 0`.

Источник: `key.cpp:381-392`; Ghidra `0x101173a58`. В декомпиляции видны строка `"Bitcoin seed"`, 64-байтовый результат и разделение на две 32-байтовые части.

Создаются четыре внешних и четыре внутренних descriptors (`outputtype.h:18-31`, `wallet.cpp:3650-3658`):

| Тип | Путь после master xpub |
|---|---|
| Legacy | `/44h/{coin}h/0h/{0|1}/*` |
| P2SH-SegWit | `/49h/{coin}h/0h/{0|1}/*` |
| Bech32 | `/84h/{coin}h/0h/{0|1}/*` |
| Bech32m / Taproot | `/86h/{coin}h/0h/{0|1}/*` |

Здесь `coin=0` для mainnet, `coin=1` для test chains; ветка `0` внешняя, `1` внутренняя. Источник: `walletutil.cpp:35-86`.

Один и тот же `master_key` передаётся всем восьми descriptor managers. Восемь ключевых записей не означают восемь независимых seed.

Стандартный `DEFAULT_KEYPOOL_SIZE = 1000` на descriptor (`scriptpubkeyman.h:64`). В лаборатории он был намеренно уменьшен до 4; `getwalletinfo` показал 16 внешних и 16 внутренних ключей, что согласуется с четырьмя output types.

## 8. Сохранение приватного материала

### Незашифрованный кошелёк

`AddDescriptorKeyWithDB` вызывает:

```text
WriteDescriptorKey(desc_id, pubkey, key.GetPrivKey())
```

Источники:

- `scriptpubkeyman.cpp:1199-1228`;
- `walletdb.cpp:227-233`;
- Ghidra `0x100d6c300`, `0x100de3880`.

`GetPrivKey()` создаёт SEC1 DER-представление, но делает это собственным кодом Bitcoin Core, без OpenSSL (`key.cpp:87-156, 171-181`). Комментарий в `key.h:136-140` исторически называет формат OpenSSL; это не вызов библиотеки OpenSSL.

В лабораторном файле обнаружено ровно 8 записей `walletdescriptorkey`, каждая со значением длиной 247 байт. Это согласуется с 214-байтовым compressed SEC1 DER, 32-байтовым контрольным хешем и сериализацией.

### Зашифрованный при создании кошелёк

Путь отличается порядком операций:

1. `CWallet::CreateNew` пропускает первичную генерацию (`wallet.cpp:3162-3165`).
2. `EncryptWallet` получает 32-байтовый encryption master key и 8-байтовую соль через `GetStrongRandBytes` (`wallet.cpp:850-868`; Ghidra `0x100da80dc`).
3. KDF настраивается примерно на 100 мс, минимум 25 000 итераций (`wallet.cpp:565-604`; `crypter.h:45-59`).
4. После включения encryption keys создаются descriptors и приватный master seed уже записывается через `WriteCryptedDescriptorKey`.
5. База переписывается через `VACUUM`, чтобы уменьшить вероятность остатков незашифрованного материала в slack space (`wallet.cpp:912-918`; `sqlite.cpp:349-354`).

Алгоритмы (`crypter.cpp:15-145`, `crypter.h:14-30`):

- passphrase → итеративный SHA-512 с солью;
- encryption master key → AES-256-CBC;
- IV для приватного ключа — первые 16 байтов double-SHA256(public key);
- количество раундов подбирается динамически, в лаборатории получилось 311 951 (`qt-regtest.log:138`).

Это legacy-совместимый, CPU-only KDF, а не memory-hard Argon2/scrypt. Это **особенность реализации и компромисс совместимости**, а не подтверждённая в данном анализе уязвимость.

### SQLite

`CreateWallet` принудительно устанавливает `DatabaseFormat::SQLITE` (`wallet.cpp:400-403`). Таблица:

```sql
CREATE TABLE main(key BLOB PRIMARY KEY NOT NULL, value BLOB NOT NULL)
```

Источник: `sqlite.cpp:311-346`. Запись параметров и значений выполняется через prepared statements в `SQLiteBatch::WriteKey`, Ghidra `0x100d9f948`.

На POSIX выставляется `umask(0077)` (`common/system.cpp:85-88`). Оба лабораторных `wallet.dat` получили режим `0600`.

## 9. Динамический изолированный эксперимент

Запускался именно исследуемый `Bitcoin-Qt.app`, а не `bitcoind`:

- сеть: `regtest`;
- отдельный `datadir` внутри рабочего каталога;
- `listen=0`, `connect=0`, `dnsseed=0`, `discover=0`, `natpmp=0`;
- нестандартные P2P/RPC-порты;
- `QT_QPA_PLATFORM=cocoa`, `splash=0`;
- искусственные RPC credentials и passphrase;
- `keypool=4`.

Созданы кошельки `unencrypted-lab` и `encrypted-lab`, получен regtest-адрес, затем вызван `stop`. Журнал подтверждает `Shutdown done`, отключение DNS seeding и отсутствие peer-соединений. Публичная сеть была исключена конфигурацией regtest и перечисленными флагами; отдельный packet capture не выполнялся.

Плагины `offscreen` отсутствуют, а `minimal` в этой среде завершился с segfault после инициализации. Успешный эксперимент выполнен через нативный `cocoa`; это ограничение среды, а не дефект генерации ключей.

Результаты SQLite (`evidence/modern/dynamic/sqlite-summary.json`):

| Проверка | Обычный кошелёк | Зашифрованный кошелёк |
|---|---:|---:|
| `PRAGMA integrity_check` | `ok` | `ok` |
| Режим файла | `0600` | `0600` |
| descriptors | 8 | 8 |
| `walletdescriptorkey` | 8 | 0 |
| `walletdescriptorckey` | 0 | 8 |
| `mkey` | 0 | 1 |
| Размер key value | 247 байт | 49 байт для ckey |
| `walletdescriptorcache` | 8 | 8 |
| `walletdescriptorlhcache` | 8 | 8 |

Проверялись типы, количество и длины записей, а не приватные значения. Отсутствие незашифрованных ключей подтверждено на уровне логических SQLite-записей; полный криминалистический анализ освобождённых страниц файла не проводился.

После формирования сводки искусственные `lab-data` удаляются. JSON-результаты RPC и журнал сохранены в `evidence/modern/dynamic/`.

## 10. Сравнение с Bitcoin 0.1.0

| Свойство | Bitcoin 0.1.0 | Bitcoin Core 32.1.0 | Уверенность |
|---|---|---|---|
| Генератор ECC | OpenSSL `EC_KEY_generate_key`; `key.h:75-79`; EXE `0x008b3020`, DLL `0x61e09510` | RNG Core + `secp256k1_ec_seckey_verify`; `0x10117364c`, `0x101262210` | Высокая |
| Основная энтропия | `RAND_screen`, perfmon, OpenSSL `RAND_poll`/CryptoAPI | macOS `getentropy` + собственное смешивание Core | Высокая |
| Ошибка базового OS RNG | Исторические ветки зависели от OpenSSL; подробности в `bitcoin-0.1.0-key-audit.md` | Немедленный `abort` | Высокая для 32.1 |
| Ключи на адреса | Независимые `CKey` | Детерминированное BIP32-развёртывание одного seed | Высокая |
| Master seed | Отсутствует | HMAC-SHA512 `"Bitcoin seed"` | Высокая |
| Адреса | P2PKH, uncompressed pubkey по умолчанию | Четыре output types, compressed keys, включая Taproot | Высокая |
| Хранилище | Berkeley DB, записи `key`; `db.h:378-387`, `main.cpp:65-81`; `AddKey 0x0041c00e` | SQLite, descriptors, `walletdescriptorkey/ckey` | Высокая |
| Шифрование кошелька | Отсутствует | AES-256-CBC + динамический SHA-512 KDF | Высокая |
| Защита ключа в памяти | Обычные объекты OpenSSL/CKey | `secure_unique_ptr`, locked pool и очистка перед освобождением | Высокая |
| Подпись ECDSA | OpenSSL `ECDSA_sign`, `key.h:121-130` | RFC6979 через libsecp256k1, low-R grinding и повторная проверка подписи, `key.cpp:210-235` | Высокая по исходникам |

Дополнительная защита ключа в памяти подтверждается `key.h:55-75` и `support/allocators/secure.h:16-42, 55-76`: память очищается перед освобождением, а locked pool предназначен для предотвращения swapping. Это не гарантирует отсутствие всех временных копий в других компонентах.

Старый GUI-майнер `BitcoinMiner` в современном коде отсутствует. В 32.1 создание блоков вынесено в node/RPC (`rpc/mining.cpp`, включая `generatetoaddress` и `generateblock`), а не в фоновый CPU-поток Qt.

## 11. Подтверждённые особенности, дефекты и гипотезы

### Подтверждённые особенности реализации

1. Один BIP32 seed используется для всех восьми descriptors; приватный master key сохраняется отдельной записью для каждого descriptor.
2. Получение нового адреса не требует нового вызова `getentropy`: адреса выводятся детерминированно из seed.
3. Strong RNG не полагается только на состояние процесса или предсказуемые environment-данные: свежий `getentropy` обязателен при каждом вызове.
4. В ARM64 build нет x86 RdRand/RdSeed.
5. Environment-данные хешируются локально; сам RNG-код не отправляет их в сеть.
6. Для ECDSA nonce формируется по RFC6979, поэтому свежая системная энтропия не нужна при каждой подписи так же, как при использовании старого OpenSSL `ECDSA_sign`.
7. Шифрованный при создании кошелёк не содержит логических записей `walletdescriptorkey`.

### Подтверждённые дефекты

В исследованном пути дефектов, позволяющих предсказать или восстановить приватный ключ, не обнаружено. Это не утверждение об отсутствии уязвимостей во всём приложении.

### Гипотезы и ограничения

- Поведение `getentropy` при реальном отказе не проверялось динамически; вывод об `abort` подтверждён статически.
- Качество энтропии ядра macOS статистически не измерялось и не требуется для подтверждения архитектуры fail-closed.
- Не доказано, что все временные копии ключевого материала во всех библиотеках очищаются; подтверждены механизмы очистки непосредственно в Core.
- Не проводился полный аудит сетевого стека, консенсуса, Qt, SQLite и libsecp256k1.
- Ручные имена stripped-функций основаны на нескольких независимых признаках; это не восстановление исходной таблицы символов.

## 12. Безопасное воспроизведение

### Проверка происхождения

```bash
export APP=/path/to/Bitcoin-Qt.app
shasum -a 256 "$APP/Contents/MacOS/Bitcoin-Qt"
codesign --verify --deep --strict --verbose=2 "$APP"
spctl -a -vv "$APP"
"$APP/Contents/MacOS/Bitcoin-Qt" -version
```

Ожидаемый SHA-256 исполняемого файла: `4d9270db7f19ecb6c375730eecf673dd6bc6d6a1cf2ff3a55bd13b89cba63a05`.

### Повтор навигации Ghidra

```bash
analyzeHeadless "$PWD/work/ghidra" bitcoin-core-32.1 \
  -process Bitcoin-Qt -noanalysis \
  -scriptPath "$PWD/scripts/modern" \
  -postScript LabelModernFunctions.java
```

Проверочные адреса: `0x10117364c`, `0x10129e684`, `0x10129e378`, `0x10129f0a4`, `0x10129f104`, `0x101262210`, `0x100dc2ab8`, `0x100d6c300`, `0x100d9f948`. Полная процедура импорта приведена в [методике](methodology.md).

### Правила лабораторного эксперимента

- Использовать только `regtest` и отдельный пустой `datadir`.
- Оставить `listen=0`, `connect=0`, `dnsseed=0`, `discover=0`, `natpmp=0`.
- Использовать искусственные пароли и никогда не копировать туда реальный `wallet.dat`.
- Не экспортировать приватные ключи; для проверки хранилища достаточно типов, количества и длин SQLite-записей.
- После `stop` проверить `PRAGMA integrity_check` в read-only подключении.
- Удалить искусственные кошельки после формирования обезличенной сводки.

## 13. Файлы результата

- `docs/bitcoin-core-32.1-wallet-audit.md` — этот отчёт;
- `work/ghidra/bitcoin-core-32.1.gpr` — создаваемый при воспроизведении размеченный проект, не включён в репозиторий;
- `scripts/modern/` — воспроизводимые Ghidra-скрипты;
- `work/modern/SHA256SUMS` — скачиваемый официальный манифест;
- `work/modern/bitcoin-32.1.tar.gz` — исходники релиза;
- `work/modern/bitcoin-32.1-arm64-apple-darwin.zip` — официальная сборка для сравнения;
- `evidence/modern/` — imports, strings, Mach-O metadata и декомпиляции;
- `evidence/modern/dynamic/` — RPC-ответы, журнал и SQLite-сводка без вывода приватных ключей.
