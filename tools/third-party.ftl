<#-- Plantilla de THIRD_PARTY_LICENSES.md para license-maven-plugin. -->
# Licencias de terceros

Dependencias de ejecución de Rora Studio. Archivo generado: `./mvnw -B -DskipTests install && ./mvnw -B license:aggregate-add-third-party@third-party`.

| Dependencia | Versión | Licencia |
|---|---|---|
<#list dependencyMap as e>
| ${e.getKey().groupId}:${e.getKey().artifactId} | ${e.getKey().version} | <#list e.getValue() as l>${l}<#sep>, </#sep></#list> |
</#list>
