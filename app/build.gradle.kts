plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
}

// Версия приходит из CI по тегу android-vX.Y.Z (-PversionName=X.Y.Z); локальная сборка —
// 0.0.0/1, чтобы самообновление на телефоне всегда считало релиз новее dev-сборки.
val appVersionName: String = (findProperty("versionName") as String?) ?: "0.0.0"
val appVersionCode: Int =
	appVersionName.split(".").map { it.toIntOrNull() ?: 0 }.let { (major, minor, patch) ->
		major * 10_000 + minor * 100 + patch
	}.coerceAtLeast(1)

android {
	namespace = "online.aimuzov.aleshavpnov"
	compileSdk = 37

	defaultConfig {
		applicationId = "online.aimuzov.aleshavpnov"
		minSdk = 26
		targetSdk = 35
		versionCode = appVersionCode
		versionName = appVersionName
		// Ядро тянет ~30 МБ на ABI; x86_64 нужен только эмулятору — см. debug ниже.
		ndk { abiFilters += listOf("arm64-v8a") }
		// Куда ведёт «Открыть кабинет». Mini App-ссылка содержит username бота, поэтому в git
		// её нет: CI передаёт -PcabinetUrl, локальная сборка ведёт на страницу «Доступ» сайта.
		buildConfigField(
			"String",
			"CABINET_URL",
			"\"${(findProperty("cabinetUrl") as String?) ?: "https://durov.aimuzov.online/get"}\"",
		)
	}

	// Ключ приходит из окружения CI; без него debug-подпись, release — без signingConfig.
	val keystorePath = System.getenv("KEYSTORE_PATH")
	if (!keystorePath.isNullOrBlank()) {
		signingConfigs {
			create("release") {
				storeFile = file(keystorePath)
				storePassword = System.getenv("KEYSTORE_PASSWORD")
				keyAlias = System.getenv("KEY_ALIAS")
				keyPassword = System.getenv("KEY_PASSWORD")
			}
		}
	}

	buildTypes {
		debug {
			ndk { abiFilters += listOf("x86_64") }
		}
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			if (!keystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	packaging {
		// .so лежат в APK несжатыми и мапятся прямо из него: меньше места на диске.
		jniLibs.useLegacyPackaging = false
	}

	sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
	sourceSets["test"].kotlin.srcDirs("src/test/kotlin")
}

dependencies {
	// libXray.aar — из scripts/build-core.sh, в git не хранится.
	implementation(fileTree("libs") { include("*.aar") })

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.service)
	implementation(libs.androidx.datastore.preferences)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.serialization.json)

	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.material3)
	implementation(libs.compose.ui.tooling.preview)
	debugImplementation(libs.compose.ui.tooling)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
}
