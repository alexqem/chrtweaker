#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Python 3 не найден. Установите через Homebrew: brew install python"
  exit 1
fi

if [ ! -d ".venv" ]; then
  echo "Создаю виртуальное окружение .venv..."
  python3 -m venv .venv
fi

source .venv/bin/activate
echo "Устанавливаю зависимости..."
pip install -q --upgrade pip
pip install -q -r requirements.txt

if [ ! -f ".env" ] && [ -f ".env.example" ]; then
  cp .env.example .env
  echo "Создан .env из .env.example"
fi

echo "Проверяю окружение и Bluetooth..."
python -m mycar doctor || true

echo ""
echo "Готово! Примеры запуска:"
echo "  source .venv/bin/activate"
echo "  python -m mycar capture                  # подключение к авто + веб-дашборд"
echo "  python -m mycar capture --sim --sim-speed 60  # тест без машины"
