# Detective Batería

App para Android que muestra **dónde se va la batería**: qué apps gastan, cuáles no dejan dormir al celu y cuánto se descarga con la pantalla apagada.

## Cómo funciona

1. Android lleva un registro completo del consumo (`batterystats`), pero una app común no puede leerlo.
2. **Shizuku** corre un proceso con el usuario `shell` (el mismo que ADB), que sí tiene el permiso `DUMP`.
3. La app ejecuta `dumpsys batterystats -c` (formato CSV) por Shizuku y procesa las líneas:
   - `pwi` → consumo en mAh por app y por componente (pantalla, CPU, radio…)
   - `wl` → wakelocks: tiempo que cada app mantuvo el procesador prendido con la pantalla apagada
   - `wua` → alarmas que despiertan al celu
   - `bt` / `dc` → tiempos y % descargado con pantalla prendida/apagada, capacidad aprendida
4. Con eso arma un diagnóstico (salud de la batería, % por hora en reposo, apps culpables).

## Acciones

- **Forzar detención** → `am force-stop <paquete>`
- **Restringir 2º plano** → `cmd appops set <paquete> RUN_ANY_IN_BACKGROUND ignore` (se revierte con `allow`)
- **Reiniciar medición** → `dumpsys batterystats --reset`
- **Compartir informe** → guarda `dumpsys batterystats --charged` completo en un .txt

## Requisitos

- Android 8 o superior
- [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) iniciado (depuración inalámbrica)

## Compilar

Cada push a `main` compila el APK con GitHub Actions y lo publica en la release **ultima**.
