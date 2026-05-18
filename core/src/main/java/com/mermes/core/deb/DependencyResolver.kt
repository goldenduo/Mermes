package com.mermes.core.deb

/**
 * Dependency resolver for deb packages
 */
internal object DependencyResolver {

    /**
     * Resolve installation order using topological sort (leaf nodes first)
     *
     * @param packages List of package control info
     * @return Ordered list of package names (dependencies first)
     * @throws CircularDependencyException if circular dependency detected
     */
    fun resolveInstallationOrder(packages: List<DebControl>): List<String> {
        // Build adjacency list (package -> list of packages that depend on it)
        val dependents = mutableMapOf<String, MutableSet<String>>()
        val inDegree = mutableMapOf<String, Int>()
        val allPackages = mutableSetOf<String>()
        val packageMap = mutableMapOf<String, DebControl>()

        // Initialize
        packages.forEach { pkg ->
            allPackages.add(pkg.packageName)
            packageMap[pkg.packageName] = pkg
            dependents.getOrPut(pkg.packageName) { mutableSetOf() }
            inDegree.putIfAbsent(pkg.packageName, 0)
        }

        // Build graph: if A depends on B, then B -> A (B must be installed before A)
        packages.forEach { pkg ->
            val allDeps = pkg.depends + pkg.preDepends
            allDeps.forEach { dep ->
                val depName = parsePackageName(dep)
                if (depName in allPackages) {
                    // depName must be installed before pkg.packageName
                    dependents.getOrPut(depName) { mutableSetOf() }.add(pkg.packageName)
                    inDegree[pkg.packageName] = (inDegree[pkg.packageName] ?: 0) + 1
                }
            }
        }

        // Kahn's algorithm for topological sort
        val queue = ArrayDeque<String>()
        val result = mutableListOf<String>()

        // Start with nodes that have no dependencies (in-degree 0)
        inDegree.forEach { (name, degree) ->
            if (degree == 0) {
                queue.add(name)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)

            // For each package that depends on current
            dependents[current]?.forEach { dependent ->
                inDegree[dependent] = (inDegree[dependent] ?: 0) - 1
                if (inDegree[dependent] == 0) {
                    queue.add(dependent)
                }
            }
        }

        // Check for circular dependencies
        if (result.size != allPackages.size) {
            val remaining = allPackages - result.toSet()
            val cycle = findCycle(remaining, packages)
            throw CircularDependencyException(
                cycle = cycle,
                message = "Circular dependency detected: ${cycle.joinToString(" -> ")}"
            )
        }

        return result
    }

    /**
     * Detect circular dependencies
     *
     * @param packages List of package control info
     * @return List of cycles found (empty if no cycles)
     */
    fun detectCircularDependencies(packages: List<DebControl>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val allPackages = mutableSetOf<String>()
        val packageMap = mutableMapOf<String, DebControl>()

        packages.forEach { pkg ->
            allPackages.add(pkg.packageName)
            packageMap[pkg.packageName] = pkg
        }

        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val parent = mutableMapOf<String, String?>()

        fun dfs(node: String): Boolean {
            visited.add(node)
            recursionStack.add(node)

            val pkg = packageMap[node] ?: return false
            val allDeps = pkg.depends + pkg.preDepends

            for (dep in allDeps) {
                val depName = parsePackageName(dep)
                if (depName !in allPackages) continue

                if (depName !in visited) {
                    parent[depName] = node
                    if (dfs(depName)) return true
                } else if (depName in recursionStack) {
                    // Found cycle, reconstruct it
                    val cycle = mutableListOf(depName)
                    var current = node
                    while (current != depName) {
                        cycle.add(0, current)
                        current = parent[current] ?: break
                    }
                    cycle.add(0, depName)
                    cycles.add(cycle)
                    return true
                }
            }

            recursionStack.remove(node)
            return false
        }

        allPackages.forEach { pkg ->
            if (pkg !in visited) {
                parent[pkg] = null
                dfs(pkg)
            }
        }

        return cycles
    }

    /**
     * Parse package name from dependency string (e.g., "libc (>= 2.28)" -> "libc")
     */
    private fun parsePackageName(dep: String): String {
        return dep.trim().split("\\s+".toRegex()).first().trim()
    }

    /**
     * Find a cycle in the remaining packages
     */
    private fun findCycle(remaining: Set<String>, packages: List<DebControl>): List<String> {
        val packageMap = packages.associateBy { it.packageName }

        // Simple DFS to find cycle
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): Boolean {
            if (node in path) {
                return true
            }
            if (node in visited) return false

            visited.add(node)
            path.add(node)

            val pkg = packageMap[node] ?: return false
            val allDeps = pkg.depends + pkg.preDepends

            for (dep in allDeps) {
                val depName = parsePackageName(dep)
                if (depName in remaining) {
                    if (dfs(depName)) return true
                }
            }

            path.removeLast()
            return false
        }

        remaining.firstOrNull()?.let { dfs(it) }

        return path.ifEmpty { remaining.toList() }
    }
}

/**
 * Circular dependency exception
 */
class CircularDependencyException(
    val cycle: List<String>,
    message: String
) : Exception(message)
