# Веб-дашборд, телеметрия и структура проекта

## Живой веб-дашборд (`capture`)

При запуске `python -m mycar capture` поднимается локальный веб-сервер на `http://127.0.0.1:8600` (FastAPI + WebSocket, Chart.js):
- **Live Telemetry:** онлайн-карточки (двигатель, ОЖ, масло, катализатор, время, MAF, коррекции, бортсеть) и 6 живых графиков (температуры, обороты, воздух, коррекции, катализатор, бортсеть).
- **HUD:** крупные показания и кнопка **Зеркало (HUD)** для проекции на лобовое стекло (`WakeLock`).
- **ECU Tweaks:** переключатели и кнопки настройки блоков (`0x750`, `0x7C0`, `0x7C4`, `0x7B0`, `0x7E1`) + строковый **Protocol Debug Log**.

По окончании сессии процесс завершается сразу; флаг `--hold` оставляет дашборд живым до 1 часа (`Ctrl+C` — выйти раньше). Отключить дашборд: `--no-dashboard`, другой порт: `--dashboard-port 8600`.

При F5 / переподключении графики пересобираются из всей текущей сессии (дедупликация по `seq`). Все нормы и пороги подставляются сервером из `mycar/config.py` (`config.NORMS`).

## Логика записи и подключение

- При подключении читается температура ОЖ: `< 40 °C → cold`, иначе `warm`.
- Каждые ~1 с снимаются Mode-01 PID (мульти-запросы до 6 PID с автоматическим откатом на одиночные, `--no-multi` принудительно).
- Транспорт: BLE (`bleak`, сервис `0000fff0`) → USB serial (`/dev/cu.usbserial-*`) → Wi-Fi (`--port socket://<ip>:35000`). Явно: `--port ble:<адрес>`.
- Обрыв связи не останавливает сессию: `capture` переподключается автоматически.
- В конце сессии читаются коды ошибок (`03`/`07`) и выводится сводка `analyze`.

## Структура репозитория

```text
chrtweaker/
├── mycar/            # Python-пакет (CLI + FastAPI дашборд + драйверы ELM)
│   ├── config.py     #   профиль авто, пороги, нормативы (единый источник)
│   ├── sensors.py    #   Mode-01 PIDs + декодеры J1979
│   ├── elm.py        #   raw-драйвер ELM327 (pyserial)
│   ├── elm_ble.py    #   BLE-драйвер (bleak)
│   ├── connection.py #   выбор транспорта (BLE / serial / Wi-Fi / sim)
│   ├── sim.py        #   симулятор ЭБУ для офлайн-тестов
│   ├── capture.py    #   запись сессии -> logs/*.csv + *.meta.json
│   ├── dashboard.py  #   FastAPI + WebSocket + /api/tweaks
│   ├── dashboard.html#   интерфейс дашборда (Telemetry, HUD, ECU Tweaks)
│   ├── tweaks.py     #   таблица ECU-настроек и команд
│   ├── analyze.py    #   сводка сессии -> stats/*_summary.md|.json
│   ├── db.py         #   SQLite-зеркало логов (sessions + samples)
│   ├── toyota.py     #   Toyota PIDs и DTC-декодер
│   └── cli.py        #   CLI: doctor | capture | analyze | db | trend | dtc | probe
├── android/          # Android-приложение (Kotlin + Jetpack Compose)
├── docs/             # документация и справочники (01..09, ECU_TWEAKS, ANDROID_BUILD)
├── logs/             # локальные CSV-логи и chr.db (в git не попадают)
└── stats/            # локальные отчёты (в git не попадают)
```
