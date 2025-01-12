@file:OptIn(ExperimentalPathApi::class)

import com.gradlets.gradle.typescript.eslint.EslintExtension
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.exists

operator fun Path.div(string: String): Path =
    resolve(string)

plugins {
    id("com.gradlets.typescript") version "1.4.1"
    id("com.gradlets.eslint")
    id("com.gradlets.webpack")
}

typeScript {
    sourceCompatibility.set("4.2.3")
    compilerOptions.putAll(
        mutableMapOf(
            "module" to "es6",
            "target" to "es5",
            "moduleResolution" to "node",
            "sourceMap" to true,
            "esModuleInterop" to true,
            "strict" to true,
            "downlevelIteration" to true,
            "lib" to listOf("dom", "es2020"),
            "jsx" to "react",
            "reactNamespace" to "JSX"
        )
    )
}

eslint {
    inheritedConfig.add("plugin:prettier/recommended")
    rules.put(
        "prettier/prettier", mutableListOf(
            "warn",
            mapOf(
                "printWidth" to 120,
                "tabWidth" to 4,
                "trailingComma" to "all",
                "arrowParens" to "avoid"
            )
        )
    )
}

webpack {
    outputDir.set(
        (buildDir.toPath() / "webpack" / "web" / "js").toFile()
    )

    config("webpack.config.js")
}

repositories {
    withGroovyBuilder {
        "npm" {
            "url"("https://registry.npmjs.org")
        }
    }
}

dependencies {
    //ESLint
    add("eslint", "npm:eslint-config-prettier:7.2.0")
    add("eslint", "npm:eslint-plugin-prettier:3.3.1")
    add("eslint", "npm:prettier:2.2.1")
    add("eslint", "npm:inherits:2.0.4")
    add("eslint", "npm:wrappy:1.0.2")

    //Webpack
    add("webpack", "npm:source-map-loader:4.0.0")
    add("webpack", "npm:webpack:5.74.0")
    add("webpack", "npm:webpack-cli:5.1.4") {
        exclude("npm:commander")
    }

    add("webpack", "npm:types/eslint:8.4.5")
    add("webpack", "npm:types/node:18.6.2")

    deps("npm:leaflet:1.9.4")
    types("npm:types/leaflet:1.9.12") {
        exclude("npm:types/geojson:*")
    }

    types("npm:types/geojson:7946.0.14")

    deps("npm:jquery:3.7.1")
    types("npm:types/jquery:3.5.30") {
        exclude("npm:types/sizzle:*")
    }

    types("npm:types/sizzle:2.3.8")

    deps("npm:sequency:0.20.0")

    deps("npm:tinycolor2:1.6.0")
    types("npm:types/tinycolor2:1.4.6")

    deps("npm:typed-duration:2.0.0")

    deps("npm:humanize-duration:3.32.1")
    types("npm:types/humanize-duration:3.27.4")
}