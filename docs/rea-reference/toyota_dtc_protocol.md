# DTC (коды неисправностей) — как читать и декодировать (Toyota ≤2020)

Как Car Scanner читает/сбрасывает DTC и превращает байты ответа в коды `Pxxxx/Cxxxx/Bxxxx/Uxxxx`.
Справочник самих кодов Toyota — `docs/toyota_dtc_codes.md`. Источник: `DTCDecoder` (строки 214270+),
`DTCItemV2` (214835+), `DTCStatusHelper` (215300+), `BaseCAN11bitECU` (206800+).

## 1. Команды чтения/сброса DTC

| Режим | Команды (по порядку опробования) | Тип |
|---|---|---|
| Сохранённые (confirmed) | `03` | OBD-II Mode 03 |
| Pending | `07` | OBD-II Mode 07 |
| Permanent | `0A` | OBD-II Mode 0A |
| Freeze frame | `02` (с PID из запроса) | OBD-II Mode 02 |
| KWP-чтение | `1800FF00`, `1802FF00`, `1802FFFF`, `1800FFFF`, `18FF00`, `17FF00`, `13FF00` | ISO 14230 |
| UDS-чтение | `1902AF`, `1902AC`, `19028D`, `190223`, `190278`, `190208`, `190FAC`, `190F8D`, `190F23`, `19D2FF00` | UDS Mode 19 |
| Сброс | `14`, `14FF00`, `14FFFFFF`, `140000`, `14FFFF` | Mode 14 / UDS 14 |

Порядок: сначала запросы «ECU существует» (`3E`, `3E01`, `3E00`), затем список `ReadDTCCommands`.
Если UDS-запрос `19xx` ответил — остальные запросы к этому ECU из очереди убираются.

### Режим DTCModeV2 (настройка профиля, поля `drm`/`dcm`)
```
Auto (0)          — стандартный список выше
NissanConsult2 (1)
AddToAuto (2)     — DTCReadingSequence ДОБАВЛЯЕТСЯ к авто-списку
ReplaceAuto (3)   — DTCReadingSequence ЗАМЕНЯЕТ авто-список
```
`DTCReadingSequence` / `DTCClearingSequence` — строки с командами через `;`
(для KWP-профилей Toyota: `ATSH8111F1;1800FF00` и т.п.).

## 2. Декодирование ответа — алгоритм

### 2.1 Как выбирается формат (`DTCDecoder.DecodeData`, строка 214272)
| Команда | Формат разбора |
|---|---|
| `03`, `07`, `0A`, `13` | CAN (если протокол CAN) или ISO (K-Line) |
| `19xxxx` | UDS Mode 19 |
| `18xxxx`, `17xxxx` | KWP Mode 18 |
| `13xxxx` (не CAN) | ISO |
| `1201` | GM12 |
| `A3` / Consult2 | Nissan Consult 2 |

### 2.2 CAN-разбор (`DecodeCAN`) — для `03/07/0A` на C-HR
```
payload = <байты ответа после маркера>
первый байт = количество кодов (0 → пусто)
далее пары байт: код = 2 байта
пропускать 00 00 и AA AA
статус: 03 → confirmed, 07 → pending, 0A → permanent
```
Пример: ответ `7E8 06 43 01 33 02 44 83` → payload после `43`: `01 33 02 44 83`
(первый байт — count, затем пары `0133`, `0244`, `83xx`).

### 2.3 UDS Mode 19 (`DecodeUDSMode19`)
```
пропустить 1 байт (кроме 19D2 на KWP-протоколах)
затем группы: 3 байта кода + 1 байт статуса
пропускать AA AA AA и 00 00 00
для протоколов 1/2 (J1850): код = 2 байта + статус 3-м байтом
```
Команды `190Fxx` — «архивные» DTC (`IsArchive=true`).

### 2.4 KWP Mode 18 (`DecodeKWPMode18`)
```
первый байт = количество (0 → пусто)
группы: 2 байта кода + 1 байт статуса
```

## 3. Байты кода → строка кода (`DTCItemV2.DecodeRawCode`, строка 215150)

2 байта (OBD-II/KWP) или 3 байта (UDS):

```python
def decode_raw_code(data: bytes) -> str:
    # data[0]: биты 8,7 (1-based) → буква; биты 6,5 → первая цифра
    b = data[0]
    hi = (b >> 6) & 0b11          # биты 7-8
    letters = {0: "P", 1: "C", 2: "B", 3: "U"}
    letter = letters[hi]
    mid = (b >> 4) & 0b11          # биты 5-6 → цифра 0..3
    digits = [b & 0xF, (data[1] >> 4) & 0xF, data[1] & 0xF]
    if len(data) >= 3:             # UDS: 6 hex-символов
        digits += [(data[2] >> 4) & 0xF, data[2] & 0xF]
    return letter + str(mid) + "".join(f"{d:X}" for d in digits)
```
Результат: `P0133`, `C0244`, `U0100`… Для UDS 3-байтовый код: `P175100` = `P1751` + расширение `00`.
Особые случаи: VAG — пересчёт через `GetVAGCode` (5 цифр), BMW — показ RawCode как есть.

## 4. Статусные биты (`DTCStatusHelper`)

UDS (`19xxxx`, байт статуса):
| Бит | Имя | Смысл |
|---|---|---|
| 0 | testFailed | отказ сейчас |
| 1 | testFailedThisOperationCycle | отказ в этой поездке |
| 2 | pendingDTC | неподтверждённый |
| 3 | confirmedDTC | подтверждённый (MIL) |
| 4 | testNotCompletedSinceLastClear | тест не завершён после сброса |
| 5 | testFailedSinceLastClear | был отказ после сброса |
| 6 | testNotCompletedThisOperationCycle | тест не завершён в поездке |
| 7 | warningIndicatorRequested | запрос лампы MIL |

KWP (`17/18/13`): `bit5_DTCStorageState` — архивный; `bit6_validatedFaultPresent` — подтверждённый.
Mode `0A` — `obd_0A_permanent` (постоянный, стирается только после ремонта).
Архивными считаются: `190Fxx`; UDS `1902xx` где только биты 3/5 без 0/1/2/7; OBD `0A`.

## 5. Связь ECU ↔ ответ (кто прислал код)

`DTCItemV2.ECU` = имя ECU по заголовку ответа (`CAN11bitHelper.GetECUName`):
| Ответ | ECU (Toyota) |
|---|---|
| `7E8` | Engine |
| `7E9` | Transmission |
| `7B8` | ABS |
| `788` | SRS/Airbag |
| `7C8` | Dashboard |
| `758` | Gateway (или BCM при расширенном адресе `40`) |
| `7CC` | Climate |
| `7A9` | EMPS (электроусилитель) |
| `799`, `798` | Radar cruise control |
| `70B`, `789` | Solar Charging Control |
| `72E` | Start/stop |
| `70D` | Rear motor generator |
| `74D` | Plug-in Control |
| `74F` | HV Battery |

## 6. Практика для mycar

1. Подключение → `ATH1` (видеть заголовки) → `03` → парсить пары байт после первого count-байта.
2. Для C-HR 2020 достаточно Mode 03/07/0A + `decode_raw_code` из §3.
3. Описания кодов — `docs/toyota_dtc_codes.md` (1406 Toyota-записей + generic),
   машиночитаемо — `extracted/dtc_json/toyota_dtc_merged.json`.
4. Сброс: `04` (OBD-II) или `14FFFFFF` (UDS); на клоне ELM327 без `ATSH` — через `7DF`.
