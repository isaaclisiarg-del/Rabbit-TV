# Rabbit TV — checklist de validación en TV

Este checklist es para una sesión futura, con el usuario presente y después de revisar el APK. **No usar para instalar ahora ni cambiar el launcher predeterminado.** Hasta entonces, las tareas de hardware quedan pendientes.

## 1. Identificar el dispositivo

- [ ] Marca/modelo exacto de TV o box:
- [ ] Versión Android / Google TV y número de compilación:
- [ ] Resolución y frecuencia de refresco:
- [ ] RAM y almacenamiento libre:
- [ ] Control remoto y botones disponibles (Home, Atrás, D-pad, OK):
- [ ] Apps instaladas y variantes (YouTube, YouTube Music, Spotify, Twitch):
- [ ] Método de depuración disponible y autorizado por el usuario:
- [ ] Launcher original y ruta de regreso documentados:
- [ ] Estado inicial anotado; no cambiar ajustes aún.

## 2. Antes de instalar

- [ ] Registrar SHA del commit probado y verificar build debug exitoso.
- [ ] Confirmar que es APK debug; revisar package/application ID.
- [ ] Revisar tamaño, permisos y firma del APK.
- [ ] Tener disponible el launcher original y saber volver a seleccionarlo desde Ajustes.
- [ ] Obtener autorización expresa para conectar ADB e instalar en esta TV.
- [ ] Confirmar espacio libre suficiente; abortar si falta margen.
- [ ] Anotar Home actual; no borrar datos, desactivar apps ni cambiar permisos especiales.
- [ ] No instalar Release, cambiar Home por defecto ni deshabilitar servicios de fabricante/Google en esta fase.

## 3. Instalación de prueba

- [ ] Instalar solamente el APK debug como app paralela.
- [ ] Abrir desde la lista de apps del sistema.
- [ ] Confirmar que el launcher original sigue disponible.
- [ ] Rechazar diálogo para establecer Rabbit TV como Home predeterminado durante esta prueba.
- [ ] Desinstalar la app debug y confirmar regreso limpio si falla.

## 4. Navegación con control remoto

- [ ] Foco inicial visible en Inicio y no escondido detrás del dock.
- [ ] Flechas navegan en orden lógico entre dock, favoritas y contenido.
- [ ] OK abre únicamente el elemento enfocado.
- [ ] Atrás vuelve al panel anterior o a Inicio de forma predecible.
- [ ] Home abre el launcher elegido por el sistema.
- [ ] Sin foco fuera de pantalla, trampas de foco ni dependencia de táctil/teclado.
- [ ] Repetir en bordes de pantalla y con texto largo o iconos ausentes.

## 5. Apps externas y recuperación

- [ ] Abrir y volver desde YouTube.
- [ ] Abrir y volver desde YouTube Music, si está instalada.
- [ ] Abrir y volver desde Spotify.
- [ ] Probar Twitch y otras favoritas disponibles.
- [ ] Confirmar recuperación de foco si el proceso permanece.
- [ ] Probar paquete desinstalado: debe ser recuperable, no provocar crash.
- [ ] Confirmar que playback y botones de sistema siguen funcionando fuera del control del launcher.

## 6. Personalización y resiliencia

- [ ] Elegir imagen local mediante selector del sistema.
- [ ] Comprobar recorte 16:9, orientación, nitidez y memoria con imagen grande.
- [ ] Reiniciar app y comprobar persistencia de fondo.
- [ ] Restaurar fondo predeterminado.
- [ ] Revocar acceso a imagen; confirmar estado claro y recuperable.
- [ ] Probar poco espacio libre y cancelación del selector.
- [ ] Confirmar que no queda actividad, notificación ni CPU permanente tras salir.

## 7. Rendimiento y aspecto

- [ ] Ejecutar el protocolo de [PERFORMANCE.md](PERFORMANCE.md).
- [ ] Registrar diez arranques fríos y diez retornos calientes.
- [ ] Navegar Inicio, Apps y Configuración dos minutos; registrar jank y PSS.
- [ ] Probar red desconectada y conexión lenta.
- [ ] Comprobar contraste del foco, texto legible y recortes de overscan.
- [ ] Revisar TalkBack/escala de texto si el sistema lo soporta.
- [ ] Adjuntar SHA, build variant, versión del sistema, resumen y resultado.

## 8. Sustitución opcional de Google TV Home

- [ ] Habilitar únicamente desde Rabbit TV → Configuración → Sistema → Pantalla de inicio.
- [ ] Activar «Usar Rabbit TV como inicio» y habilitar Rabbit TV manualmente en Ajustes → Accesibilidad.
- [ ] Probar Home desde YouTube, Spotify y otras apps; confirmar que no se interrumpe la reproducción ni se redirigen ajustes o diálogos.
- [ ] Repetir después de reiniciar; confirmar Chromecast, Wi-Fi, HDMI y el control remoto.
- [ ] Pulsar «Restaurar inicio original» y confirmar que vuelve a abrirse Google TV Home.
- [ ] Mantener habilitados LauncherX (com.google.android.apps.tv.launcherx), SetupWraith (com.google.android.tungsten.setupwraith) y Google MediaShell (com.google.android.apps.mediashell).
- [ ] No declarar la sustitución operativa sin medirla en el televisor. El inicio de una Activity desde un servicio en segundo plano está sujeto a las restricciones y excepciones de Android; el resultado puede variar según el firmware. Si Rabbit TV no pasa al frente, registrar el mensaje de ActivityTaskManager/Logcat y dejar la sustitución desactivada.

## 9. Recuperación y cierre

- [ ] Volver a seleccionar launcher original desde Ajustes; comprobar tecla Home.
- [ ] Confirmar que no se modificó servicio del sistema ni app del fabricante.
- [ ] Si Rabbit TV llegara a elegirse como Home, probar antes cómo regresar.
- [ ] Desinstalar APK debug si el usuario lo solicita.
- [ ] Registrar defectos, reproducción y si parece corresponder al launcher o fabricante.
- [ ] No declarar compatibilidad general a partir de una única TV; reportar solo modelo probado.
