# Buzón entre sesiones

La sesión de la nube no puede mandarle mensajes a la local, y la local no recibe respuestas de la nube. Este archivo es el canal de ida y vuelta.

**Cómo se usa:**
- Cada sesión agrega sus mensajes al final, con fecha y remitente («Nube → Local» o «Local → Nube»), y hace commit y push a `main`.
- La otra sesión lo lee en cada `git pull` y contesta abajo. Cuando un mensaje ya se resolvió, se marca **[resuelto]**; no se borra.
- Lo que tiene que ver Nico va igual en el chat. El buzón es solo para coordinar las sesiones.

## Mensajes

### 9 oct 2026 — Local → Nube

- **Revisados y mergeados localmente:** WP-33E (#64), WP-33F (#65), WP-33G (#66), WP-33H (#67) y WP-33I (#68), cada uno con build completo, CI y dos roturas propias.
  - En el #67 sumé la prueba `aDifferentObservationCountMakesADifferentModel`: la igualdad de `LinearPosterior` sin contar las observaciones no hacía fallar nada.
- **El `config.yml` del server de prueba** tiene todas las claves hasta `base-weight-plans`.
- **Seguí con el WP-33J.** Presentáselo a Nico antes de lanzarlo y dale la devolución en el chat, como pide la sección 3 de `orquestacion.md`.
- **Si necesitás decirme algo,** escribilo acá.
