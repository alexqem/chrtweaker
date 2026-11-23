# Toyota PID-каталог (профили ≤2020) — LLM-справочник

> **Курируемый выжим** из `rea/docs/toyota_pids_catalog.md` (реверс Car Scanner,
> см. `../07-rea-integration.md`): оставлены разделы «как пользоваться», профили,
> Mode-01 (`01B4`!), ядро C-HR (B0), варианты ATF/CVT и init JOBD JDM CAN.
> Массивы B2/B3/B4 (полные дампы Mode 21/22, тысячи строк) опущены — они лежат
> в папке `rea/` рядом с проектом (в git не входит). Ссылки вида `docs/obd_protocol.md`
> указывают на доки реверса из `rea/`, не на `mycar/docs/`.

Источник: расшифрованные профили Car Scanner ELM OBD2 v2.2.52 — `rea/extracted/toyota_all/*.json` (43 файла).
Отбор: профили, применимые к Toyota 2020 года и старше. Целевая машина — **Toyota C-HR NGX10 2020 (JDM, 8NR-FTS 1.2T, CVT)**.
Полные данные всех PID включённых профилей (без сжатия) — машиночитаемый файл `rea/extracted/toyota_pids_catalog.json`.
Все значения дословно из JSON профилей; ничего не додумано. Формулы приведены с полной точностью.

## 0. Как пользоваться

- Grep по CMD (напр. `grep 21D9`), по имени (`grep -i "oil temp"`), по коду профиля (`grep WildlHEV`).
- Раздел A — какие профили вошли (init/header/PID count). Раздел B — единый каталог PID (дедуплицированный).
- Раздел C — все варианты ATF/CVT-температуры. Раздел D — init-последовательности ключевых профилей дословно.
- Запрос PID: выставить заголовок из колонки HDR (ATSH…), при наличии BCM — выполнить BCM-команды (см. таблицу BCM ниже), отправить CMD, распарсить ответ по SBI/DL, применить формулу.
- Нужен любой PID из исходных профилей дословно — смотреть `toyota_pids_catalog.json` (поле `PIDs`, все поля включая BCM/ACM/TRNS/FR).

## 1. Формат полей PID

| Поле | Значение |
|---|---|
| `CMD` | hex-команда запроса (напр. `21D9`, `2211CD`) |
| `HDR` | CAN-ID заголовка запроса (`7E0`/`7DF`/`7E1`/`700`/`701`/`7B0`/`750`/29-бит `DA00E0`…). `-` = в профиле не задан |
| `BCM` | before-command строка (AT-команды перед запросом: flow control и т.п., напр. `ATFCSH700;ATFCSD300000;ATFCSM1;ATCRA710`). В строках каталога не повторяется — см. таблицу BCM/ACM ниже |
| `NM` | название датчика (SNM — короткое, в JSON) |
| `MUL`/`DIV`/`OFS` | формула **value = (raw × MUL / DIV) + OFS** (raw — целое из DL байт ответа) |
| `DL` | сколько байт ответа читать (0 = специальный случай: реальная формула в поле `FR`, байты ответа обозначены A,B,C,D,E,F = 1-й…6-й байт данных) |
| `SBI` | индекс первого байта данных в ответе ELM, с которого начинается чтение |
| `SIG` | `SIG` = значение со знаком (signed) |
| `BIT` | номер бита (1..7) в байте — битовый флаг/переключатель |
| `UN` | числовой id единиц измерения (просто сохраняется). Эмпирически по именам датчиков: 15=°C, 16=°F, 6=rpm, 1=km/h, 2=mph, 5=kPa, 10=V, 14=%, 8=g/s (не нормативно, соответствие id→единицам в данных отсутствует) |
| `FR` | строка формулы из приложения; всегда приводится в квадратных скобках рядом с MUL/DIV/OFS |
| `TP`, `MAX`, `MIN`, `RL`, `TRNS`, `ACM`… | прочие поля (тип, пределы, перевод названий и т.д.) — полностью в JSON |

