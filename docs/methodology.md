# Методика и воспроизведение

## 1. Принцип доказательности

Каждое наблюдение относится к одному из уровней:

- **Высокая уверенность** — официальный хеш/подпись, прямое чтение машинного кода, согласованный исходник или локальный воспроизводимый эксперимент.
- **Средняя / средне-высокая** — поведение следует из кода, но зависит от конкретной ОС либо ручного сопоставления stripped-функций.
- **Гипотеза** — код допускает сценарий, но динамический факт не подтверждён.

Адреса Ghidra относятся только к файлам с хешами из [манифеста](artifact-hashes.md). Для PE используются статические адреса оригинального image base; для Mach-O — VMA при `0x100000000`, без runtime ASLR slide. Адрес из другой сборки нельзя переносить механически.

Декомпиляция — аналитическое представление машинного кода, а не исходный текст. Имена вида `CWallet_CreateNew` в evidence и label-скриптах присвоены исследователем. Сопоставление выполнялось по строкам, вызовам, константам, структуре и официальным исходникам.

## 2. Что опубликовано

- Отчёты с исходными файлом/строкой, адресом Ghidra и уверенностью.
- Ghidra-скрипты, которыми получались карты, декомпиляции и имена.
- Текстовые фрагменты декомпиляции, imports и Mach-O metadata.
- Обезличенный журнал regtest и SQLite-сводку без ключевых значений.
- Машинную хронологию комментариев и скрипты её построения.

Не публикуются приложения, архивы, приватные ключи, `wallet.dat`, RPC credentials, Ghidra-базы и полные копии сторонних исходников. Журнал современного эксперимента прошёл только замену локальных абсолютных путей на `$HOME` и `$LAB_DIR`; временные метки и технические события сохранены.

## 3. Bitcoin 0.1.0 и OpenSSL 0.9.8h

Нужны:

