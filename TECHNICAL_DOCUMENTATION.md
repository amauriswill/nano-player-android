# TECHNICAL DOCUMENTATION — Nano Music Player (DAP Edition)
*Proyecto:* `nano-player-android` (Nano Music Player - Music-Exclusive DAP Edition)  
*Ubicación:* `C:\Users\amauriswill\Downloads\Working`

---

## 1. Visión General y Filosofía de Arquitectura
**Nano Music Player** es un reproductor de música local nativo para Android inspirado en el iPod clásico (Aqua UI / Pixo OS). Siguiendo la doctrina DHH (*The One Person Framework*), la arquitectura se ha simplificado y reorganizado en un **Monolito Cohesivo (*Flat Package Structure*)**, eliminando la fragmentación excesiva de carpetas y priorizando la legibilidad, la alta cohesión y el rendimiento de nivel industrial.

---

## 2. Estructura de Directorios y Ubicación de Archivos

```text
Working/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── kotlin/
│               └── com/ipodnano/player/
│                   ├── model/
│                   │   └── Track.kt                     # Entidad core inmutable de audio
│                   ├── repository/
│                   │   ├── MusicRepository.kt           # Interfaz del repositorio de música
│                   │   └── MediaStoreMusicRepository.kt # Escáner local de MediaStore (MP3, FLAC, etc.)
│                   └── ui/
│                       ├── MainActivity.kt              # Actividad principal, máquina de estados y control DAP
│                       ├── IpodScreenView.kt            # Motor gráfico Canvas personalizado (Aqua UI & Menús)
│                       └── IpodWidgetProvider.kt        # Proveedor de widgets de escritorio y pantalla de bloqueo
├── RAW Request.md                                       # Bitácora oficial de trazabilidad de peticiones
├── CHANGELOG.md                                         # Registro histórico de versiones y cambios
└── TECHNICAL_DOCUMENTATION.md                           # Documentación técnica del proyecto
```

---

## 3. Componentes Técnicos Clave

### 3.1. Paquete `model/`
- **`Track.kt`**: Entidad inmutable (`id`, `title`, `artist`, `album`, `durationMs`, `sourceUri`, `albumArtUri`, `trackNumber`).

### 3.2. Paquete `repository/`
- **`MusicRepository.kt`**: Contrato unificado para la obtención de pistas, artistas y álbumes.
- **`MediaStoreMusicRepository.kt`**: Implementación sobre `MediaStore.Audio.Media`. Indexa formatos múltiples, extrae carátulas desde el proveedor de álbumes y provee una pista dummie de respaldo.

### 3.3. Paquete `ui/`
- **`MainActivity.kt`**: Controlador central que maneja:
  - Pila de navegación jerárquica inmutable (`navStack` + `navigateTo`) con restauración exacta de la posición de scroll (`scrollY`) y títulos contextuales dinámicos.
  - Máquina de estados de pantalla (`ScreenMode.MENU` vs `ScreenMode.NOW_PLAYING`).
  - Pipeline DAP avanzado: Foco de audio (`AudioFocusRequest`), pausa por desconexión de auriculares (`BecomingNoisyReceiver`), y avance automático de cola mediante `setOnCompletionListener`.
  - Carga asíncrona no bloqueante de `MediaStore` en hilos secundarios (*0 ANR*).
- **`IpodScreenView.kt`**: Motor de renderizado gráfico basado en `Canvas` con aceleración por hardware:
  - Centrado vertical absoluto en la cabecera mediante `FontMetrics`.
  - Tamaño de texto ajustable con *Live Preview* desde *Settings*.
  - Truncamiento inteligente de títulos largos con puntos suspensivos (`…`).
  - Carátulas unificadas en formato **perfectamente cuadrado** con bordes nítidos (incluyendo la carátula dummie de nota musical amarilla `#FACC15` sobre fondo negro puro `#000000`).
  - Interacción táctil con respuesta instantánea (`ACTION_DOWN`) y scroll inercial fluido.
- **`IpodWidgetProvider.kt`**: Proveedor de App Widgets para la pantalla de inicio y pantalla de bloqueo (*Keyguard*).

---

## 4. Arquitectura de Audio DAP (Dedicated Audio Player)
1. **Gestión de Foco**: Respeta las prioridades del sistema operativo mediante `AudioFocusRequest`.
2. **Interrupción por Auriculares**: El `BecomingNoisyReceiver` pausa la música al desconectar auriculares o cascos Bluetooth.
3. **Reproducción en Segundo Plano**: Uso de `PARTIAL_WAKE_LOCK` para mantener el hilo de audio activo sin sobrecarga de servicios pesados en primer plano.
