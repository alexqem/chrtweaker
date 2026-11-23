# rea-reference — выжимка реверса Car Scanner для C-HR

Файлы здесь — **снапшот справочных доков реверса Car Scanner ELM OBD2 v2.2.52**
(исходная папка `../rea/`, в git не входит: 1.3 ГБ декомпилятов и извлечённых
ассетов). Сюда отобрано только то, что относится к Toyota C-HR NGX10 2020 и
реально используется в коде `mycar/`.

| файл | о чём | где применяется |
|---|---|---|
| `toyota_chr.md` | какие профили Car Scanner релевантны C-HR (JOBD JDM CAN и др.), конфигурация профилей | гипотезы `docs/07` |
| `toyota_pids_c-hr.md` | **каталог PID**: формат полей, Mode-01 (`01B4`!), ядро C-HR (B0), варианты ATF/CVT-температуры, init JOBD JDM CAN | `mycar/toyota.py::decode_atf`, `docs/02` |
| `toyota_commands.md` | AT-команды ELM327 для Toyota ≤2020, функциональная адресация `7DF` как обход ATSH | `mycar/elm.py::INIT_SEQUENCE`, `toyota.probe_cvt` |
| `toyota_dtc_protocol.md` | формат DTC (CAN «count + пары»), имена ECU по заголовкам | `mycar/toyota.py::parse_dtc_reply`, `ECU_NAMES` |
| `toyota_code_snippets.md` | алгоритмы декода из decompiled C# (ByteSet, DTC, multi-request) | перенесено в `mycar/toyota.py` |

Чего здесь **нет** (и почему):

- `toyota_dtc_codes.md` (59 КБ справочника кодов) — уже перегнан в структурированный
  `mycar/data/toyota_dtc.json` (5883 записи, Toyota-first).
- `toyota_pids_catalog.md` целиком (194 КБ) — дампы Mode 21/22 для всех профилей;
  для C-HR отобрано в `toyota_pids_c-hr.md`.
- `toyota_hud_dashboard.md`, `android_shell.md`, `aot_native.md`, `data_assets.md`,
  `profiles_data.md`, `obd_protocol.md` — внутренности приложения Car Scanner,
  для проекта бесполезны (см. `docs/07`, раздел «Что не берём»).

Статусы «проверено / не проверено на машине» — в `../07-rea-integration.md`.
