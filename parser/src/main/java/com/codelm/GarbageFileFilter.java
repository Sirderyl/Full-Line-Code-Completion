package com.codelm;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;

import java.util.List;
import java.util.regex.Pattern;

public class GarbageFileFilter {
    // This pattern is for filenames to identify hash-like names, which is usually the garbage file
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}_\\d+\\.java$");

    public static boolean isGarbage(String fileName, String javaCode) {
        // 1. Check for hashed filename anomalies
        if (HASH_PATTERN.matcher(fileName).matches()) {
            return isContentGarbage(javaCode);
        }

        // 2. If content is garbage, filter it out
        return isContentGarbage(javaCode);
    }

    private static boolean isContentGarbage(String javaCode) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaCode);
            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
            if (classes.isEmpty()) {
                return true; // Remove files with empty classes
            }

            for (ClassOrInterfaceDeclaration _class : classes) {
                List<MethodDeclaration> methods = _class.getMethods();
                if (methods.isEmpty()) continue; // Skip empty methods

                boolean hasNonEmptyMethod = false;
                for (MethodDeclaration method : methods) {
                    // Get a list of statements inside a method or get an empty list if no body
                    NodeList<Statement> statements = method.getBody().map(body -> body.getStatements()).orElse(NodeList.nodeList());
                    if (!statements.isEmpty()) {
                        hasNonEmptyMethod = true;
                        break;
                    }
                }
                // If all methods are empty or have no body, remove the file
                if (!hasNonEmptyMethod) return true;
            }
            return false;
        } catch (Exception e) {
            return false; // Includes normal files with repo tags at beginning, missing imports etc...
        }
    }
}
