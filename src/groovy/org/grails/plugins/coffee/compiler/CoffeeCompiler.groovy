package org.grails.plugins.coffee.compiler

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.grails.plugins.coffee.compiler.processor.CoffeeScriptProcessor
import org.grails.plugins.coffee.compiler.processor.NodeCoffeeScriptProcessor
import org.grails.plugins.coffee.compiler.processor.RhinoCoffeeScriptProcessor
import ro.isdc.wro.WroRuntimeException
import ro.isdc.wro.config.Context
import ro.isdc.wro.config.jmx.WroConfiguration
import ro.isdc.wro.extensions.processor.js.UglifyJsProcessor
import ro.isdc.wro.extensions.processor.support.ObjectPoolHelper
import ro.isdc.wro.extensions.processor.support.coffeescript.CoffeeScript
import ro.isdc.wro.model.group.processor.Injector
import ro.isdc.wro.model.group.processor.InjectorBuilder
import ro.isdc.wro.model.resource.Resource
import ro.isdc.wro.model.resource.ResourceType
import ro.isdc.wro.util.ObjectFactory

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CoffeeCompiler {

    private static final Log log = LogFactory.getLog(CoffeeCompiler.class);
    private static final String OPTION_COMPILE = "-cp";
    private static final String SHELL_COMMAND = "coffee";
    public static final String ALIAS = "nodeCoffeeScript";

    // Default values for CoffeeScript source and JavaScript output paths
    String coffeeSourcePath = "src/coffee"
    String jsOutputPath = "web-app/js/app"
    Long minutesToWaitForComplete = 3
    Integer threadPoolSize = 10

    CoffeeCompiler(String configCoffeeSourcePath, String configJsOutputPath) {
        if(configCoffeeSourcePath)
            coffeeSourcePath = configCoffeeSourcePath
        if(configJsOutputPath)
            jsOutputPath = configJsOutputPath
    }

    def compileFile(File file, Boolean minifyJS = false, Boolean wrapJS = true, Boolean overrideJS = true) {
        if(!file)
            return

        def rawContent = []
        rawContent << file.getText()
        def content = rawContent.join(System.getProperty("line.separator"))

        String outputFileName = file.path.replace('\\', '/').replace(coffeeSourcePath, jsOutputPath).replace(".coffee", ".js")

        File outputFile = new File(outputFileName)

        //move on if flag is not set to override AND js is newer
        if(!overrideJS && isJavascriptNewerThanCoffee(outputFile, file))
            return

        new File(outputFile.parent).mkdirs()

        Resource resource = Resource.create(file.path, ResourceType.JS);
        Reader reader = file.newReader();
        Writer writer = outputFile.newWriter();

        try {
            /*
            note: this is needed when Node is used by wro4j and we wish to "override" the bare/noWrap options.
            The default processor is Rhino in cases where it can't find node so we have to account for both... and luck
            has it that they deal with compiler options in a COMPLETELY different way.  *yeah*
            */
            Context.set(Context.standaloneContext(), new WroConfiguration());
            Injector injector = new InjectorBuilder().build();
            CoffeeScriptProcessor coffee = new CoffeeScriptProcessor(wrapJS)
            injector.inject(coffee);
            coffee.process(resource, reader, writer);

            if(minifyJS) {
                minify(outputFile)
                log.debug "Compiling and minifying ${file.path} to ${outputFile.path}"
            } else {
                log.debug "Compiling ${file.path} to ${outputFile.path}"
            }

        } catch(WroRuntimeException wroRuntimeException) {
            outputFile.delete()
            log.error " "
            log.error "${wroRuntimeException.message} in ${file.path}"
            log.error " "
            throw wroRuntimeException
        } catch(NullPointerException npe) {
            outputFile.delete()
            log.error " "
            log.error "${npe.message} in ${file.path}"
            log.error " "
            throw new WroRuntimeException(npe.getMessage(), npe)
        } catch(Exception e) {
            outputFile.delete()
            log.error " "
            log.error "${e.message} in ${file.path}"
            log.error " "
            throw new WroRuntimeException(e.getMessage(), e)
        } finally {
            reader.close()
            writer.close()
        }
    }

/**
 * Test of the JavaScript file was modified since last coffee compile.  This is a bad
 * situation to get into, but sometimes on rare occasions debugging is quicker by modifying
 * the js directly.  Arguably people should not do this, but ho-hum they do.
 * @param outputFile The javascript file
 * @param sourceFile The coffee file
 * @return true if javascript file is newer than coffee file
 */
    Boolean isJavascriptNewerThanCoffee(File outputFile, File sourceFile) {
        Boolean isJavascriptNewer = (outputFile.exists() && outputFile.lastModified() > sourceFile.lastModified())
        if(isJavascriptNewer) {
            String message = "JavaScript file ${outputFile.absolutePath} is newer than ${sourceFile.absolutePath}, skipping compile."
            //output message to stdout
            println message
            log.debug message
        }
        return isJavascriptNewer
    }

    def compileAll(Boolean minifyJS = false, Boolean purgeJS = true, Boolean wrapJS = true, Boolean overrideJS = true) {
        if(purgeJS) {
            log.debug "Purging ${jsOutputPath}..."
            new File(jsOutputPath).deleteDir()
        }
        new File(jsOutputPath).mkdirs()
        def coffeeSource = new File(coffeeSourcePath)

        def pool = Executors.newFixedThreadPool(threadPoolSize)
        def defer = { c -> pool.submit(c as Callable) }

        def eachFileHandler = { File file ->
            if(file.isDirectory()) {
                return
            }

            if(file.path.contains(".coffee")) {
                defer { compileFile(file, minifyJS, wrapJS, overrideJS) }
            }
        }

        def ignoreHidden = { File file ->
            if(file.isHidden()) {
                return false;
            }
            return true;
        }

        eachFileRecurse(coffeeSource, eachFileHandler, ignoreHidden)

        pool.shutdown()
        pool.awaitTermination(minutesToWaitForComplete, TimeUnit.MINUTES)

    }

    def eachFileRecurse(File dir, Closure closure, Closure filter = { return true }) {
        for(file in dir.listFiles()) {
            if(filter.call(file)) {
                if(file.isDirectory()) {
                    eachFileRecurse(file, closure, filter);
                }
                else {
                    closure.call(file);
                }
            }
        }
    }

    def minify(File inputFile) {
        File targetFile = new File(inputFile.path)
        inputFile.renameTo(new File(inputFile.path.replace(".js", ".tmp")))
        inputFile = new File(inputFile.path.replace(".js", ".tmp"))

        try {
            Resource resource = Resource.create(inputFile.path, ResourceType.JS);
            Reader reader = new FileReader(inputFile.path);
            Writer writer = new FileWriter(targetFile.path);
            new UglifyJsProcessor().process(resource, reader, writer);
            inputFile.delete()
        }
        catch(Exception e) {
            inputFile.renameTo(new File(inputFile.path.replace(".tmp", ".js")))
            throw e
        }
    }

}
