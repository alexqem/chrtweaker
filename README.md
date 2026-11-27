# C-HR OBD Tweaker — телеметрия и настройки Toyota C-HR (NGX10/NGX50, до 2020)

Инструмент для диагностики холодного пуска, живого мониторинга (HUD + графики) и настройки блоков ECU Toyota C-HR (2016–2020: 1.2T 8NR-FTS NGX10, 1.8 гибрид 2ZR-FXE NGX50) с Android и macOS. Эталонная машина — NGX10 8NR-FTS K114 CVT (все нормы проверены на ней); другие C-HR и Toyota — через профили, см. [«Поддерживаемые автомобили»](#поддерживаемые-автомобили).

![Live-дашборд: телеметрия, графики с нормами, HUD и ECU Tweaks](docs/screenshot.jpg)

## Скачать Android APK

- Готовый APK (`chrtweaker_v1.0.0-debug.apk`) доступен в **[GitHub Releases](https://github.com/alexqem/chrtweaker/releases/latest)**.

## Быстрый запуск на macOS (с нуля)

```sh
git clone https://github.com/alexqem/chrtweaker.git
cd chrtweaker
chmod +x install_mac.sh && ./install_mac.sh
```

Скрипт создаст `.venv`, установит зависимости, подготовит `.env` и проверит Bluetooth/окружение (`doctor`). При первом запуске macOS может запросить разрешение на доступ к Bluetooth для терминала.

Основные команды после установки (`source .venv/bin/activate`):

```sh
python -m mycar capture                     # живой захват + веб-дашборд (http://127.0.0.1:8600)
python -m mycar capture --sim --sim-speed 60  # тест в симуляторе без машины
python -m mycar analyze                     # сводка последней сессии -> stats/
python -m mycar trend                       # тренд LTFT по всем сессиям
python -m mycar dtc                         # чтение и расшифровка ошибок
```

## Совместимость с OBD-II адаптерами

- **Базовая телеметрия** (холодный пуск, обороты, температуры, топливные коррекции, HUD) работает на любом BLE ELM327 v1.5+ адаптере.
- **Расширенные функции** (температура CVT и настройки Body ECU `0x750`/`0x7C0`/`0x7C4`) требуют адаптер с полноценной поддержкой `ATSH` и ISO-TP — рекомендуется **Vgate vLinker FD+ BT4.0**. Приложение само определяет возможности адаптера (`ATI`/`VTI`) и скрывает недоступные пункты с пояснением.

## Поддерживаемые автомобили

Проект вырос из NGX10, но опрос телеметрии — это стандарт SAE J1979 (Mode 01), он работает на **любой бензиновой OBD-II машине с 2008 года**. Модельное — в командах Toyota (21xx) и в нормативах. Уровни поддержки:

| Уровень | Что работает | Кому подходит |
|---|---|---|
| **A. Телеметрия** — любой автомобиль OBD-II | Обороты, ОЖ, масло (`015C`), IAT, MAF, STFT/LTFT, напряжение, скорость, дашборд, HUD, графики, CSV/SQLite, DTC (стандартные режимы) | Любая бензиновая Toyota/Lexus 2008+, и вообще любой бензин OBD-II — выберите профиль `generic` |
| **B. + температура CVT/ATF** | Зонды `21D9` / `2182` (KWP-era Toyota). По реверсу Car Scanner те же команды в профилях **15 моделей Toyota** — Vitz/Passo CVT, Corolla/Auris (K313), RAV4/Camry/Corolla Hybrid (2182) и др. | Toyota с CVT до ~2016 и ряд гибридов; выберите профиль с `cvt_probe` |
| **C. + холодный пуск / нормы** | Анализ прогрева, °C/мин, вердикты по нормам | Профили `8nrfts` (проверено на живой машине) и `2zrfe` (нормы по мануалу Toyota, не проверены на дороге) |
| **D. + ECU-твики** | Настройки Body/Meter/HVAC (`0x750`/`0x7C0`/`0x7C4`) | **Проверено только на NGX10.** Адреса блоков — общетойотовские той эпохи, но байтовые команды (`3BA701` и т.п.) взяты из реверса C-HR: на других моделях включайте через чтение до/после (`Protocol Debug Log`) и подтверждайте ответ ECU |

### Профили (`MYCAR_PROFILE` в `.env` или `capture --profile`)

| Профиль | Автомобиль | Статус |
|---|---|---|
| `8nrfts` *(по умолчанию)* | C-HR NGX10, 1.2T 8NR-FTS, K114 CVT | ✅ проверено на живой машине |
| `2zrfe` | 1.8 атмо 2ZR-FE (Corolla, Auris — CVT/MT) | 🟡 телеметрия полная; нормы по мануалу, не проверены |
| `2zrfxe` | 1.8 гибрид 2ZR-FXE (C-HR NGX50, Corolla Hybrid, Prius) | 🟡 только телеметрия — авто-стоп ДВС ломает логику холодного пуска; анализ отключён |
| `generic` | любой OBD-II бензин | 🟡 стандартные PIDs, Toyota-зонды выключены |

Профиль пишется в `meta.json` сессии — `analyze` сам применит нормы того профиля, которым сессия записана. Температура ATF новых UDS-блоков (`221627`, Mode 22) пока **не реализована** — код опрашивает только Mode 21/01.

> **Чего не хватает для «полной поддержки» других моделей:** сверка норм по живой машине (2zrfe/2zrfxe) и read-back проверка твиков per-model. Если у вас Corolla 1.8 / C-HR NGX50 — запускайте телеметрию и присылайте `stats/*_summary.md`: это закрывает уровень C быстрее всего.

## Документация

| Тема | Файл |
|---|---|
| Веб-дашборд, телеметрия, структура проекта | [docs/DASHBOARD_AND_TELEMETRY.md](docs/DASHBOARD_AND_TELEMETRY.md) |
| Настройки ECU (`ECU Tweaks`) и выбор адаптера | [docs/ECU_TWEAKS.md](docs/ECU_TWEAKS.md) |
| Сборка Android APK и App Bundle | [docs/ANDROID_BUILD.md](docs/ANDROID_BUILD.md) |
| Паспорт авто и двигателя (NGX10, 8NR-FTS) | [docs/01-car-and-engine-ngx10.md](docs/01-car-and-engine-ngx10.md) |
| Mode-01 PID и формулы SAE J1979 | [docs/02-obd-pids-mode01.md](docs/02-obd-pids-mode01.md) |
| Нормы холодного пуска | [docs/03-cold-start-norms.md](docs/03-cold-start-norms.md) |
| macOS, Bluetooth и порты ELM327 | [docs/04-macos-bluetooth-elm327.md](docs/04-macos-bluetooth-elm327.md) |
| Чек-лист разбора лога и причины отклонений | [docs/05-cold-start-diagnostic-guide.md](docs/05-cold-start-diagnostic-guide.md) |
| Исследование топливных коррекций (LTFT) | [docs/06-fuel-trim-ltft-research.md](docs/06-fuel-trim-ltft-research.md) |
| Карта поддерживаемых PID и Mode 06/09 | [docs/08-empirical-pid-map.md](docs/08-empirical-pid-map.md) |
| Расширенный доступ к ECU (CVT, ABS, TPMS) | [docs/09-extended-ecu-access.md](docs/09-extended-ecu-access.md) |
