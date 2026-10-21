# Rabbit TV 🐇

Launcher ligero para Android TV / Google TV, con estética Lomiri Rabbit y navegación con control remoto.

> Estado real: el código base de Luncher y los recursos de diseño Rabbit Sentinel V2 ya están en la rama `integration/luncher-base`. La implementación visual/funcional de Rabbit TV, la licencia de algunos recursos y el build siguen pendientes de validar. No se instaló ni modificó nada en la TV.

## Producto previsto

- **Inicio:** encabezado Rabbit TV y solo las apps favoritas que el usuario elija. YouTube, Spotify y Twitch son candidatas iniciales, si están instaladas. Sin bienvenida, noticias, recomendaciones ni anuncios.
- **Dock lateral:** tres destinos: Inicio · Apps · Configuración.
- **Apps:** lista de aplicaciones TV disponibles, selección y reordenación de favoritas.
- **Configuración:** Personalización para elegir fondo local y restaurar el fondo predeterminado. La política de almacenamiento queda pendiente de medir el espacio real de la TV.
- **Identidad:** berenjena/violeta oscuro, superficies suaves y acento naranja. Los recursos Rabbit Sentinel V2 importados se describen como redibujos vectoriales de una lámina de referencia; comparar con el original y confirmar los derechos antes de empaquetarlos.
- **Fondo:** estética nocturna 16:9 con cosmos violeta/naranja, ciudad iluminada y conejo descansando. El arte aprobado aún no está disponible en el repo.

La [especificación del producto](docs/PRODUCT_SPEC.md) detalla alcance, aceptación y decisiones abiertas.

## Base y estado técnico

La base es [Luncher](https://github.com/CVasilakis/luncher), de Christoforos Vasilakis, bajo MIT. La licencia MIT original está presente en la raíz. Luncher aporta Kotlin nativo, Android Views y una arquitectura modular; el cambio de identidad no equivale todavía a implementar la experiencia Rabbit TV.

Estado comprobado de la rama:

- [x] Código base Luncher importado y rama existente.
- [x] Recursos y notas de Rabbit Sentinel V2 presentes.
- [x] Documentación de producto, rendimiento, checklist de TV y atribuciones añadida.
- [ ] Validación de build: pendiente; no existe resultado de CI para esta rama y no se pudo clonar el repo en este entorno.
- [ ] Confirmar origen/licencia de Rabbit Sentinel V2 y del fondo definitivo.
- [ ] Implementar y validar en emulador la experiencia de Inicio, Apps y Configuración de Rabbit TV.
- [ ] Pruebas con TV física; todavía no se conectó ni instaló nada.

## Fluidez

Las métricas propuestas son presupuestos iniciales, todavía no mediciones: respuesta de foco p95 ≤100 ms, primer frame tras arranque frío ≤2 s, retorno caliente ≤1 s, PSS en reposo ≤100 MiB y ≤150 MiB con 12 favoritas. El presupuesto de frame depende de la frecuencia de la TV.

Ver [requisitos de rendimiento](docs/PERFORMANCE.md) y su protocolo de medición.

## TV y licencias

La [lista de validación de TV](docs/DEVICE_CHECKLIST.md) reserva la instalación para una sesión futura autorizada y reversible. No configurar Rabbit TV como Home por defecto ni deshabilitar servicios del fabricante/Google durante la prueba inicial.

La [matriz de atribuciones](docs/ATTRIBUTIONS.md) registra MIT para Luncher y Apache-2.0/NOTICE para dibujos AOSP incluidos en la base. La procedencia/licencia de la lámina Rabbit Sentinel V2 y del fondo sigue sin confirmarse; mantener esos recursos fuera de un paquete distribuible hasta cerrar esa revisión.
