# Команды ELM327 для Toyota ≤2020 — быстрый справочник (LLM-карточка)

Компактная выжимка для подглядывания. Полный разбор 127 AT/ST/VT-команд с номерами строк C# —
в `docs/obd_protocol.md` (§2.1–2.3). Здесь — только то, что нужно для Toyota C-HR/Corolla/Prius ≤2020.

## 1. Инициализация адаптера (порядок важен)

### 1.1 Универсальный init Car Scanner (профиль по умолчанию, `OBDReaderProfileV2._InitSequence`)
```
ATZ
ATE0
ATH1
ATSP0
ATS0
ATM0
ATAT1
```
- `ATZ` — сброс; после него выждать ~400 мс, затем прочитать до `>`.
- `ATE0` — без эха (обязательно: иначе парсинг ломается).
- `ATH1` — заголовки ВКЛ: ответы содержат CAN-ID (`7E8…`), без них нельзя отличить ECU.
- `ATSP0` — авто-определение протокола; для C-HR сработает CAN 11bit/500k (= `ATSP6`).
- `ATS0` — без пробелов в hex; `ATM0` — memory off; `ATAT1` — адаптивный тайминг.

### 1.2 Профиль `JOBD JDM CAN` (ближе всего к C-HR NGX10 2020)
```
ATZ
ATE0
ATH1
ATS0
ATM0
ATAT1
ATSP6
ATSH7E0
ATCRA7E8
ATST32
ATSW00
```
| Команда | Смысл |
|---|---|
| `ATSP6` | CAN 11bit / 500 kbaud (ISO 15765-4) |
| `ATSH7E0` | заголовок запроса — физически ECM |
| `ATCRA7E8` | принимать только ответ ECM |
| `ATST32` | timeout = 0x32*4 = 200 мс |
| `ATSW00` | wakeup off (CAN не нужен) |

`CheckConnectionCommand = 210C` (rpm в формате Mode 21), `DefaultHeader = 7E0`,
`UseCustomInit = true`, `Mode01Prefix = 21`, `TesterPresentCommand = 2101`.

### 1.3 Обход для клона ELM327 с нерабочим ATSH (из `mycar/AGENT.md`)
Функциональная адресация `7DF` не требует `ATSH`. Рабочая схема чтения ATF:
```
ATZ
ATE0
ATH1
ATSP6
21D9          (через 7DF, если донгл сам ставит функциональный запрос)
```
Вариант `JOBD JDM CAN` с `HDR=7DF`: `CMD=21D9, SBI=4, DL=2, MUL=1, DIV=256, OFS=-40`
(см. `docs/toyota_pids_catalog.md`, раздел C).

## 2. AT-команды, которые реально нужны Toyota ≤2020

| Команда | Назначение |
|---|---|
| `ATZ` / `ATWS` | полный / тёплый сброс |
| `ATE0` / `ATE1` | эхо выкл/вкл |
| `ATH0` / `ATH1` | заголовки скрыть/показать |
| `ATS0` / `ATS1` | пробелы в hex выкл/вкл |
| `ATL0` / `ATL1` | linefeeds |
| `ATM0` / `ATM1` | memory |
| `ATAT0/1/2` | адаптивный тайминг |
| `ATAL` / `ATNL` | allow long messages / сброс (для 29-bit и длинных ISO-TP) |
| `ATSP0..9,A,B` | выбор протокола: 6 = CAN11/500 (Toyota), 7 = CAN29/500, 3 = ISO9141, 5 = KWP fast, 0 = auto |
| `ATDPN` | узнать номер протокола (после авто-определения) |
| `ATSHxxx` | заголовок запроса: `7E0` ECM, `7E1` TCM (AT), `7DF` функциональный |
| `ATCRAxxx` | фильтр приёма: `7E8` ECM-ответ, `7E9` TCM-ответ |
| `ATAR` | сброс фильтров (принимать всё) |
| `ATCAF0/1` | CAN auto-formatting: для ручного ISO-TP — `0` |
| `ATCFC0/1` | CAN flow control: `1` = авто (ELM сам шлёт FC) |
| `ATFCSH7E0` / `ATFCSD300000` / `ATFCSM1` | ручной FC (для BCM-запросов с `700→710` и т.п.) |
| `ATSTxx` | timeout (единицы 4 мс): `32` = 200 мс, `FF` = максимум |
| `ATSW00` | wakeup off |
| `ATRV` | напряжение бортсети |
| `ATI` | идентификация чипа (проверка клона) |
| `STI` / `VTI` | детекция STN/vlinker прошивок (у клона — `?`) |

