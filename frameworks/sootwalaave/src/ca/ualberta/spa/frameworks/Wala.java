package ca.ualberta.spa.frameworks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.BasicCallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.Atom;

public class Wala {
	public static void main(String[] args) throws IOException, IllegalArgumentException, InvalidClassFileException,
			ClassHierarchyException, CallGraphBuilderCancelException {
		long start = System.nanoTime();
		String bin = Paths.get("bin").toAbsolutePath().toString();
		String jreVersion = "1.6.0_45";
		String jre = Files.list(Paths.get("jre", jreVersion)).map(p -> p.toAbsolutePath().toString())
				.collect(Collectors.joining(File.pathSeparator));
		String app = Paths.get("hello", "averroes", "organized-app.jar").toAbsolutePath().toString();
		String ave = Paths.get("hello", "averroes", "averroes-lib-class.jar").toAbsolutePath().toString();
		String placeholder = Paths.get("hello", "averroes", "placeholder-lib.jar").toAbsolutePath().toString();

		String mainClass = "Lca/ualberta/spa/frameworks/examples/HelloWorld";
		boolean isAverroes = true;

		// 1. Set the classpath
		String classpath = bin + File.pathSeparator + jre;

		// 2. Set the exclusion file (similar to Soot's -exclude)
		String exclusionFile = Wala.class.getClassLoader().getResource(CallGraphTestUtil.REGRESSION_EXCLUSIONS)
				.getPath();

		// 3. Set the analysis scope => hierarchy
		AnalysisScope scope = isAverroes ? makeAverroesAnalysisScope(ave, placeholder, app)
				: AnalysisScopeReader.makeJavaBinaryAnalysisScope(classpath, new File(exclusionFile));

		IClassHierarchy cha = ClassHierarchyFactory.make(scope);

		// 4. Program entry points
		Iterable<Entrypoint> entrypoints = makeMainEntrypoints(scope.getApplicationLoader(), cha,
				new String[] { mainClass }, isAverroes);

		// 5. Misc. analysis options
		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
		options.setReflectionOptions(
				isAverroes ? ReflectionOptions.NONE : ReflectionOptions.MULTI_FLOW_TO_CASTS_APPLICATION_GET_METHOD);
		options.setHandleZeroLengthArray(isAverroes ? false : true);

		// 6. Callgraph builder
		SSAPropagationCallGraphBuilder builder = isAverroes
				? makeZeroOneCFABuilder(options, new AnalysisCacheImpl(), cha, scope, null, null)
				: Util.makeZeroOneCFABuilder(options, new AnalysisCacheImpl(), cha, scope, null, null);

		long end = System.nanoTime();
		BasicCallGraph<?> cg = (BasicCallGraph<?>) builder.makeCallGraph(options, null);
		System.out.println("[Wala] Solution found in " + ((end - start) / 1000 / 1000 / 1000) + " seconds.");

		// 7. Dump the results
		dumpCG(scope.getApplicationLoader(), builder.getPointerAnalysis(), cg, mainClass);
	}

	private static SSAPropagationCallGraphBuilder makeZeroOneCFABuilder(AnalysisOptions options, AnalysisCache cache,
			IClassHierarchy cha, AnalysisScope scope, ContextSelector customSelector,
			SSAContextInterpreter customInterpreter) {

		if (options == null) {
			throw new IllegalArgumentException("options is null");
		}
		Util.addDefaultSelectors(options, cha);

		return ZeroXCFABuilder.make(cha, options, cache, customSelector, customInterpreter,
				ZeroXInstanceKeys.ALLOCATIONS | ZeroXInstanceKeys.SMUSH_MANY | ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS
						| ZeroXInstanceKeys.SMUSH_STRINGS | ZeroXInstanceKeys.SMUSH_THROWABLES);
	}

	private static AnalysisScope makeAverroesAnalysisScope(String ave, String placeholder, String app)
			throws IOException, IllegalArgumentException, InvalidClassFileException {
		AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

		// There should be no exclusions when using Averroes
		// scope.setExclusions(new FileOfClasses(fs));

		// Library stuff
		scope.addToScope(ClassLoaderReference.Application, new JarFileModule(new JarFile(ave)));
		scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(new JarFile(placeholder)));

		// Application JAR
		scope.addToScope(ClassLoaderReference.Application, new JarFileModule(new JarFile(app)));

