import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.kotlin.dsl.getByName
import kotlin.jvm.optionals.getOrDefault

fun <T : Any> fromAny(vararg catalogs: VersionCatalog, get: VersionCatalog.() -> T?): T {
    for (catalog in catalogs) {
        when (val value = catalog.get()) {
            null -> continue
            else -> return value
        }
    }

    error("couldn't find required data in any provided version catalog: ${catalogs.map { it.name }}")
}

fun ExtensionContainer.catalog(name: String): VersionCatalog = getByName<VersionCatalogsExtension>("versionCatalogs").named(name)

fun VersionCatalog.getIntVersionOrNull(alias: String): Int? = findVersion(alias).map { it.toString().toIntOrNull() }.getOrDefault(null)
fun VersionCatalog.getStringVersionOrNull(alias: String): String? = findVersion(alias).map { it.toString() }.getOrDefault(null)
