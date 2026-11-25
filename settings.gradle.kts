rootProject.name = "backup-usb"
if (providers.gradleProperty("useLocalSources").orNull != "false") {
    includeBuild("../../framework/libraries/megalodonte-components-desktop")
    includeBuild("../../framework/libraries/megalodonte-theme")
}