- сохранившийся пакет `bitcoin-0.1.0.rar` с `bitcoin.exe`, `libeay32.dll` и `src/`;
- Ghidra с поддержкой 32-bit x86 PE;
- JDK, поддерживаемый установленной Ghidra;
- исходники [`OpenSSL_0_9_8h`](https://github.com/openssl/openssl/tree/OpenSSL_0_9_8h) для сопоставления DLL.

Проверка идентичности:

```bash
export BTC010_DIR=/path/to/bitcoin-0.1.0
shasum -a 256 "$BTC010_DIR/bitcoin.exe" "$BTC010_DIR/libeay32.dll"
```

Ожидаемые значения указаны в [artifact-hashes.md](artifact-hashes.md). При другом хеше адреса из отчёта не считаются применимыми без нового анализа.

Пример импорта и первичной карты:

```bash
mkdir -p work/ghidra work/legacy
analyzeHeadless "$PWD/work/ghidra" bitcoin-0.1.0 \
  -import "$BTC010_DIR/bitcoin.exe" \
  -scriptPath "$PWD/scripts/legacy" \
  -postScript Stage2Map.java "$PWD/work/legacy/bitcoin-map.txt"

analyzeHeadless "$PWD/work/ghidra" bitcoin-0.1.0 \
  -import "$BTC010_DIR/libeay32.dll" \
  -scriptPath "$PWD/scripts/legacy" \
  -postScript Stage2Map.java "$PWD/work/legacy/libeay-map.txt"
```

Детализация после анализа:

```bash
analyzeHeadless "$PWD/work/ghidra" bitcoin-0.1.0 \
  -process bitcoin.exe -noanalysis \
  -scriptPath "$PWD/scripts/legacy" \
  -postScript Stage2BitcoinDecompile.java "$PWD/work/legacy/bitcoin-decompile.txt"

analyzeHeadless "$PWD/work/ghidra" bitcoin-0.1.0 \
  -process bitcoin.exe -noanalysis \
  -scriptPath "$PWD/scripts/legacy" \
  -postScript Stage2WalletDecompile.java "$PWD/work/legacy/wallet-decompile.txt"

analyzeHeadless "$PWD/work/ghidra" bitcoin-0.1.0 \
  -process libeay32.dll -noanalysis \
  -scriptPath "$PWD/scripts/legacy" \
  -postScript Stage2PRNGDecompile.java "$PWD/work/legacy/prng-decompile.txt"
```

В отчёте `BTC` обозначает `src/` раннего пакета, `OSSL` — корень исходников OpenSSL 0.9.8h.

## 4. Bitcoin Core 32.1 macOS ARM64

Исследованные версии инструментов: Ghidra 12.1.4 и JDK 21. Полный автоматический анализ занял 912 секунд; Ghidra обнаружила 59 572 функции. Это эвристическое число для stripped-бинарника.

Официальные файлы:

```bash
mkdir -p work/modern
curl -fL https://bitcoincore.org/bin/bitcoin-core-32.1/bitcoin-32.1-arm64-apple-darwin.zip \
  -o work/modern/bitcoin-32.1-arm64-apple-darwin.zip
curl -fL https://bitcoincore.org/bin/bitcoin-core-32.1/bitcoin-32.1.tar.gz \
  -o work/modern/bitcoin-32.1.tar.gz
curl -fL https://bitcoincore.org/bin/bitcoin-core-32.1/SHA256SUMS \
  -o work/modern/SHA256SUMS
shasum -a 256 work/modern/bitcoin-32.1-arm64-apple-darwin.zip work/modern/bitcoin-32.1.tar.gz
```

Перед использованием сверить вывод с манифестом и распаковать архивы в отдельные каталоги:

```bash
tar -xf work/modern/bitcoin-32.1.tar.gz -C work/modern
unzip -q work/modern/bitcoin-32.1-arm64-apple-darwin.zip -d work/modern/app
```

Далее (при другом расположении приложения изменить `APP`):

```bash
export APP="$PWD/work/modern/app/Bitcoin-Qt.app"
mkdir -p work/ghidra
analyzeHeadless "$PWD/work/ghidra" bitcoin-core-32.1 \
  -import "$APP/Contents/MacOS/Bitcoin-Qt" \
  -scriptPath "$PWD/scripts/modern" \
  -postScript ModernMap.java "$PWD/work/modern/modern-map.txt"

analyzeHeadless "$PWD/work/ghidra" bitcoin-core-32.1 \
  -process Bitcoin-Qt -noanalysis \
  -scriptPath "$PWD/scripts/modern" \
  -postScript LabelModernFunctions.java
```

Произвольные адреса декомпилируются так:

```bash
analyzeHeadless "$PWD/work/ghidra" bitcoin-core-32.1 \
  -process Bitcoin-Qt -noanalysis \
  -scriptPath "$PWD/scripts/modern" \
  -postScript DecompileAddresses.java "$PWD/work/modern/key-wallet.txt" \
  0x10117364c 0x10129e684 0x10129e378 0x10129f0a4 0x101262210
```

Официальный исходный архив — справочник для сопоставления. Независимая reproducible-build пересборка не выполнялась; побайтовое совпадение установлено между исследованным приложением и официальным ZIP, а не между локально пересобранным и опубликованным бинарником.

## 5. Хронология комментариев

Корпус охватывает `bitcoin-nov08`, `0.1.0`, `0.1.3`, `0.1.5` и сохранённые теги `v0.2.0`–`v0.3.19`. Ранние архивы доступны через [SNI Code](https://satoshi.nakamotoinstitute.org/code/), теги — через [bitcoin/bitcoin](https://github.com/bitcoin/bitcoin).

Рабочая структура:

```text
work/historical/
  bitcoin-git/          Клон bitcoin/bitcoin с историческими тегами
  sources/<version>/    Распакованные исходники каждой версии
  comments/             Создаваемые JSONL-файлы
```

Извлечение для одной версии:

```bash
python3 scripts/historical/extract_comments.py \
  work/historical/sources/0.1.0 \
  work/historical/comments/0.1.0.jsonl
```

После обработки всех 31 версий:

```bash
python3 scripts/historical/build_timeline.py work/historical
```

Результат — `work/historical/comment-timeline.json`. Опубликованный [файл](../data/comment-timeline.json) содержит 312 уникальных кандидата, нормализацию, все позиции, first/last seen и `git blame` там, где доступна Git-история.

Ограничения автоматики:

- регулярное выражение отбирает кандидатов, а не доказывает наличие личного рассуждения;
- «первое появление» означает первый проверенный снимок, а не точный момент записи мысли;
- исчезновение текста не доказывает реализацию задачи;
- человеческий отчёт содержит 100 отобранных записей; перевод и интерпретация отделены от оригинала;
- ранние архивы атрибутированы Сатоши по происхождению, Git-записи — по `git blame`.

## 6. Динамические границы

Современный эксперимент запускал именно исследуемый `Bitcoin-Qt.app` в `regtest` с отдельным `datadir`, `keypool=4`, искусственными credentials/passphrase и запретами соединений. После проверки кошельки удалены. Это не packet-capture-доказательство сетевой изоляции и не полный криминалистический анализ SQLite slack space.

Историческая Windows-матрица не запускалась: в исходной среде не было Wine/QEMU. Поэтому её строки описывают ожидаемое по коду поведение. Безопасная программа проверки приведена в [experiments.md](experiments.md).
