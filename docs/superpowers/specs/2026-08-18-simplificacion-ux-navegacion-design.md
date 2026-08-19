# Simplificación de la navegación y jerarquía visual de Agora

Fecha: 2026-08-18
Estado: aprobado, pendiente de plan de implementación

## Problema

La app tiene 5 tabs (Agora, Comunidades, Actividades, Notificaciones, Perfil) y el
usuario no sabe dónde vive cada cosa. Además, comunidades y actividades se pintan
con la misma silueta de tarjeta, y la jerarquía padre/hija de comunidades es
invisible en pantalla.

Hallazgos que sostienen el rediseño:

1. `DashboardScreenModel` y `ActivityFeedScreenModel` leen la MISMA fuente
   (`activityRepository.getUpcomingActivities()`). El tab Actividades es un
   duplicado con menos información: no enriquece con plazas, ni con "reservado
   por mí", ni con posición en cola.
2. Lo único exclusivo del tab Actividades es el FAB de crear actividad, que ya
   está duplicado dentro de `CommunityDetailScreen` (línea 202).
3. La lista de comunidades ya anida las hijas dentro de la tarjeta del padre
   (`NestedChildRow`), pero la única señal es un icono `SubdirectoryArrowRight`
   gris de 16dp.

## Objetivo

Reducir la carga cognitiva: menos destinos, jerarquía legible de un vistazo, y
dos siluetas de tarjeta inconfundibles (comunidad vs actividad). Sin añadir
features nuevas.

## Alcance

Incluye: estructura de tabs, ubicación de Perfil, enrutado de deep links,
tratamiento visual de la lista de comunidades, icono configurable por comunidad,
y unificación de las siluetas de tarjeta.

Excluye (decisión explícita): subida de imágenes de comunidad a Storage,
historial de actividades pasadas, y cualquier reordenación del contenido del
dashboard más allá de la topbar.

## Diseño

### 1. Navegación: de 5 tabs a 3

Tabs finales: **Agora**, **Comunidades**, **Notificaciones**.

- Se eliminan `ActivitiesTab` y `ProfileTab` de `AppTabs.kt` y de la
  `AgoraNavigationBar` en `App.kt`.
- Se borran `ActivityFeedScreen.kt` y `ActivityFeedScreenModel.kt`, junto con su
  registro en el módulo de Koin y las cadenas de recursos que quedan huérfanas:
  `feed_title`, `feed_empty`, `feed_create_activity` y `feed_select_community`
  en `feature/activity/src/commonMain/composeResources/values/strings.xml` y su
  gemelo `values-es/`, más `tab_activities` y `tab_profile` en los recursos de
  `composeApp`.
- El FAB de crear actividad del feed desaparece sin sustituto: crear actividad
  vive solo dentro de la comunidad (`CommunityDetailScreen`), que es donde el
  usuario ya tiene el contexto de para quién la crea.

#### Perfil

Deja de ser tab y pasa a ser un botón de avatar en la esquina superior derecha de
la topbar de `DashboardScreen`. Al pulsarlo hace `navigator.push(ProfileScreen())`
dentro del `Navigator` de `AgoraTab`, de modo que el botón atrás vuelve a Agora.

El avatar muestra la inicial de `displayName` (ya disponible en
`DashboardScreenModel.UiState.Content`) sobre un círculo de color de la paleta.
Si `displayName` es nulo, icono genérico de usuario.

#### Deep links

Los tres flujos de deep link son fontanería: el tab solo es el sitio donde vive el
`Navigator` que monta la pantalla destino. El usuario nunca ve el tab.

| Origen | Estado en `DeepLinkHandler` | Destino antes | Destino después |
|---|---|---|---|
| Link de invitación a comunidad | `pendingInviteCode` | CommunitiesTab, `AutoJoinByInviteScreen` | sin cambios |
| Link público de actividad | `pendingActivityCode` | ActivitiesTab, `GuestActivityScreen` | **AgoraTab**, `GuestActivityScreen` |
| Push de FCM | `pendingNotificationActivityId` | ActivitiesTab, `ActivityDetailScreen` | **AgoraTab**, `ActivityDetailScreen` |

Cambios concretos: los dos `LaunchedEffect` que hoy viven en el bloque
`Navigator` de `ActivitiesTab` (`AppTabs.kt:90-110`) se mueven al `Navigator` de
`AgoraTab`, y `DeepLinkTabSwitcher` en `App.kt:204-215` pasa a conmutar a
`AgoraTab`. La lógica de reemplazo vs push del `ActivityDetailScreen` se conserva
tal cual.

### 2. Lista de comunidades: árbol indentado

Se mantiene el modelo de datos actual: `CommunityListScreenModel.buildTree()`
devuelve raíces con sus hijas directas, un solo nivel. Las hijas cuyo padre no
está en mis comunidades siguen apareciendo como raíces.

Tratamiento nuevo de `CommunityCard`:

- Fila superior: icono de la comunidad a 40dp, nombre en `titleMedium`, y
  contador de miembros debajo en `labelSmall`.
- Descripción, si existe, igual que ahora (máximo 2 líneas).
- Hijas: bloque indentado 24dp desde el borde izquierdo del contenido, con una
  línea vertical de 2dp en `outlineVariant` recorriendo todo el bloque. Cada hija
  es una fila con icono a 24dp, nombre en `bodyMedium` y contador en
  `labelSmall`. Toda la fila sigue siendo clicable hacia `CommunityDetailScreen`.
- Se elimina el icono `SubdirectoryArrowRight`.

