

val slf4kVersion = libs.versions.slf4k.get()
val kotlinVersion = libs.versions.kotlin.get()

allprojects {
    group = "top.wang3.slf4k"
    version = "$kotlinVersion-$slf4kVersion"
}