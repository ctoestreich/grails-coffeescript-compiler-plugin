package coffeescript.compiler

import org.grails.plugins.coffee.compiler.CoffeeCompilerManager
import org.grails.plugins.coffee.compiler.processor.CoffeeScriptProcessor
import org.junit.Before
import org.junit.Ignore

@Ignore('these should only be run if node is installed')
class PluginTestWithNodeTests extends PluginTestBase {

    @Before
    void setUp() {
        CoffeeScriptProcessor.forceNode = true
        compilerManager = new CoffeeCompilerManager()
    }

    Boolean shouldIgnore() {
        CoffeeScriptProcessor.forceNode && !CoffeeScriptProcessor.isNodeProcessor
    }
}
