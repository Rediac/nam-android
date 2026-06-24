# NAM Player Android

Reproductor de modelos **Neural Amp Modeler (NAM v2 / formato .nam a2)** para Android en tiempo real.  
Usa [NeuralAmpModelerCore](https://github.com/sdatkinson/NeuralAmpModelerCore) (Core v0.5.x) via JNI/NDK + AAudio.

## Flujo de audio

```
Guitarra → interfaz USB / micrófono
        → AAudio Input (48kHz)
        → NeuralAmpModelerCore C++ (JNI)
        → AAudio Output (baja latencia)
        → auriculares / altavoces
```

## Uso

1. Instala el APK (Android 8.0+, ARM64)
2. Pulsa **Cargar archivo .nam** y selecciona tu modelo
3. Pulsa **▶ Iniciar** y toca la guitarra

## Build

Compilado automáticamente con **GitHub Actions + NDK r25**.  
Descarga el APK: **Actions → último workflow → Artifacts → nam-player-release**

## Compatibilidad de modelos

Soporta los formatos que maneja `nam::get_dsp()`:
- Architecture A1 (LSTM, WaveNet, ConvNet, Linear) — archivos `.nam` versión 0.5.x
- Architecture A2 — archivos `.nam` a2

## Notas técnicas

- Eigen alignment fix activado (`EIGEN_MAX_ALIGN_BYTES=0`) para estabilidad en Android
- `enable_fast_tanh()` activado igual que el plugin oficial
- Dos streams AAudio separados (input/output) para mínima latencia
