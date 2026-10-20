# Product Requirements Document (PRD): Nano Music Player (Music-Exclusive Edition)
**Package:** `com.ipodnano.player`  
**Architecture:** Clean Architecture (Kotlin Native Backend + Hybrid Web/PWA UI Bridge)  
**Core Directive:** Reproductor de música local puro, dedicado única y exclusivamente a audio, inspirado en la interfaz clásica del iPod Nano (Aqua / Pixo OS) adaptada 100% a pantallas táctiles Android y optimizada bajo los estándares de diseño de Emil Kowalski.

---

## 1. Visión y Alcance del Producto
**Nano Music Player** es una aplicación nativa de Android diseñada para melómanos que buscan una experiencia nostálgica, minimalista y ultra-fluida para reproducir su biblioteca de música local. 

El producto descarta por completo funciones secundarias (vídeos, imágenes, juegos o herramientas), enfocando el 100% de los recursos de hardware, renderizado y diseño en:
1. **Gestión impecable de la biblioteca musical local** mediante un repositorio robusto sobre `MediaStore`.
2. **Estética de Alta Fidelidad (Emil Kowalski / Aqua UI):** Tipografía SF Pro, números tabulares (`tabular-nums`) para evitar saltos visuales, reflejos especulares en carátulas mediante CSS `-webkit-box-reflect`, y transiciones fluidas de menús (*Push/Pop Stack*).
3. **Ergonomía Táctil y Háptica:** Controles pensados para uso con una sola mano, áreas de toque optimizadas ($\ge 44\text{px}$) y sincronización perfecta con hardware de audio (auriculares cableados, Bluetooth A2DP con política de pausa *becoming-noisy*, y control de volumen físico).

---

## 2. Arquitectura de Limpieza y Capas (`com.ipodnano.player`)

### 2.1. Domain Layer (Kotlin Puro — 100% Tests en Verde)
* **Entidades Core (`Track`):**
  * `id`: Long (MediaStore ID)
  * `title`: String (Título de la canción)
  * `artist`: String (Artista / Intérprete)
  * `album`: String (Álbum)
  * `duration`: Long (Duración en milisegundos)
  * `dataUri`: String (URI del archivo de audio)
  * `albumArtUri`: String? (Ruta de la carátula)
* **Casos de Uso (`UseCases`):**
  * `PlayTrackUseCase`, `SkipTrackUseCase`, `AdjustVolumeUseCase`, `NavigateMenuUseCase`.

### 2.2. Data & Hardware Layer (Android Native Gateways)
* **`MediaStoreMusicRepositoryGateway`:** Escáner en tiempo real del `MediaStore.Audio.Media` optimizado para soportar formatos múltiples (`MP3`, `FLAC`, `M4A`, `AAC`, `OGG`, `OPUS`, `WAV`, `WMA`, `MIDI`) y filtrado estricto de pistas válidas.
* **`AndroidAudioPlayerGateway`:** Wrapper robusto sobre `MediaPlayer` con gestión de descriptores de archivo, foco de audio y sincronización con el stream de música.
* **Gestión de Memoria y Rendimiento:** Implementación de caché de mapas de bits basada en `LruCache` con renderizado en formato **`RGB_565`**, emulando la eficiencia de los archivos `.ithmb` clásicos para garantizar sesiones de reproducción largas y seguras frente a fugas de memoria.
* **Hardware & State Receivers:**
  * Sincronización en tiempo real del nivel de batería y estados de carga mediante `BatteryManager`.
  * Detección de dispositivos de salida (Auriculares con cable / Bluetooth A2DP).
  * Motor de vibración y respuesta háptica nativa acoplada a las acciones de navegación y botones.

### 2.3. Presentation Layer (Hybrid Bridge)
* **Puente Nativo (`MainActivity` + `IpodPresenter`):** Conecta la lógica de negocio de Kotlin con la capa de presentación Web/PWA.
* **Controlador Táctil de Listas:** Reutiliza la lógica de los antiguos eventos rotatorios de la *Click Wheel* para gobernar el scroll inercial y los tics hápticos al deslizar por las filas de la biblioteca.

