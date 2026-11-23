# Профили Toyota / релевантность для `../mycar` (Toyota C-HR NGX10, 8NR-FTS, 2020)

Важное уточнение: `chr` в пути — это **C-HR**, а не Chery. Машина в `mycar` — Toyota C-HR NGX10 (8NR-FTS), поэтому главный интерес — профили Toyota, а не китайские бренды (хотя все 1331 профиль расшифрованы и доступны).

## 1. Извлечённые профили Toyota
43 профиля → `extracted/toyota_all/*.json` (поля раскрыты, PIDы развёрнуты `v8:` → JSON).
Ключевые:
- **`JOBD JDM CAN`** (alias `ad685c0700ea4f348b9078befe8d5709`, **11 PID**) — JDM-профиль, ближе всего к C-HR 2020.
- **`OBD-II / EOBD ~2010-2022 CAN + extra sensors`** (alias `726eab54fe6f4247a5b0c88d7f9e8174`, **400 PID**) — универсальный Toyota 2010+.
- `OBD-II / EOBD ~2016 - n.d. CAN + extra sensors` (422 PID), `Corolla Hybrid 2020-` (395), `Auris TS Hybrid` (359), `RAV4 Hybrid 2019-` (395), `WWH-OBD ICE (CAN 29 bit)` (287).

## 2. Конфигурация `JOBD JDM CAN` (directly useful)
```
InitSequence:          ATZ\nATE0\nATH1\nATS0\nATM0\nATAT1\r\nATSP6\r\nATSH7E0\r\nATCRA7E8\r\nATST32\r\nATSW00
CheckConnectionCommand: 210C
TesterPresentCommand:   2101
DefaultHeader:          7E0
Mode01Prefix:           21
UseCustomInit:          true
DefaultProtocolSelected: (не задан; ATSH7E0 + ATSP6 = CAN 500k/11bit)
```
Отличие от текущего инита `mycar` (`ATZ ATE0 ATL0 ATS1 ATH0 ATSP6`): Car Scanner включает **ATH1** (заголовки в ответе), **ATCRA7E8** (фильтр приёма), **ATAT1** (авто-форматирование), **ATST32/ATSW00** (тайминги), **ATM0**. Это и есть «правильная» мягкая инициализация для клонов ELM327, которая позволяет физическую адресацию 7E0 — то, чего не умеет прошивка донгла из `mycar/AGENT.md` (ATSH — заглушка).

## 3. Температура ATF/CVT — подтверждение формул
Car Scanner использует несколько variant'ов одного датчика (поле `NM` = `RES(PID_ATF_TEMPERATURE)`):

| Профиль | CMD | HDR | SBI | DL | MUL | DIV | OFS |
|---|---|---|---|---|---|---|---|
| JOBD JDM CAN | `21D9` | 7E0 | 4 | 2 | 0.00390625 (1/256) | 1 | −40 |
| JOBD JDM CAN | `21D9` | 7E1 | 4 | 2 | 1/256 | 1 | −40 |
| JOBD JDM CAN | `21D9` | 7E0 | 4 | 2 | 1 | 256 | −40 |
| JOBD JDM CAN | `21D9` | 7DF | 4 | 2 | 1 | 256 | −40 |
| OBD-II 2010-2022 | `2182` | 7E0 | — | — | 1/256 | 1 | −40 |
| OBD-II 2010-2022 | `2182` | — | — | — | 1/256 | 1 | −40 |
| OBD-II 2016+ | `221627` | 700/701 | — | — | 1/256 | 1 | −40 |

**Вывод для `mycar`:** кастомный PID `21 82` из `mycar/sensors.py` с MUL 1/256 и OFS −40 совпадает с вариантом Car Scanner `2182`. В `JOBD JDM CAN` для JDM-машин используется `21D9` с SBI=4, DL=2 — стоит проверить на машине как альтернативу, когда донгл с рабочим ATSH появится.

### Практический лайфхак для текущего клона ELM327
В том же `JOBD JDM CAN` есть вариант **с функциональной адресацией — без ATSH**:
```
CMD=21D9, HDR=7DF, SBI=4, DL=2, MUL=1, DIV=256, OFS=-40, BIT=1, TP=1
```
То есть `21D9` через `7DF` (широковещательный запрос) даёт температуру ATF `(raw16 / 256) − 40`. Поскольку `mycar/AGENT.md` сообщает, что functional `7DF` на клоне **работает**, а `ATSH` — нет, этот вариант можно попробовать прямо сейчас, без смены адаптера. Дополнительно включить `ATH1` (клон его поддерживает), чтобы видеть заголовки ответов `7E8/7E9`, и `ATCRA7E8` для фильтрации — как в ините Car Scanner.

Полезные поля PID, которые стоит добавить в модель `sensors.py`: `SBI` (start byte index), `DL` (data length), `BIT`, `SIG`, `HDR`, `TP` (type/period), `UN` (unit id).

## 4. Другие JDM-PIDы из `JOBD JDM CAN`
- `21AA` — Engine Oil Temperature (`UN=15`, MUL=0)
- `21F1` — Initial coolant temperature / initial air temperature (`BIT=1`, `SBI=2`)
- `21F3` — Injection Volume (ml) (`BIT=1`) / Injection timing (`HDR=7E0`, `UN=44`)

## 5. Как пользоваться артефактами
```sh
cd /Users/alexqem/projects/chr/rea
.venv/bin/python tools/export_profiles.py --brand Toyota --out extracted/toyota_all
.venv/bin/python tools/export_profiles.py --alias ad685c0700ea4f348b9078befe8d5709 --out extracted/one
```
Каждый JSON: `Profile.InitSequence`, `Profile.CheckConnectionCommand`, `Profile.DefaultHeader`, `Profile.ProfilePIDsExpanded[]` (поля `CMD/HDR/BCM/MUL/DIV/OFS/SBI/DL/BIT/SIG/UN`).

## 6. Что НЕ найдено
`NXG10`/`8NR-FTS` открытым текстом в APK отсутствует (проверено и в DLL, и в AOT, и в DEX). Идентификация машины идёт через VIN/ECU-ID и alias профиля, не через строку модели двигателя.
