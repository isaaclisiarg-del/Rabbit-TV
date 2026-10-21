# Rabbit TV 🐇

Launcher ligero para Android TV / Google TV, con estética Lomiri Rabbit y navegación con control remoto.

> Estado real: Rabbit TV implementa Inicio, Apps y Configuración sobre Luncher en `integration/luncher-base`. El fondo nocturno aprobado con ciudad y conejo no está en el repositorio; se usa temporalmente el fondo berenjena liso. El APK no se compiló en esta entrega, por solicitud, y no se instaló ni modificó nada en la TV.

## Producto previsto

- **Inicio:** encabezado Rabbit TV y solo las apps favoritas que el usuario elija. YouTube, Spotify y Twitch son candidatas iniciales, si están instaladas. Sin bienvenida, noticias, recomendaciones ni anuncios.
- **Dock lateral:** tres destinos: Inicio · Apps · Configuración.
- **Apps:** lista de aplicaciones TV disponibles, selección y reordenación de favoritas.
- **Configuración:** Personalización para elegir fondo local y restaurar el fondo predeterminado; Sistema → Pantalla de inicio ofrece una sustitución opcional de Google TV Home mediante accesibilidad, aún sin verificar en hardware.
- **Identidad:** berenjena/violeta oscuro, superficies suaves, acento naranja e iconografía Rabbit Sentinel V2 disponible en el repositorio.
- **Fondo:** el editor admite imágenes locales y las prepara al tamaño de pantalla. El arte nocturno 16:9 aprobado con ciudad espacial y conejo sigue pendiente; el color berenjena liso es temporal.

La [especificación del producto](docs/PRODUCT_SPEC.md) detalla alcance, aceptación y decisiones abiertas.

## Base y estado técnico

La base es [Luncher](https://github.com/CVasilakis/luncher), de Christoforos Vasilakis, bajo MIT. La licencia MIT original está presente en la raíz. Luncher aporta Kotlin nativo, Android Views y una arquitectura modular; el cambio de identidad no equivale todavía a implementar la experiencia Rabbit TV.

Estado comprobado de la rama:

- [x] Código base Luncher importado y rama existente.
- [x] Recursos y notas de Rabbit Sentinel V2 presentes.
- [x] Documentación de producto, rendimiento, checklist de TV y atribuciones añadida.
- [ ] Validación de build: pendiente; esta entrega no generó APK para que se compile desde el PC del usuario.
- [ ] Confirmar origen/licencia de Rabbit Sentinel V2 y del fondo definitivo.
- [x] Implementadas las pantallas Inicio, Apps y Configuración, con favoritas, ordenación por control remoto y personalización del fondo.
- [ ] Validar la experiencia en emulador o hardware de referencia.
- [ ] Pruebas con TV física; todavía no se conectó ni instaló nada.

## Fluidez

Las métricas propuestas son presupuestos iniciales, todavía no mediciones: respuesta de foco p95 ≤100 ms, primer frame tras arranque frío ≤2 s, retorno caliente ≤1 s, PSS en reposo ≤100 MiB y ≤150 MiB con 12 favoritas. El presupuesto de frame depende de la frecuencia de la TV.

Ver [requisitos de rendimiento](docs/PERFORMANCE.md) y su protocolo de medición.

## TV y licencias

La [lista de validación de TV](docs/DEVICE_CHECKLIST.md) reserva la instalación para una sesión futura autorizada y reversible. No configurar Rabbit TV como Home por defecto ni deshabilitar servicios del fabricante/Google durante la prueba inicial.

La [matriz de atribuciones](docs/ATTRIBUTIONS.md) registra MIT para Luncher y Apache-2.0/NOTICE para dibujos AOSP incluidos en la base. La procedencia/licencia de la lámina Rabbit Sentinel V2 y del fondo sigue sin confirmarse; mantener esos recursos fuera de un paquete distribuible hasta cerrar esa revisión.