---

## 3. Arquitectura de Información (Árbol de Navegación Exclusivo de Música)

Estructura jerárquica limpia enfocada exclusivamente en audio, gobernada por una pila de vistas (*Navigation Stack*) con animaciones de empuje horizontal:

```text
[Root: Music Library]
 ├── Artistas (Artists)       -> [Albums] -> [Track List] -> [Now Playing]
 ├── Álbumes (Albums)         -> [Track List] -> [Now Playing]
 ├── Canciones (Songs)        -> [Now Playing]
 ├── Listas (Playlists)       -> [Track List] -> [Now Playing]
 ├── Cover Flow (Modo Horizontal / Landscape 3D)
 └── Ahora Suena (Now Playing) -> [Condicional: Visible si PlaybackStatus != STOPPED]
```

---

## 4. Especificaciones de Pantalla: "Now Playing" (Music-Exclusive View)

El diseño de la pantalla de reproducción optimiza el uso vertical del viewport para eliminar espacios muertos y asegurar estabilidad visual absoluta (Zero Layout Shift):

```text
+---------------------------------------------------+
| < Library         Now Playing      🎧 7:19 100% 🔋| <- Top Status Bar (44px)
+---------------------------------------------------+
|                                                   |
|   +------------------+   Feel Good Inc.           | <- Título: 18px Bold (#000) + Marquee
|   |                  |   Gorillaz                 | <- Artista: 15px Medium (#4A4A4A)
|   |   [ GORILLAZ ]   |   Demon Days               | <- Álbum: 14px Regular (#7A7A7A)
|   |   [  ARTWORK ]   |                            |
|   |   (140x140px)    |   [ 1 of 15 ]              | <- Contador reubicado aquí (12px Bold)
|   +------------------+                            |
|   \   [ REFLEJO  ]   /                            | <- Reflejo CSS especular (-webkit-box-reflect)
|                                                   |
+---------------------------------------------------+
|  [=============================================]  | <- Scrubber Bar (10px height, Aqua fill)
|  0:42                                       -2:59 | <- Timestamps (tabular-nums, 12px)
+---------------------------------------------------+
|                                                   |
|          ( ⏮ )       (( ▶ ))       ( ⏭ )         | <- Prev/Next (52px) | Play (72px Azul)
|                                                   |
|      🔈 [=========⚪===================] 🔊 [🔁 Off] | <- Slider Volumen + Repeat Pill (62px fijo)
+---------------------------------------------------+
```

### Reglas Clave de Diseño y Comportamiento
- **Cero Espacios Muertos:** Distribución vertical equilibrada mediante Flexbox (`justify-content: space-between`), eliminando cualquier vacío superior en pantallas de teléfonos modernos.
- **Estabilidad Numérica (CLS = 0):** Los contadores de tiempo (`0:42`, `-2:59`), el porcentaje de batería, el reloj y el contador de pistas (`1 of 15`) utilizan obligatoriamente fuentes con números tabulares (`tabular-nums`) para evitar cualquier temblor en el renderizado cada segundo.
- **Controles Ergonómicos y Estándares de Emil Kowalski:**
  - **Carátulas con Fallback e Identidad:** Tamaño de `140x140px` con bordes redondeados y efecto espejo inferior en CSS. Si el archivo no tiene carátula integrada, se muestra un icono vectorial de disco (`ri-disc-line`).
  - **Botones de Transporte:** Botón central de Play/Pause de `72px` con sombra difusa, flanqueado por controles de pista anterior/siguiente de `52px`.
  - **Slider de Volumen Ampliado:** Recorrido táctil cómodo optimizado para evitar problemas de ergonomía.
  - **Repeat Mode Pill (Ancho Fijo Estricto):** El botón de repetición mantiene un ancho fijo exacto de `62px` (para los estados *Off*, *Todo*, *Esta*) que evita de forma radical los saltos de diseño (*layout jitter*) y protege la estabilidad del slider de volumen adyacente.
