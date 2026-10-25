# CHANGELOG — Nano Music Player (DAP Edition)
*Proyecto:* `nano-player-android`  
*Ubicación:* `C:\Users\amauriswill\Downloads\Working`

---

## [2.0.0] — 2026-10-25 (Music-Exclusive DAP Edition)

### 🎵 Audio & DAP Core Engineering
- **Pipeline de Audio Robusto**: Integración de solicitudes de **Foco de Audio** (`AudioFocusRequest`) para gestionar interrupciones de llamadas y reproducción simultánea.
- **Receptor de Desconexión (`BecomingNoisyReceiver`)**: Pausa automática inmediata al desconectar auriculares cableados o dispositivos Bluetooth.
- **Cola de Reproducción Continua**: Avance automático de pista al finalizar la canción actual mediante `setOnCompletionListener`, respetando los modos de repetición y aleatorio (*Shuffle*).
- **Indexación Asíncrona**: Consulta a `MediaStore` ejecutándose en hilos secundarios para garantizar un arranque instantáneo sin bloqueos de UI (*0 ANR*).

### 📱 UI/UX Craftsmanship (Emil Kowalski & Apple Standards)
- **Cero Desplazamiento de Diseño (CLS = 0)**: Uso estricto de números tabulares (`tabular-nums`) en contadores de tiempo, reloj y porcentajes de batería.
- **Centrado Vertical por FontMetrics**: Alineación milimétrica absoluta en la barra superior de cabecera.
- **Pantalla Completa (*Edge-to-Edge*)**: Interfaz extendida uniformemente hasta los bordes de la pantalla con soporte de *Display Cutout*.
- **Carátulas Cuadradas Unificadas**: Tanto las carátulas reales como la carátula dummie (nota musical amarilla `#FACC15` sobre fondo negro puro `#000000`) se renderizan perfectamente cuadradas con bordes nítidos.
- **Píldora de Repetición / Shuffle Minimalista**: Rediseñada en formato compacto *Icon-Only* con glifos dinámicos según el modo activo.
- **Truncamiento Inteligente (*Ellipsis*)**: Recorte automático con puntos suspensivos (`…`) para nombres largos de pistas, artistas y álbumes.

### 🧭 Navegación Jerárquica e Inmutable
- **Pila de Navegación (`navStack`)**: Implementación robusta de navegación tipo *push/pop* donde el botón de retroceso (`‹ ContextTitle`) regresa exactamente un nivel a la vez con el nombre de la sección anterior.
- **Restauración de Posición de Scroll**: Al retroceder en las listas, la posición de desplazamiento vertical (`scrollY`) se restaura con precisión milimétrica al lugar exacto desde donde se ingresó.

### ⚙️ Ajustes y Personalización
- **Ajuste de Tamaño de Texto con *Live Preview***: Selector en *Settings* (`Normal`, `Large`, `Extra Large`, `Compact`) con previsualización y escalado tipográfico en tiempo real.
- **Pantalla About Dedicada**: Bloque de texto editorial tipo *Medium* con la información de desarrollo y arquitectura.

### 🖥️ Widgets
- **Home & Lock Screen Widgets**: Proveedor de widgets de escritorio y pantalla de bloqueo (`IpodWidgetProvider`) con controles multimedia y carátula en miniatura.
