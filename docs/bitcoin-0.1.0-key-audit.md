# Bitcoin 0.1.x — второй этап: генерация ключей и энтропия

## Область и критерии достоверности

Исследованы только распакованный `bitcoin-0.1.0.rar`, его исходники, `bitcoin.exe`, поставляемая `libeay32.dll` и исходники OpenSSL с тега `OpenSSL_0_9_8h`. Реальные кошельки и существующие адреса не исследовались; публичная сеть Bitcoin не использовалась.

Уровни:

- **Высокая** — подтверждено исходником и машинным кодом/таблицей вызовов Ghidra.
- **Средняя** — поведение следует из кода, но зависит от конкретной Windows/API и не проверялось динамически.
- **Гипотеза** — сценарий допустим кодом, но факт слабых или повторных ключей не доказан.

Сокращения путей:

- `BTC` = `bitcoin-0.1.0/src/`
- `OSSL` = `work/openssl-0.9.8h/`
- Адреса `004…`/`008…` относятся к `bitcoin.exe`, адреса `61d…`/`61e…` — к `libeay32.dll`.

## Краткий вывод

**На нормально работающих Windows 2000/XP подтверждённого дефекта, неизбежно приводящего к слабым приватным ключам, не найдено.** Используется штатный OpenSSL PRNG, дополнительно подключаются Windows CryptoAPI, системное состояние, таймеры и данные Bitcoin. При недостаточной учтённой энтропии ключевой путь завершается ошибкой, а не сохраняет заведомо слабый ключ.

При этом счётчик энтропии является эвристикой. NetAPI и Toolhelp могут отметить состояние как seeded без участия CryptoAPI, хотя реальная непредсказуемость этих данных на свежей или клонированной VM не доказана. Это **условие риска**, требующее лабораторной проверки, а не подтверждённая уязвимость исторических ключей.

## 1. Точная атрибуция бинарников