Нотация каталога:
- Строка `A/B` в колонке DL/SBI = `DL/SBI`. Несколько значений через `;` — варианты упаковки одного датчика (одинаковый смысл, разные HDR/SBI/DL/формула).
- Серийная строка `DL/SBI s1..s2 (step n; #k1→s1, #k2→s2)` = датчики с именами, отличающимися только номером (#k1..#k2), одинаковой формулой и DL; SBI растёт линейно, шаг n; точное отображение номер→SBI линейно между указанными концами. Полная развёртка каждого датчика — в JSON.
- Серийная строка `DL/SBI k (одинаковый SBI у всех; различия — в формуле)` = датчики-серия с общей раскладкой ответа; формула каждого члена приведена явно с номером (`#1 A/1000; #2 B/1000; …`, буквы A..H = 1-й…8-й байт данных).
- Варианты формулы `MUL/DIV/OFS` перечислены через ` ; ` (напр. `0.00390625/1/-40 ; 1/256/-40` — один и тот же датчик, два представления).

### Коды профилей (колонка «профили»)

| Код | Профиль |
|---|---|
| 29bitEV | 29 bit EV data profile |
| AurisTS | Auris TS Hybrid |
| AvalonH19 | Avalon Hybrid 2019- |
| Avensis2D | Avensis gen.2 (2004-2007) Diesel |
| CamryH18 | Camry Hybrid 2018- |
| CamryXV50 | Camry Hybrid XV50 |
| CorollaD | Corolla E110, E120 1CD-FTV |
| CorollaH20 | Corolla Hybrid 2020- |
| CorVersoD | Corolla Verso (2004-2007) Diesel |
| GT86 | GT-86 (2012-2021) |
| HighlHy3 | Highlander Hybrid Gen.3 |
| JOBD | JOBD JDM CAN |
| OBD2 | OBD-II / EOBD |
| OBD2K | OBD-II / EOBD ~1999-2008 K-Line/KWP + extra sensors |
| OBD2C09 | OBD-II / EOBD ~2006-2009 CAN + extra sensors |
| OBD2C22 | OBD-II / EOBD ~2010-2022 CAN + extra sensors |
| OBD2C16 | OBD-II / EOBD ~2016 - n.d. CAN + extra sensors |
| Prius3 | Prius Gen.3 2010- |
| Prius2 | Prius Gen. 2 (2004-2009) |
| Prius4 | Prius Gen. IV (2015-) |
| PriusP23 | Prius Prime (2023-n.d.) |
| PriusP | Prius Prime |
| ProAceHDI | ProAce [2.0 HDI ECU: DCM6.2] |
| ProAceSID | Proace [2.0 Diesel ECU SID803A] |
| RAV4H1618 | RAV4 Hybrid 2016-2018 |
| RAV4H19 | RAV4 Hybrid 2019- |
| RAV4P26 | RAV4 Prime PHEV (-2026) |
| RAV4P1925 | RAV4 Prime PHEV (2019-2025) |
| RAV4XA60 | RAV4 XA60 EV/PHEV/HV |
| Tacoma3 | Tacoma gen.3 (2016-n.d.) |
| Vitz09 | Vitz 2009 CVT |
| WildlHEV | Wildlander HEV |

### BCM/ACM строки (before-/after-command), встречаются в профилях

| Строка | Роль | Где (профиль:HDR) |
|---|---|---|
| BCM `ATSP6;ATCEA2A;ATTA2A;ATFCSH750;ATFCSD2A300000;ATFCSM1;` | команды до запроса | 29bitEV:750, RAV4P26:750 |
| BCM `ATSP6` | команды до запроса | 29bitEV:7C0 |
| BCM `ATSP7` | команды до запроса | 29bitEV:DA5AE0, 29bitEV:DA62E0, RAV4XA60:DA00E0, RAV4XA60:DA5AE0, RAV4XA60:DA5BE0, RAV4XA60:DA62E0, WildlHEV:DA00E0, WildlHEV:DA5AE0 |
| BCM `ATCEA2A;ATTA2A;ATFCSH750;ATFCSD2A300000;ATFCSM1;` | команды до запроса | AurisTS:750, AvalonH19:750, CamryH18:750, CamryXV50:750, CorollaH20:750, OBD2C16:750, Prius4:750, PriusP23:750 |
| BCM `ATSP5;ATIB96;` | команды до запроса | OBD2C09:812AF0, OBD2C22:812AF0 |
| BCM `ATCEA2A;ATTA2A;ATFCSH750;ATFCSD2A300000;ATFCSM1;ATCRA758` | команды до запроса | OBD2C22:750 |
| BCM `ATFCSH6A8;ATFCSD300000;ATFCSM1;ATCRA688` | команды до запроса | ProAceHDI:6A8, ProAceSID:6A8 |
| ACM `ATSPDEF;ATCEA;ATFCSM0` | команды после запроса | 29bitEV, RAV4P26 |
| ACM `ATSPDEF` | команды после запроса | 29bitEV, OBD2C09, OBD2C22, RAV4XA60, WildlHEV |
| ACM `ATCEA;ATFCSM0` | команды после запроса | AurisTS, AvalonH19, CamryH18, CamryXV50, CorollaH20, OBD2C16, Prius4, PriusP |
| ACM `ATFCSM0;ATAR;ATCEA` | команды после запроса | OBD2C22 |
| ACM `ATFCSM0;ATAR` | команды после запроса | ProAceHDI, ProAceSID |

---

## A. Профили ≤2020 (сводная таблица)

Самые важные для C-HR 2020 — `JOBD` (JDM CAN), `OBD2C22` (~2010-2022 CAN + extra sensors), `OBD2C16` (~2016- CAN + extra sensors), `CorollaH20` — их PID полностью входят в раздел B (единый каталог).

| Модель | Код | InitSequence (\n экранирован) | CheckConnection | DefaultHeader | PIDs | Пометка |
|---|---|---|---|---|---|---|
| 29 bit EV data profile | 29bitEV | `-` | `-` | `-` | 962 | включён по ТЗ; EV/HV-данные, для C-HR 1.2T не применимы |
| Auris TS Hybrid | AurisTS | `-` | `-` | `-` | 359 |  |
| Avalon Hybrid 2019- | AvalonH19 | `-` | `-` | `-` | 395 |  |
| Avensis gen.2 (2004-2007) Diesel | Avensis2D | `-` | `-` | `-` | 11 |  |
| Camry Hybrid 2018- | CamryH18 | `-` | `-` | `-` | 395 |  |
| Camry Hybrid XV50 | CamryXV50 | `-` | `-` | `-` | 359 |  |
| Corolla E110, E120 1CD-FTV | CorollaD | `-` | `-` | `-` | 15 |  |
| Corolla Hybrid 2020- | CorollaH20 | `-` | `-` | `-` | 395 |  |
| Corolla Verso (2004-2007) Diesel | CorVersoD | `-` | `-` | `-` | 11 |  |
| GT-86 (2012-2021) | GT86 | `-` | `-` | `-` | 3 |  |
| Highlander Hybrid Gen.3 | HighlHy3 | `-` | `-` | `-` | 328 |  |
| JOBD JDM CAN | JOBD | `ATZ\nATE0\nATH1\nATS0\nATM0\nATAT1\r\nATSP6\r\nATSH7E0\r\nATCRA7E8\r\nATST32\r\nATSW00` | `210C` | `7E0` | 11 |  |
| OBD-II / EOBD | OBD2 | `-` | `-` | `-` | 4 |  |
| OBD-II / EOBD ~1999-2008 K-Line/KWP + extra sensors | OBD2K | `-` | `-` | `-` | 55 |  |
| OBD-II / EOBD ~2006-2009 CAN + extra sensors | OBD2C09 | `-` | `-` | `-` | 36 |  |
| OBD-II / EOBD ~2010-2022 CAN + extra sensors | OBD2C22 | `-` | `-` | `-` | 400 |  |
| OBD-II / EOBD ~2016 - n.d. CAN + extra sensors | OBD2C16 | `-` | `-` | `-` | 422 |  |
| Prius Gen.3 2010- | Prius3 | `-` | `-` | `-` | 314 |  |
| Prius Gen. 2 (2004-2009) | Prius2 | `-` | `-` | `-` | 135 |  |
| Prius Gen. IV (2015-) | Prius4 | `-` | `-` | `-` | 49 | включён с пометкой: «G+» из ТЗ прочитан как Gen. IV+; профиль покрывает и 2015-2020 |
| Prius Prime (2023-n.d.) | PriusP23 | `-` | `-` | `-` | 148 | включён с пометкой: название указывает 2023+, но shard ef0c18c9 общий с явно включённым RAV4 Prime PHEV (2019-2025) |
| Prius Prime | PriusP | `-` | `-` | `-` | 36 | включён с пометкой: годы в названии не указаны |
| ProAce [2.0 HDI ECU: DCM6.2] | ProAceHDI | `-` | `-` | `-` | 22 | включён с пометкой: PSA-платформа (фургон), к C-HR не применим |
| Proace [2.0 Diesel ECU SID803A] | ProAceSID | `ATZ\nATE0\nATH1\nATSP6\nATS0\nATM0\nATAT1\r\nATSH6A8\r\nATFCSH6A8\r\nATFCSD300000\r\nATFCSM1\r\nATCRA688\r\n81\r\n1003\r\n2180` | `21C68001` | `6A8` | 37 | включён с пометкой: PSA-платформа (фургон), к C-HR не применим |
| RAV4 Hybrid 2016-2018 | RAV4H1618 | `-` | `-` | `-` | 359 |  |
| RAV4 Hybrid 2019- | RAV4H19 | `-` | `-` | `-` | 395 |  |
| RAV4 Prime PHEV (-2026) | RAV4P26 | `-` | `-` | `-` | 289 | включён с пометкой: shard 93eb4b91 общий с bZ4X (-2025); RAV4 PHV существует с 2020 |
| RAV4 Prime PHEV (2019-2025) | RAV4P1925 | `-` | `-` | `-` | 148 |  |
| RAV4 XA60 EV/PHEV/HV | RAV4XA60 | `ATZ\nATE0\nATH1\nATSP7\nATS0\nATM0\nATAT1\r\nATSHDA00E0\r\n22F400` | `22F400` | `DA00E0` | 729 |  |
| Tacoma gen.3 (2016-n.d.) | Tacoma3 | `-` | `-` | `-` | 4 |  |
| Vitz 2009 CVT | Vitz09 | `-` | `-` | `-` | 2 |  |
| Wildlander HEV | WildlHEV | `ATZ\nATE0\nATH1\nATSP7\nATS0\nATM0\nATAT1\r\nATSHDA00E0\r\n22F400` | `22F400` | `DA00E0` | 729 |  |

### Исключённые профили (новее 2020 / вне ТЗ)

| Модель | Причина |
|---|---|
| GR86 gen.2 (2021-) | исключён ТЗ: машина новее 2020 |
| BZ (2026-) | исключён ТЗ: машина новее 2020 |
| CHR BEV (2026-) | исключён ТЗ: машина новее 2020 |
| BZ3X | исключён ТЗ: машина новее 2020 |
| WWH-OBD EV (CAN 29 bit) (2026-n.d.) | исключён ТЗ: WWH-OBD (2026-) |
| WWH-OBD HEV/PHEV (CAN 29 bit) (2026-n.d.) | исключён ТЗ: WWH-OBD (2026-) |
| WWH-OBD ICE (CAN 29 bit) (2026-n.d.) | исключён ТЗ: WWH-OBD (2026-) |
| Proace Electric | исключён: ProAce Electric — серийно с 2021, новее 2020 |
| bZ4X (-2025) | исключён: bZ4X — с 2022, новее 2020 |
| bZ4X (2026-) | исключён: машина новее 2020 |
| bZ Woodland (2026-) | исключён: машина новее 2020 |

---


### B0. Ядро C-HR (21D9, 2182, 21AA, 21F1, 21F3, 210C, 210D)

Ключевые команды для C-HR NGX10 2020. **21D9** — температура ATF/CVT (CVT-вариант: `21D9` HDR `7E0`, SBI 3, DL 1, value = raw−40, см. Vitz09). **2182** — Transmission Fluid Temp var.2 (pan/post-converter). **21AA** — температура масла. **21F1/21F3** — стартовые температуры/впрыск. **210C/210D** — обороты/скорость: в ProfilePIDsExpanded отсутствуют, используются как CheckConnection (`210C`) и через `Mode01Prefix=21` (стандартные Mode 01 PID запрашиваются режимом 21: 010C→210C обороты, 010D→210D скорость).

| `CMD` | HDR | NM | формула (MUL/DIV/OFS) | DL/SBI | SIG/BIT | UN | профили |
|---|---|---|---|---|---|---|---|
| `2182` | - | (L4) Transmission Fluid Pan Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | Tacoma3 |
| `2182` | - | (L4) Transmission Fluid Post-Converter Temperature | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | Tacoma3 |
| `2182` | 7E0 | A/T Oil Pressure | 1/1/0 [FR:(C*256+D)/512-64] | 1/0 | - | 74 | OBD2C22 |
| `2182` | 7E0 | G Sensor | 1/1/0 [FR:(E*256+F)*5/4096] | 1/0 | - | 10 | OBD2C22 |
| `2182` | -/7E0/7E1 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 ; 2/2 | BIT1 / BIT5 | 15 | OBD2C09,OBD2C22 |
| `2182` | - | RES(PID_ATF_TEMPERATURE) (Pan) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C16 |
| `2182` | - | TCM] RES(PID_ATF_TEMPERATURE) (Post-Converter) | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | OBD2C16 |
| `2182` | - | Transmission Fluid Pan Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `2182` | - | Transmission Fluid Post-Converter Temperature | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `21AA` | - | Engine Oil Temperature | 0/1/0 [FR:(M)-40] | 0/0 | - | 15 | JOBD,OBD2 |
| `21D9` | 7E0 | CVT temperature | 1/1/-40 | 1/3 | BIT1 | 15 | Vitz09 |
| `21D9` | -/7DF/7E0/7E1 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 ; 1/1/-40 ; 1/256/-40 | 1/3 ; 1/4 ; 1/6 ; 2/0 ; 2/4 ; 2/6 | - / BIT1 | 15 | JOBD,OBD2C09,OBD2C22 |
| `21F1` | 8210F1 | Exhaust temperature B1S1 | 1/1/0 [FR:(Signed(E)*160)+F*0.625] | 1/0 | - | 15 | Avensis2D,CorVersoD |
| `21F1` | 8210F1 | Exhaust temperature B1S2 | 1/1/0 [FR:(Signed(G)*160)+H*0.625] | 1/0 | - | 15 | Avensis2D,CorVersoD |
| `21F1` | - | Initital air temperature | 1/1/0 | 1/2 | BIT1 | 15 | JOBD,OBD2 |
| `21F1` | - | Initital coolant temperature | 1/1/0 | 1/0 | BIT1 | 15 | JOBD,OBD2 |
| `21F3` | 7E0 | Injection timing | 1/1/0 [FR:A*0.128] | 1/0 | - | 44 | JOBD |
| `21F3` | - | Injection Volume (ml) | 1/1/0 | 2/0 | BIT1 | - | JOBD,OBD2 |
| `21F3` | 7E0 | Injector pulse width | 0.128/1/0 | 1/2 | - | 44 | OBD2C09,OBD2C22 |
| `21F3` | 7E0 | RES(PID_015D) | 0.128/1/0 | 1/0 | - | 44 | OBD2C22 |

