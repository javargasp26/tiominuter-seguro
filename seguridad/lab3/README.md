# Lab 3 · Gate de seguridad en el pipeline

Repo: javargasp26/tiominuter-seguro (snapshot público y sanitizado de TioMinuterMVI: sin GIFs ni config de Firebase).
Equipo: Javier Alejandro Vargas (@javargasp26) · Tatiana García (@Tatianag2)

## Evidencia

| Entregable | Dónde |
|---|---|
| Gate instalado | `.github/workflows/auditgate.yml` (Auditgate fijado por SHA, solo gate determinista) |
| Check requerido | Protección de `main`: PR obligatorio + `auditgate / 🚦 Security Gate` requerido |
| PR que bloquea → verde | PR #2 `feat: cliente de pagos`: API key hardcodeada en `PaymentsClient.kt:4` (gitleaks `generic-api-key`) → bloqueado; corregido inyectando la llave y reescribiendo el commit → verde |
| Regla propia | `rules/firestore/firestore-escritura-solo-auth.yaml` + fixture `.rules` (4 vulnerables, 3 seguros), `semgrep --test` en verde (PR #3) |
| PoC de la regla | PR #4: simplificar `allow update` de `groups` a `if isSignedIn();` → bloqueado en `firebase/firestore.rules:752`; cerrado sin mezclar |

## Trazabilidad de la regla

- **Amenaza:** Lab 1 · T3 · Un miembro se hace dueño del grupo (STRIDE: Elevación de privilegios).
- **Requisito (Lab 1):** solo el `ownerUid` actual puede cambiar `ownerUid`, y solo a un uid que ya es miembro.
- **ASVS 5.0:** V8 Authorization · **CWE-862** Missing Authorization.
- **Por qué esta regla:** la app no tiene backend (plan Spark); las Firestore Security Rules son la única capa de autorización, y Dart no tiene reglas de Semgrep. La regla cubre justo ese hueco.
- **Validación:** contra `firebase/firestore.rules` real dio 0 hallazgos (sin falsos positivos). Al validar se encontró un falso negativo: el código usa el helper `isSignedIn()` en vez de `request.auth != null`; se amplió la regla y se agregó el caso al fixture antes de mezclar el PR #3.

## Reflexión

1. **Qué atrapó el gate:** una API key hardcodeada en Kotlin (PR #2, secrets) y la reintroducción de T3 en las reglas de Firestore (PR #4, SAST con la regla propia). En ambos casos solo revisó lo nuevo del PR y bloqueó el merge con el único check requerido.
2. **¿El triage acertó TP/FP?** No se configuró agente de IA; el triage fue manual. Los dos hallazgos de los PR son verdaderos positivos. En el snapshot inicial gitleaks dio 41 hallazgos: las claves de traducción (`titleKey`/`parsedKey`) y un JWT sintético de prueba eran falsos positivos y quedaron excluidos en `.gitleaks.toml` con su razón; las 2 llaves reales de Firebase eran verdaderos positivos y se quitaron del repo antes de publicarlo. Dato aparte: la app externa `gitleaks` del PR #2 salió en verde y no vio la llave; el gate de Auditgate sí.
3. **Deuda vieja que dejó pasar y por qué está bien:** el código existente no se re-evalúa (gate diff-aware con baseline en el merge-base) y los 127 archivos Dart quedan SIN COBERTURA. Si el gate bloqueara la deuda histórica, ningún PR podría mezclarse y el equipo lo apagaría. La deuda se gestiona aparte (backlog, reglas nuevas como la de Firestore), y el gate garantiza que no crezca.

## Limitaciones observadas

- `auditgate scan` local termina en `exit=2` porque `osv-scanner` falla extrayendo `firebase/rules-tests/package-lock.json` en macOS; en CI el job de dependencias corre bien.
- El ID de la regla sale con el prefijo de la ruta temporal del worktree en el CLI (cosmético).
- Como el autor es admin, GitHub ofrece "bypass rules"; para endurecerlo, activar "Do not allow bypassing the above settings".
