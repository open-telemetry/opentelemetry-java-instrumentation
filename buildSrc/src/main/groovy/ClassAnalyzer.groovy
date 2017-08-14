import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

import java.util.jar.JarEntry
import java.util.jar.JarFile

class ClassAnalyzer {
  static def findMethodNames(JarFile jar, JarEntry entry) {
    def stream = jar.getInputStream(entry)

    def classNode = new ClassNode()
    def cr = new ClassReader(stream)
    cr.accept(classNode, 0)

    return classNode.methods.collect { it.name }
  }
}