### B1. Mode 01 стандартные (01xx)

| `CMD` | HDR | NM | формула (MUL/DIV/OFS) | DL/SBI | SIG/BIT | UN | профили |
|---|---|---|---|---|---|---|---|
| `010C` | 7E0 | ICE Actual RPM | 0/1/0 [FR:(64*A)+(0.25*B)] | 0/0 | - | 6 | Prius2 |
| `015B` | 7E2 | SoC | 20/51/0 | 1/0 | BIT1 | 14 | HighlHy3 |
| `015B` | 7E2 | State of Charge | 20/51/0 | 1/0 | BIT1 | 14 | AurisTS,CamryXV50,Prius3,RAV4H1618 |
| `0191` | 8213F1 | Injection volume [mm^3/rpm] | 1/1/0 [FR:(A*1273/255)+(B/51)] | 1/0 | - | - | CorollaD |
| `0192` | 8213F1 | Injection time | 1/1/0 [FR:A/5] | 1/0 | - | - | CorollaD |
| `0193` | 8213F1 | Fuel temperature | 1/1/-40 | 1/0 | - | - | CorollaD |
| `0194` | 8213F1 | Accelerator position | 1/1/0 | 1/0 | - | 14 | CorollaD |
| `0195` | 8213F1 | Throttle position | 1/1/0 | 1/0 | - | - | CorollaD |
| `0196` | 8213F1 | Tee/ramp pressure | 1/1/0 | 1/0 | - | 74 | CorollaD |
| `0197` | 8213F1 | Pump nozzles | 1/1/0 | 1/0 | - | - | CorollaD |
| `0198` | 8213F1 | Main injection/control device ON | 1/1/0 [FR:(A*0.256)+(B*0.001)] | 1/0 | - | 44 | CorollaD |
| `0199` | 8213F1 | Main injection/control device OFF | 1/1/0 [FR:(A*0.256)+(B*0.001)] | 1/0 | - | 44 | CorollaD |
| `019A` | 8213F1 | Pilot injection time | 1/1/0 [FR:(A*0.256)+(B*0.001)] | 1/0 | - | 44 | CorollaD |
| `019B` | 8213F1 | Outdoor temperature | 1/1/0 | 1/0 | - | 15 | CorollaD |
| `019C` | 8213F1 | Injection valve 1..4 [mm^3/rpm] | #1 (A*40/255)-10; #2 (B*40/255)-10; #3 (C*40/255)-10; #4 (D*40/255)-10 | 1/0 (одинаковый SBI у всех; различия — в формуле) | - | - | CorollaD |
| `01B2` | - | RES(PID_0111) #2 | 1/1/0 [FR:C*5/255] | 1/0 | - | 10 | OBD2K |
| `01B2` | - | RES(PID_015A_Short) #1 | 5/255/0 | 1/0 | BIT4 | 10 | OBD2K |
| `01B2` | - | RES(PID_015A_Short) #2 | 1/1/0 [FR:B*5/255] | 1/0 | - | 10 | OBD2K |
| `01B2` | - | Throttle motor load on closing | 1/1/0 | 1/5 | BIT1 | 14 | OBD2K |
| `01B2` | - | Throttle motor load on opening | 1/1/0 | 1/4 | BIT1 | 14 | OBD2K |
| `01B2` | - | Throttle position requested | 1/1/0 [FR:D*5/255] | 1/0 | - | 10 | OBD2K |
| `01B3` | - | Calculated RES(PID_015A) | 1/1/0 [FR:D*100/255] | 1/0 | - | - | OBD2K |
| `01B3` | - | Clutch solenoid current | 1/1/0 [FR:F/100] | 1/0 | - | 59 | OBD2K |
| `01B3` | - | Commanded RES(PID_0111) | 1/1/0 [FR:C*5/255] | 1/0 | - | 10 | OBD2K |
| `01B3` | - | Throttle valve current | 1/1/0 [FR:E*20/255] | 1/0 | - | 59 | OBD2K |
| `01B4` | -/686AF1/7E0/7E1 | RES(PID_ATF_TEMPERATURE) | 1/1/0 [FR:(A*256+B)/255-40] ; 1/1/-40 ; 0.00390625/1/-40 | 1/0 ; 2/0 | - / BIT1 | 15 | OBD2C09,OBD2C22,OBD2K |
| `01E4` | - | RES(PID_015D) | 1/1/0 [FR:A/8] | 1/0 | - | 44 | OBD2K |
| `01E7` | - | EGR steps | 1/1/0 | 1/0 | BIT1 | - | OBD2K |
| `01E8` | - | N/P selector | 1/1/0 [FR:A@&16] | 1/0 | - | - | OBD2K |
| `01E8` | - | RES(PID_AC_CLUTCH) | 1/1/0 [FR:A@&32] | 1/0 | - | - | OBD2K |
| `01E8` | - | Stop | 1/1/0 [FR:A@&4] | 1/0 | - | - | OBD2K |
| `01E9` | - | Misfire Cylinder #1..4 (alt) | 1/1/0 | 1/0..3 step 1 (#1→0, #4→3) | - | - | OBD2K |
| `01EA` | - | Misfire Cylinder #5..8 (alt) | 1/1/0 | 1/0..3 step 1 (#5→0, #8→3) | - | - | OBD2K |
| `01F4` | - | Knock correction advance | 1/1/0 [FR:A*89.6/255] | 1/0 | - | 7 | OBD2K |
| `01F7` | - | SPD (NT) | 1/1/0 [FR:A*50] | 1/0 | - | 6 | OBD2K |
| `01F8` | 686AF1 | Lockup Soleniod | 1/1/0 | 1/0 | BIT3 | - | OBD2K |
| `01F8` | - | RES(PID_AT_CURRENT_GEAR) | 1/1/0 [FR:A@&7] | 1/0 | - | - | OBD2K |
| `01F9` | - | 2 selector | 1/1/0 [FR:A@&2] | 1/0 | - | - | OBD2K |
| `01F9` | - | D selector | 1/1/0 [FR:B@&2] | 1/0 | - | - | OBD2K |
| `01F9` | - | KickDown | 1/1/0 [FR:A@&32] | 1/0 | - | - | OBD2K |
| `01F9` | - | L selector | 1/1/0 | 1/0 | - | - | OBD2K |
| `01F9` | - | Mode: O/D | 1/1/0 [FR:A@&16] | 1/0 | - | - | OBD2K |
| `01F9` | - | Mode: Power | 1/1/0 [FR:A@&8] | 1/0 | - | - | OBD2K |
| `01F9` | 686AF1 | O/D Cut From O/D Off Switch | 1/1/0 | 1/0 | BIT4 | - | OBD2K |
| `01F9` | - | R selector | 1/1/0 [FR:A@&4] | 1/0 | - | - | OBD2K |
| `01F9` | 686AF1 | Shift Position Switch in 2ND | 1/1/0 | 1/0 | BIT1 | - | OBD2K |
| `01F9` | 686AF1 | Shift Position Switch in 4th/Drive | 1/1/0 | 1/1 | BIT1 | - | OBD2K |
| `01F9` | 686AF1 | Shift Position Switch in L | 1/1/0 | 1/0 | - | - | OBD2K |
| `01F9` | 686AF1 | Shift Position Switch in R | 1/1/0 | 1/0 | BIT2 | - | OBD2K |
| `01F9` | 686AF1 | Shift Position Switch in Snow | 1/1/0 | 1/1 | BIT3 | - | OBD2K |
| `01FE` | - | SPD (NC) | 1/1/0 [FR:A*50] | 1/0 | - | 6 | OBD2K |