		return scope;
	}

	private static Iterable<Entrypoint> makeMainEntrypoints(final ClassLoaderReference loaderRef,
			final IClassHierarchy cha, final String[] classNames, boolean isAve)
					throws IllegalArgumentException, IllegalArgumentException, IllegalArgumentException {
		return new Iterable<Entrypoint>() {
			@Override
			public Iterator<Entrypoint> iterator() {
				final Atom mainMethod = Atom.findOrCreateAsciiAtom("main");

				return new Iterator<Entrypoint>() {
					private int index = 0;
					private boolean clinitTaken = false;

					@Override
					public void remove() {
						Assertions.UNREACHABLE();
					}

					@Override
					public boolean hasNext() {
						return index < classNames.length || (isAve && !clinitTaken);
					}

					@Override
					public Entrypoint next() {
						if (index < classNames.length) {
							TypeReference T = TypeReference.findOrCreate(loaderRef,
									TypeName.string2TypeName(classNames[index++]));
							MethodReference mainRef = MethodReference.findOrCreate(T, mainMethod,
									Descriptor.findOrCreateUTF8("([Ljava/lang/String;)V"));
							return new DefaultEntrypoint(mainRef, cha);
						} else if (isAve && !clinitTaken) {
							clinitTaken = true;
							TypeReference T = TypeReference.findOrCreate(loaderRef,
									TypeName.string2TypeName("Laverroes/Library"));
							MethodReference clinitRef = MethodReference.findOrCreate(T, MethodReference.clinitName,
									MethodReference.clinitSelector.getDescriptor());
							return new DefaultEntrypoint(clinitRef, cha);
						} else {
							throw new IllegalStateException("No more entry points. This should never happen!");
						}
					}
				};
			}
		};
	}

	private static void dumpCG(ClassLoaderReference loaderRef, PointerAnalysis<InstanceKey> PA,
			com.ibm.wala.ipa.callgraph.CallGraph CG, String mainClass) {
		TypeReference T = TypeReference.findOrCreate(loaderRef, TypeName.string2TypeName(mainClass));
		Atom mainMethod = Atom.findOrCreateAsciiAtom("main");
		MethodReference M = MethodReference.findOrCreate(T, mainMethod,
				Descriptor.findOrCreateUTF8("([Ljava/lang/String;)V"));
		CGNode N = CG.getNodes(M).iterator().next();
		System.err.print("callees of node " + getShortName(N) + " : [");
		boolean fst = true;
		for (Iterator<? extends CGNode> ns = CG.getSuccNodes(N); ns.hasNext();) {
			if (fst)
				fst = false;
			else
				System.err.print(", ");
			System.err.print(getShortName(ns.next()));
		}
		System.err.println("]");
		System.err.println("\nIR of node " + N.getGraphNodeId() + ", context " + N.getContext());
		IR ir = N.getIR();
		if (ir != null) {
			System.err.println(ir);
		} else {
			System.err.println("no IR!");
		}

		System.err.println("pointer analysis");
		System.err.println(PA.getHeapModel().getPointerKeyForLocal(N, 3) + " -->");
		PA.getPointsToSet(PA.getHeapModel().getPointerKeyForLocal(N, 3))
				.forEach(p -> System.out.println(p + " :: " + p.getClass()));
		System.err.println(PA.getHeapModel().getPointerKeyForLocal(N, 4) + " -->");
		PA.getPointsToSet(PA.getHeapModel().getPointerKeyForLocal(N, 4)).forEach(System.out::println);
	}

	private static String getShortName(CGNode nd) {
		IMethod method = nd.getMethod();
		return getShortName(method);
	}

	private static String getShortName(IMethod method) {
		String origName = method.getName().toString();
		String result = origName;
		if (origName.equals("do") || origName.equals("ctor")) {
			result = method.getDeclaringClass().getName().toString();
			result = result.substring(result.lastIndexOf('/') + 1);
			if (origName.equals("ctor")) {
				if (result.equals("LFunction")) {
					String s = method.toString();
					if (s.indexOf('(') != -1) {
						String functionName = s.substring(s.indexOf('(') + 1, s.indexOf(')'));
						functionName = functionName.substring(functionName.lastIndexOf('/') + 1);
						result += " " + functionName;
					}
				}
				result = "ctor of " + result;
			}
		}
		return result;
	}
}