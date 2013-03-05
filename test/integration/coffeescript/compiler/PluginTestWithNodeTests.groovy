package coffeescript.compiler

import org.grails.plugins.coffee.compiler.CoffeeCompilerManager
import org.grails.plugins.coffee.compiler.processor.CoffeeScriptProcessor
import org.junit.After
import org.junit.Before
import org.junit.Test
import ro.isdc.wro.WroRuntimeException

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import org.junit.Ignore

@Ignore('these should only be run if node is installed')
class PluginTestWithNodeTests extends PluginTestBase
{
    @Before
    void setUp() {
		CoffeeScriptProcessor.forceNode = true
        compilerManager = new CoffeeCompilerManager()
    }

    Boolean shouldIgnore(){
        CoffeeScriptProcessor.forceNode && !CoffeeScriptProcessor.isNodeProcessor
    }
}
