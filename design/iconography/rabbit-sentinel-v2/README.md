# Rabbit Sentinel • iconografía mate v2 (pendiente de integración)

Estado: **preparado para el siguiente parche; no activado en la aplicación.**

Diseño basado en la **última lámina clara aprobada** en conversación el 18 de octubre de 2026, con conejo de expresión neutral: ojos más redondeados, mandíbula corta y contornos suavizados. Es un **redibujo geométrico vectorial** del concepto, no una conversión automática del PNG: revisar frente a la lámina aprobada antes de fusionar.

## Contenido
- `svg/brand/`: emblema maestro editable (claro, oscuro, monocromo y muestras sobre fondo mate).
- `svg/modules/`: doce iconos individuales con dibujo vectorial plano.
- `android-ready/drawable/`: VectorDrawable nativos para los 12 módulos y el launcher.
- `android-ready/values*/`: colores independientes para tema claro y oscuro.
- `android-ready/mipmap-anydpi-v26/`: ejemplo de adaptive icon Android con capa monocromática.
- `palette.json`: colores de marca en formato de intercambio.

Paleta: Aubergine `#77216F`, naranja Ubuntu `#E95420`, lavanda `#E4D0EA` y carbón `#3A1239`. Acabado mate, sin degradados ni volumen.

## Próximo parche
1. Copiar `android-ready/drawable/rs2_*.xml` a `app/src/main/res/drawable` y los colores a sus directorios `values` / `values-night`.
2. Integrar las imágenes de los módulos con `painterResource(R.drawable.rs2_wifi)`, etc., en los destinos Compose apropiados, sustituyendo solo la iconografía deseada.
3. Al aprobar y probar el launcher, copiar `android-ready/mipmap-anydpi-v26/` a la misma ruta bajo `app/src/main/res/` y cambiar `android:icon` en `AndroidManifest.xml` desde `@drawable/ic_sentinel` a `@mipmap/ic_launcher`.
4. Comprobar máscara circular y squircle del icono adaptable y el icono temático; validar contraste, escalado en 24dp, TalkBack, modo oscuro y compilación Android 17 / API 37.

**IMPORTANTE:** La aplicación existente no usa estos recursos todavía. La rama **no modifica código activo** ni el icono actual. El XML de ejemplo de `ic_launcher` se guarda fuera de `app/src/main/res` de forma intencional. No confundir propuesta gráfica con implementación compilada.

Los SVG del logo usan viewport 108x108; las capas foreground XML llevan escala del 72% centrada para evitar recortes de la máscara Android. SVG no debe insertarse directamente en `res/drawable` de Android sin convertir.
