# Rabbit TV 🐇

Launcher ligero para Android TV / Google TV, con estética **Lomiri Rabbit** y navegación exclusivamente con control remoto.

> Estado: preparación del proyecto. No se ha instalado ni modificado nada en el televisor.

## Diseño acordado

- **Inicio:** encabezado `Rabbit TV` y solo las aplicaciones que el usuario elija. Favoritas iniciales previstas: **YouTube, Spotify y Twitch**, si están instaladas. Sin mensaje «Bienvenido a casa», noticias, recomendaciones, anuncios ni sugerencias.
- **Dock lateral:** tres destinos, exactamente **Inicio · Apps · Configuración**.
- **Apps:** lista de aplicaciones TV instaladas, selección/deselección de favoritas y reordenación para la pantalla de inicio.
- **Configuración:** sección **Personalización** para escoger fondo local, restaurar predeterminado y ajustar lo estrictamente necesario. La política de almacenamiento se definirá tras medir el espacio libre real de la TV.
- **Identidad:** paleta Lomiri Rabbit (berenjena/violeta oscuro, superficies suaves, acento naranja), iconografía propia del ecosistema y mascota oficial del conejo de **Rabbit Sentinel**. Los recursos definitivos del conejo requieren integrar sus assets oficiales, no dibujar un sustituto.
- **Fondos:** estética nocturna, cosmos violeta y naranja, ciudad iluminada y conejo descansando. Se prioriza una imagen 16:9 optimizada para pantalla y consumo de memoria.

## Base técnica

La base elegida es [Luncher](https://github.com/CVasilakis/luncher), de Christoforos Vasilakis, distribuida bajo la **licencia MIT**. Los avisos de derechos de autor y la licencia original deben conservarse en toda copia o redistribución que contenga su código.

Se conservarán los principios que hacen ligera la interfaz: **Kotlin nativo + Android Views**, caché de banners redimensionados, reutilización de tarjetas, procesamiento secundario con prioridades, actualizaciones incrementales y ausencia de servicios permanentes innecesarios.

La interfaz inicial no requiere Jetpack Compose ni Flutter. Pueden valorarse posteriormente si se demuestra que no empeoran la experiencia en el hardware real.

## Prioridad: fluidez

1. Al abrir Inicio, cargar primero fondo y favoritas ya preparadas.
2. Evitar lectura de disco, decodificación de imágenes y llamadas lentas al PackageManager durante los cambios de foco.
3. Cambiar de foco mediante una animación mínima de escala/contorno, de forma que el procesamiento permanezca dentro del presupuesto del fotograma.
4. Preparar fondos a la resolución lógica necesaria; no mantener originales enormes en memoria.
5. Mantener Inicio reutilizable al regresar desde otra app y no hacer trabajo mientras no esté visible.
6. Medir arranque frío/caliente, memoria y jank con la TV real. Los 60 FPS a 60 Hz y arranque subsegundo son **objetivos, no garantías**.

## Instalación y seguridad

El APK se instalará y probará por **ADB** cuando el usuario esté frente a la TV. Instalar no equivale a reemplazar permanentemente Home: primero se comprobará qué launchers y qué restricciones impone la versión del fabricante. **No deshabilitar launchers ni servicios de Google hasta verificar una forma reversible de volver al inicio original.** Proteger la compatibilidad de YouTube, YouTube Music y Spotify.

## Plan

- [ ] Importar y fijar la versión de Luncher seleccionada, preservando `LICENSE`.
- [ ] Renombrar aplicación/namespace a Rabbit TV sin romper las actividades Home y Leanback.
- [ ] Simplificar inicio, dejando encabezado, favoritas y dock de tres entradas.
- [ ] Pantalla Apps con selección y orden de favoritas.
- [ ] Configuración > Personalización > fondo local y restaurar fondo.
- [ ] Importar iconografía real y conejo de Rabbit Sentinel.
- [ ] Compilar APK de depuración, probar navegación y retorno desde apps.
- [ ] Medir en la TV, optimizar y generar APK Release.

Documentación ampliada: `docs/PRODUCT_SPEC.md`, `docs/PERFORMANCE.md` y `docs/DEVICE_CHECKLIST.md`.
