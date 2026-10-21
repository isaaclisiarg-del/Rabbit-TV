# Rabbit TV — atribuciones y licencias

Este inventario describe lo verificable en el repositorio de la rama integration/luncher-base. No es asesoramiento legal. Confirmar la licencia de cada recurso antes de distribuir el APK.

## Código y recursos

| Componente | Evidencia actual | Atribución/licencia | Acción antes de distribuir |
|---|---|---|---|
| Base Luncher | El README de upstream y docs/UPSTREAM_README.md identifican Luncher, de Christoforos Vasilakis, CVasilakis/luncher; la raíz contiene LICENSE | MIT; conservar copyright, texto de licencia y avisos en copias sustanciales | Confirmar commit/tag de upstream usado y que la licencia corresponde a ese origen. Anotar SHA/tag fijado. |
| Modificaciones Rabbit TV | Cambios de identidad, documentación y futuras modificaciones | La licencia raíz actual es MIT; no elimina avisos de componentes upstream | Confirmar que la raíz MIT cubre el código propio sin reemplazar licencias de assets. |
| Iconos/banner AOSP incluidos por Luncher | app/LICENSE-APACHE-2.0, app/NOTICE, cabeceras y app/README.md documentan ilustraciones derivadas de AOSP/Roboto | Apache License 2.0 según el inventario de la base | Conservar licencia, NOTICE y cabeceras; mantener NOTICE actualizado si se añade un derivado. |
| Rabbit Sentinel V2 | design/iconography/rabbit-sentinel-v2/README.md describe SVG/VectorDrawable como redibujo geométrico de una lámina conceptual, no conversión automática del PNG. MAPPING.md mapea doce módulos | **Titularidad/licencia de lámina y redibujo no demostradas en este repositorio** | Identificar autor/titular y fuente; obtener permiso/licencia para redistribuir app y fuentes; registrar licencia y aviso. Mantenerlo como material de diseño no empaquetado hasta resolverlo. |
| Fondo Rabbit TV | La conversación describe imagen nocturna 16:9 con conejo; el adjunto no está disponible entre los archivos recuperables de esta tarea | Origen, autor y licencia no registrados | Recuperar el arte aprobado y documentar autor y permiso/licencia antes de añadirlo al APK o repo. |
| Dependencias Gradle | Versiones declaradas en gradle/libs.versions.toml; producción incorpora AndroidX según sus build files | Cada dependencia usa su licencia publicada; inventario aún no generado desde build reproducible | Generar SBOM/inventario de dependencias resueltas y licencias; revisar dependencias transitivas y obligaciones. |

## Avisos que se deben conservar

- No borrar ni reducir la licencia MIT upstream mientras se distribuya una parte sustancial de Luncher.
- No afirmar que Rabbit Sentinel V2 es un asset oficial o de dominio público: el README actual solo documenta un redibujo basado en una lámina. Aclarar procedencia y permiso primero.
- Si se crea aviso de terceros para el APK, incluir componentes, versiones/origen, licencia y archivo de licencia/aviso correspondiente.
- Mantener archivos de licencia visibles y publicar los avisos requeridos junto al APK.
- No copiar imágenes, marca o iconos de terceros desde una app instalada en TV sin documentación de derechos.

## Estado de cumplimiento

- [x] MIT de Luncher presente en raíz.
- [x] Aviso Apache 2.0 y NOTICE de dibujos AOSP presentes bajo app/.
- [x] Inventario y acciones pendientes documentados.
- [ ] SHA/tag de Luncher fijado y anotado.
- [ ] Derechos/licencia de Rabbit Sentinel V2 verificados.
- [ ] Autor/licencia del fondo definitivo registrados.
- [ ] SBOM/licencias transitivas generados desde build reproducible.
- [ ] Avisos del paquete distribuible revisados.

## Referencias del repositorio

- [Luncher upstream](https://github.com/CVasilakis/luncher)
- [MIT License incluida](../LICENSE)
- [NOTICE de la app](../app/NOTICE) y [texto Apache 2.0](../app/LICENSE-APACHE-2.0)
- [Recursos Rabbit Sentinel V2](../design/iconography/rabbit-sentinel-v2/README.md)
- [README de la base importada](UPSTREAM_README.md)
