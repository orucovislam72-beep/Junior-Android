# Junior (Android)

Локальный ИИ-чат: Kotlin + Jetpack Compose + llama.cpp (JNI). Модели GGUF скачиваются при первом запуске.
Пакет: `com.riglineaimstriker.junior`.

## Сборка (рекомендуется через GitHub Actions)
1. `git init && git add . && git commit -m init`
2. `git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp`
   - JNI написан под актуальный C API llama.cpp (`llama_model_load_from_file`, `llama_memory_clear`, `llama_vocab_is_eog`).
     Если сборка ругается на имя функции, зафиксируйте подходящий коммит сабмодуля.
3. Пуш на GitHub. Workflow `.github/workflows/build.yml` соберёт APK (debug) и AAB (release).

Локально: Android Studio, NDK 28.0.12433566, CMake 3.22.1.

## Подпись релиза
Переменные окружения: `JUNIOR_KEYSTORE`, `JUNIOR_KS_PASS`, `JUNIOR_KEY_ALIAS`, `JUNIOR_KEY_PASS`.
В Google Play загружайте AAB (Play App Signing).

## Перед публикацией
- Лицензия Mini (Qwen2.5-Coder-3B, «Qwen Research») ограничивает коммерческое использование: замените модель.
- Политика конфиденциальности и форма Data safety (приложение офлайн; сеть нужна только для загрузки модели).
