/*
val Project.kotlin: KotlinMultiplatformExtension

tasks {
    register("sourceSetDependencies") {
        group = "help"
        doLast {
            println("---\ntitle: Source Set Dependencies\n---\nclassDiagram")
            val dependersDependees = HashMap<KotlinSourceSet, HashSet<KotlinSourceSet>>()
            for (target in kotlin.targets.sortedBy { it.name }) {
                for (compilation in target.compilations) {
                    if (target.platformType.name != "common") {
                        println("  class ${compilation.defaultSourceSet.name}")
                        println(
                            "  ${compilation.defaultSourceSet.name}" +
                                " : ${target.platformType.name}[${compilation.name}] target"
                        )
                    }
                    for (dependeeSourceSet in compilation.allKotlinSourceSets.sortedBy { it.name }) {
                        for (dependerSourceSet in dependeeSourceSet.dependsOn.sortedBy { it.name }) {
                            if (dependersDependees[dependerSourceSet]?.contains(dependeeSourceSet) != true) {
                                dependersDependees.computeIfAbsent(dependerSourceSet) { HashSet() }
                                    .add(dependeeSourceSet)
                                println("    ${dependerSourceSet.name} <|-- ${dependeeSourceSet.name}")
                            }
                        }
                    }
                }
            }
        }
    }
}
*/
