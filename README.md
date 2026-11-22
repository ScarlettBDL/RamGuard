<div align="center">

# 🛡️ RamGuard

**Mirá en qué se va tu RAM y cerrá lo que quedó colgado en segundo plano, sin tocar lo que importa.**

[![Build APK](https://github.com/ScarlettBDL/RamGuard/actions/workflows/build.yml/badge.svg)](https://github.com/ScarlettBDL/RamGuard/actions/workflows/build.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material-3-6EE7A8)
![Root | Shizuku](https://img.shields.io/badge/privilegios-Root%20%7C%20Shizuku-7DB3FF)

</div>

---

## ✨ ¿Qué es?

RamGuard es una app Android hecha en **Kotlin + Jetpack Compose** que te muestra el uso **real** de memoria del teléfono y te deja cerrar procesos en segundo plano con un **forzar detención** de verdad (`am force-stop`).

La idea es simple:

1. Detectar las apps que quedaron colgadas en segundo plano.
2. Marcar con un candado las que **nunca** querés cerrar.
3. Liberar todo lo demás de una sola vez y ver cuánta memoria se recuperó.

> Interfaz oscura, minimalista y pensada para usarse con una mano.

---

## 🚀 Funciones

| | |
|---|---|
| 📊 **RAM real** | Memoria disponible, total, usada, caché, swap y presión de memoria (`ActivityManager` + `/proc/meminfo`). |
| 📋 **Lista de procesos** | Cada app con su tamaño residente (RSS) y su `oom_score_adj`, el valor que Android usa internamente para decidir qué proceso es prescindible. |
| 🏷️ **Clasificación** | Servicio · segundo plano · en caché, calculada a partir del `oom_score_adj`. Las apps en uso quedan fuera de la lista. |
| 🔒 **Exclusiones** | Bloqueás una app con el candado y nunca se cierra, ni manual ni automáticamente. |
| 🧹 **Liberar todo** | Fuerza la detención de todos los candidatos no excluidos y reporta lo liberado (estimado y medido por el sistema). |
| ☑️ **Selección manual** | Elegí apps puntuales, o usá *Elegir* para preseleccionar todos los candidatos. |
| 🔁 **Auto-refresh** | Se actualiza cada 20 s mientras la app está abierta (se puede apagar). |
| 🩺 **Diagnóstico** | Prueba los comandos con privilegios, muestra el código de salida y la salida cruda, y lo copiás con un toque. |

---

## 🧠 Cómo clasifica los procesos

Android le asigna a cada proceso un `oom_score_adj`. Cuanto más alto, antes lo mata el sistema cuando falta memoria. RamGuard usa ese mismo criterio:

| `oom_score_adj` | Estado | ¿Se muestra? |
|:---:|---|---|
| `< 200` | En uso (foreground) | No |
| `200 – 299` | Servicio | Solo si activás *Incluir servicios* |
| `300 – 899` | Segundo plano | Sí |
| `≥ 900` | En caché | Sí |

---

## 🔐 Modos de privilegio

Android **no permite** que una app normal cierre otras apps: `FORCE_STOP_PACKAGES` es un permiso de sistema. Por eso RamGuard funciona en tres modos:

| Modo | Cómo consigue privilegios | Qué puede hacer |
|------|---------------------------|-----------------|
| 🟢 **Root** | `su` (Magisk / KernelSU / APatch) | Todo: listar procesos reales, leer RSS y ejecutar `am force-stop`. |
| 🔵 **Shizuku** | Depuración inalámbrica, sin root | Lo mismo, corriendo como `uid 2000` (shell). |
| 🟠 **Sin privilegios** | — | Solo `killBackgroundProcesses()` en modo *best effort*, lista aproximada y acceso directo a Ajustes. |

> Root nunca se consulta solo al arrancar para no disparar el pedido de superusuario en cada apertura: solo se usa si lo activás vos desde *Configurar*.

### Límites que conviene tener claros

- Android **reinicia** apps con autoarranque, jobs, alarmas o push. Cerrar no es permanente.
- Matar apps del sistema puede romper notificaciones, alarmas o la interfaz.
- La memoria "liberada" que informa Android es una **estimación**: el sistema reutiliza la caché de forma perezosa.

---

## 🛡️ Protecciones

RamGuard se niega a cerrar, sin excepción, lo que podría dejarte el teléfono inutilizable:

- La propia app y el **launcher** activo
- Tu **teclado** (IME habilitados)
- **Servicios de accesibilidad** activos
- **Administradores del dispositivo**
- `systemui`, `settings`, telefonía, Bluetooth, NFC, instalador de paquetes y controlador de permisos
- **Google Play Services**, Play Store y WebView
- Proveedores del sistema (`com.android.providers.*`)

Además, antes de ejecutar cada comando se valida el nombre del paquete con una expresión regular estricta, así nada raro llega al `sh -c`.

La lista vive en [`Safety.kt`](app/src/main/java/io/github/scarlettbdl/ramguard/system/Safety.kt) por si querés ajustarla.

---

## 📲 Instalación

1. Bajá el APK desde **Releases**, o desde *Actions → Build APK → Artifacts* (`ramguard-apk`).
2. Instalalo permitiendo "instalar apps desconocidas".
3. Abrí la app y configurá los privilegios desde el aviso **Modo limitado**.

### Configurar Shizuku (sin root)

1. Instalá **Shizuku** (Play Store o F-Droid).
2. Inicialo desde *Depuración inalámbrica* en Opciones de desarrollador.
3. En RamGuard: **Configurar → Autorizar Shizuku** y aceptá el diálogo.

### Configurar root

1. Tené Magisk, KernelSU o APatch funcionando.
2. En RamGuard: **Configurar → Activar root** y aceptá el pedido de superusuario.

---

## ⚙️ Ajustes

- Incluir apps del sistema *(peligroso)*
- Incluir servicios activos
- Ignorar procesos menores a **N MB** (0 – 512 MB)
- Considerar inactiva una app después de **N horas** (1 – 48 h, modo sin privilegios)
- Actualizar automáticamente cada 20 s
- Borrar todas las exclusiones
- Acceso directo a *Acceso a datos de uso*

---

## 🏗️ Compilar

Requisitos: **JDK 17** y Android SDK (`compileSdk 35`, `minSdk 26`).

```bash
./gradlew assembleDebug      # APK de debug (applicationId con sufijo .debug)
./gradlew assembleRelease    # APK de release
```

### Con GitHub Actions

Cada push a `main` y cada pull request compilan `debug` y `release` y dejan los APK como artifact del run. Cada tag `v*` además publica un **GitHub Release** con los APK adjuntos.

```bash
git tag v0.1.0
git push origin v0.1.0
```

También se puede lanzar a mano con `workflow_dispatch`.

### Firma estable (opcional, recomendado)

Sin keystore propio, el APK release se firma con la debug key del runner, que cambia en cada build y obliga a desinstalar la versión anterior para actualizar. Para evitarlo:

```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias ramguard -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore > keystore.b64
```

Cargá estos *secrets* en el repo y el workflow firma el release automáticamente:

| Secret | Contenido |
|--------|-----------|
| `KEYSTORE_BASE64` | Salida de `keystore.b64` |
| `KEYSTORE_PASSWORD` | Contraseña del keystore |
| `KEY_ALIAS` | `ramguard` |
| `KEY_PASSWORD` | Contraseña de la key |

---

## 🗂️ Estructura

```
app/src/main/java/io/github/scarlettbdl/ramguard/
├── MainActivity.kt          # Activity + puente de callbacks de Shizuku
├── RamGuardApp.kt           # Application + contenedor de dependencias
├── data/                    # Ajustes y exclusiones (SharedPreferences)
├── model/                   # Modelos de dominio (RamStats, ProcEntry, KillReport…)
├── system/                  # Shell root/Shizuku, parser de ps, escaneo, kill y safety
├── ui/                      # Compose: tema, pantalla principal, hojas y componentes
├── util/                    # Formato de tamaños y fechas
└── vm/                      # ViewModel con el estado de la pantalla
```

### Cómo funciona por dentro

- **`PrivilegeManager`** decide el modo (root → Shizuku → ninguno) y entrega el `Shell` correspondiente.
- **`ProcessScanner`** obtiene procesos con `ps -A`, la importancia desde `/proc/<pid>/oom_score_adj` y los paquetes instalados con `pm list packages`.
- **`ProcessKiller`** ejecuta `am force-stop <paquete>` por cada candidato y compara la memoria disponible antes y después.
- **`ShizukuShell`** corre los comandos como `uid 2000`; usa `Shizuku.newProcess` por reflexión, que sigue existiendo en la API 13.x.

---

## 🧰 Stack

- **Kotlin** · **Jetpack Compose** · **Material 3**
- **Coroutines** + `StateFlow` / `SharedFlow`, arquitectura MVVM
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** `13.1.5` para privilegios de shell sin root
- **Gradle** (Kotlin DSL) + **GitHub Actions**

---

## 📄 Licencia

Uso personal. Si pensás publicarla en Google Play, revisá antes su política sobre `QUERY_ALL_PACKAGES`, que es un permiso restringido.

<div align="center">

Hecho con 💚 por [ScarlettBDL](https://github.com/ScarlettBDL)

</div>
