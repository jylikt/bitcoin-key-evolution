# Bitcoin Key Evolution

**Реверс-инжиниринг генерации ключей, источников энтропии и хранения `wallet.dat` — от черновика 2008 года до Bitcoin Core 32.1.**

Исследовательский проект объединяет статический анализ оригинальных бинарников, сопоставление с историческими исходниками, декомпиляцию Ghidra, изолированный regtest-эксперимент и хронологию личных комментариев Сатоши Накамото.

> **English abstract.** A source- and binary-assisted study of Bitcoin key generation and wallet storage, comparing the surviving January 2009 Windows build and OpenSSL 0.9.8h with modern Bitcoin Core 32.1 on macOS ARM64. Includes Ghidra scripts, decompilation evidence, an isolated regtest experiment, and a Russian annotated timeline of Satoshi Nakamoto's design comments.

## Основные результаты

- **Идентификация ранней сборки.** Исследованный пакет `bitcoin-0.1.0.rar` содержит обновление от **10 января 2009 года**: `VERSION=101`, About показывает `0.1.1 Alpha`. Это не подтверждённая launch-day сборка 9 января.
- **Ранние ключи.** Путь восстановлен до `EC_KEY_generate_key()` внутри поставляемой `libeay32.dll` с OpenSSL 0.9.8h. Подтверждённого дефекта, неизбежно делающего ключи предсказуемыми на нормально работающих Windows 2000/XP, не найдено.
- **Условие риска, а не доказанная уязвимость.** Счётчик энтропии OpenSSL — эвристика. Сценарий свежей или клонированной VM с отказом CryptoAPI требует отдельного эксперимента; слабость исторических ключей из него не следует.
- **Современная сборка.** Локальный `Bitcoin-Qt.app` побайтово совпал с официальным Bitcoin Core 32.1 ARM64 ZIP. OpenSSL в ключевом пути больше нет: используются собственный RNG, обязательный macOS `getentropy`, libsecp256k1 и один BIP32 seed для восьми descriptors.
- **Fail-closed.** Ошибка `getentropy` в исследованном современном пути ведёт к `abort`, а не к генерации ключа на пониженной энтропии.
- **Хранение.** Ранняя версия сохраняет незашифрованный SEC1 DER в Berkeley DB. Современная — descriptor keys в SQLite; в зашифрованном лабораторном кошельке найдены только `walletdescriptorckey`, но не логические записи `walletdescriptorkey`.
- **История замыслов.** Подготовлены 100 переведённых и прокомментированных записей за 2008–2010 годы: сомнения, TODO, компромиссы, privacy, marketplace, reputation, RPC и восстановление кошелька.

Подтверждённые наблюдения, дефекты, гипотезы и ограничения разделены в каждом отчёте. Это целевые исследования ключевого пути, а не утверждение об отсутствии уязвимостей во всём Bitcoin Core.

## Документы

| Материал | Содержание |
|---|---|
| [Аудит Bitcoin 0.1.x](docs/bitcoin-0.1.0-key-audit.md) | EXE + OpenSSL 0.9.8h DLL, полный путь до `EC_KEY_generate_key`, Windows entropy sources, Berkeley DB |
| [Аудит Bitcoin Core 32.1](docs/bitcoin-core-32.1-wallet-audit.md) | ARM64 Mach-O, GUI → RNG → BIP32 → descriptors → SQLite, подпись и regtest |
| [Комментарии Сатоши](docs/satoshi-comments-timeline.md) | 100 оригиналов, русский перевод, первое появление и эволюция незавершённых идей |
| [Методика и воспроизведение](docs/methodology.md) | Версии, хеши, команды Ghidra, построение хронологии |
| [Лабораторная программа](docs/experiments.md) | Безопасные fault-injection и snapshot-тесты с искусственными ключами |
| [Хеши артефактов](docs/artifact-hashes.md) | Идентификаторы исследованных файлов и официальных архивов |

## Структура

```text
docs/                 Исследовательские отчёты и методика
data/                 Машинная хронология из 312 кандидатов комментариев
scripts/legacy/       Ghidra-скрипты для bitcoin.exe и libeay32.dll
scripts/modern/       Ghidra-скрипты для Bitcoin Core 32.1 ARM64
scripts/historical/   Извлечение комментариев, first/last seen и git blame
evidence/legacy/      Текстовые карты и декомпиляции ключевого пути
evidence/modern/      Декомпиляции, Mach-O metadata и обезличенный regtest
```

Оригинальные приложения, RAR/ZIP-архивы, полные деревья исходников, Ghidra-базы и экспериментальные `wallet.dat` намеренно не включены. Их можно получить из указанных источников и проверить по хешам. Текстовые доказательства достаточны для навигации, а скрипты — для повторного анализа.

Полный 4,6-МБ dump строк современного бинарника без сокращений приложен к релизу `v1.0.0`; ссылка и SHA-256 находятся в [evidence/modern/strings-offsets.md](evidence/modern/strings-offsets.md).

## Быстрый старт

1. Прочитать [краткий вывод раннего аудита](docs/bitcoin-0.1.0-key-audit.md#краткий-вывод) и [современного аудита](docs/bitcoin-core-32.1-wallet-audit.md#1-краткий-результат).
2. Сверить исследуемые файлы с [манифестом хешей](docs/artifact-hashes.md).
3. Повторить нужную часть по [инструкции](docs/methodology.md). Готовые адреса Ghidra и имена функций уже находятся в отчётах и label-скриптах.
4. Для исторической хронологии использовать только Python 3, Git и архивы исходников; внешние Python-зависимости не нужны.

Пример проверки официального современного приложения на macOS:

```bash
APP=/Applications/Bitcoin-Qt.app
shasum -a 256 "$APP/Contents/MacOS/Bitcoin-Qt"
codesign --verify --deep --strict --verbose=2 "$APP"
spctl -a -vv "$APP"
```

Ожидаемый SHA-256 исследованного исполняемого файла: `4d9270db7f19ecb6c375730eecf673dd6bc6d6a1cf2ff3a55bd13b89cba63a05`.

## Этические границы

- Не исследуются реальные чужие кошельки и существующие адреса.
- Не выполняются подбор или восстановление приватных ключей, поиск средств и сканирование сети.
- Динамические проверки допускаются только в offline/regtest среде с новыми искусственными ключами.
- Публичная сеть Bitcoin не использовалась. В современном эксперименте были заданы `regtest`, `listen=0`, `connect=0`, `dnsseed=0`, `discover=0`, `natpmp=0`; packet capture отдельно не выполнялся.
- Публикуемые SQLite-результаты содержат типы, количество и длины записей, но не приватные значения.

## Происхождение и лицензии

Исходные ссылки: [Satoshi Nakamoto Institute](https://satoshi.nakamotoinstitute.org/code/), [Bitcoin Core](https://github.com/bitcoin/bitcoin), [OpenSSL](https://github.com/openssl/openssl), [официальные релизы Bitcoin Core](https://bitcoincore.org/en/download/).

Оригинальные материалы этого репозитория распространяются по [MIT](LICENSE). Цитаты, декомпиляции и хеши сторонних артефактов приведены в исследовательских целях; права на исходные программы и комментарии остаются у их правообладателей и регулируются их лицензиями. Ручные имена stripped-функций — аналитические метки, а не восстановленная таблица символов.

Если материалы используются в публикации, см. [CITATION.cff](CITATION.cff). Правила дополнений — в [CONTRIBUTING.md](CONTRIBUTING.md).