Полный список (127 команд, включая STN/VT/VW TP2.0) — `docs/obd_protocol.md` §2.1.

## 3. Форматы запросов OBD (hex без пробелов, завершение `\r`)

| Формат | Пример | Смысл |
|---|---|---|
| Mode 01 (SAE) | `010C` | RPM (стандартный) |
| Mode 01 + prefix | `210C` | то же, но `Mode01Prefix=21` (JOBD JDM CAN) |
| Mode 03 | `03` | сохранённые DTC |
| Mode 07 | `07` | pending DTC |
| Mode 0A | `0A` | permanent DTC |
| Mode 04 | `04` | сброс DTC/индикатора |
| Mode 09 | `0902` / `0904` / `090A` | VIN / Calibration ID / ECU name |
| Toyota Mode 21 | `21D9`, `210C`, `21AA` | датчики Toyota (ATF, rpm, масло) |
| UDS Mode 22 | `22156`, `2211CD` | чтение данных по DID |
| UDS Mode 19 | `1902AF`, `1902AC`, `190FAC` | чтение DTC (confirmed/pending/archive) |
| UDS Mode 14 | `14FFFFFF` | сброс DTC |
| Tester present | `3E`, `2101` (в профилях Toyota) | удержание сессии |

Ответ ELM (ATH1, ATS0): строки вида `7E8 06 41 0C 1A F8 ...` или `7E8 043 ...` (ISO-TP).
Многострочные ответы ELM собирает сам (CAN auto-formatting вкл) — приложение парсит готовый hex.

## 4. Как приложение шлёт запрос (полезно для mycar)

1. Перед запросом выполняются `BCM` (BeforeCommand) — набор AT-команд через `;`,
   например `ATFCSH700;ATFCSD300000;ATFCSM1;ATCRA710` (смена заголовка/фильтра под конкретный ECU).
2. Сам запрос: hex-команда + `\r`.
3. После ответа — `ACM` (AfterCommand), часто `ATSPDEF`/`ATAR;ATFCSM0` (возврат в авто-режим).
4. `CheckConnectionCommand` (напр. `210C` или `0100`) определяет, «жив» ли профиль;
   `ShouldCheckProfilePIDs=true` — дополнительно проверяются профильные PID перед показом.
5. Ошибки ELM (`NO DATA`, `STOPPED`, `CAN ERROR`, `BUS INIT ERROR`, `UNABLE TO CONNECT`, `?`)
   обрабатываются в `OBDResponseAnalyzer`; `?` = клон не понял команду (см. `mycar/AGENT.md`).

## 5. Порядок байт в ответе и маркер ответа

- ELM возвращает ASCII-hex; приложение конвертирует в байты (`BitHelpers.ConvertHexToBytesX`).
- Маркер ответа — positive-response echo: `(service+0x40)` + эхо PID
  (`010C` → `410C`, `21D9` → `61D9`, `22156` → `62156`; см. `GetResponseMarkerFromCommand`, строка 175000+).
  Он отрезается перед разбором: `01/21xx` → 2 байта, `22xxxx` → 3 байта, DTC-режимы → по `ResponseMarker`.
- Далее payload, который индексирует `SBI` (см. `toyota_units_formulas.md`).
- Исключение: `UseServiceResponseForPositiveResponseMarker` — маркер = service+0x40.
