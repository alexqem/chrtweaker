# OBD-II Mode 01 PID: что снимаем и как считаются значения

Стандарт SAE J1979 (ISO 15031-5). Mode 01 = текущие данные. Наша таблица опроса — `mycar/sensors.py`; формулы ниже — по стандарту SAE J1979 / Wikipedia OBD-II PIDs.

## Опрашиваемые PID (стандартные, есть на любом исправном ELM327)

| Колонка CSV | PID | Название | Формула | Единицы |
|---|---|---|---|---|
| rpm | 0x0C | Engine RPM | ((A·256)+B)/4 | rpm |
| speed | 0x0D | Vehicle speed | A | km/h |
| coolant | 0x05 | Coolant temp | A−40 | °C |
| oil_temp | 0x5C | Engine oil temp | A−40 | °C |
| iat | 0x0F | Intake air temp | A−40 | °C |
| map | 0x0B | Intake manifold abs. pressure | A | kPa |
| baro | 0x33 | Barometric pressure | A | kPa |
| maf | 0x10 | Mass air flow | ((A·256)+B)/100 | g/s |
| load | 0x04 | Calculated engine load | 100·A/255 | % |
| stft | 0x06 | Short term fuel trim B1 | (A−128)·100/128 | % |
| ltft | 0x07 | Long term fuel trim B1 | (A−128)·100/128 | % |
| timing | 0x0E | Timing advance | A/2−64 | ° |
| throttle | 0x11 (rel: 0x45) | Throttle position (relative) | 100·A/255 | % |
| rail_p | 0x23 | Fuel rail pressure (direct) | ((A·256)+B)·10 | kPa |
| voltage | 0x42 | Control module voltage | ((A·256)+B)/1000 | V |
| runtime | 0x1F | Time since engine start | (A·256)+B | s |
| cvt_temp | **01B4** / 21D9 / 2182 (Toyota ext., функциональный 7DF) | CVT fluid temp K313/K114 | см. ниже | °C |
| cvt_raw | — | hex-отдача 01B4/21D9/2182 (для отладки) | — | — |

## Кастомные Toyota PID (Mode 21) — температура масла CVT

Стандартный Mode 01 не отдаёт температуру вариатора K313/K114. На платформе TNGA 2016+ (C-HR NGX10 2020, Corolla E210 и др.) ECM **релеит** данные TCM и отвечает на запрос Mode 21 PID 0x82 **по своему же заголовку 7E0** — переключение `AT SH 7E1` не требуется.

| Поле | Значение |
|---|---|
| Заголовок | `7E0` (ECM) |
| Режим | `21` (manufacturer-enhanced) |
| PID | `82` → команда `2182` |
| Ответ | `61 82 <payload…>` |

### Формулы декодирования (варианты упаковки из каталога Car Scanner)

Декодер `mycar/toyota.py::decode_atf` перебирает документированные варианты (приоритет = режим с максимальными шансами на клоне; полный список — `docs/07`):

1. **`01B4` (Mode-01 расширение Toyota, функциональный — как обычный `01xx`)**: `DL=2`: `°C = raw/256 − 40` (v.15) или `DL=1`: `°C = A − 40` (v.16)
2. **JOBD JDM CAN `21D9`**: payload после `61 D9`, `SBI=4, DL=2`: `°C = raw/256 − 40`
3. **Vitz CVT `21D9`**: `SBI=3, DL=1`: `°C = A − 40`
4. **`2182` Pan / Post-Converter**: `SBI=0|2, DL=2`: `°C = raw/256 − 40`
5. **Legacy (Torque-сообщество, obd2australia)**: `°F = (raw·0.07 − 400)/10` → `°C` — как кандидат

Все байты ответа пишутся в колонку `cvt_raw` — при первом реальном ответе сверься с ней (на холодной CVT ≈ уличная температура и растёт медленнее ECT; абсурдные значения → другой вариант упаковки, разберём по raw + `mycar probe`).

⚠ **Ограничение текущего адаптера (2026-11-23):** BLE-клон «v1.5» не умеет менять TX-заголовок (ATSH — заглушка с фейковым OK; физическая адресация 7E0 невозможна). Обход — функциональная адресация: `21D9` через `7DF` (вариант `HDR=7DF` из профиля `JOBD JDM CAN`, реверс Car Scanner, `../rea`). **Экспериментально и не подтверждено на машине** (проверяется `python -m mycar probe`); `21 82` через 7DF опрашивается как фолбэк. Подтверждения формул: obd2australia (Toyota 2016+, `7E0/21/82`), Torque-профили владельцев Corolla Axio E160 2017 (CVT K312, `2182 @ 7E0`), ToyotaNation (FJ 2011+, Camry 2012), каталог Car Scanner `toyota_pids_catalog.md` (раздел B0).

### Шаблон добавления нового PID (из Torque Pro CSV)

```
Torque PID "2182"        -> command=b"2182"            # mode+pid hex
Torque header "7E0"      -> header=b"7E0" (default)    # иначе AT SH посылает сама библиотека
Torque equation          -> decoder=def(messages): ... # payload = messages[0].data[2:]
```

См. рабочий пример `CVT_FLUID_TEMP` в `mycar/sensors.py`. Библиотека сама переключает заголовки (`AT SH`) с кэшированием — сырой pyserial и ручные `AT Z/AT SP 6/AT H1` не нужны (python-obd делает это на этапе подключения).

## Производные (считаются в analyze.py, не опрашиваются)

- **boost = map − baro** (kPa) — на 8NR-FTS вакуумный wastegate на ХХ держит ~−60 kPa (вакуум), положительный наддув виден под нагрузкой.
- **ΔT cooling rate** — °C/мин по колонке coolant.

## Примечания для NGX10 (Toyota)

- Протокол: **ISO 15765-4 (CAN 11 bit / 500 kbps)** — ELM327 обычно определяет автоматически (`ATSP0`).
- Часть «фирменных» параметров Toyota (давление ТНВД low/high контуров, CVT fluid temp, HVAC data list) лежит **вне стандарта Mode 01** — читается только дилерским Techstream/GTS или профильными расширенными PID. Их отсутствие в нашем логе — норма, не неисправность адаптера.
- Если ответ `NO DATA` по отдельному PID — ЭБУ его не отдаёт; сравнивать с нормами нельзя, см. docs/03 (раздел про недокументированные параметры).

## Источники

- SAE J1979 / ISO 15031-5 (стандарт); обзорная таблица: Wikipedia «OBD-II PIDs»
- python-obd документация (команды, соединения, кастомные заголовки `AT SH`): https://python-obd.readthedocs.io
- **Toyota ATF/CVT temp PID 21 82**: ToyotaNation «Transmission Temp PID for TorquePro» (2022); FJCC forum (FJ Cruiser, формула ×0.07); obd2australia.com.au «Auto Transmission Temperature PIDs» (Toyota 2016+: mode 21, PID 82, 7E0); drive2.ru (Corolla E210 1.6 CVT, PID 2182 — та же платформа TNGA/K-серия, что у NGX10). Вариант «Toyota 2021+: mode 22, PID 1AAA, ECU 700» — для 2021+ и новее, к нашему 2020 году не применяется.
- ⚠ Все кастомные PID — из сообщества, заводом не документированы: первое чтение на машине сверять по `cvt_raw` и вменяемости динамики.
