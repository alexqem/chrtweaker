# Toyota C-HR NGX10 — ECU Tweaks & OBD-II Adapter Requirements

> **Совместимость:** Toyota C-HR NGX10 (2016–2020, 8NR-FTS 1.2T / K114 CVT). Для рестайлинга и более новых моделей (2020+ TNGA) часть ECU использует UDS (`22`/`2E`) вместо KWP-over-CAN (`21`/`3B`) — перед записью проверяйте ответы в **Protocol Debug Log**.

## Требования к OBD-II адаптеру
- **Рекомендуется:** **Vgate vLinker FD+ BT4.0** (или аналогичный STN/vLinker адаптер). Поддерживает длинные ISO-TP фреймы, смену заголовков (`ATSH 750`, `0x7C0`, `0x7C4`) и надёжную запись кастомизаций Body ECU.
- **Дешёвые одноплатные ELM327 клоны (v1.5 / v2.1):** подходят только для базовой телеметрии (`0x7DF`/`0x7E0`) и простых UDS-сбросов, но часто не поддерживают запись в Body ECU (`0x750`). В Android-приложении настройки Body ECU автоматически скрываются, если подключён не vLinker/Vgate.

## Доступные настройки (вкладка `ECU Tweaks`)
- **Switches (чтение/запись):** Auto-Fold Mirrors (`0x750`), Remote Window Open/Close (`0x750`), Blink Hazards on Lock (`0x750`), Beep on Lock/Unlock (`0x750`), Eco Drive Indicator (`0x7C0`), A/C Eco Mode (`0x7C4`), Rear Seatbelt Chime (`0x7C0`).
- **Buttons (одноразовые/многозначные):** One-Touch Turn Signal (3 / 5 / 7 flashes, `0x750`), ABS Inspection Mode (`0x7B0`), CVT/ATF Thermal Reset (`0x7E1`).
- **Protocol Debug Log:** строковый лог внизу вкладки с кнопкой копирования (показывает протокол, `ATSH`, TX-команду и сырой RX-ответ).
