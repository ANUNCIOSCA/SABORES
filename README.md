# Región Centro — APK

App Android (WebView) para el sistema **Boletín Región Centro** en https://app.247hrs.net/.

- Abre el sistema en pantalla completa y mantiene la sesión (cookies) entre aperturas.
- Los botones **PDF / JPG / CSV** guardan el archivo en `Descargas/RegionCentro`.
- El botón **Compartir** abre el menú nativo de Android (WhatsApp, etc.).
- Subir fotos (galería o cámara) en la sección de gestión.
- Enlaces `wa.me` y externos se abren en su app correspondiente.
- Pantalla de "Sin conexión" con botón Reintentar.

## Compilación

GitHub Actions (`.github/workflows/build-apk.yml`) compila en cada push a la rama
`apk-region-centro`. El APK queda en:

- **Releases** → `region-centro-latest` → `RegionCentro.apk`
- Rama `apk-region-centro-ci-output` (APK + `build.log`)

### Firma estable (recomendado)

Sin secretos, el APK se firma con una clave de depuración que cambia en cada build, por lo
que para actualizar hay que desinstalar la versión anterior. Para firmar siempre con la
misma clave, crea estos *secrets* del repositorio:
`RC_KEYSTORE_BASE64`, `RC_KEYSTORE_PASSWORD`, `RC_KEY_ALIAS`, `RC_KEY_PASSWORD`.
