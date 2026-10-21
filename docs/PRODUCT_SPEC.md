# Rabbit TV — especificación de producto

## Propósito

Rabbit TV es un launcher ligero para Android TV y Google TV, pensado para control remoto. Reutiliza Luncher como base Android nativa y adapta su identidad y experiencia a Rabbit TV. El objetivo es ofrecer un inicio limpio que dé acceso rápido a las apps elegidas, sin anuncios, feed ni recomendaciones.

Esta especificación fija el alcance de producto para la primera versión funcional. Los puntos que necesitan una decisión o evidencia del dispositivo están marcados como pendientes; no deben leerse como funciones ya implementadas.

## Usuarios y contexto

- Persona que usa un televisor Android TV / Google TV con control remoto.
- Interacción desde el sofá: foco visible, controles grandes y navegación predecible.
- No se requiere pantalla táctil.
- El launcher no sustituye servicios del fabricante, la tienda ni las apps de video.

## Experiencia principal

### Inicio

- Mostrar el nombre Rabbit TV, un fondo panorámico y las apps favoritas del usuario.
- Navegación completa con flechas, OK/Enter, Atrás y Home cuando el sistema permita entregarlo.
- El foco debe permanecer visible y no depender exclusivamente del color.
- Las favoritas iniciales propuestas son YouTube, Spotify y Twitch. Mostrar solo las instaladas; permitir cambiarlas después. Confirmar la selección antes de cerrar la primera versión.
- No mostrar bienvenida, noticias, publicidad, contenido recomendado ni una fila automática de apps no elegidas.
- Al volver de una app, conservar el estado de inicio y el foco previo si Android conserva el proceso.

### Navegación

El dock lateral tiene exactamente tres destinos en este orden:

1. **Inicio** — pantalla principal y favoritas.
2. **Apps** — aplicaciones visibles para TV instaladas y administración de favoritas.
3. **Configuración** — personalización y opciones básicas.

Al cambiar entre destinos, el foco inicial y la ruta de Atrás deben ser predecibles. Atrás no debe salir silenciosamente ni abrir pantallas del sistema inesperadas.

### Apps y favoritas

- Consultar aplicaciones que el sistema expone como lanzables en TV; no enumerar ni iniciar paquetes ocultos con métodos no soportados.
- Mostrar nombre e icono, y abrir con el intent del sistema.
- Poder añadir, quitar y reordenar favoritas.
- Si una app favorita se desinstala o deja de estar disponible, no bloquear Inicio; mostrar un estado recuperable o retirar la entrada obsoleta.
- No asumir nombres de paquete fijos para servicios del fabricante. Detectar disponibilidad en el dispositivo.

### Configuración y fondos

- Incluir **Personalización** y las opciones necesarias para elegir una imagen local como fondo y restaurar el fondo por defecto.
- Solicitar acceso mediante el selector del sistema y guardar una copia propia solo si la estrategia de almacenamiento elegida lo necesita.
- Respetar el permiso concedido, eliminar la copia al reemplazarla si corresponde y explicar cualquier limitación.
- El diseño buscado es 16:9, nocturno y limpio, con cosmos violeta/naranja, ciudad iluminada y el conejo descansando. La imagen fuente adjunta a la conversación no está disponible en el repositorio actual; recuperar el original aprobado antes de integrarlo.
- No decidir aún compresión, persistencia o espacio máximo: medir el espacio libre y acordar un límite primero.

### Identidad visual y accesibilidad

- Paleta prevista: berenjena/violeta oscuro, superficies lavanda suaves y acento naranja Lomiri.
- Iconografía Rabbit Sentinel V2 en acabado mate. El directorio de diseño la describe como redibujo vectorial conceptual; comparar con la lámina aprobada y verificar titularidad/licencia antes de empaquetarla.
- Evitar degradados o volumen si contradicen esa propuesta.
- Texto legible desde la distancia, contraste suficiente sobre fondos y estados de foco diferenciados por contorno/escala además del color.
- Probar escala de texto, TalkBack donde sea compatible y navegación sin teclado.

## Fuera del alcance inicial

Reproductor, agregador de streaming, gestión de cuentas, recomendaciones, anuncios, control remoto por teléfono, sincronización en nube y servicio residente. No modificar firmware, permisos de sistema, servicios Google o launcher del fabricante. No convertir Rabbit TV en launcher predeterminado sin prueba de recuperación reversible y autorización expresa posterior.

## Privacidad y permisos

- Pedir solo permisos imprescindibles para mostrar apps y seleccionar fondo.
- No recopilar historial de uso, credenciales, telemetría ni datos personales en la primera versión.
- No ejecutar trabajo permanente en segundo plano.
- Documentar cualquier permiso que se descubra como necesario antes de añadirlo.

## Criterios de aceptación de la primera versión funcional

- El APK debug compila y abre; esto está pendiente de comprobar para la rama actual.
- Android reconoce la aplicación como app Leanback y como opción Home, sin configurarla automáticamente como launcher por defecto.
- Todas las funciones visibles se recorren con control remoto.
- Inicio muestra solo favoritas elegidas y disponibles; Apps abre aplicaciones instaladas.
- Configuración permite seleccionar y restaurar fondo, con almacenamiento validado y límites documentados.
- Atrás, Home y retorno de una app externa no dejan foco perdido ni bloquean el launcher.
- No hay anuncios, feeds ni procesos permanentes añadidos.
- La verificación de licencia de cada recurso empaquetado está completa.

## Decisiones pendientes

- Modelo, versión Android/firmware, resolución, Hz, almacenamiento libre y disponibilidad de ADB de la TV.
- Confirmar apps iniciales, comportamiento de elementos ausentes y orden.
- Adjuntar/ubicar el fondo final aprobado.
- Identificar fuente de la lámina Rabbit Sentinel V2 y confirmar permiso/licencia para distribuir sus redibujos.
- Elegir límite de almacenamiento de fondos después de medir el dispositivo.
- Elegir métricas finales de rendimiento con un build reproducible y hardware de referencia.
