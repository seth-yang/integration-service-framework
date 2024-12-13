package org.dreamwork.integration.context;

import org.dreamwork.integration.api.annotation.AModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;

/**
 * 将类/包扫描的工具函数分离出来
 *
 * @since 1.1.0
 */
public class ClassScannerHelper {
    public static void fillPackageNames (ClassLoader loader, String base, Set<String> list) throws IOException {
        String path = base.replace ('.', '/');
        try (InputStream in = loader.getResourceAsStream (path)) {
            if (in != null) {
                BufferedReader reader = new BufferedReader (new InputStreamReader (in));
                String line;
                while ((line = reader.readLine ()) != null) {
                    if (!line.contains (".")) {
                        list.add (base + "." + line);

                        fillPackageNames (loader, base + "." + line, list);
                    }
                }
            }
        }
    }

    public static void fillPackageNames (Class<?> type, AModule am, ClassLoader loader, Set<String> packages) throws IOException {
        String base = type.getPackage ().getName ();
        packages.add (base);
        if (am.recursive ()) {
            fillPackageNames (loader, base, packages);
        }

        String[] array = am.value ();
        if (array.length == 0) {
            array = am.scanPackages ();
        }

        for (String packageName : array) {
            packages.add (packageName);
            if (am.recursive ()) {
                fillPackageNames (loader, packageName, packages);
            }
        }
    }

    public static void fillPackageNames (String base, String[] array, ClassLoader loader, Set<String> packages) throws IOException {
        packages.add (base);
        fillPackageNames (loader, base, packages);
        for (String packageName : array) {
            packages.add (packageName);
            fillPackageNames (loader, packageName, packages);
        }
    }
}