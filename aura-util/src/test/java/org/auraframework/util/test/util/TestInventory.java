/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.util.test.util;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.auraframework.ds.serviceloader.AuraServiceProvider;
import org.auraframework.util.ServiceLocator;
import org.auraframework.util.test.annotation.IntegrationTest;
import org.auraframework.util.test.annotation.JSTest;
import org.auraframework.util.test.annotation.PerfCmpTest;
import org.auraframework.util.test.annotation.PerfCustomTest;
import org.auraframework.util.test.annotation.PerfFrameworkTest;
import org.auraframework.util.test.annotation.PerfTestSuite;
import org.auraframework.util.test.annotation.WebDriverTest;

import com.google.common.collect.Maps;

public class TestInventory implements AuraServiceProvider {
    public final static String TEST_CLASS_SUFFIX = "Test";
    public static final EnumSet<Type> ALL_TESTS = EnumSet.allOf(Type.class);
    public static final EnumSet<Type> PERF_TESTS = EnumSet.of(Type.PERFSUITE, Type.PERFCMP, Type.PERFFRAMEWORK, Type.PERFCUSTOM);
    public static final EnumSet<Type> FUNC_TESTS = EnumSet.of(Type.JSTEST, Type.WEBDRIVER, Type.INTEGRATION);

    public enum Type {
        JSTEST, WEBDRIVER, INTEGRATION, IGNORED, PERFSUITE, PERFCMP, PERFFRAMEWORK, PERFCUSTOM;
    }

    private URI rootUri;
    private final Map<Type, TestSuite> suites = Maps.newHashMap();
    private final Map<Type, Vector<Class<? extends Test>>> classes = Maps.newHashMap();

    public TestInventory(Class<?> classInModule) {
        suites.put(Type.IGNORED, new TestSuite());
    	rootUri = ModuleUtil.getRootUri(classInModule);
    }

    public TestSuite getTestSuite(Type type) {
        if (suites.isEmpty() || !suites.containsKey(type)) {
            loadTestSuites(type);
        }
        return suites.get(type);
    }
    public Vector<Class<? extends Test>> getTestClasses(Type type) {
        if (classes.isEmpty() || !classes.containsKey(type)) {
            loadTestClasses(type);
        }
        return classes.get(type);
    }

    public void loadTestClasses(Type type) {
        TestFilter filter = ServiceLocator.get().get(TestFilter.class);
        Vector<Class<? extends Test>> vector = new Vector<>();
        for (String className : ModuleUtil.getClassNames(rootUri)) {
            Class<? extends Test> testClass = filter.applyTo(getTestClass(className));
            if (testClass != null) {
                Type target = getAnnotationType(testClass);
                if (target == type) {
                    vector.add(testClass);
                }
            }
        }
        classes.put(type, vector);
    }

    public void loadTestSuites(Type type) {
        TestFilter filter = ServiceLocator.get().get(TestFilter.class);
        TestSuite suite = new TestSuite();
        suites.put(type, suite);

        System.out.println(String.format("Loading %s tests from %s", type, rootUri));

        for (String className : ModuleUtil.getClassNames(rootUri)) {
            Class<? extends Test> testClass = filter.applyTo(getTestClass(className));
            if (testClass != null) {
                Type target = getAnnotationType(testClass);
                if (target == type) {
                    try {
                        addTest(suite, filter, (Test) testClass.getMethod("suite").invoke(null));
                    } catch (Exception e) {}
                    try {
                        addTest(suite, filter, new TestSuite(testClass.asSubclass(TestCase.class)));
                    } catch (ClassCastException cce) {}
                }
            }
        }
    }

    private Type getAnnotationType (Class<? extends Test> testClass) {
        Type target = null;
        if (testClass.getAnnotation(PerfTestSuite.class) != null) {
            target = Type.PERFSUITE;
        } else if (testClass.getAnnotation(PerfCustomTest.class) != null) {
            target = Type.PERFCUSTOM;
        } else if (testClass.getAnnotation(PerfCmpTest.class) != null) {
            target = Type.PERFCMP;
        } else if (testClass.getAnnotation(PerfFrameworkTest.class) != null) {
            target = Type.PERFFRAMEWORK;
        } else if (testClass.getAnnotation(JSTest.class) != null) {
            target = Type.JSTEST;
        } else if (testClass.getAnnotation(WebDriverTest.class) != null) {
            target = Type.WEBDRIVER;
        } else if (testClass.getAnnotation(IntegrationTest.class) != null) {
            target = Type.INTEGRATION;
        }
        return target;
    }

    private void addTest(TestSuite suite, TestFilter filter, Test test) {
        if (test == null) {
            return;
        } else if (test instanceof TestCase) {
            if (filter == null) {
                suite.addTest(test);
            } else {
                TestCase tc = filter.applyTo((TestCase) test);
                if (tc != null) {
                    suite.addTest(test);
                }
            }
        } else if (test instanceof TestSuite) {
            TestSuite newSuite = new TestSuite(((TestSuite) test).getName());
            for (Enumeration<Test> tests = ((TestSuite) test).tests(); tests.hasMoreElements();) {
                addTest(newSuite, filter, tests.nextElement());
            }
            if (newSuite.testCount() > 0) {
                suite.addTest(newSuite);
            }
        } else if (test instanceof JUnit4TestAdapter) {
            // This is a hack because this inventory is not actually complaint
            // with the JUnit specification. All of the
            // tests in the suite will appear to the runner as a single test.
            TestSuite newSuite = new TestSuite(test.toString() + "JUnit4TestAdapterHack");
            newSuite.addTest(test);
            suite.addTest(newSuite);
        }
    }

    /**
     * Check if class might be a valid test case. Must be public, non-abstract, named "*Test" and extend from
     * {@link Test}.
     */
    private static Class<? extends Test> getTestClass(String className) {
        if (!className.endsWith(TEST_CLASS_SUFFIX)) {
            return null;
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoClassDefFoundError e) {
            return null;
        }
        int mods = clazz.getModifiers();
        if (!Modifier.isPublic(mods)) {
            return null;
        }
        if (Modifier.isAbstract(mods)) {
            return null;
        }
        Class<? extends Test> testClazz;
        try {
            testClazz = clazz.asSubclass(Test.class);
        } catch (ClassCastException e) {
            return null;
        }
        return testClazz;
    }
}
