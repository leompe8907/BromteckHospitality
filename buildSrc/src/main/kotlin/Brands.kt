import groovy.json.JsonSlurper
import java.io.File

/**
 * Marca blanca: un flavor por carpeta en brands/. Cada carpeta trae
 *   assets/brand.json      -> id, applicationId, displayName, flags (lo lee también la app)
 *   assets/hotel_data.json -> contenido "acerca del hotel"
 *   res/                   -> íconos, banner, logo (sobrescriben los de src/main/res)
 * Agregar una marca = copiar una carpeta. Los build.gradle.kts de las apps no se tocan.
 *
 * Lo usan app/tv, app/mobile y app/staff: así las tres apps tienen siempre las mismas marcas.
 */
data class Brand(
    val dir: File,
    val id: String,
    val applicationId: String,
    val displayName: String,
    /** applicationId de la app del celular: "mobileApplicationId" o, si falta, el de la TV + ".mobile". */
    val mobileApplicationId: String,
    /** applicationId de la app del personal: "staffApplicationId" o, si falta, el de la TV + ".staff". */
    val staffApplicationId: String,
)

fun loadBrands(brandsDir: File): List<Brand> = brandsDir.listFiles { f -> f.isDirectory }.orEmpty()
    .sortedBy { it.name }
    .map { dir ->
        val config = JsonSlurper().parse(File(dir, "assets/brand.json")) as Map<*, *>
        val id = config["id"] as String
        require(id == dir.name) { "brands/${dir.name}: el id de brand.json ('$id') debe ser igual al nombre de la carpeta" }
        val appId = config["applicationId"] as String
        Brand(
            dir = dir,
            id = id,
            applicationId = appId,
            displayName = config["displayName"] as String,
            mobileApplicationId = (config["mobileApplicationId"] as String?) ?: "$appId.mobile",
            staffApplicationId = (config["staffApplicationId"] as String?) ?: "$appId.staff",
        )
    }
