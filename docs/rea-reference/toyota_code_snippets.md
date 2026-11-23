# Готовый код (Python) — сниппеты из реверса Car Scanner для mycar

Фрагменты перенесены из декомпилированного C# Car Scanner v2.2.52 в Python.
Можно копировать прямо в `mycar/`. Все ссылки на строки — `decompiled/CarScannerMaui/CarScannerMaui.decompiled.cs`.

## 1. Декодирование значения PID (ByteSet) — `CustomPID.DecodeByteSet`, строки 180677–180755

```python
def decode_pid_value(payload: bytes, sbi: int, dl: int, *, signed: bool = False,
                     mul: float = 1.0, div: float = 1.0, ofs: float = 0.0,
                     reversed_bytes: bool = False) -> float:
    """value = raw * MUL / DIV + OFS.

    payload — данные ответа ПОСЛЕ маркера ответа (эхо запроса, напр. '61D9' отрезается).
    sbi  — StartByteId (смещение первого байта, поле SBI профиля).
    dl   — DataLength (1..4; 0 = до конца payload, максимум 4 байта).
    """
    if len(payload) < sbi + dl and dl:
        raise ValueError("payload too short")
    n = dl if dl else len(payload) - sbi
    n = min(n, 4)
    b = payload[sbi:sbi + n]
    if reversed_bytes:                      # RBS=true
        b = b[::-1]
    raw = int.from_bytes(b, "big", signed=signed)
    return raw * mul / div + ofs
```

Пример — ATF/CVT температура C-HR (`21D9`, JOBD JDM CAN, HDR=7DF):
```python
# ответ ELM: "61D9 04 3B ..." → payload после маркера "61D9"
temp_c = decode_pid_value(payload, sbi=4, dl=2, mul=1.0, div=256.0, ofs=-40.0)
```

## 2. Разбор битового значения (TP=2) — `DecodeBitValue`, строка 180662

```python
def decode_bit_value(payload: bytes, sbi: int, bit: int) -> int:
    return 1 if (payload[sbi] >> bit) & 1 else 0   # GetBit0_7
```

## 3. Формула с переменными A..FZ (TP=0, mXparser) — `BuildDictionaryV2`, строка 180797

```python
DICT_LETTERS = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
                "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
                "U", "V", "W", "X", "Y", "Z"] + [...]  # всего 182: A..Z, AA..AZ, ...
# A = payload[0], B = payload[1], ... (индекс буквы = позиция байта)

def eval_formula(payload: bytes, formula: str) -> float:
    """formula напр. '((A*256+B)+35340)/93' — безопасный eval с подстановкой байтов."""
    import ast, operator as op
    env = {name: payload[i] for i, name in enumerate(DICT_LETTERS) if i < len(payload)}
    # или eval(formula, {"__builtins__": {}}, env) при доверенных формулах
    return eval(formula, {"__builtins__": {}}, env)
```

## 4. Декодирование DTC — `DTCItemV2.DecodeRawCode`, строка 215150

```python
_LETTERS = {0: "P", 1: "C", 2: "B", 3: "U"}

def decode_dtc(data: bytes) -> str:
    """2 байта (OBD-II/KWP) или 3 байта (UDS) → 'P0133' / 'P175100'."""
    if len(data) < 2:
        return ""
    b0 = data[0]
    letter = _LETTERS[(b0 >> 6) & 0b11]      # биты 8,7
    digits = [(b0 >> 4) & 0b11, b0 & 0xF, (data[1] >> 4) & 0xF, data[1] & 0xF]
    if len(data) >= 3:
        digits += [(data[2] >> 4) & 0xF, data[2] & 0xF]
    return letter + str(digits[0]) + "".join(f"{d:X}" for d in digits[1:])
```

## 5. Разбор CAN-ответа `03` (список DTC) — `DTCDecoder.DecodeCAN`, строка 214381

```python
def parse_mode03(payload: bytes) -> list[bytes]:
    """payload после маркера '43': первый байт = count, далее пары байт кода."""
    if not payload or payload[0] == 0:
        return []
    count = payload[0]
    codes, i = [], 1
    while i + 1 < len(payload) and len(codes) < count:
        pair = payload[i:i + 2]
        if pair not in (b"\x00\x00", b"\xaa\xaa"):
            codes.append(pair)
        i += 2
    return codes

# dtc_strings = [decode_dtc(c) for c in parse_mode03(payload)]
```