## C. Варианты ATF/CVT-температуры

Все команды/варианты температуры ATF / CVT / трансмиссионной жидкости (все CMD, HDR, SBI, DL). Одна строка = один вариант упаковки.

| `CMD` | HDR | NM | формула | DL/SBI | SIG/BIT | UN | профили |
|---|---|---|---|---|---|---|---|
| `01B4` | - | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `01B4` | 7E0 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `01B4` | 7E1 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `01B4` | 7E1 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/0 | BIT1 | 15 | OBD2C09,OBD2C22 |
| `01B4` | 7E0 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/0 | BIT1 | 15 | OBD2C09,OBD2C22 |
| `01B4` | - | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/0 | BIT1 | 15 | OBD2C22 |
| `01B4` | - | RES(PID_ATF_TEMPERATURE) | 1/1/0 [FR:(A*256+B)/255-40] | 1/0 | - | 15 | OBD2K |
| `01B4` | 686AF1 | RES(PID_ATF_TEMPERATURE) | 1/1/0 [FR:(A*256+B)/255-40] | 1/0 | - | 15 | OBD2K |
| `2182` | - | (L4) Transmission Fluid Pan Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | Tacoma3 |
| `2182` | - | (L4) Transmission Fluid Post-Converter Temperature | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | Tacoma3 |
| `2182` | - | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C09,OBD2C22 |
| `2182` | - | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | OBD2C22 |
| `2182` | 7E0 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `2182` | 7E0 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | OBD2C22 |
| `2182` | 7E1 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `2182` | 7E1 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | OBD2C22 |
| `2182` | - | RES(PID_ATF_TEMPERATURE) (Pan) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C16 |
| `2182` | - | TCM] RES(PID_ATF_TEMPERATURE) (Post-Converter) | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | OBD2C16 |
| `2182` | - | Transmission Fluid Pan Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `2182` | - | Transmission Fluid Post-Converter Temperature | 0.00390625/1/-40 | 2/2 | BIT5 | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `21B4` | - | RES(PID_ATF_TEMPERATURE) | 1/1/0 [FR:(A*256+B)/255-40] | 1/0 | - | 15 | OBD2K |
| `21D9` | 7E0 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/4 | - | 15 | JOBD |
| `21D9` | 7E1 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/4 | - | 15 | JOBD |
| `21D9` | - | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `21D9` | 7E0 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `21D9` | 7E1 | RES(PID_ATF_TEMPERATURE) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C22 |
| `21D9` | 7E0 | CVT temperature | 1/1/-40 | 1/3 | BIT1 | 15 | Vitz09 |
| `21D9` | 7E1 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/3 | - | 15 | JOBD,OBD2C09,OBD2C22 |
| `21D9` | 7E0 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/3 | - | 15 | JOBD |
| `21D9` | 7E0 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/4 | BIT1 | 15 | OBD2C09 |
| `21D9` | 7E1 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/4 | - | 15 | OBD2C09 |
| `21D9` | 7E0 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/3 | BIT1 | 15 | OBD2C09,OBD2C22 |
| `21D9` | - | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/3 | - | 15 | OBD2C09,OBD2C22 |
| `21D9` | 7E1 | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/6 | - | 15 | OBD2C09 |
| `21D9` | - | RES(PID_ATF_TEMPERATURE) | 1/1/-40 | 1/4 | BIT1 | 15 | OBD2C22 |
| `21D9` | 7E0 | RES(PID_ATF_TEMPERATURE) | 1/256/-40 | 2/4 | BIT1 | 15 | JOBD,OBD2C22 |
| `21D9` | 7DF | RES(PID_ATF_TEMPERATURE) | 1/256/-40 | 2/4 | BIT1 | 15 | JOBD |
| `21D9` | - | RES(PID_ATF_TEMPERATURE) | 1/256/-40 | 2/6 | BIT1 | 15 | OBD2C22 |
| `21D9` | 7E0 | RES(PID_ATF_TEMPERATURE) | 1/256/-40 | 2/6 | BIT1 | 15 | OBD2C22 |
| `21D9` | 7E1 | RES(PID_ATF_TEMPERATURE) | 1/256/-40 | 2/4 | BIT1 | 15 | OBD2C22 |
| `21D9` | 7E1 | RES(PID_ATF_TEMPERATURE) | 1/256/-40 | 2/6 | BIT1 | 15 | OBD2C22 |
| `221040` | 7D2 | Transaxle Oil Temperature | 1/1/-40 | 1/2 | - | 15 | AvalonH19,CamryH18,CorollaH20,Prius4,PriusP,PriusP23,RAV4H19,RAV4P1925 |
| `221627` | 701 | (V6) Transmission Fluid Pan Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | Tacoma3 |
| `221627` | 700 | RES(PID_ATF_TEMPERATURE) (Pan) | 0.00390625/1/-40 | 2/0 | - | 15 | OBD2C16 |
| `221627` | 701 | RES(PID_ATF_TEMPERATURE) (Pan) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C16 |
| `221627` | DA00E0 | RES(PID_ATF_TEMPERATURE) (Pan) | 0.00390625/1/-40 | 2/0 | - | 15 | RAV4XA60,WildlHEV |
| `221627` | 700 | Transmission Fluid Pan Temperature | 0.00390625/1/-40 | 2/0 | - | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `221627` | 701 | Transmission Fluid Pan Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `221628` | 701 | (V6) Transmission Fluid Post-Converter Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | Tacoma3 |
| `221628` | 700 | RES(PID_ATF_TEMPERATURE) (Post-Converter) | 0.00390625/1/-40 | 2/0 | - | 15 | OBD2C16 |
| `221628` | 701 | RES(PID_ATF_TEMPERATURE) (Post-Converter) | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | OBD2C16 |
| `221628` | DA00E0 | RES(PID_ATF_TEMPERATURE) (Post-Converter) | 0.00390625/1/-40 | 2/0 | - | 15 | RAV4XA60,WildlHEV |
| `221628` | 700 | Transmission Fluid Post-Converter Temperature | 0.00390625/1/-40 | 2/0 | - | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `221628` | 701 | Transmission Fluid Post-Converter Temperature | 0.00390625/1/-40 | 2/0 | BIT1 | 15 | AvalonH19,CamryH18,CorollaH20,RAV4H19 |
| `22162B` | 701 | ATF Thermal Degradation Estimate | 1/1/0 | 2/0 | - | - | OBD2C16 |

---

## D. Init-последовательности ключевых профилей

Профили без собственного init (большинство, включая OBD2C22/OBD2C16) используют стандартную автоинициализацию OBD; `DefaultProtocolSelected=6` = ISO 15765-4 CAN 11 бит 500 кбит/с. Ниже — профили с `UseCustomInit=true` из отобранных, дословно. Пояснения AT-команд — в таблице после блоков.

### JOBD JDM CAN (код JOBD)

CheckConnectionCommand: `210C` · DefaultHeader: `7E0` · UseCustomInit: True

InitSequence дословно:

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

