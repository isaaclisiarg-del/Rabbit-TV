# Rabbit TV — requisitos de rendimiento

## Estado y alcance

Los valores de esta guía son **presupuestos iniciales de ingeniería**, no resultados medidos ni garantías para todo televisor. La rama aún no tiene build verificado ni modelo de TV de referencia documentado. Tras compilar y conseguir el dispositivo, registrar versión del sistema, resolución/Hz, RAM y método de medida; ajustar límites con una medición de base acordada.

El principio heredado de Luncher es mantener corto el camino de foco y dibujo: no leer archivos, decodificar imágenes ni consultar sincrónicamente PackageManager durante una pulsación del control.

## Presupuestos iniciales

| Métrica | Objetivo inicial | Verificación |
|---|---:|---|
| Respuesta visual de foco (p95) | ≤ 100 ms desde tecla recibida | Repetir D-pad sobre lista cargada; registrar marcas de tiempo o video |
| Render a 60 Hz | p95 ≤ 16,7 ms; frames > 33,3 ms < 1% durante navegación estable | Android Studio/JankStats o Perfetto en hardware de referencia |
| Primer frame, arranque frío | ≤ 2,0 s en hardware objetivo | Forzar cierre, iniciar Home; medir con Macrobenchmark/Perfetto, 10 repeticiones; reportar mediana y p90 |
| Retorno desde app externa | ≤ 1,0 s cuando se conserva el proceso | Abrir app y volver; 10 repeticiones |
| PSS en reposo | ≤ 100 MiB tras 2 min sin interacción | dumpsys meminfo en dispositivo de referencia |
| PSS con Inicio cargado | ≤ 150 MiB con hasta 12 favoritas | Igual medición, registrar estado y conteo |
| Fondo en memoria | Decodificar al tamaño de pantalla; evitar original y copia escalada simultáneos | Prueba con imagen 4K; bitmap principal objetivo ≤ 16 MiB |
| Tamaño APK debug | Sin objetivo hasta conocer dependencias y baseline | Registrar artefacto generado |

Los límites de frames presuponen 60 Hz. Si la TV funciona a otra frecuencia, convertirlos al presupuesto de frame correspondiente y anotarlo.

## Reglas de implementación

- Mantener el contenido visible y foco previo al volver de otras apps; actualizar solo datos que cambiaron.
- Cargar iconos y banners fuera del hilo principal y al tamaño adecuado.
- Reutilizar vistas/tarjetas y limitar el trabajo por cambio de foco a actualización visual local.
- Leer/actualizar la lista de paquetes en trabajo diferido, no en cada movimiento D-pad.
- Decodificar el fondo con muestreo/tamaño objetivo; evitar conservar bitmap fuente y copia escalada simultáneamente.
- No animar decoraciones de fondo continuamente. Mantener breve y acotada la animación de foco.
- Suspender trabajo de UI cuando el launcher no está visible; evitar servicios persistentes.
- Ante memoria o espacio limitado, usar el fondo por defecto y evitar crashes o reintentos en bucle.

## Protocolo de medida

1. Construir el commit/configuración que se entrega y guardar SHA, variant, versión Android y configuración del dispositivo.
2. Medir al menos diez arranques fríos y diez retornos calientes; reportar mediana y p90.
3. Navegar Inicio, Apps y Configuración durante dos minutos con D-pad; registrar frames/jank y PSS.
4. Repetir con 0, 3 y 12 favoritas, con/sin red, y fondo predeterminado/imagen local grande.
5. Confirmar que la TV no termina el proceso bajo presión de memoria y que volver desde YouTube/Spotify recupera Inicio.
6. Guardar método, condiciones y resumen en un informe del dispositivo; no presentar objetivos como aprobados sin evidencia.

## Criterio de aceptación

Un objetivo se aprueba solo si la medición identifica dispositivo, build y método, y cumple el umbral en las repeticiones definidas. Si el hardware no alcanza un límite, registrar la desviación y decidir explícitamente si optimizar o ajustar el presupuesto antes de release.
