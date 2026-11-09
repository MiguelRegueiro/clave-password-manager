# Publicar una versión

El workflow `.github/workflows/release-android.yml` construye el APK en Ubuntu y publica una GitHub Release cuando se sube un tag con el formato `v*`.

## Preparación única

El APK debe estar firmado siempre con la misma clave para que Android pueda instalar actualizaciones. Configura estos secrets en el repositorio de GitHub antes de publicar:

- `CLAVE_KEYSTORE_BASE64`: el resultado de codificar `.signing/clave.jks` en Base64.
- `CLAVE_STORE_PASSWORD`: el valor de `storePassword` de `.signing/signing.properties`.

En Linux, con GitHub CLI autenticado, puedes cargarlos sin imprimirlos en pantalla:

```sh
base64 -w 0 .signing/clave.jks | gh secret set CLAVE_KEYSTORE_BASE64
gh secret set CLAVE_STORE_PASSWORD
```

Guarda una copia segura de `.signing/clave.jks` y de su contraseña. No deben añadirse al repositorio.

## Probar el build

En la pestaña **Actions**, ejecuta manualmente **Release Android APK** desde `main`. Generará un artefacto descargable durante 14 días, pero no creará un release.

## Publicar

1. Actualiza `versionName` y `versionCode` en `native/app/build.gradle.kts`. El nombre de la versión debe coincidir con el tag sin la `v`.
2. Sube el commit de la versión a `main`.
3. Crea y sube el tag anotado:

```sh
git tag -a v1.0.0 -m "Clave v1.0.0"
git push origin v1.0.0
```

El workflow ejecuta las pruebas de release, genera `clave-v1.0.0.apk` y su checksum SHA-256, y los adjunta a la GitHub Release.