## 6. Конвертация ASCII-hex ELM → байты — `BitHelpers.ConvertHexToBytesX`

```python
import re

def elm_hex_to_bytes(line: str) -> bytes:
    """'7E8 043 01 33' → b'\\x43\\x01\\x33' (пробелы/переводы строк убираются)."""
    hexstr = re.sub(r"[^0-9A-Fa-f]", "", line)   # FilterHexAndNewLineOnly
    # CAN-заголовок 3 байта (7E8) в строке при ATH1 — срезается приложением заранее
    return bytes.fromhex(hexstr)
```

## 7. Стандартные Mode-01 формулы (встроенные, `PIDWithFloatValueFormula`, строки 184429+)

```python
def mode01_value(pid: str, a: int, b: int = 0, c: int = 0, d: int = 0) -> float | str:
    """pid — '010C' без '01' (т.е. '0C'), a..d — байты данных ответа."""
    f = {
        "04": lambda: a * 100 / 255,                       # Calculated load, %
        "05": lambda: a - 40,                              # Coolant temp, °C
        "06": lambda: (a - 128) * 100 / 128,               # STFT B1, %
        "07": lambda: (a - 128) * 100 / 128,               # LTFT B1, %
        "0A": lambda: a * 3,                               # Fuel pressure, kPa
        "0B": lambda: float(a),                            # MAP, kPa
        "0C": lambda: (a * 256 + b) / 4,                   # RPM
        "0D": lambda: float(a),                            # Speed, km/h
        "0E": lambda: (a - 128) / 2,                       # Timing advance, °
        "0F": lambda: a - 40,                              # IAT, °C
        "10": lambda: (a * 256 + b) * 0.01,                # MAF, g/s
        "11": lambda: a * 100 / 255,                       # Throttle, %
        "21": lambda: float(a * 256 + b),                  # Distance with MIL, km
        "2F": lambda: a * 100 / 255,                       # Fuel level, %
        "33": lambda: float(a),                            # Barometric, kPa
        "3C": lambda: (a * 256 + b) / 10 - 40,             # Catalyst temp B1S1, °C
        "42": lambda: (a * 256 + b) / 1000,                # Module voltage, V
        "43": lambda: (a * 256 + b) * 100 / 255,           # Absolute load, %
        "45": lambda: a * 100 / 255,                       # Relative throttle, %
        "46": lambda: a - 40,                              # Ambient temp, °C
        "5C": lambda: a - 40,                              # Engine oil temp, °C
        "5D": lambda: (a * 256 + b - 26880) / 128,         # Injection timing, °
        "5E": lambda: (a * 256 + b) * 0.05,                # Fuel rate, L/h
        "61": lambda: a - 125,                             # Driver demand torque, %
        "62": lambda: a - 125,                             # Actual torque, %
        "63": lambda: float(a * 256 + b),                  # Reference torque, Nm
    }.get(pid)
    return f() if f else None
```

Полная таблица (100 формул + единицы) — `docs/toyota_units_formulas.md` и `extracted/units_pids.json`.

## 8. Поддерживаемые PID (Mode 01, `0100`/`0120`…) — битовая маска

```python
def parse_supported_pids(data: bytes, base_pid: int = 0x00) -> list[int]:
    """data — 4 байта битовой маски; бит 0 (MSB первого байта) = PID base+1."""
    supported = []
    for i in range(32):
        if (data[i // 8] >> (7 - i % 8)) & 1:
            supported.append(base_pid + i + 1)
    return supported
```

## 9. Схема запроса с BeforeCommand (BCM) — как в профилях Toyota

