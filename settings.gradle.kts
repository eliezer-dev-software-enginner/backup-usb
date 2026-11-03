rootProject.name = "backup-usb"
if (providers.gradleProperty("useLocalSources").orNull != "false") {
    includeBuild("../../framework/libraries/megalodonte-components")
    includeBuild("../../framework/libraries/megalodonte-theme")
}