La diferenciación origen/sub se apoya en tres señales simultáneas: tamaño de
icono (40 vs 24), escala tipográfica (`titleMedium` vs `bodyMedium`) y la línea
de conexión. Ninguna señal individual carga con todo el trabajo, lo que la hace
robusta en modo oscuro y en pantallas pequeñas.

### 3. Icono configurable por comunidad

#### Datos

Migración nueva: `ALTER TABLE public.communities ADD COLUMN icon_key text;`

Nullable, sin default. `Community` gana `@SerialName("icon_key") val iconKey: String? = null`.

Los reads directos del repositorio usan `select {}` (equivalente a `*`), así que
la columna fluye sin tocarlos. Hay dos funciones SQL que enumeran columnas a mano
y deben incluir `icon_key` para que el icono aparezca en explorar y en la
previsualización de invitación: la que construye el JSON de comunidad en
`baseline.sql:809` y la vista de comunidades públicas en `baseline.sql:2006`. Se
redefinen en la migración nueva, no se edita el baseline.

`CommunityRepository.updateCommunity(id, name, description)` gana un parámetro
`iconKey: String?`. `createCommunity` igual.

#### Pool de iconos

16 claves estables, resueltas en cliente contra `compose.materialIconsExtended`
(ya presente en `composeApp/build.gradle.kts:60` y
`feature/community/build.gradle.kts:37`, no hay que añadir dependencias ni
assets). El mapa clave a `ImageVector` vive en un único archivo en `core/ui`, de
forma que app y web comparten exactamente el mismo repertorio.

Agrupadas en cuatro familias de cuatro: deporte, social, cultura, otros. Las
claves son texto estable (por ejemplo `"volleyball"`), nunca el nombre de la
constante de Compose, para que un cambio de icono en el futuro no requiera
migrar datos.

#### Selector

Campo inline (`CommunityIconField`), no un diálogo: avatar y etiqueta en una fila
que se pliega y despliega, y debajo un `FlowRow` de 17 tiles — los 16 iconos más
uno de "sin icono". Un toque elige. Aparece en dos sitios: en
`CreateCommunityScreen` al crear, y dentro del diálogo de edición de admin en
`CommunityDetailScreen`.

Se descartó el diálogo con el que empezó el diseño por dos motivos que solo
salieron al implementarlo. En la pantalla de edición habría sido un `AlertDialog`
encima de otro, y no hay ningún precedente de diálogos apilados en la app: en
wasmJs Compose los pinta como overlays de la misma composición, no como ventanas
del sistema, y los popups anidados son terreno frágil ahí. Y la rejilla tenía que
ser un `FlowRow` y no un `LazyVerticalGrid`, porque la columna del diálogo de
edición lleva `verticalScroll` y un scrollable lazy dentro de otro scrollable
lanza excepción por constraint infinito. Con 17 tiles la laziness no aporta nada.

El icono no se guarda con `updateCommunity`, sino con un `updateCommunityIcon`
propio, siguiendo el patrón que `saveCommunity()` ya usa para visibilidad y tags:
cada grupo de campos se compara por separado y se guarda con su propia llamada.
Colarlo en `updateCommunity` con un parámetro por defecto borraba el icono al
renombrar una comunidad, y protegerlo con `iconKey?.let` rompía en silencio el
botón de "sin icono".

#### Fallback

Sin `iconKey`, se pinta la inicial del nombre sobre un color derivado
determinísticamente del `id` de la comunidad. Nunca queda un hueco vacío, y las
comunidades existentes se ven distintas entre sí desde el primer despliegue sin
que nadie configure nada.

### 4. Dos siluetas inconfundibles

Hoy comunidades y actividades comparten `MarbleCard` con la misma composición
interna, que es la causa raíz de la confusión al navegar. Regla nueva, constante
en toda la app:

**Tarjeta de comunidad**: icono cuadrado redondeado a la izquierda, nombre,
contador de miembros. Nunca muestra fecha.

**Tarjeta de actividad**: bloque de fecha a la izquierda (día en grande, mes
debajo), nombre y ubicación en el centro, badge de plazas a la derecha. Nunca
usa el icono de comunidad como elemento principal.

Se aplica en los cuatro sitios donde estas tarjetas aparecen: `DashboardScreen`
(hero y compactas), `CommunityListScreen`, `CommunityDetailScreen` (pestañas de
actividades y subcomunidades) y `ExploreCommunitiesScreen`.

Ambas siguen construidas sobre `MarbleCard` para conservar la identidad visual
del tema; lo que cambia es la composición interna, que se extrae a dos
composables reutilizables en `core/ui` para que las cuatro pantallas no vuelvan a
divergir.

## Verificación

- Compilación de los dos targets afectados: Android y wasmJs. La web comparte el
  mismo código Compose, así que no hay trabajo específico de web más allá de
  comprobar el build y una pasada visual.
- Recorrido manual de los tres deep links tras mover el enrutado a `AgoraTab`:
  link de invitación, link público de actividad y push de FCM.
- Comprobar que la migración aplica sobre la base de producción sin romper las
  dos funciones SQL redefinidas.
- Revisar que no queden referencias colgando a `ActivityFeedScreen`,
  `ActivitiesTab` ni `ProfileTab` tras el borrado.

## Riesgos

El único cambio con efecto en base de datos es la columna `icon_key` y la
redefinición de las dos funciones SQL. Es aditivo: una versión antigua de la app
ignora la columna nueva sin romperse, así que no hay problema con usuarios que
no hayan actualizado.

Mover el enrutado de deep links es el punto más delicado del cambio, porque un
fallo ahí rompe los links compartidos que ya circulan. Por eso se verifica a mano
antes de dar la fase por terminada.