| Наблюдение | Исходник | Ghidra | Уверенность |
|---|---|---|---|
| Исследуемый пакет — обновление от 10 января 2009, а не launch-day сборка. `VERSION=101`, About отображает `0.1.1 Alpha`, хотя архив называется 0.1.0. | `BTC/serialize.h:22`; `evidence/legacy/ui.cpp:1239-1242` | `bitcoin.exe`: строка версии `.rdata 00993ce7`; startup `CMyApp_OnInit2 004687e8` | Высокая |
| SHA-256 exe `fbcac071…30d2d`, MD5 RAR `91e2dfa2…5500`, SHA-1 RAR `ec9ed4cc…90dd` совпадают с независимым воспроизводимым rebuild и опубликованными хешами. | Локальные хеши; внешняя сверка: [t4t5/bitcoin-0.1-rebuilt](https://github.com/t4t5/bitcoin-0.1-rebuilt) | Весь импортированный `bitcoin.exe` | Высокая |
| Проверенной launch-day копии для сравнения нет; утверждать, что её PRNG отличался или совпадал, нельзя. | Внешняя оговорка в [описании rebuild](https://github.com/t4t5/bitcoin-0.1-rebuilt) | — | Высокая для ограничения вывода |
| DLL — именно OpenSSL 0.9.8h от 28 May 2008, MinGW/i486; в строке компиляции видны отключённые алгоритмы, но SHA-1 и EC присутствуют. | `BTC/readme.txt:34-55`; строки DLL | `SSLeay_version 61d81180`; строка `61e33000` | Высокая |

PE timestamps: exe содержит время 10 Jan 2009 23:16 UTC в исследовании rebuild; DLL — 28 Aug 2008. Отображение `objdump` в локальной timezone может показывать другую календарную дату.

## 2. Полный путь создания первого кошелька

### 2.1. Инициализация до генерации

```text
Статический CInit
  util.cpp:26-42 / Ghidra 008b4f2c
  ├─ установка OpenSSL locking callback
  ├─ RAND_screen()
  │    util.cpp:38 / bitcoin thunk 005ccfc0
  │    └─ libeay RAND_screen 61dace80
  │         ├─ RAND_poll 61dac610
  │         └─ readscreen только для NT service
  └─ RandAddSeed(true)
       util.cpp:41,57-86 / Ghidra 00401444

CMyApp::OnInit2
  ui.cpp:2885,2984-2989 / Ghidra 004687e8
  └─ LoadWallet()
       db.cpp:580-604 / Ghidra 00412bb0
       ├─ CWalletDB("cr").LoadWallet()
       │    db.cpp:495-577 / Ghidra 00411fb4
       │    └─ CDB open/close дополнительно вызывают RandAddSeed()
       │         db.cpp:39-112,114-130
       └─ если defaultkey отсутствует:
            keyUser.MakeNewKey()
              db.cpp:592-600
```

`RAND_screen()` не выставляет внутренний `initialized` флаг `md_rand`. Поэтому первый `RAND_bytes()` выполняет ещё один `RAND_poll()`, даже после начального `RAND_screen()`: `OSSL/crypto/rand/md_rand.c:379-383`, Ghidra `ssleay_rand_bytes 61dc50e0`. **Уверенность: высокая.**

### 2.2. Генерация пары ключей

```text
LoadWallet / GenerateNewKey / BitcoinMiner
  ├─ LoadWallet:      db.cpp:595       / 00412bb0
  ├─ GenerateNewKey:  main.cpp:75-82   / 0041c582
  └─ BitcoinMiner:    main.cpp:2188-2189 / 0042f028

CKey::CKey
  key.h:49-54 / 008b3618
  └─ EC_KEY_new_by_curve_name(NID_secp256k1)
       bitcoin thunk 005cd0a8 → libeay 61e090c0

CKey::MakeNewKey
  key.h:75-79 / 008b3020
  └─ EC_KEY_generate_key
       bitcoin thunk 005cd0c0 → libeay 61e09510
       └─ EC_GROUP_get_order
       └─ BN_rand_range(priv_key, order)
            ec_key.c:258-264
            libeay export 61d998e0 → internal bn_rand_range 61d99730
            └─ BN_rand → bnrand
                 bn_rand.c:118-154,211-214
                 internal bnrand 61d99440
                 ├─ RAND_add(time, entropy=0)
                 └─ RAND_bytes
                      libeay 61dabfe0
                      └─ default RAND_SSLeay 61dc4ce0
                      └─ ssleay_rand_bytes 61dc50e0
                           ├─ RAND_poll при первом вызове
                           └─ success только при entropy >= 32
       └─ повтор при d=0; d ∈ [1, order-1]
       └─ EC_POINT_mul: pub = d·G
            ec_key.c:261-279 / 61e09510
```

Rejection sampling в `BN_rand_range` и исключение нуля дают штатный выбор скаляра без очевидного усечения до пароля, timestamp или 32/64-битного seed. **Источники:** `OSSL/crypto/bn/bn_rand.c:229-300`, `OSSL/crypto/ec/ec_key.c:233-292`; **Ghidra:** `61d99730`, `61e09510`; **уверенность: высокая.**

### 2.3. Сохранение в wallet.dat

```text
AddKey
  main.cpp:65-73 / Ghidra 0041c00e
  ├─ CKey::GetPrivKey → i2d_ECPrivateKey
  │    key.h:89-99 / 008e38a8
  │    bitcoin thunk 005cd0e8 → libeay 61e08550
  ├─ mapKeys[pubkey] = DER_private_key
  ├─ mapPubKeys[Hash160(pubkey)] = pubkey
  └─ CWalletDB.WriteKey(pubkey, privkey)
       db.h:384-387
       Ghidra 008c986c
       └─ CDB::Write(pair("key", pubkey), privkey, DB_NOOVERWRITE)
            db.h:72-96
            └─ Berkeley DB Db::put

LoadWallet затем отдельно записывает defaultkey=pubkey
  db.cpp:598-600 / Ghidra LoadWallet 00412bb0
  Ghidra WriteDefaultKey path 008c9300
```

Приватный ключ хранится как DER `ECPrivateKey`, без пароля и без шифрования. Это **особенность хранения, не дефект PRNG**. **Источники:** `BTC/db.h:378-397`, `BTC/key.h:89-99`; **Ghidra:** `008e38a8`, `008c986c`; **уверенность: высокая.**

При загрузке существующего кошелька новый ключ не создаётся: публичный и приватный ключ читаются и передаются в `SetPubKey`/`SetPrivKey`: `BTC/db.cpp:548-560,586-591`; Ghidra `CWalletDB_LoadWallet 00411fb4`, `LoadWallet 00412bb0`, `d2i_ECPrivateKey 008b31d8`. **Уверенность: высокая.**

## 3. Все найденные источники случайности

| Источник | Данные и оценка энтропии | Исходник | Ghidra | Уверенность |
|---|---|---|---|---|
| Windows CryptoAPI, обычный CSP | 64 байта от `CryptGenRandom`, но `RAND_add(..., 0)`: материал смешивается, счётчик не растёт | `OSSL/crypto/rand/rand_win.c:335-366` | `RAND_poll 61dac610`; refs `61dac6f1`, `61dac6fc` | Высокая |
| Intel Hardware CSP | 64 байта, оценка 64 байта, если отдельный провайдер установлен и работает | `rand_win.c:368-380` | `61dac74f` | Высокая для кода; средняя для наличия в исторической системе |
| NetAPI Workstation/Server | Структуры статистики; оценки 45 и 17 байт | `rand_win.c:250-275` | `61dac610`, начало тела | Высокая |
| Toolhelp heap | Heap list: 3; heap entry: 5, до 80 entries на heap | `rand_win.c:483-516` | `61dac610`; `CreateToolhelp32Snapshot` ref `61dac880` | Высокая |
| Toolhelp processes/threads/modules | Оценки 9/6/9 за entry | `rand_win.c:518-554` | `61dac610` | Высокая |
| GUI state | Foreground window 0; cursor 2; queue 1 | `rand_win.c:386-433` | `61dac610` | Высокая |
| OpenSSL timer | QPC с оценкой 0; GetTickCount fallback с 0 | `rand_win.c:632-669` | вызовы в `RAND_poll 61dac610` | Высокая |
| Memory/PID | GlobalMemoryStatus 1; PID 1 | `rand_win.c:567-576` | конец `RAND_poll 61dac610` | Высокая |
| Screen | SHA-1 экранных bitmap strips, оценка 0; только Windows NT service, не обычный desktop | `rand_win.c:625-629,689-755` | `RAND_screen 61dace80` | Высокая |
| Bitcoin QPC | PerformanceCount, оценка 1.5 | `BTC/util.cpp:57-63` | `RandAddSeed 00401444`, QPC call `0040145d` | Высокая |
| Bitcoin perfmon | `HKEY_PERFORMANCE_DATA/Global`, SHA-256 результата; оценка `min(nSize/500,32)` | `BTC/util.cpp:65-85` | `RandAddSeed 00401444`, registry call `00401571` | Высокая |
| Bitcoin GUI mouse | x и y по 0.25 за событие; перед этим `RandAddSeed()` | `evidence/legacy/ui.cpp:365-370` | `CMainFrame_OnMouseEvents 0043b14e` | Высокая |
| OpenSSL PID в output | PID дополнительно участвует в SHA-1 при выдаче | `OSSL/crypto/rand/md_rand.c:330-332,458-463` | `ssleay_rand_bytes 61dc50e0` | Высокая |
| Время при BN_rand | `time_t` смешивается с оценкой 0 при каждом BN random | `OSSL/crypto/bn/bn_rand.c:141-154` | `bnrand 61d99440` | Высокая |

Не найдены вызовы Bitcoin к `RAND_event`, `RAND_load_file`, `RAND_write_file` или `RAND_pseudo_bytes` для ключей. Соответствующие импорты в `bitcoin.exe` отсутствуют; путь использует `RAND_screen`, `RAND_add`, `RAND_bytes`. **Источник:** полный поиск `BTC/*.cpp,*.h`; **Ghidra:** таблица импортов и `Stage2Map`; **уверенность: высокая.**

## 4. Реализация PRNG в поставляемой DLL

### 4.1. Алгоритм

`RAND_get_rand_method` выбирает engine или, при его отсутствии, `RAND_SSLeay`: `OSSL/crypto/rand/rand_lib.c:86-107`, Ghidra `61dabcf0`, `61dc4ce0`. В DLL таблица `_rand_ssleay_meth` находится по `61e2f7b0`:

```text
seed         → 61dc50c0
bytes        → 61dc50e0  ssleay_rand_bytes
cleanup      → 61dc4cf0
add          → 61dc4d60  ssleay_rand_add
pseudo_bytes → 61dc54d0
status       → 61dc5530
```

Параметры: SHA-1, 20-byte digest, 1023-byte state, counters, entropy double, threshold 32 байта. **Источники:** `OSSL/crypto/rand/rand_lcl.h:115-143` (`ENTROPY_NEEDED=32`, `USE_SHA1_RAND`), `md_rand.c:136-170`; **Ghidra:** `61dc4d60`, `61dc50e0`; **уверенность: высокая.**

`ssleay_rand_add` хеширует предыдущий digest, участок state, новые данные и counter, XOR-ит digest в state и увеличивает оценку энтропии: `md_rand.c:190-314`; Ghidra `61dc4d60`. `ssleay_rand_bytes` выдаёт данные 10-byte частями и обновляет состояние: `md_rand.c:321-508`; Ghidra `61dc50e0`. **Уверенность: высокая.**

SHA-1 здесь не является самостоятельным доказательством слабых ключей: известные collision-атаки на SHA-1 не равны восстановлению секретного состояния этого PRNG. **Уверенность: высокая.**

### 4.2. Отказы и fail-open/fail-closed

| Поведение | Исходник | Ghidra | Уверенность |
|---|---|---|---|
| `RAND_poll()` всегда возвращает 1 и не сообщает, какие источники отказали. | `OSSL/crypto/rand/rand_win.c:567-582` | `61dac610` | Высокая |
| `RAND_screen()` и Bitcoin `RandAddSeed()` — void; Bitcoin не вызывает `RAND_status()` перед ключом. | `BTC/util.cpp:26-86`; `BTC/key.h:75-79` | `008b4f2c`, `00401444`, `008b3020` | Высокая |
| Первый `ssleay_rand_bytes` сам вызывает `RAND_poll`, затем сравнивает счётчик с 32. | `OSSL/crypto/rand/md_rand.c:379-388` | `61dc50e0` | Высокая |
| При недостаточной энтропии `RAND_bytes` возвращает 0 и `PRNG_NOT_SEEDED`. Буфер уже мог быть заполнен, поэтому игнорировать return code опасно. | `md_rand.c:452-507` | `61dc50e0`; строка `PRNG not seeded 61e43d50` | Высокая |
| Ключевой путь проверяет ошибку: `RAND_bytes<=0 → bnrand=0 → BN_rand_range=0 → EC_KEY_generate_key=0 → Bitcoin key_error`. | `bn_rand.c:145-154,229-300`; `ec_key.c:261-264`; `BTC/key.h:75-79` | `61d99440`, `61d99730`, `61e09510`, `008b3020` | Высокая |
| При таком отказе `AddKey` не вызывается из нормального пути; новый валидный ключ в wallet.dat не сохраняется. | `BTC/db.cpp:592-600`; `BTC/main.cpp:75-82` | `00412bb0`, `0041c582` | Высокая |
| Bitcoin `GetRand()` и request-ID код не проверяют `RAND_bytes`; это дефект обработки ошибок вне генерации приватных ключей. | `BTC/util.cpp:295-307`; `BTC/net.h:711-747` | `GetRand 00402486`, RAND_bytes call `0040250f` | Высокая |

Таким образом, ключевой путь **fail-closed по внутреннему счётчику**, но счётчик не является доказательством фактической энтропии.

## 5. Исторические конфигурации Windows

Динамический запуск не выполнялся: на машине отсутствуют Wine и QEMU. Ниже — ожидаемое поведение, восстановленное по коду, а не результат лабораторного запуска.

| Конфигурация | Ожидаемое поведение | Доказательства | Уверенность |
|---|---|---|---|
| Windows XP SP3, desktop, нормальные CryptoAPI/NetAPI/Toolhelp | Множественные источники; ожидается `RAND_status=1` и успешная генерация. Экран не читается, потому что процесс не service. | `rand_win.c:386-560,625-629,704-705`; Ghidra `61dac610`, `61dace80` | Средняя без запуска |
| Windows 2000 SP4, desktop | Аналогично; Toolhelp доступен, cursor разрешён. Комментарий OpenSSL о возможном сбое ADVAPI относится к старому registry-коду, который в OpenSSL отключён. | `rand_win.c:280-287,406-422`; Ghidra `61dac610` | Средняя |
| Windows NT 4.0 | Toolhelp отсутствует, cursor намеренно отключён. При отказе NetAPI/Intel и других источников счётчик может остаться <32; тогда ожидается `EC_KEY_generate_key=0`, а не слабый ключ. | `rand_win.c:406-413,435-488`; `md_rand.c:388,499-507`; Ghidra `61dac610`, `61dc50e0` | Средняя |
| NT/2000/XP service | USER32-источники пропускаются; `readscreen` разрешён только здесь. Toolhelp и остальные источники остаются. | `rand_win.c:386-388,704-705`; Ghidra `61dac610`, `61dace80` | Средняя |
| Windows 9x | Не входит в заявленные NT/2000/XP. Toolhelp/CryptoAPI предусмотрены OpenSSL, но экран пропускается по `GetVersion`. | `BTC/readme.txt:18-23`; `rand_win.c:435-488,704-705` | Средняя |
| Все названные источники искусственно отключены | Ожидается `PRNG_NOT_SEEDED`, исключение `key_error`, отсутствие нового wallet key. | `md_rand.c:499-507`; `key.h:75-79`; Ghidra `61dc50e0`, `008b3020` | Средняя до лабораторного подтверждения |
| CryptoAPI отказал, но NetAPI/Toolhelp возвращают правдоподобные данные | PRNG может признать состояние seeded благодаря эвристическим оценкам. Фактическая стойкость зависит от непредсказуемости этих структур. | `rand_win.c:250-275,483-554`; Ghidra `61dac610` | Гипотеза риска |

Отдельная особенность: OpenSSL отключил свой старый perfmon-registry блок из-за сообщений о hang на NT4, но Bitcoin самостоятельно вызывает `RegQueryValueEx(HKEY_PERFORMANCE_DATA,"Global")`: `OSSL/crypto/rand/rand_win.c:280-333`; `BTC/util.cpp:65-85`; Ghidra `00401571`. Это потенциальная проблема надёжности, не доказательство слабого PRNG. **Уверенность: высокая для кода, средняя для исторического hang.**

## 6. Подтверждённые дефекты, гипотезы и особенности

### Подтверждённые дефекты

1. **Непроверенный return code `RAND_bytes` в `GetRand` и network request IDs.** Это не путь EC private key. Доказательства: `BTC/util.cpp:295-307`, `BTC/net.h:711-747`; Ghidra `00402486`, `008b5fa0`, `008b61a4`; уверенность высокая.
2. **Нет явной проверки `RAND_status` перед созданием кошелька.** Нижние слои всё равно проверяют `RAND_bytes`, поэтому это не обход fail-closed. Доказательства: `BTC/util.cpp:26-86`, `BTC/key.h:75-79`; Ghidra `008b4f2c`, `008b3020`; уверенность высокая.
3. **Приватные ключи в wallet.dat не шифруются.** Доказательства: `BTC/db.h:384-387`, `BTC/key.h:89-99`; Ghidra `0041c00e`, `008c986c`; уверенность высокая. Это отдельный риск хранения, не энтропии.

### Гипотезы, не доказанные как исторические уязвимости

1. **Клонированная/свежая VM + отказ CryptoAPI:** NetAPI/Toolhelp могут дать достаточный формальный счётчик при малой фактической непредсказуемости. Нужны тесты повторяемости только с искусственными ключами. Доказательства предпосылки: `rand_win.c:250-275,483-554`; Ghidra `61dac610`; уровень — гипотеза.
2. **Одинаковые входы при восстановлении snapshot:** повторные публичные ключи возможны только если ОС/PRNG не добавили свежей непредсказуемости; код Bitcoin сам это не доказывает. Уровень — гипотеза.
3. **Перенос Debian CVE-2008-0166 на этот Windows-бинарник:** не подтверждён и противоречит происхождению DLL. См. следующий раздел.

### Особенности реализации, не являющиеся дефектами сами по себе

- CryptoAPI default CSP добавляет 64 байта с нулевой оценкой: `rand_win.c:354-360`, Ghidra `61dac610`; уверенность высокая.
- `RAND_screen` на обычном desktop фактически сводится к `RAND_poll`, без чтения экрана: `rand_win.c:625-629,704-705`, Ghidra `61dace80`; уверенность высокая.
- Первый ключ использует полный secp256k1 scalar и rejection sampling, а не усечённый seed: `ec_key.c:261-279`, `bn_rand.c:229-300`; Ghidra `61e09510`, `61d99730`; уверенность высокая.
- Майнер генерирует ключ заранее, но сохраняет его через `AddKey` после найденного блока; после этого сразу создаёт следующий: `BTC/main.cpp:2188-2189,2328-2331`; Ghidra `BitcoinMiner 0042f028`, xref `00430277`; уверенность высокая.
- Keypool и повторная генерация при запуске отсутствуют; новые адреса создаются по требованию: `BTC/main.cpp:75-82`, `evidence/legacy/ui.cpp:1816-1826`; Ghidra `GenerateNewKey 0041c582`; уверенность высокая.

## 7. Сопоставление с историческими ошибками

### CVE-2008-0166 / Debian OpenSSL weak keys

Официальное описание относится к Debian-пакетам OpenSSL 0.9.8c-1 до исправления в 0.9.8g-9 и к специфичному Debian-изменению PRNG: [NVD CVE-2008-0166](https://nvd.nist.gov/vuln/detail/cve-2008-0166).

Исследуемая DLL — Windows MinGW build официального OpenSSL 0.9.8h, а не Debian package. Её `RAND_poll` использует Windows API, а код генерации ключа не содержит Debian source patch. **Переносить CVE-2008-0166 на этот артефакт нельзя.** Доказательства: `BTC/readme.txt:34-55`; Ghidra `SSLeay_version 61d81180`, `RAND_poll 61dac610`; уверенность высокая.

### Другие исторические классы

- Android SecureRandom и ошибки отдельных Bitcoin wallet-приложений относятся к другой платформе и более позднему коду; доказательств связи с этой DLL нет.
- Старый возраст OpenSSL 0.9.8h и использование SHA-1 не доказывают предсказуемость ключей. Нужен конкретный дефект seeding/state, а не только версия библиотеки.
- Комментарии в `rand_win.c` о Windows 2000/NT4 подтверждают проблемы совместимости отдельных API, но не сообщают о предсказуемых ключах Bitcoin.

## 8. Безопасные воспроизводимые эксперименты

### 8.1. Изолированный harness только для libeay32.dll

Не запускать `bitcoin.exe`. Собрать 32-bit Windows probe, который статически или через `LoadLibrary` использует исходную DLL и:

1. Печатает `SSLeay_version(SSLEAY_VERSION)`.
2. Создаёт `EC_KEY_new_by_curve_name(NID_secp256k1)`.
3. Вызывает `RAND_status`, `EC_KEY_generate_key`, повторный `RAND_status`.
4. Записывает только return codes, ошибку OpenSSL и **публичный** ключ искусственной пары; приватный ключ не выводить.
5. Каждый сценарий запускать в новом процессе, поскольку `RAND_poll` вызывается один раз на initialized state.

Ожидание: при полном отказе источников `RAND_status=0`, `EC_KEY_generate_key=0`; при нормальных API — успех. Это проверка fail-closed, не генерация ключей для существующих адресов.

### 8.2. Матрица отказов

Через API hooking в тестовом процессе, не заменяя системные DLL и не изменяя оригинальную `libeay32.dll`, по отдельности и комбинациями отключать:

- `CryptAcquireContextW` / `CryptGenRandom`;
- `NetStatisticsGet`;
- `CreateToolhelp32Snapshot` и связанные функции;
- `QueryPerformanceCounter`;
- `HKEY_PERFORMANCE_DATA/Global` для Bitcoin-side теста;
- USER32 cursor/queue;
- screen `BitBlt` для service-сценария.

Для каждого сценария фиксировать `RAND_status`, return code генерации и наличие/отсутствие записи в искусственный wallet. Хук должен журналировать вызванные источники, чтобы отличить «отказ безопасен» от «другой источник незаметно дал энтропию».

### 8.3. Snapshot/clone test

1. Создать новую offline VM, установить snapshot до первого запуска probe.
2. Сгенерировать N искусственных EC-пар, сохранить только публичные значения и return codes.
3. Восстановить snapshot и повторить.
4. Повторить с нормальным CryptoAPI, затем с отключённым CryptoAPI и включёнными/отключёнными NetAPI/Toolhelp.
5. Сравнить публичные значения на дубликаты; не вычислять адреса реальных кошельков и не сканировать сеть.

Дубликат в таком тесте был бы подтверждением повторяемости лабораторных ключей. Отсутствие дубликатов не доказывает криптографическую стойкость, поэтому дополнительно журналируются входы и счётчик `RAND_status`.

### 8.4. Проверка записи wallet.dat

В отдельном каталоге с искусственным кошельком проверить:

- успешная генерация создаёт записи `key` и `defaultkey`;
- при принудительном `EC_KEY_generate_key=0` запись `key` отсутствует;
- при ошибке Berkeley DB `AddKey=false`, `GenerateNewKey` выбрасывает исключение;
- повторный запуск читает существующий ключ и не вызывает генератор.

Сетевые интерфейсы VM должны быть отключены; тест не должен запускать полный Bitcoin node без необходимости.

## 9. Артефакты

- Ghidra-проект при воспроизведении: `work/ghidra/bitcoin-0.1.0.gpr`, программы `bitcoin.exe` и `libeay32.dll`. База не включена в репозиторий.
- Bitcoin key/RNG map: `evidence/legacy/stage2-bitcoin-map.txt`.
- Bitcoin decompile: `evidence/legacy/stage2-bitcoin-decompile.txt`, `evidence/legacy/stage2-wallet-decompile.txt`.
- Оригинальная DLL exports/xrefs/decompile: `evidence/legacy/stage2-libeay-map.txt`.
- Внутренний PRNG: `evidence/legacy/stage2-prng-decompile.txt`.
- Соответствующие исходники OpenSSL 0.9.8h: `work/openssl-0.9.8h/`.
