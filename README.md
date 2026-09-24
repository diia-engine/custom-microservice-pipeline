# Custom microservice Build and Deploy Pipeline

## Призначення

Jenkins Shared Library для збирання та розгортання Java-застосунків.

## Передумови: Shared Library

Щоб pipeline був доступний для виконання, у репозиторії
`registry-regulations-publication-pipeline` мають бути додані всі необхідні
файли Shared Library.

Структура файлів:

```text
registry-regulations-publication-pipeline/
├── vars/
│   └── BuildJavaApplication.groovy
│
└── src/com/epam/digital/data/platform/pipelines/stages/impl/buildJavaApplication/
    ├── BuildImage.groovy
    ├── Checkout.groovy
    ├── ConfigureOpenShiftBuild.groovy
    ├── Deploy.groovy
    ├── DetectBuildConfiguration.groovy
    ├── LocalBuildContext.groovy
    └── MavenBuild.groovy
```

`BuildJavaApplication.groovy` є точкою входу pipeline.

Stage-класи знаходяться в `buildJavaApplication` та використовуються через
існуючий `StageFactory`.

## Підключення pipeline до Jenkins

Pipeline не створюється автоматично. Для його використання необхідно вручну
створити в Jenkins параметризований Pipeline job.

При створенні job необхідно увімкнути **This project is parameterized** та
додати такі параметри:

| Параметр | Значення за замовчуванням | Опис |
| --- | --- | --- |
| `REPOSITORY_NAME` | `ext-info-service` | Назва репозиторію в Gerrit |
| `GIT_BRANCH` | `master` | Git-гілка, яку потрібно зібрати |
| `LOG_LEVEL` | `INFO` | Рівень логування: `ERROR`, `WARN`, `INFO` або `DEBUG` |
| `HELM_VALUES` | — | Кастомні Helm values у форматі JSON |

Ці параметри **обов'язково мають бути налаштовані в Jenkins job**, оскільки
pipeline отримує їх через Jenkins build parameters.

В якості Pipeline script необхідно вказати:

```groovy
@Library(['edp-library-stages', 'edp-library-pipelines']) _

BuildJavaApplication()
```

### `HELM_VALUES`

`HELM_VALUES` використовується для передачі додаткових параметрів у Helm.

Значення параметра має бути JSON з flat map ключів:

```json
{
  "keycloak.host": "platform-keycloak.apps.example.gov.ua",
  "image.version": "2.0.1",
  "global.container.requestsLimitsEnabled": "true"
}
```

Для вкладених Helm values використовуйте крапку в назві ключа. Не передавайте
вкладені JSON-об'єкти.

Правильно:

```json
{
  "keycloak.host": "platform-keycloak.apps.example.gov.ua",
  "global.container.requestsLimitsEnabled": "true"
}
```

Неправильно:

```json
{
  "keycloak": {
    "host": "platform-keycloak.apps.example.gov.ua"
  }
}
```

## Запуск pipeline

Після створення job та налаштування параметрів pipeline можна запускати через
**Build with Parameters**.

Під час виконання pipeline проходить такі етапи:

1. **Init** — створює та ініціалізує `LocalBuildContext`, отримує конфігурацію
   платформи, Docker Registry, Keycloak, Gerrit та Stage Factory.
2. **Checkout** — очищає workspace, checkout-ить потрібну гілку Gerrit та зберігає
   вихідний код у stash `source`.
3. **Detect build configuration** — читає `pom.xml` та визначає `artifactId`,
   версію проєкту, Java version, BuildConfig та параметри image.
4. **Maven build** — запускає Maven у тимчасовому Kubernetes pod з відповідним
   Java/Maven image та створює необхідні stash-и.
5. **Configure OpenShift build** — створює або оновлює OpenShift BuildConfig для
   Binary Build та налаштовує публікацію image у Nexus.
6. **Build image** — запускає OpenShift Binary Build, який створює Docker image
   з `Dockerfile` та JAR і публікує його в Nexus.
7. **Deploy** — запускає Helm upgrade/install для застосунку.

## Визначення Java version

Етап `detect-build-configuration` визначає Java version з `pom.xml` у такому
порядку:

1. `java.version`;
2. `maven.compiler.release`;
3. `maven.compiler.source`.

Java version є обов'язковою. Якщо version не вказана або не підтримується,
pipeline завершується до початку збірки.

Підтримуються:

| Java version | Maven image |
| --- | --- |
| `8` | `maven:3.9-eclipse-temurin-8` |
| `11` | `maven:3.9-eclipse-temurin-11` |
| `17` | `maven:3.9-eclipse-temurin-17` |
| `21` | `maven:3.9-eclipse-temurin-21` |

Maven запускається у відповідному тимчасовому Kubernetes pod. Таким чином,
збірка використовує Java version, оголошену самим проєктом, а не Java version,
встановлену на Jenkins master.

## Розгортання через Helm

На етапі **Deploy** pipeline викликає:

```groovy
Helm.upgrade(
    context,
    context.projectArtifactId,
    'deploy-templates',
    parametersMap,
    '',
    context.namespace,
    true
)
```

Перед викликом `Helm.upgrade` формується мапа стандартних параметрів:

| Helm key | Значення |
| --- | --- |
| `namespace` | namespace поточної збірки (`context.namespace`) |
| `cdPipelineStageName` | `main` |
| `dnsWildcard` | wildcard домену OpenShift (`context.dnsWildcard`) |
| `image.name` | `${context.dockerRegistry.host}/${context.namespace}/${context.projectArtifactId}` |
| `image.version` | версія проєкту (`context.projectVersion`) |
| `nexusPullSecret` | `context.dockerRegistry.PUSH_SECRET` |
| `keycloak.url` | `${context.keycloak.url}/auth` |

Після формування стандартних параметрів до мапи додаються значення з
`HELM_VALUES`:

```groovy
parametersMap.putAll(context.helmValues)
```

Таким чином, значення з `HELM_VALUES` передаються в `Helm.upgrade` разом зі
стандартними параметрами та можуть використовуватися для додаткового
налаштування `deploy-templates`.

### Перезапис стандартних параметрів

Мапа `HELM_VALUES` додається **після** стандартних параметрів. Тому параметр з таким
самим ключем перезапише стандартне значення pipeline.

Не рекомендується перезаписувати системні параметри, зокрема:

- `namespace`
- `image.name`
- `image.version`
- `nexusPullSecret`
- `dnsWildcard`

### Приклад

Для застосунку `ext-info-service` стандартні параметри можуть мати вигляд:

```text
namespace=ck-198
cdPipelineStageName=main
dnsWildcard=apps.krrt-two.ncr.gov.ua
image.name=<nexus-host>/ck-198/ext-info-service
image.version=2.0.0
nexusPullSecret=nexus-docker-registry-namespaced
keycloak.url=https://platform-keycloak.apps.krrt-two.ncr.gov.ua/auth
```

Користувацькі значення:

```json
{
  "replicas": 2,
  "global.container.requestsLimitsEnabled": "true"
}
```

додаються до цієї карти перед передачею її в `Helm.upgrade`.