```python
async def send_profile_pid(elm, pid: dict):
    # pid = {"BCM": "ATFCSH700;ATFCSD300000;ATFCSM1;ATCRA710", "CMD": "21D9",
    #        "HDR": "7E0", "SBI": 4, "DL": 2, "MUL": 1, "DIV": 256, "OFS": -40}
    for cmd in (pid.get("BCM") or "").split(";"):
        if cmd.strip():
            await elm.send(cmd.strip())
    if pid.get("HDR"):
        await elm.send("ATSH" + pid["HDR"])     # на клоне с нерабочим ATSH — пропустить, использовать 7DF
    resp = await elm.send(pid["CMD"])
    payload = strip_response_marker(resp, pid["CMD"])   # эхо/код услуги отрезается
    return decode_pid_value(payload, pid["SBI"], pid["DL"],
                            mul=pid.get("MUL", 1), div=pid.get("DIV", 1),
                            ofs=pid.get("OFS", 0), signed=pid.get("SIG", False))

def strip_response_marker(raw: str, request_cmd: str) -> bytes:
    """Отрезает маркер ответа (positive response echo).

    Правило из кода (строка 173187): skip = длина эха в hex-символах:
      '01xx' → 2 байта ('41xx');  '21xx' → 2 байта ('61xx');  '22xxxx' → 3 байта ('62xxxx');
      DTC-режимы → len(ResponseMarker)//2.
    """
    data = elm_hex_to_bytes(raw)
    if request_cmd.startswith("22"):
        skip = 3
    elif request_cmd.startswith(("01", "21")):
        skip = 2
    else:
        skip = 1
    return data[skip:]
```

## 10. Мульти-запросы: объединение PID в один CAN-запрос (`OBDMultiRequest`, строка 174905)

Car Scanner не шлёт PID по одному — он склеивает их (для скорости опроса):

```python
def build_multi_request(commands: list[str], header: str) -> tuple[list[str], str]:
    """['010C','010D','010F'] → ('010C0D0F', ...) — первый запрос целиком,
    у остальных отбрасывается режим (первые 2 hex-символа).

    Возвращает (AT-команды до/после, сама hex-команда).
    """
    cmd = commands[0] + "".join(c[2:] for c in commands[1:])
    before, after = [], []
    if len(cmd) > 14:                      # длинный кадр → ручной ISO-TP
        before += ["ATAL", "ATCAF0", f"ATFCSH{header or '7E0'}", "ATFCSD300000", "ATFCSM1"]
        after  += ["ATCAF1", "ATCFC1", "ATFCSM0"]
    return before, cmd
```

Правила оптимизатора (`OBDRequestQueueOptimizer.OptimizeV2`, строка 175841):
- группировка по заголовку; внутри — сортировка по команде, дедупликация;
- максимум **6 PID** в одном Mode 01 запросе (без STN-сегментации);
- максимум **3** в Mode 22 запросе (`22xxxx` DID);
- `ResponseMarker` берётся от первой команды (`GetResponseMarkerFromCommand`);
- длинные ответы (>7 байт данных) переводятся в ручной ISO-TP (см. before/after выше);
- при ошибке мульти-запрос разбирается обратно на одиночные (`Disassemble`).

Пример склейки для mycar: вместо 3 запросов `010C`, `010D`, `010F` — один `010C0D0F`
(ответ: `41 0C <hi> <lo> 0D <spd> 0F <iat>` — парсить по смещениям).

## 11. Ссылки на ключевые места C# (для углубления)

| Что | Класс / метод | Строки |
|---|---|---|
| Декод значения PID | `CustomPID.DecodeByteSet` | 180677–180755 |
| Little-endian вариант | `CustomPID.DecodeReversedByteSet` | 180576+ |
| Формулы mXparser | `CustomPID.DecodeFormula`, `BuildDictionaryV2` | 180757–180810 |
| Стандартные Mode 01 | `PIDWithFloatValueFormula.PID01xx_*` | 184429–190470 |
| Декод DTC | `DTCItemV2.DecodeRawCode` | 215150–215190 |
| Разбор 03/07/0A | `DTCDecoder.DecodeCAN` | 214381–214420 |
| Разбор UDS 19 | `DTCDecoder.DecodeUDSMode19` | 214308–214380 |
| Статусы DTC | `DTCStatusHelper` | 215300–215380 |
| Имена ECU Toyota | `CAN11bitHelper.GetECUName` | 206800+ (блок Toyota) |
| Инициализация ELM | `OBDDataReader.c()/b()` | 171302–171675 |
| Отправка BCM/ACM | `OBDDataReader.SendBeforeOrAfterCommands` | 170497+ |
| Мульти-запросы | `OBDMultiRequest` | 174905–175060 |
| Оптимизатор очереди | `OBDRequestQueueOptimizer.OptimizeV2` | 175841+ |
